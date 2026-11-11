package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class StaffModeManager {
    private final NexusStaffPlugin plugin;
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, Double> speeds = new HashMap<>();

    public StaffModeManager(NexusStaffPlugin plugin) { this.plugin = plugin; }

    public boolean isEnabled(Player player) { return snapshots.containsKey(player.getUniqueId()); }

    public boolean toggle(Player player) {
        if (isEnabled(player)) {
            restore(player);
            return false;
        }
        snapshots.put(player.getUniqueId(), Snapshot.capture(player));
        speeds.put(player.getUniqueId(), 1.0);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.05f);
        player.setWalkSpeed((float) plugin.getConfig().getDouble("staff-mode.flight-speed", 0.2));
        player.setCollidable(false);
        give(player, "inspector", org.bukkit.Material.SPYGLASS, "<aqua>Inspector Lens");
        give(player, "freeze", org.bukkit.Material.PACKED_ICE, "<aqua>Quantum Freeze");
        give(player, "vanish", org.bukkit.Material.COMPASS, "<aqua>Phasing Vanish");
        give(player, "spectate", org.bukkit.Material.ENDER_EYE, "<aqua>AC Spectate");
        give(player, "dashboard", org.bukkit.Material.NETHER_STAR, "<light_purple>Control Center");
        return true;
    }

    private void give(Player player, String action, org.bukkit.Material material, String name) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(plugin.messages().raw(name, Map.of()));
        meta.getPersistentDataContainer().set(plugin.actionKey(), org.bukkit.persistence.PersistentDataType.STRING, action);
        meta.setCustomModelData(12000 + action.hashCode() & 0x7fff);
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
    }

    public double cycleSpeed(Player player) {
        double current = speeds.getOrDefault(player.getUniqueId(), 1.0);
        double next = current >= 8.0 ? 1.0 : current * 2.0;
        speeds.put(player.getUniqueId(), next);
        player.setFlySpeed((float) (0.05 * next));
        return next;
    }

    public double speed(Player player) { return speeds.getOrDefault(player.getUniqueId(), 1.0); }

    public void restore(Player player) {
        Snapshot snapshot = snapshots.remove(player.getUniqueId());
        speeds.remove(player.getUniqueId());
        if (snapshot == null) return;
        player.setCollidable(snapshot.collidable && !plugin.vanish().isVanished(player));
        player.setFlying(false);
        player.setAllowFlight(snapshot.allowFlight);
        player.setGameMode(snapshot.gameMode);
        player.getInventory().setContents(snapshot.contents);
        player.getInventory().setArmorContents(snapshot.armor);
        player.getInventory().setItemInOffHand(snapshot.offhand);
        player.setLevel(snapshot.level);
        player.setExp(snapshot.exp);
        player.setFoodLevel(snapshot.food);
        player.setSaturation(snapshot.saturation);
        player.setWalkSpeed(snapshot.walkSpeed);
        player.setFlySpeed(snapshot.flySpeed);
        if (snapshot.location != null && snapshot.location.getWorld() != null) player.teleport(snapshot.location);
        if (snapshot.maxHealth > 0) {
            var attribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) attribute.setBaseValue(snapshot.maxHealth);
            player.setHealth(Math.min(snapshot.health, snapshot.maxHealth));
        }
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        snapshot.effects.forEach(player::addPotionEffect);
    }

    public void restoreAll() {
        for (UUID id : Map.copyOf(snapshots).keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) restore(player);
        }
    }

    private record Snapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, int level,
                            float exp, int food, float saturation, float walkSpeed, float flySpeed,
                            boolean allowFlight, boolean collidable, GameMode gameMode, Location location,
                            double health, double maxHealth, java.util.Collection<PotionEffect> effects) {
        private static Snapshot capture(Player player) {
            var attribute = player.getAttribute(Attribute.MAX_HEALTH);
            return new Snapshot(cloneItems(player.getInventory().getContents()),
                    cloneItems(player.getInventory().getArmorContents()), cloneItem(player.getInventory().getItemInOffHand()),
                    player.getLevel(), player.getExp(), player.getFoodLevel(), player.getSaturation(),
                    player.getWalkSpeed(), player.getFlySpeed(), player.getAllowFlight(), player.isCollidable(),
                    player.getGameMode(), player.getLocation().clone(), player.getHealth(),
                    attribute == null ? 20 : attribute.getBaseValue(), java.util.List.copyOf(player.getActivePotionEffects()));
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] copy = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) copy[i] = cloneItem(items[i]);
            return copy;
        }

        private static ItemStack cloneItem(ItemStack item) { return item == null ? null : item.clone(); }
    }
}