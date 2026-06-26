package net.runelite.client.plugins.cupidbot.mess;

public class TheMessScriptTest
{
	public static void main(String[] args)
	{
		assertSuppressesShutdownInterrupt();
		assertKeepsRealTickFailuresVisible();
	}

	private static void assertSuppressesShutdownInterrupt()
	{
		RuntimeException ex = new RuntimeException(
			"Interrupted waiting for client thread",
			new InterruptedException());

		if (!TheMessScript.shouldSuppressTickFailure(ex))
		{
			throw new AssertionError("shutdown interrupt should be suppressed");
		}
	}

	private static void assertKeepsRealTickFailuresVisible()
	{
		if (TheMessScript.shouldSuppressTickFailure(new RuntimeException("walk failed")))
		{
			throw new AssertionError("non-interrupt failures must remain visible");
		}
	}
}
