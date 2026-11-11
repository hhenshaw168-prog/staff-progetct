package com.nexusstaff.pro.gui.menus;

import com.nexusstaff.pro.NexusStaffPlugin;
import com.nexusstaff.pro.gui.holders.StaffDashboardHolder;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class StaffDashboardMenu {
    private final NexusStaffPlugin plugin;
    private final YamlConfiguration config;

    public StaffDashboardMenu(NexusStaffPlugin plugin, YamlConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void open(Player player) {
        int size = Math.max(9, Math.min(54, config.getInt("dashboard.size", 54)));
        size -= size % 9;
        var title = plugin.messages().raw(config.getString("dashboard.title", "<aqua>NexusStaff Dashboard</aqua>"), Map.of());
        StaffDashboardHolder holder = new StaffDashboardHolder(size, title);
        for (String id : new String[]{"metrics", "staff-status", "vanish", "staffmode", "mutechat", "freezeall", "reports", "threats", "players"}) {
            holder.getInventory().setItem(slot(id), item(id, Map.of()));
        }
        player.openInventory(holder.getInventory());
    }

    public void refresh(Inventory inventory, DashboardMetrics.Snapshot snapshot) {
        if (!(inventory.getHolder(false) instanceof StaffDashboardHolder)) return;
        inventory.setItem(slot("metrics"), item("metrics", Map.of(
                "tps", format(snapshot.tps()), "mspt", format(snapshot.mspt()),
                "ram_percent", Integer.toString(snapshot.ramPercent()), "ram_used", snapshot.ramUsedMb(),
                "ram_max", snapshot.ramMaxMb(), "chunks", Integer.toString(snapshot.loadedChunks()),
                "reports", Integer.toString(snapshot.openReports()))));
        inventory.setItem(slot("staff-status"), item("staff-status", Map.of(
                "staff_online", Integer.toString(snapshot.onlineStaff()),
                "staffmode", Integer.toString(snapshot.staffMode()),
                "vanished", Integer.toString(snapshot.vanished()))));
    }

    private int slot(String id) { return config.getInt("dashboard.items." + id + ".slot", 0); }

    private ItemStack item(String id, Map<String, String> placeholders) {
        String path = "dashboard.items." + id;
        Material material = Material.matchMaterial(config.getString(path + ".material", "PAPER"));
        if (material == null) material = Material.PAPER;
        ItemStack stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.displayName(plugin.messages().raw(config.getString(path + ".name", "<white>" + id + "</white>"), placeholders));
        meta.lore(config.getStringList(path + ".lore").stream().map(line -> plugin.messages().raw(line, placeholders)).toList());
        int modelData = config.getInt(path + ".custom-model-data", 0);
        if (modelData > 0) meta.setCustomModelData(modelData);
        String action = config.getString(path + ".action");
        if (action != null && !action.isBlank()) meta.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    private String format(double value) { return String.format(java.util.Locale.US, "%.1f", value); }
}