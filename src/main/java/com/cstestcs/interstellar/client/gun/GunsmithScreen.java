package com.cstestcs.interstellar.client.gun;

import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.gun.GunDataManager;
import com.cstestcs.interstellar.gun.GunItem;
import com.cstestcs.interstellar.gun.GunsmithMenu;
import com.cstestcs.interstellar.network.ModPayloads;
import com.cstestcs.interstellar.registry.ModComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Gunsmith table screen. With an empty gun slot it lists gun frames (archetypes) to build; with a gun it shows tabs
 * for the archetype's slots and the parts that fit the selected slot. New frames and parts are made from the
 * material in the material slot, which is required (its look, stats and effects carry over). Magazines are crafted from the bottom row, and a trim
 * template plus trim material applies a trim.
 */
public class GunsmithScreen extends AbstractContainerScreen<GunsmithMenu> {
    private static final int LIST_ROWS = 4;
    private GunData.Slot selectedSlot = GunData.Slot.RECEIVER;
    private int page;
    private ItemStack lastGun = ItemStack.EMPTY;
    private final List<Button> dynamic = new ArrayList<>();

    public GunsmithScreen(GunsmithMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 256, 206);
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack gun = menu.gunSlot();
        if (!ItemStack.isSameItemSameComponents(gun, lastGun)) {
            lastGun = gun.copy();
            rebuild();
        }
    }

    private void send(int action, String a, String b) {
        ClientPacketDistributor.sendToServer(new ModPayloads.GunsmithAction(action, a, b));
    }

    private void rebuild() {
        for (Button b : dynamic) removeWidget(b);
        dynamic.clear();
        GunRegistry reg = GunDataManager.registry();
        int x = leftPos + 8, y = topPos + 18;
        ItemStack gun = menu.gunSlot();
        GunBuild build = gun.get(ModComponents.GUN_BUILD.get());
        if (build == null) {
            // ---- choose a frame
            List<GunData.Archetype> archs = new ArrayList<>(reg.archetypes());
            int perPage = LIST_ROWS * 3;
            page = Math.floorMod(page, Math.max(1, (archs.size() + perPage - 1) / perPage));
            for (int i = 0; i < perPage && page * perPage + i < archs.size(); i++) {
                GunData.Archetype a = archs.get(page * perPage + i);
                Button b = Button.builder(Component.literal(GunItem.pretty(a.id())).withStyle(a.alien() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE),
                                btn -> send(ModPayloads.GunsmithAction.NEW_FRAME, a.id(), ""))
                        .bounds(x + (i % 3) * 80, y + (i / 3) * 18, 78, 16)
                        .tooltip(Tooltip.create(cost("archetype", a.id(), Component.translatable("screen.interstellar.gunsmith.frame")))).build();
                dynamic.add(addRenderableWidget(b));
            }
            pager(archs.size(), perPage);
        } else {
            GunData.Archetype arch = reg.archetype(build.archetype());
            if (arch == null) return;
            // ---- slot tabs
            List<GunData.Slot> slots = new ArrayList<>(arch.slots());
            if (!slots.contains(selectedSlot)) selectedSlot = slots.get(0);
            for (int i = 0; i < slots.size(); i++) {
                GunData.Slot s = slots.get(i);
                boolean installed = build.get(s) != null, required = arch.required().contains(s);
                Component label = Component.literal(GunItem.pretty(s.key()).substring(0, Math.min(5, s.key().length())))
                        .withStyle(s == selectedSlot ? ChatFormatting.YELLOW : installed ? ChatFormatting.GREEN : required ? ChatFormatting.RED : ChatFormatting.GRAY);
                Button b = Button.builder(label, btn -> {
                    selectedSlot = s;
                    page = 0;
                    rebuild();
                }).bounds(x + i * 30, y - 2, 29, 14).tooltip(Tooltip.create(Component.literal(GunItem.pretty(s.key())))).build();
                dynamic.add(addRenderableWidget(b));
            }
            // ---- parts for the slot
            List<GunData.Part> parts = reg.partsFor(arch.id(), selectedSlot);
            int perPage = (LIST_ROWS - 1) * 3;
            page = Math.floorMod(page, Math.max(1, (parts.size() + perPage - 1) / perPage));
            for (int i = 0; i < perPage && page * perPage + i < parts.size(); i++) {
                GunData.Part p = parts.get(page * perPage + i);
                boolean current = build.get(selectedSlot) != null && build.get(selectedSlot).part().equals(p.id());
                Button b = Button.builder(Component.literal(GunItem.pretty(p.id())).withStyle(current ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                                btn -> send(ModPayloads.GunsmithAction.INSTALL_PART, selectedSlot.key(), p.id()))
                        .bounds(x + (i % 3) * 80, y + 16 + (i / 3) * 18, 78, 16)
                        .tooltip(Tooltip.create(partTooltip(p))).build();
                dynamic.add(addRenderableWidget(b));
            }
            pager(parts.size(), perPage);
            dynamic.add(addRenderableWidget(Button.builder(Component.translatable("screen.interstellar.gunsmith.remove"),
                    btn -> send(ModPayloads.GunsmithAction.REMOVE_PART, selectedSlot.key(), "")).bounds(leftPos + 130, topPos + 94, 56, 16).build()));
            dynamic.add(addRenderableWidget(Button.builder(Component.translatable("screen.interstellar.gunsmith.trim"),
                    btn -> send(ModPayloads.GunsmithAction.APPLY_TRIM, "", "")).bounds(leftPos + 190, topPos + 94, 58, 16)
                    .tooltip(Tooltip.create(Component.translatable("screen.interstellar.gunsmith.trim.tooltip"))).build()));
            // ---- magazines for this gun
            GunRegistry.Stats stats = GunItem.stats(gun);
            if (stats != null) {
                String kind = stats.magazineKind();
                dynamic.add(addRenderableWidget(Button.builder(Component.translatable("screen.interstellar.gunsmith.magazine", GunItem.pretty(kind)),
                                btn -> send(ModPayloads.GunsmithAction.CRAFT_MAGAZINE, kind, ""))
                        .bounds(leftPos + 130, topPos + 76, 118, 16).tooltip(Tooltip.create(cost("magazine", kind, Component.literal(GunItem.pretty(kind)))))
                        .build()));
            }
        }
    }

    private void pager(int count, int perPage) {
        if (count <= perPage) return;
        dynamic.add(addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            rebuild();
        }).bounds(leftPos + 8, topPos + 94, 14, 16).build()));
        dynamic.add(addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            rebuild();
        }).bounds(leftPos + 112, topPos + 94, 14, 16).build()));
    }

    private static Component cost(String kind, String id, Component title) {
        MutableComponent c = title.copy().withStyle(ChatFormatting.GOLD);
        for (GunDataManager.Cost cost : GunDataManager.cost(kind, id)) {
            c.append(Component.literal("\n " + cost.count() + "x ").withStyle(ChatFormatting.GRAY)).append(new ItemStack(cost.item()).getHoverName());
        }
        return c;
    }

    private static Component partTooltip(GunData.Part p) {
        MutableComponent c = Component.literal(GunItem.pretty(p.id())).withStyle(p.alien() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA);
        c.append(Component.literal("  Tier " + p.tier()).withStyle(ChatFormatting.DARK_GRAY));
        p.modifiers().forEach((stat, m) -> {
            String text = (m.add() != 0 ? String.format(java.util.Locale.ROOT, " %+.1f", m.add()) : "")
                    + (m.mul() != 1 ? String.format(java.util.Locale.ROOT, " x%.2f", m.mul()) : "");
            c.append(Component.literal("\n " + GunItem.pretty(stat.key()) + ":" + text).withStyle(ChatFormatting.GRAY));
        });
        if (p.fireModeOverride() != null) c.append(Component.literal("\n Fire mode: " + GunItem.pretty(p.fireModeOverride().name())).withStyle(ChatFormatting.YELLOW));
        if (!p.effects().isEmpty()) c.append(Component.literal("\n ✦ " + String.join(", ", p.effects())).withStyle(ChatFormatting.GOLD));
        c.append(Component.literal("\n").append(cost("part", p.id(), Component.translatable("screen.interstellar.gunsmith.cost"))));
        return c;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2F36);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF3C424C);
        g.fill(x + 4, y + 14, x + imageWidth - 4, y + 92, 0xFF262A30);
        for (int[] s : new int[][] { { 26, 98 }, { 62, 98 }, { 98, 98 } }) g.fill(x + s[0] - 1, y + s[1] - 1, x + s[0] + 17, y + s[1] + 17, 0xFF1A1D22);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 8 + col * 18, sy = y + 124 + row * 18;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF1A1D22);
            }
        }
        for (int col = 0; col < 9; col++) g.fill(x + 7 + col * 18, y + 181, x + 25 + col * 18, y + 199, 0xFF1A1D22);
        g.text(font, Component.translatable("screen.interstellar.gunsmith.gun"), x + 22, y + 116 - 2, 0xFFB0B8C4);
        g.text(font, Component.translatable("screen.interstellar.gunsmith.material"), x + 52, y + 116 - 2, 0xFFB0B8C4);
    }
}
