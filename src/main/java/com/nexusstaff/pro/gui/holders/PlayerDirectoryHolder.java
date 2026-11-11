package com.nexusstaff.pro.gui.holders;

import com.nexusstaff.pro.gui.MenuHolder;
import com.nexusstaff.pro.gui.MenuType;

public final class PlayerDirectoryHolder extends MenuHolder {
    private final int page;

    public PlayerDirectoryHolder(int page, net.kyori.adventure.text.Component title) {
        super(MenuType.PLAYERS, null, 54, title);
        this.page = page;
    }

    public int page() { return page; }
}