package net.runelite.client.plugins.cupidbot.cluesolver;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("cluesolver")
public interface ClueSolverConfig extends Config {

    @ConfigItem(
            keyName = "enableClueSolver",
            name = "Enable Clue Solver",
            description = "Enable the clue solver plugin",
            position = 1
    )
    default boolean enableClueSolver() {
        return false;
    }
}
