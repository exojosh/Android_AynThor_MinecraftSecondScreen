package com.exojosh.minecraftsecondscreen.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ThorHudRepository"
private const val HOST = "127.0.0.1"
private const val PORT = 48291
private const val RECONNECT_DELAY_MS = 1500L

/** How long an unanswered ICON: request stays "in flight" before we re-send it.
 *  The mod renders at most a couple of icons per tick, so give it room. */
private const val ICON_REQUEST_TIMEOUT_MS = 3000L

/** Backoff after the mod explicitly says it has no icon for an item. */
private const val ICON_FAILURE_RETRY_MS = 60_000L

data class HotbarSlot(
    val itemId: String,
    val count: Int,
    val damage: Int,
    val maxDamage: Int,
    val hasGlint: Boolean
) {
    /** null when the item isn't damageable at all -- distinguishes "no bar to draw"
     *  from "bar at 0%," matching how maxDamage=0 comes through from the mod. */
    val durabilityFraction: Float?
        get() = if (maxDamage <= 0) null else 1f - (damage.toFloat() / maxDamage.toFloat())
}

data class HudState(
    val health: Float,
    val maxHealth: Float,
    val food: Int,
    val xpLevel: Int,
    val xpProgress: Float,
    val armor: Int,
    val selectedSlot: Int,
    /** Remaining breath in ticks, and its current maximum (300 unenchanted,
     *  raised by Respiration). Vanilla only shows bubbles while air < maxAir. */
    val air: Int,
    val maxAir: Int,
    val hotbar: List<HotbarSlot>
) {
    val isDrowning: Boolean get() = maxAir > 0 && air < maxAir
}

/**
 * Owns the connection to the mod's loopback socket. Exposes:
 *  - hudState: null when disconnected (mod not running / game not loaded),
 *    non-null once we've received at least one snapshot.
 *  - isConnected: drives whether the Presentation should even be showing --
 *    this is how "only show the second screen if the mod is present" works,
 *    with zero mod-detection logic needed on the launcher side.
 *  - sendCommand(): fire-and-forget a short code (e.g. "R") to the mod.
 *
 * Runs its own reconnect loop so the companion app can be started before,
 * during, or after Minecraft, in any order, and just keeps trying.
 */
class HudRepository(private val scope: CoroutineScope) {

    private val _hudState = MutableStateFlow<HudState?>(null)
    val hudState: StateFlow<HudState?> = _hudState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** Item id -> decoded icon bitmap. mutableStateMapOf so Compose recomposes
     *  automatically the moment a requested icon arrives -- no manual refresh needed. */
    val iconCache = mutableStateMapOf<String, Bitmap>()

    /**
     * Item id -> when we last asked the mod for it. This used to be a plain
     * "already requested" Set, which meant a request that never got answered
     * (mod couldn't resolve the item, or we asked before the socket was up and
     * sendCommand silently dropped it) permanently blocked that item from ever
     * being requested again. That was the actual cause of icons showing up
     * inconsistently. Timestamping instead lets an unanswered request expire
     * and be retried.
     *
     * Touched from the UI thread (requestIcon, during composition) and the IO
     * thread (response handling), hence the concurrent map.
     */
    private val pendingIconRequests = ConcurrentHashMap<String, Long>()

    /** Item id -> when the mod last told us it has no icon for this item.
     *  Retried far more slowly than a dropped request, but not never -- the
     *  answer can change when the player switches resource packs. */
    private val failedIconRequests = ConcurrentHashMap<String, Long>()

    @Volatile
    private var writer: PrintWriter? = null

    fun start() {
        scope.launch(Dispatchers.IO) { connectionLoop() }
    }

    /** Returns the cached icon if we already have it; otherwise fires a
     *  request and returns null -- the UI will recompose once iconCache
     *  updates. Safe to call every frame: repeat calls for an item we're
     *  already waiting on are dropped until the request goes stale. */
    fun requestIcon(itemId: String): Bitmap? {
        iconCache[itemId]?.let { return it }
        if (itemId.isEmpty() || itemId == "minecraft:air") return null

        val now = SystemClock.elapsedRealtime()

        failedIconRequests[itemId]?.let { failedAt ->
            if (now - failedAt < ICON_FAILURE_RETRY_MS) return null
            failedIconRequests.remove(itemId)
        }

        val lastRequestedAt = pendingIconRequests[itemId]
        if (lastRequestedAt != null && now - lastRequestedAt < ICON_REQUEST_TIMEOUT_MS) return null

        // Don't mark it pending if there's nowhere to send it -- otherwise the
        // request is dropped on the floor but still counts as "asked," and the
        // item goes iconless until the timeout expires.
        if (writer == null) return null

        pendingIconRequests[itemId] = now
        sendCommand("ICON:$itemId")
        return null
    }

    private suspend fun connectionLoop() {
        while (true) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(HOST, PORT), 1000)
                    socket.soTimeout = 5000

                    writer = PrintWriter(socket.getOutputStream(), true)
                    _isConnected.value = true
                    Log.i(TAG, "Connected to mod on $HOST:$PORT")

                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    var line: String?
                    while (true) {
                        line = try {
                            reader.readLine()
                        } catch (e: SocketTimeoutException) {
                            continue
                        }
                        if (line == null) break // mod closed the connection
                        parseAndPublish(line)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Disconnected/failed to connect: ${e.message}")
            }

            writer = null
            _isConnected.value = false
            _hudState.value = null
            // Anything we were still waiting on died with the socket. Drop the
            // in-flight bookkeeping (but keep iconCache) so the next
            // connection re-asks immediately instead of waiting out timeouts.
            pendingIconRequests.clear()
            failedIconRequests.clear()
            delay(RECONNECT_DELAY_MS)
        }
    }

    private fun parseAndPublish(line: String) {
        try {
            val json = JSONObject(line)
            if (json.optString("type") == "icon") {
                handleIconResponse(json)
            } else {
                handleHudState(json)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse line: $line", e)
        }
    }

    private fun handleIconResponse(json: JSONObject) {
        val itemId = json.getString("itemId")
        pendingIconRequests.remove(itemId)

        // The mod omits "data" entirely when it couldn't produce an icon.
        // That's a real answer, not a dropped request -- record it so we back
        // off instead of re-asking on every recomposition.
        val base64Png = if (json.isNull("data")) null else json.optString("data").takeIf { it.isNotEmpty() }
        if (base64Png == null) {
            Log.w(TAG, "Mod reported no icon available for $itemId")
            failedIconRequests[itemId] = SystemClock.elapsedRealtime()
            return
        }

        val bytes = Base64.decode(base64Png, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap != null) {
            iconCache[itemId] = bitmap
            failedIconRequests.remove(itemId)
        } else {
            Log.w(TAG, "Failed to decode icon bitmap for $itemId")
            failedIconRequests[itemId] = SystemClock.elapsedRealtime()
        }
    }

    private fun handleHudState(json: JSONObject) {
        val hotbarArray = json.getJSONArray("hotbar")
        val hotbar = buildList {
            for (i in 0 until hotbarArray.length()) {
                val slot = hotbarArray.getJSONObject(i)
                add(
                    HotbarSlot(
                        itemId = slot.getString("itemId"),
                        count = slot.getInt("count"),
                        damage = slot.getInt("damage"),
                        maxDamage = slot.getInt("maxDamage"),
                        hasGlint = slot.getBoolean("hasGlint")
                    )
                )
            }
        }

        _hudState.value = HudState(
            health = json.getDouble("health").toFloat(),
            maxHealth = json.getDouble("maxHealth").toFloat(),
            food = json.getInt("food"),
            xpLevel = json.getInt("xpLevel"),
            xpProgress = json.getDouble("xpProgress").toFloat(),
            armor = json.getInt("armor"),
            selectedSlot = json.getInt("selectedSlot"),
            // Tolerated as optional so a newer app still runs against an older
            // mod build that predates these fields -- defaults read as "not
            // drowning", which hides the bubbles.
            air = json.optInt("air", 0),
            maxAir = json.optInt("maxAir", 0),
            hotbar = hotbar
        )
    }

    /** Sends a short command code to the mod. No-op if not currently connected. */
    fun sendCommand(code: String) {
        val currentWriter = writer ?: run {
            Log.w(TAG, "sendCommand($code) dropped -- not connected")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                currentWriter.println(code)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send command $code: ${e.message}")
            }
        }
    }
}
