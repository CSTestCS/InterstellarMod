package com.cstestcs.interstellar.ship.plating;

import com.cstestcs.interstellar.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import org.jspecify.annotations.Nullable;

/**
 * Remembers the block a hull block is plated with. The plating is saved with the block, sent to clients (which draw
 * the hull block covered with it) and carried through ship assembly like any other block entity data.
 */
public class PlatedBlockEntity extends BlockEntity {
    public static final String KEY = "plating";
    private @Nullable BlockState plating;

    public PlatedBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlocks.PLATED_ENTITY.get(), pos, state);
    }

    protected PlatedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public @Nullable BlockState plating() {
        return plating;
    }

    public void setPlating(@Nullable BlockState plating) {
        this.plating = plating;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            if (level.isClientSide()) requestModelDataUpdate();
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        BlockState before = plating;
        plating = in.read(KEY, BlockState.CODEC).orElse(null);
        if (level != null && level.isClientSide() && before != plating) {
            requestModelDataUpdate();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (plating != null) out.store(KEY, BlockState.CODEC, plating);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public ModelData getModelData() {
        return plating == null ? ModelData.EMPTY : ModelData.of(Plating.PROPERTY, plating);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && plating != null && state.getBlock() instanceof Platable p && p.dropsPlating(state)) {
            Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(plating.getBlock()));
        }
    }
}
