package com.nexusstaff.pro.gui.listeners;

import com.nexusstaff.pro.NexusStaffPlugin;
import com.nexusstaff.pro.gui.MenuFactory;
import com.nexusstaff.pro.gui.holders.StaffDashboardHolder;
import com.nexusstaff.pro.gui.menus.StaffDashboardMenu;
import com.nexusstaff.pro.gui.menus.ReportsMenu;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;

public final class StaffDashboardListener implements Listener {
    private final NexusStaffPlugin plugin;
    private final StaffDashboardMenu dashboard;
    private final MenuFactory menus;
    private final ReportsMenu reports;

    public StaffDashboardListener(NexusStaffPlugin plugin, StaffDashboardMenu dashboard) {
        this.plugin = plugin;
        this.dashboard = dashboard;
        menus = new MenuFactory(plugin);
        reports = new ReportsMenu(plugin);
    }

    @EventHandler
    public void onDashboardClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof StaffDashboardHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        var clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(plugin.actionKey(), PersistentDataType.STRING);
        if (action == null) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.25f);
        switch (action) {
            case "toggle:vanish" -> {
                boolean vanished = plugin.vanish().toggle(player);
                player.sendMessage(plugin.messages().raw(vanished ? "<green>Vanish enabled.</green>" : "<yellow>Vanish disabled.</yellow>", Map.of()));
            }
            case "toggle:staffmode" -> {
                boolean enabled = plugin.staffMode().toggle(player);
                plugin.messages().send(player, enabled ? "staffmode-enabled" : "staffmode-disabled");
                player.closeInventory();
            }
            case "toggle:mutechat" -> {
                boolean muted = plugin.chatControl().toggle();
                Bukkit.broadcast(plugin.messages().raw(muted ? "<red>Global chat mute enabled.</red>" : "<green>Global chat mute disabled.</green>", Map.of()));
            }
            case "toggle:freezeall" -> {
                if (!player.hasPermission("nexusstaff.freezeall")) {
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                boolean frozen = plugin.freeze().toggleAllNonStaff();
                player.sendMessage(plugin.messages().raw(frozen ? "<red>Emergency freeze enabled.</red>" : "<green>Emergency freeze disabled.</green>", Map.of()));
            }
            case "open:players" -> menus.openPlayers(player);
            case "open:threats" -> menus.openThreats(player);
            case "open:reports" -> {
                reports.open(player);
            }
            default -> { }
        }
    }
}