# tools

Dev tooling for working on the HUD without a running Minecraft.

## Why

The HUD's whole job is to render state that only exists inside a running
modded Minecraft on a dual-screen device. That made every UI change
effectively unverifiable: the absorption-heart feature shipped as unreachable
dead code and stayed that way, because confirming it needed a golden apple in a
real world on real hardware.

`hud_sim.py` removes that dependency. It speaks the mod's socket protocol, so
the app can be driven into any state — absorption, drowning, reduced max
health, death — in seconds, and the result screenshotted off the actual second
display.

## hud_sim.py

Stand-in for `HudStateServer`. Listens on 48291, sends the asset bundle and the
key-binding list on connect, then streams a fixed `HudState` at 2 Hz. Answers
`ICON:`, `ASSETS` and `BINDINGS` requests.

The bindings in `BINDINGS` are a hand-written slice of what `KeyBindingCatalog`
really sends — several categories so the picker's grouping is visible, one
unbound entry, and one pretending to come from another mod, since "bindings are
data, not a table" is the thing worth being able to see.

Textures are read out of a **real client jar**, so the app exercises its normal
socket-asset path rather than falling back to drawn placeholders. Pull one off
the device:

```
adb pull "/sdcard/Android/data/com.movtery.zalithlauncher.v2/files/.minecraft/versions/1.21.11 Fabric 0.19.3/1.21.11 Fabric 0.19.3.jar" mc_client.jar
```

Icon replies are the flat `textures/item|block/<name>.png`, *not* the mod's
isometric render — this is for exercising HUD layout, not judging icon
fidelity. Items whose icon isn't a same-named texture file (`jungle_stairs`,
`stripped_birch_wood`) come back empty here, which is exactly the limitation
`ItemIconRenderer` exists to solve on the mod side.

```
python hud_sim.py mc_client.jar absorption
```

Scenarios live in `SCENARIOS` at the top of the file; add cases there rather
than editing `BASE`.

## capture_hud.ps1

Runs a list of scenarios end to end and pulls a screenshot of the second
display for each.

```
adb reverse tcp:48291 tcp:48291
.\capture_hud.ps1 -Jar .\mc_client.jar -Scenarios absorption,drowning,reduced-max -OutDir .\shots
```

`adb reverse` is what makes this work: the app connects to `127.0.0.1:48291` on
the device exactly as it would to the real mod, and that lands on this
machine's listener instead.

**Minecraft must not be running** — the real mod binds the same port on device.

**Always tear the reverse down when you're finished:**

```
adb reverse --remove tcp:48291
```

It outlives the simulator, the shell and app restarts, and while it's up
*adbd* owns 48291 on the device — so the mod's `ServerSocket` fails to bind
(`BindException`, logged once to `latest.log` and then the accept thread exits
for good) and the app connects to adbd instead, which accepts and instantly
drops every connection. On the second screen that reads as "Not connected"
alternating with "Waiting for map data…", i.e. exactly like a broken app build.
Restarting Minecraft is then required even after the reverse is removed.

`-DisplayId` defaults to the AYN Thor's bottom screen. For other hardware, find
it with `adb shell dumpsys SurfaceFlinger --display-id`.

**Check what actually has focus before trusting a run.** The AYN dual-screen
panel can sit on top of the `Presentation`, in which case every shot is that
panel and the script still prints `captured X` for each one:

```
adb shell dumpsys window displays | Select-String mCurrentFocus
```

If display 4's focus is `com.odin.dualscreen.assistant`, the captures are
worthless. Nothing over adb dismisses it — `keyevent BACK` on display 4,
`am force-stop` (it restarts at once), tapping the selected mode, relaunching
`MainActivity` — so it appears to need the physical dual-screen control.

## What this does not cover

The mod side. A protocol change still has to be exercised against the real mod
in a real world; the simulator will happily keep speaking the old shape. Treat
green screenshots here as "the app renders this state correctly", not "the
feature works end to end".
