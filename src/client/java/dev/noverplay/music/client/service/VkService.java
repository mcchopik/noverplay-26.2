package dev.noverplay.music.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.noverplay.music.client.model.PlaylistTracksResult;
import dev.noverplay.music.client.model.SearchResult;
import dev.noverplay.music.client.model.ServiceType;
import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.net.HttpHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VkService implements MusicService {
	private static final String API_BASE = "https://api.vk.com/method";
	private static final String API_VERSION = "5.131";
	private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10)";

	private String accessToken = "";

	@Override
	public ServiceType getServiceType() {
		return ServiceType.VK;
	}

	public void setAccessToken(String token) {
		this.accessToken = token == null ? "" : token.trim();
	}

	@Override
	public SearchResult searchTracks(String query, int offset, int limit) {
		if (query == null || query.isBlank()) {
			return new SearchResult(List.of(), List.of(), null);
		}
		if (accessToken.isBlank()) {
			return new SearchResult(List.of(), List.of(), "VK access token required");
		}
		try {
			JsonElement element = HttpHelper.getJson(
				API_BASE + "/audio.search",
				Map.of(
					"q", query,
					"count", String.valueOf(Math.min(Math.max(1, limit), 100)),
					"offset", String.valueOf(Math.max(0, offset)),
					"access_token", accessToken,
					"v", API_VERSION,
					"https", "1"
				),
				Map.of("User-Agent", USER_AGENT, "Accept", "application/json")
			);
			if (element == null || !element.isJsonObject()) {
				return new SearchResult(List.of(), List.of(), "VK unavailable");
			}
			JsonObject payload = element.getAsJsonObject();
			if (payload.has("error")) {
				return new SearchResult(List.of(), List.of(), "VK error");
			}
			JsonObject response = payload.getAsJsonObject("response");
			if (response == null) {
				return new SearchResult(List.of(), List.of(), null);
			}
			JsonArray items = response.getAsJsonArray("items");
			if (items == null) {
				return new SearchResult(List.of(), List.of(), null);
			}
			List<TrackInfo> tracks = new ArrayList<>();
			for (JsonElement entry : items) {
				if (!entry.isJsonObject()) {
					continue;
				}
				TrackInfo track = toTrack(entry.getAsJsonObject());
				if (track != null) {
					tracks.add(track);
				}
			}
			return new SearchResult(tracks, List.of(), null);
		} catch (Exception ignored) {
			return new SearchResult(List.of(), List.of(), "VK error");
		}
	}

	@Override
	public SearchResult searchPlaylists(String query, int offset, int limit) {
		return new SearchResult(List.of(), List.of(), "VK playlists not supported");
	}

	@Override
	public PlaylistTracksResult loadPlaylist(String playlistId, int offset, int limit) {
		return new PlaylistTracksResult(List.of(), null);
	}

	public TrackInfo refreshTrack(TrackInfo track) {
		if (track == null || accessToken.isBlank()) {
			return track;
		}
		String trackId = track.trackId();
		String[] parts = trackId == null ? new String[0] : trackId.split(":", 3);
		if (parts.length < 3) {
			return track;
		}
		try {
			String audios = parts[0] + "_" + parts[1] + "_" + parts[2];
			JsonElement element = HttpHelper.getJson(
				API_BASE + "/audio.getById",
				Map.of(
					"audios", audios,
					"access_token", accessToken,
					"v", API_VERSION,
					"https", "1"
				),
				Map.of("User-Agent", USER_AGENT, "Accept", "application/json")
			);
			if (element == null || !element.isJsonObject()) {
				return track;
			}
			JsonObject payload = element.getAsJsonObject();
			if (payload.has("error")) {
				return track;
			}
			JsonArray items = payload.getAsJsonArray("response");
			if (items == null || items.isEmpty()) {
				return track;
			}
			JsonElement first = items.get(0);
			if (!first.isJsonObject()) {
				return track;
			}
			TrackInfo refreshed = toTrack(first.getAsJsonObject());
			return refreshed == null ? track : refreshed;
		} catch (Exception ignored) {
			return track;
		}
	}

	private TrackInfo toTrack(JsonObject item) {
		if (item == null) {
			return null;
		}
		String ownerId = getString(item, "owner_id");
		String audioId = getString(item, "id");
		String accessKey = getString(item, "access_key");
		if (ownerId == null || audioId == null || accessKey == null) {
			return null;
		}
		String trackId = ownerId + ":" + audioId + ":" + accessKey;
		String title = getString(item, "title");
		String artist = getString(item, "artist");
		int duration = getInt(item, "duration");
		String url = getString(item, "url");
		return new TrackInfo(
			trackId,
			safe(title),
			safe(artist),
			duration,
			ServiceType.VK,
			null,
			url
		);
	}

	private String getString(JsonObject obj, String key) {
		if (obj == null || !obj.has(key)) {
			return null;
		}
		JsonElement element = obj.get(key);
		if (element != null && element.isJsonPrimitive()) {
			return element.getAsString();
		}
		return null;
	}

	private int getInt(JsonObject obj, String key) {
		if (obj == null || !obj.has(key)) {
			return 0;
		}
		JsonElement element = obj.get(key);
		if (element != null && element.isJsonPrimitive()) {
			try {
				return element.getAsInt();
			} catch (NumberFormatException ignored) {
				return 0;
			}
		}
		return 0;
	}

	private String safe(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}
}
