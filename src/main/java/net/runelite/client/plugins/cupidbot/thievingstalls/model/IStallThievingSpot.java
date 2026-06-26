package net.runelite.client.plugins.cupidbot.thievingstalls.model;

public interface IStallThievingSpot {
    void thieve();
    void bank();

    Integer[] getItemIdsToDrop();
}
