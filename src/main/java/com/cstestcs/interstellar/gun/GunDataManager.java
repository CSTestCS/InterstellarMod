package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.network.ModPayloads;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Loads {@code data/<ns>/gun/{archetype,part,material,magazine}/*.json} on every datapack reload and sends it to
 * clients. Ids are the file name (prefixed with the namespace unless it is "interstellar").
 */
public final class GunDataManager extends SimplePreparableReloadListener<Map<String, Map<String, JsonObject>>> {
    public static final GunDataManager INSTANCE = new GunDataManager();
    private static final String[] KINDS = { "archetype", "part", "material", "magazine" };

    public record Cost(Item item, int count) {}

    private static volatile GunRegistry registry = GunRegistry.EMPTY;
    private static volatile Map<String, List<Cost>> costs = Map.of();
    private static volatile Map<String, String> rawJson = Map.of();

    private GunDataManager() {}

    public static GunRegistry registry() {
        return registry;
    }

    /** Crafting cost of an archetype frame ("archetype:<id>"), part ("part:<id>") or magazine ("magazine:<kind>"). */
    public static List<Cost> cost(String kind, String id) {
        return costs.getOrDefault(kind + ":" + id, List.of());
    }

    @Override
    protected Map<String, Map<String, JsonObject>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Map<String, JsonObject>> out = new HashMap<>();
        for (String kind : KINDS) {
            Map<String, JsonObject> map = new LinkedHashMap<>();
            String dir = "gun/" + kind;
            for (Map.Entry<Identifier, Resource> e : manager.listResources(dir, id -> id.getPath().endsWith(".json")).entrySet()) {
                Identifier file = e.getKey();
                String path = file.getPath().substring(dir.length() + 1, file.getPath().length() - 5);
                String id = file.getNamespace().equals(Interstellar.MODID) ? path : file.getNamespace() + ":" + path;
                try (Reader reader = e.getValue().openAsReader()) {
                    JsonElement json = JsonParser.parseReader(reader);
                    if (json.isJsonObject()) map.put(id, json.getAsJsonObject());
                } catch (Exception ex) {
                    Interstellar.LOGGER.error("Bad gun data file {}", file, ex);
                }
            }
            out.put(kind, map);
        }
        return out;
    }

    @Override
    protected void apply(Map<String, Map<String, JsonObject>> data, ResourceManager manager, ProfilerFiller profiler) {
        load(data);
        Map<String, String> raw = new HashMap<>();
        for (String kind : KINDS) {
            JsonObject bundle = new JsonObject();
            data.getOrDefault(kind, Map.of()).forEach(bundle::add);
            raw.put(kind, bundle.toString());
        }
        rawJson = raw;
    }

    private static void load(Map<String, Map<String, JsonObject>> data) {
        GunRegistry.Builder b = new GunRegistry.Builder();
        Map<String, List<Cost>> c = new HashMap<>();
        data.getOrDefault("archetype", Map.of()).forEach((id, o) -> {
            b.archetype(id, o);
            c.put("archetype:" + id, parseCost(o));
        });
        data.getOrDefault("part", Map.of()).forEach((id, o) -> {
            b.part(id, o);
            c.put("part:" + id, parseCost(o));
        });
        data.getOrDefault("material", Map.of()).forEach(b::material);
        data.getOrDefault("magazine", Map.of()).forEach((id, o) -> c.put("magazine:" + id, parseCost(o)));
        GunRegistry reg = b.build();
        for (String p : reg.problems()) Interstellar.LOGGER.warn("[guns] {}", p);
        registry = reg;
        costs = c;
        Interstellar.LOGGER.info("Loaded {} gun archetypes, {} parts, {} materials", reg.archetypes().size(), reg.parts().size(), reg.materials().size());
    }

    private static List<Cost> parseCost(JsonObject o) {
        List<Cost> out = new ArrayList<>();
        if (!o.has("cost")) return out;
        for (JsonElement e : o.getAsJsonArray("cost")) {
            JsonObject c = e.getAsJsonObject();
            Identifier id = Identifier.tryParse(c.get("item").getAsString());
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null) out.add(new Cost(item, c.has("count") ? c.get("count").getAsInt() : 1));
        }
        return out;
    }

    public static void sendTo(ServerPlayer player) {
        Map<String, String> raw = rawJson;
        PacketDistributor.sendToPlayer(player, new ModPayloads.GunData(raw.getOrDefault("archetype", "{}"), raw.getOrDefault("part", "{}"),
                raw.getOrDefault("material", "{}"), raw.getOrDefault("magazine", "{}")));
    }

    /** Client side: rebuild the registry from the server's data. */
    public static void receive(ModPayloads.GunData payload) {
        Map<String, Map<String, JsonObject>> data = new HashMap<>();
        String[] parts = { payload.archetypes(), payload.parts(), payload.materials(), payload.magazines() };
        for (int i = 0; i < KINDS.length; i++) {
            Map<String, JsonObject> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : JsonParser.parseString(parts[i]).getAsJsonObject().entrySet()) m.put(e.getKey(), e.getValue().getAsJsonObject());
            data.put(KINDS[i], m);
        }
        load(data);
    }
}
