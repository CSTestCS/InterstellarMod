package com.cstestcs.interstellar.registry;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.alien.AlienEntity;
import com.cstestcs.interstellar.gun.GunProjectile;
import com.cstestcs.interstellar.ship.SeatEntity;
import com.cstestcs.interstellar.ship.SpacecraftEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Interstellar.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpacecraftEntity>> SPACECRAFT = ENTITIES.registerEntityType("spacecraft",
            SpacecraftEntity::new, MobCategory.MISC, b -> b.sized(1f, 1f).clientTrackingRange(16).updateInterval(1).fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.registerEntityType("seat",
            SeatEntity::new, MobCategory.MISC, b -> b.sized(0.5f, 0.25f).clientTrackingRange(8).noSummon());
    public static final DeferredHolder<EntityType<?>, EntityType<AlienEntity>> ALIEN = ENTITIES.registerEntityType("alien",
            AlienEntity::new, MobCategory.MONSTER, b -> b.sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<GunProjectile>> GUN_PROJECTILE = ENTITIES.registerEntityType("gun_projectile",
            GunProjectile::new, MobCategory.MISC, b -> b.sized(0.25f, 0.25f).clientTrackingRange(6).updateInterval(1));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ALIEN.get(), AlienEntity.createAttributes().build());
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        // Aliens spawn on any solid ground at any light level, but only in the foreign worlds (via their biomes).
        event.register(ALIEN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AlienEntity::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
