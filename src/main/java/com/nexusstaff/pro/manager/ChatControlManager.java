package com.nexusstaff.pro.manager;

public final class ChatControlManager {
    private volatile boolean muted;

    public boolean toggle() {
        muted = !muted;
        return muted;
    }

    public boolean isMuted() { return muted; }
}