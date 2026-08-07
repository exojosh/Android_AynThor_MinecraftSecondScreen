# AYN Thor Second Screen — companion app

**The Android half of a two-part system that moves Minecraft's HUD onto the AYN
Thor's bottom screen.**

This app connects to a Fabric mod running inside Minecraft on the same device
and draws what the game's HUD would have drawn — hearts, hunger, armor, breath,
XP, the hotbar, a live map, your inventory and chat — on the second display,
using the game's own textures. It sends taps, item moves, key binds and chat
back.

| | |
|---|---|
| **This repo** | the Android app (Kotlin / Compose) |
| **The mod** | [AynThorSecondScreen](https://github.com/exojosh/AynThorSecondScreen) |

You need **both**, and **the mod's README is the install guide** — it covers
loading the mod in Zalith Launcher 2 and pairing the two. Start there.

## Install

Grab the APK from [Releases](https://github.com/exojosh/Android_AynThor_MinecraftSecondScreen/releases),
or build it:

```
./gradlew installDebug
```

No permissions, no setup, nothing to configure. Launch it, launch Minecraft, and
the bottom screen fills in.

## How it works

Two processes on one device, talking over a loopback socket
(`127.0.0.1:48291`), newline-delimited JSON out and short text commands back.
They're separate Android app sandboxes despite sharing a device, so a socket is
the simplest channel there is — and "only show the second screen while the game
is running" then falls straight out of the connection state.

**The app ships no Minecraft assets.** Every texture it draws with — heart and
armor sprites, the hotbar, the bitmap font, the GUI panel and buttons, item
icons rendered through the game's own model pipeline — arrives over that socket,
resolved through Minecraft's resource manager. That means your resource pack
applies here too, and no game files are copied, granted or redistributed.

## The screens

- **HUD** — the status stack (hearts, hunger, armor, bubbles, XP, hotbar,
  off-hand) over a live map. Tap a hotbar slot to select it.
- **Chat** — the game's log in Minecraft's own font, with a keyboard to answer
  it: the app's own by default, or Android's if you turn that on in Settings.
- **Items** — the open container, drawn on vanilla's GUI panel. Tap or drag to
  move, in `Stack` / `Half` / `Single` / `Move` modes; hold then drag to spread
  a stack across slots.
- **Input** — nine buttons, each bound to any key binding the game reports.
- **Settings** — which HUD elements live down here. Anything switched off is
  handed back to the game and drawn on the top screen instead, so the HUD is
  always drawn exactly once across the two displays.

## Development

```
./gradlew build                  # compile + lint
./gradlew installDebug           # install to a connected device
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (needs a device)
```

The second-screen `Presentation` only exists on real dual-screen hardware.
Without one, `MainActivity` falls back to a status view on the primary screen —
that's the normal way to develop this on a phone or emulator.

The logic worth trusting is kept free of Compose and Android types and unit
tested: inventory click sequences (`ui/InventoryMoves`), slot layout
(`ui/InventoryLayout`), heart rows (`ui/HeartLayout`), chat wrapping
(`ui/ChatLineWrapper`), input-slot persistence (`settings/InputSlotCodec`) and
the reconnect schedule (`net/ReconnectPolicy`). None of those fail loudly, which
is exactly why they're pinned.

## License

[CC0-1.0](https://creativecommons.org/publicdomain/zero/1.0/), same as the mod.
