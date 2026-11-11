package com.nexusstaff.pro.gui.menus;

import com.nexusstaff.pro.NexusStaffPlugin;
import com.nexusstaff.pro.gui.MenuHolder;
import com.nexusstaff.pro.gui.MenuType;
import java.io.File;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ReportsMenu {
    private final NexusStaffPlugin plugin;
    private final YamlConfiguration config;

    public ReportsMenu(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "guis.yml"));
    }

    public void open(Player viewer) {
        int size = Math.max(9, Math.min(54, config.getInt("reports.size", 54)));
        size -= size % 9;
        var title = plugin.messages().raw(config.getString("reports.title", "<yellow>Active reports</yellow>"), Map.of());
        MenuHolder holder = new MenuHolder(MenuType.REPORTS, null, size, title);
        int slot = 0;
        for (var report : plugin.reports().openReports()) {
            if (slot >= size) break;
            Player target = Bukkit.getPlayer(report.targetId());
            ItemStack item = new ItemStack(target == null ? Material.PAPER : Material.PLAYER_HEAD);
            var meta = item.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull && target != null) skull.setOwningPlayer(target);
            String targetName = target == null ? report.targetId().toString() : target.getName();
            meta.displayName(plugin.messages().raw("<yellow>" + targetName + "</yellow>", Map.of()));
            meta.lore(java.util.List.of(
                    plugin.messages().raw("<gray>Reason: <white>" + report.reason(), Map.of()),
                    plugin.messages().raw("<gray>Reported: <white>" + report.createdAt(), Map.of()),
                    plugin.messages().raw("<aqua>Left-click: teleport | Right-click: resolve</aqua>", Map.of())));
            meta.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "report:" + report.id());
            item.setItemMeta(meta);
            holder.getInventory().setItem(slot++, item);
        }
        if (slot == 0) {
            ItemStack empty = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            var meta = empty.getItemMeta();
            meta.displayName(plugin.messages().raw("<green>No open reports</green>", Map.of()));
            empty.setItemMeta(meta);
            holder.getInventory().setItem(size / 2, empty);
        }
        viewer.openInventory(holder.getInventory());
    }
}