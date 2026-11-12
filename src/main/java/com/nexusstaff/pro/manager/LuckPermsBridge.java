package com.nexusstaff.pro.manager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.query.QueryOptions;

final class LuckPermsBridge implements LuckPermsManager.Bridge {
    private final LuckPerms api;

    LuckPermsBridge() {
        api = LuckPermsProvider.get();
    }

    @Override
    public LuckPermsManager.RankData rank(UUID uniqueId) {
        var user = api.getUserManager().getUser(uniqueId);
        return user == null ? LuckPermsManager.RankData.EMPTY : rank(user);
    }

    @Override
    public CompletableFuture<LuckPermsManager.RankData> rankAsync(UUID uniqueId) {
        return api.getUserManager().loadUser(uniqueId).thenApply(this::rank);
    }

    @Override
    public CompletableFuture<Void> setState(UUID uniqueId, String key, boolean enabled) {
        MetaNode node = MetaNode.builder(key, "true").build();
        return api.getUserManager().modifyUser(uniqueId, user -> {
            user.data().remove(node);
            if (enabled) user.data().add(node);
        });
    }

    private LuckPermsManager.RankData rank(net.luckperms.api.model.user.User user) {
        var meta = user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions());
        String groupName = user.getPrimaryGroup();
        var group = api.getGroupManager().getGroup(groupName);
        String weight = group == null || group.getWeight().isEmpty() ? "-" : Integer.toString(group.getWeight().getAsInt());
        return new LuckPermsManager.RankData(groupName, meta.getPrefix() == null ? "" : meta.getPrefix(), weight);
    }
}