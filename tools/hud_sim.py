"""
Stand-in for the Fabric mod's HudStateServer.

Speaks the same newline-delimited JSON protocol on 48291, so the companion app
can be driven into any HUD state -- absorption, drowning, low health -- without
Minecraft running at all. Paired with `adb reverse tcp:48291 tcp:48291`, the
app on the Thor connects to this instead of the real mod.

Textures come out of a real client jar, so the app takes the same asset path it
would in production rather than falling back to its drawn placeholders.

Usage:  python hud_sim.py <client.jar> <scenario>
"""
import base64
import json
import socket
import sys
import threading
import time
import zipfile

PORT = 48291

# Mirrors HudAssetCatalog on the mod side. Keys are the wire contract; paths
# are what they resolve to in the jar.
ASSETS = {
    "heart_full": "textures/gui/sprites/hud/heart/full.png",
    "heart_half": "textures/gui/sprites/hud/heart/half.png",
    "heart_container": "textures/gui/sprites/hud/heart/container.png",
    "heart_absorbing_full": "textures/gui/sprites/hud/heart/absorbing_full.png",
    "heart_absorbing_half": "textures/gui/sprites/hud/heart/absorbing_half.png",
    "armor_full": "textures/gui/sprites/hud/armor_full.png",
    "armor_half": "textures/gui/sprites/hud/armor_half.png",
    "armor_empty": "textures/gui/sprites/hud/armor_empty.png",
    "food_full": "textures/gui/sprites/hud/food_full.png",
    "food_half": "textures/gui/sprites/hud/food_half.png",
    "food_empty": "textures/gui/sprites/hud/food_empty.png",
    "air": "textures/gui/sprites/hud/air.png",
    "air_bursting": "textures/gui/sprites/hud/air_bursting.png",
    "xp_bar_background": "textures/gui/sprites/hud/experience_bar_background.png",
    "xp_bar_progress": "textures/gui/sprites/hud/experience_bar_progress.png",
    "hotbar": "textures/gui/sprites/hud/hotbar.png",
    "hotbar_selection": "textures/gui/sprites/hud/hotbar_selection.png",
    "font_ascii": "textures/font/ascii.png",
    "background": "textures/block/dirt.png",
}


def slot(item_id, count=1, damage=0, max_damage=0, glint=False):
    return {
        "itemId": item_id,
        "count": count,
        "damage": damage,
        "maxDamage": max_damage,
        "hasGlint": glint,
    }


EMPTY = slot("minecraft:air", 0)

HOTBAR = [
    slot("minecraft:diamond_sword", 1, 120, 1561, True),
    slot("minecraft:diamond_pickaxe", 1, 40, 1561),
    slot("minecraft:jungle_stairs", 64),
    slot("minecraft:stripped_birch_wood", 32),
    slot("minecraft:golden_apple", 3),
    slot("minecraft:cooked_beef", 12),
    slot("minecraft:torch", 64),
    EMPTY,
    EMPTY,
]

BASE = {
    "health": 20.0,
    "maxHealth": 20.0,
    "absorption": 0.0,
    "armor": 12,
    "food": 17,
    "xpLevel": 30,
    "xpProgress": 0.45,
    "selectedSlot": 0,
    "air": 300,
    "maxAir": 300,
    "hotbar": HOTBAR,
}

SCENARIOS = {
    # The case the old clamp made unreachable: full health leaves no room in a
    # 10-slot row, so absorption has to wrap to a second row or vanish.
    "absorption": {"absorption": 4.0},
    "absorption-odd": {"absorption": 3.0},
    "absorption-big": {"absorption": 12.0},
    "plain": {},
    "hurt": {"health": 7.0, "food": 4},
    "half-heart": {"health": 19.0},
    "drowning": {"air": 130, "maxAir": 300},
    "drowning-low": {"air": 40, "maxAir": 300},
    "reduced-max": {"health": 6.0, "maxHealth": 12.0},
    "dead": {"health": 0.0},
}


def load_assets(jar_path):
    out = {}
    with zipfile.ZipFile(jar_path) as z:
        names = set(z.namelist())
        for key, path in ASSETS.items():
            full = "assets/minecraft/" + path
            if full in names:
                out[key] = base64.b64encode(z.read(full)).decode("ascii")
            else:
                print(f"  MISSING in jar: {key} -> {full}")
                out[key] = None
    return out


def load_item_textures(jar_path):
    """Cheap stand-in for ItemIconRenderer: the flat item/block texture.

    Not isometric -- this simulator is for exercising the app's HUD layout, not
    for judging icon fidelity.
    """
    tex = {}
    with zipfile.ZipFile(jar_path) as z:
        names = set(z.namelist())
        for s in HOTBAR:
            item = s["itemId"]
            if item == "minecraft:air":
                continue
            short = item.split(":", 1)[1]
            for folder in ("item", "block"):
                p = f"assets/minecraft/textures/{folder}/{short}.png"
                if p in names:
                    tex[item] = base64.b64encode(z.read(p)).decode("ascii")
                    break
    return tex


def handle(conn, addr, assets, item_tex, state):
    print(f"[sim] client connected: {addr}")
    send_lock = threading.Lock()

    def send(obj):
        with send_lock:
            conn.sendall((json.dumps(obj) + "\n").encode("utf-8"))

    # Asset bundle first, as the mod does for each new connection.
    for key, data in assets.items():
        send({"type": "asset", "assetId": key, "data": data})
    print(f"[sim] sent {len(assets)} assets")

    stop = threading.Event()

    def reader():
        buf = b""
        while not stop.is_set():
            try:
                chunk = conn.recv(4096)
            except OSError:
                break
            if not chunk:
                break
            buf += chunk
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                cmd = line.decode("utf-8", "replace").strip()
                if not cmd:
                    continue
                print(f"[sim] <- {cmd}")
                if cmd.startswith("ICON:"):
                    item = cmd[5:]
                    send({"type": "icon", "itemId": item, "data": item_tex.get(item)})
                elif cmd == "ASSETS":
                    for k, d in assets.items():
                        send({"type": "asset", "assetId": k, "data": d})
        stop.set()

    threading.Thread(target=reader, daemon=True).start()

    try:
        while not stop.is_set():
            send(state)
            time.sleep(0.5)
    except OSError:
        pass
    print(f"[sim] client gone: {addr}")


def main():
    jar = sys.argv[1]
    name = sys.argv[2] if len(sys.argv) > 2 else "absorption"
    if name not in SCENARIOS:
        print(f"unknown scenario {name!r}; have: {', '.join(sorted(SCENARIOS))}")
        return 2

    state = dict(BASE)
    state.update(SCENARIOS[name])
    print(f"[sim] scenario {name}: "
          + ", ".join(f"{k}={v}" for k, v in SCENARIOS[name].items() or [("(base)", "")]))

    print("[sim] loading textures from jar...")
    assets = load_assets(jar)
    item_tex = load_item_textures(jar)
    print(f"[sim] {sum(1 for v in assets.values() if v)} assets, {len(item_tex)} item textures")

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", PORT))
    srv.listen(4)
    print(f"[sim] listening on {PORT}")

    while True:
        conn, addr = srv.accept()
        threading.Thread(target=handle, args=(conn, addr, assets, item_tex, state),
                         daemon=True).start()


if __name__ == "__main__":
    sys.exit(main() or 0)
