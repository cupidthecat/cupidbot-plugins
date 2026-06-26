package net.runelite.client.plugins.cupidbot.microhunter.scripts;

import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.cupidbot.microhunter.AutoHunterConfig;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

enum State {
    IDLE,
    CATCHING,
    DROPPING,
    LAYING
}


public class AutoChinScript extends Script {

    public static boolean test = false;
    private boolean oneRun = false;
    private List<WorldPoint> boxtiles = new ArrayList<>();
    private List<Integer> trapIds = Arrays.asList(
            ItemID.BOX_TRAP,
            ObjectID.BOX_TRAP,
            ObjectID.BOX_TRAP_9385,
            ObjectID.BOX_TRAP_9380,
            ObjectID.SHAKING_BOX_9384,
            ObjectID.SHAKING_BOX_9383,
            ObjectID.SHAKING_BOX_9382,
            ObjectID.SHAKING_BOX
    );
    State currentState = State.IDLE;

    public boolean run(AutoHunterConfig config) {
        CupidBot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                switch (currentState) {
                    case IDLE:
                        handleBreaks();
                        handleIdleState();
                        break;
                    case DROPPING:
                        handleBreaks();
                        handleDroppingState(config);
                        break;
                    case CATCHING:
                        handleBreaks();
                        handleCatchingState(config);
                        break;
                    case LAYING:
                        handleBreaks();
                        handleLayingState(config);
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void handleIdleState() {
        try {
            if (CupidBot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(4).interact("lay")) {
                currentState = State.LAYING;
                return;
            }

            if (Rs2Inventory.emptySlotCount() <= 1 && Rs2Inventory.contains(ItemID.FERRET)) {
                while (Rs2Inventory.contains(ItemID.FERRET)) {
                    Rs2Inventory.interact(ItemID.FERRET, "Release");
                    sleep(0, 750);
                    if (!Rs2Inventory.contains(ItemID.FERRET)) {
                        break;
                    }
                }
                currentState = State.DROPPING;
                return;
            }

            if (CupidBot.getRs2TileObjectCache().query().withId(ObjectID.SHAKING_BOX_9384).within(4).interact("reset")) {
                currentState = State.CATCHING;
                return;
            }
            if (CupidBot.getRs2TileObjectCache().query().withId(ObjectID.SHAKING_BOX_9383).within(4).interact("reset")) {
                currentState = State.CATCHING;
                return;
            }
            if (CupidBot.getRs2TileObjectCache().query().withId(ObjectID.SHAKING_BOX_9382).within(4).interact("reset")) {
                currentState = State.CATCHING;
                return;
            }
            // Black chinchompa shaking box
            if (CupidBot.getRs2TileObjectCache().query().withId(ObjectID.SHAKING_BOX).within(4).interact("reset")) {
                currentState = State.CATCHING;
                return;
            }

            if (CupidBot.getRs2TileObjectCache().query().withId(ObjectID.BOX_TRAP_9385).within(4).interact("reset")) {
                currentState = State.CATCHING;
            }
        } catch (Exception ex) {
            CupidBot.log(ex.getMessage());
            ex.printStackTrace();
            currentState = State.CATCHING;
        }
    }

    private void handleDroppingState(AutoHunterConfig config) {
        sleep(config.minSleepAfterLay(), config.maxSleepAfterLay());
        currentState = State.IDLE;
    }

    private void handleCatchingState(AutoHunterConfig config) {
        sleep(config.minSleepAfterCatch(), config.maxSleepAfterCatch());
        currentState = State.IDLE;
    }

    private void handleLayingState(AutoHunterConfig config) {
        sleep(config.minSleepAfterLay(), config.maxSleepAfterLay());
        currentState = State.IDLE;
    }

    public void handleBreaks() {
        int secondsUntilBreak = BreakHandlerScript.breakIn;

        if (secondsUntilBreak > 61 && secondsUntilBreak < 200) {
            if (!boxtiles.isEmpty()) {
                boxtiles.clear();
            }
        }

        if (secondsUntilBreak > 0 && secondsUntilBreak <= 60) {
            for (int trapId : trapIds) {
                var gameObjects = CupidBot.getRs2TileObjectCache().query().withId(trapId).toList();
                for (Rs2TileObjectModel gameObject : gameObjects) {
                    WorldPoint location = gameObject.getWorldLocation();
                    if (Rs2Player.getWorldLocation().distanceTo(location) > 5) {
                        continue;
                    }
                    if (!boxtiles.contains(location)) {
                        boxtiles.add(location);
                    }
                }
            }

            for (WorldPoint oldTile : boxtiles) {
                if (CupidBot.getRs2TileObjectCache().query().within(oldTile, 0).first() != null) {
                    if (Rs2Player.getWorldLocation().distanceTo(oldTile) > 5) {
                        continue;
                    }
                    while (CupidBot.getRs2TileObjectCache().query().within(oldTile, 0).first() != null) {
                        if (Rs2Player.getWorldLocation().distanceTo(oldTile) > 5) {
                            break;
                        }
                        if (CupidBot.getRs2TileObjectCache().query().within(oldTile, 0).interact("Dismantle")) {
                            sleep(1000, 3000);
                            break;
                        }
                        if (CupidBot.getRs2TileObjectCache().query().within(oldTile, 0).interact("Reset")) {
                            sleep(1000, 3000);
                            break;
                        }
                    }
                }
            }
            oneRun = true;
        }

        if (secondsUntilBreak > 60 && oneRun) {
            if (!boxtiles.isEmpty()) {
                for (WorldPoint LayTrapTile : boxtiles) {
                    if (CupidBot.getRs2TileObjectCache().query().within(LayTrapTile, 0).first() != null) {

                    } else {
                        if (!Rs2Player.getWorldLocation().equals(LayTrapTile)) {
                            while (!Rs2Player.getWorldLocation().equals(LayTrapTile)) {
                                CupidBot.log("Walking to trap tile");
                                Rs2Walker.walkTo(LayTrapTile, 0);
                                sleep(1000, 3000);
                            }
                        }
                        CupidBot.log("Placing trap");
                        int maxTries = 0;
                        while (CupidBot.getRs2TileObjectCache().query().within(LayTrapTile, 0).first() == null) {
                            if (CupidBot.getRs2TileItemCache().query().withName("Box trap").within(6).count() == 0) {
                                if (Rs2Inventory.contains("Box trap")) {
                                    Rs2Inventory.interact("Box trap", "Lay");
                                    sleep(4000, 6000);
                                }
                            } else {
                                CupidBot.getRs2TileItemCache().query().withName("Box trap").within(6).interact("Take");
                                sleep(4000, 6000);
                            }
                            if (maxTries >= 3) {
                                CupidBot.log("Failed, placing the trap");
                                break;
                            }
                            maxTries++;
                        }
                    }
                }
            }
            oneRun = false;
        }
    }
}
