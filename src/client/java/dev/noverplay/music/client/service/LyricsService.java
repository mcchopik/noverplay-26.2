package dev.noverplay.music.client.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import dev.noverplay.music.client.net.HttpHelper;

import java.util.HashMap;
import java.util.Map;

public class LyricsService {
	private static final String BASE_URL = "https://lrclib.net/api";
	private static final String USER_AGENT = "NoverplayMod/1.0";

	public String fetchLyrics(String title, String artist, Integer duration) {
		String safeTitle = safe(title);
		String safeArtist = safe(artist);
		if (safeTitle.isEmpty()) {
			return null;
		}
		Map<String, String> headers = Map.of("User-Agent", USER_AGENT);
		Map<String, String> params = new HashMap<>();
		params.put("track_name", safeTitle);
		params.put("artist_name", safeArtist);
		if (duration != null && duration > 0) {
			params.put("duration", String.valueOf(duration));
		}
		try {
			JsonElement element = HttpHelper.getJson(BASE_URL + "/get", params, headers);
			if (element != null && element.isJsonObject()) {
				return pickLyrics(element.getAsJsonObject());
			}
		} catch (Exception ignored) {
		}
		try {
			JsonElement element = HttpHelper.getJson(BASE_URL + "/search", Map.of("q", safeTitle), headers);
			if (element != null && element.isJsonArray()) {
				JsonArray array = element.getAsJsonArray();
				for (JsonElement entry : array) {
					if (entry.isJsonObject()) {
						String lyrics = pickLyrics(entry.getAsJsonObject());
						if (lyrics != null) {
							return lyrics;
						}
					}
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private String pickLyrics(JsonObject obj) {
		if (obj == null) {
			return null;
		}
		if (obj.has("syncedLyrics") && obj.get("syncedLyrics").isJsonPrimitive()) {
			String value = obj.get("syncedLyrics").getAsString();
			if (!value.isBlank()) {
				return value;
			}
		}
		if (obj.has("plainLyrics") && obj.get("plainLyrics").isJsonPrimitive()) {
			String value = obj.get("plainLyrics").getAsString();
			if (!value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String safe(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}
}
