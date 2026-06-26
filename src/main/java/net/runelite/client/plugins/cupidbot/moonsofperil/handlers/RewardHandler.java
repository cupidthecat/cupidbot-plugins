package net.runelite.client.plugins.cupidbot.moonsofperil.handlers;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.Locations;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.State;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.Widgets;
import net.runelite.client.plugins.cupidbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;

import javax.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;

import static net.runelite.client.plugins.cupidbot.util.Global.sleep;

public class RewardHandler implements BaseHandler {

    @Getter
    private static final AtomicInteger rewardChestCount = new AtomicInteger();
    private static final WorldPoint rewardChestLocation = Locations.REWARDS_CHEST_LOBBY.getWorldPoint();
    private static final int lunarChestGameObjectID = 51346;
    private static final int lunarChestBankAllWidgetID = 56885268;
    private boolean eclipseEnabled;
    private boolean blueEnabled;
    private boolean bloodEnabled;
    private final boolean debugLogging;
    private final net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BossHandler boss;

    @Inject
    public RewardHandler(MoonsOfPerilConfig cfg) {
        this.eclipseEnabled = cfg.enableEclipse();
        this.blueEnabled = cfg.enableBlue();
        this.bloodEnabled = cfg.enableBlood();
        this.boss = new BossHandler(cfg);
        this.debugLogging = cfg.debugLogging();
    }

    @Override
    public boolean validate() {
        boolean eclipseDone = !eclipseEnabled || !boss.bossIsAlive("Eclipse Moon", Widgets.ECLIPSE_MOON_ID.getID());
        boolean blueDone = !blueEnabled || !boss.bossIsAlive("Blue Moon", Widgets.BLUE_MOON_ID.getID());
        boolean bloodDone = !bloodEnabled || !boss.bossIsAlive("Blood Moon", Widgets.BLOOD_MOON_ID.getID());
        return eclipseDone && blueDone && bloodDone;
    }

    @Override
    public State execute() {
        BreakHandlerScript.setLockState(true);
        boss.walkToBoss(null, "Rewards Chest", rewardChestLocation);
        if (CupidBot.getRs2TileObjectCache().query().interact(lunarChestGameObjectID, "Claim")) {
            if (debugLogging) {CupidBot.log("Successfully claimed rewards from Lunar Chest");}
            rewardChestCount.incrementAndGet();
            sleep(2_400);
        }
        if (Rs2Widget.clickWidget(lunarChestBankAllWidgetID)) {
            if (debugLogging) {CupidBot.log("Successfully banked all rewards");}
            sleep(1_200);
        }
        BreakHandlerScript.setLockState(false);
        return State.IDLE;
    }
}
