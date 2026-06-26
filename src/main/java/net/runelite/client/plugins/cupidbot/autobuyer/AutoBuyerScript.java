package net.runelite.client.plugins.cupidbot.autobuyer;

import net.runelite.api.ChatMessageType;
import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.cupidbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.cupidbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.cupidbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.cupidbot.util.grandexchange.Rs2GrandExchange;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AutoBuyerScript extends Script {

    public static boolean test = false;
    private static boolean initialized = false;
    private static int initialCount = 0;
    private static int totalBought = 0;
    private static Map<String, Integer> itemsList;
    private Integer percent = null;

    public boolean run(AutoBuyerConfig config) {

        CupidBot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run() || !isRunning()) return;
                long startTime = System.currentTimeMillis();

                String listOfItemsToBuy;
                if (getQuestHelperPlugin().getSelectedQuest() != null && config.buyQuest()) {
                    final List<Integer> idsList = CupidBot.getClientThread().runOnClientThreadOptional(getQuestHelperPlugin()::itemsToTag).orElse(new ArrayList<>());

                    final Set<Integer> ids = idsList.stream()
                            .distinct()
                            .filter(this::isTradeable)
                            .limit(250)
                            .collect(Collectors.toCollection(TreeSet::new));

                    listOfItemsToBuy = getItemRequirements()
                            .stream()
                            .filter(item -> ids.contains(item.getId())) // Filter items based on the ids list
                            .map(item -> getItemName(item.getId()) + "[" + item.getQuantity() + "]") // Format: "Name[Quantity]"
                            .collect(Collectors.joining(",")); // Joins names with ", "

                    if (!listOfItemsToBuy.isEmpty()) CupidBot.log("Using quest helper item list: " + listOfItemsToBuy);
                    else CupidBot.log("Requirements already met check bank");


                } else {
                    listOfItemsToBuy = config.listOfItemsToBuy().replaceAll("\\s*,\\s*", ",");
                }

                if (!initialized) {
                    if (listOfItemsToBuy.length() <= 0) {
                        CupidBot.log("No items found.");
                        shutdown();
                    }
                    itemsList = mapItems(splitItemsByCommas(listOfItemsToBuy));
                    initialCount = itemsList.size();
                    initialized = true;

                    percent = config.pricePerItem().getValue();

                    if (!isRunning())
                        return;
                }

                if (!Rs2GrandExchange.isOpen()) {
                    Rs2GrandExchange.openExchange();
                }

                itemsList.forEach((itemName, quantity) -> {
                    if (!isRunning())
                        return;
                    // Try to collect items to bank to free up slots
                    if (!hasFreeSlots()) {
                        if (canFreeUpSlots()) {
                            Rs2GrandExchange.collectAllToBank();
                            CupidBot.log("Items bought from G.E. are collected to your bank.");
                        } else {
                            CupidBot.log("All G.E. slots are in use, either abort or wait until one comes available.");
                            return;
                        }
                    }

                    GrandExchangeRequest request = GrandExchangeRequest.builder()
                            .action(GrandExchangeAction.BUY)
                            .itemName(itemName)
                            .quantity(quantity)
                            .percent(percent)
                            .build();

                    boolean result = Rs2GrandExchange.processOffer(request);

                    if (!result) {
                        CupidBot.log("Could not buy '" + itemName + "'. Skipping it.");
                        Rs2GrandExchange.backToOverview();
                    }

                    itemsList.remove(itemName); // Remove from list so we don't buy the same item again
                    totalBought++;
                });

                // Loop until we bought every item
                if (totalBought < initialCount)
                    return;

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);
                CupidBot.getClientThread().runOnClientThreadOptional(() -> {
                    CupidBot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "Made with love by Acun.", "Acun", false);

                    if (config.buyQuest()) {
                        CupidBot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "Auto-buying quest items enabled! Churr bro ! - Wassupzzz", "Wassupzzz", false);
                    }

                    return null;
                });
                CupidBot.log("Finished buying.");
                shutdown();
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean canFreeUpSlots() {
        return Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer();
    }

    private String[] splitItemsByCommas(String input) {
        // Split the input string by commas
        return input.split(",");
    }

    private boolean hasFreeSlots() {
        return Rs2GrandExchange.getAvailableSlot() != null;
    }


    private Map<String, Integer> mapItems(String[] items) {
        Map<String, Integer> itemMap = new HashMap<>();

        // Process each item
        for (String item : items) {
            try {
                // Check if the item contains the quantity part
                if (item.contains("[")) {
                    // Split the item into name and quantity parts
                    String[] parts = item.split("\\[");
                    String name = parts[0].trim();
                    String quantityStr = parts[1].replace("]", "").trim();

                    // Convert the quantity to an integer
                    int quantity = Integer.parseInt(quantityStr);

                    // Store the item and its quantity in the map
                    itemMap.put(name, quantity);
                } else {
                    // If quantity is missing, default it to 1
                    itemMap.put(item.trim(), 1);
                }
            } catch (NumberFormatException e) {
                CupidBot.log(item + " has an invalid quantity. Quantity must be a number.");
                shutdown();
            }
        }

        return itemMap;
    }

    @Override
    public void shutdown() {
        initialized = false;
        initialCount = 0;
        totalBought = 0;
        itemsList.clear();
        percent = null;
        super.shutdown();
    }

    public String getItemName(int itemId) {
        ItemComposition item = CupidBot.getClientThread().runOnClientThreadOptional(() -> CupidBot.getItemManager().getItemComposition(itemId)).orElse(null);
        return item.getName();
    }

    public List<ItemRequirement> getItemRequirements() {
        return CupidBot.getClientThread().runOnClientThreadOptional(() -> getQuestHelperPlugin().getSelectedQuest().getItemRequirements()).orElse(null);
    }

    public boolean isTradeable(int itemId) {
        ItemComposition item = CupidBot.getClientThread().runOnClientThreadOptional(() -> CupidBot.getItemManager().getItemComposition(itemId)).orElse(null);
        return item.isTradeable();
    }

    protected QuestHelperPlugin getQuestHelperPlugin() {
        return (QuestHelperPlugin) CupidBot.getPluginManager().getPlugins().stream().filter(x -> x instanceof QuestHelperPlugin).findFirst().orElse(null);
    }
}