package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.alien.AlienArmor;
import com.cstestcs.interstellar.alien.AlienEntity;
import com.cstestcs.interstellar.core.race.RaceDefinition;
import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.race.RaceEvents;
import com.cstestcs.interstellar.race.RaceManager;
import com.cstestcs.interstellar.registry.ModEntities;
import com.cstestcs.interstellar.space.SpaceTravel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Alien starships in space: now and then, near players in space, an alien crew from one of the foreign worlds flies by
 * in one of the starship designs (built-in or saved by players), its hull plating swapped for their world's metal
 * or stone. Crews wear armour from their home world and fire the ship's cannons at nearby players.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class AlienShips {
    private AlienShips() {}

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 200 != 0) return;
        ServerLevel space = event.getServer().getLevel(SpaceTravel.SPACE);
        if (space == null || space.players().isEmpty()) return;
        int max = InterstellarConfig.SERVER.alienShipsPerPlayer.get();
        if (max <= 0) return;
        RandomSource random = space.getRandom();
        for (ServerPlayer player : space.players()) {
            if (random.nextFloat() > 0.25f) continue;
            int near = space.getEntitiesOfClass(SpacecraftEntity.class, new AABB(player.blockPosition()).inflate(160), SpacecraftEntity::isAlienCrewed).size();
            if (near >= max) continue;
            double angle = random.nextDouble() * Math.PI * 2, dist = 50 + random.nextDouble() * 40;
            Vec3 pos = player.position().add(Math.cos(angle) * dist, (random.nextDouble() - 0.5) * 20, Math.sin(angle) * dist);
            ForeignWorlds.World world = ForeignWorlds.all().get(random.nextInt(ForeignWorlds.all().size()));
            List<String> designs = StarshipLibrary.names();
            spawn(space, pos, world, designs.get(random.nextInt(designs.size())), random);
        }
    }

    /** Spawns an alien-crewed ship of a design. @return the ship, or null if the design or race is missing */
    public static @Nullable SpacecraftEntity spawn(ServerLevel level, Vec3 pos, ForeignWorlds.World world, String designName, RandomSource random) {
        ShipStructure design = StarshipLibrary.load(level.getServer(), designName).orElse(null);
        RaceDefinition race = RaceManager.get(world.alienRaceId());
        if (design == null || design.entries.isEmpty()) return null;
        BlockState hull = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(ForeignWorlds.hullBlock(world))).orElse(Blocks.STONE).defaultBlockState();
        ShipStructure structure = StarshipLibrary.withHull(design, hull);
        // The crew loads every cannon with one of their own weapons.
        if (race != null) structure = StarshipLibrary.withCannonLoad(structure, RaceEvents.cannonWeapon(race, random), level.registryAccess());
        SpacecraftEntity ship = SpacecraftEntity.create(level, structure, pos, SpacecraftEntity.MODE_FLYING);
        ship.setYRot(random.nextFloat() * 360f);
        ship.setAlienCrewed(true);
        level.addFreshEntity(ship);
        int crew = Math.min(structure.seatCount(), 1 + random.nextInt(3));
        for (int seat = 0; seat < crew; seat++) {
            AlienEntity alien = ModEntities.ALIEN.get().spawn(level, BlockPos.containing(pos), EntitySpawnReason.EVENT);
            if (alien == null) continue;
            if (race != null) RaceEvents.setRace(alien, race);
            AlienArmor.equip(alien, world.dimensionId(), random, 0.5f);
            alien.setPersistenceRequired();
            if (alien.startRiding(ship, true, true)) ship.restoreSeat(alien, seat);
        }
        return ship;
    }
}
