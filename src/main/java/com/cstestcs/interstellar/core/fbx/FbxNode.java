package com.cstestcs.interstellar.core.fbx;

import java.util.ArrayList;
import java.util.List;

/** One record of an FBX node tree. Properties are boxed primitives, Strings, byte[] or primitive arrays. */
public final class FbxNode {
    public final String name;
    public final List<Object> props = new ArrayList<>();
    public final List<FbxNode> children = new ArrayList<>();

    public FbxNode(String name) {
        this.name = name;
    }

    public FbxNode add(Object... values) {
        for (Object v : values) props.add(v);
        return this;
    }

    public FbxNode child(FbxNode c) {
        children.add(c);
        return c;
    }

    public FbxNode child(String name, Object... values) {
        return child(new FbxNode(name).add(values));
    }

    public FbxNode first(String childName) {
        for (FbxNode c : children) if (c.name.equals(childName)) return c;
        return null;
    }

    public List<FbxNode> all(String childName) {
        List<FbxNode> out = new ArrayList<>();
        for (FbxNode c : children) if (c.name.equals(childName)) out.add(c);
        return out;
    }

    public Object prop(int i) {
        return i < props.size() ? props.get(i) : null;
    }

    public long longProp(int i) {
        Object o = prop(i);
        return o instanceof Number n ? n.longValue() : 0;
    }

    public double doubleProp(int i) {
        Object o = prop(i);
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    public String stringProp(int i) {
        Object o = prop(i);
        return o instanceof String s ? s : null;
    }

    /** First property of the named child as a double array (accepts float/int/long arrays too). */
    public double[] doubles(String childName) {
        FbxNode c = first(childName);
        if (c == null) return null;
        Object o = c.prop(0);
        if (o instanceof double[] d) return d;
        if (o instanceof float[] f) {
            double[] d = new double[f.length];
            for (int i = 0; i < f.length; i++) d[i] = f[i];
            return d;
        }
        if (o instanceof int[] in) {
            double[] d = new double[in.length];
            for (int i = 0; i < in.length; i++) d[i] = in[i];
            return d;
        }
        if (o instanceof long[] l) {
            double[] d = new double[l.length];
            for (int i = 0; i < l.length; i++) d[i] = l[i];
            return d;
        }
        return null;
    }

    public int[] ints(String childName) {
        FbxNode c = first(childName);
        if (c == null) return null;
        Object o = c.prop(0);
        if (o instanceof int[] in) return in;
        if (o instanceof long[] l) {
            int[] out = new int[l.length];
            for (int i = 0; i < l.length; i++) out[i] = (int) l[i];
            return out;
        }
        return null;
    }

    public long[] longs(String childName) {
        FbxNode c = first(childName);
        if (c == null) return null;
        Object o = c.prop(0);
        if (o instanceof long[] l) return l;
        if (o instanceof int[] in) {
            long[] out = new long[in.length];
            for (int i = 0; i < in.length; i++) out[i] = in[i];
            return out;
        }
        return null;
    }

    public String childString(String childName) {
        FbxNode c = first(childName);
        return c == null ? null : c.stringProp(0);
    }

    /** Finds a "P" entry inside Properties70 by name and returns its values (props after the 4 header strings). */
    public List<Object> p70(String propName) {
        FbxNode p70 = first("Properties70");
        if (p70 == null) return null;
        for (FbxNode p : p70.children) {
            if (p.name.equals("P") && propName.equals(p.stringProp(0))) return p.props.subList(Math.min(4, p.props.size()), p.props.size());
        }
        return null;
    }

    public double[] p70Vec(String propName, double[] def) {
        List<Object> v = p70(propName);
        if (v == null || v.size() < 3) return def;
        return new double[] { ((Number) v.get(0)).doubleValue(), ((Number) v.get(1)).doubleValue(), ((Number) v.get(2)).doubleValue() };
    }

    public double p70Num(String propName, double def) {
        List<Object> v = p70(propName);
        if (v == null || v.isEmpty() || !(v.get(0) instanceof Number n)) return def;
        return n.doubleValue();
    }

    @Override
    public String toString() {
        return name + props;
    }
}
