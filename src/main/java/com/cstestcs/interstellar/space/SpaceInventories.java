package com.cstestcs.interstellar.space;

import com.cstestcs.interstellar.Interstellar;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Inventories of players who died in space (where items would be lost in the void). Restored only through the
 * operator command {@code /interstellar spaceinventory restore <player>}.
 */
public final class SpaceInventories extends SavedData {
    private static final Codec<Map<UUID, List<ItemStack>>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.OPTIONAL_CODEC.listOf());
    private static final Codec<SpaceInventories> CODEC = MAP_CODEC.xmap(SpaceInventories::new, s -> s.saved);

    public static final SavedDataType<SpaceInventories> TYPE = new SavedDataType<>(Interstellar.id("space_inventories"),
            level -> new SpaceInventories(Map.of()), level -> CODEC);

    private final Map<UUID, List<ItemStack>> saved;

    private SpaceInventories(Map<UUID, List<ItemStack>> saved) {
        this.saved = new HashMap<>(saved);
    }

    public static SpaceInventories get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Moves the player's whole inventory into storage (appending to anything saved earlier). */
    public void store(ServerPlayer player) {
        Inventory inv = player.getInventory();
        List<ItemStack> list = new ArrayList<>(saved.getOrDefault(player.getUUID(), List.of()));
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) list.add(s.copy());
        }
        inv.clearContent();
        if (!list.isEmpty()) {
            saved.put(player.getUUID(), list);
            setDirty();
        }
    }

    /** @return number of stacks given back */
    public int restore(ServerPlayer player) {
        List<ItemStack> list = saved.remove(player.getUUID());
        if (list == null) return 0;
        setDirty();
        for (ItemStack s : list) {
            if (!player.getInventory().add(s.copy())) player.spawnAtLocation(player.level(), s.copy());
        }
        return list.size();
    }

    public int count(UUID player) {
        return saved.getOrDefault(player, List.of()).size();
    }
}
