package com.cstestcs.interstellar.core.race;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Raw, un-resolved contents of a {@code RACENAME_details.txt} file.
 * <p>
 * Every key is optional. Keys that are absent (or blank) are "empty settings": for subraces they inherit the
 * parent's resolved value, for root races they fall back to {@link RaceDefinition} defaults.
 * Key matching is case-insensitive and ignores spaces/underscores, so {@code Home Biome:} == {@code HomeBiome:}.
 */
public final class RaceDetails {
    /** All keys understood by the parser, in the order they are written to generated files. */
    public enum Key {
        RACE_NAME("RaceName", "Display name shown in game."),
        SUBRACE("Subrace", "Id of an existing race this one is a subrace of. Empty settings inherit from it."),
        GENDER("Gender", "male, female or null."),
        TAIL_TYPE("Tailtype", "none, cat, fox, wolf, lizard, dragon, demon, monkey, rabbit, fish. Colour comes from the top-left 8x8 of RACENAME_skin.png."),
        HOME_DIMENSION("HomeDimension", "Comma separated dimension ids / #tags / namespace:* / !negations. Empty = any."),
        MOB_CONSTRAINT("MobConstraint", "Comma separated entity ids / #tags this race may be applied to. Empty = any biped."),
        HOME_BIOME("HomeBiome", "Comma separated biome ids / #tags. Empty = any."),
        POPULATION_PERCENT("PopulationPercent", "0-100. Share of valid bipeds (or, for subraces, of the parent race) that become this race."),
        DEFAULT_DISPOSITION("DefaultDisposition", "hostile, neutral or passive."),
        FAVORITE_ITEM("FavoriteItem", "Item id (or #tag) that sways members of this race. Empty = cannot be recruited."),
        FAVORITE_ITEM_POTENCY("FavoriteItemPotency", "0-100. Percent of full affection each favourite item gives. 0 = cannot be swayed, 100 = one item."),
        RACE_SIZE("RaceSize", "Scale multiplier, 1.0 = normal."),
        WEAPONS("Weapons", "Preferred weapon kinds (one is picked per mob): sword, axe, bow, crossbow, trident, mace, gun, gun:<theme>, blade (laser/energy sword), or item ids."),
        // ---- additional keys needed to fully define a race ----
        FALLBACK("Fallback", "true = only chosen when no other race rolled for this mob (used by the vanilla default races)."),
        REPLACE_BODY("ReplaceBody", "true = replace the mob's model with a base biped using RACENAME_skin.png."),
        BODY_TYPE("BodyType", "Comma separated custom body names (custommodels) this race prefers, 'any' or 'none'."),
        SLIM_ARMS("SlimArms", "true = 3px arms on the base biped."),
        HEAD_SCALE("HeadScale", "Extra scale applied to the head model only."),
        FACTION("Faction", "Faction id. Members of the same faction never fight each other."),
        ALLIES("Allies", "Comma separated faction ids treated as friends."),
        ENEMIES("Enemies", "Comma separated faction ids attacked on sight (or race:<id>)."),
        HEALTH_MULTIPLIER("HealthMultiplier", "Max health multiplier."),
        SPEED_MULTIPLIER("SpeedMultiplier", "Movement speed multiplier."),
        DAMAGE_MULTIPLIER("DamageMultiplier", "Attack damage multiplier."),
        TRAITS("Traits", "fire_immune, water_breathing, night_vision, slow_fall, no_fall_damage, regeneration."),
        NAMES("Names", "Comma separated name pool. Mobs of this race get a random name from it."),
        PLAYER_SELECTABLE("PlayerSelectable", "true = players may pick this race themselves with /race choose <race>."),
        WEAPON_TIER("WeaponTier", "0-5. Quality of rolled guns for aliens of this race."),
        WEAPON_THEME("WeaponTheme", "Gun material/theme used when rolling weapons, e.g. human, nether, void, crystal, plasma.");

        public final String fileKey;
        public final String help;

        Key(String fileKey, String help) {
            this.fileKey = fileKey;
            this.help = help;
        }

        private static final Map<String, Key> LOOKUP = new LinkedHashMap<>();

        static {
            for (Key k : values()) LOOKUP.put(normalize(k.fileKey), k);
            LOOKUP.put("tail", TAIL_TYPE);
            LOOKUP.put("size", RACE_SIZE);
            LOOKUP.put("name", RACE_NAME);
            LOOKUP.put("parent", SUBRACE);
            LOOKUP.put("population", POPULATION_PERCENT);
            LOOKUP.put("disposition", DEFAULT_DISPOSITION);
            LOOKUP.put("homedimensions", HOME_DIMENSION);
            LOOKUP.put("homebiomes", HOME_BIOME);
            LOOKUP.put("mobconstraints", MOB_CONSTRAINT);
        }

        public static Key byName(String raw) {
            return LOOKUP.get(normalize(raw));
        }

        static String normalize(String s) {
            return s.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
        }
    }

    private final String id;
    private final Map<Key, String> values;
    private final List<String> warnings;

    RaceDetails(String id, Map<Key, String> values, List<String> warnings) {
        this.id = id;
        this.values = Collections.unmodifiableMap(values);
        this.warnings = List.copyOf(warnings);
    }

    /** Lower-case race id, derived from the file prefix (RACENAME). */
    public String id() {
        return id;
    }

    /** @return the raw value or {@code null} if the setting is empty. */
    public String get(Key key) {
        return values.get(key);
    }

    public Map<Key, String> values() {
        return values;
    }

    public List<String> warnings() {
        return warnings;
    }
}
