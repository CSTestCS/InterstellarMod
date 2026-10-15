package com.cstestcs.interstellar.core.image;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Minimal ARGB image with a dependency-free PNG encoder, used to generate default textures. */
public final class Image {
    public final int width;
    public final int height;
    public final int[] argb;

    public Image(int width, int height) {
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
    }

    public int get(int x, int y) {
        return argb[y * width + x];
    }

    public void set(int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < width && y < height) argb[y * width + x] = color;
    }

    public void fill(int x0, int y0, int w, int h, int color) {
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) set(x, y, color);
    }

    public byte[] toPng() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(ihdr);
            d.writeInt(width);
            d.writeInt(height);
            d.writeByte(8); // bit depth
            d.writeByte(6); // RGBA
            d.writeByte(0);
            d.writeByte(0);
            d.writeByte(0);
            chunk(out, "IHDR", ihdr.toByteArray());

            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DeflaterOutputStream z = new DeflaterOutputStream(raw, new Deflater(9))) {
                byte[] row = new byte[width * 4 + 1];
                for (int y = 0; y < height; y++) {
                    row[0] = 0;
                    for (int x = 0; x < width; x++) {
                        int c = argb[y * width + x];
                        row[1 + x * 4] = (byte) (c >> 16);
                        row[2 + x * 4] = (byte) (c >> 8);
                        row[3 + x * 4] = (byte) c;
                        row[4 + x * 4] = (byte) (c >>> 24);
                    }
                    z.write(row);
                }
            }
            chunk(out, "IDAT", raw.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        d.writeInt(data.length);
        byte[] t = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        d.write(t);
        d.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int shade(int color, float f) {
        return rgb((int) (((color >> 16) & 255) * f), (int) (((color >> 8) & 255) * f), (int) ((color & 255) * f));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
