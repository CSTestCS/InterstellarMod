#!/usr/bin/env python3
"""Generates the datapack for space, the alien species' biomes and the planet dimension types.

Usage (from the repository root):
  javac -d build/tools -cp <compiled core classes> tools/ExportForeignWorlds.java
  java -cp build/tools:<core classes> ExportForeignWorlds > build/worlds.json
  python3 tools/gen_worlds.py <vanilla 26.3 data dir (.../data/minecraft)> build/worlds.json

The vanilla data dir is a 26.3 data extraction (for example misode/mcmeta tag 26.3-data-json). World definitions
come from ForeignWorlds.java (species, which also drives the alien races) and PlanetTypes.java (the dimension types
procedural planets are created with at runtime, see PlanetDimensions), so Java and data always agree.
"""
import copy
import json
import os
import random
import sys

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "interstellar")

TEMPLATES = {"overworld": "overworld", "amplified": "amplified", "large_biomes": "large_biomes",
             "caves": "caves", "floating_islands": "floating_islands"}

THEME_FEATURES = {
    "crystal": {2: ["minecraft:amethyst_geode"], 9: ["minecraft:glow_lichen"]},
    "magma": {2: ["minecraft:delta"], 9: ["minecraft:basalt_pillar", "minecraft:large_basalt_columns", "minecraft:patch_fire"]},
    "void": {9: ["minecraft:chorus_plant"]},
    "fungal": {9: ["minecraft:mushroom_island_vegetation", "minecraft:brown_mushroom_normal"]},
    "frozen": {2: ["minecraft:ice_spike", "minecraft:iceberg_packed"], 9: ["minecraft:blue_ice"]},
    "toxic": {9: ["minecraft:glow_lichen", "minecraft:patch_grass_jungle"]},
    "dune": {9: ["minecraft:patch_cactus_desert", "minecraft:patch_dead_bush"]},
    "reef": {9: ["minecraft:kelp_warm", "minecraft:glow_lichen"]},
    "rust": {2: ["minecraft:dripstone_cluster"], 9: ["minecraft:glow_lichen"]},
    "echo": {2: ["minecraft:large_dripstone"], 9: ["minecraft:glow_lichen"]},
    "honey": {9: ["minecraft:flower_meadow", "minecraft:patch_grass_meadow"]},
    "bone": {2: ["minecraft:fossil_upper"], 9: ["minecraft:patch_soul_fire"]},
}
ORES = ["minecraft:ore_coal_upper", "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_gold",
        "minecraft:ore_redstone", "minecraft:ore_diamond", "minecraft:ore_lapis", "minecraft:ore_copper", "minecraft:ore_emerald"]
# Alien resource ore per theme (the material its aliens drop), set in the world's base stone.
ALIEN_ORE = {"crystal": "crystal_lattice", "magma": "nether_cinder", "void": "void_shard", "fungal": "bio_gel", "frozen": "cryo_crystal",
             "toxic": "plasma_core", "dune": "solar_cell", "reef": "hydro_pearl", "rust": "tech_chip", "echo": "sonic_resonator",
             "honey": "bio_gel", "bone": "void_shard"}
# Metals every foreign world has, embedded in its own base stone: (ore block, vein size, veins per chunk, max height).
WORLD_ORES = [("iron", "minecraft:iron_ore", 9, 12, 96), ("gold", "minecraft:gold_ore", 9, 4, 40), ("copper", "minecraft:copper_ore", 10, 10, 112),
              ("diamond", "minecraft:diamond_ore", 6, 2, 16)]
CREATURES = ["minecraft:sheep", "minecraft:pig", "minecraft:cow", "minecraft:chicken", "minecraft:goat", "minecraft:frog",
             "minecraft:armadillo", "minecraft:camel", "minecraft:rabbit", "minecraft:mooshroom", "minecraft:sniffer", "minecraft:llama"]
MONSTERS = ["minecraft:spider", "minecraft:creeper", "minecraft:slime", "minecraft:enderman", "minecraft:phantom", "minecraft:silverfish"]


def load(vanilla, *path):
    with open(os.path.join(vanilla, *path)) as f:
        return json.load(f)


def write(rel, data):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def placed_exists(vanilla, fid):
    return os.path.exists(os.path.join(vanilla, "worldgen", "placed_feature", fid.split(":")[1] + ".json"))


def spawns(monsters, creatures):
    return {"minecraft:gameplay/natural_mob_spawns": {"modifier": "overlay", "argument": {"spawn_costs": {}, "spawns_by_category": {
        "monster": monsters, "creature": creatures, "ambient": [], "axolotls": [], "misc": [],
        "underground_water_creature": [], "water_ambient": [], "water_creature": []}}}}


def material_rule(w, rng, floating):
    seq = []
    if not floating:
        seq.append("minecraft:bedrock_floor")
    patches = []
    lo = rng.uniform(-0.6, 0.3)
    patches.append({"type": "minecraft:condition",
                    "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": lo, "max_threshold": lo + 0.25},
                    "then_run": {"type": "minecraft:block", "result_state": w["accent"]}})
    seq.append({"type": "minecraft:condition", "if_true": "minecraft:on_floor",
                "then_run": {"type": "minecraft:sequence", "sequence": patches + [{"type": "minecraft:block", "result_state": w["top"]}]}})
    seq.append({"type": "minecraft:condition", "if_true": "minecraft:under_floor",
                "then_run": {"type": "minecraft:block", "result_state": w["under"]}})
    return {"type": "minecraft:sequence", "sequence": seq}


def main(vanilla, worlds_file):
    with open(worlds_file) as f:
        exported = json.load(f)
    worlds = exported["worlds"]
    # Species used to be dimensions of their own; procedural planets replaced them.
    for kind in ("dimension", "dimension_type", "worldgen/noise_settings", "worldgen/material_rule"):
        folder = os.path.join(OUT, kind)
        for name in (os.listdir(folder) if os.path.isdir(folder) else []):
            if name.startswith("foreign_"):
                os.remove(os.path.join(folder, name))
    overworld_type = load(vanilla, "dimension_type", "overworld.json")
    end_type = load(vanilla, "dimension_type", "the_end.json")

    for w in worlds:
        rng = random.Random(w["seed"])
        wid = w["id"]
        terrain = w["terrain"]
        # ---- biome
        features = [[] for _ in range(11)]
        if terrain != "flat":
            features[6] = [o for o in ORES if placed_exists(vanilla, o)]
        # Ores in the world's own base stone: its alien resource plus the common metals.
        ore_list = [(f"{wid}_alien_ore", f"interstellar:{ALIEN_ORE[w['theme']]}_ore", 7, 9, 128)]
        ore_list += [(f"{wid}_ore_{name}", state, size, count, top) for name, state, size, count, top in WORLD_ORES]
        for fid, state, size, count, top in ore_list:
            write(f"worldgen/feature/{fid}.json", {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": size,
                                                    "targets": [{"state": state, "target": {"predicate_type": "minecraft:block_match",
                                                                                            "block": w["default_block"]}}]})
            write(f"worldgen/placed_feature/{fid}.json", {"feature": f"interstellar:{fid}", "placement": [
                {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 4},
                                                              "max_inclusive": {"absolute": top}}},
                {"type": "minecraft:biome"}]})
            features[6].append(f"interstellar:{fid}")
        for step, ids in THEME_FEATURES.get(w["theme"], {}).items():
            features[step] += [i for i in ids if placed_exists(vanilla, i)]
        creatures = [{"type": c, "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 4}, "weight": 8}
                     for c in rng.sample(CREATURES, 3)]
        monsters = [{"type": "interstellar:alien", "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 4}, "weight": 120}]
        monsters += [{"type": m, "count": 2, "weight": 25} for m in rng.sample(MONSTERS, 2)]
        biome = {
            "attributes": dict(spawns(monsters, creatures), **{"minecraft:visual/sky_color": w["sky"], "minecraft:visual/fog_color": w["fog"]}),
            "carvers": ["minecraft:cave", "minecraft:canyon"] if terrain not in ("flat", "floating_islands") else [],
            "downfall": round(rng.uniform(0, 1), 2),
            "effects": {"water_color": w["water"], "foliage_color": w["foliage"], "grass_color": w["foliage"]},
            "features": features,
            "has_precipitation": rng.random() < 0.5,
            "temperature": round(rng.uniform(-0.5, 2.0), 2),
        }
        write(f"worldgen/biome/{wid}.json", biome)

    # ---- space: an empty void under a starry night sky without sun or moon
    st = copy.deepcopy(end_type)
    st["has_ender_dragon_fight"] = False
    st["min_y"], st["height"], st["logical_height"] = 0, 256, 256
    st.pop("skybox", None)  # overworld sky (stars); the custom renderer below hides the sun and moon
    st["attributes"]["minecraft:visual/sky_color"] = "#000000"
    st["attributes"]["minecraft:visual/fog_color"] = "#000000"
    st["attributes"]["minecraft:visual/sun_angle"] = 180.0
    st["attributes"]["minecraft:visual/moon_angle"] = 180.0
    st["attributes"]["minecraft:visual/moon_phase"] = "new_moon"
    st["attributes"]["minecraft:visual/star_angle"] = 0.0
    st["attributes"]["minecraft:visual/star_brightness"] = 0.5
    st["attributes"]["minecraft:visual/sunrise_sunset_color"] = "#00000000"
    st["attributes"]["neoforge:custom_skybox"] = "interstellar:space"
    st["attributes"].pop("minecraft:audio/background_music", None)
    st["attributes"].pop("minecraft:audio/ambient_sounds", None)
    write("dimension_type/space.json", st)
    write("worldgen/biome/space.json", {
        "attributes": dict(spawns([], []), **{"minecraft:visual/sky_color": "#000000", "minecraft:visual/fog_color": "#03030a"}),
        "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"}, "features": [[] for _ in range(11)],
        "has_precipitation": False, "temperature": 0.5})
    write("dimension/space.json", {"type": "interstellar:space", "generator": {"type": "minecraft:flat", "settings": {
        "biome": "interstellar:space", "features": False, "lakes": False, "layers": [], "structure_overrides": []}}})

    # ---- planet dimension types (see PlanetTypes.java)
    for t in exported["planet_types"]:
        dt = copy.deepcopy(overworld_type)
        attrs = dt["attributes"]
        attrs["minecraft:visual/sky_color"] = t["sky"]
        attrs["minecraft:visual/fog_color"] = t["fog"]
        attrs["minecraft:visual/cloud_color"] = "#cc" + t["cloud"][1:]
        attrs["minecraft:visual/star_brightness"] = round(t["stars"], 3)
        attrs.pop("minecraft:gameplay/nether_portal_spawns_piglin", None)
        dt["has_skylight"] = t["skylight"]
        dt["ambient_light"] = round(t["ambient"], 3)
        write(f"dimension_type/planet_type_{t['index']}.json", dt)

    # ---- tags steering what planets are made of (packs and mods can add to them)
    def tag(rel, values):
        write(f"../{rel}", {"replace": False, "values": values})
    tag("interstellar/tags/block/planet_excluded_blocks.json", [
        "minecraft:tnt", "minecraft:barrier", "minecraft:light", "minecraft:structure_void", "minecraft:reinforced_deepslate",
        "interstellar:atmosphere", "interstellar:control_panel", "interstellar:generator", "interstellar:life_support",
        "interstellar:landing_gear", "interstellar:thruster", "interstellar:hull_plating", "interstellar:vehicle_pad", "interstellar:wheel",
        {"id": "#c:storage_blocks", "required": False}])
    tag("interstellar/tags/block/planet_ores.json", [])
    tag("interstellar/tags/worldgen/biome/planet_excluded_biomes.json", ["interstellar:space", "minecraft:the_void"])
    tag("interstellar/tags/entity_type/planet_excluded_entities.json", [
        "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian", "minecraft:giant",
        "minecraft:illusioner", "minecraft:creaking"])
    features = ["oak", "birch", "fancy_oak", "spruce", "pine", "jungle_bush", "acacia", "cherry", "dark_oak", "mangrove", "azalea_tree",
                "huge_brown_mushroom", "huge_red_mushroom", "crimson_fungus", "warped_fungus", "chorus_plant", "amethyst_geode", "ice_spike",
                "blue_ice", "basalt_pillar", "large_basalt_columns", "dripstone_cluster", "large_dripstone", "glow_lichen", "moss_patch",
                "cactus", "pumpkin", "melon", "sugar_cane", "fossil_coal", "fossil_diamonds", "monster_room", "desert_well", "end_island",
                "iceberg_packed", "iceberg_blue", "delta", "patch_fire", "patch_soul_fire", "spore_blossom", "sculk_patch_deep_dark", "pile_hay"]
    present = [f"minecraft:{f}" for f in features if os.path.exists(os.path.join(vanilla, "worldgen", "feature", f + ".json"))]
    tag("interstellar/tags/worldgen/feature/planet_features.json", present)
    print(f"generated {len(worlds)} species biomes, {len(exported['planet_types'])} planet types + space")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
