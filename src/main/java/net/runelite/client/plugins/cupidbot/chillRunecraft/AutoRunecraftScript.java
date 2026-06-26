package net.runelite.client.plugins.cupidbot.chillRunecraft;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.chillRunecraft.Altars;
import net.runelite.client.plugins.cupidbot.chillRunecraft.AutoRunecraftConfig;
import net.runelite.client.plugins.cupidbot.chillRunecraft.States;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;


public class AutoRunecraftScript extends Script
{
    private States state;
    private Altars altar;
    private boolean initialise;
    public static int runsCompleted = 0;
    public static int initialRunecraftLevel;
    public static int runecraftLevel;
    public static int initialRunecraftXp;
    public static int runecraftXp;

    public boolean run(AutoRunecraftConfig config)
    {
        CupidBot.enableAutoRunOn = true;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_RUNECRAFT);

        altar = config.ALTAR();

        initialise = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                long startTime = System.currentTimeMillis();

                if (initialise)
                {
                    initialRunecraftXp = CupidBot.getClient().getSkillExperience(Skill.RUNECRAFT);
                    initialRunecraftLevel = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);

                    if (!Rs2Inventory.hasItem(altar.getTalismanName()) && !Rs2Equipment.isWearing(altar.getTiaraName()))
                    {
                        CupidBot.log("No Talisman / Tiara found - banking");
                        state = States.BANKING;
                        initialise = false;
                        return;
                    }

                    if (!Rs2Inventory.hasItem("Pure Essence", false))
                    {
                        CupidBot.log("No essence in inventory - banking");
                        state = States.BANKING;
                        initialise = false;
                        return;
                    }
                    state = States.WALKING_TO_ALTAR;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                if (Rs2Player.isInteracting()) return;

                switch(state)
                {
                    case BANKING:
                        initialise = false;
                        boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                        CupidBot.status = "Walking to bank";
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;

                        if (!Rs2Inventory.hasItem(altar.getTalismanName()) && !Rs2Equipment.isWearing(altar.getTiaraName()))
                        {
                            CupidBot.status = "Withdrawing Tiara";

                            if (!Rs2Bank.hasBankItem(altar.getTiaraName()))
                            {
                                CupidBot.status = "Withdrawing Talisman";
                                if (!Rs2Bank.hasBankItem(altar.getTalismanName()))
                                {
                                    CupidBot.showMessage("No tiara / talisman in bank!");
                                    shutdown();
                                    return;
                                }
                                Rs2Bank.withdrawOne(altar.getTalismanName());
                            }
                            Rs2Bank.withdrawAndEquip(altar.getTiaraName());
                            Rs2Random.wait(800,1600);
                        }

                        if (Rs2Inventory.hasItem(altar.getRuneName(), false))
                        {
                            CupidBot.status = "Depositing runes";
                            Rs2Bank.depositAll(altar.getRuneName(), false);
                            Rs2Random.wait(800, 1600);
                        }

                        CupidBot.status = "Withdrawing pure essence";

                        if (!Rs2Bank.hasBankItem("Pure essence", false))
                        {
                            CupidBot.showMessage("No pure essence in bank!");
                            shutdown();
                            return;
                        }
                        Rs2Bank.withdrawAll("Pure essence", true);
                        Rs2Random.wait(800, 1600);
                        state = States.WALKING_TO_ALTAR;
                        Rs2Bank.closeBank();
                        break;

                    case WALKING_TO_ALTAR:
                        initialise = false;
                        CupidBot.status = "Walking to altar";
                        boolean walked = Rs2Walker.walkTo(altar.getAltarWorldPoint(), 2);
                        if (!walked) return;
                        Rs2Random.wait(800, 1600);
                        state = States.ENTERING_ALTAR;
                        break;

                    case ENTERING_ALTAR:
                        initialise = false;
                        CupidBot.status = "Entering altar";
                        if (!Rs2Equipment.isWearing(altar.getTiaraName()))
                        {
                            Rs2Inventory.useItemOnObject(altar.getTalismanID(), altar.getAltarRuinsID());
                        }
                        else
                        {
                            CupidBot.getRs2TileObjectCache().query().withId(altar.getAltarRuinsID()).interact("Enter");
                        }
                        Rs2Random.wait(800, 1600);
                        sleepUntil(() -> !Rs2Player.isMoving());
                        Rs2Random.wait(2000, 2400);
                        state = States.CRAFTING_RUNES;
                        break;

                    case CRAFTING_RUNES:
                        initialise = false;
                        CupidBot.status = "Crafting runes";
                        Rs2Inventory.useItemOnObject(ItemID.BLANKRUNE_HIGH, altar.getAltarID()); //could just interact with altar, but I'm too lazy to change this now
                        Rs2Random.wait(800, 1600);
                        sleepUntil(() -> Rs2Player.waitForXpDrop(Skill.RUNECRAFT));
                        Rs2Random.wait(800, 1600);
                        runsCompleted++;
                        updateLevelXp();
                        state = States.EXITING_ALTAR;
                        break;

                    case EXITING_ALTAR:
                        CupidBot.status = "Exiting altar";
                        CupidBot.getRs2TileObjectCache().query().withId(altar.getPortalID()).interact("Use");
                        sleepUntil(() -> !Rs2Player.isMoving());
                        Rs2Random.wait(800, 1600);
                        state = States.BANKING;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex)
            {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    public void updateLevelXp()
    {
        runecraftLevel = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);
        runecraftXp = CupidBot.getClient().getSkillExperience(Skill.RUNECRAFT);
    }
}