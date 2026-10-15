package com.cstestcs.interstellar.core.race;

import java.util.Set;

/** Everything race selection needs to know about where and what a mob is. All ids are fully qualified strings. */
public record SpawnContext(
        String entityType,
        Set<String> entityTags,
        String dimension,
        Set<String> dimensionTags,
        String biome,
        Set<String> biomeTags) {

    public static SpawnContext of(String entityType, String dimension, String biome) {
        return new SpawnContext(entityType, Set.of(), dimension, Set.of(), biome, Set.of());
    }
}
