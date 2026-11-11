package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public final class FreezeManager {
    private final NexusStaffPlugin plugin;
    private final Set<UUID> frozen = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozenByEmergency = new HashSet<>();
    private boolean emergencyFreeze;

    public FreezeManager(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::renderCages, 20L, 20L);
    }

    public boolean toggle(Player target) {
        if (frozen.remove(target.getUniqueId())) {
            target.resetTitle();
            return false;
        }
        frozen.add(target.getUniqueId());
        target.showTitle(net.kyori.adventure.title.Title.title(
                plugin.messages().raw(plugin.messages().text("freeze-title", "<red>FROZEN</red>"), java.util.Map.of()),
                plugin.messages().raw(plugin.messages().text("freeze-subtitle", "<gray>Please wait for staff.</gray>"), java.util.Map.of())));
        return true;
    }

    public boolean isFrozen(Player player) { return frozen.contains(player.getUniqueId()); }
    public boolean isFrozen(UUID playerId) { return frozen.contains(playerId); }

    public boolean toggleAllNonStaff() {
        emergencyFreeze = !emergencyFreeze;
        if (emergencyFreeze) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!player.hasPermission("nexusstaff.staff") && !isFrozen(player)) {
                    frozenByEmergency.add(player.getUniqueId());
                    toggle(player);
                }
            }
        } else {
            for (UUID id : Set.copyOf(frozenByEmergency)) {
                Player player = plugin.getServer().getPlayer(id);
                if (player != null && isFrozen(player)) toggle(player);
            }
            frozenByEmergency.clear();
        }
        return emergencyFreeze;
    }

    public void handleJoin(Player player) {
        if (emergencyFreeze && !player.hasPermission("nexusstaff.staff") && !isFrozen(player)) {
            frozenByEmergency.add(player.getUniqueId());
            toggle(player);
        }
    }

    public void handleQuit(Player player) { frozenByEmergency.remove(player.getUniqueId()); }
    public boolean isEmergencyFreeze() { return emergencyFreeze; }

    private void renderCages() {
        for (UUID id : frozen) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) continue;
            var location = player.getLocation();
            for (int y = 0; y < 3; y++) {
                for (int corner = 0; corner < 4; corner++) {
                    double x = (corner % 2 == 0 ? -0.6 : 0.6);
                    double z = (corner < 2 ? -0.6 : 0.6);
                    player.getWorld().spawnParticle(Particle.END_ROD, location.getX() + x, location.getY() + y, location.getZ() + z, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}