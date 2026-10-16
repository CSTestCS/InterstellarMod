package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds the weapon or ammunition loaded into a {@link CannonBlock}; the barrel size follows it. */
public class CannonBlockEntity extends BlockEntity {
    public static final String WEAPON_KEY = "weapon";
    private ItemStack weapon = ItemStack.EMPTY;

    public CannonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CANNON_ENTITY.get(), pos, state);
    }

    public ItemStack weapon() {
        return weapon;
    }

    public void setWeapon(ItemStack stack) {
        weapon = stack;
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            int size = ShipWeapons.firepower(stack);
            if (state.hasProperty(CannonBlock.SIZE) && state.getValue(CannonBlock.SIZE) != size) {
                level.setBlock(worldPosition, state.setValue(CannonBlock.SIZE, size), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        weapon = in.read(WEAPON_KEY, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!weapon.isEmpty()) out.store(WEAPON_KEY, ItemStack.OPTIONAL_CODEC, weapon);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !weapon.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), weapon);
    }
}
