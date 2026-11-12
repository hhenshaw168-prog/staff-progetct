package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class VanishManager {
    private final NexusStaffPlugin plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, Boolean> collisionState = new HashMap<>();

    public VanishManager(NexusStaffPlugin plugin) { this.plugin = plugin; }

    public boolean toggle(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            plugin.luckPerms().setState(player.getUniqueId(), "vanished", false);
            for (Player online : plugin.getServer().getOnlinePlayers()) online.showPlayer(plugin, player);
            player.setCollidable(collisionState.remove(player.getUniqueId()) && !plugin.staffMode().isEnabled(player));
            return false;
        }
        vanished.add(player.getUniqueId());
        plugin.luckPerms().setState(player.getUniqueId(), "vanished", true);
        collisionState.put(player.getUniqueId(), player.isCollidable());
        player.setCollidable(false);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.hasPermission("nexusstaff.staff")) online.hidePlayer(plugin, player);
        }
        return true;
    }

    public boolean isVanished(Player player) { return vanished.contains(player.getUniqueId()); }
    public boolean isVanished(UUID playerId) { return vanished.contains(playerId); }

    public void handleJoin(Player joining) {
        for (UUID id : vanished) {
            Player hidden = plugin.getServer().getPlayer(id);
            if (hidden != null && !joining.hasPermission("nexusstaff.staff")) joining.hidePlayer(plugin, hidden);
        }
    }

    public void handleQuit(Player player) {
        if (vanished.contains(player.getUniqueId())) plugin.luckPerms().setState(player.getUniqueId(), "vanished", false);
        vanished.remove(player.getUniqueId());
        collisionState.remove(player.getUniqueId());
    }
}