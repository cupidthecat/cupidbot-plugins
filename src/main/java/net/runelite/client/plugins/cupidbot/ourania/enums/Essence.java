package net.runelite.client.plugins.cupidbot.ourania.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum Essence
{
	PURE_ESSENCE(ItemID.BLANKRUNE_HIGH),
	DAEYALT_ESSENCE(ItemID.BLANKRUNE_DAEYALT);

	private final int itemId;
}
