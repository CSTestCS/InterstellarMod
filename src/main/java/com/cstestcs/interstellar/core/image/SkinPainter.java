package com.cstestcs.interstellar.core.image;

import java.util.SplittableRandom;

/** Procedurally paints skins in the 64x64 biped layout (used for generated alien races). */
public final class SkinPainter {
    private SkinPainter() {}

    public record Palette(int skin, int suit, int accent, int eye, int tail) {}

    public static Palette randomPalette(long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        float hue = r.nextFloat();
        int skin = hsv(hue, 0.35f + r.nextFloat() * 0.4f, 0.55f + r.nextFloat() * 0.35f);
        int suit = hsv((hue + 0.4f + r.nextFloat() * 0.2f) % 1f, 0.4f + r.nextFloat() * 0.4f, 0.25f + r.nextFloat() * 0.3f);
        int accent = hsv((hue + 0.5f) % 1f, 0.8f, 0.9f);
        int eye = r.nextBoolean() ? hsv(r.nextFloat(), 0.9f, 1f) : 0xFF101010;
        int tail = Image.shade(skin, 0.85f);
        return new Palette(skin, suit, accent, eye, tail);
    }

    public static Image paintBiped(Palette p, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        Image img = new Image(64, 64);
        int pattern = r.nextInt(3); // 0 plain, 1 stripes, 2 spots
        paintBox(img, SkinLayout.Part.HEAD, p.skin, r, pattern, p.accent);
        paintBox(img, SkinLayout.Part.BODY, p.suit, r, 0, p.accent);
        paintBox(img, SkinLayout.Part.RIGHT_ARM, p.skin, r, pattern, p.accent);
        paintBox(img, SkinLayout.Part.LEFT_ARM, p.skin, r, pattern, p.accent);
        paintBox(img, SkinLayout.Part.RIGHT_LEG, Image.shade(p.suit, 0.8f), r, 0, p.accent);
        paintBox(img, SkinLayout.Part.LEFT_LEG, Image.shade(p.suit, 0.8f), r, 0, p.accent);
        // Sleeves cover the upper arm with the suit colour; the rest of the overlay stays transparent.
        for (SkinLayout.Part sleeve : new SkinLayout.Part[] { SkinLayout.Part.RIGHT_SLEEVE, SkinLayout.Part.LEFT_SLEEVE }) {
            for (SkinLayout.Face f : SkinLayout.Face.values()) {
                int[] rect = SkinLayout.faceRect(sleeve, f);
                int bottom = f == SkinLayout.Face.TOP || f == SkinLayout.Face.BOTTOM ? rect[3] : rect[1] + 4;
                for (int y = rect[1]; y < bottom; y++) for (int x = rect[0]; x < rect[2]; x++) img.set(x, y, noisy(p.suit, r));
            }
        }
        // Belt accent.
        int[] front = SkinLayout.faceRect(SkinLayout.Part.BODY, SkinLayout.Face.FRONT);
        img.fill(front[0], front[3] - 2, front[2] - front[0], 1, p.accent);
        // Eyes on the face.
        int[] face = SkinLayout.faceRect(SkinLayout.Part.HEAD, SkinLayout.Face.FRONT);
        int eyeY = face[1] + 3 + r.nextInt(2);
        int eyeW = 1 + r.nextInt(2);
        img.fill(face[0] + 1, eyeY, eyeW, 1 + r.nextInt(2), p.eye);
        img.fill(face[2] - 1 - eyeW, eyeY, eyeW, 1 + r.nextInt(2), p.eye);
        img.fill(face[0] + 3, face[1] + 6, 2, 1, Image.shade(p.skin, 0.5f));
        // Tail colour source (top-left 8x8).
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) img.set(x, y, noisy(p.tail, r));
        return img;
    }

    /** Head-model texture: noise in the skin tone, with the bottom-right 8x8 reserved for eyes. */
    public static Image paintHeadTexture(Palette p, long seed) {
        SplittableRandom r = new SplittableRandom(seed ^ 0x5EED);
        Image img = new Image(64, 64);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.set(x, y, noisy(p.skin, r));
        img.fill(48, 48, 16, 16, p.eye);
        img.fill(32, 48, 16, 16, p.accent);
        return img;
    }

    private static void paintBox(Image img, SkinLayout.Part part, int color, SplittableRandom r, int pattern, int accent) {
        for (SkinLayout.Face f : SkinLayout.Face.values()) {
            int[] rect = SkinLayout.faceRect(part, f);
            for (int y = rect[1]; y < rect[3]; y++) {
                for (int x = rect[0]; x < rect[2]; x++) {
                    int c = color;
                    if (pattern == 1 && ((y - rect[1]) % 4 == 0)) c = Image.shade(color, 0.7f);
                    if (pattern == 2 && r.nextInt(9) == 0) c = Image.shade(accent, 0.6f);
                    img.set(x, y, noisy(c, r));
                }
            }
        }
    }

    private static int noisy(int c, SplittableRandom r) {
        return Image.shade(c, 0.9f + r.nextFloat() * 0.2f);
    }

    public static int hsv(float h, float s, float v) {
        int i = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6), p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return Image.rgb((int) (r * 255), (int) (g * 255), (int) (b * 255));
    }
}
