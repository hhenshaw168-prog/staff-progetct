package com.nexusstaff.pro.gui;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MenuHolder implements InventoryHolder {
    private final MenuType type;
    private final UUID target;
    private final Inventory inventory;

    public MenuHolder(MenuType type, UUID target, int size, net.kyori.adventure.text.Component title) {
        this.type = type;
        this.target = target;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public MenuType type() { return type; }
    public UUID target() { return target; }
    @Override public Inventory getInventory() { return inventory; }
}