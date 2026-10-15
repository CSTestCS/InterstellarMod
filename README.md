# Interstellar (NeoForge, Minecraft 26.3)

Races and subraces, FBX custom bodies driven by Mixamo animations, buildable spacecraft, a space dimension of
block-built planets, 20w14∞-style foreign worlds, modular gunsmithing and alien factions.

Built on the official `MDK-26.3-ModDevGradle` template (NeoForge `26.3.0.76`, Java 25).

> **Build status:** the Minecraft-independent core (`com.cstestcs.interstellar.core`: race files, Blockbench
> model interpreter, FBX reader/writer, skinning and retargeting, default bodies, planet layout, ship scanner, gun
> stats) is compiled and covered by 23 passing unit tests. The Minecraft/NeoForge integration was written against
> NeoForge's 26.3 sources but **has not been compiled yet**, because the environment it was written in could not
> reach `maven.neoforged.net`. Run `./gradlew build` first and expect a round of API fixes (see
> [Verification](#verification)).

---

## Folders (created on first launch under `config/interstellar/`)

| Folder | Side | Contents |
|---|---|---|
| `races/` | host (server / singleplayer) | Race files. Synced to every client automatically. |
| `custommodels/` | each client | Body models (`NAME.fbx` + optional `NAME.txt`). |
| `customanimations/` | each client | Mixamo animation clips (`*.fbx`), optionally in a sub folder per body. |

## 1. Races and subraces

Every file in `races/` belongs to a race named by its prefix:

| File | Purpose |
|---|---|
| `RACENAME_details.txt` | Definition (below). |
| `RACENAME_head.java` | Blockbench "Modded Entity" Java export. Replaces the head of any biped of this race. Interpreted at runtime, so no compiling is needed. A part named `head` is aligned with the mob's head pivot. |
| `RACENAME_skin.png` | 64x64 biped skin, used when `ReplaceBody: true`. Its top-left 8x8 pixels colour the tail. |
| `RACENAME_headskin.png` | Texture of the head model. |
| `RACENAME_skin_01.png`, `RACENAME_headskin_2.png`, … | Variants. Each mob picks a body variant and a head variant independently. |

`RACENAME_details.txt` keys (case-insensitive; empty values inherit from the `Subrace` parent, else use the default):

| Key | Meaning |
|---|---|
| `RaceName` | In-game name. |
| `Subrace` | Parent race id. Empty settings inherit from it, and spawned mobs of the parent may become this subrace (rolled with `PopulationPercent`, recursively). |
| `Gender` | `male`, `female` or `null`. |
| `Tailtype` | `none`, `cat`, `fox`, `wolf`, `lizard`, `dragon`, `demon`, `monkey`, `rabbit`, `fish`. |
| `HomeDimension` / `MobConstraint` / `HomeBiome` | Comma-separated ids, `#tags`, `namespace:*`, `!exclusions`. Empty = any. |
| `PopulationPercent` | Share of valid bipeds (or, for a subrace, of its parent) that become this race. |
| `DefaultDisposition` | `hostile`, `neutral`, `passive`. |
| `FavoriteItem` / `FavoriteItemPotency` | Item (or `#tag`) that wins the mob over; potency 0–100 is how much affection one item gives (100 = recruited at once, 0 = cannot be swayed). Recruited mobs follow and defend you. |
| `RaceSize` | Scale, 1.0 = normal. |
| `Weapons` | Preferred weapons: `sword`, `axe`, `bow`, `crossbow`, `trident`, `mace`, `gun`, `gun:<theme>`, or item ids. |
| `Fallback` | Only used when no other race rolled (the generated vanilla races). |
| `ReplaceBody`, `SlimArms`, `BodyType` | Replace the model with a biped wearing the race skin; preferred custom bodies (`any`, `none` or names). |
| `HeadScale` | Extra head scale. |
| `Faction`, `Allies`, `Enemies` | Same-faction and allied mobs never fight. Enemies (`faction` or `race:<id>`) are attacked on sight. |
| `HealthMultiplier`, `SpeedMultiplier`, `DamageMultiplier` | Stat multipliers. |
| `Traits` | `fire_immune`, `water_breathing`, `night_vision`, `slow_fall`, `no_fall_damage`, `glowing`, `regeneration`. |
| `Names` | Name pool for mobs. |
| `PlayerSelectable` | Lets players pick it with `/race choose <race>`. |
| `WeaponTier`, `WeaponTheme` | Quality and theme of rolled guns. |

A default race is generated for every vanilla biped (player, villager, wandering trader, zombie family, skeleton
family, piglins, illagers, witch, enderman, mannequin). Bipeds are listed in the entity-type tag
`interstellar:bipeds`, so other mods' bipeds can be added with a datapack. Raced villagers that replace their body
still wear their biome, profession and level clothes on the biped.

## 2. Custom bodies (client side)

Drop a Mixamo-rigged `NAME.fbx` (FBX Binary) into `custommodels/`. It uses the standard 64x64 skin layout, keeps
the mob's own head (or its race's head), and is driven by:

* clips from `customanimations/` (matched by file name: `Idle`, `Walking`, `Standard Run`, `Crouched Walking`,
  `Jumping`, `Falling Idle`, `Swimming`, `Sitting Idle`, `Flying`, `Climbing`, `Punching`, `Rifle Aiming`, `Dying`, …).
  Retargeting uses world-space rotation deltas, so any T-posed Mixamo clip fits any T-posed humanoid rig.
* otherwise the vanilla limb angles, so a body works with an empty animations folder.

Armour and any other biped layer (vanilla, modded, custom-model armour) is bent onto the body: every vertex is
mapped to the matching limb chain, scaled to the limb's measured thickness and skinned with the same bones as the
mesh. That means it stretches to fit and bends at elbows and knees.

`NAME.txt` sets `DisplayName`, `Gender: male | female | any | null` and `Weight`. **Consistency:** each client
reports its installed bodies, and the server picks a body per entity from the union of those reports and stores it
on the entity. Every client with that body installed shows the same body; clients without it see the vanilla model.

Five bodies are generated on first launch as real Mixamo-rigged FBX files: `classic`, `buff` (male), `slim`
(female), `minifig` and `storymode`. Preview: [`docs/default-bodies-preview.png`](docs/default-bodies-preview.png).

Client config (`config/interstellar-client.toml`) can turn bodies, animations and armour bending on or off, disable
specific bodies, choose your own body, and set a render distance.

## 3. Spacecraft

1. Place **Landing Gear** as the bottom layer and build on top of it.
2. Include at least one **Ship Generator**, **Control Panel** and **Life Support**. Optional parts:
   **Passenger Seat**, **Ship Cannon**, **Thruster**, **Hull Plating**. Which block counts as which part is decided
   by block tags (`interstellar:ship/*`).
3. Right-click the control panel to take off. The ship becomes an entity and the camera switches to third person.

| Input | Action |
|---|---|
| Look | Steer (always moving, like an infinitely boosted elytra) |
| Hold *use* | Boost (cruise → max speed) |
| Hold *attack* | Fire every Ship Cannon |
| *Sneak* | Halt: snap to the grid and turn back into blocks, with orientations kept |
| Shift-right-click the panel (as blocks) | Landing procedure: descend straight down; within 2 blocks of the ground, realign and extend the landing gear onto it |

Seated mobs come along; leashed or recruited mobs brought near an empty seat sit down by themselves; interacting
with a seated mob while holding a lead takes it out. A passenger sneaking in flight gets a confirmation prompt
first. If the pilot dies, the next player passenger takes the controls. The block limit and speeds are in the
server config.

## 4. Space

Fly 400 blocks above a dimension's build limit (configurable) to enter **space**: an empty, end-like void. In space,
the ship and everyone aboard are scaled to 0.1 (configurable), and halting, landing and dismounting are disabled.
Every dimension (vanilla, Interstellar's, and other mods') has a block-built planet: 1 block for the Nether and moons,
up to 16³ for the Overworld. Each planet is wrapped in an invisible, glowing **atmosphere** shell. Touching it moves
the ship to that dimension at X=0, Z=0, 200 blocks above the build limit, at normal scale.

Players who die in space have their inventory stored. Restore it with
`/interstellar spaceinventory restore <player>` (operators only).

## 5. Foreign worlds (20w14∞)

Twelve procedurally defined worlds (`interstellar:foreign_0` … `foreign_11`), each with its own terrain template,
blocks, fluid, sea level, sky, fog and water colours. They are reachable only as planets. Aliens spawn there
instead of villagers, illagers and zombies.

## 6. Gunsmithing

There is a single **gun** item, a single **magazine** item, and 11 alien materials. Everything else is data:

* `data/<ns>/gun/archetype/*.json`: 22 families inspired by Halo, Destiny and Call of Duty (pistol, hand cannon,
  SMG, assault, battle, pulse and scout rifles, DMR, sniper, shotgun, LMG, rocket launcher; plasma pistol and
  rifle, needler, fusion rifle, beam rifle, fuel rod cannon, shard launcher, void lance, sonic disruptor, bio spitter).
* `gun/part/*.json`: 83 parts across 9 slots (receiver, barrel, stock, grip, magazine, sight, muzzle,
  underbarrel, alien core). Each has stat modifiers, fire-mode/ammo overrides, effects and a model.
* `gun/material/*.json`: 23 materials. A part takes the material in the table's material slot, which adds its
  colour, stat changes and on-hit effects (ignite, freeze, poison, chain, pierce, shatter, levitate…).
* Trims: an armour-trim template plus a trim material recolours the gun's trim layer.

Each installed part changes the model; materials tint each part. Guns have no durability. Reload with **R**
using a magazine of the gun's kind; refill magazines at the **Gunsmith Table**. Human weapons need late-game
overworld materials (diamond, netherite, nether stars). Alien weapons need alien materials, so for those, aliens
(30% gun drop chance, randomly rolled builds) are the easier source.

Regenerate the default data with `python3 tools/gen_gun_data.py`.

## 7. Aliens

One alien race per foreign world, plus Elite and Scout subraces. Each has a procedurally painted skin, a
Blockbench-format head (antennae, eye clusters, tentacles…), a faction, a disposition, a favourite item and a weapon
theme matching its world. They are ordinary race files in `races/`, so you can edit or replace them.

## Commands

```
/race choose <race>                                   (players; PlayerSelectable races)
/interstellar race list | reload | info <entity> | set <targets> <race>
/interstellar body set <targets> <name|none>
/interstellar spaceinventory restore|count <player>
/interstellar planets list | rebuild
/interstellar space <player>                          (teleport into space, for testing)
/interstellar gun give <player> <archetype> [tier]
```

## Development

```
./gradlew build          # compile + unit tests (needs maven.neoforged.net)
./gradlew runClient
python3 tools/gen_gun_data.py    # gun archetypes/parts/materials
python3 tools/gen_assets.py      # textures, models, item definitions, lang, tags, recipes, loot
python3 tools/gen_worlds.py <26.3 vanilla data dir> worlds.json   # space + foreign worlds (see script header)
```

Layout: `core/` is plain Java (no Minecraft imports) and unit tested; `race/`, `body/`, `ship/`, `space/`, `gun/`,
`alien/` are the common integration; `client/` and `mixin/` are client only. The only mixin (`ModelMixin`) lets the
body renderer take over drawing of biped models. Its injectors are optional (`defaultRequire: 0`), so a mismatch
disables custom bodies instead of crashing.

## Verification

* ✅ `core` compiles and its 23 unit tests pass: race parsing, inheritance and selection; Blockbench model
  interpretation; FBX round trip; body normalisation; vanilla-driven posing; Mixamo retargeting; armour mapping;
  ship scanning; planet layout; gun stats, rolls and the shipped gun data.
* ✅ Default bodies were rendered offline to check proportions and UVs (`docs/default-bodies-preview.png`).
* ⚠️ Minecraft/NeoForge code: written against NeoForge 26.3 sources and tests, but not yet compiled or run.
  The most likely spots for API adjustments are rendering (`client/`), item-model property signatures
  (`GunItemModels`), `SavedDataType` constructors, and GUI extraction methods.
* ⚠️ Gun display transforms and placeholder textures will need tuning in game.
