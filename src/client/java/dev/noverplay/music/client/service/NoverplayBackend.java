package dev.noverplay.music.client.service;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.noverplay.music.client.config.NoverplayConfig;
import dev.noverplay.music.client.model.PlaylistTracksResult;
import dev.noverplay.music.client.model.SearchResult;
import dev.noverplay.music.client.model.ServiceType;
import dev.noverplay.music.client.model.TrackInfo;

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
	private final YouTubeStreamResolver youtubeStreamResolver = new YouTubeStreamResolver();
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
		return CompletableFuture.supplyAsync(() -> getService(service).searchTracks(query, offset, limit), executor);
	}

	public CompletableFuture<SearchResult> searchPlaylists(ServiceType service, String query, int offset, int limit) {
		return CompletableFuture.supplyAsync(() -> getService(service).searchPlaylists(query, offset, limit), executor);
	}

	public CompletableFuture<PlaylistTracksResult> loadPlaylist(ServiceType service, String id, int offset, int limit) {
		return CompletableFuture.supplyAsync(() -> getService(service).loadPlaylist(id, offset, limit), executor);
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
		if (track.streamUrl() != null && !track.streamUrl().isBlank()) {
			return track;
		}
		if (track.service() == ServiceType.YOUTUBE) {
			TrackInfo resolved = youtubeStreamResolver.resolve(track);
			return resolved == null ? track : resolved;
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
}
