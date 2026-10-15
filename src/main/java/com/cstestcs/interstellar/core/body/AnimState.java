package com.cstestcs.interstellar.core.body;

import java.util.Locale;

/** Locomotion states that animation clips are chosen for. Clip files are matched by keywords in their name. */
public enum AnimState {
    IDLE(true, "idle", "standing", "breathing"),
    WALK(true, "walk"),
    RUN(true, "run", "sprint", "jog"),
    SNEAK_IDLE(true, "crouch idle", "crouching idle", "sneak idle", "crouched idle"),
    SNEAK_WALK(true, "crouch", "sneak", "crouched"),
    JUMP(false, "jump"),
    FALL(true, "fall", "falling"),
    SWIM(true, "swim", "treading"),
    SIT(true, "sit", "seated", "sitting", "driving", "pilot"),
    FLY(true, "fly", "flying", "glide", "superhero"),
    CLIMB(true, "climb", "ladder"),
    ATTACK(false, "attack", "punch", "slash", "swing", "stab", "hit"),
    SHOOT(true, "shoot", "aim", "rifle", "pistol", "firing"),
    DEATH(false, "death", "dying", "die");

    public final boolean loops;
    private final String[] keywords;

    AnimState(boolean loops, String... keywords) {
        this.loops = loops;
        this.keywords = keywords;
    }

    /** Best matching state for a clip file name, preferring longer (more specific) keywords. */
    public static AnimState fromFileName(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        AnimState best = null;
        int bestLen = 0;
        for (AnimState s : values()) {
            for (String k : s.keywords) {
                if (n.contains(k) && k.length() > bestLen) {
                    best = s;
                    bestLen = k.length();
                }
            }
        }
        return best;
    }

    /** States to try, in order, when no clip exists for this one. */
    public AnimState fallback() {
        return switch (this) {
            case RUN -> WALK;
            case SNEAK_WALK -> WALK;
            case SNEAK_IDLE -> IDLE;
            case JUMP, CLIMB -> FALL;
            case FALL, SIT, FLY, SWIM, SHOOT, ATTACK, DEATH -> IDLE;
            default -> null;
        };
    }
}
