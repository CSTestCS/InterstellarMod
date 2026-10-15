package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.core.ship.ShipScanner;
import com.cstestcs.interstellar.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Validates a built ship and converts it from blocks into a {@link SpacecraftEntity}. */
public final class ShipAssembler {
    private ShipAssembler() {}

    public static boolean assemble(ServerLevel level, BlockPos panel, Player player, boolean landing) {
        int limit = InterstellarConfig.SERVER.shipBlockLimit.get();
        ShipScanner.Result result = ShipScanner.scan(panel.getX(), panel.getY(), panel.getZ(), limit,
                (x, y, z) -> ModBlocks.kindOf(level.getBlockState(new BlockPos(x, y, z))));
        if (!result.ok()) {
            MutableComponent msg = Component.translatable("message.interstellar.ship.invalid").withStyle(ChatFormatting.RED);
            for (ShipScanner.Problem p : result.problems()) {
                msg.append("\n - ").append(Component.translatable("message.interstellar.ship.problem." + p.name().toLowerCase(Locale.ROOT), limit));
            }
            player.displayClientMessage(msg, false);
            return false;
        }
        BlockState panelState = level.getBlockState(panel);
        float yaw = ShipStructure.forward(panelState).toYRot();

        // Remember who sits on the ship's seats before the blocks disappear.
        List<Entity> seated = new ArrayList<>();
        List<BlockPos> seatPositions = new ArrayList<>();
        for (long[] b : result.blocks()) {
            BlockPos p = new BlockPos((int) b[0], (int) b[1], (int) b[2]);
            if (ModBlocks.kindOf(level.getBlockState(p)) != ShipScanner.Kind.SEAT) continue;
            for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(p))) {
                for (Entity rider : seat.getPassengers()) {
                    seated.add(rider);
                    seatPositions.add(p.subtract(panel));
                }
                seat.ejectPassengers();
                seat.discard();
            }
        }

        ShipStructure structure = ShipStructure.capture(level, panel, result.blocks(), yaw);
        SpacecraftEntity ship = SpacecraftEntity.create(level, structure, Vec3.atBottomCenterOf(panel),
                landing ? SpacecraftEntity.MODE_LANDING : SpacecraftEntity.MODE_FLYING);
        level.addFreshEntity(ship);
        if (player.isPassenger()) player.stopRiding();
        player.startRiding(ship, true);
        for (int i = 0; i < seated.size(); i++) {
            Entity rider = seated.get(i);
            int seat = structure.seats.indexOf(seatPositions.get(i));
            if (rider == player) continue;
            if (rider.startRiding(ship, true) && seat > 0) ship.restoreSeat(rider, seat);
        }
        player.displayClientMessage(Component.translatable(landing ? "message.interstellar.ship.landing" : "message.interstellar.ship.launch",
                result.blocks().size()), true);
        return true;
    }
}
