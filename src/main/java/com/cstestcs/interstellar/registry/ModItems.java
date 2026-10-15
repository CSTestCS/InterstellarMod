package com.cstestcs.interstellar.registry;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.gun.GunItem;
import com.cstestcs.interstellar.gun.MagazineItem;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Deliberately few items: every gun is one {@link GunItem} configured by data components, every magazine one
 * {@link MagazineItem}, and alien crafting materials are a small fixed set.
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Interstellar.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Interstellar.MODID);

    static {
        ModBlocks.BLOCKS.getEntries().forEach(b -> {
            if (b != ModBlocks.ATMOSPHERE) ITEMS.registerSimpleBlockItem(b);
        });
    }

    public static final DeferredItem<GunItem> GUN = ITEMS.registerItem("gun", GunItem::new, p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<MagazineItem> MAGAZINE = ITEMS.registerItem("magazine", MagazineItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<SpawnEggItem> ALIEN_SPAWN_EGG = ITEMS.registerItem("alien_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(ModEntities.ALIEN.get())));

    /** Alien crafting materials, one per alien weapon theme. Dropped by aliens; required for alien gun parts. */
    public static final List<String> ALIEN_MATERIAL_NAMES = List.of("alien_alloy", "plasma_core", "nether_cinder", "void_shard",
            "crystal_lattice", "bio_gel", "cryo_crystal", "solar_cell", "hydro_pearl", "tech_chip", "sonic_resonator");
    public static final List<DeferredItem<Item>> ALIEN_MATERIALS = ALIEN_MATERIAL_NAMES.stream()
            .map(n -> ITEMS.registerSimpleItem(n, p -> p.rarity(Rarity.UNCOMMON))).toList();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.interstellar"))
            .icon(() -> new ItemStack(ModBlocks.CONTROL_PANEL.get()))
            .displayItems((params, out) -> {
                for (var item : ITEMS.getEntries()) {
                    if (item.get() == GUN.get()) continue;
                    out.accept(item.get());
                }
                GunItem.addCreativeExamples(out);
            }).build());
}
