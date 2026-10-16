package com.cstestcs.interstellar.alien;

import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Armour aliens may wear: any wearable armour piece (vanilla or modded) whose repair material can be found in the
 * alien's home dimension - the ores every foreign world generates, the world's theme blocks and its alien resource.
 * Computed once per dimension from the item registry.
 */
public final class AlienArmor {
    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    /** Materials every foreign world provides (its generated ores, smelted). */
    private static final String[] COMMON = { "minecraft:iron_ingot", "minecraft:raw_iron", "minecraft:gold_ingot", "minecraft:raw_gold",
            "minecraft:copper_ingot", "minecraft:raw_copper", "minecraft:diamond", "minecraft:emerald", "minecraft:lapis_lazuli",
            "minecraft:redstone", "minecraft:coal" };
    private static final Map<String, Map<EquipmentSlot, List<Item>>> CACHE = new ConcurrentHashMap<>();

    private AlienArmor() {}

    static Set<Item> materials(String dimension) {
        Set<Item> set = new HashSet<>();
        for (String id : COMMON) add(set, id);
        ForeignWorlds.World w = ForeignWorlds.byDimension(dimension);
        if (w != null) {
            add(set, w.theme().topBlock);
            add(set, w.theme().underBlock);
            add(set, w.theme().accentBlock);
            add(set, w.defaultBlock());
            Item material = AlienEntity.themeMaterial(w.theme().weaponTheme);
            if (material != null) set.add(material);
            set.add(ModItems.ALIEN_MATERIALS.get(0).get());
        }
        return set;
    }

    private static void add(Set<Item> set, String id) {
        BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).ifPresent(set::add);
    }

    public static Map<EquipmentSlot, List<Item>> armorFor(String dimension) {
        return CACHE.computeIfAbsent(dimension, d -> {
            Set<Item> mats = materials(d);
            Map<EquipmentSlot, List<Item>> out = new EnumMap<>(EquipmentSlot.class);
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                Equippable eq = stack.get(DataComponents.EQUIPPABLE);
                Repairable rep = stack.get(DataComponents.REPAIRABLE);
                if (eq == null || rep == null || !isArmorSlot(eq.slot())) continue;
                if (rep.items().stream().anyMatch(h -> mats.contains(h.value()))) out.computeIfAbsent(eq.slot(), s -> new ArrayList<>()).add(item);
            }
            return out;
        });
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        for (EquipmentSlot s : SLOTS) if (s == slot) return true;
        return false;
    }

    /** Gives a mob a random set of armour pieces made from its home dimension's materials. */
    public static void equip(Mob mob, String homeDimension, RandomSource random, float chancePerSlot) {
        Map<EquipmentSlot, List<Item>> armor = armorFor(homeDimension);
        // One material family per alien looks deliberate rather than random.
        int pick = random.nextInt(1000);
        for (EquipmentSlot slot : SLOTS) {
            List<Item> options = armor.get(slot);
            if (options == null || options.isEmpty() || !mob.getItemBySlot(slot).isEmpty() || random.nextFloat() >= chancePerSlot) continue;
            mob.setItemSlot(slot, new ItemStack(options.get(pick % options.size())));
        }
    }

    public static void clear() {
        CACHE.clear();
    }
}
