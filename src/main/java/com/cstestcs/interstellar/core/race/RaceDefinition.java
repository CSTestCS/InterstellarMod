package com.cstestcs.interstellar.core.race;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A fully resolved race (inheritance already applied). Immutable and cheap to share between threads. */
public record RaceDefinition(
        String id,
        String parentId,
        String displayName,
        Gender gender,
        String tailType,
        IdMatcher homeDimension,
        IdMatcher mobConstraint,
        IdMatcher homeBiome,
        double populationPercent,
        Disposition disposition,
        String favoriteItem,
        double favoriteItemPotency,
        float size,
        List<String> weapons,
        boolean fallback,
        boolean replaceBody,
        List<String> bodyTypes,
        boolean slimArms,
        float headScale,
        String faction,
        Set<String> allies,
        Set<String> enemies,
        double healthMultiplier,
        double speedMultiplier,
        double damageMultiplier,
        Set<String> traits,
        List<String> names,
        boolean playerSelectable,
        int weaponTier,
        String weaponTheme) {

    public enum Gender {
        MALE, FEMALE, NULL;

        public static Gender parse(String s) {
            if (s == null) return NULL;
            return switch (s.strip().toLowerCase(Locale.ROOT)) {
                case "male", "m", "man" -> MALE;
                case "female", "f", "woman" -> FEMALE;
                default -> NULL;
            };
        }

        /** Whether something tagged with {@code other} (a body type gender, "any" = NULL) suits this gender. */
        public boolean compatibleWith(Gender other) {
            return this == NULL || other == NULL || this == other;
        }
    }

    public enum Disposition {
        HOSTILE, NEUTRAL, PASSIVE;

        public static Disposition parse(String s, Disposition def) {
            if (s == null) return def;
            return switch (s.strip().toLowerCase(Locale.ROOT)) {
                case "hostile", "aggressive" -> HOSTILE;
                case "passive", "friendly", "peaceful" -> PASSIVE;
                case "neutral" -> NEUTRAL;
                default -> def;
            };
        }
    }

    public boolean isSubrace() {
        return parentId != null;
    }

    public boolean hasTrait(String trait) {
        return traits.contains(trait);
    }

    public boolean hasTail() {
        return tailType != null && !tailType.equals("none");
    }

    public boolean canBeSwayed() {
        return favoriteItem != null && favoriteItemPotency > 0;
    }

    /** Whether this race may be applied to a mob/biped in the given context (ignores population rolls). */
    public boolean accepts(SpawnContext ctx) {
        return mobConstraint.matches(ctx.entityType(), ctx.entityTags())
                && homeDimension.matches(ctx.dimension(), ctx.dimensionTags())
                && homeBiome.matches(ctx.biome(), ctx.biomeTags());
    }

    public boolean isAlliedWith(RaceDefinition other) {
        if (other == null) return false;
        if (faction != null && faction.equals(other.faction)) return true;
        return other.faction != null && allies.contains(other.faction);
    }

    public boolean isEnemyOf(RaceDefinition other) {
        if (other == null || isAlliedWith(other)) return false;
        return other.faction != null && enemies.contains(other.faction) || enemies.contains("race:" + other.id);
    }
}
