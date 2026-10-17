#!/usr/bin/env python3
"""Generates the default gunsmithing datapack JSON (archetypes, parts, materials, magazines).

Run from the repository root:  python3 tools/gen_gun_data.py
Output: src/main/resources/data/interstellar/gun/{archetype,part,material,magazine}/*.json
Everything here is plain data: packs can add, override or remove entries without code.

Design rule: every option changes both looks (each part has its own model, see tools/gen_assets.py) and behaviour
(each part and material carries at least one effect no other option in its slot has; see GunEffects.java for what
each effect does). Power sources (cores, focus crystals, special ammunition) also carry the colour of shots and blades.
"""
import json
import os
import shutil

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "interstellar", "gun")

HUMAN = ["human"]
ALIEN_THEMES = ["plasma", "nether", "solar", "tech", "crystal", "void", "cryo", "bio", "toxic", "hydro", "sonic"]
ALL_SLOTS_HUMAN = ["receiver", "barrel", "stock", "grip", "magazine", "sight", "muzzle", "underbarrel"]
BLADE_SLOTS = ["receiver", "core", "grip"]

# id: (alien, tier, fire_mode, ammo, magazine kind, stats, slots, required, themes[, extra])
# Blades: damage = hit damage, fire_rate = swings per minute, magazine = blade charge, range = blade length (blocks);
# "blade" picks how the glowing blade is drawn.
ARCHETYPES = {
    # ---- human / modern (Call of Duty, Halo UNSC, Destiny kinetic) ----
    "pistol":          (False, 1, "semi",  "ballistic", "pistol",  dict(damage=5, fire_rate=360, magazine=12, reload_time=1.4, spread=1.5, range=40, velocity=6), ["receiver", "barrel", "grip", "magazine", "sight", "muzzle"], ["receiver", "barrel"], HUMAN),
    "revolver":        (False, 2, "semi",  "ballistic", "pistol",  dict(damage=9, fire_rate=160, magazine=6, reload_time=2.4, spread=1.0, recoil=3.5, range=48, headshot=2.2), ["receiver", "barrel", "grip", "sight", "muzzle"], ["receiver", "barrel"], HUMAN),
    "hand_cannon":     (False, 2, "semi",  "ballistic", "pistol",  dict(damage=11, fire_rate=140, magazine=8, reload_time=2.0, spread=1.0, recoil=4, range=56, headshot=2.0), ["receiver", "barrel", "grip", "magazine", "sight"], ["receiver", "barrel"], HUMAN),
    "smg":             (False, 2, "auto",  "ballistic", "pistol",  dict(damage=3.5, fire_rate=900, magazine=32, reload_time=1.8, spread=4, recoil=0.6, range=32), ALL_SLOTS_HUMAN, ["receiver", "barrel"], HUMAN),
    "carbine":         (False, 2, "semi",  "ballistic", "rifle",   dict(damage=7, fire_rate=380, magazine=20, reload_time=1.9, spread=1.2, recoil=1.0, range=72), ALL_SLOTS_HUMAN, ["receiver", "barrel"], HUMAN),
    "assault_rifle":   (False, 2, "auto",  "ballistic", "rifle",   dict(damage=5, fire_rate=650, magazine=32, reload_time=2.2, spread=2.5, recoil=0.9, range=64), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "battle_rifle":    (False, 3, "burst", "ballistic", "rifle",   dict(damage=6.5, fire_rate=700, magazine=36, burst=3, reload_time=2.3, spread=1.2, recoil=1.2, range=80), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "pulse_rifle":     (False, 3, "burst", "ballistic", "rifle",   dict(damage=5.5, fire_rate=540, magazine=40, burst=4, reload_time=2.1, spread=1.5, range=72), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "scout_rifle":     (False, 3, "semi",  "ballistic", "rifle",   dict(damage=10, fire_rate=260, magazine=16, reload_time=2.2, spread=0.6, range=110, zoom=2.5), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "dmr":             (False, 3, "semi",  "ballistic", "rifle",   dict(damage=12, fire_rate=200, magazine=15, reload_time=2.5, spread=0.4, recoil=2, range=128, zoom=3), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "sniper_rifle":    (False, 4, "semi",  "ballistic", "sniper",  dict(damage=32, fire_rate=45, magazine=4, reload_time=3.5, spread=0.1, recoil=6, range=256, zoom=8, armor_pierce=0.4, headshot=2.5), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock", "sight"], HUMAN),
    "railgun":         (False, 5, "charge", "energy", "energy_cell", dict(damage=40, fire_rate=30, magazine=3, reload_time=3.8, spread=0.0, recoil=7, range=300, velocity=14, zoom=5, armor_pierce=0.9, charge_time=1.0), ["receiver", "barrel", "stock", "sight", "magazine"], ["receiver", "barrel", "stock"], HUMAN),
    "shotgun":         (False, 2, "semi",  "ballistic", "shell",   dict(damage=3.5, fire_rate=70, magazine=6, pellets=8, reload_time=3.0, spread=8, recoil=5, range=20), ["receiver", "barrel", "stock", "grip", "magazine", "muzzle", "underbarrel"], ["receiver", "barrel"], HUMAN),
    "lmg":             (False, 4, "auto",  "ballistic", "belt",    dict(damage=5.5, fire_rate=750, magazine=100, reload_time=5.0, spread=4.5, recoil=1.4, range=72), ALL_SLOTS_HUMAN, ["receiver", "barrel", "stock"], HUMAN),
    "flamethrower":    (False, 3, "auto",  "plasma", "fuel_canister", dict(damage=2.5, fire_rate=1000, magazine=80, reload_time=3.0, spread=7, range=12, velocity=0.9, effect_power=2), ["receiver", "barrel", "grip", "magazine", "muzzle"], ["receiver", "barrel"], HUMAN),
    "grenade_launcher": (False, 3, "semi", "explosive", "rocket", dict(damage=14, fire_rate=70, magazine=6, reload_time=3.2, spread=1.5, recoil=3, range=64, velocity=1.3, effect_power=2), ["receiver", "barrel", "stock", "grip", "sight"], ["receiver", "barrel"], HUMAN),
    "rocket_launcher": (False, 5, "semi",  "explosive", "rocket",  dict(damage=30, fire_rate=30, magazine=2, reload_time=4.5, spread=0.5, recoil=8, range=160, velocity=1.6, effect_power=3), ["receiver", "barrel", "sight", "grip"], ["receiver", "barrel"], HUMAN),
    # ---- alien / high tech (Halo Covenant & Forerunner, Destiny energy) ----
    "plasma_pistol":   (True, 1, "charge", "plasma", "energy_cell", dict(damage=6, fire_rate=420, magazine=40, reload_time=1.0, spread=2.5, range=48, velocity=2.2, charge_time=1.0, effect_power=1), ["receiver", "core", "grip", "muzzle"], ["receiver", "core"], ["plasma", "nether", "solar", "tech"]),
    "plasma_rifle":    (True, 2, "auto",  "plasma", "energy_cell", dict(damage=5, fire_rate=600, magazine=60, reload_time=1.5, spread=3, range=56, velocity=2.4, effect_power=1), ["receiver", "core", "barrel", "grip", "muzzle"], ["receiver", "core"], ["plasma", "nether", "solar", "tech", "bio"]),
    "needler":         (True, 2, "auto",  "needle", "crystal_cartridge", dict(damage=3, fire_rate=500, magazine=24, reload_time=2.0, spread=5, range=48, velocity=1.4, effect_power=2), ["receiver", "core", "magazine"], ["receiver", "core"], ["crystal", "bio", "void"]),
    "spiker":          (True, 2, "auto",  "needle", "crystal_cartridge", dict(damage=4, fire_rate=560, magazine=40, reload_time=2.2, spread=3.5, range=40, velocity=2.6), ["receiver", "core", "barrel", "grip"], ["receiver", "core"], ["nether", "bio", "toxic", "sonic"]),
    "arc_caster":      (True, 3, "auto",  "energy", "energy_cell", dict(damage=3, fire_rate=480, magazine=50, reload_time=1.8, spread=2, range=30, velocity=4.5, effect_power=1), ["receiver", "core", "barrel", "muzzle"], ["receiver", "core"], ["tech", "hydro", "sonic", "solar"]),
    "fusion_rifle":    (True, 3, "charge", "energy", "energy_cell", dict(damage=4.5, fire_rate=120, magazine=10, pellets=7, burst=1, reload_time=2.4, spread=3, range=40, velocity=3.5, charge_time=0.8), ["receiver", "core", "barrel", "stock", "sight"], ["receiver", "core"], ["plasma", "tech", "cryo", "sonic"]),
    "beam_rifle":      (True, 4, "semi",  "energy", "energy_cell", dict(damage=28, fire_rate=60, magazine=8, reload_time=2.5, spread=0.05, range=256, velocity=12, zoom=6, armor_pierce=0.5), ["receiver", "core", "barrel", "sight", "stock"], ["receiver", "core", "barrel"], ["tech", "void", "crystal", "hydro"]),
    "gravity_rifle":   (True, 4, "semi",  "energy", "void_cell", dict(damage=12, fire_rate=90, magazine=10, reload_time=2.6, spread=0.5, range=80, velocity=2.0, effect_power=2), ["receiver", "core", "barrel", "sight"], ["receiver", "core"], ["void", "crystal", "cryo"]),
    "fuel_rod_cannon": (True, 5, "semi",  "explosive", "fuel_rod", dict(damage=26, fire_rate=50, magazine=5, reload_time=3.5, spread=1, range=128, velocity=1.4, effect_power=3), ["receiver", "core", "barrel", "sight"], ["receiver", "core"], ["toxic", "plasma", "bio", "nether"]),
    "shard_launcher":  (True, 3, "auto",  "needle", "crystal_cartridge", dict(damage=4, fire_rate=420, magazine=30, reload_time=2.0, spread=2, range=64, velocity=2.0, effect_power=2), ["receiver", "core", "barrel", "magazine"], ["receiver", "core"], ["crystal", "cryo", "hydro"]),
    "void_lance":      (True, 4, "charge", "energy", "void_cell", dict(damage=22, fire_rate=80, magazine=6, reload_time=2.8, spread=0.2, range=160, velocity=8, charge_time=1.2, armor_pierce=0.8), ["receiver", "core", "barrel", "sight"], ["receiver", "core"], ["void", "sonic"]),
    "sonic_disruptor": (True, 3, "auto",  "energy", "energy_cell", dict(damage=4, fire_rate=300, magazine=30, pellets=3, reload_time=2.0, spread=6, range=24, velocity=5, effect_power=2), ["receiver", "core", "muzzle"], ["receiver", "core"], ["sonic", "tech"]),
    "bio_spitter":     (True, 2, "auto",  "plasma", "bio_sac", dict(damage=3, fire_rate=360, magazine=40, reload_time=1.6, spread=5, range=28, velocity=1.2, effect_power=2), ["receiver", "core", "magazine"], ["receiver", "core"], ["bio", "toxic"]),
    # ---- blades ----
    "laser_sword":     (False, 4, "melee", "energy", "energy_cell", dict(damage=9, fire_rate=96, magazine=64, reload_time=1.5, range=1.3, spread=0), BLADE_SLOTS, ["receiver", "core"], HUMAN, {"blade": "saber"}),
    "laser_dagger":    (False, 3, "melee", "energy", "energy_cell", dict(damage=5.5, fire_rate=170, magazine=90, reload_time=1.0, range=0.6, spread=0), BLADE_SLOTS, ["receiver", "core"], HUMAN, {"blade": "dagger"}),
    "laser_greatsword": (False, 5, "melee", "energy", "energy_cell", dict(damage=16, fire_rate=50, magazine=40, reload_time=2.2, range=2.0, spread=0), BLADE_SLOTS, ["receiver", "core"], HUMAN, {"blade": "broad"}),
    "laser_glaive":    (False, 4, "melee", "energy", "energy_cell", dict(damage=10, fire_rate=80, magazine=56, reload_time=1.8, range=1.0, spread=0), BLADE_SLOTS, ["receiver", "core"], HUMAN, {"blade": "glaive"}),
    "energy_sword":    (True, 3, "melee", "plasma", "energy_cell", dict(damage=11, fire_rate=84, magazine=48, reload_time=1.2, range=1.1, spread=0, effect_power=1), BLADE_SLOTS, ["receiver", "core"], ALIEN_THEMES, {"blade": "prongs"}),
    "energy_dagger":   (True, 2, "melee", "plasma", "energy_cell", dict(damage=6, fire_rate=160, magazine=70, reload_time=0.9, range=0.55, spread=0), BLADE_SLOTS, ["receiver", "core"], ALIEN_THEMES, {"blade": "dagger"}),
    "plasma_scythe":   (True, 4, "melee", "plasma", "energy_cell", dict(damage=13, fire_rate=64, magazine=44, reload_time=1.6, range=1.4, spread=0, effect_power=1), BLADE_SLOTS, ["receiver", "core"], ALIEN_THEMES, {"blade": "scythe"}),
    "gravity_hammer":  (True, 5, "melee", "energy", "void_cell", dict(damage=18, fire_rate=40, magazine=30, reload_time=2.4, range=0.7, spread=0, effect_power=2), BLADE_SLOTS, ["receiver", "core"], ALIEN_THEMES, {"blade": "hammer"}),
}
HUMAN_BLADES = ["laser_sword", "laser_dagger", "laser_greatsword", "laser_glaive"]
ALIEN_BLADES = ["energy_sword", "energy_dagger", "plasma_scythe", "gravity_hammer"]
BLADES = HUMAN_BLADES + ALIEN_BLADES


def part(slot, tier=1, alien=False, archetypes=(), mods=None, effects=(), fire_mode=None, ammo=None, themes=(), cost=(), color=None):
    d = {"slot": slot, "tier": tier, "alien": alien}
    if archetypes: d["archetypes"] = list(archetypes)
    if mods: d["modifiers"] = mods
    if effects: d["effects"] = list(effects)
    if fire_mode: d["fire_mode"] = fire_mode
    if ammo: d["ammo"] = ammo
    if themes: d["themes"] = list(themes)
    if color: d["color"] = color
    d["cost"] = [{"item": i, "count": c} for i, c in cost]
    return d


M = lambda add=0, mul=1: {"add": add, "mul": mul}
HUMAN_ARCH = [k for k, v in ARCHETYPES.items() if not v[0] and k not in BLADES]
ALIEN_ARCH = [k for k, v in ARCHETYPES.items() if v[0] and k not in BLADES]
ALIEN_CORE_ARCH = ALIEN_ARCH + ALIEN_BLADES
AUTO_HUMAN = ["smg", "assault_rifle", "lmg", "pulse_rifle", "carbine"]

PARTS = {
    # ---------------------------------------------------------------- human receivers
    "standard_receiver":   part("receiver", 1, archetypes=HUMAN_ARCH, effects=["steadfast"], cost=[("minecraft:iron_block", 2), ("minecraft:redstone", 8), ("minecraft:piston", 1)]),
    "match_receiver":      part("receiver", 3, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.7), "damage": M(mul=1.1)}, effects=["execute"], cost=[("minecraft:diamond", 4), ("minecraft:iron_block", 2), ("minecraft:comparator", 2)]),
    "rapid_receiver":      part("receiver", 3, archetypes=AUTO_HUMAN, mods={"fire_rate": M(mul=1.3), "recoil": M(mul=1.2)}, effects=["adrenaline"], cost=[("minecraft:diamond", 3), ("minecraft:repeater", 4), ("minecraft:gold_block", 1)]),
    "burst_receiver":      part("receiver", 3, archetypes=["assault_rifle", "smg", "pistol", "scout_rifle", "carbine"], fire_mode="burst", mods={"burst": M(add=2), "spread": M(mul=0.8)}, effects=["echo"], cost=[("minecraft:diamond", 3), ("minecraft:repeater", 3), ("minecraft:observer", 2)]),
    "heavy_receiver":      part("receiver", 4, archetypes=["battle_rifle", "dmr", "sniper_rifle", "hand_cannon", "lmg", "shotgun", "revolver", "railgun"], mods={"damage": M(mul=1.3), "fire_rate": M(mul=0.8), "recoil": M(mul=1.3)}, effects=["stagger"], cost=[("minecraft:netherite_ingot", 1), ("minecraft:iron_block", 4), ("minecraft:observer", 2)]),
    "auto_shotgun_receiver": part("receiver", 4, archetypes=["shotgun"], fire_mode="auto", mods={"fire_rate": M(mul=2.5), "damage": M(mul=0.85)}, effects=["spray"], cost=[("minecraft:netherite_ingot", 1), ("minecraft:repeater", 6)]),
    "lever_receiver":      part("receiver", 2, archetypes=["revolver", "carbine", "shotgun", "scout_rifle", "dmr"], mods={"damage": M(mul=1.15), "fire_rate": M(mul=0.85)}, effects=["knockback"], cost=[("minecraft:iron_block", 2), ("minecraft:lever", 2), ("minecraft:oak_planks", 6)]),
    "gyro_receiver":       part("receiver", 4, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.4), "spread": M(mul=0.85)}, effects=["ammo_saver"], cost=[("minecraft:netherite_ingot", 1), ("minecraft:compass", 2), ("minecraft:observer", 2)]),
    # alien receivers
    "alien_receiver":      part("receiver", 2, alien=True, archetypes=ALIEN_ARCH, effects=["mark"], cost=[("interstellar:alien_alloy", 6), ("minecraft:ender_eye", 2)]),
    "forerunner_receiver": part("receiver", 5, alien=True, archetypes=ALIEN_ARCH, mods={"damage": M(mul=1.35), "reload_time": M(mul=0.75)}, effects=["phase"], themes=["tech", "void", "crystal"], cost=[("interstellar:alien_alloy", 12), ("minecraft:nether_star", 1), ("interstellar:tech_chip", 4)]),
    "covenant_receiver":   part("receiver", 3, alien=True, archetypes=ALIEN_ARCH, mods={"fire_rate": M(mul=1.15)}, effects=["accelerate"], themes=["plasma", "nether", "solar", "bio"], cost=[("interstellar:alien_alloy", 8), ("interstellar:plasma_core", 2)]),
    "swarm_receiver":      part("receiver", 3, alien=True, archetypes=ALIEN_ARCH, mods={"damage": M(mul=0.85), "fire_rate": M(mul=1.25)}, effects=["homing"], themes=["bio", "toxic", "crystal"], cost=[("interstellar:alien_alloy", 8), ("interstellar:bio_gel", 3)]),
    # ---------------------------------------------------------------- barrels
    "short_barrel":        part("barrel", 1, archetypes=HUMAN_ARCH, mods={"range": M(mul=0.75), "spread": M(mul=1.2), "recoil": M(mul=0.85)}, effects=["shockwave"], cost=[("minecraft:iron_ingot", 6)]),
    "standard_barrel":     part("barrel", 1, archetypes=HUMAN_ARCH, effects=["pierce"], cost=[("minecraft:iron_ingot", 10), ("minecraft:copper_ingot", 4)]),
    "long_barrel":         part("barrel", 2, archetypes=HUMAN_ARCH, mods={"range": M(mul=1.4), "spread": M(mul=0.75), "damage": M(add=0.5), "velocity": M(mul=1.2)}, effects=["accelerate"], cost=[("minecraft:iron_block", 2), ("minecraft:diamond", 1)]),
    "fluted_barrel":       part("barrel", 3, archetypes=HUMAN_ARCH, mods={"fire_rate": M(mul=1.1), "recoil": M(mul=0.8)}, effects=["ricochet"], cost=[("minecraft:diamond", 2), ("minecraft:iron_block", 1)]),
    "heavy_barrel":        part("barrel", 4, archetypes=HUMAN_ARCH, mods={"damage": M(mul=1.25), "armor_pierce": M(add=0.2), "fire_rate": M(mul=0.9)}, effects=["armor_shred"], cost=[("minecraft:netherite_ingot", 1), ("minecraft:diamond", 2)]),
    "rifled_slug_barrel":  part("barrel", 3, archetypes=["shotgun"], mods={"pellets": M(add=-7), "damage": M(mul=5), "spread": M(mul=0.2), "range": M(mul=3)}, effects=["launch"], cost=[("minecraft:diamond", 3)]),
    "ported_barrel":       part("barrel", 2, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.7), "velocity": M(mul=1.1)}, effects=["arc"], cost=[("minecraft:iron_block", 1), ("minecraft:gunpowder", 6)]),
    "coil_barrel":         part("barrel", 5, archetypes=["railgun", "sniper_rifle", "dmr", "scout_rifle"], mods={"damage": M(mul=1.2), "velocity": M(mul=1.6)}, effects=["lightning"], cost=[("minecraft:netherite_ingot", 1), ("minecraft:lightning_rod", 4), ("minecraft:copper_block", 2)]),
    "focusing_lens":       part("barrel", 2, alien=True, archetypes=ALIEN_ARCH, mods={"spread": M(mul=0.6), "range": M(mul=1.3)}, effects=["blind"], cost=[("interstellar:alien_alloy", 3), ("minecraft:amethyst_shard", 6)]),
    "overcharged_emitter": part("barrel", 4, alien=True, archetypes=ALIEN_ARCH, mods={"damage": M(mul=1.4), "reload_time": M(mul=1.2)}, effects=["volatile"], cost=[("interstellar:alien_alloy", 6), ("interstellar:plasma_core", 2)]),
    "splitter_emitter":    part("barrel", 3, alien=True, archetypes=ALIEN_ARCH, mods={"damage": M(mul=0.8)}, effects=["split"], cost=[("interstellar:alien_alloy", 5), ("interstellar:crystal_lattice", 2)]),
    "gravity_coil":        part("barrel", 4, alien=True, archetypes=ALIEN_ARCH, mods={"velocity": M(mul=0.8)}, effects=["gravity_well"], themes=["void", "cryo", "crystal"], cost=[("interstellar:alien_alloy", 6), ("interstellar:void_shard", 2)]),
    # ---------------------------------------------------------------- stocks
    "light_stock":         part("stock", 1, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.85)}, effects=["adrenaline"], cost=[("minecraft:stick", 4), ("minecraft:iron_ingot", 2)]),
    "tactical_stock":      part("stock", 2, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.65), "spread": M(mul=0.9)}, effects=["steadfast"], cost=[("minecraft:iron_block", 1), ("minecraft:leather", 4)]),
    "precision_stock":     part("stock", 3, archetypes=HUMAN_ARCH + ALIEN_ARCH, mods={"spread": M(mul=0.7), "recoil": M(mul=0.6)}, effects=["mark"], cost=[("minecraft:diamond", 2), ("minecraft:iron_block", 1)]),
    "skeleton_stock":      part("stock", 2, archetypes=HUMAN_ARCH, mods={"reload_time": M(mul=0.85), "recoil": M(mul=1.1)}, effects=["ammo_saver"], cost=[("minecraft:bone_block", 2), ("minecraft:iron_ingot", 3)]),
    "recoil_stock":        part("stock", 4, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.35), "fire_rate": M(mul=1.05)}, effects=["parry"], cost=[("minecraft:netherite_scrap", 2), ("minecraft:slime_block", 2)]),
    # ---------------------------------------------------------------- grips
    "vertical_grip":       part("grip", 1, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.8)}, effects=["steadfast"], cost=[("minecraft:iron_ingot", 3)]),
    "angled_grip":         part("grip", 2, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.85), "reload_time": M(mul=0.9)}, effects=["adrenaline"], cost=[("minecraft:iron_ingot", 3), ("minecraft:copper_ingot", 3)]),
    "stub_grip":           part("grip", 2, archetypes=HUMAN_ARCH, mods={"fire_rate": M(mul=1.08)}, effects=["ammo_saver"], cost=[("minecraft:iron_ingot", 2), ("minecraft:gold_ingot", 2)]),
    "rubber_grip":         part("grip", 1, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.9), "spread": M(mul=0.95)}, effects=["parry"], cost=[("minecraft:leather", 3), ("minecraft:slime_ball", 2)]),
    "alien_grip":          part("grip", 2, alien=True, archetypes=ALIEN_ARCH, mods={"recoil": M(mul=0.7), "charge_time": M(mul=0.8)}, effects=["leech"], cost=[("interstellar:alien_alloy", 2)]),
    "tendril_grip":        part("grip", 3, alien=True, archetypes=ALIEN_ARCH, mods={"fire_rate": M(mul=1.1)}, effects=["plague"], themes=["bio", "toxic"], cost=[("interstellar:alien_alloy", 2), ("interstellar:bio_gel", 2)]),
    # ---------------------------------------------------------------- magazines (ammunition is also a power source: colour)
    "extended_mag":        part("magazine", 2, archetypes=HUMAN_ARCH, mods={"magazine": M(mul=1.5), "reload_time": M(mul=1.15)}, effects=["ammo_saver"], cost=[("minecraft:iron_block", 1), ("minecraft:redstone", 6)]),
    "drum_mag":            part("magazine", 3, archetypes=AUTO_HUMAN + ["shotgun"], mods={"magazine": M(mul=2.5), "reload_time": M(mul=1.5)}, effects=["spray"], cost=[("minecraft:iron_block", 2), ("minecraft:diamond", 1)]),
    "speed_mag":           part("magazine", 2, archetypes=HUMAN_ARCH, mods={"reload_time": M(mul=0.65), "magazine": M(mul=0.85)}, effects=["adrenaline"], cost=[("minecraft:gold_ingot", 6), ("minecraft:redstone", 4)]),
    "incendiary_mag":      part("magazine", 3, archetypes=HUMAN_ARCH, mods={"damage": M(mul=0.9)}, effects=["ignite"], color="#ff6a1a", cost=[("minecraft:blaze_powder", 6), ("minecraft:iron_block", 1)]),
    "armor_piercing_mag":  part("magazine", 3, archetypes=HUMAN_ARCH, mods={"armor_pierce": M(add=0.3), "magazine": M(mul=0.8)}, effects=["armor_shred"], color="#c9d6e3", cost=[("minecraft:netherite_scrap", 1), ("minecraft:iron_block", 1)]),
    "cryo_mag":            part("magazine", 3, archetypes=HUMAN_ARCH, mods={"fire_rate": M(mul=0.95)}, effects=["freeze"], color="#9fe8ff", cost=[("minecraft:blue_ice", 2), ("minecraft:iron_block", 1)]),
    "explosive_mag":       part("magazine", 4, archetypes=HUMAN_ARCH, mods={"magazine": M(mul=0.6), "damage": M(mul=1.1)}, effects=["concussive"], color="#ffb000", cost=[("minecraft:tnt", 3), ("minecraft:iron_block", 1)]),
    "crystal_matrix":      part("magazine", 3, alien=True, archetypes=["needler", "shard_launcher", "bio_spitter"], mods={"magazine": M(mul=1.8)}, effects=["shatter"], themes=["crystal", "bio"], color="#ff8cf0", cost=[("interstellar:crystal_lattice", 3)]),
    "spore_sac":           part("magazine", 3, alien=True, archetypes=["needler", "shard_launcher", "bio_spitter"], mods={"magazine": M(mul=1.3)}, effects=["cluster"], themes=["bio", "toxic"], color="#8cff3a", cost=[("interstellar:bio_gel", 4)]),
    # ---------------------------------------------------------------- sights
    "iron_sights":         part("sight", 1, archetypes=HUMAN_ARCH + ALIEN_ARCH, effects=["stagger"], cost=[("minecraft:iron_nugget", 6)]),
    "red_dot":             part("sight", 2, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.9), "zoom": M(add=0.3)}, effects=["mark"], cost=[("minecraft:glass_pane", 2), ("minecraft:redstone_torch", 1), ("minecraft:iron_ingot", 2)]),
    "acog_scope":          part("sight", 3, archetypes=HUMAN_ARCH, mods={"zoom": M(add=2.5), "spread": M(mul=0.85)}, effects=["execute"], cost=[("minecraft:spyglass", 1), ("minecraft:iron_ingot", 4)]),
    "sniper_scope":        part("sight", 4, archetypes=["sniper_rifle", "dmr", "scout_rifle", "beam_rifle", "railgun"], mods={"zoom": M(add=6), "spread": M(mul=0.6)}, effects=["reaper"], cost=[("minecraft:spyglass", 2), ("minecraft:diamond", 2)]),
    "thermal_sight":       part("sight", 3, archetypes=HUMAN_ARCH, mods={"zoom": M(add=1.5)}, effects=["darkness"], cost=[("minecraft:spyglass", 1), ("minecraft:magma_block", 2), ("minecraft:redstone", 4)]),
    "holo_scope":          part("sight", 3, alien=True, archetypes=ALIEN_ARCH, mods={"zoom": M(add=3), "headshot": M(add=0.3)}, effects=["homing"], cost=[("interstellar:tech_chip", 2), ("minecraft:glass_pane", 4)]),
    "seer_eye":            part("sight", 4, alien=True, archetypes=ALIEN_ARCH, mods={"zoom": M(add=4)}, effects=["warp"], themes=["void", "crystal"], cost=[("interstellar:void_shard", 2), ("minecraft:ender_eye", 2)]),
    # ---------------------------------------------------------------- muzzles
    "suppressor":          part("muzzle", 2, archetypes=HUMAN_ARCH, mods={"range": M(mul=0.9), "recoil": M(mul=0.9)}, effects=["silent"], cost=[("minecraft:iron_block", 1), ("minecraft:white_wool", 4)]),
    "compensator":         part("muzzle", 2, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.6)}, effects=["steadfast"], cost=[("minecraft:iron_ingot", 6)]),
    "flash_hider":         part("muzzle", 1, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.9)}, effects=["blind"], cost=[("minecraft:iron_ingot", 4)]),
    "incendiary_choke":    part("muzzle", 3, archetypes=HUMAN_ARCH + ALIEN_ARCH, effects=["ignite"], cost=[("minecraft:blaze_powder", 8), ("minecraft:iron_block", 1)]),
    "muzzle_brake":        part("muzzle", 2, archetypes=HUMAN_ARCH, mods={"recoil": M(mul=0.5), "spread": M(mul=1.1)}, effects=["shockwave"], cost=[("minecraft:iron_block", 1), ("minecraft:anvil", 1)]),
    "spread_choke":        part("muzzle", 2, archetypes=["shotgun", "flamethrower", "smg"], mods={"spread": M(mul=1.4), "pellets": M(add=2)}, effects=["spray"], cost=[("minecraft:iron_ingot", 6), ("minecraft:hopper", 1)]),
    "plasma_vent":         part("muzzle", 2, alien=True, archetypes=ALIEN_ARCH, mods={"fire_rate": M(mul=1.2)}, effects=["volatile"], themes=["plasma", "nether", "solar"], cost=[("interstellar:plasma_core", 1)]),
    "arc_vent":            part("muzzle", 3, alien=True, archetypes=ALIEN_ARCH, effects=["chain"], themes=["tech", "hydro", "sonic"], cost=[("interstellar:tech_chip", 2), ("minecraft:lightning_rod", 1)]),
    # ---------------------------------------------------------------- underbarrel
    "grenade_launcher":    part("underbarrel", 4, archetypes=["assault_rifle", "battle_rifle", "lmg", "shotgun", "carbine"], effects=["grenade"], cost=[("minecraft:tnt", 4), ("minecraft:netherite_ingot", 1)]),
    "laser_sight":         part("underbarrel", 2, archetypes=HUMAN_ARCH, mods={"spread": M(mul=0.75)}, effects=["mark"], cost=[("minecraft:redstone_block", 1), ("minecraft:glass_pane", 1)]),
    "bayonet":             part("underbarrel", 1, archetypes=HUMAN_ARCH, effects=["bayonet"], cost=[("minecraft:iron_sword", 1)]),
    "grapple_hook":        part("underbarrel", 3, archetypes=HUMAN_ARCH, effects=["pull"], cost=[("minecraft:tripwire_hook", 2), ("minecraft:chain", 4), ("minecraft:iron_block", 1)]),
    "shield_plate":        part("underbarrel", 3, archetypes=HUMAN_ARCH, mods={"spread": M(mul=1.1)}, effects=["parry"], cost=[("minecraft:shield", 1), ("minecraft:iron_block", 2)]),
    "flashlight":          part("underbarrel", 1, archetypes=HUMAN_ARCH, effects=["blind"], cost=[("minecraft:glowstone", 2), ("minecraft:iron_ingot", 2)]),
    # ---------------------------------------------------------------- human blade parts
    "saber_hilt":          part("receiver", 3, archetypes=HUMAN_BLADES, effects=["parry"], cost=[("minecraft:iron_block", 2), ("minecraft:redstone_block", 1), ("minecraft:lightning_rod", 1)]),
    "crossguard_hilt":     part("receiver", 4, archetypes=HUMAN_BLADES, mods={"damage": M(mul=1.2), "fire_rate": M(mul=0.85)}, effects=["sweep"], cost=[("minecraft:netherite_ingot", 1), ("minecraft:iron_block", 2), ("minecraft:lightning_rod", 2)]),
    "duelist_hilt":        part("receiver", 4, archetypes=HUMAN_BLADES, mods={"fire_rate": M(mul=1.25), "damage": M(mul=0.9)}, effects=["lunge"], cost=[("minecraft:diamond", 4), ("minecraft:gold_block", 1), ("minecraft:lightning_rod", 1)]),
    "executioner_hilt":    part("receiver", 5, archetypes=HUMAN_BLADES, mods={"damage": M(mul=1.35), "fire_rate": M(mul=0.75)}, effects=["execute"], cost=[("minecraft:netherite_ingot", 2), ("minecraft:wither_skeleton_skull", 1)]),
    "guardian_hilt":       part("receiver", 3, archetypes=HUMAN_BLADES, mods={"range": M(mul=1.1)}, effects=["steadfast"], cost=[("minecraft:iron_block", 3), ("minecraft:shield", 1)]),
    "amethyst_focus":      part("core", 2, archetypes=HUMAN_BLADES + ["railgun"], effects=["shatter"], color="#c77dff", cost=[("minecraft:amethyst_shard", 8), ("minecraft:redstone_block", 2)]),
    "diamond_focus":       part("core", 3, archetypes=HUMAN_BLADES + ["railgun"], mods={"damage": M(mul=1.15)}, effects=["pierce"], color="#5ef0ff", cost=[("minecraft:diamond", 4), ("minecraft:redstone_block", 2)]),
    "echo_focus":          part("core", 4, archetypes=HUMAN_BLADES + ["railgun"], mods={"armor_pierce": M(add=0.4), "range": M(mul=1.15)}, effects=["darkness"], color="#1d6fff", cost=[("minecraft:echo_shard", 4), ("minecraft:nether_star", 1)]),
    "emerald_focus":       part("core", 3, archetypes=HUMAN_BLADES + ["railgun"], mods={"reload_time": M(mul=0.7)}, effects=["leech"], color="#36ff6a", cost=[("minecraft:emerald", 6), ("minecraft:redstone_block", 2)]),
    "ruby_focus":          part("core", 3, archetypes=HUMAN_BLADES + ["railgun"], mods={"damage": M(mul=1.1)}, effects=["ignite"], color="#ff2a2a", cost=[("minecraft:redstone_block", 4), ("minecraft:blaze_rod", 2)]),
    "sunstone_focus":      part("core", 4, archetypes=HUMAN_BLADES + ["railgun"], mods={"fire_rate": M(mul=1.1)}, effects=["blind"], color="#ffd23a", cost=[("minecraft:glowstone", 6), ("minecraft:gold_block", 2)]),
    "prismarine_focus":    part("core", 3, archetypes=HUMAN_BLADES + ["railgun"], mods={"range": M(mul=1.2)}, effects=["knockback"], color="#3affd8", cost=[("minecraft:prismarine_crystals", 8), ("minecraft:heart_of_the_sea", 1)]),
    "wrapped_grip":        part("grip", 1, archetypes=HUMAN_BLADES, mods={"fire_rate": M(mul=1.1)}, effects=["adrenaline"], cost=[("minecraft:leather", 4), ("minecraft:iron_ingot", 2)]),
    "knurled_grip":        part("grip", 2, archetypes=HUMAN_BLADES, mods={"damage": M(mul=1.05)}, effects=["stagger"], cost=[("minecraft:iron_block", 1), ("minecraft:flint", 4)]),
    "chain_grip":          part("grip", 3, archetypes=HUMAN_BLADES, effects=["pull"], cost=[("minecraft:chain", 6), ("minecraft:iron_ingot", 2)]),
    # alien blade parts
    "energy_sword_hilt":   part("receiver", 3, alien=True, archetypes=ALIEN_BLADES, effects=["lunge"], cost=[("interstellar:alien_alloy", 8), ("minecraft:ender_eye", 2)]),
    "twin_prong_hilt":     part("receiver", 5, alien=True, archetypes=ALIEN_BLADES, mods={"damage": M(mul=1.3), "range": M(mul=1.2)}, effects=["sweep"], themes=["tech", "void", "crystal"], cost=[("interstellar:alien_alloy", 14), ("minecraft:nether_star", 1)]),
    "talon_hilt":          part("receiver", 3, alien=True, archetypes=ALIEN_BLADES, mods={"fire_rate": M(mul=1.2)}, effects=["bleed"], themes=["bio", "toxic", "nether"], cost=[("interstellar:alien_alloy", 8), ("interstellar:bio_gel", 2)]),
    "gravity_hilt":        part("receiver", 4, alien=True, archetypes=ALIEN_BLADES, mods={"damage": M(mul=1.15)}, effects=["slam"], themes=["void", "cryo", "sonic"], cost=[("interstellar:alien_alloy", 10), ("interstellar:void_shard", 2)]),
    "alien_blade_grip":    part("grip", 2, alien=True, archetypes=ALIEN_BLADES, mods={"fire_rate": M(mul=1.05)}, effects=["leech"], cost=[("interstellar:alien_alloy", 2)]),
    "ridged_blade_grip":   part("grip", 3, alien=True, archetypes=ALIEN_BLADES, mods={"damage": M(mul=1.08)}, effects=["parry"], cost=[("interstellar:alien_alloy", 3), ("minecraft:prismarine_shard", 4)]),
}

# Alien cores: the power source of every alien weapon (and alien blade). The theme decides the colour of shots and
# blades and the core's signature effect; the grade adds a second behaviour.
THEME_CORES = {
    "plasma":  ("interstellar:plasma_core", "#2fb8ff", {"damage": M(mul=1.1)}, "concussive"),
    "nether":  ("interstellar:nether_cinder", "#ff4a1c", {"damage": M(mul=1.15)}, "ignite"),
    "void":    ("interstellar:void_shard", "#9b30ff", {"armor_pierce": M(add=0.3)}, "levitate"),
    "crystal": ("interstellar:crystal_lattice", "#ff7cf2", {"headshot": M(add=0.5)}, "shatter"),
    "bio":     ("interstellar:bio_gel", "#7dff2a", {"effect_power": M(add=1)}, "poison"),
    "toxic":   ("interstellar:bio_gel", "#c6ff00", {"effect_power": M(add=2)}, "wither"),
    "cryo":    ("interstellar:cryo_crystal", "#a8f0ff", {"fire_rate": M(mul=0.9)}, "freeze"),
    "solar":   ("interstellar:solar_cell", "#ffc400", {"damage": M(mul=1.05), "velocity": M(mul=1.2)}, "blind"),
    "hydro":   ("interstellar:hydro_pearl", "#1e8bff", {"range": M(mul=1.2)}, "knockback"),
    "tech":    ("interstellar:tech_chip", "#36ffb0", {"reload_time": M(mul=0.8)}, "chain"),
    "sonic":   ("interstellar:sonic_resonator", "#14e0e0", {"armor_pierce": M(add=0.5)}, "nausea"),
}
GRADES = [("unstable", 0.9, "volatile"), ("stable", 1.0, "steadfast"), ("refined", 1.15, "echo"), ("ancient", 1.3, "reaper")]
for theme, (item, color, mods, fx) in THEME_CORES.items():
    for tier, (prefix, mul, grade_fx) in enumerate(GRADES, start=1):
        m = dict(mods)
        m["damage"] = M(mul=m.get("damage", M())["mul"] * mul)
        PARTS[f"{prefix}_{theme}_core"] = part("core", tier + 1 if tier > 1 else 1, alien=True, archetypes=ALIEN_CORE_ARCH, mods=m,
                                              effects=[fx, grade_fx], themes=[theme], color=color,
                                              cost=[(item, 2 * tier), ("interstellar:alien_alloy", 2 * tier)] + ([("minecraft:nether_star", 1)] if tier == 4 else []))

# Materials: what a frame or part is made of. Each has its own on-hit effect.
MATERIALS = {
    "iron":       ("minecraft:iron_ingot", "#d8d8d8", 1, {}, ["stagger"]),
    "copper":     ("minecraft:copper_ingot", "#c15a36", 1, {"reload_time": M(mul=0.95), "damage": M(mul=0.95)}, ["lightning"]),
    "gold":       ("minecraft:gold_ingot", "#fad64a", 2, {"reload_time": M(mul=0.8), "recoil": M(mul=1.1)}, ["ammo_saver"]),
    "quartz":     ("minecraft:quartz", "#e3d4c4", 2, {"spread": M(mul=0.9)}, ["mark"]),
    "amethyst":   ("minecraft:amethyst_shard", "#9a5cc6", 2, {"headshot": M(add=0.3)}, ["shatter"]),
    "prismarine": ("minecraft:prismarine_crystals", "#5ba69b", 2, {"range": M(mul=1.1)}, ["slow"]),
    "obsidian":   ("minecraft:obsidian", "#2a1d3d", 3, {"recoil": M(mul=0.8), "fire_rate": M(mul=0.95)}, ["steadfast"]),
    "diamond":    ("minecraft:diamond", "#4ee1d6", 3, {"damage": M(mul=1.15)}, ["execute"]),
    "emerald":    ("minecraft:emerald", "#17c544", 3, {"reload_time": M(mul=0.85), "magazine": M(mul=1.1)}, ["reaper"]),
    "echo":       ("minecraft:echo_shard", "#0d4f5c", 4, {"armor_pierce": M(add=0.25)}, ["darkness"]),
    "netherite":  ("minecraft:netherite_ingot", "#4a3e40", 4, {"damage": M(mul=1.25), "recoil": M(mul=0.9)}, ["armor_shred"]),
    "blaze":      ("minecraft:blaze_rod", "#f2a51a", 3, {}, ["ignite"]),
    "alien_alloy":     ("interstellar:alien_alloy", "#7d8ea8", 3, {"damage": M(mul=1.1), "reload_time": M(mul=0.9)}, ["adrenaline"]),
    "plasma":          ("interstellar:plasma_core", "#39c6ff", 4, {"damage": M(mul=1.2)}, ["concussive"]),
    "void":            ("interstellar:void_shard", "#2c0f4a", 5, {"armor_pierce": M(add=0.4)}, ["warp"]),
    "crystal":         ("interstellar:crystal_lattice", "#e98cff", 4, {"headshot": M(add=0.5)}, ["split"]),
    "cryo":            ("interstellar:cryo_crystal", "#a6e4ff", 4, {}, ["freeze"]),
    "solar":           ("interstellar:solar_cell", "#ffb300", 4, {"velocity": M(mul=1.3)}, ["blind"]),
    "bio":             ("interstellar:bio_gel", "#6fd12b", 3, {"effect_power": M(add=1)}, ["plague"]),
    "hydro":           ("interstellar:hydro_pearl", "#1f6fd1", 3, {"range": M(mul=1.15)}, ["knockback"]),
    "tech":            ("interstellar:tech_chip", "#3cf0a0", 4, {"reload_time": M(mul=0.75)}, ["echo"]),
    "sonic":           ("interstellar:sonic_resonator", "#0f8f8f", 5, {"armor_pierce": M(add=0.5)}, ["shockwave"]),
    "nether_cinder":   ("interstellar:nether_cinder", "#d4401c", 3, {"damage": M(mul=1.1)}, ["wither"]),
}
MATERIAL_THEMES = {"alien_alloy": [], "plasma": ["plasma", "solar"], "void": ["void"], "crystal": ["crystal"], "cryo": ["cryo"],
                   "solar": ["solar"], "bio": ["bio", "toxic"], "hydro": ["hydro"], "tech": ["tech"], "sonic": ["sonic"], "nether_cinder": ["nether"]}

MAGAZINES = {
    "pistol": [("minecraft:iron_nugget", 6), ("minecraft:gunpowder", 2)],
    "rifle": [("minecraft:iron_ingot", 2), ("minecraft:gunpowder", 4)],
    "sniper": [("minecraft:iron_ingot", 2), ("minecraft:gunpowder", 4), ("minecraft:copper_ingot", 2)],
    "shell": [("minecraft:paper", 2), ("minecraft:gunpowder", 3), ("minecraft:iron_nugget", 6)],
    "belt": [("minecraft:iron_ingot", 6), ("minecraft:gunpowder", 10)],
    "rocket": [("minecraft:tnt", 2), ("minecraft:iron_ingot", 3)],
    "fuel_canister": [("minecraft:blaze_powder", 3), ("minecraft:iron_ingot", 2)],
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


def check_unique_effects():
    """Within a slot, no two parts that can share a weapon have the same effects: one different choice always changes
    behaviour. Materials all differ too."""
    by_slot = {}
    for pid, p in PARTS.items():
        by_slot.setdefault(p["slot"], []).append((pid, p))
    for slot, entries in by_slot.items():
        for i, (a, pa) in enumerate(entries):
            for b, pb in entries[i + 1:]:
                shared = set(pa.get("archetypes", [])) & set(pb.get("archetypes", []))
                if shared and set(pa.get("effects", [])) == set(pb.get("effects", [])):
                    raise SystemExit(f"{a} and {b} ({slot}) have the same effects {pa.get('effects')}")
    for pid, p in PARTS.items():
        if not p.get("effects"):
            raise SystemExit(f"part {pid} has no effect")
    fx = [tuple(m[4]) for m in MATERIALS.values()]
    if len(set(fx)) != len(fx):
        raise SystemExit("two materials share an effect")


def main():
    check_unique_effects()
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    for k, spec in ARCHETYPES.items():
        alien, tier, fm, ammo, mag, stats, slots, req, themes = spec[:9]
        extra = spec[9] if len(spec) > 9 else {}
        if alien:
            cost = [("interstellar:alien_alloy", 8 + 4 * tier), ("minecraft:ender_eye", tier)]
        else:
            cost = [("minecraft:iron_block", 2 + tier), ("minecraft:diamond", tier), ("minecraft:redstone_block", 1)]
            if tier > 3:
                cost.append(("minecraft:netherite_ingot", tier - 3))
        write("archetype", k, {"alien": alien, "tier": tier, "fire_mode": fm, "ammo": ammo, "magazine": mag, "stats": stats,
                               "slots": slots, "required": req, "themes": themes, **extra,
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
