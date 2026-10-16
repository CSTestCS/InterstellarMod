#!/usr/bin/env python3
"""Generates Interstellar's client assets and simple data files.

Run from the repository root:  python3 tools/gen_assets.py
Writes (overwriting) under src/main/resources:
  assets/interstellar/{blockstates,models,items,textures,lang}
  data/interstellar/{recipe,loot_table}, data/*/tags
Gun part models are derived from the part ids in data/interstellar/gun/part (run tools/gen_gun_data.py first).
Textures are procedural placeholders in a consistent sci-fi palette; replace any PNG freely.
"""
import json
import math
import os
import shutil
import random
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "interstellar")
D = os.path.join(ROOT, "data")
NS = "interstellar"


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- PNG
def png(path, w, h, pixels):
    """pixels: list of (r,g,b,a) rows*cols"""
    raw = b""
    for y in range(h):
        raw += b"\x00" + bytes(c for x in range(w) for c in pixels[y * w + x])
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3] if len(c) > 3 else 255)


def tex(name, base, accent=None, pattern="panel", seed=0, size=16):
    rng = random.Random(hash(name) ^ seed)
    px = []
    for y in range(size):
        for x in range(size):
            f = 0.9 + rng.random() * 0.15
            c = base
            if pattern == "panel":
                if x in (0, size - 1) or y in (0, size - 1):
                    f *= 0.65
                elif (x == size // 2 or y == size // 2) and accent is None:
                    f *= 0.85
            if pattern == "grate" and y % 3 == 0:
                f *= 0.6
            if pattern == "rivets" and (x in (2, size - 3) and y in (2, size - 3)):
                c = accent or (220, 220, 220)
            if accent is not None and pattern in ("glow", "screen"):
                cx, cy = size / 2 - 0.5, size / 2 - 0.5
                d = math.hypot(x - cx, y - cy)
                if pattern == "glow" and d < size * 0.3:
                    c = accent
                    f = 1.15 - d / (size * 0.3) * 0.3
                if pattern == "screen" and 3 <= x <= size - 4 and 3 <= y <= size - 7:
                    c = accent
                    f = 0.8 + 0.3 * ((x + y) % 3 == 0)
                if pattern == "screen" and y >= size - 5 and x % 3 == 1 and 2 <= x <= size - 3:
                    c = (220, 60, 60) if x % 2 else (60, 220, 90)
            if x in (0, size - 1) or y in (0, size - 1):
                f *= 0.8
            px.append(shade(c, f))
    png(os.path.join(A, "textures", name + ".png"), size, size, px)


def item_icon(name, color, shape):
    """16x16 item sprite drawn from a tiny shape mask."""
    rng = random.Random(hash(name))
    px = [(0, 0, 0, 0)] * 256
    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y * 16 + x] = c
    if shape == "ingot":
        for y in range(6, 11):
            for x in range(3 + (10 - y) // 2, 13 - (y - 6) // 2):
                put(x, y, shade(color, 0.85 + rng.random() * 0.25 if y > 6 else 1.2))
    elif shape == "crystal":
        for y in range(2, 14):
            w = 4 - abs(y - 8) // 2
            for x in range(8 - w, 8 + w):
                put(x, y, shade(color, 0.8 + (x - 8 + w) / (2 * w) * 0.5))
    elif shape == "orb":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 5.5:
                    put(x, y, shade(color, 1.25 - d / 6))
    elif shape == "chip":
        for y in range(4, 12):
            for x in range(4, 12):
                put(x, y, shade(color, 0.7 if (x + y) % 4 == 0 else 1.0))
        for i in range(5, 11, 2):
            for p in (put(i, 3, (200, 200, 200, 255)), put(i, 12, (200, 200, 200, 255)), put(3, i, (200, 200, 200, 255)), put(12, i, (200, 200, 200, 255))):
                pass
    elif shape == "magazine":
        for y in range(3, 14):
            for x in range(6 + (y - 3) // 4, 11 + (y - 3) // 4):
                put(x, y, shade(color, 0.75 if y in (3, 13) else 1.0))
        for x in range(7, 10):
            put(x, 2, (230, 190, 80, 255))
    elif shape == "scanner":
        for y in range(3, 14):
            for x in range(4, 12):
                put(x, y, shade((70, 74, 84, 255), 0.8 if x in (4, 11) or y in (3, 13) else 1.0))
        for y in range(5, 9):
            for x in range(6, 10):
                put(x, y, shade(color, 0.9 + (x + y) % 2 * 0.25))
        for x in range(6, 10, 3):
            put(x, 11, (230, 80, 60, 255))
        put(8, 2, (200, 200, 200, 255))
        put(8, 1, (255, 220, 90, 255))
    elif shape == "egg":
        for y in range(16):
            for x in range(16):
                d = math.hypot((x - 7.5) / 5, (y - 8.5) / 6.5)
                if d < 1:
                    spot = rng.random() < 0.18
                    put(x, y, shade((120, 60, 200) if spot else color, 1.15 - d * 0.4))
    png(os.path.join(A, "textures", "item", name + ".png"), 16, 16, px)


# ---------------------------------------------------------------- blocks
DIRECTIONAL = {  # block: (front, side, top)
    "control_panel": ("block/control_panel_front", "block/ship_side", "block/control_panel_top"),
    "ship_generator": ("block/ship_generator_front", "block/ship_side", "block/ship_top"),
    "life_support": ("block/life_support_front", "block/ship_side", "block/ship_top"),
    "ship_thruster": ("block/ship_thruster_front", "block/ship_side", "block/ship_top"),
    "gunsmith_table": ("block/gunsmith_table_front", "block/gunsmith_table_side", "block/gunsmith_table_top"),
}


def blocks():
    hull = (118, 126, 140)
    tex("block/hull_plating", hull, pattern="rivets")
    tex("block/ship_side", (96, 104, 118), pattern="panel")
    tex("block/ship_top", (110, 118, 132), pattern="grate")
    tex("block/control_panel_front", (60, 66, 78), accent=(60, 200, 255), pattern="screen")
    tex("block/control_panel_top", (70, 76, 88), accent=(255, 180, 60), pattern="screen")
    tex("block/ship_generator_front", (80, 86, 98), accent=(255, 210, 80), pattern="glow")
    tex("block/life_support_front", (80, 98, 92), accent=(90, 255, 160), pattern="glow")
    tex("block/ship_thruster_front", (50, 52, 60), accent=(90, 170, 255), pattern="glow")
    tex("block/ship_cannon_front", (64, 60, 60), accent=(255, 90, 50), pattern="glow")
    tex("block/landing_gear", (150, 140, 90), pattern="grate")
    tex("block/landing_gear_top", (120, 120, 130), pattern="rivets")
    tex("block/passenger_seat", (150, 40, 40), pattern="panel")
    tex("block/gunsmith_table_front", (90, 70, 50), accent=(200, 200, 210), pattern="rivets")
    tex("block/gunsmith_table_side", (80, 62, 46), pattern="panel")
    tex("block/gunsmith_table_top", (140, 144, 150), pattern="grate")

    # directional cubes
    for name, (front, side, top) in DIRECTIONAL.items():
        write_json(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/orientable",
                   "textures": {"front": f"{NS}:{front}", "side": f"{NS}:{side}", "top": f"{NS}:{top}"}})
        variants = {f"facing={f}": {"model": f"{NS}:block/{name}", **({"y": r} if r else {})}
                    for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        write_json(f"{A}/blockstates/{name}.json", {"variants": variants})
    # control panel: lower console shape
    write_json(f"{A}/models/block/control_panel.json", {
        "parent": "minecraft:block/block", "textures": {"particle": f"{NS}:block/control_panel_front", "front": f"{NS}:block/control_panel_front",
                                                      "side": f"{NS}:block/ship_side", "top": f"{NS}:block/control_panel_top"},
        "elements": [{"from": [0, 0, 0], "to": [16, 12, 16], "faces": {
            "north": {"texture": "#front", "uv": [0, 4, 16, 16]}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
            "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#side"}}}]})
    # simple cubes
    write_json(f"{A}/models/block/hull_plating.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/hull_plating"}})
    write_json(f"{A}/blockstates/hull_plating.json", {"variants": {"": {"model": f"{NS}:block/hull_plating"}}})
    # landing gear
    write_json(f"{A}/models/block/landing_gear_extended.json", {
        "parent": "minecraft:block/block", "textures": {"particle": f"{NS}:block/landing_gear", "s": f"{NS}:block/landing_gear", "t": f"{NS}:block/landing_gear_top"},
        "elements": [
            {"from": [0, 12, 0], "to": [16, 16, 16], "faces": {d: {"texture": "#t" if d in ("up", "down") else "#s"} for d in ("north", "south", "east", "west", "up", "down")}},
            {"from": [6, 2, 6], "to": [10, 12, 10], "faces": {d: {"texture": "#s"} for d in ("north", "south", "east", "west")}},
            {"from": [3, 0, 3], "to": [13, 2, 13], "faces": {d: {"texture": "#t"} for d in ("north", "south", "east", "west", "up", "down")}}]})
    write_json(f"{A}/models/block/landing_gear_retracted.json", {
        "parent": "minecraft:block/block", "textures": {"particle": f"{NS}:block/landing_gear", "s": f"{NS}:block/landing_gear", "t": f"{NS}:block/landing_gear_top"},
        "elements": [{"from": [0, 10, 0], "to": [16, 16, 16], "faces": {d: {"texture": "#t" if d in ("up", "down") else "#s"} for d in ("north", "south", "east", "west", "up", "down")}}]})
    write_json(f"{A}/blockstates/landing_gear.json", {"variants": {"extended=true": {"model": f"{NS}:block/landing_gear_extended"},
                                                                  "extended=false": {"model": f"{NS}:block/landing_gear_retracted"}}})
    # seat
    write_json(f"{A}/models/block/passenger_seat.json", {
        "parent": "minecraft:block/block", "textures": {"particle": f"{NS}:block/passenger_seat", "c": f"{NS}:block/passenger_seat", "m": f"{NS}:block/ship_side"},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 8, 15], "faces": {d: {"texture": "#c" if d == "up" else "#m"} for d in ("north", "south", "east", "west", "up", "down")}},
            {"from": [1, 8, 13], "to": [15, 20, 15], "faces": {d: {"texture": "#c"} for d in ("north", "south", "east", "west", "up", "down")}}]})
    write_json(f"{A}/models/block/passenger_seat_collapsed.json", {
        "parent": "minecraft:block/block", "textures": {"particle": f"{NS}:block/passenger_seat", "c": f"{NS}:block/passenger_seat", "m": f"{NS}:block/ship_side"},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 3, 15], "faces": {d: {"texture": "#c" if d == "up" else "#m"} for d in ("north", "south", "east", "west", "up", "down")}},
            {"from": [1, 3, 13], "to": [15, 5, 15], "faces": {d: {"texture": "#c"} for d in ("north", "south", "east", "west", "up", "down")}}]})
    write_json(f"{A}/blockstates/passenger_seat.json", {"variants": {
        f"collapsed={str(c).lower()},facing={f}": {"model": f"{NS}:block/passenger_seat" + ("_collapsed" if c else ""), **({"y": r} if r else {})}
        for c in (False, True) for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    extra_blocks()
    # atmosphere: invisible, only a particle texture
    tex("block/atmosphere", (120, 180, 255), pattern="panel")
    write_json(f"{A}/models/block/atmosphere.json", {"textures": {"particle": f"{NS}:block/atmosphere"}})
    write_json(f"{A}/blockstates/atmosphere.json", {"variants": {"": {"model": f"{NS}:block/atmosphere"}}})

    item_models = {"landing_gear": "landing_gear_extended", "ship_cannon": "ship_cannon_3"}
    for b in ["landing_gear", "control_panel", "ship_generator", "life_support", "ship_thruster", "ship_cannon", "passenger_seat", "hull_plating", "gunsmith_table",
              "wheel", "vehicle_pad"] + [f"{m}_block" for m in STORAGE]:
        model = f"{NS}:block/{item_models.get(b, b)}"
        write_json(f"{A}/items/{b}.json", {"model": {"type": "minecraft:model", "model": model}})
        write_json(f"{D}/{NS}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}],
                                                                                                    "conditions": [{"condition": "minecraft:survives_explosion"}]}],
                                                              "random_sequence": f"{NS}:blocks/{b}"})


STORAGE = ["alien_alloy", "plasma_core", "nether_cinder", "void_shard", "crystal_lattice", "bio_gel", "cryo_crystal", "solar_cell", "hydro_pearl",
           "tech_chip", "sonic_resonator"]
ORES = STORAGE[1:]


def cube(d_from, d_to, faces):
    return {"from": d_from, "to": d_to, "faces": faces}


def extra_blocks():
    """Cannon (6 facings x 4 barrel sizes), wheel, vehicle pad, alien ores and material blocks."""
    # ---- cannon: the barrel points north in the model; blockstates rotate it to the six facings
    for size, t in enumerate((4, 8, 12, 16)):
        lo, hi = (16 - t) / 2, 16 - (16 - t) / 2
        els = [cube([lo, lo, 0], [hi, hi, 16], {"north": {"texture": "#front"}, "south": {"texture": "#back"},
                                                 **{d: {"texture": "#side"} for d in ("east", "west", "up", "down")}})]
        if size < 3:
            # mounting plate at the back
            els.append(cube([2, 2, 14], [14, 14, 16], {d: {"texture": "#back"} for d in ("north", "south", "east", "west", "up", "down")}))
        write_json(f"{A}/models/block/ship_cannon_{size}.json", {"parent": "minecraft:block/block", "textures": {
            "particle": f"{NS}:block/ship_side", "front": f"{NS}:block/ship_cannon_front", "side": f"{NS}:block/ship_side", "back": f"{NS}:block/ship_top"},
            "elements": els})
    rot = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    write_json(f"{A}/blockstates/ship_cannon.json", {"variants": {f"facing={f},size={sz}": {"model": f"{NS}:block/ship_cannon_{sz}", **r}
                                                                  for f, r in rot.items() for sz in range(4)}})
    # ---- wheel: tyre with hub; axle along x by default
    tex("block/wheel_tread", (34, 34, 38), pattern="grate")
    tex("block/wheel_hub", (150, 154, 162), accent=(90, 90, 96), pattern="rivets")
    write_json(f"{A}/models/block/wheel.json", {"parent": "minecraft:block/block", "textures": {
        "particle": f"{NS}:block/wheel_tread", "tread": f"{NS}:block/wheel_tread", "hub": f"{NS}:block/wheel_hub"},
        "elements": [cube([0, 3, 1], [16, 13, 15], {"east": {"texture": "#hub"}, "west": {"texture": "#hub"},
                                                     **{d: {"texture": "#tread"} for d in ("north", "south", "up", "down")}}),
                     cube([0, 0, 4], [16, 16, 12], {"east": {"texture": "#hub"}, "west": {"texture": "#hub"},
                                                     **{d: {"texture": "#tread"} for d in ("north", "south", "up", "down")}})]})
    write_json(f"{A}/blockstates/wheel.json", {"variants": {"axis=x": {"model": f"{NS}:block/wheel"}, "axis=z": {"model": f"{NS}:block/wheel", "y": 90}}})
    # ---- vehicle pad
    tex("block/vehicle_pad", (70, 74, 80), accent=(240, 200, 40), pattern="rivets")
    tex("block/vehicle_pad_top", (64, 68, 74), accent=(240, 200, 40), pattern="screen")
    write_json(f"{A}/models/block/vehicle_pad.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"{NS}:block/vehicle_pad_top", "bottom": f"{NS}:block/vehicle_pad", "side": f"{NS}:block/vehicle_pad"}})
    write_json(f"{A}/blockstates/vehicle_pad.json", {"variants": {"": {"model": f"{NS}:block/vehicle_pad"}}})
    # ---- alien material blocks (also the textures guns use for these materials)
    for m in STORAGE:
        color = ALIEN[m][0]
        tex(f"block/{m}_block", color, accent=shade(color, 1.35)[:3], pattern="glow" if ALIEN[m][1] in ("orb", "crystal") else "panel")
        write_json(f"{A}/models/block/{m}_block.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{m}_block"}})
        write_json(f"{A}/blockstates/{m}_block.json", {"variants": {"": {"model": f"{NS}:block/{m}_block"}}})
    # ---- alien ores: grey stone (tinted to the world's base stone in game) + coloured, untinted specks
    rng = random.Random(42)
    stone = []
    for y in range(16):
        for x in range(16):
            v = int(150 + rng.random() * 50)
            stone.append((v, v, v, 255))
    png(os.path.join(A, "textures", "block", "alien_ore_stone.png"), 16, 16, stone)
    for m in ORES:
        color = ALIEN[m][0]
        r2 = random.Random(m)
        px = [(0, 0, 0, 0)] * 256
        for _ in range(6):
            cx, cy = r2.randint(2, 13), r2.randint(2, 13)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0)):
                if r2.random() < 0.85:
                    px[(cy + dy) % 16 * 16 + (cx + dx) % 16] = shade(color + (255,), 0.85 + r2.random() * 0.4)
        png(os.path.join(A, "textures", "block", f"{m}_ore_overlay.png"), 16, 16, px)
        faces = ("north", "south", "east", "west", "up", "down")
        write_json(f"{A}/models/block/{m}_ore.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                     "textures": {"particle": f"{NS}:block/alien_ore_stone", "stone": f"{NS}:block/alien_ore_stone",
                                                                  "overlay": f"{NS}:block/{m}_ore_overlay"},
                                                     "elements": [cube([0, 0, 0], [16, 16, 16], {d: {"texture": "#stone", "tintindex": 0, "cullface": d} for d in faces}),
                                                                  cube([0, 0, 0], [16, 16, 16], {d: {"texture": "#overlay", "cullface": d} for d in faces})]})
        write_json(f"{A}/blockstates/{m}_ore.json", {"variants": {"": {"model": f"{NS}:block/{m}_ore"}}})
        write_json(f"{A}/items/{m}_ore.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{m}_ore",
                                                         "tints": [{"type": "minecraft:constant", "value": -7368817}]}})
        write_json(f"{D}/{NS}/loot_table/blocks/{m}_ore.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
            "type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"{NS}:{m}_ore"},
                {"type": "minecraft:item", "name": f"{NS}:{m}", "functions": [
                    {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                    {"function": "minecraft:explosion_decay"}]}]}]}], "random_sequence": f"{NS}:blocks/{m}_ore"})


# ---------------------------------------------------------------- items
ALIEN = {"alien_alloy": ((125, 142, 168), "ingot"), "plasma_core": ((57, 198, 255), "orb"), "nether_cinder": ((212, 64, 28), "orb"),
         "void_shard": ((60, 20, 100), "crystal"), "crystal_lattice": ((233, 140, 255), "crystal"), "bio_gel": ((111, 209, 43), "orb"),
         "cryo_crystal": ((166, 228, 255), "crystal"), "solar_cell": ((255, 179, 0), "chip"), "hydro_pearl": ((31, 111, 209), "orb"),
         "tech_chip": ((60, 240, 160), "chip"), "sonic_resonator": ((15, 143, 143), "crystal")}


def items():
    for name, (color, shape) in ALIEN.items():
        item_icon(name, color + (255,), shape)
        write_json(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        write_json(f"{A}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    item_icon("magazine", (90, 92, 100, 255), "magazine")
    write_json(f"{A}/models/item/magazine.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/magazine"}})
    write_json(f"{A}/items/magazine.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/magazine"}})
    item_icon("race_scanner", (70, 200, 230, 255), "scanner")
    write_json(f"{A}/models/item/race_scanner.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/race_scanner"}})
    write_json(f"{A}/items/race_scanner.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/race_scanner"}})
    item_icon("alien_spawn_egg", (60, 200, 120, 255), "egg")
    write_json(f"{A}/models/item/alien_spawn_egg.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/alien_spawn_egg"}})
    write_json(f"{A}/items/alien_spawn_egg.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/alien_spawn_egg"}})


# ---------------------------------------------------------------- guns
# Gun parts are covered with the baked model of their material's block (gold block for gold...), so resource packs and
# mods that change how the block looks change the gun too.
MATERIAL_BLOCK = {
    "iron": "minecraft:iron_block", "gold": "minecraft:gold_block", "diamond": "minecraft:diamond_block",
    "emerald": "minecraft:emerald_block", "netherite": "minecraft:netherite_block", "copper": "minecraft:copper_block",
    "amethyst": "minecraft:amethyst_block", "obsidian": "minecraft:obsidian", "quartz": "minecraft:quartz_block",
    "prismarine": "minecraft:prismarine", "blaze": "minecraft:magma_block", "echo": "minecraft:sculk",
    "alien_alloy": f"{NS}:alien_alloy_block", "plasma": f"{NS}:plasma_core_block", "nether_cinder": f"{NS}:nether_cinder_block",
    "void": f"{NS}:void_shard_block", "crystal": f"{NS}:crystal_lattice_block", "bio": f"{NS}:bio_gel_block",
    "cryo": f"{NS}:cryo_crystal_block", "solar": f"{NS}:solar_cell_block", "hydro": f"{NS}:hydro_pearl_block",
    "tech": f"{NS}:tech_chip_block", "sonic": f"{NS}:sonic_resonator_block",
}
GUN_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 2.5, 1.5], "scale": [0.75, 0.75, 0.75]},
    # Left hand entries are mirrored by the game (y/z rotation and x translation negated), so these are written
    # pre-mirrored: the barrel points forward in either hand.
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 2.5, 1.5], "scale": [0.75, 0.75, 0.75]},
    "firstperson_righthand": {"rotation": [0, 95, 5], "translation": [1.5, 3, -2], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, -95, -5], "translation": [1.5, 3, -2], "scale": [0.8, 0.8, 0.8]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [0, 0, -30], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def box(fr, to, tex="#metal", tint=0):
    return {"from": fr, "to": to, "faces": {d: {"texture": tex, "tintindex": tint, "uv": [0, 0, max(1, to[0] - fr[0]) if d in ("north", "south", "up", "down") else max(1, to[2] - fr[2]),
                                                                                       max(1, to[1] - fr[1]) if d not in ("up", "down") else max(1, to[2] - fr[2])]}
                                         for d in ("north", "south", "east", "west", "up", "down")}}


def clampbox(fr, to):
    return [max(-16, min(32, v)) for v in fr], [max(-16, min(32, v)) for v in to]


def part_boxes(pid, slot, alien):
    """Cuboids (in a 16-unit item space, gun pointing to +X) for a part, varied by keywords in its id."""
    long_ = "long" in pid or "heavy" in pid or "emitter" in pid
    short = "short" in pid
    thick = 1 if ("heavy" in pid or "drum" in pid or "lmg" in pid) else 0
    b = []
    if slot == "receiver":
        b.append(box([3, 6, 6.5], [10, 10 + thick, 9.5]))
        if alien:
            b.append(box([4, 9.5, 7], [9, 11, 9]))
    elif slot == "barrel":
        end = 15 if short else 20 if long_ else 17
        b.append(box([10, 8, 7.25 - thick / 2], [end, 9.5 + thick / 2, 8.75 + thick / 2]))
        if "fluted" in pid or "lens" in pid:
            b.append(box([end - 3, 7.5, 7], [end - 1, 10, 9]))
    elif slot == "stock":
        b.append(box([-2 if "precision" in pid else -1, 6, 7], [3, 9 if "light" in pid else 10, 9]))
    elif slot == "grip":
        b.append(box([4.5, 3, 7.25], [6.5, 6, 8.75]) if "angled" not in pid else box([10.5, 4.5, 7.25], [12.5, 8, 8.75]))
    elif slot == "magazine":
        h = 2 if "speed" in pid else 4 if "extended" in pid else 3
        if "drum" in pid:
            b.append(box([6.5, 3, 6.5], [9.5, 6, 9.5]))
        else:
            b.append(box([7, 6 - h, 7.25], [8.75, 6, 8.75]))
    elif slot == "sight":
        if "scope" in pid:
            ln = 6 if "sniper" in pid else 4
            b.append(box([5, 10.5, 7.25], [5 + ln, 12, 8.75]))
        else:
            b.append(box([6, 10, 7.5], [8, 11, 8.5]))
    elif slot == "muzzle":
        b.append(box([20, 7.5, 6.75], [22.5, 10, 9.25]) if "suppressor" in pid else box([20, 7.75, 7], [21.5, 9.75, 9]))
    elif slot == "underbarrel":
        if "grenade" in pid:
            b.append(box([11, 5.5, 7], [16, 7.5, 9]))
        elif "bayonet" in pid:
            b.append(box([16, 7, 7.75], [21, 8, 8.25]))
        else:
            b.append(box([11, 6.5, 7.5], [13, 7.5, 8.5]))
    elif slot == "core":
        b.append(box([5, 8, 5.5], [9, 11, 10.5]))
        b.append(box([6, 11, 6.5], [8, 12, 9.5]))
    return b


def guns():
    # grayscale metal textures, tinted by the material colour
    tex("item/gun/metal", (200, 200, 205), pattern="panel")
    tex("item/gun/alien", (200, 200, 205), accent=(255, 255, 255), pattern="glow")
    tex("item/gun/trim", (230, 230, 230), pattern="grate")
    base = {"textures": {"metal": f"{NS}:item/gun/metal", "particle": f"{NS}:item/gun/metal"}, "display": GUN_DISPLAY}
    write_json(f"{A}/models/item/gun/base.json", {**base, "elements": [box([3, 6, 6.5], [10, 10, 9.5])]})

    part_dir = os.path.join(D, NS, "gun", "part")
    arch_dir = os.path.join(D, NS, "gun", "archetype")
    parts = {f[:-5]: json.load(open(os.path.join(part_dir, f))) for f in sorted(os.listdir(part_dir))}
    archs = {f[:-5]: json.load(open(os.path.join(arch_dir, f))) for f in sorted(os.listdir(arch_dir))}
    for pid, p in parts.items():
        texture = f"{NS}:item/gun/alien" if p.get("alien") else f"{NS}:item/gun/metal"
        write_json(f"{A}/models/item/gun/part/{pid}.json", {"textures": {"metal": texture, "particle": texture}, "display": GUN_DISPLAY,
                                                          "elements": part_boxes(pid, p["slot"], p.get("alien", False))})
    # Frame per archetype: a small body that gives each family a silhouette even when parts are missing.
    for aid, a in archs.items():
        texture = f"{NS}:item/gun/alien" if a.get("alien") else f"{NS}:item/gun/metal"
        length = {"pistol": 2, "hand_cannon": 3, "smg": 4, "shotgun": 6, "lmg": 7, "sniper_rifle": 7, "rocket_launcher": 8}.get(aid, 5)
        els = [box([3, 7, 7], [3 + length, 9, 9])]
        if aid in ("rocket_launcher", "fuel_rod_cannon"):
            els = [box([0, 7, 6], [16, 11, 10])]
        write_json(f"{A}/models/item/gun/frame/{aid}.json", {"textures": {"metal": texture, "particle": texture}, "display": GUN_DISPLAY, "elements": els})
    write_json(f"{A}/models/item/gun/trim.json", {"textures": {"metal": f"{NS}:item/gun/trim", "particle": f"{NS}:item/gun/trim"}, "display": GUN_DISPLAY,
                                                  "elements": [box([3.5, 9.9, 6.4], [9.5, 10.2, 9.6]), box([3.5, 7, 6.3], [9.5, 7.5, 6.5])]})

    mat_dir = os.path.join(D, NS, "gun", "material")
    materials = sorted(f[:-5] for f in os.listdir(mat_dir))
    for mid in materials:
        if mid not in MATERIAL_BLOCK:
            raise SystemExit(f"material {mid} has no block in MATERIAL_BLOCK")
    for kind in ("part_material", "frame_material"):  # replaced by interstellar:block_textured item models
        shutil.rmtree(f"{A}/models/item/gun/{kind}", ignore_errors=True)
    index = ["assets/interstellar/models/item/gun/base.json", "assets/interstellar/models/item/gun/trim.json"]
    index += [f"assets/interstellar/models/item/gun/part/{pid}.json" for pid in parts]
    index += [f"assets/interstellar/models/item/gun/frame/{aid}.json" for aid in archs]
    with open(f"{A}/gunmodels_index.txt", "w") as f:
        f.write("\n".join(index) + "\n")

    def by_material(kind, pid, slot):
        """Model for a part made of a known material (block textured), tinted fallback for unknown materials."""
        return {"type": "minecraft:select", "property": f"{NS}:gun_material", "slot": slot,
                "cases": [{"when": mid, "model": {"type": f"{NS}:block_textured", "model": f"{NS}:item/gun/{kind}/{pid}", "block": MATERIAL_BLOCK[mid]}}
                          for mid in materials],
                "fallback": tinted(f"{NS}:item/gun/{kind}/{pid}", slot)}

    def tinted(model, slot=None, trim=False):
        t = {"type": f"{NS}:gun_material", "default": 0xFFFFFF}
        if slot:
            t["slot"] = slot
        if trim:
            t["trim"] = True
        return {"type": "minecraft:model", "model": model, "tints": [t]}

    layers = [{"type": "minecraft:select", "property": f"{NS}:gun_info", "field": "archetype",
               "cases": [{"when": aid, "model": by_material("frame", aid, "receiver")} for aid in archs],
               "fallback": {"type": "minecraft:model", "model": f"{NS}:item/gun/base"}}]
    for slot in ["receiver", "barrel", "stock", "grip", "magazine", "sight", "muzzle", "underbarrel", "core"]:
        cases = [{"when": pid, "model": by_material("part", pid, slot)} for pid, p in parts.items() if p["slot"] == slot]
        if cases:
            layers.append({"type": "minecraft:select", "property": f"{NS}:gun_part", "slot": slot, "cases": cases, "fallback": {"type": "minecraft:empty"}})
    patterns = ["sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib", "spire", "wayfinder", "shaper", "silence", "raiser",
                "host", "flow", "bolt"]
    layers.append({"type": "minecraft:select", "property": f"{NS}:gun_info", "field": "trim",
                   "cases": [{"when": patterns, "model": tinted(f"{NS}:item/gun/trim", trim=True)}], "fallback": {"type": "minecraft:empty"}})
    # Written compactly: one select case per part and material makes this file large.
    os.makedirs(f"{A}/items", exist_ok=True)
    with open(f"{A}/items/gun.json", "w") as f:
        json.dump({"model": {"type": "minecraft:composite", "models": layers}}, f, separators=(",", ":"))


# ---------------------------------------------------------------- entity
def entity():
    # Default alien skin (aliens normally wear their race's generated skin instead).
    rng = random.Random(7)
    px = []
    for y in range(64):
        for x in range(64):
            c = (90, 170, 110, 255) if y < 16 or x >= 32 or y >= 48 else (70, 70, 90, 255)
            px.append(shade(c, 0.9 + rng.random() * 0.2))
    for x in range(9, 15, 4):
        px[12 * 64 + x] = (10, 10, 10, 255)
    png(os.path.join(A, "textures", "entity", "alien.png"), 64, 64, px)


# ---------------------------------------------------------------- data
def data():
    blocks_ = ["landing_gear", "control_panel", "ship_generator", "life_support", "ship_thruster", "ship_cannon", "passenger_seat", "hull_plating", "gunsmith_table"]
    blocks_ += ["wheel", "vehicle_pad"] + [f"{m}_block" for m in STORAGE] + [f"{m}_ore" for m in ORES]
    write_json(f"{D}/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": [f"{NS}:{b}" for b in blocks_]})
    write_json(f"{D}/minecraft/tags/block/needs_iron_tool.json", {"replace": False, "values": [f"{NS}:{m}_ore" for m in ORES]})
    write_json(f"{D}/{NS}/tags/block/ship/wheels.json", {"values": [f"{NS}:wheel"]})
    write_json(f"{D}/{NS}/tags/block/ship/vehicle_pads.json", {"values": [f"{NS}:vehicle_pad"]})
    write_json(f"{D}/{NS}/tags/block/ship/landing_gear.json", {"values": [f"{NS}:landing_gear"]})
    write_json(f"{D}/{NS}/tags/block/ship/control_panels.json", {"values": [f"{NS}:control_panel"]})
    write_json(f"{D}/{NS}/tags/block/ship/generators.json", {"values": [f"{NS}:ship_generator"]})
    write_json(f"{D}/{NS}/tags/block/ship/life_support.json", {"values": [f"{NS}:life_support"]})
    write_json(f"{D}/{NS}/tags/block/ship/seats.json", {"values": [f"{NS}:passenger_seat"]})
    write_json(f"{D}/{NS}/tags/block/ship/weapons.json", {"values": [f"{NS}:ship_cannon"]})
    write_json(f"{D}/{NS}/tags/block/ship/thrusters.json", {"values": [f"{NS}:ship_thruster"]})
    write_json(f"{D}/{NS}/tags/block/ship/unmovable.json", {"values": ["minecraft:bedrock", "minecraft:nether_portal", "minecraft:end_portal",
                                                                      "minecraft:end_portal_frame", "minecraft:end_gateway", "minecraft:barrier",
                                                                      "minecraft:command_block", "minecraft:structure_block", "minecraft:reinforced_deepslate",
                                                                      f"{NS}:atmosphere"]})
    bipeds = ["player", "mannequin", "villager", "wandering_trader", "zombie", "husk", "drowned", "zombie_villager", "giant", "skeleton", "stray",
              "bogged", "parched", "wither_skeleton", "piglin", "piglin_brute", "zombified_piglin", "pillager", "vindicator", "evoker", "illusioner",
              "witch", "enderman"]
    write_json(f"{D}/{NS}/tags/entity_type/bipeds.json", {"values": [f"minecraft:{b}" for b in bipeds] + [f"{NS}:alien"]})

    def shaped(name, pattern, key, count=1):
        write_json(f"{D}/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
                                                    "result": {"id": f"{NS}:{name}", "count": count}})
    # Spacecraft parts are late-game: built from blocks of metal, gems and rare drops.
    shaped("hull_plating", ["IOI", "OCO", "IOI"], {"I": "minecraft:iron_block", "O": "minecraft:obsidian", "C": "minecraft:copper_block"}, 8)
    shaped("landing_gear", ["III", "PSP", "A A"], {"I": "minecraft:iron_block", "P": "minecraft:sticky_piston", "S": "minecraft:slime_block",
                                                   "A": "minecraft:anvil"}, 2)
    shaped("control_panel", ["GDG", "CXC", "IRI"], {"G": "minecraft:tinted_glass", "D": "minecraft:diamond_block", "C": "minecraft:comparator",
                                                    "X": "minecraft:recovery_compass", "I": "minecraft:iron_block", "R": "minecraft:redstone_block"})
    shaped("ship_generator", ["NBN", "RSR", "NBN"], {"N": "minecraft:netherite_scrap", "B": "minecraft:blaze_rod", "R": "minecraft:redstone_block",
                                                     "S": "minecraft:nether_star"})
    shaped("life_support", ["GTG", "OCO", "IDI"], {"G": "minecraft:tinted_glass", "T": "minecraft:totem_of_undying", "O": "minecraft:oxidized_copper",
                                                   "C": "minecraft:conduit", "I": "minecraft:iron_block", "D": "minecraft:diamond_block"})
    shaped("ship_thruster", ["IFI", "DGD", "BRB"], {"I": "minecraft:iron_block", "F": "minecraft:fire_charge", "D": "minecraft:diamond",
                                                    "G": "minecraft:ghast_tear", "B": "minecraft:blast_furnace", "R": "minecraft:redstone_block"})
    shaped("ship_cannon", ["IOI", "TDT", "IOI"], {"I": "minecraft:iron_block", "O": "minecraft:observer", "T": "minecraft:tnt", "D": "minecraft:dispenser"})
    shaped("passenger_seat", ["WL ", "WWW", "I I"], {"W": "#minecraft:wool", "L": "minecraft:leather", "I": "minecraft:iron_block"})
    shaped("wheel", [" K ", "KIK", " K "], {"K": "minecraft:dried_kelp_block", "I": "minecraft:iron_block"}, 2)
    shaped("vehicle_pad", ["YYY", "SSS", "III"], {"Y": "minecraft:yellow_concrete", "S": "minecraft:smooth_stone", "I": "minecraft:iron_ingot"}, 6)
    shaped("race_scanner", [" S ", "IGI", "IRI"], {"S": "minecraft:spyglass", "I": "minecraft:iron_ingot", "G": "minecraft:glass_pane",
                                                   "R": "minecraft:redstone"})
    # Alien material storage blocks, both ways.
    for m in STORAGE:
        shaped(f"{m}_block", ["MMM", "MMM", "MMM"], {"M": f"{NS}:{m}"})
        write_json(f"{D}/{NS}/recipe/{m}_from_block.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                            "ingredients": [f"{NS}:{m}_block"], "result": {"id": f"{NS}:{m}", "count": 9}})
    shaped("gunsmith_table", ["SNS", "IAI", "III"], {"S": "minecraft:smithing_table", "N": "minecraft:netherite_ingot", "I": "minecraft:iron_block",
                                                     "A": "minecraft:anvil"})


# ---------------------------------------------------------------- lang
EXTRA_LANG = {
    "block.interstellar.wheel": "Wheel", "block.interstellar.vehicle_pad": "Vehicle Pad",
    "item.interstellar.race_scanner": "Race Scanner",
    "command.interstellar.body.own": "Your body is now %s (saved for every world)",
    "message.interstellar.scanner.header": "Scan of %s", "message.interstellar.scanner.race": "Race: ",
    "message.interstellar.scanner.favorite": "Favorite item: ", "message.interstellar.scanner.home": "Home dimension: ",
    "message.interstellar.scanner.none": "No race data.", "message.interstellar.scanner.nothing": "nothing",
    "message.interstellar.scanner.anywhere": "anywhere",
    "message.interstellar.cannon.occupied": "This cannon is already loaded with %s",
    "message.interstellar.cannon.loaded": "Cannon loaded: %s x%s",
    "message.interstellar.cannon.holds": "Loaded with %s x%s (shift-right-click with an empty hand to unload)",
    "message.interstellar.seat.collapsed": "Seat folded away", "message.interstellar.seat.opened": "Seat unfolded",
    "message.interstellar.ship.problem.not_on_pads": "A land vehicle can only be assembled on vehicle pads: every wheel must stand on one",
    "message.interstellar.ship.problem.not_on_wheels": "The vehicle must be built on top of its wheels",
    "message.interstellar.ship.problem.gear_and_wheels": "A craft cannot have both landing gear and wheels",
    "message.interstellar.ship.drive": "Vehicle ready (%s blocks): forward/back to drive, look to steer, get out over vehicle pads to park",
    "command.interstellar.ship.saved": "Saved starship design '%s' (%s blocks)",
    "command.interstellar.ship.no_ship": "Pilot a spacecraft or look at a control panel to save its design",
    "command.interstellar.ship.invalid": "That craft is not a valid spacecraft",
    "command.interstellar.ship.list": "Starship designs: %s", "command.interstellar.ship.unknown": "Unknown starship design %s",
    "command.interstellar.ship.spawned": "Spawned an alien %s",
}


def lang():
    L = {
        "itemGroup.interstellar": "Interstellar",
        "block.interstellar.landing_gear": "Landing Gear", "block.interstellar.control_panel": "Control Panel",
        "block.interstellar.ship_generator": "Ship Generator", "block.interstellar.life_support": "Life Support",
        "block.interstellar.ship_thruster": "Thruster", "block.interstellar.ship_cannon": "Ship Cannon",
        "block.interstellar.passenger_seat": "Passenger Seat", "block.interstellar.hull_plating": "Hull Plating",
        "block.interstellar.gunsmith_table": "Gunsmith Table", "block.interstellar.atmosphere": "Atmosphere",
        "item.interstellar.gun": "Gun", "item.interstellar.magazine": "Magazine", "item.interstellar.magazine.kind": "%s Magazine",
        "item.interstellar.alien_spawn_egg": "Alien Spawn Egg",
        "entity.interstellar.alien": "Alien", "entity.interstellar.spacecraft": "Spacecraft", "entity.interstellar.seat": "Seat",
        "entity.interstellar.gun_projectile": "Energy Bolt",
        "container.interstellar.gunsmith": "Gunsmith Table",
        "key.interstellar.reload": "Reload Gun", "key.category.interstellar.keys": "Interstellar",
        "message.interstellar.ship.invalid": "This spacecraft cannot fly:",
        "message.interstellar.ship.problem.too_many_blocks": "More than %s blocks (or the ship touches the ground or other builds)",
        "message.interstellar.ship.problem.no_generator": "Needs a generator",
        "message.interstellar.ship.problem.no_control_panel": "Needs a control panel",
        "message.interstellar.ship.problem.no_life_support": "Needs life support",
        "message.interstellar.ship.problem.no_landing_gear": "Needs landing gear",
        "message.interstellar.ship.problem.not_on_landing_gear": "The lowest layer must be landing gear",
        "message.interstellar.ship.problem.unmovable_block": "Contains a block that cannot be moved",
        "message.interstellar.ship.launch": "Launching (%s blocks). Hold use to boost, attack to fire, sneak to halt.",
        "message.interstellar.ship.landing": "Landing procedure engaged (%s blocks).",
        "message.interstellar.ship.obstructed": "Something is in the way - cannot settle here.",
        "message.interstellar.ship.new_pilot": "You now pilot the spacecraft.",
        "message.interstellar.space.entered": "Entering space",
        "message.interstellar.space.arrived": "Entering the atmosphere of %s",
        "message.interstellar.space.approaching": "Approaching %s...",
        "command.interstellar.planets.nearest": "Nearest planet: %s (%s) at %s %s %s, %s terrain, settled by the %s",
        "command.interstellar.planets.visiting": "Travelling to %s",
        "message.interstellar.space.no_dismount": "You cannot leave the spacecraft in space.",
        "message.interstellar.space.inventory_saved": "Your inventory was saved. An operator can restore it with /interstellar spaceinventory restore.",
        "message.interstellar.race.recruited": "%s will now follow you.",
        "message.interstellar.race.affection": "%s likes that (%s%%).",
        "message.interstellar.race.already_recruited": "%s is already with you.",
        "message.interstellar.gun.incomplete": "This gun is missing required parts.",
        "message.interstellar.gun.no_magazine": "No loaded %s magazine.",
        "message.interstellar.gunsmith.missing": "Missing %s x %s",
        "tooltip.interstellar.gun.ammo": "Ammo: %s / %s (%s)", "tooltip.interstellar.gun.empty": "An unfinished gun frame",
        "tooltip.interstellar.magazine.rounds": "%s / %s rounds",
        "stat.interstellar.gun.damage": "Damage", "stat.interstellar.gun.fire_rate": "Fire rate", "stat.interstellar.gun.range": "Range",
        "stat.interstellar.gun.reload_time": "Reload", "stat.interstellar.gun.spread": "Spread",
        "screen.interstellar.dismount.title": "Leave the spacecraft?",
        "screen.interstellar.dismount.warning": "You will be removed from the spacecraft while it is flying.",
        "screen.interstellar.dismount.leave": "Leave", "screen.interstellar.dismount.stay": "Stay aboard",
        "screen.interstellar.gunsmith.frame": "New frame", "screen.interstellar.gunsmith.remove": "Remove",
        "screen.interstellar.gunsmith.trim": "Apply trim", "screen.interstellar.gunsmith.trim.tooltip": "Trim template + trim material (material slot)",
        "screen.interstellar.gunsmith.magazine": "Craft %s magazine", "screen.interstellar.gunsmith.cost": "Cost:",
        "screen.interstellar.gunsmith.gun": "Gun", "screen.interstellar.gunsmith.material": "Material / Template",
        "command.interstellar.race.reloaded": "Reloaded %s races", "command.interstellar.race.unknown": "Unknown race",
        "command.interstellar.race.set": "Set the race of %s entities to %s", "command.interstellar.body.set": "Set the body of %s entities to %s",
        "command.interstellar.spaceinventory.restored": "Restored %s stacks to %s", "command.interstellar.spaceinventory.count": "%s has %s saved stacks",
        "command.interstellar.planets.rebuilt": "Rebuilt all planets", "command.interstellar.gun.unknown": "Unknown gun archetype %s",
        "interstellar.configuration.title": "Interstellar",
    }
    L.update(EXTRA_LANG)
    for m in STORAGE:
        nice = " ".join(w.capitalize() for w in m.split("_"))
        L[f"block.interstellar.{m}_block"] = f"Block of {nice}"
        if m in ORES:
            L[f"block.interstellar.{m}_ore"] = f"{nice} Ore"
    arch_dir = os.path.join(D, NS, "gun", "archetype")
    for f in sorted(os.listdir(arch_dir)):
        aid = f[:-5]
        L[f"gun.interstellar.{aid}"] = " ".join(w.capitalize() for w in aid.split("_"))
    write_json(f"{A}/lang/en_us.json", L)


if __name__ == "__main__":
    blocks()
    items()
    guns()
    entity()
    data()
    lang()
    print("assets generated")
