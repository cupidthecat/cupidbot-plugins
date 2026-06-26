package net.runelite.client.plugins.cupidbot.chatbot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class OpenAiService
{
	private static final String API_URL = "https://api.openai.com/v1/chat/completions";
	private static final Gson GSON = new Gson();

	public static class ChatMessage
	{
		private final String role;
		private final String content;

		public ChatMessage(String role, String content)
		{
			this.role = role;
			this.content = content;
		}

		public String getRole()
		{
			return role;
		}

		public String getContent()
		{
			return content;
		}
	}

	public static String getChatCompletion(String apiKey, String model, List<ChatMessage> messages,
		int maxTokens, double temperature)
	{
		try
		{
			JsonObject requestBody = new JsonObject();
			requestBody.addProperty("model", model);
			requestBody.addProperty("max_tokens", maxTokens);
			requestBody.addProperty("temperature", temperature);

			JsonArray messagesArray = new JsonArray();
			for (ChatMessage msg : messages)
			{
				JsonObject msgObj = new JsonObject();
				msgObj.addProperty("role", msg.getRole());
				msgObj.addProperty("content", msg.getContent());
				messagesArray.add(msgObj);
			}
			requestBody.add("messages", messagesArray);

			String jsonBody = GSON.toJson(requestBody);

			URL url = new URL(API_URL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Authorization", "Bearer " + apiKey);
			conn.setDoOutput(true);
			conn.setConnectTimeout(15000);
			conn.setReadTimeout(30000);

			try (OutputStream os = conn.getOutputStream())
			{
				os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
			}

			int responseCode = conn.getResponseCode();
			if (responseCode != 200)
			{
				try (BufferedReader br = new BufferedReader(
					new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8)))
				{
					StringBuilder errorBody = new StringBuilder();
					String line;
					while ((line = br.readLine()) != null)
					{
						errorBody.append(line);
					}
					log.error("OpenAI API error (HTTP {}): {}", responseCode, errorBody.toString());
				}
				return null;
			}

			try (BufferedReader br = new BufferedReader(
				new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)))
			{
				StringBuilder responseBody = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null)
				{
					responseBody.append(line);
				}

				JsonObject response = new JsonParser().parse(responseBody.toString()).getAsJsonObject();
				JsonArray choices = response.getAsJsonArray("choices");
				if (choices != null && choices.size() > 0)
				{
					JsonObject firstChoice = choices.get(0).getAsJsonObject();
					JsonObject message = firstChoice.getAsJsonObject("message");
					return message.get("content").getAsString().trim();
				}
			}

			return null;
		}
		catch (Exception e)
		{
			log.error("Failed to call OpenAI API: ", e);
			return null;
		}
	}
}
