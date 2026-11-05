package com.cstestcs.interstellar.ship.plating;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block that can be plated with any full block (see {@link Plating}). Its block entity must be a
 * {@link PlatedBlockEntity}.
 */
public interface Platable {
    /** Whether breaking this block gives the plating back (only one half of a two-block door does). */
    default boolean dropsPlating(BlockState state) {
        return true;
    }

    /** The other block that shares this block's plating (the other half of a door), or null. */
    default @Nullable BlockPos partner(BlockState state, BlockPos pos) {
        return null;
    }
}
