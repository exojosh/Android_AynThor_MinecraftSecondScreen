# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone Android app (Kotlin/Compose) that connects to a companion Fabric mod's loopback socket and renders Minecraft's HUD (health, hunger, XP, armor, hotbar) on the second (bottom) screen of an **AYN Thor** (dual-screen Android handheld), via Android's `Presentation` API. It also sends input back to the mod — tap-to-select hotbar, a button grid for custom keybinds.

This app is one half of a two-process system. The other half — the Fabric mod that hides the vanilla HUD and streams its state — lives in a sibling repo, `AynThorSecondScreen`. The two communicate only via a JSON-over-TCP protocol on `127.0.0.1:48291`; there's no shared code or build.

**`thor-hud-handoff.md`** is a detailed technical handoff/design doc covering the full system (both repos) — read it first for the JSON protocol spec, architecture rationale, and known issues. It's a snapshot from a point in development and its file/class inventory has drifted from the current tree in a few specific ways (see "Handoff doc vs. current code" below) — verify anything file-specific against the actual source before relying on it.

## Commands

```
./gradlew build                  # compile + lint
./gradlew installDebug           # install to a connected device/emulator
./gradlew test                   # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # instrumented tests (app/src/androidTest), needs a device/emulator
```

Since the second-screen `Presentation` only appears on real dual-screen hardware, `MainActivity` falls back to a minimal status view on the primary screen when no `DISPLAY_CATEGORY_PRESENTATION` display is found — this is the normal (and only practical) way to dev/test on a regular phone or emulator.

## HUD textures come from the mod, not from bundled assets

**The app needs no Minecraft assets on disk.** On connect, the mod pushes every HUD texture it needs — hearts, armor, food, bubbles, XP bar, hotbar, the bitmap font sheet, the tiled background — as `{"type":"asset"}` lines, resolved through Minecraft's *own* resource manager. That means they already reflect the player's active resource pack, with no pack-parsing, no SAF folder grant, and no hand-copying. `HudAssetCatalog` on the mod side owns the key→path mapping and is authoritative; `HudIcon.assetKey` must match it.

`app/src/main/assets/minecraft/` still exists as a **legacy fallback** (and is gitignored — extracted game textures aren't redistributable and this repo is public). `ResourcePackIconProvider` checks the socket-delivered `assetCache` first and only then a bundled file, so those files are optional and safe to delete; they only matter when running against a mod build older than asset delivery. Note the bundled-path candidate names were partly *guessed wrong* — vanilla nests hearts under `heart/` but keeps armor and food flat with an underscore (`hud/armor_full.png`) — another reason to prefer the mod's copy.

If you do keep local files: **filenames are case-sensitive**, Android's asset manager won't find `Hotbar.webp` when the code asks for `hotbar.webp`, and on Windows a case-only rename can leave the Gradle asset merger reporting a bogus "Duplicate resources" error until `app/build/intermediates/assets` is deleted.

`GameDirectoryAccess` (the SAF folder picker) and `ResourcePackIconProvider`'s `resolveActivePacks`/`searchActivePacksFor` are now **dead weight** — they were the app's own attempt at reading resource packs, which the mod does better. Not yet removed.

## Architecture

Full protocol spec (message shapes, field meanings, command codes) is in `thor-hud-handoff.md` §2 — don't re-derive it from source when the doc already has it verified. In short: the app connects to the mod's socket, receives a full HUD snapshot every client tick (~20/sec), renders it, and can request an item's icon on demand (`ICON:<itemId>` → base64 PNG reply) or send back a short command code representing a simulated key/hotbar press.

**Since that doc was written**, three protocol additions:

- The snapshot gained `air`/`maxAir` (breath in ticks and its current maximum — Respiration raises it above 300) for the bubble row, and `absorption` (extra golden-heart health). All parsed with `optInt`/`optDouble` defaults so a newer app still runs against an older mod build; the defaults read as "not drowning, no absorption".
- Icon replies gained a failure form: `{"type":"icon","itemId":...}` with **no `data` field**, meaning "the mod tried and has no icon for this".
- **`{"type":"asset","assetId":...,"data":...}`** — a HUD texture, pushed unprompted for every catalog key right after a client connects (also `data`-less when unavailable). The `ASSETS` command asks for the bundle again, which is how a resource pack change is picked up — the mod has no reload hook, so nothing re-sends on its own.

**Why the app is standalone rather than a launcher fork:** Android's `Presentation` API (`DISPLAY_CATEGORY_PRESENTATION`) is available to any app, so no ZL2-side integration is needed. "Only show the second screen when the mod is running" falls directly out of socket-connection state, with no separate detection logic.

### Key classes (`app/src/main/java/com/exojosh/minecraftsecondscreen/`)

- `MainActivity` — finds the Thor's presentation-category external display via `DisplayManager`; owns the single `HudRepository` instance; shows `SecondScreenPresentation` there if found.
- `SecondScreenPresentation` — `Presentation` subclass hosting a `ComposeView`, with lifecycle/viewmodel/saved-state owners manually wired to the host `Activity` (required because a `Presentation`'s window sits outside the normal Activity view hierarchy).
- `net/HudRepository` — owns the reconnect loop to the mod's socket (flat 1.5s retry, no backoff), parses inbound lines by presence/absence of `"type":"icon"`, exposes `hudState`/`isConnected` as `StateFlow` and `iconCache` as an unbounded Compose-observable map, and has `sendCommand()`/`requestIcon()` for the reverse direction. Icons arriving from the mod are rendered isometrically by the mod itself (see its CLAUDE.md), so nothing here needs to know about item models.
  - **Icon request retry:** `requestIcon()` is called during composition, so it must tolerate being hit every frame. It tracks `pendingIconRequests` (itemId → timestamp) and `failedIconRequests` rather than a plain "already requested" set. The set version was a real bug: a request that was never answered — because the mod couldn't resolve the item, or because it was sent before the socket came up and `sendCommand()` dropped it — permanently blocked that item from ever being requested again, which is what made icons appear inconsistently. Requests now expire after `ICON_REQUEST_TIMEOUT_MS` and retry; an explicit "no icon" reply from the mod (a `"type":"icon"` line with no `data` field) backs off for `ICON_FAILURE_RETRY_MS` instead of retrying hot; and both maps clear on disconnect so a reconnect re-asks immediately. `requestIcon()` also refuses to mark anything pending while `writer == null`.
- `net/GameDirectoryAccess` — SAF folder-picker + persisted URI permission, granting read access to ZL2's game directory (must point at shared storage, not `Android/data/...`, which is cross-app-inaccessible on Android).
- `net/ResourcePackIconProvider` — resolves HUD sprite icons (hearts/armor/food/xp) from the active resource pack (parses `options.txt` for pack order, searches zip/folder packs by the `HudIcon` enum's candidate paths), falling back to bundled assets under `app/src/main/assets/` when a pack doesn't override a sprite — the bundled-fallback logic lives inside this file, not a separate provider class.
- `SecondScreenApp` — top-level HUD/Input tab switcher shown inside the `Presentation`. Lives directly under the `minecraftsecondscreen` package, not `ui/`.
- `ui/HudScreen` — renders armor/bubbles/hearts+hunger/XP bar/hotbar over a tiled background; also defines `RepeatingTextureBackground`, the tiling helper (inline in this file, not a separate file). `XpBar` and `BubbleRow` both work in *logical texture pixels* scaled from the available width, the same approach `HotbarRow` uses — see below.
  - `XpBar` follows `ExperienceBar.renderBar`: the 182×5 progress sprite is **source-cropped** to the fill fraction and drawn into the same fraction of the destination, keeping it at 1:1 scale so its internal segment boundaries line up with the unfilled bar behind it. Don't reimplement this as a full-width `Image` inside a clipped/`fillMaxWidth(fraction)` box — `Modifier.width()` is only a *preferred* size, so the narrowed parent constraints squeeze the Image and `ContentScale.FillBounds` stretches the bitmap. That was a real bug; the explicit `drawImage(srcOffset/srcSize/dstSize)` form is there to avoid it. Vanilla's fill width is `progress * 183` (one wider than the sprite, so a nearly-full bar actually reaches the end); that's preserved and clamped. The level number is `MinecraftTextStyle.OUTLINE` in `#80FF20`, hidden at level 0, and deliberately overhangs the bar upwards with its bottom 2px overlapping — all straight from `Bar.drawExperienceLevel`.
  - `HeartRow` follows `renderHealthBar`, including absorption: `ceil(maxHealth/2)` normal slots followed by `ceil(absorption/2)` absorption slots in one continuous run, every slot backed by a container, and the last absorption heart drawn as a half when the amount is odd. Capped at one row of 10 — vanilla wraps into stacked rows above, which isn't reproduced.
  - **The bubble index is deliberately mirrored.** Vanilla positions bubble `n` (1-based) at `left - (n-1)*8 - 9`, so n=1 is the *rightmost* and full bubbles are `n <= k`. As air drops the highest-numbered bubble goes first, so the row empties from its **left** end. Laying bubbles out in naive left-to-right index order pops them from the wrong side; `position = STATUS_ICON_COUNT - index` is what fixes it.
  - **All four status rows (armor, hearts, hunger, bubbles) share the `STATUS_ICON_SIZE`/`STATUS_ICON_COUNT` grid.** Vanilla aligns its rows for free because they all use 9px sprites on an 8px pitch; this HUD draws icons edge to edge instead, so anything not on that shared grid comes out a different width than the row above it. Bubbles previously used vanilla's 8px pitch and ended up narrower than the hunger row.
  - `BubbleRow` follows `InGameHud.renderAirBubbles`. Bubble counts use vanilla's `getAirBubbles` rounding, where the full count lags two ticks of air behind so the last bubble visibly bursts before vanishing. Only rendered while `air < maxAir`, matching vanilla (there's no full-row idle state). Vanilla's brief `air_empty` pop animation isn't reproduced, so an emptied slot renders nothing — same class of simplification as the enchant glint. It shares a `Row` with the armor icons rather than having its own line, mirroring vanilla's stacking: armor above health on the left, bubbles above hunger on the right. The row's height comes from the armor icons, so it doesn't collapse when the player surfaces.
- `ui/MinecraftFont` — vanilla in-game text. Minecraft's font is a **bitmap sheet, not a TTF**, so matching it means drawing glyphs from `assets/minecraft/textures/font/ascii.png` (a 16×16 grid of cells covering codepoints 0x00–0xFF) rather than shipping a lookalike font file — which also means a resource pack restyling the font works automatically. `MinecraftFontSheet.from()` measures each glyph's advance the way vanilla does (scan the cell right-to-left for the last column with any non-transparent pixel, +1px letter spacing), normalising to vanilla's 8-logical-pixels-per-cell so a hi-res pack sheet lays out identically to the 128×128 default; space is hardcoded to 4 since it has no pixels to measure. `MinecraftText()` draws it with vanilla's 1px offset quarter-brightness drop shadow. Build the sheet via `rememberMinecraftFont()` — measuring walks every pixel, so don't redo it per recomposition. Sizing is in *font pixels* (`pixelSize`), not `sp`.
- `ui/HotbarRow` — the 9 hotbar slots: on-demand icon request, stack count, durability bar, static enchant-glint overlay, selected-slot highlight, tap-to-select. Takes optional `backgroundBitmap`/`selectionBitmap` params (sourced from `HudIcon.HOTBAR_BACKGROUND`/`HOTBAR_SELECTION`); when null it falls back to the original per-slot bordered boxes. No actual hotbar texture is bundled yet — drop one into `app/src/main/assets/minecraft/textures/gui/sprites/hud/hotbar.png` (and optionally `hotbar_selection.png`) to activate it. The pixel-alignment math is a best-effort approximation of vanilla's proportions (182x22 background, 20px slot pitch, ~24px-wide selection overlay), scaled from whatever bitmap actually gets bundled rather than hardcoded pixel counts — check it visually on-device once a real texture is in place, per the note in `thor-hud-handoff.md` on hotbar pixel alignment.
- `ui/InputGridScreen` — 3×3 grid of buttons sending fixed command codes.

## Handoff doc vs. current code

`thor-hud-handoff.md` is accurate on protocol and overall architecture, but its file/class inventory has drifted from the current tree — don't assume a class it names still exists or is still missing without checking:

- `RadialInputPad.kt` and standalone `TiledTextureBackground.kt` / `BundledIconProvider.kt` **do not exist**. The doc's claim that a radial gesture pad is "implemented" is not reflected in the current source — only the 3×3 `InputGridScreen` grid is wired into `SecondScreenApp`. Tiling and bundled-icon-fallback logic exist but live inline inside `HudScreen.kt` and `ResourcePackIconProvider.kt` respectively, not as separate files.
- Package name is settled at `com.exojosh.minecraftsecondscreen`, not still in flux as the doc's §3 warns.

## Known unverified/fragile areas (per the handoff doc)

- No reconnect backoff or icon-cache eviction — fine for development, not production-hardened.
- Enchant glint on hotbar items is a static translucent overlay, not vanilla's animated shimmer (intentional simplification).
