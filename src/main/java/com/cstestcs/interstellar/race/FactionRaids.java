package com.cstestcs.interstellar.race;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.alien.AlienArmor;
import com.cstestcs.interstellar.alien.AlienEntity;
import com.cstestcs.interstellar.core.race.RaceDefinition;
import com.cstestcs.interstellar.registry.ModEntities;
import com.cstestcs.interstellar.space.SpaceTravel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Alien factions sending squads into any dimension that is not vanilla Minecraft's: other mods' worlds and
 * Interstellar's planets alike. A squad is one faction (picked by {@link FactionRoster} among those whose races may
 * live there, e.g. the Covenant anywhere) and lands a little away from a player, now and then.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class FactionRaids {
    private static final int INTERVAL = 20 * 30;

    private FactionRaids() {}

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) continue;
            String dim = level.dimension().identifier().toString();
            if (dim.startsWith("minecraft:") || SpaceTravel.isSpace(level) || level.getDifficulty() == Difficulty.PEACEFUL) continue;
            RandomSource random = level.getRandom();
            if (random.nextInt(6) != 0) continue; // about one squad every three minutes
            if (level.getEntitiesOfClass(AlienEntity.class, new AABB(player.blockPosition()).inflate(64)).size() >= 8) continue;
            List<RaceDefinition> faction = FactionRoster.pickFaction(dim, random);
            if (faction == null) continue;
            BlockPos at = landing(level, player.blockPosition(), random);
            if (at == null) continue;
            int size = 3 + random.nextInt(3);
            for (int i = 0; i < size; i++) {
                BlockPos p = landing(level, at, random, 4);
                if (p == null) continue;
                AlienEntity alien = ModEntities.ALIEN.get().spawn(level, p, EntitySpawnReason.EVENT);
                if (alien == null) continue;
                RaceEvents.setRace(alien, FactionRoster.pickMember(faction, random));
                AlienArmor.equip(alien, dim, random, 0.5f);
            }
        }
    }

    private static @Nullable BlockPos landing(ServerLevel level, BlockPos around, RandomSource random) {
        for (int tries = 0; tries < 8; tries++) {
            double angle = random.nextDouble() * Math.PI * 2, dist = 24 + random.nextDouble() * 16;
            BlockPos p = landing(level, around.offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist)), random, 0);
            if (p != null) return p;
        }
        return null;
    }

    /** Standing room (air over solid ground) near a point, searched up and down from it. */
    private static @Nullable BlockPos landing(ServerLevel level, BlockPos around, RandomSource random, int spread) {
        BlockPos base = spread == 0 ? around : around.offset(random.nextInt(spread * 2 + 1) - spread, 0, random.nextInt(spread * 2 + 1) - spread);
        if (!level.isLoaded(base)) return null;
        for (int dy = 0; dy <= 12; dy++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                BlockPos p = base.above(dy * sign);
                if (level.isOutsideBuildHeight(p)) continue;
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) return p;
            }
        }
        return null;
    }
}
