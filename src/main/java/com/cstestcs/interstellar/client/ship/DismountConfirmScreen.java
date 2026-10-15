package com.cstestcs.interstellar.client.ship;

import com.cstestcs.interstellar.network.ModPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Warns a passenger that leaving a spacecraft in flight drops them out of it. */
public class DismountConfirmScreen extends Screen {
    /** Set when the player confirmed, so the next dismount (sent back by the server) is let through locally. */
    static boolean confirmed;

    public DismountConfirmScreen() {
        super(Component.translatable("screen.interstellar.dismount.title"));
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.interstellar.dismount.leave"), b -> {
            confirmed = true;
            ClientPacketDistributor.sendToServer(new ModPayloads.ConfirmDismount());
            onClose();
        }).bounds(cx - 105, cy + 10, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.interstellar.dismount.stay"), b -> onClose())
                .bounds(cx + 5, cy + 10, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 30, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.interstellar.dismount.warning"), width / 2, height / 2 - 14, 0xFFFFAA55);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
