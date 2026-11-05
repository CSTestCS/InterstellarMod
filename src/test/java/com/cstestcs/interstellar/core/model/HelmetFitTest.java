package com.cstestcs.interstellar.core.model;

import static org.junit.jupiter.api.Assertions.*;

import com.cstestcs.interstellar.core.race.DefaultRaces;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Worn helmets are carried onto each head's skull. */
class HelmetFitTest {
    @Test
    void standardHeadKeepsTheHelmetWhereItIs() {
        ModelDef.PartDef root = new ModelDef.PartDef("root");
        ModelDef.PartDef head = new ModelDef.PartDef("head");
        head.cubes.add(new ModelDef.CubeDef(0, 0, -4, -8, -4, 8, 8, 8, 0, 0, 0, false));
        root.children.put("head", head);
        Matrix4f m = HelmetFit.compute(new ModelDef(64, 64, root));
        assertTrue(m.equals(new Matrix4f(), 1e-5f), m.toString());
    }

    @Test
    void everyBuiltInHeadGetsAHelmetOnItsSkull() throws Exception {
        Map<String, byte[]> files = DefaultRaces.files();
        int n = 0;
        for (var e : files.entrySet()) {
            if (!e.getKey().endsWith("_head.java")) continue;
            ModelDef def = JavaModelInterpreter.interpret(new String(e.getValue(), StandardCharsets.UTF_8));
            Matrix4f m = HelmetFit.compute(def);
            assertNotNull(m, e.getKey());
            // The helmet's centre lands somewhere sensible: above the neck and within a couple of blocks.
            Vector3f c = m.transformPosition(new Vector3f(0, -4 / 16f, 0));
            assertTrue(c.length() < 2f && c.y < 0.2f, e.getKey() + " helmet centre " + c);
            n++;
        }
        assertTrue(n > 10);
    }
}
