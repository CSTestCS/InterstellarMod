package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.network.ModPayloads;
import com.cstestcs.interstellar.registry.ModComponents;
import com.cstestcs.interstellar.registry.ModItems;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Server-side handling of reloads and gunsmith table actions. */
public final class GunActions {
    private GunActions() {}

    /** Rounds per magazine of each kind (packs may add kinds; unknown kinds hold {@link MagazineItem#DEFAULT_CAPACITY}). */
    private static final Map<String, Integer> CAPACITY = Map.ofEntries(Map.entry("pistol", 48), Map.entry("rifle", 90), Map.entry("sniper", 20),
            Map.entry("shell", 24), Map.entry("belt", 200), Map.entry("rocket", 4), Map.entry("energy_cell", 120), Map.entry("crystal_cartridge", 72),
            Map.entry("fuel_rod", 10), Map.entry("void_cell", 24), Map.entry("bio_sac", 80));

    public static int capacity(String kind) {
        return CAPACITY.getOrDefault(kind, MagazineItem.DEFAULT_CAPACITY);
    }

    // ------------------------------------------------------------------ reload

    public static void reload(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        if (!(gun.getItem() instanceof GunItem)) return;
        GunRegistry.Stats stats = GunItem.stats(gun);
        if (stats == null) return;
        GunCodecs.GunState state = GunItem.state(gun);
        long now = player.level().getGameTime();
        int missing = stats.magazine() - state.ammo();
        if (missing <= 0 || state.reloadUntil() > now) return;
        Inventory inv = player.getInventory();
        int loaded = 0;
        for (int i = 0; i < inv.getContainerSize() && loaded < missing; i++) {
            ItemStack s = inv.getItem(i);
            GunCodecs.Magazine mag = MagazineItem.data(s);
            if (mag == null || !mag.kind().equals(stats.magazineKind()) || mag.ammo() <= 0) continue;
            int take = Math.min(missing - loaded, mag.ammo());
            loaded += take;
            if (s.getCount() > 1) {
                // Split one magazine off the stack so only that one is drained.
                ItemStack one = s.split(1);
                one.set(ModComponents.MAGAZINE.get(), new GunCodecs.Magazine(mag.kind(), mag.ammo() - take, mag.capacity()));
                if (!inv.add(one)) player.spawnAtLocation(player.level(), one);
            } else {
                s.set(ModComponents.MAGAZINE.get(), new GunCodecs.Magazine(mag.kind(), mag.ammo() - take, mag.capacity()));
            }
        }
        if (loaded == 0 && !player.getAbilities().instabuild) {
            player.sendOverlayMessage(Component.translatable("message.interstellar.gun.no_magazine", GunItem.pretty(stats.magazineKind())));
            return;
        }
        if (player.getAbilities().instabuild) loaded = missing;
        gun.set(ModComponents.GUN_STATE.get(), new GunCodecs.GunState(state.ammo() + loaded, now + stats.reloadTicks()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ gunsmith table

    public static void gunsmith(ServerPlayer player, ModPayloads.GunsmithAction action) {
        if (!(player.containerMenu instanceof GunsmithMenu menu) || !menu.stillValid(player)) return;
        GunRegistry reg = GunDataManager.registry();
        ItemStack gun = menu.gunSlot();
        switch (action.action()) {
            case ModPayloads.GunsmithAction.NEW_FRAME -> {
                GunData.Archetype arch = reg.archetype(action.a());
                if (arch == null || !gun.isEmpty()) return;
                GunData.Material frameMat = material(player, menu, reg);
                if (frameMat == null) return;
                if (!pay(player, GunDataManager.cost("archetype", arch.id()))) return;
                menu.materialSlot().shrink(1);
                menu.setGunSlot(GunItem.create(new GunBuild(arch.id(), Map.of(), null, Map.of(), frameMat.id()), false));
            }
            case ModPayloads.GunsmithAction.INSTALL_PART -> {
                GunBuild build = gun.get(ModComponents.GUN_BUILD.get());
                GunData.Slot slot = GunData.Slot.byKey(action.a());
                GunData.Part part = reg.part(action.b());
                if (build == null || slot == null || part == null || part.slot() != slot) return;
                if (!reg.partsFor(build.archetype(), slot).contains(part)) return;
                GunData.Archetype arch = reg.archetype(build.archetype());
                if (arch == null || !arch.slots().contains(slot)) return;
                GunData.Material mat = material(player, menu, reg);
                if (mat == null) return;
                if (!pay(player, GunDataManager.cost("part", part.id()))) return;
                menu.materialSlot().shrink(1);
                ItemStack updated = gun.copy();
                updated.set(ModComponents.GUN_BUILD.get(), build.with(slot, new GunBuild.Choice(part.id(), mat.id())));
                menu.setGunSlot(updated);
            }
            case ModPayloads.GunsmithAction.REMOVE_PART -> {
                GunBuild build = gun.get(ModComponents.GUN_BUILD.get());
                GunData.Slot slot = GunData.Slot.byKey(action.a());
                if (build == null || slot == null) return;
                ItemStack updated = gun.copy();
                updated.set(ModComponents.GUN_BUILD.get(), build.with(slot, null));
                menu.setGunSlot(updated);
            }
            case ModPayloads.GunsmithAction.CRAFT_MAGAZINE -> {
                List<GunDataManager.Cost> cost = GunDataManager.cost("magazine", action.a());
                if (cost.isEmpty() || !pay(player, cost)) return;
                ItemStack mag = MagazineItem.create(action.a(), capacity(action.a()), capacity(action.a()));
                if (!player.getInventory().add(mag)) player.spawnAtLocation(player.level(), mag);
            }
            case ModPayloads.GunsmithAction.APPLY_TRIM -> {
                GunBuild build = gun.get(ModComponents.GUN_BUILD.get());
                ItemStack template = menu.templateSlot(), matStack = menu.materialSlot();
                if (build == null || template.isEmpty() || matStack.isEmpty()) return;
                String templateId = BuiltInRegistries.ITEM.getKey(template.getItem()).getPath();
                if (!templateId.endsWith("_armor_trim_smithing_template")) return;
                String pattern = templateId.replace("_armor_trim_smithing_template", "");
                String matId = BuiltInRegistries.ITEM.getKey(matStack.getItem()).toString();
                GunData.Material mat = reg.materialForItem(matId);
                int color = mat != null ? mat.color() : 0xC0C0C0;
                template.shrink(1);
                matStack.shrink(1);
                ItemStack updated = gun.copy();
                updated.set(ModComponents.GUN_BUILD.get(), build.withTrim(new GunData.Trim(pattern, matId, color)));
                menu.setGunSlot(updated);
            }
            default -> {}
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.1f);
        menu.broadcastChanges();
    }

    /**
     * The material in the material slot: frames and parts are always made of one. Tells the player when the slot is
     * empty or holds something that is not a gunsmithing material. @return null if there is none
     */
    static GunData.Material material(ServerPlayer player, GunsmithMenu menu, GunRegistry reg) {
        ItemStack matStack = menu.materialSlot();
        GunData.Material mat = matStack.isEmpty() ? null : reg.materialForItem(BuiltInRegistries.ITEM.getKey(matStack.getItem()).toString());
        if (mat == null) {
            player.sendOverlayMessage(Component.translatable(matStack.isEmpty() ? "message.interstellar.gunsmith.need_material"
                    : "message.interstellar.gunsmith.not_material", matStack.getHoverName()));
        }
        return mat;
    }

    /** Takes the cost from the player's inventory (nothing in creative). @return false if they cannot afford it. */
    static boolean pay(ServerPlayer player, List<GunDataManager.Cost> cost) {
        if (player.getAbilities().instabuild) return true;
        Inventory inv = player.getInventory();
        for (GunDataManager.Cost c : cost) {
            if (inv.countItem(c.item()) < c.count()) {
                player.sendOverlayMessage(Component.translatable("message.interstellar.gunsmith.missing", c.count(), new ItemStack(c.item()).getHoverName()));
                return false;
            }
        }
        for (GunDataManager.Cost c : cost) {
            int left = c.count();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(c.item())) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        return true;
    }

    static boolean isGun(ItemStack s) {
        return s.is(ModItems.GUN.get());
    }
}
