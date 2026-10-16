package com.cstestcs.interstellar.client.gun;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.gun.GunCodecs;
import com.cstestcs.interstellar.gun.GunDataManager;
import com.cstestcs.interstellar.registry.ModComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Item model hooks that make one gun item look like every possible gun. {@code assets/interstellar/items/gun.json} is
 * a composite of one {@code select} per slot on {@code interstellar:gun_part} (choosing a part model by part id)
 * plus the frame and trim chosen by {@code interstellar:gun_info}; each part is tinted by
 * {@code interstellar:gun_material} with the colour of the material it was made from (and the trim colour on the
 * trim layer).
 */
public final class GunItemModels {
    private GunItemModels() {}

    /** Part id installed in a slot ("" if empty). */
    public record PartProperty(GunData.Slot slot) implements SelectItemModelProperty<String> {
        public static final MapCodec<PartProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                GunCodecs.SLOT.fieldOf("slot").forGetter(PartProperty::slot)).apply(i, PartProperty::new));
        public static final Type<PartProperty, String> TYPE = Type.create(MAP_CODEC, Codec.STRING);

        @Override
        public String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext context) {
            GunBuild build = stack.get(ModComponents.GUN_BUILD.get());
            GunBuild.Choice c = build == null ? null : build.get(slot);
            return c == null ? "" : c.part();
        }

        @Override
        public Codec<String> valueCodec() {
            return Codec.STRING;
        }

        @Override
        public Type<PartProperty, String> type() {
            return TYPE;
        }
    }

    /**
     * Material a slot's part was made from ("" if none). Item models use it to pick the part model textured with the
     * material's block (gold block for gold, diamond block for diamond...).
     */
    public record MaterialProperty(GunData.Slot slot) implements SelectItemModelProperty<String> {
        public static final MapCodec<MaterialProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                GunCodecs.SLOT.fieldOf("slot").forGetter(MaterialProperty::slot)).apply(i, MaterialProperty::new));
        public static final Type<MaterialProperty, String> TYPE = Type.create(MAP_CODEC, Codec.STRING);

        @Override
        public String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext context) {
            GunBuild build = stack.get(ModComponents.GUN_BUILD.get());
            GunBuild.Choice c = build == null ? null : build.get(slot);
            return c == null || c.material() == null ? "" : c.material();
        }

        @Override
        public Codec<String> valueCodec() {
            return Codec.STRING;
        }

        @Override
        public Type<MaterialProperty, String> type() {
            return TYPE;
        }
    }

    /** Gun-wide value: {@code "field": "archetype"} (selects the frame) or {@code "trim"} (trim pattern, "" if none). */
    public record InfoProperty(String field) implements SelectItemModelProperty<String> {
        public static final MapCodec<InfoProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("field", "archetype").forGetter(InfoProperty::field)).apply(i, InfoProperty::new));
        public static final Type<InfoProperty, String> TYPE = Type.create(MAP_CODEC, Codec.STRING);

        @Override
        public String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext context) {
            GunBuild build = stack.get(ModComponents.GUN_BUILD.get());
            if (build == null) return "";
            if (field.equals("trim")) return build.trim() == null ? "" : build.trim().pattern();
            return build.archetype();
        }

        @Override
        public Codec<String> valueCodec() {
            return Codec.STRING;
        }

        @Override
        public Type<InfoProperty, String> type() {
            return TYPE;
        }
    }

    /** Material colour of a slot (or the trim colour when {@code slot} is absent and {@code trim} is true). */
    public record MaterialTint(GunData.Slot slot, boolean trim, int fallback) implements ItemTintSource {
        public static final MapCodec<MaterialTint> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                GunCodecs.SLOT.optionalFieldOf("slot", GunData.Slot.RECEIVER).forGetter(MaterialTint::slot),
                Codec.BOOL.optionalFieldOf("trim", false).forGetter(MaterialTint::trim),
                Codec.INT.optionalFieldOf("default", 0xFFFFFF).forGetter(MaterialTint::fallback)).apply(i, MaterialTint::new));

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
            GunBuild build = stack.get(ModComponents.GUN_BUILD.get());
            if (build == null) return 0xFF000000 | fallback;
            if (trim) return 0xFF000000 | (build.trim() == null ? fallback : build.trim().color());
            GunBuild.Choice c = build.get(slot);
            if (c == null || c.material() == null) return 0xFF000000 | fallback;
            GunRegistry reg = GunDataManager.registry();
            GunData.Material m = reg.material(c.material());
            return 0xFF000000 | (m == null ? fallback : m.color());
        }

        @Override
        public MapCodec<? extends ItemTintSource> type() {
            return MAP_CODEC;
        }
    }

    public static void registerSelectProperties(RegisterSelectItemModelPropertyEvent event) {
        event.register(Interstellar.id("gun_part"), PartProperty.TYPE);
        event.register(Interstellar.id("gun_info"), InfoProperty.TYPE);
        event.register(Interstellar.id("gun_material"), MaterialProperty.TYPE);
    }

    public static void registerItemModels(net.neoforged.neoforge.client.event.RegisterItemModelsEvent event) {
        event.register(Interstellar.id("block_textured"), BlockTexturedItemModel.Unbaked.MAP_CODEC);
    }

    public static void registerTints(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Interstellar.id("gun_material"), MaterialTint.MAP_CODEC);
    }
}
