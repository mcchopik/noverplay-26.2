package dev.noverplay.music.client.audio;

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration.ResamplingQuality;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.DefaultSoundCloudFormatHandler;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudFormatHandler;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudTrackFormat;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import dev.noverplay.music.NoverPlay;
import dev.noverplay.music.client.config.NoverplayConfig;
import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.service.TrackResolver;
import dev.noverplay.music.client.util.DebugLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoverplayPlayer {
	private static final int FRAME_BUFFER_MS = 5000;
	private final AudioPlayerManager playerManager;
	private final AudioDataFormat audioDataFormat;
	private final AudioPlayer player;
	private final LavaplayerAudioOutput output;
	private final String soundcloudClientId;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final Queue<TrackInfo> queue = new ArrayDeque<>();
	private final Deque<TrackInfo> history = new ArrayDeque<>();
	private volatile TrackInfo currentTrack;
	private volatile String playlistTitle = "";
	private volatile float volume = 0.5f;
	private TrackResolver resolver;

	public NoverplayPlayer(NoverplayConfig config) {
		this.playerManager = new DefaultAudioPlayerManager();
		this.audioDataFormat = new Pcm16AudioDataFormat(2, 48000, 960, true);
		this.soundcloudClientId = config == null ? "" : safe(config.soundcloudClientId);
		setupPlayerManager();
		this.player = playerManager.createPlayer();
		player.setFrameBufferDuration(FRAME_BUFFER_MS);
		this.player.addListener(new TrackScheduler());
		this.output = new LavaplayerAudioOutput(player, audioDataFormat);
		this.output.start();
		float initialVolume = config == null ? 0.5f : config.volume;
		setVolume(initialVolume);
		DebugLog.info("Player initialized volume=" + initialVolume);
	}

	private void setupPlayerManager() {
		playerManager.setFrameBufferDuration(FRAME_BUFFER_MS);
		playerManager.setPlayerCleanupThreshold(Long.MAX_VALUE);

		playerManager.getConfiguration().setResamplingQuality(ResamplingQuality.HIGH);
		playerManager.getConfiguration().setOpusEncodingQuality(10);
		playerManager.getConfiguration().setOutputFormat(audioDataFormat);

		YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager();
		playerManager.registerSourceManager(youtube);
		registerSoundCloud();
		AudioSourceManagers.registerRemoteSources(
			playerManager,
			com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class,
			com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager.class
		);
		AudioSourceManagers.registerLocalSource(playerManager);
		Package pkg = AudioPlayerManager.class.getPackage();
		String version = pkg == null ? null : pkg.getImplementationVersion();
		DebugLog.info("Lavaplayer version=" + (version == null ? "<unknown>" : version));
		DebugLog.info("YouTube manager=" + youtube.getClass().getName());
	}

	private void registerSoundCloud() {
		com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager manager =
			com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager.builder()
				.withFormatHandler(createSoundCloudFormatHandler())
				.build();
		if (!soundcloudClientId.isBlank()) {
			boolean applied = applySoundCloudClientId(manager, soundcloudClientId);
			DebugLog.info("SoundCloud client_id provided len=" + soundcloudClientId.length()
				+ " applied=" + applied);
		}
		playerManager.registerSourceManager(manager);
	}

	private SoundCloudFormatHandler createSoundCloudFormatHandler() {
		DefaultSoundCloudFormatHandler fallback = new DefaultSoundCloudFormatHandler();
		return new SoundCloudFormatHandler() {
			@Override
			public SoundCloudTrackFormat chooseBestFormat(List<SoundCloudTrackFormat> formats) {
				SoundCloudTrackFormat selected = pickPreferredFormat(formats);
				if (selected == null) {
					selected = fallback.chooseBestFormat(formats);
				}
				if (selected != null) {
					DebugLog.info("SoundCloud format selected protocol=" + selected.getProtocol()
						+ " mime=" + selected.getMimeType());
				}
				return selected;
			}

			@Override
			public String buildFormatIdentifier(SoundCloudTrackFormat format) {
				return fallback.buildFormatIdentifier(format);
			}

			@Override
			public com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudM3uInfo getM3uInfo(String identifier) {
				return fallback.getM3uInfo(identifier);
			}

			@Override
			public String getMp3LookupUrl(String trackId) {
				return fallback.getMp3LookupUrl(trackId);
			}
		};
	}

	private SoundCloudTrackFormat pickPreferredFormat(List<SoundCloudTrackFormat> formats) {
		SoundCloudTrackFormat match = pickFormat(formats, true, true);
		if (match != null) {
			return match;
		}
		match = pickFormat(formats, true, false);
		if (match != null) {
			return match;
		}
		return pickFormat(formats, false, true);
	}

	private SoundCloudTrackFormat pickFormat(
		List<SoundCloudTrackFormat> formats,
		boolean progressiveOnly,
		boolean mp3Only
	) {
		if (formats == null) {
			return null;
		}
		for (SoundCloudTrackFormat format : formats) {
			if (format == null) {
				continue;
			}
			String protocol = format.getProtocol();
			if (progressiveOnly && (protocol == null || !protocol.equalsIgnoreCase("progressive"))) {
				continue;
			}
			String mime = format.getMimeType();
			if (mp3Only && (mime == null || (!mime.toLowerCase().contains("mpeg") && !mime.toLowerCase().contains("mp3")))) {
				continue;
			}
			return format;
		}
		return null;
	}

	private boolean applySoundCloudClientId(
		com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager manager,
		String clientId
	) {
		try {
			// Lavaplayer doesn't expose a setter, so update the internal tracker.
			java.lang.reflect.Field trackerField = manager.getClass().getDeclaredField("clientIdTracker");
			trackerField.setAccessible(true);
			Object tracker = trackerField.get(manager);
			if (tracker == null) {
				return false;
			}
			java.lang.reflect.Field clientIdField = tracker.getClass().getDeclaredField("clientId");
			clientIdField.setAccessible(true);
			clientIdField.set(tracker, clientId);
			try {
				java.lang.reflect.Field lastUpdateField = tracker.getClass().getDeclaredField("lastClientIdUpdate");
				lastUpdateField.setAccessible(true);
				lastUpdateField.setLong(tracker, System.currentTimeMillis());
			} catch (NoSuchFieldException ignored) {
			}
			return true;
		} catch (Exception ex) {
			DebugLog.error("Failed to apply SoundCloud client_id", ex);
			return false;
		}
	}

	private String safe(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}

	public void setResolver(TrackResolver resolver) {
		this.resolver = resolver;
	}

	public AudioPlayerManager getPlayerManager() {
		return playerManager;
	}

	public void playFromList(List<TrackInfo> tracks, int index) {
		if (tracks == null || tracks.isEmpty() || index < 0 || index >= tracks.size()) {
			return;
		}
		queue.clear();
		history.clear();
		for (int i = index + 1; i < tracks.size(); i++) {
			queue.offer(tracks.get(i));
		}
		TrackInfo track = tracks.get(index);
		play(track);
	}

	public void play(TrackInfo track) {
		if (track == null) {
			return;
		}
		executor.execute(() -> {
			TrackInfo resolved = resolver != null ? resolver.resolve(track) : track;
			if (resolved == null) {
				DebugLog.warn("Track resolve returned null");
				return;
			}
			String identifier = resolved.streamUrl();
			if (identifier == null || identifier.isBlank()) {
				DebugLog.warn("Track has empty stream url: " + formatTrack(resolved));
				return;
			}
			DebugLog.info("Loading track: " + formatTrack(resolved) + " identifier=" + identifier);
			playerManager.loadItemOrdered(this, identifier, new AudioLoadResultHandler() {
				@Override
				public void trackLoaded(AudioTrack audioTrack) {
					DebugLog.info("Track loaded: " + formatTrack(resolved)
						+ " lavaplayer=" + formatTrack(audioTrack));
					startTrack(audioTrack, resolved);
				}

				@Override
				public void playlistLoaded(AudioPlaylist playlist) {
					DebugLog.info("Playlist loaded name=" + playlist.getName()
						+ " tracks=" + playlist.getTracks().size()
						+ " selected=" + formatTrack(playlist.getSelectedTrack()));
					AudioTrack first = playlist.getSelectedTrack();
					if (first == null && !playlist.getTracks().isEmpty()) {
						first = playlist.getTracks().get(0);
					}
					if (first != null) {
						DebugLog.info("Playlist resolved first track: " + formatTrack(first));
						startTrack(first, resolved);
					}
				}

				@Override
				public void noMatches() {
					DebugLog.warn("No matches for identifier=" + identifier);
				}

				@Override
				public void loadFailed(FriendlyException exception) {
					NoverPlay.LOGGER.warn("Track load failed", exception);
					DebugLog.error("Track load failed for identifier=" + identifier, exception);
				}
			});
		});
	}

	private void startTrack(AudioTrack audioTrack, TrackInfo trackInfo) {
		currentTrack = trackInfo;
		player.setPaused(false);
		player.startTrack(audioTrack, false);
		DebugLog.info("Start playback: " + formatTrack(trackInfo) + " lavaplayer=" + formatTrack(audioTrack));
	}

	public void pause() {
		player.setPaused(true);
		DebugLog.info("Player paused");
	}

	public void resume() {
		player.setPaused(false);
		DebugLog.info("Player resumed");
	}

	public void stop() {
		player.stopTrack();
		DebugLog.info("Player stopped");
	}

	public void next() {
		TrackInfo next = queue.poll();
		if (next != null) {
			if (currentTrack != null) {
				history.push(currentTrack);
			}
			DebugLog.info("Next track: " + formatTrack(next));
			play(next);
		} else {
			stop();
		}
	}

	public void previous() {
		TrackInfo prev = history.poll();
		if (prev != null) {
			DebugLog.info("Previous track: " + formatTrack(prev));
			play(prev);
		}
	}

	public void seek(double seconds) {
		AudioTrack track = player.getPlayingTrack();
		if (track == null) {
			return;
		}
		long position = Math.max(0L, (long) (seconds * 1000L));
		track.setPosition(position);
		DebugLog.info("Seek to " + position + "ms");
	}

	public void setVolume(float volume) {
		float next = Math.max(0.0f, Math.min(1.0f, volume));
		this.volume = next;
		player.setVolume(Math.round(next * 100.0f));
		DebugLog.info("Volume set " + next);
	}

	public float getVolume() {
		return volume;
	}

	public void setPlaylistTitle(String title) {
		this.playlistTitle = title == null ? "" : title;
	}

	public PlayerState snapshot() {
		AudioTrack track = player.getPlayingTrack();
		int position = 0;
		int duration = 0;
		boolean buffering = false;
		boolean playing = track != null && !player.isPaused();
		if (track != null) {
			position = (int) (track.getPosition() / 1000L);
			duration = (int) (track.getDuration() / 1000L);
			buffering = track.getState() == AudioTrackState.LOADING;
		}
		List<TrackInfo> queued = new ArrayList<>(queue);
		return new PlayerState(currentTrack, playing, buffering, position, duration, volume, queued, playlistTitle);
	}

	public void shutdown() {
		stop();
		output.stop();
		executor.shutdownNow();
		playerManager.shutdown();
		DebugLog.info("Player shutdown");
	}

	private String formatTrack(TrackInfo track) {
		if (track == null) {
			return "<null>";
		}
		return "id=" + track.trackId()
			+ " title=" + DebugLog.preview(track.title())
			+ " artist=" + DebugLog.preview(track.artist())
			+ " service=" + track.service();
	}

	private String formatTrack(AudioTrack track) {
		if (track == null) {
			return "<null>";
		}
		if (track.getInfo() == null) {
			return "<info null>";
		}
		return "id=" + track.getInfo().identifier
			+ " title=" + DebugLog.preview(track.getInfo().title)
			+ " author=" + DebugLog.preview(track.getInfo().author)
			+ " length=" + track.getInfo().length;
	}

	private class TrackScheduler extends AudioEventAdapter {
		@Override
		public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
			DebugLog.error("Track exception " + formatTrack(track), exception);
		}

		@Override
		public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
			DebugLog.info("Track ended reason=" + endReason + " track=" + formatTrack(track));
			if (endReason.mayStartNext) {
				next();
			}
		}

		@Override
		public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
			DebugLog.warn("Track stuck thresholdMs=" + thresholdMs + " track=" + formatTrack(track));
		}
	}
}
