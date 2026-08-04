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

## Architecture

Full protocol spec (message shapes, field meanings, command codes) is in `thor-hud-handoff.md` §2 — don't re-derive it from source when the doc already has it verified. In short: the app connects to the mod's socket, receives a full HUD snapshot every client tick (~20/sec), renders it, and can request an item's icon on demand (`ICON:<itemId>` → base64 PNG reply) or send back a short command code representing a simulated key/hotbar press.

**Why the app is standalone rather than a launcher fork:** Android's `Presentation` API (`DISPLAY_CATEGORY_PRESENTATION`) is available to any app, so no ZL2-side integration is needed. "Only show the second screen when the mod is running" falls directly out of socket-connection state, with no separate detection logic.

### Key classes (`app/src/main/java/com/exojosh/minecraftsecondscreen/`)

- `MainActivity` — finds the Thor's presentation-category external display via `DisplayManager`; owns the single `HudRepository` instance; shows `SecondScreenPresentation` there if found.
- `SecondScreenPresentation` — `Presentation` subclass hosting a `ComposeView`, with lifecycle/viewmodel/saved-state owners manually wired to the host `Activity` (required because a `Presentation`'s window sits outside the normal Activity view hierarchy).
- `net/HudRepository` — owns the reconnect loop to the mod's socket (flat 1.5s retry, no backoff), parses inbound lines by presence/absence of `"type":"icon"`, exposes `hudState`/`isConnected` as `StateFlow` and `iconCache` as an unbounded Compose-observable map, and has `sendCommand()`/`requestIcon()` for the reverse direction.
- `net/GameDirectoryAccess` — SAF folder-picker + persisted URI permission, granting read access to ZL2's game directory (must point at shared storage, not `Android/data/...`, which is cross-app-inaccessible on Android).
- `net/ResourcePackIconProvider` — resolves HUD sprite icons (hearts/armor/food/xp) from the active resource pack (parses `options.txt` for pack order, searches zip/folder packs by the `HudIcon` enum's candidate paths), falling back to bundled assets under `app/src/main/assets/` when a pack doesn't override a sprite — the bundled-fallback logic lives inside this file, not a separate provider class.
- `SecondScreenApp` — top-level HUD/Input tab switcher shown inside the `Presentation`. Lives directly under the `minecraftsecondscreen` package, not `ui/`.
- `ui/HudScreen` — renders armor/hearts+hunger/XP bar/hotbar over a tiled background; also defines `RepeatingTextureBackground`, the tiling helper (inline in this file, not a separate file).
- `ui/HotbarRow` — the 9 hotbar slots: on-demand icon request, stack count, durability bar, static enchant-glint overlay, selected-slot highlight, tap-to-select.
- `ui/InputGridScreen` — 3×3 grid of buttons sending fixed command codes.

## Handoff doc vs. current code

`thor-hud-handoff.md` is accurate on protocol and overall architecture, but its file/class inventory has drifted from the current tree — don't assume a class it names still exists or is still missing without checking:

- `RadialInputPad.kt` and standalone `TiledTextureBackground.kt` / `BundledIconProvider.kt` **do not exist**. The doc's claim that a radial gesture pad is "implemented" is not reflected in the current source — only the 3×3 `InputGridScreen` grid is wired into `SecondScreenApp`. Tiling and bundled-icon-fallback logic exist but live inline inside `HudScreen.kt` and `ResourcePackIconProvider.kt` respectively, not as separate files.
- Package name is settled at `com.exojosh.minecraftsecondscreen`, not still in flux as the doc's §3 warns.

## Known unverified/fragile areas (per the handoff doc)

- No reconnect backoff or icon-cache eviction — fine for development, not production-hardened.
- Enchant glint on hotbar items is a static translucent overlay, not vanilla's animated shimmer (intentional simplification).
