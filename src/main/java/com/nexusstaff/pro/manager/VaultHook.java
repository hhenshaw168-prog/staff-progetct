package com.nexusstaff.pro.manager;

import java.lang.reflect.Method;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import com.nexusstaff.pro.NexusStaffPlugin;
import org.bukkit.entity.Player;

public final class VaultHook {
    private final ClassLoader vaultClassLoader;

    public VaultHook(NexusStaffPlugin plugin) {
        var vault = Bukkit.getPluginManager().getPlugin("Vault");
        vaultClassLoader = vault == null ? Thread.currentThread().getContextClassLoader() : vault.getClass().getClassLoader();
    }

    public boolean permissionsAvailable() { return provider("net.milkbowl.vault.permission.Permission").isPresent(); }
    public boolean economyAvailable() { return provider("net.milkbowl.vault.economy.Economy").isPresent(); }

    public String primaryGroup(Player player) {
        return callString("net.milkbowl.vault.permission.Permission", "getPrimaryGroup", player).orElse("default");
    }

    public String prefix(Player player) {
        return callString("net.milkbowl.vault.permission.Permission", "getPlayerPrefix", player).orElse("");
    }

    public double balance(OfflinePlayer player) {
        Object economy = provider("net.milkbowl.vault.economy.Economy").orElse(null);
        if (economy == null) return 0.0;
        try {
            Method method = economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            return ((Number) method.invoke(economy, player)).doubleValue();
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return 0.0;
        }
    }

    private Optional<String> callString(String service, String methodName, Player player) {
        Object provider = provider(service).orElse(null);
        if (provider == null) return Optional.empty();
        try {
            Object result = provider.getClass().getMethod(methodName, Player.class).invoke(provider, player);
            return result instanceof String value ? Optional.of(value) : Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Object> provider(String className) {
        try {
            Class<?> service = Class.forName(className, false, vaultClassLoader);
            var registration = Bukkit.getServicesManager().getRegistration(service);
            return registration == null ? Optional.empty() : Optional.ofNullable(registration.getProvider());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}