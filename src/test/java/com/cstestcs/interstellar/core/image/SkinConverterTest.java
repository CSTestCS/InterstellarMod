package com.cstestcs.interstellar.core.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkinConverterTest {
    @Test
    void villagerBoxesLandOnBipedBoxes() {
        Image v = new Image(64, 64);
        // Villager head front (8,8)-(16,18) red, legs front (4,26)-(8,38) blue, robe front green.
        v.fill(8, 8, 8, 10, 0xFFFF0000);
        v.fill(4, 26, 4, 12, 0xFF0000FF);
        v.fill(6, 44, 8, 20, 0xFF00FF00);
        Image b = SkinConverter.toBiped(v, SkinConverter.Layout.VILLAGER);
        assertEquals(0xFFFF0000, b.get(12, 12));      // biped head front
        assertEquals(0xFF0000FF, b.get(6, 25));       // right leg front
        assertEquals(0xFF0000FF, b.get(22, 60));      // left leg front (mirrored copy)
        assertEquals(0xFF00FF00, b.get(24, 40));      // jacket front
        assertEquals(0xFF00FF00, b.get(5, 37));       // right pants front, top rows (skirt)
        assertEquals(0, b.get(5, 46));                 // below the skirt stays transparent
    }

    @Test
    void outfitLayersComposite() {
        Image base = new Image(64, 64), cloth = new Image(64, 64);
        base.fill(0, 0, 64, 64, 0xFF808080);
        cloth.fill(0, 0, 8, 8, 0xFF00FF00);
        Image c = SkinConverter.composite(List.of(base, cloth));
        assertEquals(0xFF00FF00, c.get(1, 1));
        assertEquals(0xFF808080, c.get(20, 20));
    }

    @Test
    void thinLimbsAreWidened() {
        Image s = new Image(64, 32);
        s.fill(42, 18, 2, 12, 0xFFFFFFFF); // skeleton right arm front
        Image b = SkinConverter.toBiped(s, SkinConverter.Layout.THIN_BIPED);
        for (int x = 44; x < 48; x++) assertEquals(0xFFFFFFFF, b.get(x, 25));
    }
}
