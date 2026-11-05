"""Generates the hull plating shapes (stairs, slab, wall, fence, fence gate, door, trapdoor, button, pane): blockstates,
block and item models, textures, loot tables, recipes and tags.

The blockstates and models follow vanilla's own (stone brick stairs/slab/wall, nether brick fence, oak fence gate,
iron door/trapdoor, stone button, glass pane), read from an extracted vanilla client jar:

    python3 tools/gen_hull_shapes.py <dir with assets/minecraft>

Blockstate keys never mention the "tilt" property, so every tilt uses the upright model; the game turns it.
"""
import json
import os
import re
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "interstellar")
D = os.path.join(ROOT, "data")
NS = "interstellar"

# shape -> (vanilla block it copies, vanilla model name prefix)
SHAPES = {
    "stairs": "stone_brick_stairs",
    "slab": "stone_brick_slab",
    "wall": "stone_brick_wall",
    "fence": "nether_brick_fence",
    "fence_gate": "oak_fence_gate",
    "door": "iron_door",
    "trapdoor": "iron_trapdoor",
    "button": "stone_button",
    "pane": "glass_pane",
}
HULL = f"{NS}:block/hull_plating"


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def model_id(shape, vanilla_id):
    """minecraft:block/stone_brick_stairs_inner -> interstellar:block/hull_plating_stairs_inner."""
    name = vanilla_id.split("/", 1)[1]
    src = SHAPES[shape]
    if shape == "slab" and name == "stone_bricks":
        return HULL
    assert name.startswith(src), (shape, name)
    return f"{NS}:block/hull_plating_{shape}{name[len(src):]}"


def texture_for(shape, slot):
    if shape == "door":
        return f"{NS}:block/hull_plating_door_{slot}" if slot in ("top", "bottom") else HULL
    if shape == "trapdoor":
        return f"{NS}:block/hull_plating_trapdoor"
    if shape == "pane" and slot == "edge":
        return f"{NS}:block/hull_plating_pane_top"
    return HULL


def textures():
    hull = Image.open(os.path.join(A, "textures", "block", "hull_plating.png")).convert("RGBA")
    base = hull.getpixel((8, 8))

    def shade(c, f):
        return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

    # door: the plating with a seam, a window up top and a handle
    for half in ("top", "bottom"):
        img = hull.copy()
        for y in range(16):
            for x in range(16):
                if x in (2, 13) or (half == "top" and y == 2) or (half == "bottom" and y == 13):
                    img.putpixel((x, y), shade(base, 0.7))
        if half == "top":
            for y in range(4, 10):
                for x in range(4, 12):
                    img.putpixel((x, y), (60, 90, 110, 255) if (x + y) % 5 else (110, 150, 170, 255))
        else:
            for y in range(6, 9):
                img.putpixel((11, y), (40, 40, 44, 255))
        img.save(os.path.join(A, "textures", "block", f"hull_plating_door_{half}.png"))
    # trapdoor: plating with a grate
    img = hull.copy()
    for y in range(16):
        for x in range(16):
            if 3 <= x <= 12 and 3 <= y <= 12 and y % 3 == 0:
                img.putpixel((x, y), shade(base, 0.55))
    img.save(os.path.join(A, "textures", "block", "hull_plating_trapdoor.png"))
    # pane edge
    img = Image.new("RGBA", (16, 16), shade(base, 0.85))
    img.save(os.path.join(A, "textures", "block", "hull_plating_pane_top.png"))
    # door item: the door's two halves squeezed into one icon
    top = Image.open(os.path.join(A, "textures", "block", "hull_plating_door_top.png"))
    bottom = Image.open(os.path.join(A, "textures", "block", "hull_plating_door_bottom.png"))
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    door = Image.new("RGBA", (16, 32))
    door.paste(top, (0, 0))
    door.paste(bottom, (0, 16))
    icon.paste(door.resize((8, 16), Image.NEAREST), (4, 0))
    os.makedirs(os.path.join(A, "textures", "item"), exist_ok=True)
    icon.save(os.path.join(A, "textures", "item", "hull_plating_door.png"))


def main(vanilla):
    M = os.path.join(vanilla, "assets", "minecraft")
    textures()
    for shape, src in SHAPES.items():
        name = f"hull_plating_{shape}"
        state = json.load(open(os.path.join(M, "blockstates", f"{src}.json")))
        text = json.dumps(state)
        used = sorted(set(re.findall(r"minecraft:block/[a-z_]+", text)))
        for vid in used:
            mine = model_id(shape, vid)
            text = text.replace(f'"{vid}"', f'"{mine}"')
            if mine == HULL:
                continue
            model = json.load(open(os.path.join(M, "models", "block", vid.split("/", 1)[1] + ".json")))
            model["textures"] = {slot: texture_for(shape, slot) for slot in model.get("textures", {})}
            write_json(os.path.join(A, "models", "block", mine.split("/", 1)[1] + ".json"), model)
        write_json(os.path.join(A, "blockstates", f"{name}.json"), json.loads(text))
        # item model
        item = json.load(open(os.path.join(M, "items", f"{src}.json")))
        ref = item["model"]["model"]
        if ref.startswith("minecraft:item/"):
            layer = f"{NS}:item/{name}" if shape == "door" else HULL
            write_json(os.path.join(A, "models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": layer}})
            item["model"]["model"] = f"{NS}:item/{name}"
        else:
            vname = ref.split("/", 1)[1]
            if f"minecraft:block/{vname}" not in used:  # inventory models (fence, wall, button)
                model = json.load(open(os.path.join(M, "models", "block", f"{vname}.json")))
                model["textures"] = {slot: texture_for(shape, slot) for slot in model.get("textures", {})}
                write_json(os.path.join(A, "models", "block", model_id(shape, f"minecraft:block/{vname}").split("/", 1)[1] + ".json"), model)
            item["model"]["model"] = model_id(shape, f"minecraft:block/{vname}")
        write_json(os.path.join(A, "items", f"{name}.json"), item)
        loot(shape, name)
    recipes()
    tags()


def loot(shape, name):
    entry = {"type": "minecraft:item", "name": f"{NS}:{name}"}
    if shape == "slab":
        entry["functions"] = [{"function": "minecraft:set_count", "add": False, "count": 2.0,
                               "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:{name}", "properties": {"type": "double"}}]},
                              {"function": "minecraft:explosion_decay"}]
    pool = {"rolls": 1.0, "bonus_rolls": 0.0, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}
    if shape == "door":
        pool["conditions"].append({"condition": "minecraft:block_state_property", "block": f"{NS}:{name}", "properties": {"half": "lower"}})
    write_json(os.path.join(D, NS, "loot_table", "blocks", f"{name}.json"), {"type": "minecraft:block", "pools": [pool], "random_sequence": f"{NS}:blocks/{name}"})


def recipes():
    H, I = f"{NS}:hull_plating", "minecraft:iron_ingot"

    def shaped(name, pattern, key, count):
        write_json(os.path.join(D, NS, "recipe", f"{name}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "pattern": pattern,
                                                                  "key": key, "result": {"id": f"{NS}:{name}", "count": count}})

    shaped("hull_plating_stairs", ["H  ", "HH ", "HHH"], {"H": H}, 4)
    shaped("hull_plating_slab", ["HHH"], {"H": H}, 6)
    shaped("hull_plating_wall", ["HHH", "HHH"], {"H": H}, 6)
    shaped("hull_plating_fence", ["HIH", "HIH"], {"H": H, "I": I}, 6)
    shaped("hull_plating_fence_gate", ["IHI", "IHI"], {"H": H, "I": I}, 1)
    shaped("hull_plating_door", ["HH", "HH", "HH"], {"H": H}, 3)
    shaped("hull_plating_trapdoor", ["HH", "HH"], {"H": H}, 2)
    shaped("hull_plating_pane", ["HHH", "HHH"], {"H": H}, 16)
    write_json(os.path.join(D, NS, "recipe", "hull_plating_button.json"), {"type": "minecraft:crafting_shapeless", "category": "redstone",
                                                                           "ingredients": [H], "result": {"id": f"{NS}:hull_plating_button", "count": 1}})
    for shape, count in (("stairs", 1), ("slab", 2), ("wall", 1)):
        write_json(os.path.join(D, NS, "recipe", f"hull_plating_{shape}_from_stonecutting.json"),
                   {"type": "minecraft:stonecutting", "ingredient": H, "result": {"id": f"{NS}:hull_plating_{shape}", "count": count}})


def tags():
    def tag(path, values):
        p = os.path.join(D, "minecraft", "tags", "block", f"{path}.json")
        old = json.load(open(p))["values"] if os.path.exists(p) else []
        write_json(p, {"replace": False, "values": sorted(set(old) | set(values))})

    names = [f"{NS}:hull_plating_{s}" for s in SHAPES]
    tag("mineable/pickaxe", names)
    tag("stairs", [f"{NS}:hull_plating_stairs"])
    tag("slabs", [f"{NS}:hull_plating_slab"])
    tag("walls", [f"{NS}:hull_plating_wall"])
    tag("fences", [f"{NS}:hull_plating_fence"])
    tag("fence_gates", [f"{NS}:hull_plating_fence_gate"])
    tag("doors", [f"{NS}:hull_plating_door"])
    tag("trapdoors", [f"{NS}:hull_plating_trapdoor"])
    tag("buttons", [f"{NS}:hull_plating_button"])


if __name__ == "__main__":
    main(sys.argv[1])
