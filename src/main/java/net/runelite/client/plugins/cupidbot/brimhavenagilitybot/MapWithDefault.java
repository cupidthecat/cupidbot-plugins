package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.HashMap;

public class MapWithDefault<K, V> extends HashMap<K, V>
{
	private final V defaultValue;

	public MapWithDefault(V defaultValue)
	{
		this.defaultValue = defaultValue;
	}

	@Override
	public V get(Object key)
	{
		return super.getOrDefault(key, defaultValue);
	}
}
