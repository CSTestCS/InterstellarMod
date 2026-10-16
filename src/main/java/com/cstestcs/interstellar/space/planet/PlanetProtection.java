package com.cstestcs.interstellar.space.planet;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.space.PlanetGenome;
import com.cstestcs.interstellar.core.space.PlanetLayout;
import com.cstestcs.interstellar.core.space.PlanetShape;
import com.cstestcs.interstellar.space.PlanetManager;
import com.cstestcs.interstellar.space.SpaceTravel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Planets in space cannot be blown apart: explosions there (ship cannons, TNT...) leave every block inside a planet's
 * atmosphere alone. The atmosphere shell itself is blast-proof as well.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class PlanetProtection {
    private PlanetProtection() {}

    @SubscribeEvent
    static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !SpaceTravel.isSpace(level)) return;
        List<BlockPos> blocks = event.getAffectedBlocks();
        if (blocks.isEmpty()) return;
        BlockPos first = blocks.get(0);
        List<PlanetShape> shapes = new ArrayList<>();
        for (PlanetLayout.Planet p : PlanetManager.get(level.getServer()).all()) {
            if (Math.abs(p.x() - first.getX()) < 64 && Math.abs(p.z() - first.getZ()) < 64) shapes.add(p.shape());
        }
        for (PlanetGenome g : PlanetGenome.near(first.getX(), first.getZ(), 64)) shapes.add(g.shape());
        if (shapes.isEmpty()) return;
        blocks.removeIf(pos -> {
            for (PlanetShape s : shapes) if (s.protects(pos.getX(), pos.getY(), pos.getZ())) return true;
            return false;
        });
    }
}
