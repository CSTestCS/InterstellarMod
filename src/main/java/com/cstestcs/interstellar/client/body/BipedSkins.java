package com.cstestcs.interstellar.client.body;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.image.Image;
import com.cstestcs.interstellar.core.image.SkinConverter;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Converted skins for mobs whose textures do not use the player skin layout (villagers with their biome and
 * profession outfits, illagers, witches, skeletons), built once per texture combination and cached as dynamic
 * textures. Cleared on resource reload.
 */
public final class BipedSkins {
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final Map<String, Boolean> FAILED = new HashMap<>();

    private BipedSkins() {}

    public static @Nullable Identifier get(SkinConverter.Layout layout, List<Identifier> sources) {
        if (sources.isEmpty()) return null;
        StringBuilder key = new StringBuilder(layout.name());
        for (Identifier id : sources) key.append('|').append(id);
        String k = key.toString();
        Identifier cached = CACHE.get(k);
        if (cached != null || FAILED.containsKey(k)) return cached;
        try {
            List<Image> layers = new ArrayList<>();
            for (Identifier id : sources) {
                var res = Minecraft.getInstance().getResourceManager().getResource(id);
                if (res.isEmpty()) continue;
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    Image image = new Image(img.getWidth(), img.getHeight());
                    for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) image.set(x, y, img.getPixel(x, y));
                    layers.add(image);
                }
            }
            if (layers.isEmpty()) {
                FAILED.put(k, true);
                return null;
            }
            Image skin = SkinConverter.toBiped(SkinConverter.composite(layers), layout);
            NativeImage out = new NativeImage(64, 64, true);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) out.setPixel(x, y, skin.get(x, y));
            Identifier id = Interstellar.id("dynamic/biped_skin/" + layout.name().toLowerCase(Locale.ROOT) + "_" + Integer.toHexString(k.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, out));
            CACHE.put(k, id);
            return id;
        } catch (Exception e) {
            Interstellar.LOGGER.warn("Could not convert {} for a custom body: {}", sources, e.toString());
            FAILED.put(k, true);
            return null;
        }
    }

    public static void clear() {
        for (Identifier id : CACHE.values()) Minecraft.getInstance().getTextureManager().release(id);
        CACHE.clear();
        FAILED.clear();
    }
}
