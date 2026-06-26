package net.runelite.client.plugins.cupidbot.qualityoflife.scripts.wintertodt;

import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.cupidbot.qualityoflife.QoLPlugin;
import net.runelite.client.plugins.cupidbot.qualityoflife.enums.WintertodtActions;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.cupidbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WintertodtScript extends Script {
    public static QoLConfig config;
    public static Rs2TileObjectModel unlitBrazier;
    public static Rs2TileObjectModel brokenBrazier;
    public static Rs2NpcModel pyromancer;
    public static Rs2NpcModel incapitatedPyromancer;
    public static boolean helpedIncapitatedPyromancer = false;
    public static boolean isWintertodtAlive = false;
    public static int wintertodtHp = -1;
    @Inject
    private QoLPlugin qolPlugin;

    public static boolean isInWintertodtRegion() {
        var location = Rs2Player.getWorldLocation();
        return location != null && location.getRegionID() == 6462;
    }

    public boolean run(QoLConfig config) {
        WintertodtScript.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn() || !super.run() || !isInWintertodtRegion()) {
                    return;
                }

                Widget wintertodtHealthbar = Rs2Widget.getWidget(25952276);
                isWintertodtAlive = Rs2Widget.hasWidget("Wintertodt's Energy");
                if (wintertodtHealthbar != null && isWintertodtAlive) {
                    String widgetText = wintertodtHealthbar.getText();
                    wintertodtHp = Integer.parseInt(widgetText.split("\\D+")[1]);
                } else {
                    wintertodtHp = -1;
                }
                brokenBrazier = CupidBot.getRs2TileObjectCache().query().withId(ObjectID.WINT_BRAZIER_BROKEN).within(5).nearest();
                unlitBrazier = CupidBot.getRs2TileObjectCache().query().withId(ObjectID.WINT_BRAZIER).within(5).nearest();

                shouldEat();

                if (!config.interrupted())
                    return;

                NewMenuEntry actionToResume = config.wintertodtActions().getMenuEntry();
                if (config.wintertodtActions().equals(WintertodtActions.FEED)) {
                    Rs2TileObjectModel fireBrazier = CupidBot.getRs2TileObjectCache().query().withId(ObjectID.WINT_BRAZIER_LIT).nearest();
                    if (fireBrazier != null && fireBrazier.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) < 5) {
                        if (!Rs2Inventory.contains(ItemID.WINT_BRUMA_ROOT) && !Rs2Inventory.contains(ItemID.WINT_BRUMA_KINDLING)) {
                            qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
                            qolPlugin.updateWintertodtInterupted(false);
                            CupidBot.log("No resources found in inventory, cancelling action.");
                            return;
                        }
                        CupidBot.doInvoke(actionToResume, new java.awt.Rectangle(1, 1));
                        qolPlugin.updateWintertodtInterupted(false);
                    }
                }
                if (config.wintertodtActions().equals(WintertodtActions.FLETCH)) {
                    if (!Rs2Inventory.contains(ItemID.WINT_BRUMA_ROOT)) {
                        qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
                        qolPlugin.updateWintertodtInterupted(false);
                        return;
                    }

                    CupidBot.doInvoke(actionToResume, new java.awt.Rectangle(1, 1));
                    WintertodtActions.fletchBrumaRootsOnClicked();
                    qolPlugin.updateWintertodtInterupted(false);
                }

            } catch (Exception e) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), e);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    // shutdown
    @Override
    public void shutdown() {
        super.shutdown();
        CupidBot.log("Winterdotd script shutting down");
    }

    public void onNpcChanged(NpcChanged event) {
        if (event.getNpc().getId() == 7372) {
            CupidBot.getRs2NpcCache().query().where(n -> n.getNpc().equals(event.getNpc())).nearest();
        } else if (event.getNpc().getId() == 7371) {
            pyromancer = CupidBot.getRs2NpcCache().query().where(n -> n.getNpc().equals(event.getNpc())).nearest();
            incapitatedPyromancer = null;
            if (helpedIncapitatedPyromancer) {
                if (config.lightUnlitBrazier()) {
                    if (Rs2Equipment.isWearing(ItemID.WINT_TORCH) || Rs2Equipment.isWearing(ItemID.WINT_TORCH_OFFHAND) || Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                        scheduledFuture = scheduledExecutorService.schedule(() -> unlitBrazier.click("Light"), 300, TimeUnit.MILLISECONDS);
                    }
                }
            }
            helpedIncapitatedPyromancer = false;
        }
    }

    public void onNpcSpawned(NpcSpawned event) {
        if (event.getNpc().getId() == 7372) {
            if (incapitatedPyromancer != null) {
                if (incapitatedPyromancer.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) > event.getNpc().getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation())) {
                    incapitatedPyromancer = CupidBot.getRs2NpcCache().query().where(n -> n.getNpc().equals(event.getNpc())).nearest();
                    return;
                }
            }
            incapitatedPyromancer = CupidBot.getRs2NpcCache().query().where(n -> n.getNpc().equals(event.getNpc())).nearest();
        }
        if (event.getNpc().getId() == 7371) {
            if (pyromancer != null) {
                if (pyromancer.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) > event.getNpc().getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation())) {
                    pyromancer = CupidBot.getRs2NpcCache().query().where(n -> n.getNpc().equals(event.getNpc())).nearest();
                    return;
                }
            }
            pyromancer = CupidBot.getRs2NpcCache().query().where(n -> n.getNpc().equals(event.getNpc())).nearest();
        }
    }

    public void onNpcDespawned(NpcDespawned event) {
        if (incapitatedPyromancer != null && event.getNpc().equals(incapitatedPyromancer.getNpc())) {
            incapitatedPyromancer = CupidBot.getRs2NpcCache().query().withName("Incapacitated Pyromancer").nearestOnClientThread();
        }
        if (pyromancer != null && event.getNpc().equals(pyromancer.getNpc())) {
            pyromancer = CupidBot.getRs2NpcCache().query().withName("Pyromancer").nearestOnClientThread();
        }
    }

    public void onChatMessage(ChatMessage chatMessage) {
        var message = chatMessage.getMessage();
        if (message.contains("The brazier is broken and shrapnel")) {
            if (config.fixBrokenBrazier()) {
                qolPlugin.updateWintertodtInterupted(false);
                scheduledFuture = scheduledExecutorService.schedule(() -> brokenBrazier.click("Fix"), 300, TimeUnit.MILLISECONDS);
            }
        }

        if (message.startsWith("The brazier has gone out")) {
            if (incapitatedPyromancer != null) {
                if (!config.healPyromancer())
                    return;
                scheduledFuture = scheduledExecutorService.schedule(() -> incapitatedPyromancer.click("Help"), 300, TimeUnit.MILLISECONDS);
                helpedIncapitatedPyromancer = true;
            } else {
                if (config.lightUnlitBrazier()) {
                    if (Rs2Equipment.isWearing(ItemID.WINT_TORCH) || Rs2Equipment.isWearing(ItemID.WINT_TORCH_OFFHAND) || Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                        scheduledFuture = scheduledExecutorService.schedule(() -> unlitBrazier.click("Light"), 300, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }

        if (message.startsWith("You fix the brazier")) {
            if (incapitatedPyromancer != null) {
                if (!config.healPyromancer())
                    return;
                scheduledFuture = scheduledExecutorService.schedule(() -> incapitatedPyromancer.click("Help"), 300, TimeUnit.MILLISECONDS);
                helpedIncapitatedPyromancer = true;
            } else {
                if (config.lightUnlitBrazier()) {
                    if (Rs2Equipment.isWearing(ItemID.WINT_TORCH) || Rs2Equipment.isWearing(ItemID.WINT_TORCH_OFFHAND) || Rs2Inventory.hasItem(ItemID.TINDERBOX)) {
                        scheduledFuture = scheduledExecutorService.schedule(() -> unlitBrazier.click("Light"), 300, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }

        if (message.startsWith("Heal the Pyromancer")) {

                if (!config.healPyromancer())
                    return;
                scheduledFuture = scheduledExecutorService.schedule(() -> incapitatedPyromancer.click("Help"), 300, TimeUnit.MILLISECONDS);
                helpedIncapitatedPyromancer = true;

        }

        if (message.startsWith("You light the brazier")) {
            if (config.wintertodtActions().equals(WintertodtActions.NONE)) {
                return;
            }
            qolPlugin.updateWintertodtInterupted(true);
        }

        if (message.startsWith("You have gained a supply crate")) {
            qolPlugin.updateWintertodtInterupted(false);
            qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
        }

        if (message.startsWith("You did not earn enough points")) {
            qolPlugin.updateWintertodtInterupted(false);
            qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
        }

        if (message.startsWith("You have run out of bruma roots")) {
            qolPlugin.updateWintertodtInterupted(false);
            qolPlugin.updateLastWinthertodtAction(WintertodtActions.NONE);
        }
    }

    public int getWarmthLevel() {
        String warmthWidgetText = Rs2Widget.getChildWidgetText(396, 20);

        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(warmthWidgetText);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return 100;
    }

    private boolean shouldEat() {
        if (getWarmthLevel() <= config.eatFoodPercentage()) {
                List<Rs2ItemModel> rejuvenationPotions = Rs2Inventory.getPotions();

                if (rejuvenationPotions.isEmpty()) {
                    return false;
                }

                Rs2Inventory.interact(rejuvenationPotions.get(0), "Drink");
                sleepGaussian(600, 150);
                qolPlugin.updateWintertodtInterupted(true);
                return true;

        }
        return false;
    }
}
