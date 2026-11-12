package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.node.types.MetaNode;
import org.bukkit.Bukkit;

public final class LuckPermsManager {
    private final LuckPerms api;

    public LuckPermsManager(NexusStaffPlugin plugin) {
        LuckPerms found = null;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                found = LuckPermsProvider.get();
            } catch (IllegalStateException exception) {
                plugin.getLogger().warning("LuckPerms is enabled but its API provider is unavailable.");
            }
        }
        api = found;
    }

    public boolean isAvailable() { return api != null; }

    public RankData rank(UUID uniqueId) {
        if (api == null) return RankData.EMPTY;
        var user = api.getUserManager().getUser(uniqueId);
        if (user == null) return RankData.EMPTY;
        return rank(user);
    }

    public CompletableFuture<RankData> rankAsync(UUID uniqueId) {
        if (api == null) return CompletableFuture.completedFuture(RankData.EMPTY);
        return api.getUserManager().loadUser(uniqueId).thenApply(this::rank);
    }

    private RankData rank(net.luckperms.api.model.user.User user) {
        var meta = user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions());
        String groupName = user.getPrimaryGroup();
        var group = api.getGroupManager().getGroup(groupName);
        String weight = group == null || group.getWeight().isEmpty() ? "-" : Integer.toString(group.getWeight().getAsInt());
        return new RankData(groupName, meta.getPrefix() == null ? "" : meta.getPrefix(), weight);
    }

    public CompletableFuture<Void> setState(UUID uniqueId, String key, boolean enabled) {
        if (api == null) return CompletableFuture.completedFuture(null);
        MetaNode node = MetaNode.builder(key, "true").build();
        return api.getUserManager().modifyUser(uniqueId, user -> {
            user.data().remove(node);
            if (enabled) user.data().add(node);
        });
    }

    public record RankData(String group, String prefix, String weight) {
        public static final RankData EMPTY = new RankData("default", "", "-");
    }
}