package com.exojosh.minecraftsecondscreen.net

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

private const val TAG = "ResourcePackIcons"

/** Where vanilla resource packs store the individual HUD sprite PNGs as of the
 *  1.21.2+ HUD sprite-split. Verify exact filenames (e.g. whether it's
 *  "full.png"/"half.png" nested under a "heart/" folder, vs. flat
 *  "heart_full.png") by unzipping any real resource pack and checking
 *  assets/minecraft/textures/gui/sprites/hud/ directly -- don't trust this
 *  blind, we've been burned by guessed Minecraft internals all session. */
private const val HUD_SPRITE_DIR = "minecraft/textures/gui/sprites/hud"

enum class HudIcon(val candidateNames: List<String>) {
    HEART_FULL(listOf("heart/full.png", "heart/full.webp", "heart_full.png")),
    HEART_HALF(listOf("heart/half.png", "heart/half.webp", "heart_half.png")),
    HEART_CONTAINER(listOf("heart/container.png", "heart/container.webp", "heart_container.png")),
    ARMOR_FULL(listOf("armor/full.png", "armor/full.webp", "armor_full.png")),
    ARMOR_HALF(listOf("armor/half.png", "armor/half.webp", "armor_half.png")),
    ARMOR_EMPTY(listOf("armor/empty.png", "armor/empty.webp", "armor_empty.png")),
    FOOD_FULL(listOf("food_full.png", "food/full.webp", "food/full.png")),
    FOOD_HALF(listOf("food_half.png", "food/half.webp", "food/half.png")),
    FOOD_EMPTY(listOf("food_empty.png", "food/empty.webp", "food/empty.png")),
    EXPERIENCE_BAR_BACKGROUND(listOf("experience_bar_background.png")),
    EXPERIENCE_BAR_PROGRESS(listOf("experience_bar_progress.png"))
}

/**
 * Resolves and caches HUD icon bitmaps from the active resource pack stack,
 * falling back to null (caller decides the fallback -- e.g. a bundled
 * drawable or the original drawn placeholder) when no active pack overrides
 * a given sprite.
 *
 * Requires a granted folder tree from GameDirectoryAccess pointing at the
 * .minecraft root (the folder containing resourcepacks/ and options.txt).
 */
class ResourcePackIconProvider(private val context: Context) {

    private val cache = mutableMapOf<HudIcon, Bitmap?>()
    private var activePacksResolved = false
    private var activePackFolders: List<DocumentFile> = emptyList()

    /** Call once after confirming GameDirectoryAccess.hasValidAccess(). Cheap to call
     *  again later (e.g. after the player changes packs in-game) to pick up changes. */
    fun refresh() {
        cache.clear()
        //activePacksResolved = false
        //resolveActivePacks()
    }
    /*Old
    fun getIcon(icon: HudIcon): Bitmap? {
        if (!activePacksResolved) resolveActivePacks()
        return cache.getOrPut(icon) { searchActivePacksFor(icon) }
    }
    */

    /**
     * Fetches the requested HUD icon from cache or loads it from assets.
     */
    fun getIcon(icon: HudIcon): Bitmap? {
        //return cache.getOrPut(icon) { loadIconFromAssets(icon) }
        return loadIconFromAssets(icon)
    }

    private fun loadIconFromAssets(icon: HudIcon): Bitmap? {
        val assetManager = context.assets
        Log.d(TAG, "Test, calling loadIconFromAssets")
        for (candidate in icon.candidateNames) {
            val assetPath = "$HUD_SPRITE_DIR/$candidate"
            try {
                assetManager.open(assetPath).use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        Log.d(TAG, "SUCCESS: Loaded $assetPath")
                        return bitmap
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "FAILED to load asset at path: $assetPath")
            }
        }
        return null
    }

    private fun resolveActivePacks() {
        activePacksResolved = true
        val root = GameDirectoryAccess.getSavedTreeUri(context)?.let {
            DocumentFile.fromTreeUri(context, it)
        } ?: return

        val optionsFile = root.findFile("options.txt") ?: run {
            Log.w(TAG, "options.txt not found in granted directory")
            return
        }
        val resourcePacksDir = root.findFile("resourcepacks")

        val activeNames = parseActivePackNames(optionsFile)
        // options.txt lists packs bottom-to-top (later entries override earlier
        // ones), so reverse to search highest-priority pack first.
        activePackFolders = activeNames.reversed().mapNotNull { name ->
            resolvePackEntry(resourcePacksDir, name)
        }
    }

    private fun parseActivePackNames(optionsFile: DocumentFile): List<String> {
        val text = context.contentResolver.openInputStream(optionsFile.uri)
            ?.bufferedReader()?.use { it.readText() } ?: return emptyList()

        val line = text.lineSequence().firstOrNull { it.startsWith("resourcePacks:") }
            ?: return emptyList()

        // Format looks like: resourcePacks:["vanilla","file/SomePack.zip"]
        return Regex("\"([^\"]+)\"").findAll(line)
            .map { it.groupValues[1] }
            .filter { it != "vanilla" } // vanilla isn't a real file to search
            .toList()
    }

    private fun resolvePackEntry(resourcePacksDir: DocumentFile?, name: String): DocumentFile? {
        val fileName = name.removePrefix("file/")
        return resourcePacksDir?.findFile(fileName)
    }

    private fun searchActivePacksFor(icon: HudIcon): Bitmap? {
        for (pack in activePackFolders) {
            for (candidate in icon.candidateNames) {
                val bitmap = if (pack.name?.endsWith(".zip") == true) {
                    findInZip(pack, candidate)
                } else {
                    findInFolder(pack, candidate)
                }
                if (bitmap != null) return bitmap
            }
        }
        return null // caller falls back to a bundled default
    }

    private fun findInFolder(packRoot: DocumentFile, relativePath: String): Bitmap? {
        var current: DocumentFile? = packRoot
        val parts = "$HUD_SPRITE_DIR/$relativePath".split("/")
        for (part in parts) {
            current = current?.findFile(part) ?: return null
        }
        return current?.uri?.let { decodeUri(it) }
    }

    private fun findInZip(packZip: DocumentFile, relativePath: String): Bitmap? {
        val targetEntry = "$HUD_SPRITE_DIR/$relativePath"
        val input: InputStream = context.contentResolver.openInputStream(packZip.uri) ?: return null
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == targetEntry) {
                    return BitmapFactory.decodeStream(zip)
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun decodeUri(uri: android.net.Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}
