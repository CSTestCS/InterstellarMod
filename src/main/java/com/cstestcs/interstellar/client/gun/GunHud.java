package com.cstestcs.interstellar.client.gun;

import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.gun.GunCodecs;
import com.cstestcs.interstellar.gun.GunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Rounds left (and reload progress) in the bottom-right corner while holding a gun. */
public final class GunHud {
    private GunHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem)) return;
        GunRegistry.Stats stats = GunItem.stats(stack);
        if (stats == null) return;
        GunCodecs.GunState state = GunItem.state(stack);
        long now = mc.player.level().getGameTime();
        String text = state.reloadUntil() > now ? "RELOADING" : state.ammo() + " / " + stats.magazine();
        int color = state.reloadUntil() > now ? 0xFFFFAA33 : state.ammo() == 0 ? 0xFFFF5555 : 0xFFFFFFFF;
        int w = mc.font.width(text);
        int x = g.guiWidth() - w - 10, y = g.guiHeight() - 22;
        g.fill(x - 4, y - 3, x + w + 4, y + 11, 0x80000000);
        g.text(mc.font, text, x, y, color);
        g.text(mc.font, GunItem.pretty(stats.fireMode().name()), x, y - 11, 0xFFAAAAAA);
    }
}
