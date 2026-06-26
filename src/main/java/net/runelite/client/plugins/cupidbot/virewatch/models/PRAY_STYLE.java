package net.runelite.client.plugins.cupidbot.virewatch.models;

import lombok.Getter;

public enum PRAY_STYLE {

    NORMAL("Normal prayers"),
    QUICK_PRAYERS("Quick-prayers");

    @Getter
    private String name;

    PRAY_STYLE(String name) {
        this.name = name;
    }
}
