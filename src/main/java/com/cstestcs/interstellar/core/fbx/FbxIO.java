package com.cstestcs.interstellar.core.fbx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Reader and writer for the binary FBX container (versions 7.x, both 32- and 64-bit record headers). */
public final class FbxIO {
    private static final byte[] MAGIC = "Kaydara FBX Binary  \0".getBytes(StandardCharsets.ISO_8859_1);

    private FbxIO() {}

    public static final class FbxFormatException extends IOException {
        public FbxFormatException(String msg) {
            super(msg);
        }
    }

    // ------------------------------------------------------------------ reading

    /** @return a synthetic root node whose children are the top level records. */
    public static FbxNode read(byte[] data) throws FbxFormatException {
        if (data.length < 27 || !startsWith(data, MAGIC)) {
            if (new String(data, 0, Math.min(data.length, 64), StandardCharsets.ISO_8859_1).contains("FBX")) {
                throw new FbxFormatException("ASCII FBX is not supported; re-export as binary FBX (Mixamo and Blender default to binary)");
            }
            throw new FbxFormatException("not an FBX file");
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(23);
        int version = buf.getInt();
        boolean wide = version >= 7500;
        FbxNode root = new FbxNode("");
        root.add(version);
        while (buf.remaining() > (wide ? 25 : 13)) {
            FbxNode n = readNode(buf, wide);
            if (n == null) break;
            root.children.add(n);
        }
        return root;
    }

    private static FbxNode readNode(ByteBuffer buf, boolean wide) throws FbxFormatException {
        long endOffset = wide ? buf.getLong() : Integer.toUnsignedLong(buf.getInt());
        long numProps = wide ? buf.getLong() : Integer.toUnsignedLong(buf.getInt());
        if (wide) buf.getLong();
        else buf.getInt(); // property list length
        int nameLen = buf.get() & 0xFF;
        if (endOffset == 0) return null; // NULL record
        if (endOffset > buf.capacity()) throw new FbxFormatException("corrupt record end offset " + endOffset);
        byte[] nameBytes = new byte[nameLen];
        buf.get(nameBytes);
        FbxNode node = new FbxNode(new String(nameBytes, StandardCharsets.UTF_8));
        for (long i = 0; i < numProps; i++) node.props.add(readProp(buf));
        while (buf.position() < endOffset) {
            FbxNode child = readNode(buf, wide);
            if (child == null) break;
            node.children.add(child);
        }
        buf.position((int) endOffset);
        return node;
    }

    private static Object readProp(ByteBuffer buf) throws FbxFormatException {
        char type = (char) buf.get();
        switch (type) {
            case 'Y': return buf.getShort();
            case 'C': return buf.get() != 0;
            case 'I': return buf.getInt();
            case 'F': return buf.getFloat();
            case 'D': return buf.getDouble();
            case 'L': return buf.getLong();
            case 'S': {
                byte[] b = new byte[buf.getInt()];
                buf.get(b);
                // Object names are stored as "Name\0\1Class"; keep only the name.
                String s = new String(b, StandardCharsets.UTF_8);
                int sep = s.indexOf("\u0000\u0001");
                return sep >= 0 ? s.substring(0, sep) : s;
            }
            case 'R': {
                byte[] b = new byte[buf.getInt()];
                buf.get(b);
                return b;
            }
            case 'f': case 'd': case 'l': case 'i': case 'b':
                return readArray(buf, type);
            default:
                throw new FbxFormatException("unknown property type '" + type + "' at " + (buf.position() - 1));
        }
    }

    private static Object readArray(ByteBuffer buf, char type) throws FbxFormatException {
        int len = buf.getInt();
        int encoding = buf.getInt();
        int compressedLen = buf.getInt();
        int elem = switch (type) { case 'f', 'i' -> 4; case 'd', 'l' -> 8; default -> 1; };
        ByteBuffer src;
        if (encoding == 1) {
            byte[] comp = new byte[compressedLen];
            buf.get(comp);
            byte[] raw = new byte[len * elem];
            Inflater inf = new Inflater();
            inf.setInput(comp);
            try {
                int n = 0;
                while (n < raw.length && !inf.finished()) {
                    int r = inf.inflate(raw, n, raw.length - n);
                    if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                    n += r;
                }
            } catch (DataFormatException e) {
                throw new FbxFormatException("bad zlib array: " + e.getMessage());
            } finally {
                inf.end();
            }
            src = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        } else {
            byte[] raw = new byte[len * elem];
            buf.get(raw);
            src = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        }
        switch (type) {
            case 'f': { float[] a = new float[len]; src.asFloatBuffer().get(a); return a; }
            case 'd': { double[] a = new double[len]; src.asDoubleBuffer().get(a); return a; }
            case 'l': { long[] a = new long[len]; src.asLongBuffer().get(a); return a; }
            case 'i': { int[] a = new int[len]; src.asIntBuffer().get(a); return a; }
            default: { boolean[] a = new boolean[len]; for (int i = 0; i < len; i++) a[i] = src.get() != 0; return a; }
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    // ------------------------------------------------------------------ writing (version 7400)

    public static byte[] write(List<FbxNode> topLevel) {
        Out out = new Out();
        out.bytes(MAGIC);
        out.u8(0x1A);
        out.u8(0x00);
        out.i32(7400);
        for (FbxNode n : topLevel) writeNode(out, n);
        out.zero(13); // top-level NULL record
        // Footer: magic-ish id, padding to 16, version, 120 zero bytes and the closing magic.
        out.bytes(new byte[] { (byte) 0xfa, (byte) 0xbc, (byte) 0xab, 0x09, (byte) 0xd0, (byte) 0xc8, (byte) 0xd4, 0x66,
                (byte) 0xb1, 0x76, (byte) 0xfb, (byte) 0x83, 0x1c, (byte) 0xf7, 0x26, 0x7e });
        out.zero(4);
        int pad = (16 - (out.size() % 16)) % 16;
        out.zero(pad == 0 ? 16 : pad);
        out.i32(7400);
        out.zero(120);
        out.bytes(new byte[] { (byte) 0xf8, 0x5a, (byte) 0x8c, 0x6a, (byte) 0xde, (byte) 0xf5, (byte) 0xd9, 0x7e,
                (byte) 0xec, (byte) 0xe9, 0x0c, (byte) 0xe3, 0x75, (byte) 0x8f, 0x29, 0x0b });
        return out.toByteArray();
    }

    private static void writeNode(Out out, FbxNode n) {
        int start = out.size();
        out.i32(0); // end offset placeholder
        out.i32(n.props.size());
        out.i32(0); // prop list length placeholder
        byte[] name = n.name.getBytes(StandardCharsets.UTF_8);
        out.u8(name.length);
        out.bytes(name);
        int propStart = out.size();
        for (Object p : n.props) writeProp(out, p);
        out.patch(start + 8, out.size() - propStart);
        if (!n.children.isEmpty()) {
            for (FbxNode c : n.children) writeNode(out, c);
            out.zero(13);
        }
        out.patch(start, out.size());
    }

    private static void writeProp(Out out, Object p) {
        if (p instanceof Short s) { out.u8('Y'); out.i16(s); }
        else if (p instanceof Boolean b) { out.u8('C'); out.u8(b ? 1 : 0); }
        else if (p instanceof Integer i) { out.u8('I'); out.i32(i); }
        else if (p instanceof Float f) { out.u8('F'); out.i32(Float.floatToIntBits(f)); }
        else if (p instanceof Double d) { out.u8('D'); out.i64(Double.doubleToLongBits(d)); }
        else if (p instanceof Long l) { out.u8('L'); out.i64(l); }
        else if (p instanceof String s) { byte[] b = s.getBytes(StandardCharsets.UTF_8); out.u8('S'); out.i32(b.length); out.bytes(b); }
        else if (p instanceof byte[] b) { out.u8('R'); out.i32(b.length); out.bytes(b); }
        else if (p instanceof double[] a) { ByteBuffer bb = le(a.length * 8); for (double v : a) bb.putDouble(v); array(out, 'd', a.length, bb.array()); }
        else if (p instanceof float[] a) { ByteBuffer bb = le(a.length * 4); for (float v : a) bb.putFloat(v); array(out, 'f', a.length, bb.array()); }
        else if (p instanceof int[] a) { ByteBuffer bb = le(a.length * 4); for (int v : a) bb.putInt(v); array(out, 'i', a.length, bb.array()); }
        else if (p instanceof long[] a) { ByteBuffer bb = le(a.length * 8); for (long v : a) bb.putLong(v); array(out, 'l', a.length, bb.array()); }
        else throw new IllegalArgumentException("cannot write FBX property " + p);
    }

    /** Object names are written with the class suffix FBX expects ("Name\0\1Class"). */
    public static String objectName(String name, String cls) {
        return name + "\u0000\u0001" + cls;
    }

    private static void array(Out out, char type, int len, byte[] raw) {
        Deflater def = new Deflater();
        def.setInput(raw);
        def.finish();
        ByteArrayOutputStream comp = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        while (!def.finished()) comp.write(tmp, 0, def.deflate(tmp));
        def.end();
        out.u8(type);
        out.i32(len);
        out.i32(1);
        out.i32(comp.size());
        out.bytes(comp.toByteArray());
    }

    private static ByteBuffer le(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static final class Out {
        private byte[] buf = new byte[1 << 16];
        private int size;

        void ensure(int n) {
            if (size + n > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length * 2, size + n));
        }

        void u8(int v) { ensure(1); buf[size++] = (byte) v; }
        void i16(short v) { ensure(2); buf[size++] = (byte) v; buf[size++] = (byte) (v >> 8); }
        void i32(int v) { ensure(4); for (int i = 0; i < 4; i++) buf[size++] = (byte) (v >> (8 * i)); }
        void i64(long v) { ensure(8); for (int i = 0; i < 8; i++) buf[size++] = (byte) (v >> (8 * i)); }
        void bytes(byte[] b) { ensure(b.length); System.arraycopy(b, 0, buf, size, b.length); size += b.length; }
        void zero(int n) { ensure(n); size += n; }
        void patch(int at, int v) { for (int i = 0; i < 4; i++) buf[at + i] = (byte) (v >> (8 * i)); }
        int size() { return size; }
        byte[] toByteArray() { return java.util.Arrays.copyOf(buf, size); }
    }

    /** Convenience for tests and tools. */
    public static List<FbxNode> list(FbxNode... nodes) {
        return new ArrayList<>(List.of(nodes));
    }
}
