package com.nexusstaff.pro.gui.holders;

import com.nexusstaff.pro.gui.MenuHolder;
import com.nexusstaff.pro.gui.MenuType;
import java.util.UUID;

public final class StaffDashboardHolder extends MenuHolder {
    public StaffDashboardHolder(int size, net.kyori.adventure.text.Component title) {
        super(MenuType.DASHBOARD, (UUID) null, size, title);
    }
}