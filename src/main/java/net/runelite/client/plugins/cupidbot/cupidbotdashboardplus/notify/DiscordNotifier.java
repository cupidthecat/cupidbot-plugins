package net.runelite.client.plugins.cupidbot.cupidbotdashboardplus.notify;

import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Async Discord webhook sender.
 */
@Slf4j
public class DiscordNotifier
{
	private static final int CONNECT_TIMEOUT_MS = 4_000;
	private static final int READ_TIMEOUT_MS = 4_000;
	private static final int MAX_BODY_LENGTH = 1900;

	private volatile String webhookUrl = "";
	private ScheduledExecutorService executor;

	public synchronized void setWebhookUrl(String url)
	{
		this.webhookUrl = url == null ? "" : url.trim();
	}

	public boolean isConfigured()
	{
		String u = webhookUrl;
		if (u == null || u.isEmpty())
		{
			return false;
		}
		try
		{
			URI uri = URI.create(u);
			if (!"https".equalsIgnoreCase(uri.getScheme()))
			{
				return false;
			}
			String host = uri.getHost();
			if (host == null)
			{
				return false;
			}
			host = host.toLowerCase();
			boolean validHost = host.equals("discord.com") || host.equals("discordapp.com")
				|| host.endsWith(".discord.com") || host.endsWith(".discordapp.com");
			String path = uri.getPath();
			return validHost && path != null && path.startsWith("/api/webhooks/");
		}
		catch (Throwable t)
		{
			return false;
		}
	}

	public synchronized void start()
	{
		if (executor != null)
		{
			return;
		}
		executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CupidBotDashboardPlus-Discord");
			t.setDaemon(true);
			return t;
		});
	}

	public synchronized void shutdown()
	{
		if (executor != null)
		{
			executor.shutdownNow();
			try
			{
				executor.awaitTermination(1, TimeUnit.SECONDS);
			}
			catch (InterruptedException ignored)
			{
				Thread.currentThread().interrupt();
			}
			executor = null;
		}
	}

	public void send(String message)
	{
		if (!isConfigured() || message == null || message.isEmpty())
		{
			return;
		}
		if (executor == null)
		{
			start();
		}

		final String url = webhookUrl;
		final String body = truncate(message, MAX_BODY_LENGTH);
		try
		{
			executor.submit(() -> postSafely(url, body));
		}
		catch (Throwable t)
		{
			log.debug("Discord submit failed: {}", t.getMessage());
		}
	}

	private static void postSafely(String url, String content)
	{
		HttpURLConnection conn = null;
		try
		{
			URI uri = URI.create(url);
			URL u = uri.toURL();
			conn = (HttpURLConnection) u.openConnection();
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			conn.setRequestProperty("User-Agent", "CupidBotDashboardPlus");

			String payload = "{\"content\":\"" + jsonEscape(content) + "\"}";
			byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
			try (OutputStream os = conn.getOutputStream())
			{
				os.write(bytes);
			}

			int code = conn.getResponseCode();
			if (code < 200 || code >= 300)
			{
				log.debug("Discord webhook returned {} (not logging URL or body)", code);
			}
		}
		catch (Throwable t)
		{
			log.debug("Discord webhook send failed: {}", t.getClass().getSimpleName());
		}
		finally
		{
			if (conn != null)
			{
				conn.disconnect();
			}
		}
	}

	private static String jsonEscape(String s)
	{
		if (s == null)
		{
			return "";
		}
		StringBuilder b = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			switch (c)
			{
				case '"':
					b.append("\\\"");
					break;
				case '\\':
					b.append("\\\\");
					break;
				case '\n':
					b.append("\\n");
					break;
				case '\r':
					b.append("\\r");
					break;
				case '\t':
					b.append("\\t");
					break;
				default:
					if (c < 0x20)
					{
						b.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						b.append(c);
					}
			}
		}
		return b.toString();
	}

	private static String truncate(String s, int max)
	{
		if (s == null)
		{
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "...";
	}
}
