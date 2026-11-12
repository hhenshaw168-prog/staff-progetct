package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

public final class LuckPermsManager {
    private final Bridge bridge;

    public LuckPermsManager(NexusStaffPlugin plugin) {
        Bridge found = null;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                found = new LuckPermsBridge();
            } catch (IllegalStateException | LinkageError exception) {
                plugin.getLogger().warning("LuckPerms is enabled but its API provider is unavailable.");
            }
        }
        bridge = found;
    }

    public boolean isAvailable() { return bridge != null; }

    public RankData rank(UUID uniqueId) {
        return bridge == null ? RankData.EMPTY : bridge.rank(uniqueId);
    }

    public CompletableFuture<RankData> rankAsync(UUID uniqueId) {
        return bridge == null ? CompletableFuture.completedFuture(RankData.EMPTY) : bridge.rankAsync(uniqueId);
    }

    public CompletableFuture<Void> setState(UUID uniqueId, String key, boolean enabled) {
        return bridge == null ? CompletableFuture.completedFuture(null) : bridge.setState(uniqueId, key, enabled);
    }

    interface Bridge {
        RankData rank(UUID uniqueId);
        CompletableFuture<RankData> rankAsync(UUID uniqueId);
        CompletableFuture<Void> setState(UUID uniqueId, String key, boolean enabled);
    }

    public record RankData(String group, String prefix, String weight) {
        public static final RankData EMPTY = new RankData("default", "", "-");
    }
}