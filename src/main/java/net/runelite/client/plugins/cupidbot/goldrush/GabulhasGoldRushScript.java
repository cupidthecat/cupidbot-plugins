package net.runelite.client.plugins.cupidbot.goldrush;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.cupidbot.goldrush.GabulhasGoldRushInfo.botStatus;

@Slf4j
public class GabulhasGoldRushScript extends Script {
    @Inject
    private Notifier notifier;

    private WorldPoint zanarisRing = new WorldPoint(2412, 4434, 0);

    private WorldPoint bankPoint = new WorldPoint(2381, 4455, 0);

    public boolean run(GabulhasGoldRushConfig config) {
        CupidBot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case STARTING:
                        Rs2Camera.setZoom(181);
                        Rs2Camera.setAngle(90, 90);
                        Rs2Camera.adjustPitch(383);

                        break;
                    case GETTING_BARS:
                        Rs2Inventory.wield("Goldsmith gauntlets");
                        sleep(100, 1000);
                        Rs2Bank.openBank();
                        sleep(100, 1000);
                        while (Rs2Inventory.contains("Gold bar")) {
                            if (Rs2Bank.isOpen()) {
                                sleep(100, 600);
                                Rs2Bank.depositAll("Gold bar");
                            }
                            sleep(100, 1000);
                        }

                        sleep(100, 2000);
                        while (!Rs2Inventory.contains("Gold ore")) {
                            Rs2Bank.withdrawAll("Gold ore");
                            sleep(100, 1000);
                        }
                        Rs2Bank.closeBank();
                        if (CupidBot.getClient().getEnergy() > Rs2Random.nextInt(2000, 10000, 2, true)) {
                            sleep(100, 2000);
                            Rs2Player.toggleRunEnergy(true);
                        }
                        botStatus = GabulhasGoldRushInfo.states.USING_BARS;
                        break;
                    case USING_BARS:
                        int currentXP = CupidBot.getClient().getSkillExperience(Skill.SMITHING);
                        CupidBot.getRs2TileObjectCache().query().withId(9100).interact("Put-ore-on");
                        while (Rs2Inventory.contains("Gold ore")) {
                            sleep(100);
                        }

                        Rs2Walker.walkTo(new WorldPoint(1940, 4964, 0));

                        while (CupidBot.getClient().getSkillExperience(Skill.SMITHING) == currentXP) {
                            sleep(100, 600);
                        }
                        botStatus = GabulhasGoldRushInfo.states.RETRIEVING_BARS;
                        break;
                    case RETRIEVING_BARS:
                        Rs2Inventory.wield("Ice gloves");
                        CupidBot.getRs2TileObjectCache().query().withId(9092).interact("Take");
                        Rs2Keyboard.keyPress(' ');
                        while (!Rs2Inventory.contains("Gold bar")) {
                            Rs2Keyboard.keyPress(32);
                            sleep(100, 1000);
                        }

                        botStatus = GabulhasGoldRushInfo.states.GETTING_BARS;

                        break;
                }

            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }


}

