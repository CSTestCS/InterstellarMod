package com.cstestcs.interstellar;

import com.cstestcs.interstellar.body.BodyServer;
import com.cstestcs.interstellar.gun.GunDataManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Interstellar.MODID)
public final class CommonEvents {
    private CommonEvents() {}

    /** Gun data follows datapacks: sent on join and after every /reload. */
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(GunDataManager::sendTo);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BodyServer.forget(event.getEntity().getUUID());
    }
}
