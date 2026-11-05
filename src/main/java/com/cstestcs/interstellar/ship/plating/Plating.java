package com.cstestcs.interstellar.ship.plating;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

/**
 * Plating: hull plating, its shapes and the ship parts can be covered with any full block. Right-click one with the
 * block in hand to plate it (it takes one block, and gives back the plating it had); right-click with a pickaxe to
 * pry the plating off. Sneak to place blocks against a platable block instead of plating it. A plated block
 * looks like its plating (the shapes and parts keep their own shape, covered with it) and breaks, resists
 * explosions, sounds and needs the tool of its plating. Breaking it drops the plating too.
 */
public final class Plating {
    /** Model data a plated block's block entity gives its model: the plating's block state. */
    public static final ModelProperty<BlockState> PROPERTY = new ModelProperty<>();

    private Plating() {}

    public static @Nullable BlockState get(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlatedBlockEntity be ? be.plating() : null;
    }

    /** Full, opaque, ordinary blocks (no block entities, no other platable blocks, nothing unbreakable). */
    public static boolean canPlateWith(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof Platable || b instanceof EntityBlock || s.isAir()) return false;
        if (s.getRenderShape() != RenderShape.MODEL || !s.canOcclude()) return false;
        if (s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0) return false;
        return Block.isShapeFullBlock(s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    /** The plating a block item gives: its default state, pillars turned along the clicked face. */
    public static @Nullable BlockState platingOf(ItemStack stack, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        BlockState s = item.getBlock().defaultBlockState();
        if (s.hasProperty(BlockStateProperties.AXIS)) s = s.setValue(BlockStateProperties.AXIS, hit.getDirection().getAxis());
        return canPlateWith(s) ? s : null;
    }

    /**
     * Right-click with a block: plates the block (or changes its plating); with a pickaxe: takes the plating off.
     * @return null if the item does neither (the block then handles the click as usual)
     */
    public static @Nullable InteractionResult use(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (stack.is(net.minecraft.tags.ItemTags.PICKAXES)) return strip(state, level, pos, player);
        BlockState plating = platingOf(stack, hit);
        if (plating == null || !(level.getBlockEntity(pos) instanceof PlatedBlockEntity be)) return null;
        BlockState old = be.plating();
        if (old == plating) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        set(level, pos, state, plating);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (old != null) give(player, level, pos, old);
        }
        SoundType sound = plating.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        return InteractionResult.SUCCESS;
    }

    /** Takes the plating off. @return null if there is none */
    public static @Nullable InteractionResult strip(BlockState state, Level level, BlockPos pos, Player player) {
        BlockState old = get(level, pos);
        if (old == null) return null;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        set(level, pos, state, null);
        if (!player.getAbilities().instabuild) give(player, level, pos, old);
        SoundType sound = old.getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        return InteractionResult.SUCCESS;
    }

    /** Sets the plating of a block and of the block sharing it (the other half of a door). */
    public static void set(Level level, BlockPos pos, BlockState state, @Nullable BlockState plating) {
        if (level.getBlockEntity(pos) instanceof PlatedBlockEntity be) be.setPlating(plating);
        BlockPos other = state.getBlock() instanceof Platable p ? p.partner(state, pos) : null;
        if (other != null && level.getBlockEntity(other) instanceof PlatedBlockEntity be) be.setPlating(plating);
    }

    /** Block entity data holding only a plating (for designs and generated ships), or null without plating. */
    public static net.minecraft.nbt.@Nullable CompoundTag tagOf(net.minecraft.world.level.block.entity.@Nullable BlockEntity be) {
        return be instanceof PlatedBlockEntity p && p.plating() != null ? tag(p.plating()) : null;
    }

    public static net.minecraft.nbt.@Nullable CompoundTag tag(BlockState plating) {
        var encoded = BlockState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, plating).result();
        if (encoded.isEmpty()) return null;
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put(PlatedBlockEntity.KEY, encoded.get());
        return tag;
    }

    /** The plating stored in block entity data, or null. */
    public static @Nullable BlockState fromTag(net.minecraft.nbt.@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(PlatedBlockEntity.KEY)) return null;
        return BlockState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get(PlatedBlockEntity.KEY)).result().orElse(null);
    }

    private static void give(Player player, Level level, BlockPos pos, BlockState plating) {
        ItemStack back = new ItemStack(plating.getBlock());
        if (!player.getInventory().add(back)) Block.popResource(level, pos, back);
    }

    // ---- properties taken from the plating

    public static float destroyProgress(BlockState plating, Player player, BlockGetter level, BlockPos pos) {
        return plating.getDestroyProgress(player, level, pos);
    }

    public static float explosionResistance(BlockGetter level, BlockPos pos, Explosion explosion, float own) {
        BlockState p = get(level, pos);
        return p == null ? own : p.getExplosionResistance(level, pos, explosion);
    }

    public static boolean canHarvest(BlockGetter level, BlockPos pos, Player player, boolean own) {
        BlockState p = get(level, pos);
        return p == null ? own : net.neoforged.neoforge.event.EventHooks.doPlayerHarvestCheck(player, p, level, pos);
    }

    public static SoundType sound(LevelReader level, BlockPos pos, @Nullable Entity entity, SoundType own) {
        BlockState p = get(level, pos);
        return p == null ? own : p.getSoundType(level, pos, entity);
    }

    public static MapColor mapColor(BlockGetter level, BlockPos pos, MapColor own) {
        BlockState p = get(level, pos);
        return p == null ? own : p.getMapColor(level, pos);
    }
}
