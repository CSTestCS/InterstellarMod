package com.cstestcs.interstellar.body;

import com.cstestcs.interstellar.core.body.BodyMeta;
import com.cstestcs.interstellar.core.race.RaceDefinition;
import com.cstestcs.interstellar.core.race.RaceFiles;
import com.cstestcs.interstellar.network.ModPayloads;
import com.cstestcs.interstellar.race.RaceEvents;
import com.cstestcs.interstellar.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Custom bodies are client-side models, but which body a mob uses must be the same for everyone. Each client reports
 * the bodies it has installed; the server picks from the union of all reports and stores the choice on the entity
 * (a synced attachment). A client renders the body if it has it installed and enabled, otherwise the vanilla model,
 * so two players who both installed "buff" always see the same buff villager.
 */
public final class BodyServer {
    private static final Map<UUID, Map<String, BodyMeta.BodyGender>> REPORTED = new ConcurrentHashMap<>();
    private static final Map<UUID, String> OWN_CHOICE = new ConcurrentHashMap<>();
    private static volatile Map<String, BodyMeta.BodyGender> union = Map.of();

    private BodyServer() {}

    public static void onInstalledBodies(ServerPlayer player, ModPayloads.InstalledBodies payload) {
        Map<String, BodyMeta.BodyGender> mine = new TreeMap<>();
        for (int i = 0; i < Math.min(payload.names().size(), 256); i++) {
            String name = payload.names().get(i);
            if (name.length() > 64) continue;
            mine.put(name, BodyMeta.BodyGender.parse(i < payload.genders().size() ? payload.genders().get(i) : "any"));
        }
        REPORTED.put(player.getUUID(), mine);
        OWN_CHOICE.put(player.getUUID(), payload.ownChoice());
        boolean grew = rebuildUnion();
        reassign(player, RaceEvents.raceOf(player));
        if (grew) {
            // New bodies became available: give them to loaded bipeds that have none yet.
            for (ServerLevel level : player.level().getServer().getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (e instanceof LivingEntity living && !(living instanceof Player) && living.getData(ModAttachments.BODY).isEmpty()
                            && living.getType().is(RaceEvents.BIPEDS)) {
                        assignIfMissing(living, RaceEvents.raceOf(living));
                    }
                }
            }
        }
    }

    public static void forget(UUID player) {
        REPORTED.remove(player);
        OWN_CHOICE.remove(player);
        rebuildUnion();
    }

    private static boolean rebuildUnion() {
        Map<String, BodyMeta.BodyGender> u = new TreeMap<>();
        for (Map<String, BodyMeta.BodyGender> m : REPORTED.values()) m.forEach(u::putIfAbsent);
        boolean grew = !union.keySet().containsAll(u.keySet());
        union = u;
        return grew;
    }

    public static void assignIfMissing(LivingEntity living, @Nullable RaceDefinition race) {
        if (!living.getData(ModAttachments.BODY).isEmpty() || union.isEmpty()) return;
        reassign(living, race);
    }

    public static void reassign(LivingEntity living, @Nullable RaceDefinition race) {
        String body;
        if (living instanceof Player p) {
            String choice = OWN_CHOICE.getOrDefault(p.getUUID(), "auto");
            body = choice.equals("auto") ? choose(living.getUUID(), race) : choice.equals("none") ? "" : choice;
        } else {
            body = choose(living.getUUID(), race);
        }
        if (!body.equals(living.getData(ModAttachments.BODY))) living.setData(ModAttachments.BODY, body);
    }

    /**
     * Picks a body for an entity. Rendezvous hashing over the eligible names makes the choice stable: adding a new
     * body only moves the entities that now prefer it.
     */
    static String choose(UUID id, @Nullable RaceDefinition race) {
        List<String> eligible = new ArrayList<>();
        List<String> wanted = race == null ? List.of("any") : race.bodyTypes();
        if (wanted.contains("none")) return "";
        RaceDefinition.Gender gender = race == null ? RaceDefinition.Gender.NULL : race.gender();
        for (Map.Entry<String, BodyMeta.BodyGender> e : union.entrySet()) {
            boolean named = wanted.contains(e.getKey().toLowerCase(java.util.Locale.ROOT));
            if ((named || wanted.contains("any")) && e.getValue().suits(gender)) eligible.add(e.getKey());
        }
        // Prefer explicitly named bodies when any of them are available.
        List<String> named = eligible.stream().filter(n -> wanted.contains(n.toLowerCase(java.util.Locale.ROOT))).toList();
        if (!named.isEmpty()) eligible = named;
        if (eligible.isEmpty()) return "";
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        String best = "";
        long bestScore = Long.MIN_VALUE;
        for (String n : eligible) {
            long score = RaceFiles.mix(seed ^ n.hashCode() * 0x9E3779B97F4A7C15L);
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }
}
