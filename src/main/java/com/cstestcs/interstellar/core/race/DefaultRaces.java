package com.cstestcs.interstellar.core.race;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cstestcs.interstellar.core.race.RaceDetails.Key.*;

/**
 * The default race files written into the races folder on first launch: one per vanilla biped (all flagged
 * {@code Fallback: true} so they only apply when no custom race rolls) plus the alien races of the foreign worlds.
 */
public final class DefaultRaces {
    private DefaultRaces() {}

    /** Vanilla bipedal mobs that get a default race and can receive custom races. */
    public static final List<String> VANILLA_BIPEDS = List.of(
            "minecraft:player", "minecraft:mannequin", "minecraft:villager", "minecraft:wandering_trader",
            "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager", "minecraft:giant",
            "minecraft:skeleton", "minecraft:stray", "minecraft:bogged", "minecraft:parched", "minecraft:wither_skeleton",
            "minecraft:piglin", "minecraft:piglin_brute", "minecraft:zombified_piglin",
            "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:illusioner", "minecraft:witch",
            "minecraft:enderman");

    /** @return file name -> file contents for every default race file. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        vanilla(out, "human", "Human", "minecraft:player, minecraft:mannequin", "passive", null, 0, "sword, axe, bow, crossbow, gun", "humans", "");
        vanilla(out, "villager", "Villager", "minecraft:villager", "passive", "minecraft:emerald", 25, "", "villagers", "");
        vanilla(out, "wandering_trader", "Wandering Trader", "minecraft:wandering_trader", "passive", "minecraft:emerald", 20, "", "villagers", "");
        vanilla(out, "zombie", "Zombie", "minecraft:zombie, minecraft:giant", "hostile", null, 0, "sword, shovel", "undead", "humans, villagers");
        vanilla(out, "husk", "Husk", "minecraft:husk", "hostile", null, 0, "sword, shovel", "undead", "humans, villagers");
        vanilla(out, "drowned", "Drowned", "minecraft:drowned", "hostile", null, 0, "trident", "undead", "humans, villagers");
        vanilla(out, "zombie_villager", "Zombie Villager", "minecraft:zombie_villager", "hostile", "minecraft:golden_apple", 100, "", "undead", "humans, villagers");
        vanilla(out, "skeleton", "Skeleton", "minecraft:skeleton", "hostile", "minecraft:bone", 2, "bow", "undead", "humans, villagers");
        vanilla(out, "stray", "Stray", "minecraft:stray", "hostile", null, 0, "bow", "undead", "humans, villagers");
        vanilla(out, "bogged", "Bogged", "minecraft:bogged", "hostile", null, 0, "bow", "undead", "humans, villagers");
        vanilla(out, "parched", "Parched", "minecraft:parched", "hostile", null, 0, "bow", "undead", "humans, villagers");
        vanilla(out, "wither_skeleton", "Wither Skeleton", "minecraft:wither_skeleton", "hostile", null, 0, "sword", "nether_fortress", "humans, villagers, piglins");
        vanilla(out, "piglin", "Piglin", "minecraft:piglin", "neutral", "minecraft:gold_ingot", 20, "crossbow, sword", "piglins", "nether_fortress");
        vanilla(out, "piglin_brute", "Piglin Brute", "minecraft:piglin_brute", "hostile", "minecraft:gold_block", 10, "axe", "piglins", "humans, nether_fortress");
        vanilla(out, "zombified_piglin", "Zombified Piglin", "minecraft:zombified_piglin", "neutral", null, 0, "sword", "undead_piglins", "");
        vanilla(out, "pillager", "Pillager", "minecraft:pillager", "hostile", null, 0, "crossbow", "illagers", "humans, villagers");
        vanilla(out, "vindicator", "Vindicator", "minecraft:vindicator", "hostile", null, 0, "axe", "illagers", "humans, villagers");
        vanilla(out, "evoker", "Evoker", "minecraft:evoker", "hostile", null, 0, "", "illagers", "humans, villagers");
        vanilla(out, "illusioner", "Illusioner", "minecraft:illusioner", "hostile", null, 0, "bow", "illagers", "humans, villagers");
        vanilla(out, "witch", "Witch", "minecraft:witch", "hostile", "minecraft:nether_wart", 10, "", "witches", "humans");
        vanilla(out, "enderman", "Enderman", "minecraft:enderman", "neutral", "minecraft:chorus_fruit", 10, "", "end", "");
        AlienRaces.write(out);
        return out;
    }

    private static void vanilla(Map<String, byte[]> out, String id, String name, String mobs, String disposition,
            String favorite, int potency, String weapons, String faction, String enemies) {
        Map<RaceDetails.Key, String> v = new EnumMap<>(RaceDetails.Key.class);
        v.put(RACE_NAME, name);
        v.put(GENDER, "null");
        v.put(TAIL_TYPE, "none");
        v.put(MOB_CONSTRAINT, mobs);
        v.put(POPULATION_PERCENT, "100");
        v.put(DEFAULT_DISPOSITION, disposition);
        if (favorite != null) v.put(FAVORITE_ITEM, favorite);
        v.put(FAVORITE_ITEM_POTENCY, Integer.toString(potency));
        v.put(RACE_SIZE, "1.0");
        v.put(WEAPONS, weapons);
        v.put(FALLBACK, "true");
        v.put(REPLACE_BODY, "false");
        v.put(BODY_TYPE, "any");
        v.put(FACTION, faction);
        v.put(ENEMIES, enemies);
        v.put(PLAYER_SELECTABLE, id.equals("human") ? "true" : "false");
        out.put(id + "_details.txt", text(RaceDetailsParser.write("Default race for " + mobs + " (generated by Interstellar).\nEdit freely; delete the file to regenerate it.", v)));
    }

    static byte[] text(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
