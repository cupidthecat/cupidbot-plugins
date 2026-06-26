package net.runelite.client.plugins.cupidbot.eventdismiss;

public enum EventAction {
    ACCEPT("Accept"),
    DISMISS("Dismiss");

    private final String name;

    EventAction(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}