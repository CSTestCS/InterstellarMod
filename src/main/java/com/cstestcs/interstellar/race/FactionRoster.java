package com.cstestcs.interstellar.race;

import com.cstestcs.interstellar.core.race.RaceDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The alien factions that may appear in a dimension: every loaded root race that alien mobs can have and whose home
 * includes the dimension, grouped by faction. Fully data driven, so factions added in race files take part too.
 */
public final class FactionRoster {
    private FactionRoster() {}

    /** Faction id -> member races (root races) that can live in the dimension. */
    public static Map<String, List<RaceDefinition>> factions(String dimension) {
        String home = RaceEvents.homeKey(dimension);
        Map<String, List<RaceDefinition>> out = new LinkedHashMap<>();
        for (RaceDefinition r : RaceManager.registry().all()) {
            if (r.parentId() != null || r.fallback() || r.populationPercent() <= 0) continue;
            if (r.mobConstraint().isAny() || !r.mobConstraint().matches("interstellar:alien", Set.of())) continue;
            if (!r.homeDimension().matches(home, Set.of())) continue;
            out.computeIfAbsent(r.faction() == null || r.faction().isEmpty() ? r.id() : r.faction(), k -> new ArrayList<>()).add(r);
        }
        return out;
    }

    /** A faction that can appear in the dimension, weighted by its members' populations (null if none). */
    public static @Nullable List<RaceDefinition> pickFaction(String dimension, RandomSource random) {
        Map<String, List<RaceDefinition>> all = factions(dimension);
        double total = 0;
        for (List<RaceDefinition> f : all.values()) total += weight(f);
        if (total <= 0) return null;
        double roll = random.nextDouble() * total;
        for (List<RaceDefinition> f : all.values()) {
            roll -= weight(f);
            if (roll <= 0) return f;
        }
        return all.values().iterator().next();
    }

    /** The members of one faction (by id) that can appear in the dimension. */
    public static List<RaceDefinition> members(String faction, String dimension) {
        return factions(dimension).getOrDefault(faction, List.of());
    }

    /** A member of the faction (weighted by population), then possibly one of its subraces. */
    public static RaceDefinition pickMember(List<RaceDefinition> faction, RandomSource random) {
        double roll = random.nextDouble() * weight(faction);
        RaceDefinition chosen = faction.get(faction.size() - 1);
        for (RaceDefinition r : faction) {
            roll -= r.populationPercent();
            if (roll <= 0) {
                chosen = r;
                break;
            }
        }
        for (RaceDefinition sub : RaceManager.registry().subracesOf(chosen.id())) {
            if (random.nextDouble() * 100 < sub.populationPercent()) return sub;
        }
        return chosen;
    }

    private static double weight(List<RaceDefinition> f) {
        double w = 0;
        for (RaceDefinition r : f) w += r.populationPercent();
        return w;
    }
}
