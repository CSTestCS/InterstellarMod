package com.cstestcs.interstellar.client;

import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Alien ores take the colour of the stone they are set in: the base stone of the foreign world being rendered (grey
 * anywhere else). The ore's grey stone texture is tint layer 0; its specks are untinted.
 */
public final class OreTint {
    private static final int STONE = 0xFF8F8F8F;
    private static final Map<String, Integer> BY_DIMENSION = new ConcurrentHashMap<>();

    private OreTint() {}

    static int current() {
        var level = Minecraft.getInstance().level;
        if (level == null) return STONE;
        return BY_DIMENSION.computeIfAbsent(level.dimension().identifier().toString(), dim -> {
            ForeignWorlds.World w = ForeignWorlds.byDimension(dim);
            if (w == null) return STONE;
            Block base = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(w.defaultBlock())).orElse(null);
            return base == null ? STONE : 0xFF000000 | base.defaultMapColor().col;
        });
    }

    public static void register(RegisterColorHandlersEvent.BlockTintSources event) {
        BlockTintSource tint = new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return STONE;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return current();
            }
        };
        event.register(List.of(tint), ModBlocks.ALIEN_ORES.values().stream().map(b -> (Block) b.get()).toArray(Block[]::new));
    }
}
