package net.runelite.client.plugins.cupidbot.crafting.scripts;

import java.util.Map;

public interface ICraftingScript {
    String getName();
    String getVersion();
    String getState();
    Map<String, String> getCustomProperties();
}
