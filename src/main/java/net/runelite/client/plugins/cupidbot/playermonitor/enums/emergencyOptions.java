package net.runelite.client.plugins.cupidbot.playermonitor.enums;

public enum emergencyOptions {
    LOGOUT("logout"),
    HOP_WORLDS("hop_worlds"),
    USE_ITEM("use_item");

    private final String option;

    emergencyOptions(String options) {
        this.option = options;
    }
    public String getOption(){
        return option;
    }
}