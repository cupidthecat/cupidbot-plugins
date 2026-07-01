package net.runelite.client.plugins.cupidbot.mke_wintertodt;

import net.runelite.api.TileObject;
import net.runelite.client.plugins.cupidbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.cupidbot.mke_wintertodt.enums.State;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class MKE_WintertodtScriptStateDecisionTest
{
	public static void main(String[] args) throws Exception
	{
		lateGameBurnOverridesLockedFletching();
		recentWaitingStateDoesNotImmediatelyForceBrazierLighting();
		brazierMaintenanceHonorsInitialLightingGrace();
		roundStartPriorityCanStillLightDuringInitialGrace();
	}

	private static void lateGameBurnOverridesLockedFletching() throws Exception
	{
		MKE_WintertodtScript script = new MKE_WintertodtScript();
		Object gameState = newGameState();
		setField(gameState, "hasItemsToBurn", true);
		setStatic("state", State.FLETCH_LOGS);
		setStatic("lockState", true);

		boolean shouldBurn = (boolean) invokePrivate(script, "shouldBurnLogs", gameState);

		assertTrue(shouldBurn, "Late-game burn should be selected");
		assertSame(State.BURN_LOGS, getStatic("state"));
		assertFalse((boolean) getStatic("lockState"), "Burn override should clear the stale fletch lock");
	}

	private static void recentWaitingStateDoesNotImmediatelyForceBrazierLighting() throws Exception
	{
		MKE_WintertodtScript script = new MKE_WintertodtScript();
		Object gameState = newGameState();
		setActiveRoundWithUnlitBrazier(gameState);
		setStatic("state", State.WAITING);
		setStatic("lockState", false);
		setStatic("shouldPriorizeBrazierAtStart", false);
		setStatic("lastStateChange", System.currentTimeMillis());

		boolean shouldLight = (boolean) invokePrivate(script, "shouldLightBrazier", gameState);

		assertFalse(shouldLight, "Startup cache grace should not immediately force a light click");
		assertSame(State.WAITING, getStatic("state"));
	}

	private static void brazierMaintenanceHonorsInitialLightingGrace() throws Exception
	{
		MKE_WintertodtScript script = new MKE_WintertodtScript();
		Object gameState = newGameState();
		setActiveRoundWithUnlitBrazier(gameState);
		setStatic("config", new MKE_WintertodtConfig() {});
		setStatic("state", State.WAITING);
		setStatic("lockState", false);
		setStatic("shouldPriorizeBrazierAtStart", false);
		setStatic("resetActions", false);
		setStatic("lastStateChange", System.currentTimeMillis());

		boolean handled = (boolean) invokePrivate(script, "handleBrazierMaintenance", gameState);

		assertFalse(handled, "Startup cache grace should also suppress priority relight maintenance");
		assertSame(State.WAITING, getStatic("state"));
	}

	private static void roundStartPriorityCanStillLightDuringInitialGrace() throws Exception
	{
		MKE_WintertodtScript script = new MKE_WintertodtScript();
		Object gameState = newGameState();
		setActiveRoundWithUnlitBrazier(gameState);
		setStatic("state", State.WAITING);
		setStatic("lockState", false);
		setStatic("shouldPriorizeBrazierAtStart", true);
		setStatic("lastStateChange", System.currentTimeMillis());

		boolean shouldLight = (boolean) invokePrivate(script, "shouldLightBrazier", gameState);

		assertTrue(shouldLight, "Actual round-start priority should still light the brazier");
		assertSame(State.LIGHT_BRAZIER, getStatic("state"));
	}

	private static void setActiveRoundWithUnlitBrazier(Object gameState) throws Exception
	{
		setField(gameState, "isWintertodtAlive", true);
		setField(gameState, "wintertodtHp", 50);
		setField(gameState, "needBanking", false);
		setField(gameState, "brazier", dummyTileObjectModel());
		setField(gameState, "burningBrazier", null);
	}

	private static Object newGameState() throws Exception
	{
		Class<?> gameStateClass = Class.forName(MKE_WintertodtScript.class.getName() + "$GameState");
		Constructor<?> constructor = gameStateClass.getDeclaredConstructor();
		constructor.setAccessible(true);
		return constructor.newInstance();
	}

	private static Rs2TileObjectModel dummyTileObjectModel()
	{
		TileObject tileObject = (TileObject) Proxy.newProxyInstance(
			TileObject.class.getClassLoader(),
			new Class<?>[]{TileObject.class},
			(proxy, method, args) -> defaultValue(method.getReturnType()));
		return new Rs2TileObjectModel(tileObject);
	}

	private static Object invokePrivate(MKE_WintertodtScript script, String methodName, Object gameState) throws Exception
	{
		Method method = MKE_WintertodtScript.class.getDeclaredMethod(methodName, gameState.getClass());
		method.setAccessible(true);
		return method.invoke(script, gameState);
	}

	private static void setField(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void setStatic(String name, Object value) throws Exception
	{
		Field field = MKE_WintertodtScript.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static Object getStatic(String name) throws Exception
	{
		Field field = MKE_WintertodtScript.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(null);
	}

	private static Object defaultValue(Class<?> type)
	{
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0f;
		if (type == double.class) return 0d;
		if (type == char.class) return '\0';
		return null;
	}

	private static void assertTrue(boolean value, String message)
	{
		if (!value)
		{
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean value, String message)
	{
		if (value)
		{
			throw new AssertionError(message);
		}
	}

	private static void assertSame(Object expected, Object actual)
	{
		if (expected != actual)
		{
			throw new AssertionError("Expected " + expected + " but was " + actual);
		}
	}
}
