package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.core.ship.ShipScanner;
import com.cstestcs.interstellar.registry.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

/**
 * The blocks of a spacecraft in its entity form, relative to the control panel (which is always at 0,0,0), in the
 * orientation the ship had when it was assembled ({@link #baseYaw}).
 */
public final class ShipStructure {
    public record Entry(BlockPos pos, BlockState state, Optional<CompoundTag> blockEntity) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                BlockState.CODEC.fieldOf("state").forGetter(Entry::state),
                CompoundTag.CODEC.optionalFieldOf("be").forGetter(Entry::blockEntity)
        ).apply(i, Entry::new));
    }

    public static final Codec<ShipStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("blocks").forGetter(s -> s.entries),
            Codec.FLOAT.fieldOf("base_yaw").forGetter(s -> s.baseYaw)
    ).apply(i, ShipStructure::new));

    public static final ShipStructure EMPTY = new ShipStructure(List.of(), 0);

    public final List<Entry> entries;
    public final float baseYaw;
    /** Seat positions (relative, block positions) in seat order; index 0 is the pilot spot behind the control panel. */
    public final List<BlockPos> seats = new ArrayList<>();
    public final List<Entry> weapons = new ArrayList<>();
    public final BlockPos min, max;

    public ShipStructure(List<Entry> entries, float baseYaw) {
        this.entries = List.copyOf(entries);
        this.baseYaw = baseYaw;
        int x0 = 0, y0 = 0, z0 = 0, x1 = 0, y1 = 0, z1 = 0;
        BlockPos pilot = BlockPos.ZERO;
        for (Entry e : entries) {
            BlockPos p = e.pos();
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
            ShipScanner.Kind k = ModBlocks.kindOf(e.state());
            if (p.equals(BlockPos.ZERO) && e.state().hasProperty(ShipPartBlock.FACING)) {
                pilot = p.relative(e.state().getValue(ShipPartBlock.FACING));
            }
            if (k == ShipScanner.Kind.SEAT) seats.add(p);
            if (k == ShipScanner.Kind.WEAPON) weapons.add(e);
        }
        seats.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        seats.add(0, pilot);
        this.min = new BlockPos(x0, y0, z0);
        this.max = new BlockPos(x1, y1, z1);
    }

    public int seatCount() {
        return seats.size();
    }

    /** Local bounds in blocks (unrotated, unscaled). */
    public AABB localBounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    // ------------------------------------------------------------------ capture / place

    /** Removes the scanned blocks from the world and returns them as a structure. Block entity contents are kept. */
    public static ShipStructure capture(ServerLevel level, BlockPos origin, List<long[]> blocks, float baseYaw) {
        List<Entry> out = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        for (long[] b : blocks) {
            BlockPos world = new BlockPos((int) b[0], (int) b[1], (int) b[2]);
            BlockState state = level.getBlockState(world);
            BlockEntity be = level.getBlockEntity(world);
            CompoundTag tag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
            if (state.getBlock() instanceof LandingGearBlock) state = state.setValue(LandingGearBlock.EXTENDED, false);
            out.add(new Entry(world.subtract(origin), state, Optional.ofNullable(tag)));
            positions.add(world);
        }
        // Remove fragile blocks (torches, buttons, carpets...) before the blocks they hang on, without drops or updates.
        positions.sort(Comparator.comparing((BlockPos p) -> level.getBlockState(p).isCollisionShapeFullBlock(level, p)));
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (BlockPos p : positions) {
            level.removeBlockEntity(p);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
        }
        for (BlockPos p : positions) level.updateNeighborsAt(p, Blocks.AIR);
        return new ShipStructure(out, baseYaw);
    }

    /** @return the first position that cannot be built into, or null if the structure fits. */
    public BlockPos obstruction(ServerLevel level, BlockPos origin, Rotation rotation) {
        for (Entry e : entries) {
            BlockPos p = origin.offset(e.pos().rotate(rotation));
            if (!level.isInWorldBounds(p)) return p;
            BlockState there = level.getBlockState(p);
            if (!there.isAir() && !there.canBeReplaced() && !there.is(ModBlocks.ATMOSPHERE.get())) return p;
        }
        return null;
    }

    /** Places the structure back into the world, rotated, restoring block entity data. */
    public void place(ServerLevel level, BlockPos origin, Rotation rotation, boolean extendGear) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        List<Entry> ordered = new ArrayList<>(entries);
        // Solid blocks first, then the things that attach to them.
        ordered.sort(Comparator.comparing((Entry e) -> !e.state().isCollisionShapeFullBlock(level, BlockPos.ZERO)));
        List<BlockPos> placed = new ArrayList<>();
        for (Entry e : ordered) {
            BlockPos p = origin.offset(e.pos().rotate(rotation));
            BlockState state = e.state().rotate(level, p, rotation);
            if (state.getBlock() instanceof LandingGearBlock) state = state.setValue(LandingGearBlock.EXTENDED, extendGear);
            level.setBlock(p, state, flags);
            e.blockEntity().ifPresent(tag -> {
                BlockEntity be = level.getBlockEntity(p);
                if (be != null) {
                    CompoundTag copy = tag.copy();
                    copy.putInt("x", p.getX());
                    copy.putInt("y", p.getY());
                    copy.putInt("z", p.getZ());
                    try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(be.problemPath(), com.cstestcs.interstellar.Interstellar.LOGGER)) {
                        be.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), copy));
                    }
                    be.setChanged();
                }
            });
            placed.add(p);
        }
        for (BlockPos p : placed) level.updateNeighborsAt(p, level.getBlockState(p).getBlock());
    }

    // ------------------------------------------------------------------ networking (client only needs states)

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeFloat(baseYaw);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeBlockPos(e.pos());
            buf.writeVarInt(Block.getId(e.state()));
        }
    }

    public static ShipStructure read(RegistryFriendlyByteBuf buf) {
        float yaw = buf.readFloat();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new Entry(buf.readBlockPos(), Block.stateById(buf.readVarInt()), Optional.empty()));
        return new ShipStructure(list, yaw);
    }

    public static Rotation rotationForQuarterTurns(int q) {
        return switch (Math.floorMod(q, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Direction forward(BlockState panel) {
        return panel.hasProperty(ShipPartBlock.FACING) ? panel.getValue(ShipPartBlock.FACING).getOpposite() : Direction.NORTH;
    }
}
