package com.nexusstaff.pro;

import com.nexusstaff.pro.command.StaffCommand;
import com.nexusstaff.pro.gui.MenuListener;
import com.nexusstaff.pro.gui.menus.DashboardMetrics;
import com.nexusstaff.pro.gui.menus.StaffDashboardMenu;
import com.nexusstaff.pro.gui.menus.StaffDashboardRefreshTask;
import com.nexusstaff.pro.manager.ReportManager;
import com.nexusstaff.pro.gui.listeners.StaffDashboardListener;
import com.nexusstaff.pro.listener.StaffListener;
import com.nexusstaff.pro.manager.AntiCheatHooks;
import com.nexusstaff.pro.manager.AntiCheatEventBridge;
import com.nexusstaff.pro.manager.ChatControlManager;
import com.nexusstaff.pro.manager.DiscordWebhookManager;
import com.nexusstaff.pro.manager.FreezeManager;
import com.nexusstaff.pro.manager.MessageService;
import com.nexusstaff.pro.manager.NetworkMessenger;
import com.nexusstaff.pro.manager.LuckPermsManager;
import com.nexusstaff.pro.manager.VaultHook;
import com.nexusstaff.pro.manager.AltAccountManager;
import com.nexusstaff.pro.manager.PunishmentManager;
import com.nexusstaff.pro.manager.StaffModeManager;
import com.nexusstaff.pro.manager.StorageManager;
import com.nexusstaff.pro.manager.VanishManager;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexusStaffPlugin extends JavaPlugin {
    private MessageService messages;
    private StorageManager storage;
    private StaffModeManager staffMode;
    private VanishManager vanish;
    private FreezeManager freeze;
    private AntiCheatHooks antiCheat;
    private PunishmentManager punishments;
    private StaffListener staffListener;
    private DiscordWebhookManager discord;
    private NetworkMessenger network;
    private LuckPermsManager luckPerms;
    private VaultHook vault;
    private AltAccountManager altAccounts;
    private AntiCheatEventBridge antiCheatBridge;
    private ChatControlManager chatControl;
    private StaffDashboardMenu dashboardMenu;
    private ReportManager reports;
    private int dashboardRefreshTask;
    private NamespacedKey actionKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("guis.yml", false);
        saveResource("messages.yml", false);
        messages = new MessageService(this);
        luckPerms = new LuckPermsManager(this);
        vault = new VaultHook(this);
        altAccounts = new AltAccountManager(this);
        actionKey = new NamespacedKey(this, "menu-action");
        storage = new StorageManager(this);
        reports = new ReportManager(this);
        staffMode = new StaffModeManager(this);
        vanish = new VanishManager(this);
        freeze = new FreezeManager(this);
        antiCheat = new AntiCheatHooks(this);
        antiCheatBridge = new AntiCheatEventBridge(this);
        punishments = new PunishmentManager(this);
        discord = new DiscordWebhookManager(this);
        network = new NetworkMessenger(this);
        chatControl = new ChatControlManager();
        var guiConfiguration = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "guis.yml"));
        dashboardMenu = new StaffDashboardMenu(this, guiConfiguration);

        StaffCommand command = new StaffCommand(this);
        for (String name : new String[]{"staff", "staffmode", "threats", "inspect", "punish", "staffchat", "mutechat", "freezeall", "report", "ban", "tempban", "ipban", "tempmute", "mute", "dupeip"}) {
            getCommand(name).setExecutor(command);
            getCommand(name).setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new StaffDashboardListener(this, dashboardMenu), this);
        dashboardRefreshTask = getServer().getScheduler().runTaskTimer(this,
            new StaffDashboardRefreshTask(this, new DashboardMetrics(this), dashboardMenu), 20L, 20L).getTaskId();
        staffListener = new StaffListener(this);
        getServer().getPluginManager().registerEvents(staffListener, this);
        getLogger().info("AsterMC enabled. LuckPerms=" + luckPerms.isAvailable() + ", VaultPermissions=" + vault.permissionsAvailable()
            + ", VaultEconomy=" + vault.economyAvailable() + ", GrimAC=" + antiCheat.isGrimPresent() + ", Vulcan=" + antiCheat.isVulcanPresent());
    }

    @Override
    public void onDisable() {
        if (staffMode != null) staffMode.restoreAll();
        if (staffListener != null) staffListener.stop();
        if (dashboardRefreshTask != 0) getServer().getScheduler().cancelTask(dashboardRefreshTask);
        if (storage != null) storage.close();
    }

    public MessageService messages() { return messages; }
    public StorageManager storage() { return storage; }
    public StaffModeManager staffMode() { return staffMode; }
    public VanishManager vanish() { return vanish; }
    public FreezeManager freeze() { return freeze; }
    public AntiCheatHooks antiCheat() { return antiCheat; }
    public PunishmentManager punishments() { return punishments; }
    public DiscordWebhookManager discord() { return discord; }
    public NetworkMessenger network() { return network; }
    public LuckPermsManager luckPerms() { return luckPerms; }
    public VaultHook vault() { return vault; }
    public AltAccountManager altAccounts() { return altAccounts; }
    public AntiCheatEventBridge antiCheatBridge() { return antiCheatBridge; }
    public ChatControlManager chatControl() { return chatControl; }
    public StaffDashboardMenu dashboardMenu() { return dashboardMenu; }
    public ReportManager reports() { return reports; }
    public boolean isInCombat(java.util.UUID playerId) { return staffListener != null && staffListener.isInCombat(playerId); }
    public NamespacedKey actionKey() { return actionKey; }
}