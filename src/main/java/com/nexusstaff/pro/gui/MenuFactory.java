package com.nexusstaff.pro.gui;

import com.nexusstaff.pro.NexusStaffPlugin;
import com.nexusstaff.pro.gui.holders.PlayerDirectoryHolder;
import java.io.File;
import java.util.Comparator;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

public final class MenuFactory {
    private final NexusStaffPlugin plugin;
    private final YamlConfiguration guiConfig;

    public MenuFactory(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        plugin.saveResource("guis.yml", false);
        guiConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "guis.yml"));
    }

    public void openDashboard(Player viewer) {
        MenuHolder holder = create(MenuType.DASHBOARD, null, "dashboard", guiConfig.getInt("dashboard.size", 54), Map.of());
        configured(holder, "dashboard.players", "players", Material.PLAYER_HEAD, "<aqua>Online players", "<gray>Browse online players");
        configured(holder, "dashboard.threats", "threats", Material.SPECTRAL_ARROW, "<red>Threat monitor", "<gray>Review anti-cheat threat scores");
        configured(holder, "dashboard.staffmode", "staffmode", Material.CLOCK, "<green>Toggle staff mode", "<gray>Enable or restore your loadout");
        set(holder, 30, "vanish", Material.GLASS, "<yellow>Toggle vanish", "<gray>Hide from non-staff players");
        set(holder, 32, "freeze-all", Material.PACKED_ICE, "<aqua>Freeze all", "<gray>Toggle freeze on online non-staff");
        set(holder, 40, "health", Material.COMPASS, "<white>Server health", "<gray>TPS: " + String.format("%.2f", Bukkit.getTPS()[0]) + " | Online: " + Bukkit.getOnlinePlayers().size());
        viewer.openInventory(holder.getInventory());
    }

    public void openPlayers(Player viewer) {
        openPlayers(viewer, 0);
    }

    public void openPlayers(Player viewer, int requestedPage) {
        var online = Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName)).toList();
        int pageCount = Math.max(1, (online.size() + 44) / 45);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        var title = plugin.messages().raw(guiConfig.getString("dashboard.title", "<aqua>NexusStaff</aqua>"), Map.of());
        PlayerDirectoryHolder holder = new PlayerDirectoryHolder(page, title);
        int slot = 0;
        for (Player target : online.stream().skip((long) page * 45).limit(45).toList()) {
                ItemStack head = playerHead(target, "<yellow>" + target.getName(),
                    "<gray>Health: <white>" + String.format(java.util.Locale.US, "%.1f", target.getHealth()) + "/" + target.getMaxHealth(),
                    "<gray>Hunger: <white>" + target.getFoodLevel() + "/20",
                    "<gray>Ping: <white>" + target.getPing() + "ms <dark_gray>|</dark_gray> Brand: <white>" + target.getClientBrandName(),
                    plugin.vanish().isVanished(target) ? "<dark_gray>[Vanished]</dark_gray>" : "<gray>[Visible]</gray>",
                    plugin.freeze().isFrozen(target) ? "<aqua>[Frozen]</aqua>" : "",
                    plugin.punishments().isMuted(target) ? "<red>[Muted]</red>" : "",
                    plugin.isInCombat(target.getUniqueId()) ? "<red>[In Combat]</red>" : "",
                    "<gray>Recent AC flags: <white>" + plugin.antiCheat().flagCount(target.getUniqueId()),
                    "<dark_gray>Left: teleport | Right: inspect | Shift: freeze");
                tag(head, "directory:" + target.getUniqueId());
            holder.getInventory().setItem(slot++, head);
        }
        if (page > 0) set(holder, 45, "directory-page:" + (page - 1), Material.ARROW, "<yellow>Previous page</yellow>", "<gray>Page " + page + " of " + pageCount);
        set(holder, 49, "directory-page:current", Material.PAPER, "<white>Page " + (page + 1) + "/" + pageCount, "<gray>Online: " + online.size());
        if (page + 1 < pageCount) set(holder, 53, "directory-page:" + (page + 1), Material.ARROW, "<yellow>Next page</yellow>", "<gray>Page " + (page + 2) + " of " + pageCount);
        viewer.openInventory(holder.getInventory());
    }

    public void openThreats(Player viewer) {
        MenuHolder holder = create(MenuType.THREATS, null, "threats", guiConfig.getInt("threats.size", 54), Map.of());
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparingDouble((Player player) -> plugin.antiCheat().threat(player.getUniqueId())).reversed()).toList()) {
            if (slot >= 45) break;
            double vl = plugin.antiCheat().threat(target.getUniqueId());
            ItemStack head = playerHead(target, "<red>" + target.getName(), "<gray>Combined threat: <white>" + vl,
                    "<gray>Ping: <white>" + target.getPing() + "ms", "<dark_gray>Grim/Vulcan adapters can publish flags here");
            tag(head, "inspect:" + target.getUniqueId());
            holder.getInventory().setItem(slot++, head);
        }
        viewer.openInventory(holder.getInventory());
    }

    public void openInspect(Player viewer, Player target) {
        MenuHolder holder = create(MenuType.INSPECT, target.getUniqueId(), "inspect", guiConfig.getInt("inspect.size", 54), Map.of("player", target.getName()));
        ItemStack[] contents = target.getInventory().getStorageContents();
        for (int i = 0; i < Math.min(18, contents.length); i++) holder.getInventory().setItem(i, clone(contents[i]));
        ItemStack[] ender = target.getEnderChest().getContents();
        for (int i = 0; i < Math.min(18, ender.length); i++) holder.getInventory().setItem(i + 18, clone(ender[i]));
        set(holder, 45, "teleport", Material.ENDER_PEARL, "<aqua>Teleport", "<gray>Teleport to this player");
        set(holder, 47, "freeze", Material.PACKED_ICE, "<aqua>Toggle freeze", "<gray>Freeze or unfreeze this player");
        set(holder, 49, "punish", Material.ANVIL, "<red>Punishments", "<gray>Open punishment actions");
        set(holder, 51, "threat", Material.SPECTRAL_ARROW, "<yellow>Threat score", "<gray>VL: " + plugin.antiCheat().threat(target.getUniqueId()));
        viewer.openInventory(holder.getInventory());
    }

    public void openPunish(Player viewer, Player target) {
        MenuHolder holder = create(MenuType.PUNISH, target.getUniqueId(), "punish", guiConfig.getInt("punish.size", 54), Map.of("player", target.getName()));
        set(holder, 20, "punish:warn", Material.PAPER, "<yellow>Warn", "<gray>Issue a warning");
        set(holder, 22, "punish:mute", Material.MUSIC_DISC_13, "<gold>Mute 1 hour", "<gray>Mute chat for one hour");
        set(holder, 24, "punish:kick", Material.IRON_DOOR, "<red>Kick", "<gray>Remove from the server");
        set(holder, 31, "punish:ban", Material.BARRIER, "<dark_red>Ban", "<gray>Permanent ban");
        viewer.openInventory(holder.getInventory());
    }

    private MenuHolder create(MenuType type, java.util.UUID target, String guiPath, int size, Map<String, String> placeholders) {
        String title = guiConfig.getString(guiPath + ".title", plugin.getConfig().getString("gui." + guiPath + "-title", "<aqua>NexusStaff"));
        return new MenuHolder(type, target, size, plugin.messages().raw(title, placeholders));
    }

    private void configured(MenuHolder holder, String path, String action, Material fallback, String name, String... lore) {
        Material material = Material.matchMaterial(guiConfig.getString(path + ".material", fallback.name()));
        if (material == null) material = fallback;
        java.util.List<String> configuredLore = guiConfig.getStringList(path + ".lore");
        set(holder, guiConfig.getInt(path + ".slot", 0), action, material,
                guiConfig.getString(path + ".name", name), configuredLore.isEmpty() ? lore : configuredLore.toArray(String[]::new));
    }

    private void set(MenuHolder holder, int slot, String action, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(plugin.messages().raw(name, Map.of()));
        meta.lore(java.util.Arrays.stream(lore).map(line -> plugin.messages().raw(line, Map.of())).toList());
        item.setItemMeta(meta);
        tag(item, action);
        holder.getInventory().setItem(slot, item);
    }

    private ItemStack playerHead(OfflinePlayer target, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(plugin.messages().raw(name, Map.of()));
        meta.lore(java.util.Arrays.stream(lore).map(line -> plugin.messages().raw(line, Map.of())).toList());
        item.setItemMeta(meta);
        return item;
    }

    private void tag(ItemStack item, String action) {
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    private ItemStack clone(ItemStack item) { return item == null ? null : item.clone(); }
}