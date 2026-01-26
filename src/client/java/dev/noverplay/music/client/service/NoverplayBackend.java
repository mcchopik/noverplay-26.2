package dev.noverplay.music.client.service;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.noverplay.music.client.config.NoverplayConfig;
import dev.noverplay.music.client.model.PlaylistInfo;
import dev.noverplay.music.client.model.PlaylistTracksResult;
import dev.noverplay.music.client.model.SearchResult;
import dev.noverplay.music.client.model.ServiceType;
import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.util.DebugLog;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoverplayBackend implements TrackResolver {
	private final Map<ServiceType, MusicService> services = new EnumMap<>(ServiceType.class);
	private final LyricsService lyricsService = new LyricsService();
	private final SoundCloudService soundCloudService;
	private final VkService vkService;
	private final ExecutorService executor = Executors.newFixedThreadPool(4);

	public NoverplayBackend(AudioPlayerManager playerManager, NoverplayConfig config) {
		SoundCloudClientIdResolver resolver =
			new SoundCloudClientIdResolver(config.soundcloudClientId, 21600);
		this.soundCloudService = new SoundCloudService(resolver);
		this.vkService = new VkService();
		this.vkService.setAccessToken(config.vkAccessToken);
		services.put(ServiceType.YOUTUBE, new YouTubeService(playerManager));
		services.put(ServiceType.SOUNDCLOUD, soundCloudService);
		services.put(ServiceType.VK, vkService);
	}

	public CompletableFuture<SearchResult> searchTracks(ServiceType service, String query, int offset, int limit) {
		DebugLog.info("Search tracks request service=" + service + " query=" + DebugLog.preview(query)
			+ " offset=" + offset + " limit=" + limit);
		return CompletableFuture.supplyAsync(() -> {
			SearchResult result = getService(service).searchTracks(query, offset, limit);
			int count = result.tracks() == null ? 0 : result.tracks().size();
			DebugLog.info("Search tracks result count=" + count + " notice=" + result.notice());
			logTracks(result.tracks());
			return result;
		}, executor);
	}

	public CompletableFuture<SearchResult> searchPlaylists(ServiceType service, String query, int offset, int limit) {
		DebugLog.info("Search playlists request service=" + service + " query=" + DebugLog.preview(query)
			+ " offset=" + offset + " limit=" + limit);
		return CompletableFuture.supplyAsync(() -> {
			SearchResult result = getService(service).searchPlaylists(query, offset, limit);
			int count = result.playlists() == null ? 0 : result.playlists().size();
			DebugLog.info("Search playlists result count=" + count + " notice=" + result.notice());
			logPlaylists(result.playlists());
			return result;
		}, executor);
	}

	public CompletableFuture<PlaylistTracksResult> loadPlaylist(ServiceType service, String id, int offset, int limit) {
		DebugLog.info("Load playlist request service=" + service + " id=" + DebugLog.preview(id)
			+ " offset=" + offset + " limit=" + limit);
		return CompletableFuture.supplyAsync(() -> {
			PlaylistTracksResult result = getService(service).loadPlaylist(id, offset, limit);
			int count = result.tracks() == null ? 0 : result.tracks().size();
			DebugLog.info("Load playlist result count=" + count + " total=" + result.totalCount());
			logTracks(result.tracks());
			return result;
		}, executor);
	}

	public CompletableFuture<String> fetchLyrics(TrackInfo track) {
		if (track == null) {
			return CompletableFuture.completedFuture(null);
		}
		return CompletableFuture.supplyAsync(
			() -> lyricsService.fetchLyrics(track.title(), track.artist(), track.duration()),
			executor
		);
	}

	public void setVkAccessToken(String token) {
		vkService.setAccessToken(token);
	}

	public void setSoundCloudClientId(String clientId) {
		soundCloudService.setClientId(clientId);
	}

	public String resolveSoundCloudClientId() {
		return soundCloudService.resolveClientId();
	}

	@Override
	public TrackInfo resolve(TrackInfo track) {
		if (track == null) {
			return null;
		}
		if (track.service() == ServiceType.YOUTUBE) {
			String stream = track.streamUrl();
			if (stream == null || stream.isBlank()) {
				TrackInfo updated = track.withStreamUrl("https://www.youtube.com/watch?v=" + track.trackId());
				DebugLog.info("Resolved YouTube stream url: " + updated.streamUrl());
				return updated;
			}
			return track;
		}
		if (track.streamUrl() != null && !track.streamUrl().isBlank()) {
			return track;
		}
		if (track.service() == ServiceType.VK) {
			return vkService.refreshTrack(track);
		}
		if (track.service() == ServiceType.SOUNDCLOUD) {
			TrackInfo resolved = soundCloudService.resolveTrack(track.trackId());
			return resolved == null ? track : resolved;
		}
		return track;
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	private MusicService getService(ServiceType service) {
		MusicService resolved = services.get(service);
		if (resolved != null) {
			return resolved;
		}
		return services.get(ServiceType.YOUTUBE);
	}

	private void logTracks(Iterable<TrackInfo> tracks) {
		if (tracks == null) {
			return;
		}
		int index = 0;
		for (TrackInfo track : tracks) {
			if (track == null) {
				continue;
			}
			DebugLog.info("Track[" + index++ + "] id=" + track.trackId()
				+ " title=" + DebugLog.preview(track.title())
				+ " artist=" + DebugLog.preview(track.artist())
				+ " duration=" + track.duration()
				+ " service=" + track.service()
				+ " url=" + DebugLog.preview(track.streamUrl()));
		}
	}

	private void logPlaylists(Iterable<PlaylistInfo> playlists) {
		if (playlists == null) {
			return;
		}
		int index = 0;
		for (PlaylistInfo playlist : playlists) {
			if (playlist == null) {
				continue;
			}
			DebugLog.info("Playlist[" + index++ + "] id=" + DebugLog.preview(playlist.playlistId())
				+ " title=" + DebugLog.preview(playlist.title())
				+ " author=" + DebugLog.preview(playlist.author())
				+ " tracks=" + playlist.trackCount()
				+ " service=" + playlist.service());
		}
	}
}
