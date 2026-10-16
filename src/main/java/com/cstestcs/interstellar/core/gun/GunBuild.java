package com.cstestcs.interstellar.core.gun;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Everything that identifies one gun: archetype, the material its frame is made of, the part (and material) in each
 * slot, optional trim, and the per-stat roll multipliers alien weapons get. This is what is stored on the gun item
 * stack. Guns built before frames had materials have a null frame material (their receiver's material is shown).
 */
public record GunBuild(String archetype, Map<GunData.Slot, Choice> parts, GunData.Trim trim, Map<GunData.Stat, Double> rolls, String frame) {
    public record Choice(String part, String material) {}

    public GunBuild(String archetype, Map<GunData.Slot, Choice> parts, GunData.Trim trim, Map<GunData.Stat, Double> rolls) {
        this(archetype, parts, trim, rolls, null);
    }

    public GunBuild {
        parts = Collections.unmodifiableMap(new EnumMap<>(parts.isEmpty() ? new EnumMap<>(GunData.Slot.class) : parts));
        rolls = Collections.unmodifiableMap(rolls.isEmpty() ? new EnumMap<>(GunData.Stat.class) : new EnumMap<>(rolls));
    }

    public GunBuild with(GunData.Slot slot, Choice choice) {
        Map<GunData.Slot, Choice> p = new EnumMap<>(GunData.Slot.class);
        p.putAll(parts);
        if (choice == null) p.remove(slot);
        else p.put(slot, choice);
        return new GunBuild(archetype, p, trim, rolls, frame);
    }

    public GunBuild withTrim(GunData.Trim t) {
        return new GunBuild(archetype, parts, t, rolls, frame);
    }

    public GunBuild withFrame(String material) {
        return new GunBuild(archetype, parts, trim, rolls, material);
    }

    /** Material the frame is drawn in: its own, else (older guns) the receiver's. */
    public String frameMaterial() {
        if (frame != null) return frame;
        Choice receiver = parts.get(GunData.Slot.RECEIVER);
        return receiver == null ? null : receiver.material();
    }

    public Choice get(GunData.Slot slot) {
        return parts.get(slot);
    }
}
