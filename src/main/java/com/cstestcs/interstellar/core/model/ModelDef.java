package com.cstestcs.interstellar.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loader-neutral description of a cuboid model (what a Blockbench Java export builds). */
public final class ModelDef {
    public final int textureWidth;
    public final int textureHeight;
    public final PartDef root;

    public ModelDef(int textureWidth, int textureHeight, PartDef root) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.root = root;
    }

    public static final class PartDef {
        public final String name;
        public float x, y, z;          // pivot offset, pixels
        public float xRot, yRot, zRot; // radians
        public float scale = 1f;
        public final List<CubeDef> cubes = new ArrayList<>();
        public final Map<String, PartDef> children = new LinkedHashMap<>();

        public PartDef(String name) {
            this.name = name;
        }

        public PartDef find(String childName) {
            if (name.equals(childName)) return this;
            for (PartDef c : children.values()) {
                PartDef f = c.find(childName);
                if (f != null) return f;
            }
            return null;
        }

        public int cubeCount() {
            int n = cubes.size();
            for (PartDef c : children.values()) n += c.cubeCount();
            return n;
        }
    }

    public record CubeDef(int u, int v, float x, float y, float z, float w, float h, float d,
            float growX, float growY, float growZ, boolean mirror) {}
}
