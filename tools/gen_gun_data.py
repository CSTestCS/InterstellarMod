#!/usr/bin/env python3
"""Generates the default gunsmithing datapack JSON (archetypes, parts, materials).

Run from the repository root:  python3 tools/gen_gun_data.py
Output: src/main/resources/data/interstellar/gun/{archetype,part,material}/*.json
Everything here is plain data: packs can add, override or remove entries without code.
"""
import json
import os
import shutil

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "interstellar", "gun")

HUMAN = ["human"]
ALL_SLOTS_HUMAN = ["receiver", "barrel", "stock", "grip", "magazine", "sight", "muzzle", "underbarrel"]

# id: (alien, tier, fire_mode, ammo, magazine kind, stats, slots, required, themes)
ARCHETYPES = {
    # ---- human / modern (Call of Duty, Halo UNSC, Destiny kinetic) ----
    "pistol":          (False, 1, "semi",  "ballistic", "pistol",  dict(damage=5, fire_rate=360, magazine=12, reload_time=1.4, spread=1.5, range=40, velocity=6), ["receiver", "barrel", "grip", "magazine", "sight", "muzzle"], ["receiver", "barrel"], HUMAN),
    "hand_cannon":     (False, 2, "semi",  "ballistic", "pistol",  dict(damage=11, fire_rate=140, magazine=8, reload_time=2.0, spread=1.0, recoil=4, range=56, headshot=2.0), ["receiver", "barrel", "grip", "magazine", "sight"], ["receiver", "barrel"], HUMAN),
    "smg":             (False, 2, "auto",  "ballistic", "pistol",  dict(damage=3.5, fire_rate=900, magazine=32, reload_time=1.8, spread=4, recoil=0.6, range=32), ALL_SLOTS_HUMAN, ["receiver", "barrel"], HUMAN),
    "assault_rifle":   (False, 2, "auto",  "ballistic", "rifle",   dict(damage=5, fire_rate=650, magazine=32, reload_time=2.2, spread=2.5, recoil=0.9, range=64), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "battle_rifle":    (False, 3, "burst", "ballistic", "rifle",   dict(damage=6.5, fire_rate=700, magazine=36, burst=3, reload_time=2.3, spread=1.2, recoil=1.2, range=80), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "pulse_rifle":     (False, 3, "burst", "ballistic", "rifle",   dict(damage=5.5, fire_rate=540, magazine=40, burst=4, reload_time=2.1, spread=1.5, range=72), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "scout_rifle":     (False, 3, "semi",  "ballistic", "rifle",   dict(damage=10, fire_rate=260, magazine=16, reload_time=2.2, spread=0.6, range=110, zoom=2.5), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "dmr":             (False, 3, "semi",  "ballistic", "rifle",   dict(damage=12, fire_rate=200, magazine=15, reload_time=2.5, spread=0.4, recoil=2, range=128, zoom=3), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "sniper_rifle":    (False, 4, "semi",  "ballistic", "sniper",  dict(damage=32, fire_rate=45, magazine=4, reload_time=3.5, spread=0.1, recoil=6, range=256, zoom=8, armor_pierce=0.4, headshot=2.5), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock", "sight"], HUMAN),
    "shotgun":         (False, 2, "semi",  "ballistic", "shell",   dict(damage=3.5, fire_rate=70, magazine=6, pellets=8, reload_time=3.0, spread=8, recoil=5, range=20), ["receiver", "barrel", "stock", "grip", "magazine", "muzzle", "underbarrel"], ["receiver", "barrel"], HUMAN),
    "lmg":             (False, 4, "auto",  "ballistic", "belt",    dict(damage=5.5, fire_rate=750, magazine=100, reload_time=5.0, spread=4.5, recoil=1.4, range=72), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "rocket_launcher": (False, 5, "semi",  "explosive", "rocket",  dict(damage=30, fire_rate=30, magazine=2, reload_time=4.5, spread=0.5, recoil=8, range=160, velocity=1.6, effect_power=3), ["receiver", "barrel", "sight", "grip"], ["receiver", "barrel"], HUMAN),
    # ---- alien / high tech (Halo Covenant & Forerunner, Destiny energy) ----
    "plasma_pistol":   (True, 1, "charge", "plasma", "energy_cell", dict(damage=6, fire_rate=420, magazine=40, reload_time=1.0, spread=2.5, range=48, velocity=2.2, charge_time=1.0, effect_power=1), ["receiver", "core", "grip", "muzzle"], ["receiver", "core"], ["plasma", "nether", "solar", "tech"]),
    "plasma_rifle":    (True, 2, "auto",  "plasma", "energy_cell", dict(damage=5, fire_rate=600, magazine=60, reload_time=1.5, spread=3, range=56, velocity=2.4, effect_power=1), ["receiver", "core", "barrel", "grip", "muzzle"], ["receiver", "core"], ["plasma", "nether", "solar", "tech", "bio"]),
    "needler":         (True, 2, "auto",  "needle", "crystal_cartridge", dict(damage=3, fire_rate=500, magazine=24, reload_time=2.0, spread=5, range=48, velocity=1.4, effect_power=2), ["receiver", "core", "magazine"], ["receiver", "core"], ["crystal", "bio", "void"]),
    "fusion_rifle":    (True, 3, "charge", "energy", "energy_cell", dict(damage=4.5, fire_rate=120, magazine=10, pellets=7, burst=1, reload_time=2.4, spread=3, range=40, velocity=3.5, charge_time=0.8), ["receiver", "core", "barrel", "stock", "sight"], ["receiver", "core"], ["plasma", "tech", "cryo", "sonic"]),
    "beam_rifle":      (True, 4, "semi",  "energy", "energy_cell", dict(damage=28, fire_rate=60, magazine=8, reload_time=2.5, spread=0.05, range=256, velocity=12, zoom=6, armor_pierce=0.5), ["receiver", "core", "barrel", "sight", "stock"], ["receiver", "core", "barrel"], ["tech", "void", "crystal", "hydro"]),
    "fuel_rod_cannon": (True, 5, "semi",  "explosive", "fuel_rod", dict(damage=26, fire_rate=50, magazine=5, reload_time=3.5, spread=1, range=128, velocity=1.4, effect_power=3), ["receiver", "core", "barrel", "sight"], ["receiver", "core"], ["toxic", "plasma", "bio", "nether"]),
    "shard_launcher":  (True, 3, "auto",  "needle", "crystal_cartridge", dict(damage=4, fire_rate=420, magazine=30, reload_time=2.0, spread=2, range=64, velocity=2.0, effect_power=2), ["receiver", "core", "barrel", "magazine"], ["receiver", "core"], ["crystal", "cryo", "hydro"]),
    "void_lance":      (True, 4, "charge", "energy", "void_cell", dict(damage=22, fire_rate=80, magazine=6, reload_time=2.8, spread=0.2, range=160, velocity=8, charge_time=1.2, armor_pierce=0.8), ["receiver", "core", "barrel", "sight"], ["receiver", "core"], ["void", "sonic"]),
    "sonic_disruptor": (True, 3, "auto",  "energy", "energy_cell", dict(damage=4, fire_rate=300, magazine=30, pellets=3, reload_time=2.0, spread=6, range=24, velocity=5, effect_power=2), ["receiver", "core", "muzzle"], ["receiver", "core"], ["sonic", "tech"]),
    "bio_spitter":     (True, 2, "auto",  "plasma", "bio_sac", dict(damage=3, fire_rate=360, magazine=40, reload_time=1.6, spread=5, range=28, velocity=1.2, effect_power=2), ["receiver", "core", "magazine"], ["receiver", "core"], ["bio", "toxic"]),
    # ---- blades: strike instead of shooting. damage = hit damage, fire_rate = swings per minute, magazine = blade
    # charge (hits per energy cell), range = blade length in blocks. The blade glows in its core's material colour.
    "laser_sword":     (False, 4, "melee", "energy", "energy_cell", dict(damage=9, fire_rate=96, magazine=64, reload_time=1.5, range=1.3, spread=0), ["receiver", "core", "grip"], ["receiver", "core"], HUMAN),
    "energy_sword":    (True, 3, "melee", "plasma", "energy_cell", dict(damage=11, fire_rate=84, magazine=48, reload_time=1.2, range=1.1, spread=0, effect_power=1), ["receiver", "core", "grip"], ["receiver", "core"], ["plasma", "nether", "solar", "tech", "crystal", "void", "cryo", "bio", "toxic", "hydro", "sonic"]),
}
BLADES = ["laser_sword", "energy_sword"]

def part(slot, tier=1, alien=False, archetypes=(), mods=None, effects=(), fire_mode=None, ammo=None, themes=(), cost=()):
    d = {"slot": slot, "tier": tier, "alien": alien}
    if archetypes: d["archetypes"] = list(archetypes)
    if mods: d["modifiers"] = mods
    if effects: d["effects"] = list(effects)
    if fire_mode: d["fire_mode"] = fire_mode
    if ammo: d["ammo"] = ammo
    if themes: d["themes"] = list(themes)
    d["cost"] = [{"item": i, "count": c} for i, c in cost]
    return d

M = lambda add=0, mul=1: {"add": add, "mul": mul}
# Gun parts fit guns only; blades have their own hilts and grips (alien cores power both).
HUMAN_ARCH = [k for k, v in ARCHETYPES.items() if not v[0] and k not in BLADES]
ALIEN_ARCH = [k for k, v in ARCHETYPES.items() if v[0] and k not in BLADES]
ALIEN_CORE_ARCH = ALIEN_ARCH + ["energy_sword"]

PARTS = {
    # receivers
    "standard_receiver":   part("receiver", 1, archetypes=HUMAN_ARCH, cost=[("minecraft:iron_block", 2), ("minecraft:redstone", 8), ("minecraft:piston", 1)]),
    "match_receiver":      part("receiver", 3, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.7), "damage": M(mul=1.1)}, cost=[("minecraft:diamond", 4), ("minecraft:iron_block", 2), ("minecraft:comparator", 2)]),
    "rapid_receiver":      part("receiver", 3, archetypes=["smg", "assault_rifle", "lmg", "pulse_rifle"], mods={"fire_rate": M(mul=1.3), "recoil": M(mul=1.2)}, cost=[("minecraft:diamond", 3), ("minecraft:repeater", 4), ("minecraft:gold_block", 1)]),
    "burst_receiver":      part("receiver", 3, archetypes=["assault_rifle", "smg", "pistol", "scout_rifle"], fire_mode="burst", mods={"burst": M(add=2), "spread": M(mul=0.8)}, cost=[("minecraft:diamond", 3), ("minecraft:repeater", 3), ("minecraft:observer", 2)]),
    "heavy_receiver":      part("receiver", 4, archetypes=["battle_rifle", "dmr", "sniper_rifle", "hand_cannon", "lmg", "shotgun"], mods={"damage": M(mul=1.3), "fire_rate": M(mul=0.8), "recoil": M(mul=1.3)}, cost=[("minecraft:netherite_ingot", 1), ("minecraft:iron_block", 4), ("minecraft:observer", 2)]),
    "auto_shotgun_receiver": part("receiver", 4, archetypes=["shotgun"], fire_mode="auto", mods={"fire_rate": M(mul=2.5), "damage": M(mul=0.85)}, cost=[("minecraft:netherite_ingot", 1), ("minecraft:repeater", 6)]),
    "alien_receiver":      part("receiver", 2, alien=True, archetypes=ALIEN_ARCH, cost=[("interstellar:alien_alloy", 6), ("minecraft:ender_eye", 2)]),
    "forerunner_receiver": part("receiver", 5, alien=True, archetypes=ALIEN_ARCH, mods={"damage": M(mul=1.35), "reload_time": M(mul=0.75)}, themes=["tech", "void", "crystal"], cost=[("interstellar:alien_alloy", 12), ("minecraft:nether_star", 1), ("interstellar:tech_chip", 4)]),
    # barrels
    "short_barrel":        part("barrel", 1, archetypes=HUMAN_ARCH, mods={"range": M(mul=0.75), "spread": M(mul=1.2), "recoil": M(mul=0.85)}, cost=[("minecraft:iron_ingot", 6)]),
    "standard_barrel":     part("barrel", 1, archetypes=HUMAN_ARCH, cost=[("minecraft:iron_ingot", 10), ("minecraft:copper_ingot", 4)]),
    "long_barrel":         part("barrel", 2, archetypes=HUMAN_ARCH, mods={"range": M(mul=1.4), "spread": M(mul=0.75), "damage": M(add=0.5), "velocity": M(mul=1.2)}, cost=[("minecraft:iron_block", 2), ("minecraft:diamond", 1)]),
    "fluted_barrel":       part("barrel", 3, archetypes=HUMAN_ARCH, mods={"fire_rate": M(mul=1.1), "recoil": M(mul=0.8)}, cost=[("minecraft:diamond", 2), ("minecraft:iron_block", 1)]),
    "heavy_barrel":        part("barrel", 4, archetypes=HUMAN_ARCH, mods={"damage": M(mul=1.25), "armor_pierce": M(add=0.2), "fire_rate": M(mul=0.9)}, cost=[("minecraft:netherite_ingot", 1), ("minecraft:diamond", 2)]),
    "rifled_slug_barrel":  part("barrel", 3, archetypes=["shotgun"], mods={"pellets": M(add=-7), "damage": M(mul=5), "spread": M(mul=0.2), "range": M(mul=3)}, cost=[("minecraft:diamond", 3)]),
    "focusing_lens":       part("barrel", 2, alien=True, archetypes=ALIEN_ARCH, mods={"spread": M(mul=0.6), "range": M(mul=1.3)}, cost=[("interstellar:alien_alloy", 3), ("minecraft:amethyst_shard", 6)]),
    "overcharged_emitter": part("barrel", 4, alien=True, archetypes=ALIEN_ARCH, mods={"damage": M(mul=1.4), "reload_time": M(mul=1.2)}, cost=[("interstellar:alien_alloy", 6), ("interstellar:plasma_core", 2)]),
    # stocks
    "light_stock":         part("stock", 1, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.85)}, cost=[("minecraft:stick", 4), ("minecraft:iron_ingot", 2)]),
    "tactical_stock":      part("stock", 2, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.65), "spread": M(mul=0.9)}, cost=[("minecraft:iron_block", 1), ("minecraft:leather", 4)]),
    "precision_stock":     part("stock", 3, archetypes=HUMAN_ARCH + ALIEN_ARCH, mods={"spread": M(mul=0.7), "recoil": M(mul=0.6)}, cost=[("minecraft:diamond", 2), ("minecraft:iron_block", 1)]),
    # grips
    "vertical_grip":       part("grip", 1, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.8)}, cost=[("minecraft:iron_ingot", 3)]),
    "angled_grip":         part("grip", 2, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.85), "reload_time": M(mul=0.9)}, cost=[("minecraft:iron_ingot", 3), ("minecraft:copper_ingot", 3)]),
    "alien_grip":          part("grip", 2, alien=True, archetypes=ALIEN_ARCH + ["energy_sword"], mods={"recoil": M(mul=0.7), "charge_time": M(mul=0.8)}, cost=[("interstellar:alien_alloy", 2)]),
    # magazines
    "extended_mag":        part("magazine", 2, archetypes=HUMAN_ARCH, mods={"magazine": M(mul=1.5), "reload_time": M(mul=1.15)}, cost=[("minecraft:iron_block", 1), ("minecraft:redstone", 6)]),
    "drum_mag":            part("magazine", 3, archetypes=["smg", "assault_rifle", "lmg", "shotgun"], mods={"magazine": M(mul=2.5), "reload_time": M(mul=1.5)}, cost=[("minecraft:iron_block", 2), ("minecraft:diamond", 1)]),
    "speed_mag":           part("magazine", 2, archetypes=HUMAN_ARCH, mods={"reload_time": M(mul=0.65), "magazine": M(mul=0.85)}, cost=[("minecraft:gold_ingot", 6), ("minecraft:redstone", 4)]),
    "crystal_matrix":      part("magazine", 3, alien=True, archetypes=["needler", "shard_launcher", "bio_spitter"], mods={"magazine": M(mul=1.8)}, themes=["crystal", "bio"], cost=[("interstellar:crystal_lattice", 3)]),
    # sights
    "iron_sights":         part("sight", 1, archetypes=HUMAN_ARCH + ALIEN_ARCH, cost=[("minecraft:iron_nugget", 6)]),
    "red_dot":             part("sight", 2, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.9), "zoom": M(add=0.3)}, cost=[("minecraft:glass_pane", 2), ("minecraft:redstone_torch", 1), ("minecraft:iron_ingot", 2)]),
    "acog_scope":          part("sight", 3, archetypes=HUMAN_ARCH, mods={"zoom": M(add=2.5), "spread": M(mul=0.85)}, cost=[("minecraft:spyglass", 1), ("minecraft:iron_ingot", 4)]),
    "sniper_scope":        part("sight", 4, archetypes=["sniper_rifle", "dmr", "scout_rifle", "beam_rifle"], mods={"zoom": M(add=6), "spread": M(mul=0.6)}, cost=[("minecraft:spyglass", 2), ("minecraft:diamond", 2)]),
    "holo_scope":          part("sight", 3, alien=True, archetypes=ALIEN_ARCH, mods={"zoom": M(add=3), "headshot": M(add=0.3)}, cost=[("interstellar:tech_chip", 2), ("minecraft:glass_pane", 4)]),
    # muzzles
    "suppressor":          part("muzzle", 2, archetypes=HUMAN_ARCH, mods={"range": M(mul=0.9), "recoil": M(mul=0.9)}, effects=["silent"], cost=[("minecraft:iron_block", 1), ("minecraft:white_wool", 4)]),
    "compensator":         part("muzzle", 2, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.6)}, cost=[("minecraft:iron_ingot", 6)]),
    "flash_hider":         part("muzzle", 1, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.9)}, cost=[("minecraft:iron_ingot", 4)]),
    "incendiary_choke":    part("muzzle", 3, archetypes=HUMAN_ARCH + ALIEN_ARCH, effects=["ignite"], cost=[("minecraft:blaze_powder", 8), ("minecraft:iron_block", 1)]),
    "plasma_vent":         part("muzzle", 2, alien=True, archetypes=ALIEN_ARCH, mods={"fire_rate": M(mul=1.2)}, themes=["plasma", "nether", "solar"], cost=[("interstellar:plasma_core", 1)]),
    # underbarrel
    "grenade_launcher":    part("underbarrel", 4, archetypes=["assault_rifle", "battle_rifle", "lmg", "shotgun"], effects=["grenade"], cost=[("minecraft:tnt", 4), ("minecraft:netherite_ingot", 1)]),
    "laser_sight":         part("underbarrel", 2, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.75)}, cost=[("minecraft:redstone_block", 1), ("minecraft:glass_pane", 1)]),
    "bayonet":             part("underbarrel", 1, archetypes=HUMAN_ARCH, effects=["bayonet"], cost=[("minecraft:iron_sword", 1)]),
    # alien cores (one per theme)
    # blades
    "saber_hilt":          part("receiver", 3, archetypes=["laser_sword"], cost=[("minecraft:iron_block", 2), ("minecraft:redstone_block", 1), ("minecraft:lightning_rod", 1)]),
    "crossguard_hilt":     part("receiver", 4, archetypes=["laser_sword"], mods={"damage": M(mul=1.2), "fire_rate": M(mul=0.85)}, cost=[("minecraft:netherite_ingot", 1), ("minecraft:iron_block", 2), ("minecraft:lightning_rod", 2)]),
    "duelist_hilt":        part("receiver", 4, archetypes=["laser_sword"], mods={"fire_rate": M(mul=1.25), "damage": M(mul=0.9)}, cost=[("minecraft:diamond", 4), ("minecraft:gold_block", 1), ("minecraft:lightning_rod", 1)]),
    "amethyst_focus":      part("core", 2, archetypes=["laser_sword"], cost=[("minecraft:amethyst_shard", 8), ("minecraft:redstone_block", 2)]),
    "diamond_focus":       part("core", 3, archetypes=["laser_sword"], mods={"damage": M(mul=1.15)}, cost=[("minecraft:diamond", 4), ("minecraft:redstone_block", 2)]),
    "echo_focus":          part("core", 4, archetypes=["laser_sword"], mods={"armor_pierce": M(add=0.4), "range": M(mul=1.15)}, cost=[("minecraft:echo_shard", 4), ("minecraft:nether_star", 1)]),
    "wrapped_grip":        part("grip", 1, archetypes=["laser_sword"], mods={"fire_rate": M(mul=1.1)}, cost=[("minecraft:leather", 4), ("minecraft:iron_ingot", 2)]),
    "energy_sword_hilt":   part("receiver", 3, alien=True, archetypes=["energy_sword"], cost=[("interstellar:alien_alloy", 8), ("minecraft:ender_eye", 2)]),
    "twin_prong_hilt":     part("receiver", 5, alien=True, archetypes=["energy_sword"], mods={"damage": M(mul=1.3), "range": M(mul=1.2)}, themes=["tech", "void", "crystal"], cost=[("interstellar:alien_alloy", 14), ("minecraft:nether_star", 1)]),
}
THEME_CORES = {
    "plasma":  ("interstellar:plasma_core", {"damage": M(mul=1.1)}, ["ignite"]),
    "nether":  ("interstellar:nether_cinder", {"damage": M(mul=1.15)}, ["ignite"]),
    "void":    ("interstellar:void_shard", {"armor_pierce": M(add=0.3)}, ["levitate"]),
    "crystal": ("interstellar:crystal_lattice", {"headshot": M(add=0.5)}, ["shatter"]),
    "bio":     ("interstellar:bio_gel", {"effect_power": M(add=1)}, ["poison"]),
    "toxic":   ("interstellar:bio_gel", {"effect_power": M(add=2)}, ["poison", "wither"]),
    "cryo":    ("interstellar:cryo_crystal", {"fire_rate": M(mul=0.9)}, ["freeze"]),
    "solar":   ("interstellar:solar_cell", {"damage": M(mul=1.05), "velocity": M(mul=1.2)}, ["ignite", "blind"]),
    "hydro":   ("interstellar:hydro_pearl", {"range": M(mul=1.2)}, ["knockback"]),
    "tech":    ("interstellar:tech_chip", {"reload_time": M(mul=0.8)}, ["chain"]),
    "sonic":   ("interstellar:sonic_resonator", {"armor_pierce": M(add=0.5)}, ["pierce"]),
}
for theme, (item, mods, fx) in THEME_CORES.items():
    for tier, (prefix, mul) in enumerate([("unstable", 0.9), ("stable", 1.0), ("refined", 1.15), ("ancient", 1.3)], start=1):
        m = dict(mods)
        m["damage"] = M(mul=m.get("damage", M())["mul"] * mul)
        PARTS[f"{prefix}_{theme}_core"] = part("core", tier + 1 if tier > 1 else 1, alien=True, archetypes=ALIEN_CORE_ARCH, mods=m, effects=fx, themes=[theme],
                                              cost=[(item, 2 * tier), ("interstellar:alien_alloy", 2 * tier)] + ([("minecraft:nether_star", 1)] if tier == 4 else []))

# materials: item -> colour, tier, modifiers, effects
MATERIALS = {
    "iron":       ("minecraft:iron_ingot", "#d8d8d8", 1, {}, []),
    "copper":     ("minecraft:copper_ingot", "#c15a36", 1, {"reload_time": M(mul=0.95), "damage": M(mul=0.95)}, []),
    "gold":       ("minecraft:gold_ingot", "#fad64a", 2, {"reload_time": M(mul=0.8), "recoil": M(mul=1.1)}, []),
    "quartz":     ("minecraft:quartz", "#e3d4c4", 2, {"spread": M(mul=0.9)}, []),
    "amethyst":   ("minecraft:amethyst_shard", "#9a5cc6", 2, {"headshot": M(add=0.3)}, ["shatter"]),
    "prismarine": ("minecraft:prismarine_crystals", "#5ba69b", 2, {"range": M(mul=1.1)}, []),
    "obsidian":   ("minecraft:obsidian", "#2a1d3d", 3, {"recoil": M(mul=0.8), "fire_rate": M(mul=0.95)}, []),
    "diamond":    ("minecraft:diamond", "#4ee1d6", 3, {"damage": M(mul=1.15)}, []),
    "emerald":    ("minecraft:emerald", "#17c544", 3, {"reload_time": M(mul=0.85), "magazine": M(mul=1.1)}, []),
    "echo":       ("minecraft:echo_shard", "#0d4f5c", 4, {"armor_pierce": M(add=0.25)}, ["pierce"]),
    "netherite":  ("minecraft:netherite_ingot", "#4a3e40", 4, {"damage": M(mul=1.25), "recoil": M(mul=0.9)}, []),
    "blaze":      ("minecraft:blaze_rod", "#f2a51a", 3, {}, ["ignite"]),
    "alien_alloy":     ("interstellar:alien_alloy", "#7d8ea8", 3, {"damage": M(mul=1.1), "reload_time": M(mul=0.9)}, []),
    "plasma":          ("interstellar:plasma_core", "#39c6ff", 4, {"damage": M(mul=1.2)}, ["ignite"]),
    "void":            ("interstellar:void_shard", "#2c0f4a", 5, {"armor_pierce": M(add=0.4)}, ["levitate"]),
    "crystal":         ("interstellar:crystal_lattice", "#e98cff", 4, {"headshot": M(add=0.5)}, ["shatter"]),
    "cryo":            ("interstellar:cryo_crystal", "#a6e4ff", 4, {}, ["freeze"]),
    "solar":           ("interstellar:solar_cell", "#ffb300", 4, {"velocity": M(mul=1.3)}, ["ignite"]),
    "bio":             ("interstellar:bio_gel", "#6fd12b", 3, {"effect_power": M(add=1)}, ["poison"]),
    "hydro":           ("interstellar:hydro_pearl", "#1f6fd1", 3, {"range": M(mul=1.15)}, ["knockback"]),
    "tech":            ("interstellar:tech_chip", "#3cf0a0", 4, {"reload_time": M(mul=0.75)}, ["chain"]),
    "sonic":           ("interstellar:sonic_resonator", "#0f8f8f", 5, {"armor_pierce": M(add=0.5)}, ["pierce"]),
    "nether_cinder":   ("interstellar:nether_cinder", "#d4401c", 3, {"damage": M(mul=1.1)}, ["ignite"]),
}
MATERIAL_THEMES = {"alien_alloy": [], "plasma": ["plasma", "solar"], "void": ["void"], "crystal": ["crystal"], "cryo": ["cryo"],
                   "solar": ["solar"], "bio": ["bio", "toxic"], "hydro": ["hydro"], "tech": ["tech"], "sonic": ["sonic"], "nether_cinder": ["nether"]}

MAGAZINES = {
    # kind: (ammo per magazine is the gun's magazine stat; cost to craft/refill one magazine)
    "pistol": [("minecraft:iron_nugget", 6), ("minecraft:gunpowder", 2)],
    "rifle": [("minecraft:iron_ingot", 2), ("minecraft:gunpowder", 4)],
    "sniper": [("minecraft:iron_ingot", 2), ("minecraft:gunpowder", 4), ("minecraft:copper_ingot", 2)],
    "shell": [("minecraft:paper", 2), ("minecraft:gunpowder", 3), ("minecraft:iron_nugget", 6)],
    "belt": [("minecraft:iron_ingot", 6), ("minecraft:gunpowder", 10)],
    "rocket": [("minecraft:tnt", 2), ("minecraft:iron_ingot", 3)],
    "energy_cell": [("interstellar:alien_alloy", 1), ("minecraft:redstone", 4), ("minecraft:glowstone_dust", 2)],
    "crystal_cartridge": [("interstellar:crystal_lattice", 1), ("minecraft:amethyst_shard", 4)],
    "fuel_rod": [("interstellar:bio_gel", 2), ("minecraft:glowstone", 1)],
    "void_cell": [("interstellar:void_shard", 1), ("minecraft:ender_pearl", 1)],
    "bio_sac": [("interstellar:bio_gel", 1), ("minecraft:slime_ball", 2)],
}


def write(sub, name, data):
    path = os.path.join(ROOT, sub, name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    for k, (alien, tier, fm, ammo, mag, stats, slots, req, themes) in ARCHETYPES.items():
        if alien:
            cost = [("interstellar:alien_alloy", 8 + 4 * tier), ("minecraft:ender_eye", tier)]
        else:
            cost = [("minecraft:iron_block", 2 + tier), ("minecraft:diamond", tier), ("minecraft:redstone_block", 1)]
            if tier > 3:
                cost.append(("minecraft:netherite_ingot", tier - 3))
        # "cost" is what a new frame of this archetype costs at the gunsmith table: human guns are late game,
        # alien frames need alien loot (aliens drop finished guns far more cheaply).
        write("archetype", k, {"alien": alien, "tier": tier, "fire_mode": fm, "ammo": ammo, "magazine": mag, "stats": stats,
                               "slots": slots, "required": req, "themes": themes,
                               "cost": [{"item": i, "count": c} for i, c in cost]})
    for k, v in PARTS.items():
        write("part", k, v)
    for k, (item, color, tier, mods, fx) in MATERIALS.items():
        alien = item.startswith("interstellar:")
        write("material", k, {"item": item, "color": color, "tier": tier, "modifiers": mods, "effects": fx, "alien": alien,
                              "themes": MATERIAL_THEMES.get(k, [])})
    for k, cost in MAGAZINES.items():
        write("magazine", k, {"cost": [{"item": i, "count": c} for i, c in cost]})
    print(f"{len(ARCHETYPES)} archetypes, {len(PARTS)} parts, {len(MATERIALS)} materials, {len(MAGAZINES)} magazine kinds")


if __name__ == "__main__":
    main()
