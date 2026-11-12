package com.nexusstaff.pro.listener;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

@SuppressWarnings("deprecation")
public final class StaffListener implements Listener {
    private final NexusStaffPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> combatExpiry = new java.util.concurrent.ConcurrentHashMap<>();
    private final int hudTask;

    public StaffListener(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHud, 20L, 20L).getTaskId();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.altAccounts().remember(event.getPlayer());
        plugin.freeze().handleJoin(event.getPlayer());
        plugin.vanish().handleJoin(event.getPlayer());
        if (plugin.vanish().isVanished(event.getPlayer())) {
            event.joinMessage(null);
            return;
        }
        if (!event.getPlayer().hasPermission("nexusstaff.staff")) {
            for (Player online : Bukkit.getOnlinePlayers()) if (plugin.vanish().isVanished(online)) event.getPlayer().hidePlayer(plugin, online);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.freeze().handleQuit(player);
        if (plugin.staffMode().isEnabled(player)) plugin.staffMode().restore(player);
        if (plugin.freeze().isFrozen(player)) {
            plugin.discord().alert("Frozen player disconnected", player.getName() + " disconnected while frozen", 15158332);
            for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission("nexusstaff.alerts")) {
                staff.sendMessage(plugin.messages().raw("<red>Frozen player <player> disconnected.</red>", Map.of("player", player.getName())));
            }
        }
        if (plugin.vanish().isVanished(player)) event.quitMessage(null);
        plugin.vanish().handleQuit(player);
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.freeze().isFrozen(player)) {
            event.setCancelled(true);
            return;
        }
        if (!plugin.staffMode().isEnabled(player)) return;
        var item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(plugin.actionKey(), PersistentDataType.STRING);
        if (action == null) return;
        event.setCancelled(true);
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        switch (action) {
            case "dashboard" -> { if (rightClick) new com.nexusstaff.pro.gui.MenuFactory(plugin).openDashboard(player); }
            case "vanish" -> {
                if (rightClick) {
                    boolean hidden = plugin.vanish().toggle(player);
                    player.sendMessage(plugin.messages().raw(hidden ? "<green>Vanish enabled.</green>" : "<yellow>Vanish disabled.</yellow>", Map.of()));
                } else if (event.getAction().isLeftClick()) {
                    double speed = plugin.staffMode().cycleSpeed(player);
                    player.sendMessage(plugin.messages().raw("<aqua>Flight speed: <speed>x</aqua>", Map.of("speed", Double.toString(speed))));
                }
            }
            case "spectate" -> { if (rightClick) Bukkit.dispatchCommand(player, "sm spectate"); }
            default -> { }
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player staff = event.getPlayer();
        if (!plugin.staffMode().isEnabled(staff) || !(event.getRightClicked() instanceof Player target)) return;
        var item = staff.getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(plugin.actionKey(), PersistentDataType.STRING);
        if (action == null) return;
        event.setCancelled(true);
        if (action.equals("inspector")) new com.nexusstaff.pro.gui.MenuFactory(plugin).openInspect(staff, target);
        if (action.equals("freeze")) {
            boolean frozen = plugin.freeze().toggle(target);
            staff.sendMessage(plugin.messages().raw(frozen ? "<red>Player frozen.</red>" : "<green>Player unfrozen.</green>", Map.of()));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.freeze().isFrozen(event.getPlayer()) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) event.setTo(event.getFrom());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.freeze().isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.punishments().isMuted(event.getPlayer()) && !event.getPlayer().hasPermission("nexusstaff.admin")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().raw("<red>You are muted.</red>", Map.of()));
            return;
        }
        if (plugin.chatControl().isMuted() && !event.getPlayer().hasPermission("nexusstaff.staff")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().raw("<red>Chat is currently muted.</red>", Map.of()));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.staffMode().isEnabled(event.getPlayer()) || plugin.freeze().isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && (plugin.staffMode().isEnabled(player) || plugin.vanish().isVanished(player))) event.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && plugin.vanish().isVanished(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
        if (attacker == null) return;
        long expiry = System.currentTimeMillis() + 15_000L;
        combatExpiry.put(victim.getUniqueId(), expiry);
        combatExpiry.put(attacker.getUniqueId(), expiry);
    }

    public boolean isInCombat(UUID playerId) {
        Long expiry = combatExpiry.get(playerId);
        if (expiry == null) return false;
        if (expiry > System.currentTimeMillis()) return true;
        combatExpiry.remove(playerId, expiry);
        return false;
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && plugin.vanish().isVanished(player)
                && !(event.getInventory().getHolder(false) instanceof com.nexusstaff.pro.gui.MenuHolder)) event.setCancelled(true);
    }

    private void updateHud() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.staffMode().isEnabled(player)) {
                BossBar old = bars.remove(player.getUniqueId());
                if (old != null) player.hideBossBar(old);
                continue;
            }
            String actionbar = plugin.getConfig().getString("staff-mode.actionbar", "<red>STAFF MODE</red>");
            player.sendActionBar(plugin.messages().raw(actionbar, Map.of(
                    "state_vanish", plugin.vanish().isVanished(player) ? "YES" : "NO",
                    "flight_speed", Double.toString(plugin.staffMode().speed(player)),
                    "spectating", player.getSpectatorTarget() == null ? "-" : player.getSpectatorTarget().getName())));
            double tps = Math.min(20.0, Bukkit.getTPS()[0]);
            int duty = (int) Bukkit.getOnlinePlayers().stream().filter(online -> plugin.staffMode().isEnabled(online)).count();
                String title = "<aqua>TPS " + String.format("%.1f", tps) + "</aqua> <dark_gray>|</dark_gray> <yellow>Reports "
                    + plugin.reports().openReports().size() + "</yellow> <dark_gray>|</dark_gray> <red>AC "
                    + plugin.antiCheat().trackedThreatCount() + "</red> <dark_gray>|</dark_gray> <green>Staff " + duty + "</green>";
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> BossBar.bossBar(
                    plugin.messages().raw(title, Map.of()), (float) (tps / 20.0), BossBar.Color.GREEN, BossBar.Overlay.PROGRESS));
            bar.name(plugin.messages().raw(title, Map.of()));
            bar.progress((float) (tps / 20.0));
            player.showBossBar(bar);
        }
    }

    public void stop() {
        Bukkit.getScheduler().cancelTask(hudTask);
        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.get(player.getUniqueId());
            if (bar != null) player.hideBossBar(bar);
        }
    }
}