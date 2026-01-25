package dev.noverplay.music.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.noverplay.music.client.model.PlaylistInfo;
import dev.noverplay.music.client.model.PlaylistTracksResult;
import dev.noverplay.music.client.model.SearchResult;
import dev.noverplay.music.client.model.ServiceType;
import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.net.HttpHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SoundCloudService implements MusicService {
	private static final String API_BASE = "https://api-v2.soundcloud.com";
	private static final String USER_AGENT =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

	private final SoundCloudClientIdResolver clientIdResolver;

	public SoundCloudService(SoundCloudClientIdResolver resolver) {
		this.clientIdResolver = resolver;
	}

	@Override
	public ServiceType getServiceType() {
		return ServiceType.SOUNDCLOUD;
	}

	@Override
	public SearchResult searchTracks(String query, int offset, int limit) {
		if (query == null || query.isBlank()) {
			return new SearchResult(List.of(), List.of(), null);
		}
		String clientId = clientIdResolver.getClientId();
		if (clientId == null || clientId.isBlank()) {
			return new SearchResult(List.of(), List.of(), "SoundCloud client_id required");
		}
		try {
			JsonElement element = HttpHelper.getJson(
				API_BASE + "/search/tracks",
				Map.of(
					"q", query,
					"client_id", clientId,
					"limit", String.valueOf(Math.min(Math.max(1, limit), 50)),
					"offset", String.valueOf(Math.max(0, offset))
				),
				Map.of("User-Agent", USER_AGENT, "Accept", "application/json")
			);
			if (element == null || !element.isJsonObject()) {
				return new SearchResult(List.of(), List.of(), "SoundCloud unavailable");
			}
			JsonObject payload = element.getAsJsonObject();
			JsonArray collection = payload.getAsJsonArray("collection");
			if (collection == null) {
				return new SearchResult(List.of(), List.of(), null);
			}
			List<TrackInfo> tracks = new ArrayList<>();
			for (JsonElement entry : collection) {
				if (!entry.isJsonObject()) {
					continue;
				}
				JsonObject item = entry.getAsJsonObject();
				if (item.has("kind") && !"track".equals(item.get("kind").getAsString())) {
					continue;
				}
				TrackInfo track = toTrack(item);
				if (track != null) {
					tracks.add(track);
				}
			}
			return new SearchResult(tracks, List.of(), null);
		} catch (Exception ignored) {
			return new SearchResult(List.of(), List.of(), "SoundCloud error");
		}
	}

	@Override
	public SearchResult searchPlaylists(String query, int offset, int limit) {
		if (query == null || query.isBlank()) {
			return new SearchResult(List.of(), List.of(), null);
		}
		String clientId = clientIdResolver.getClientId();
		if (clientId == null || clientId.isBlank()) {
			return new SearchResult(List.of(), List.of(), "SoundCloud client_id required");
		}
		try {
			JsonElement element = HttpHelper.getJson(
				API_BASE + "/search/playlists",
				Map.of(
					"q", query,
					"client_id", clientId,
					"limit", String.valueOf(Math.min(Math.max(1, limit), 50)),
					"offset", String.valueOf(Math.max(0, offset))
				),
				Map.of("User-Agent", USER_AGENT, "Accept", "application/json")
			);
			if (element == null || !element.isJsonObject()) {
				return new SearchResult(List.of(), List.of(), "SoundCloud unavailable");
			}
			JsonObject payload = element.getAsJsonObject();
			JsonArray collection = payload.getAsJsonArray("collection");
			if (collection == null) {
				return new SearchResult(List.of(), List.of(), null);
			}
			List<PlaylistInfo> playlists = new ArrayList<>();
			for (JsonElement entry : collection) {
				if (!entry.isJsonObject()) {
					continue;
				}
				JsonObject item = entry.getAsJsonObject();
				String id = getString(item, "id");
				if (id == null || id.isBlank()) {
					continue;
				}
				String title = getString(item, "title");
				String author = "";
				if (item.has("user") && item.get("user").isJsonObject()) {
					author = getString(item.getAsJsonObject("user"), "username");
				}
				String artwork = getString(item, "artwork_url");
				if ((artwork == null || artwork.isBlank()) && item.has("user") && item.get("user").isJsonObject()) {
					artwork = getString(item.getAsJsonObject("user"), "avatar_url");
				}
				int trackCount = getInt(item, "track_count");
				playlists.add(new PlaylistInfo(
					id,
					safe(title),
					safe(author),
					trackCount,
					upgradeCover(artwork),
					ServiceType.SOUNDCLOUD
				));
			}
			return new SearchResult(List.of(), playlists, null);
		} catch (Exception ignored) {
			return new SearchResult(List.of(), List.of(), "SoundCloud error");
		}
	}

	@Override
	public PlaylistTracksResult loadPlaylist(String playlistId, int offset, int limit) {
		if (playlistId == null || playlistId.isBlank()) {
			return new PlaylistTracksResult(List.of(), null);
		}
		String clientId = clientIdResolver.getClientId();
		if (clientId == null || clientId.isBlank()) {
			return new PlaylistTracksResult(List.of(), null);
		}
		try {
			JsonElement element = HttpHelper.getJson(
				API_BASE + "/playlists/" + playlistId,
				Map.of("client_id", clientId),
				Map.of("User-Agent", USER_AGENT, "Accept", "application/json")
			);
			if (element == null || !element.isJsonObject()) {
				return new PlaylistTracksResult(List.of(), null);
			}
			JsonObject payload = element.getAsJsonObject();
			JsonArray tracks = payload.getAsJsonArray("tracks");
			if (tracks == null) {
				return new PlaylistTracksResult(List.of(), null);
			}
			List<TrackInfo> results = new ArrayList<>();
			int from = Math.max(0, offset);
			int to = Math.min(tracks.size(), from + Math.max(1, limit));
			for (int i = from; i < to; i++) {
				JsonElement entry = tracks.get(i);
				if (!entry.isJsonObject()) {
					continue;
				}
				JsonObject item = entry.getAsJsonObject();
				JsonObject trackPayload = item.has("track") && item.get("track").isJsonObject()
					? item.getAsJsonObject("track")
					: item;
				TrackInfo track = toTrack(trackPayload);
				if (track != null) {
					results.add(track);
				}
			}
			return new PlaylistTracksResult(results, tracks.size());
		} catch (Exception ignored) {
			return new PlaylistTracksResult(List.of(), null);
		}
	}

	public void setClientId(String clientId) {
		clientIdResolver.setManualId(clientId);
	}

	public String resolveClientId() {
		return clientIdResolver.getClientId();
	}

	public TrackInfo resolveTrack(String trackId) {
		if (trackId == null || trackId.isBlank()) {
			return null;
		}
		String clientId = clientIdResolver.getClientId();
		if (clientId == null || clientId.isBlank()) {
			return null;
		}
		try {
			JsonElement element = HttpHelper.getJson(
				API_BASE + "/tracks/" + trackId,
				Map.of("client_id", clientId),
				Map.of("User-Agent", USER_AGENT, "Accept", "application/json")
			);
			if (element == null || !element.isJsonObject()) {
				return null;
			}
			return toTrack(element.getAsJsonObject());
		} catch (Exception ignored) {
			return null;
		}
	}

	private TrackInfo toTrack(JsonObject item) {
		String id = getString(item, "id");
		if (id == null || id.isBlank()) {
			return null;
		}
		String title = getString(item, "title");
		String artist = "";
		if (item.has("user") && item.get("user").isJsonObject()) {
			artist = getString(item.getAsJsonObject("user"), "username");
		}
		if ((artist == null || artist.isBlank()) && item.has("publisher_metadata") && item.get("publisher_metadata").isJsonObject()) {
			artist = getString(item.getAsJsonObject("publisher_metadata"), "artist");
		}
		int durationMs = getInt(item, "duration");
		String cover = upgradeCover(getString(item, "artwork_url"));
		if ((cover == null || cover.isBlank()) && item.has("user") && item.get("user").isJsonObject()) {
			cover = upgradeCover(getString(item.getAsJsonObject("user"), "avatar_url"));
		}
		String permalink = getString(item, "permalink_url");
		return new TrackInfo(
			id,
			safe(title),
			safe(artist),
			Math.max(0, durationMs / 1000),
			ServiceType.SOUNDCLOUD,
			cover,
			permalink
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

	private String upgradeCover(String url) {
		if (url == null) {
			return null;
		}
		return url.replace("-large", "-t500x500");
	}

	private String safe(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}
}
