package com.cstestcs.interstellar.ship.plating;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hull shapes placed on their sides. {@link #TILT} is where the shape's own "up" points: {@code up} is the normal,
 * upright shape; a horizontal value lays it over onto that side, turning it 90 degrees about the horizontal axis
 * across that direction. Every other property (stair facing, slab half, fence arms...) then describes the shape in
 * its own, tilted frame, and the block's model and outline are the upright ones turned the same way.
 * <p>
 * Sneak while placing a shape to lay it on its side: against the side of a block it lies with its bottom against
 * that block; on a floor or ceiling it tips over towards you.
 */
public final class Tilt {
    public static final EnumProperty<Direction> TILT = EnumProperty.create("tilt", Direction.class,
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);

    private static final Map<VoxelShape, Map<Direction, VoxelShape>> SHAPES = new ConcurrentHashMap<>();

    private Tilt() {}

    public static Direction of(BlockState state) {
        return state.hasProperty(TILT) ? state.getValue(TILT) : Direction.UP;
    }

    public static boolean tilted(BlockState state) {
        return of(state) != Direction.UP;
    }

    /** A direction of the shape's own frame, in the world. */
    public static Direction toWorld(Direction tilt, Direction local) {
        if (tilt == Direction.UP) return local;
        int[] v = rotate(tilt, local.getStepX(), local.getStepY(), local.getStepZ());
        return direction(v[0], v[1], v[2]);
    }

    /** A world direction, in the shape's own frame. */
    public static Direction toLocal(Direction tilt, Direction world) {
        if (tilt == Direction.UP) return world;
        for (Direction d : Direction.values()) if (toWorld(tilt, d) == world) return d;
        return world;
    }

    /**
     * Rotation by 90 degrees about a = up x tilt (so up turns into tilt): v' = (a.v) a + a x v.
     */
    static int[] rotate(Direction tilt, int x, int y, int z) {
        // a = (0,1,0) x (tx,0,tz) = (tz, 0, -tx)
        int ax = tilt.getStepZ(), ay = 0, az = -tilt.getStepX();
        int dot = ax * x + ay * y + az * z;
        int cx = ay * z - az * y, cy = az * x - ax * z, cz = ax * y - ay * x;
        return new int[] { dot * ax + cx, dot * ay + cy, dot * az + cz };
    }

    /** The same rotation as a 3x3 matrix (columns: where x, y and z go), for turning models. */
    public static int[][] matrix(Direction tilt) {
        int[] cx = rotate(tilt, 1, 0, 0), cy = rotate(tilt, 0, 1, 0), cz = rotate(tilt, 0, 0, 1);
        return new int[][] { { cx[0], cy[0], cz[0] }, { cx[1], cy[1], cz[1] }, { cx[2], cy[2], cz[2] } };
    }

    private static Direction direction(int x, int y, int z) {
        for (Direction d : Direction.values()) if (d.getStepX() == x && d.getStepY() == y && d.getStepZ() == z) return d;
        throw new IllegalStateException("not a direction: " + x + "," + y + "," + z);
    }

    /** An upright shape turned onto its side (cached). */
    public static VoxelShape shape(VoxelShape upright, Direction tilt) {
        if (tilt == Direction.UP || upright.isEmpty()) return upright;
        return SHAPES.computeIfAbsent(upright, s -> new ConcurrentHashMap<>()).computeIfAbsent(tilt, t -> {
            int[][] m = matrix(t);
            List<VoxelShape> parts = new ArrayList<>();
            for (AABB box : upright.toAabbs()) {
                Vec3 a = turn(m, box.minX, box.minY, box.minZ), b = turn(m, box.maxX, box.maxY, box.maxZ);
                parts.add(Shapes.box(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z)));
            }
            VoxelShape out = Shapes.empty();
            for (VoxelShape p : parts) out = Shapes.or(out, p);
            return out.optimize();
        });
    }

    private static Vec3 turn(int[][] m, double x, double y, double z) {
        x -= 0.5;
        y -= 0.5;
        z -= 0.5;
        return new Vec3(m[0][0] * x + m[0][1] * y + m[0][2] * z + 0.5, m[1][0] * x + m[1][1] * y + m[1][2] * z + 0.5,
                m[2][0] * x + m[2][1] * y + m[2][2] * z + 0.5);
    }

    /** The tilt for a shape being placed: sideways only while sneaking. */
    public static Direction forPlacement(BlockPlaceContext ctx) {
        if (!ctx.isSecondaryUseActive()) return Direction.UP;
        Direction face = ctx.getClickedFace();
        return face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
    }

    /**
     * The horizontal direction (in the tilted frame) the player looks along, for shapes that face away from the
     * player like stairs, gates and doors.
     */
    public static Direction lookingLocal(BlockPlaceContext ctx, Direction tilt) {
        Vec3 look = ctx.getPlayer() == null ? Vec3.atLowerCornerOf(ctx.getHorizontalDirection().getUnitVec3i()) : ctx.getPlayer().getLookAngle();
        Direction best = Direction.NORTH;
        double bestDot = -2;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction w = toWorld(tilt, d);
            double dot = w.getStepX() * look.x + w.getStepY() * look.y + w.getStepZ() * look.z;
            if (dot > bestDot) {
                bestDot = dot;
                best = d;
            }
        }
        return best;
    }

    public static BlockState rotate(BlockState rotated, Rotation rotation) {
        if (!rotated.hasProperty(TILT) || !tilted(rotated)) return rotated;
        return rotated.setValue(TILT, rotation.rotate(rotated.getValue(TILT)));
    }

    public static BlockState mirror(BlockState mirrored, Mirror mirror) {
        if (!mirrored.hasProperty(TILT) || !tilted(mirrored)) return mirrored;
        return mirrored.setValue(TILT, mirror.mirror(mirrored.getValue(TILT)));
    }
}
