package com.cstestcs.interstellar.space.planet;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

/**
 * Generator of the space dimension: empty (a flat world without layers), except for the procedural planets, which
 * are written into each chunk as it is generated, so exploring space never stalls the server.
 */
public class SpaceChunkGenerator extends FlatLevelSource {
    public static final MapCodec<SpaceChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(FlatLevelSource::settings)
    ).apply(i, SpaceChunkGenerator::new));

    public SpaceChunkGenerator(FlatLevelGeneratorSettings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        SpacePlanets.build(level.registryAccess(), chunk);
    }
}
