package net.runelite.client.plugins.cupidbot.fishingtrawler;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.cupidbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.cupidbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Slf4j
public class FishingTrawlerScript extends Script {
    public static boolean tentacle = false;
    public static boolean wasInsideBoat = false;

    private static final int WIDGET_GANGPLANK_CONTINUE = 15007746;
    private static final int WIDGET_CONTRIBUTION = 23986189;
    private static final int OBJECT_TRAWLERNET = 2483;
    private static final int OBJECT_SHIPSLADDER = 4060;
    private static final int OBJECT_DAISIES = 1189;
    private static final int OBJECT_TENTACLE_LADDER = 4139;

    public boolean run(FishingTrawlerConfig config) {
        CupidBot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn() || !super.run() || BreakHandlerScript.isBreakActive()) return;

                long startTime = System.currentTimeMillis();
                if (!Rs2Inventory.hasItem("axe", false) && !Rs2Equipment.isWearing("axe", false)) {
                    CupidBot.showMessage("You need an axe in your inventory or equipped.");
                    shutdown();
                    return;
                }

                if (CupidBot.getRs2TileObjectCache().query().withId(OBJECT_TRAWLERNET).nearest() != null) {
                    if (wasInsideBoat) {
                        CupidBot.log("Looting Rewards");
                        CupidBot.status = "Looting Rewards";
                        CupidBot.getRs2TileObjectCache().query().interact(OBJECT_TRAWLERNET, "inspect");
                        Rs2Player.waitForWalking();
                        //check for visible trawler widget
                        sleepUntil(() -> Rs2Widget.isWidgetVisible(367, 19), 5000);
                        Rs2Widget.clickWidget(367,19);
                        sleep(Rs2Random.randomGaussian(600, 300));
                        wasInsideBoat = false;
                        BreakHandlerScript.setLockState(false);
                        if (BreakHandlerScript.isBreakActive()) {
                            CupidBot.log("Break time, waiting...");
                            return;
                        }
                    }

                    BreakHandlerScript.setLockState(true);

                    if (Rs2GameObject.interact(new WorldPoint(2675, 3170, 0), "Cross")) {
                        CupidBot.log("Crossing Gangplank");
                        Rs2Player.waitForWalking(10000);
                    }
                } else if (CupidBot.getRs2TileObjectCache().query().withId(OBJECT_DAISIES).nearest() != null && Rs2Player.getWorldLocation().getPlane() == 0) {
                    CupidBot.log("Washed up — heading back");
                    Rs2Walker.walkTo(new WorldPoint(2676, 3170, 0));
                }

                Widget gangplankWidget = Rs2Widget.getWidget(WIDGET_GANGPLANK_CONTINUE);
                String gangplankMessage = gangplankWidget != null ? gangplankWidget.getText() : null;

                Widget contributionWidget = Rs2Widget.getWidget(WIDGET_CONTRIBUTION);
                String contributionText = contributionWidget != null ? contributionWidget.getText() : null;

                if (gangplankMessage != null && gangplankMessage.contains("continue")) {
                        Rs2Widget.clickWidget(WIDGET_GANGPLANK_CONTINUE);
                        CupidBot.status = "Waiting inside the boat";
                }

                int contributionValue = 0;
                if (contributionText != null && contributionText.contains(":")) {
                    try {
                        contributionValue = Integer.parseInt(contributionText.split(":")[1].strip());
                    } catch (Exception e) {
                        log.debug("Failed to parse contribution value: {} [{}]", contributionText, e.getMessage());
                    }
                } else {
                    log.debug("Contribution widget missing or malformed.");
                }

                if (Rs2Player.getWorldLocation().getPlane() == 0 && CupidBot.getRs2TileObjectCache().query().withId(OBJECT_SHIPSLADDER).nearest() != null) {
                    CupidBot.log("In minigame — heading up ladder");
                    wasInsideBoat = true;
                    Rs2GameObject.interact(new WorldPoint(1884, 4826, 0), "Climb-up");
                    Rs2Player.waitForWalking();
                    Rs2Player.waitForAnimation();
                    Rs2Walker.walkFastCanvas(new WorldPoint(1885, 4827, 1));
                }

                if (config.stopat50() && contributionValue >= 50) return;

                if (!Rs2Player.isInteracting() && !Rs2Player.isMoving()) {
                    log.debug("Tentacle phase");

                    Rs2NpcModel tentacleNpc = CupidBot.getRs2NpcCache().query().withName("Enormous Tentacle").nearestOnClientThread();

                    if (tentacleNpc != null) {
                        if (!tentacle) {
                            CupidBot.log("Tentacle found, chopping it down");
                            Rs2Camera.turnTo(tentacleNpc.getNpc());
                        }
                        sleepUntil(() -> tentacleNpc.getNpc().getAnimation() == 8953, 10000);
                        CupidBot.getClientThread().invoke(() -> CupidBot.getRs2NpcCache().query().withName("Enormous Tentacle").interact("Chop"));
                        sleepUntilTick(2);
                        if (!Rs2Player.isInteracting()) CupidBot.getClientThread().invoke(() -> CupidBot.getRs2NpcCache().query().withName("Enormous Tentacle").interact("Chop"));
                        tentacle = true;
                        wasInsideBoat = true;
                        sleepUntil(() -> !Rs2Player.isInteracting());
                    } else if (tentacle) {
                        Rs2TileObjectModel ladderObject = CupidBot.getRs2TileObjectCache().query().withId(OBJECT_TENTACLE_LADDER).nearest();
                        if (ladderObject != null) {
                            WorldPoint current = Rs2Player.getWorldLocation();
                            WorldPoint ladder = ladderObject.getWorldLocation();
                            int plane = current.getPlane();
                            int dx = ladder.dx(1).getX();

                            if (current.getY() == 4823) {
                                Rs2Walker.walkFastCanvas(new WorldPoint(dx, current.dy(4).getY(), plane));
                                tentacle = false;
                            } else if (current.getY() == 4827) {
                                Rs2Walker.walkFastCanvas(new WorldPoint(dx, current.dy(-4).getY(), plane));
                                tentacle = false;
                            }
                        } else {
                            log.debug("Tentacle ladder object not found.");
                        }
                    }
                }

                long endTime = System.currentTimeMillis();
                log.debug("Loop time: {}ms", endTime - startTime);

            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        wasInsideBoat = false;
        tentacle = false;
    }
}
