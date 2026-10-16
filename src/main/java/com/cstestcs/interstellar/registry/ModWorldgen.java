package com.cstestcs.interstellar.registry;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.space.planet.PlanetChunkGenerator;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    private ModWorldgen() {}

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR,
            Interstellar.MODID);

    /** Procedural planet terrain (see {@link PlanetChunkGenerator}). */
    public static final Supplier<MapCodec<PlanetChunkGenerator>> PLANET = CHUNK_GENERATORS.register("planet", () -> PlanetChunkGenerator.CODEC);
}
