package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.space.PlanetManager;
import com.cstestcs.interstellar.space.SpaceInventories;
import com.cstestcs.interstellar.space.SpaceTravel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = Interstellar.MODID)
public final class ShipEvents {
    private ShipEvents() {}

    /**
     * Guards leaving a spacecraft in entity form: impossible in space, sneaking pilots halt the ship instead, other
     * players must confirm a warning prompt first (shown client-side by the matching client handler).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onMount(EntityMountEvent event) {
        if (!event.isDismounting() || !(event.getEntityBeingMounted() instanceof SpacecraftEntity ship)) return;
        Entity rider = event.getEntityMounting();
        if (SpaceTravel.isTransferring() || ship.isRemoved() || !rider.isAlive() || rider.isRemoved()) return;
        if (rider instanceof ServerPlayer sp && sp.hasDisconnected()) return;
        if (event.getLevel().isClientSide()) return; // the client handler decides what to show
        if (!ship.allowDismount(rider)) {
            event.setCanceled(true);
            if (ship.inSpace() && rider instanceof ServerPlayer p) {
                p.sendOverlayMessage(Component.translatable("message.interstellar.space.no_dismount"));
            }
        }
    }

    /** Players who die in space keep their items in storage instead of losing them in the void. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SpaceTravel.isSpace(player.level())) return;
        ServerLevel level = (ServerLevel) player.level();
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) return;
        SpaceInventories.get(level.getServer()).store(player);
        player.sendSystemMessage(Component.translatable("message.interstellar.space.inventory_saved"));
    }

    /** Interacting with a seated mob while holding a lead takes it out of its seat and onto the lead. */
    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Mob mob) || !event.getItemStack().is(Items.LEAD)) return;
        Entity vehicle = mob.getVehicle();
        if (!(vehicle instanceof SeatEntity) && !(vehicle instanceof SpacecraftEntity)) return;
        if (!event.getLevel().isClientSide() && mob.canBeLeashed()) {
            mob.stopRiding();
            mob.setLeashedTo(event.getEntity(), true);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        PlanetManager.get(event.getServer()).ensurePlanets(event.getServer());
    }
}
