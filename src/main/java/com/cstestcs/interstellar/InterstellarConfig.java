package com.cstestcs.interstellar;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side gameplay settings (per world, synced to clients) and client-only rendering settings. */
public final class InterstellarConfig {
    private InterstellarConfig() {}

    // ------------------------------------------------------------------ server
    public static final class Server {
        public final ModConfigSpec.BooleanValue racesEnabled;
        public final ModConfigSpec.BooleanValue assignRacesToPlayers;
        public final ModConfigSpec.IntValue shipBlockLimit;
        public final ModConfigSpec.DoubleValue spaceScale;
        public final ModConfigSpec.IntValue spaceEntryHeight;
        public final ModConfigSpec.IntValue planetArrivalHeight;
        public final ModConfigSpec.DoubleValue shipMinSpeed;
        public final ModConfigSpec.DoubleValue shipMaxSpeed;
        public final ModConfigSpec.DoubleValue landingSpeed;
        public final ModConfigSpec.IntValue seatPickupRadius;
        public final ModConfigSpec.DoubleValue alienWeaponDropChance;
        public final ModConfigSpec.ConfigValue<List<? extends String>> planetSizeOverrides;
        public final ModConfigSpec.ConfigValue<List<? extends String>> excludedPlanetDimensions;

        Server(ModConfigSpec.Builder b) {
            b.push("races");
            racesEnabled = b.comment("Apply races from the races folder to bipedal mobs when they spawn.").define("enabled", true);
            assignRacesToPlayers = b.comment("Roll a race for players when they first join (otherwise they keep the default 'human').").define("assignToPlayers", false);
            b.pop();
            b.push("spacecraft");
            shipBlockLimit = b.comment("Maximum number of blocks a spacecraft may contain.").defineInRange("blockLimit", 1024, 8, 65536);
            shipMinSpeed = b.comment("Cruise speed (blocks/tick) when the interact key is released.").defineInRange("minSpeed", 0.4, 0.05, 10);
            shipMaxSpeed = b.comment("Boost speed (blocks/tick) while the interact key is held.").defineInRange("maxSpeed", 2.0, 0.1, 20);
            landingSpeed = b.comment("Descent speed (blocks/tick) during the landing procedure.").defineInRange("landingSpeed", 0.25, 0.02, 2);
            seatPickupRadius = b.comment("Mobs (leashed or recruited) this close to an empty passenger seat sit down in it.").defineInRange("seatPickupRadius", 4, 1, 16);
            b.pop();
            b.push("space");
            spaceScale = b.comment("Scale of spacecraft and their passengers inside the space dimension (1.0 = normal size).").defineInRange("scale", 0.1, 0.01, 1);
            spaceEntryHeight = b.comment("Blocks above a dimension's build limit a spacecraft must climb to reach space.").defineInRange("entryHeight", 400, 16, 4096);
            planetArrivalHeight = b.comment("Blocks above the build limit a spacecraft arrives at when entering a planet.").defineInRange("arrivalHeight", 200, 8, 4096);
            planetSizeOverrides = b.comment("Planet sizes, e.g. \"mymod:mars=10\". Sizes are 1-16 blocks.")
                    .defineListAllowEmpty("planetSizes", List.of(), () -> "minecraft:overworld=16", o -> o instanceof String s && s.contains("="));
            excludedPlanetDimensions = b.comment("Dimensions that never get a planet.")
                    .defineListAllowEmpty("excludedDimensions", List.of(), () -> "", o -> o instanceof String);
            b.pop();
            b.push("aliens");
            alienWeaponDropChance = b.comment("Chance an alien drops the gun it carries.").defineInRange("weaponDropChance", 0.30, 0, 1);
            b.pop();
        }
    }

    // ------------------------------------------------------------------ client
    public static final class Client {
        public final ModConfigSpec.BooleanValue customBodies;
        public final ModConfigSpec.ConfigValue<List<? extends String>> disabledBodies;
        public final ModConfigSpec.BooleanValue customAnimations;
        public final ModConfigSpec.BooleanValue deformArmor;
        public final ModConfigSpec.ConfigValue<String> ownBody;
        public final ModConfigSpec.IntValue bodyRenderDistance;
        public final ModConfigSpec.BooleanValue autoThirdPerson;

        Client(ModConfigSpec.Builder b) {
            b.push("bodies");
            customBodies = b.comment("Render custom FBX bodies from the custommodels folder.").define("enabled", true);
            disabledBodies = b.comment("Body names (file names without .fbx) that should never be rendered on this client.")
                    .defineListAllowEmpty("disabled", List.of(), () -> "buff", o -> o instanceof String);
            customAnimations = b.comment("Play Mixamo clips from the customanimations folder (otherwise bodies follow vanilla limb angles).").define("animations", true);
            deformArmor = b.comment("Bend armour and other biped layers to fit custom bodies.").define("deformArmor", true);
            ownBody = b.comment("Body your own player uses: a body name, 'auto' (server assigned) or 'none'.").define("ownBody", "auto");
            bodyRenderDistance = b.comment("Beyond this distance (blocks) bodies render with vanilla models, for performance.").defineInRange("renderDistance", 64, 8, 512);
            b.pop();
            b.push("spacecraft");
            autoThirdPerson = b.comment("Switch to third person automatically while flying a spacecraft.").define("autoThirdPerson", true);
            b.pop();
        }
    }

    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder s = new ModConfigSpec.Builder();
        SERVER = new Server(s);
        SERVER_SPEC = s.build();
        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        CLIENT = new Client(c);
        CLIENT_SPEC = c.build();
    }
}
