package com.cstestcs.interstellar.core.race;

import com.cstestcs.interstellar.core.image.Image;
import com.cstestcs.interstellar.core.image.SkinLayout;
import com.cstestcs.interstellar.core.model.ModelDef;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Builds a race head model and paints its texture at the same time: every cube gets its own area of the texture
 * (packed left to right, row by row), filled with a material (colour, noise and pattern), and faces can carry small
 * pixel-art decals (eyes, mouths, markings). Model space as in Blockbench/vanilla: y grows downwards, the face looks
 * towards -z, the head's pivot is the neck.
 */
final class HeadKit {
    /** How a cube's faces are painted. */
    record Mat(int color, float noise, Pattern pattern, int accent) {
        Mat(int color) {
            this(color, 0.08f, Pattern.PLAIN, color);
        }

        Mat with(Pattern p, int accent) {
            return new Mat(color, noise, p, accent);
        }

        Mat noise(float n) {
            return new Mat(color, n, pattern, accent);
        }
    }

    /** ROWS and COLUMNS alternate colour and accent every pixel (rows of teeth, ridged tooth edges). */
    enum Pattern { PLAIN, SPOTS, STRIPES, SCALES, FUR, PIXELS, PLATES, GLOW, ROWS, COLUMNS }

    /** A cube and where its faces are in the texture. */
    record Cube(int u, int v, int w, int h, int d) {
        int[] face(SkinLayout.Face f) {
            return SkinLayout.faceRect(u, v, w, h, d, f);
        }
    }

    final Image tex;
    final ModelDef.PartDef root = new ModelDef.PartDef("root");
    final ModelDef.PartDef head = new ModelDef.PartDef("head");
    private final SplittableRandom rng;
    private int cu, cv, rowH;
    /** Material of the first (main) cube: used for the neck. */
    private Mat main;

    HeadKit(int size, long seed) {
        tex = new Image(size, size);
        rng = new SplittableRandom(seed);
        root.children.put("head", head);
    }

    /**
     * Makes sure the head sits on the body: the neck pivot is y = 0, so a head whose lowest cube ends above it would
     * float. Such heads get a neck in their main colour, reaching from under the skull down to the body.
     */
    void connect() {
        if (head.cubes.isEmpty()) return;
        float bottom = -99, minX = 99, maxX = -99, minZ = 99, maxZ = -99;
        ModelDef.CubeDef skull = head.cubes.get(0);
        for (ModelDef.CubeDef c : head.cubes) bottom = Math.max(bottom, c.y() + c.h());
        float gap = -bottom;
        if (gap <= 0.01f) return;
        minX = skull.x();
        maxX = skull.x() + skull.w();
        minZ = skull.z();
        maxZ = skull.z() + skull.d();
        int w = Math.max(2, Math.min(4, (int) (maxX - minX) - 2)), d = Math.max(2, Math.min(4, (int) (maxZ - minZ) - 2));
        int h = (int) Math.ceil(gap) + 1; // reaches a little into the skull so nothing shows between them
        float cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        box(head, cx - w / 2f, -h, cz - d / 2f, w, h, d, main == null ? new Mat(0xFF808080) : main.with(Pattern.PLAIN, main.color()));
    }

    /** Width of the main head cube (pixels). */
    float width() {
        return head.cubes.isEmpty() ? 8 : head.cubes.get(0).w();
    }

    ModelDef model() {
        return new ModelDef(tex.width, tex.height, root);
    }

    /** A child part (pivot in the parent's space, rotation in degrees). */
    ModelDef.PartDef part(ModelDef.PartDef parent, String name, float x, float y, float z, float xDeg, float yDeg, float zDeg) {
        ModelDef.PartDef p = new ModelDef.PartDef(name);
        p.x = x;
        p.y = y;
        p.z = z;
        p.xRot = (float) Math.toRadians(xDeg);
        p.yRot = (float) Math.toRadians(yDeg);
        p.zRot = (float) Math.toRadians(zDeg);
        parent.children.put(name, p);
        return p;
    }

    /** Adds a cube to a part and paints it. Sizes are whole pixels so the texture lines up. */
    Cube box(ModelDef.PartDef p, float x, float y, float z, int w, int h, int d, Mat m) {
        return box(p, x, y, z, w, h, d, m, false);
    }

    Cube box(ModelDef.PartDef p, float x, float y, float z, int w, int h, int d, Mat m, boolean mirror) {
        int bw = 2 * (w + d), bh = d + h;
        if (cu + bw > tex.width) {
            cu = 0;
            cv += rowH;
            rowH = 0;
        }
        if (cv + bh > tex.height) throw new IllegalStateException("head texture full");
        Cube c = new Cube(cu, cv, w, h, d);
        cu += bw;
        rowH = Math.max(rowH, bh);
        p.cubes.add(new ModelDef.CubeDef(c.u, c.v, x, y, z, w, h, d, 0, 0, 0, mirror));
        if (main == null && p == head) main = m;
        paint(c, m);
        return c;
    }

    private void paint(Cube c, Mat m) {
        for (SkinLayout.Face f : SkinLayout.Face.values()) {
            int[] r = c.face(f);
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    int lx = x - r[0], ly = y - r[1];
                    int col = switch (m.pattern) {
                        case PLAIN -> m.color;
                        case SPOTS -> hash(x * 7 + y * 13) % 9 < 2 ? m.accent : m.color;
                        case STRIPES -> ly % 3 == 0 ? m.accent : m.color;
                        case SCALES -> (lx + (ly % 2)) % 2 == 0 ? m.color : Image.shade(m.color, 0.86f);
                        case FUR -> rng.nextInt(4) == 0 ? m.accent : m.color;
                        case PIXELS -> pick(m.color, m.accent, hash(x * 31 + y * 17));
                        case PLATES -> lx == 0 || ly == 0 ? Image.shade(m.color, 0.7f) : m.color;
                        case GLOW -> (lx + ly) % 2 == 0 ? m.color : m.accent;
                        case ROWS -> ly % 2 == 0 ? m.color : m.accent;
                        case COLUMNS -> lx % 2 == 0 ? m.color : m.accent;
                    };
                    float shade = f == SkinLayout.Face.TOP ? 1.06f : f == SkinLayout.Face.BOTTOM ? 0.8f : 1f;
                    tex.set(x, y, Image.shade(col, shade * (1 - m.noise / 2 + rng.nextFloat() * m.noise)));
                }
            }
        }
    }

    private static int pick(int a, int b, int h) {
        return switch (h % 4) {
            case 0 -> Image.shade(a, 0.75f);
            case 1 -> b;
            default -> a;
        };
    }

    private static int hash(int i) {
        i = (i ^ 61) ^ (i >>> 16);
        i *= 9;
        i ^= i >>> 4;
        i *= 0x27d4eb2d;
        return (i ^ (i >>> 15)) & 0x7fffffff;
    }

    /**
     * Pixel art on one face, anchored at its top left (offset by dx, dy). Each character of a row is a pixel; '.'
     * and ' ' leave the face as it is.
     */
    void decal(Cube c, SkinLayout.Face f, int dx, int dy, Map<Character, Integer> colors, String... rows) {
        int[] r = c.face(f);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                Integer col = colors.get(rows[y].charAt(x));
                int px = r[0] + dx + x, py = r[1] + dy + y;
                if (col != null && px < r[2] && py < r[3]) tex.set(px, py, col);
            }
        }
    }

    /** Fills a rectangle of a face (relative to its top left). */
    void fill(Cube c, SkinLayout.Face f, int dx, int dy, int w, int h, int color) {
        int[] r = c.face(f);
        for (int y = r[1] + dy; y < Math.min(r[3], r[1] + dy + h); y++) {
            for (int x = r[0] + dx; x < Math.min(r[2], r[0] + dx + w); x++) tex.set(x, y, color);
        }
    }

    /** The same rectangle on every side face (bands, collars). */
    void band(Cube c, int dy, int h, int color) {
        for (SkinLayout.Face f : new SkinLayout.Face[] { SkinLayout.Face.FRONT, SkinLayout.Face.BACK, SkinLayout.Face.LEFT, SkinLayout.Face.RIGHT }) {
            fill(c, f, 0, dy, 64, h, color);
        }
    }

    static int rgb(int hex) {
        return 0xFF000000 | hex;
    }
}
