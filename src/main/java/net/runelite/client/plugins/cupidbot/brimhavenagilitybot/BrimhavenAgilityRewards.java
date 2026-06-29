package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;

public final class BrimhavenAgilityRewards
{
	private static final Set<Integer> REWARD_ITEM_IDS = Set.of(
		ItemID.AGILITYARENA_TICKET,
		ItemID.AGILITYARENA_TICKET_NEW,
		ItemID.AGILITYARENA_VOUCHER);

	private BrimhavenAgilityRewards()
	{
	}

	public static boolean isRewardItem(int itemId)
	{
		return REWARD_ITEM_IDS.contains(itemId);
	}

	public static Set<Integer> rewardItemIds()
	{
		return REWARD_ITEM_IDS;
	}

	public static int rewardQuantity(IntUnaryOperator quantityProvider)
	{
		return REWARD_ITEM_IDS.stream()
			.mapToInt(itemId -> quantityProvider.applyAsInt(itemId))
			.sum();
	}

	public static Set<Integer> bankRetainedItemIds(int foodItemId)
	{
		Set<Integer> retained = new HashSet<>(REWARD_ITEM_IDS);
		retained.add(ItemID.COINS);
		if (foodItemId > 0)
		{
			retained.add(foodItemId);
		}
		return Set.copyOf(retained);
	}
}
