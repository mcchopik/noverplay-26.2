package dev.noverplay.music.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.noverplay.music.client.model.PlaylistInfo;
import dev.noverplay.music.client.model.PlaylistTracksResult;
import dev.noverplay.music.client.model.SearchResult;
import dev.noverplay.music.client.model.ServiceType;
import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.net.HttpHelper;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class YouTubeService implements MusicService {
	private static final String YT_SEARCH_PREFIX = "ytsearch:";
	private static final String PLAYLIST_SEARCH_FILTER = "EgIQAw%3D%3D";
	private static final String VIDEO_SEARCH_FILTER = "EgIQAQ%3D%3D";
	private static final String USER_AGENT =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

	private final AudioPlayerManager playerManager;

	public YouTubeService(AudioPlayerManager playerManager) {
		this.playerManager = playerManager;
	}

	@Override
	public ServiceType getServiceType() {
		return ServiceType.YOUTUBE;
	}

	@Override
	public SearchResult searchTracks(String query, int offset, int limit) {
		if (query == null || query.isBlank()) {
			return new SearchResult(List.of(), List.of(), null);
		}
		List<TrackInfo> results = searchTracksHtml(query, offset, limit);
		if (results.isEmpty()) {
			results = searchTracksLavaplayer(query, offset, limit);
		}
		return new SearchResult(results, List.of(), null);
	}

	@Override
	public SearchResult searchPlaylists(String query, int offset, int limit) {
		if (query == null || query.isBlank()) {
			return new SearchResult(List.of(), List.of(), null);
		}
		List<PlaylistInfo> playlists = searchPlaylistsHtml(query, offset, limit);
		return new SearchResult(List.of(), playlists, null);
	}

	@Override
	public PlaylistTracksResult loadPlaylist(String playlistId, int offset, int limit) {
		if (playlistId == null || playlistId.isBlank()) {
			return new PlaylistTracksResult(List.of(), null);
		}
		String normalized = normalizePlaylistId(playlistId);
		String url = "https://www.youtube.com/playlist?list=" + normalized;
		AudioItem item = loadItem(url);
		if (!(item instanceof AudioPlaylist playlist)) {
			return new PlaylistTracksResult(List.of(), null);
		}
		List<AudioTrack> tracks = playlist.getTracks();
		if (tracks == null || tracks.isEmpty()) {
			return new PlaylistTracksResult(List.of(), null);
		}
		int from = Math.max(0, offset);
		int to = Math.min(tracks.size(), from + Math.max(1, limit));
		List<TrackInfo> results = new ArrayList<>();
		for (AudioTrack track : tracks.subList(from, to)) {
			AudioTrackInfo info = track.getInfo();
			if (info == null || info.identifier == null || info.identifier.isBlank()) {
				continue;
			}
			results.add(new TrackInfo(
				info.identifier,
				safe(info.title),
				safe(info.author),
				(int) (info.length / 1000L),
				ServiceType.YOUTUBE,
				youtubeCover(info.identifier),
				youtubeUrl(info.identifier)
			));
		}
		return new PlaylistTracksResult(results, tracks.size());
	}

	private AudioItem loadItem(String identifier) {
		CompletableFuture<AudioItem> future = new CompletableFuture<>();
		playerManager.loadItem(identifier, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack track) {
				future.complete(track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				future.complete(playlist);
			}

			@Override
			public void noMatches() {
				future.complete(null);
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				future.complete(null);
			}
		});
		try {
			return future.get(15, TimeUnit.SECONDS);
		} catch (Exception ignored) {
			return null;
		}
	}

	private List<PlaylistInfo> searchPlaylistsHtml(String query, int offset, int limit) {
		try {
			String body = HttpHelper.getText(
				"https://www.youtube.com/results",
				Map.of("search_query", query, "sp", PLAYLIST_SEARCH_FILTER, "hl", "en", "gl", "US"),
				Map.of("User-Agent", USER_AGENT, "Accept-Language", "en-US,en;q=0.9")
			);
			if (body == null || body.isBlank()) {
				return List.of();
			}
			List<String> blocks = extractRendererBlocksByKey(body, "playlistRenderer");
			blocks.addAll(extractRendererBlocksByKey(body, "gridPlaylistRenderer"));
			if (blocks.isEmpty()) {
				body = HttpHelper.getText(
					"https://www.youtube.com/results",
					Map.of("search_query", query, "hl", "en", "gl", "US"),
					Map.of("User-Agent", USER_AGENT, "Accept-Language", "en-US,en;q=0.9")
				);
				if (body == null || body.isBlank()) {
					return List.of();
				}
				blocks = extractRendererBlocksByKey(body, "playlistRenderer");
				blocks.addAll(extractRendererBlocksByKey(body, "gridPlaylistRenderer"));
				if (blocks.isEmpty()) {
					return List.of();
				}
			}
			List<PlaylistInfo> results = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			for (String block : blocks) {
				JsonElement element = JsonParser.parseString(block);
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject obj = element.getAsJsonObject();
				String playlistId = getString(obj, "playlistId");
				if (playlistId == null || playlistId.isBlank() || seen.contains(playlistId)) {
					continue;
				}
				String title = readText(obj.getAsJsonObject("title"));
				String author = readText(obj.getAsJsonObject("shortBylineText"));
				int count = parseInt(readText(obj.getAsJsonObject("videoCountText")));
				String cover = readThumbnail(obj.getAsJsonObject("thumbnail"));
				results.add(new PlaylistInfo(playlistId, safe(title), safe(author), count, cover, ServiceType.YOUTUBE));
				seen.add(playlistId);
			}
			if (results.isEmpty()) {
				return List.of();
			}
			int from = Math.max(0, offset);
			int to = Math.min(results.size(), from + Math.max(1, limit));
			return results.subList(from, to);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private List<TrackInfo> searchTracksHtml(String query, int offset, int limit) {
		try {
			String body = HttpHelper.getText(
				"https://www.youtube.com/results",
				Map.of("search_query", query, "sp", VIDEO_SEARCH_FILTER, "hl", "en", "gl", "US"),
				Map.of("User-Agent", USER_AGENT, "Accept-Language", "en-US,en;q=0.9")
			);
			if (body == null || body.isBlank()) {
				return List.of();
			}
			List<String> blocks = extractRendererBlocks(body, "\"videoRenderer\":{");
			if (blocks.isEmpty()) {
				return List.of();
			}
			List<TrackInfo> results = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			for (String block : blocks) {
				JsonElement element = JsonParser.parseString(block);
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject obj = element.getAsJsonObject();
				String videoId = getString(obj, "videoId");
				if (videoId == null || videoId.isBlank() || seen.contains(videoId)) {
					continue;
				}
				String title = readText(obj.getAsJsonObject("title"));
				String author = readText(obj.getAsJsonObject("ownerText"));
				if (author.isBlank()) {
					author = readText(obj.getAsJsonObject("shortBylineText"));
				}
				if (title.isBlank()) {
					title = "YouTube video";
				}
				if (author.isBlank()) {
					author = "YouTube";
				}
				String durationText = readText(obj.getAsJsonObject("lengthText"));
				int duration = parseDuration(durationText);
				String cover = readThumbnail(obj.getAsJsonObject("thumbnail"));
				if (cover == null || cover.isBlank()) {
					cover = youtubeCover(videoId);
				}
				results.add(new TrackInfo(
					videoId,
					safe(title),
					safe(author),
					duration,
					ServiceType.YOUTUBE,
					cover,
					youtubeUrl(videoId)
				));
				seen.add(videoId);
			}
			if (results.isEmpty()) {
				return List.of();
			}
			int from = Math.max(0, offset);
			int to = Math.min(results.size(), from + Math.max(1, limit));
			return results.subList(from, to);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private List<TrackInfo> searchTracksLavaplayer(String query, int offset, int limit) {
		AudioItem item = loadItem(YT_SEARCH_PREFIX + query);
		if (!(item instanceof AudioPlaylist playlist)) {
			return List.of();
		}
		List<AudioTrack> tracks = playlist.getTracks();
		if (tracks == null || tracks.isEmpty()) {
			return List.of();
		}
		int from = Math.max(0, offset);
		int to = Math.min(tracks.size(), from + Math.max(1, limit));
		List<TrackInfo> results = new ArrayList<>();
		for (AudioTrack track : tracks.subList(from, to)) {
			AudioTrackInfo info = track.getInfo();
			if (info == null || info.identifier == null || info.identifier.isBlank()) {
				continue;
			}
			String videoId = normalizeVideoId(info.identifier);
			if (videoId.isBlank()) {
				continue;
			}
			results.add(new TrackInfo(
				videoId,
				safe(info.title),
				safe(info.author),
				(int) (info.length / 1000L),
				ServiceType.YOUTUBE,
				youtubeCover(videoId),
				youtubeUrl(videoId)
			));
		}
		return results;
	}

	private List<String> extractRendererBlocks(String body, String marker) {
		List<String> blocks = new ArrayList<>();
		int index = 0;
		while ((index = body.indexOf(marker, index)) != -1) {
			int start = body.indexOf('{', index);
			if (start < 0) {
				break;
			}
			int end = findMatchingBrace(body, start);
			if (end < 0) {
				break;
			}
			blocks.add(body.substring(start, end + 1));
			index = end + 1;
		}
		return blocks;
	}

	private List<String> extractRendererBlocksByKey(String body, String key) {
		String marker = "\"" + key + "\"";
		List<String> blocks = new ArrayList<>();
		int index = 0;
		while ((index = body.indexOf(marker, index)) != -1) {
			int colon = body.indexOf(':', index + marker.length());
			if (colon < 0) {
				index += marker.length();
				continue;
			}
			int start = body.indexOf('{', colon);
			if (start < 0) {
				index = colon + 1;
				continue;
			}
			int end = findMatchingBrace(body, start);
			if (end < 0) {
				break;
			}
			blocks.add(body.substring(start, end + 1));
			index = end + 1;
		}
		return blocks;
	}

	private int findMatchingBrace(String body, int start) {
		int depth = 0;
		boolean inString = false;
		for (int i = start; i < body.length(); i++) {
			char ch = body.charAt(i);
			if (ch == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
				inString = !inString;
			}
			if (inString) {
				continue;
			}
			if (ch == '{') {
				depth++;
			} else if (ch == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	private String readText(JsonObject obj) {
		if (obj == null) {
			return "";
		}
		if (obj.has("simpleText") && obj.get("simpleText").isJsonPrimitive()) {
			return obj.get("simpleText").getAsString();
		}
		if (obj.has("runs") && obj.get("runs").isJsonArray()) {
			for (JsonElement entry : obj.getAsJsonArray("runs")) {
				if (entry != null && entry.isJsonObject()) {
					JsonObject run = entry.getAsJsonObject();
					if (run.has("text") && run.get("text").isJsonPrimitive()) {
						return run.get("text").getAsString();
					}
				}
				break;
			}
		}
		return "";
	}

	private String readThumbnail(JsonObject thumbnail) {
		if (thumbnail == null || !thumbnail.has("thumbnails")) {
			return null;
		}
		JsonElement element = thumbnail.get("thumbnails");
		if (!element.isJsonArray()) {
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		if (array.isEmpty()) {
			return null;
		}
		JsonElement last = array.get(array.size() - 1);
		if (last != null && last.isJsonObject()) {
			JsonObject obj = last.getAsJsonObject();
			if (obj.has("url") && obj.get("url").isJsonPrimitive()) {
				return obj.get("url").getAsString();
			}
		}
		return null;
	}

	private String getString(JsonObject obj, String key) {
		if (obj == null || !obj.has(key)) {
			return null;
		}
		JsonElement element = obj.get(key);
		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}
		return null;
	}

	private int parseInt(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		String digits = value.replaceAll("[^0-9]", "");
		if (digits.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private int parseDuration(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		String[] parts = value.trim().split(":");
		if (parts.length == 0) {
			return 0;
		}
		int total = 0;
		for (String part : parts) {
			String digits = part.replaceAll("[^0-9]", "");
			if (digits.isEmpty()) {
				return 0;
			}
			int segment = 0;
			try {
				segment = Integer.parseInt(digits);
			} catch (NumberFormatException ignored) {
				return 0;
			}
			total = total * 60 + segment;
		}
		return total;
	}

	private String safe(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}

	private String normalizePlaylistId(String playlistId) {
		String value = playlistId.trim();
		int listIndex = value.indexOf("list=");
		if (listIndex >= 0) {
			String part = value.substring(listIndex + 5);
			int amp = part.indexOf("&");
			if (amp >= 0) {
				part = part.substring(0, amp);
			}
			value = part;
		}
		if (value.startsWith("VL")) {
			value = value.substring(2);
		}
		return value;
	}

	private String youtubeUrl(String id) {
		return "https://www.youtube.com/watch?v=" + id;
	}

	private String youtubeCover(String id) {
		return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
	}

	private String normalizeVideoId(String identifier) {
		String value = identifier.trim();
		int vIndex = value.indexOf("v=");
		if (vIndex >= 0) {
			String part = value.substring(vIndex + 2);
			int amp = part.indexOf("&");
			if (amp >= 0) {
				part = part.substring(0, amp);
			}
			return part;
		}
		int shortIndex = value.indexOf("youtu.be/");
		if (shortIndex >= 0) {
			String part = value.substring(shortIndex + 9);
			int amp = part.indexOf("?");
			if (amp >= 0) {
				part = part.substring(0, amp);
			}
			return part;
		}
		return value;
	}
}
