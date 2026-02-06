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
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import dev.lavalink.youtube.AllClientsFailedException;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidMusic;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.Ios;
import dev.lavalink.youtube.clients.MWeb;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import dev.lavalink.youtube.clients.skeleton.Client;
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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoverplayPlayer {
	private static final int FRAME_BUFFER_MS = 200;
	private static final long YOUTUBE_STREAM_RETRY_THRESHOLD_MS = 15000L;
	private static final AtomicBoolean LOGIN_HINT_SHOWN = new AtomicBoolean(false);
	private static final long DEFAULT_OAUTH_POLL_INTERVAL_MS = 5000L;
	private final AudioPlayerManager playerManager;
	private YoutubeAudioSourceManager youtube;
	private final AudioDataFormat audioDataFormat;
	private final boolean bigEndian;
	private final AudioPlayer player;
	private final LavaplayerAudioOutput output;
	private final NoverplayConfig config;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final Queue<TrackInfo> queue = new ArrayDeque<>();
	private final Deque<TrackInfo> history = new ArrayDeque<>();
	private volatile AudioTrack preloadedTrack;
	private volatile TrackInfo preloadedInfo;
	private volatile TrackInfo currentTrack;
	private volatile String playlistTitle = "";
	private volatile float volume = 0.5f;
	private TrackResolver resolver;

	public NoverplayPlayer(NoverplayConfig config) {
		System.setProperty("java.net.preferIPv4Stack", "true");
		this.config = config == null ? new NoverplayConfig() : config;
		this.playerManager = new DefaultAudioPlayerManager();
		FormatChoice choice = selectAudioFormat();
		this.audioDataFormat = choice.dataFormat;
		this.bigEndian = choice.bigEndian;
		setupPlayerManager(this.config);
		this.player = playerManager.createPlayer();
		player.setFrameBufferDuration(FRAME_BUFFER_MS);
		this.player.addListener(new TrackScheduler());
		this.output = new LavaplayerAudioOutput(player, audioDataFormat, bigEndian);
		this.output.start();
		float initialVolume = config == null ? 0.5f : config.volume;
		setVolume(initialVolume);
		DebugLog.info("Player initialized volume=" + initialVolume);
	}

	private void setupPlayerManager(NoverplayConfig config) {
		playerManager.setFrameBufferDuration(FRAME_BUFFER_MS);
		playerManager.setPlayerCleanupThreshold(Long.MAX_VALUE);
		dev.lavalink.youtube.track.YoutubeAudioTrack.BAD_STREAM_POSITION_THRESHOLD_MS = YOUTUBE_STREAM_RETRY_THRESHOLD_MS;

		playerManager.getConfiguration().setResamplingQuality(ResamplingQuality.HIGH);
		playerManager.getConfiguration().setOpusEncodingQuality(10);
		playerManager.getConfiguration().setOutputFormat(audioDataFormat);

		youtube = buildYoutubeSource(config);
		playerManager.registerSourceManager(youtube);
		AudioSourceManagers.registerRemoteSources(
			playerManager,
			com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
		);
		AudioSourceManagers.registerLocalSource(playerManager);
		Package pkg = AudioPlayerManager.class.getPackage();
		String version = pkg == null ? null : pkg.getImplementationVersion();
		DebugLog.info("Lavaplayer version=" + (version == null ? "<unknown>" : version));
		DebugLog.info("YouTube manager=" + youtube.getClass().getName());
	}

	public void startYoutubeOauthFlow(String refreshToken) {
		if (youtube == null) {
			DebugLog.warn("YouTube manager not initialized");
			return;
		}
		String token = trimToNull(refreshToken);
		if (token != null) {
			youtube.useOauth2(token, true);
			DebugLog.info("YouTube OAuth refresh token applied");
		} else {
			DebugLog.warn("YouTube OAuth refresh token missing.");
		}
	}

	private YoutubeAudioSourceManager buildYoutubeSource(NoverplayConfig config) {
		List<Client> clients = new ArrayList<>();
		boolean oauthEnabled = config != null && config.youtubeOauthEnabled;
		YoutubeSourceOptions options = new YoutubeSourceOptions();

		if (config != null) {
			String cipherUrl = trimToNull(config.youtubeCipherUrl);
			if (cipherUrl != null) {
				String cipherPassword = trimToNull(config.youtubeCipherPassword);
				String cipherUserAgent = trimToNull(config.youtubeCipherUserAgent);
				options.setRemoteCipher(cipherUrl, cipherPassword, cipherUserAgent);
				DebugLog.info("YouTube remote cipher configured");
			}
		}

		// Metadata loading and playback use separate client groups.
		// This keeps URL resolution stable while preferring non-script playback clients first.
		ClientOptions metadataOnly = metadataClientOptions();
		clients.add(new Web(metadataOnly.copy()));
		clients.add(new WebEmbedded(metadataOnly.copy()));
		clients.add(new MWeb(metadataOnly.copy()));

		ClientOptions playbackOnly = playbackClientOptions();
		clients.add(new Ios(playbackOnly.copy()));
		clients.add(new AndroidVr(playbackOnly.copy()));
		clients.add(new AndroidMusic(playbackOnly.copy()));
		clients.add(new Web(playbackOnly.copy()));
		clients.add(new WebEmbedded(playbackOnly.copy()));

		YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(options, clients.toArray(new Client[0]));

		if (config != null) {
			String poToken = trimToNull(config.youtubePoToken);
			String visitorData = trimToNull(config.youtubeVisitorData);
			if (poToken != null && visitorData != null) {
				Web.setPoTokenAndVisitorData(poToken, visitorData);
				WebEmbedded.setPoTokenAndVisitorData(poToken, visitorData);
				DebugLog.info("YouTube PO token configured");
			}

			String refreshToken = trimToNull(config.youtubeRefreshToken);
			if (refreshToken != null) {
				youtube.useOauth2(refreshToken, true);
				DebugLog.info("YouTube OAuth refresh token configured");
			} else if (oauthEnabled) {
				DebugLog.warn("YouTube OAuth enabled without refresh token; add token in noverplay config.");
			}
		}

		return youtube;
	}

	private ClientOptions metadataClientOptions() {
		ClientOptions options = ClientOptions.DEFAULT.copy();
		options.setPlayback(false);
		return options;
	}

	private ClientOptions playbackClientOptions() {
		ClientOptions options = ClientOptions.DEFAULT.copy();
		options.setSearching(false);
		return options;
	}

	public YoutubeOauthDevice requestYoutubeOauthDeviceCode() {
		if (youtube == null) {
			DebugLog.warn("YouTube manager not initialized");
			return null;
		}
		JsonBrowser response = youtube.getOauth2Handler().fetchDeviceCode();
		String verificationUrl = firstNonBlank(
			textOrNull(response, "verification_url"),
			textOrNull(response, "verification_uri")
		);
		String verificationUrlComplete = firstNonBlank(
			textOrNull(response, "verification_url_complete"),
			textOrNull(response, "verification_uri_complete")
		);
		String userCode = textOrNull(response, "user_code");
		String deviceCode = textOrNull(response, "device_code");
		long intervalMs = response.get("interval").asLong(0) * 1000L;
		if (intervalMs <= 0) {
			intervalMs = DEFAULT_OAUTH_POLL_INTERVAL_MS;
		}
		long expiresMs = response.get("expires_in").asLong(0) * 1000L;
		if (verificationUrl == null || userCode == null || deviceCode == null) {
			DebugLog.warn("YouTube OAuth device code response missing fields: " + response.format());
			return null;
		}
		String openUrl = verificationUrlComplete != null ? verificationUrlComplete : verificationUrl;
		return new YoutubeOauthDevice(openUrl, verificationUrl, userCode, deviceCode, intervalMs, expiresMs);
	}

	public YoutubeOauthTokenResult fetchYoutubeOauthToken(String deviceCode) {
		if (youtube == null) {
			return YoutubeOauthTokenResult.error("YouTube manager not initialized");
		}
		if (deviceCode == null || deviceCode.isBlank()) {
			return YoutubeOauthTokenResult.error("Device code is empty");
		}
		try {
			JsonBrowser response = youtube.getOauth2Handler().fetchRefreshToken(deviceCode);
			if (!response.get("error").isNull()) {
				String error = response.get("error").text();
				return YoutubeOauthTokenResult.fromError(error);
			}
			String refreshToken = response.get("refresh_token").text();
			if (refreshToken == null || refreshToken.isBlank()) {
				return YoutubeOauthTokenResult.error("Refresh token missing");
			}
			return YoutubeOauthTokenResult.success(refreshToken);
		} catch (Exception ex) {
			return YoutubeOauthTokenResult.error(ex.getMessage());
		}
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String textOrNull(JsonBrowser browser, String field) {
		if (browser == null) {
			return null;
		}
		JsonBrowser value = browser.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.text();
		return text == null || text.isBlank() ? null : text;
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	public record YoutubeOauthDevice(
		String openUrl,
		String verificationUrl,
		String userCode,
		String deviceCode,
		long pollIntervalMs,
		long expiresInMs
	) {}

	public record YoutubeOauthTokenResult(Status status, String refreshToken, String message) {
		public enum Status {
			PENDING,
			SLOW_DOWN,
			EXPIRED,
			DENIED,
			SUCCESS,
			ERROR
		}

		public static YoutubeOauthTokenResult pending() {
			return new YoutubeOauthTokenResult(Status.PENDING, null, null);
		}

		public static YoutubeOauthTokenResult slowDown() {
			return new YoutubeOauthTokenResult(Status.SLOW_DOWN, null, null);
		}

		public static YoutubeOauthTokenResult expired() {
			return new YoutubeOauthTokenResult(Status.EXPIRED, null, null);
		}

		public static YoutubeOauthTokenResult denied() {
			return new YoutubeOauthTokenResult(Status.DENIED, null, null);
		}

		public static YoutubeOauthTokenResult success(String refreshToken) {
			return new YoutubeOauthTokenResult(Status.SUCCESS, refreshToken, null);
		}

		public static YoutubeOauthTokenResult error(String message) {
			return new YoutubeOauthTokenResult(Status.ERROR, null, message);
		}

		public static YoutubeOauthTokenResult fromError(String error) {
			if (error == null) {
				return error("unknown");
			}
			return switch (error) {
				case "authorization_pending" -> pending();
				case "slow_down" -> slowDown();
				case "expired_token" -> expired();
				case "access_denied" -> denied();
				default -> error(error);
			};
		}
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
		preloadedTrack = null;
		preloadedInfo = null;
		for (int i = index + 1; i < tracks.size(); i++) {
			queue.offer(tracks.get(i));
		}
		TrackInfo track = tracks.get(index);
		play(track);
	}

	private static FormatChoice selectAudioFormat() {
		int[] sampleRates = new int[]{48000, 44100, 32000, 24000};
		int[] channels = new int[]{2, 1};
		boolean[] endians = new boolean[]{false, true};

		for (int sampleRate : sampleRates) {
			int frameDuration = Math.max(1, sampleRate / 50);
			for (int channelCount : channels) {
				for (boolean endian : endians) {
					if (canOpenLine(sampleRate, channelCount, endian)) {
						return new FormatChoice(
							new Pcm16AudioDataFormat(channelCount, sampleRate, frameDuration, endian),
							endian
						);
					}
				}
			}
		}
		int fallbackRate = 48000;
		int fallbackFrameDuration = Math.max(1, fallbackRate / 50);
		return new FormatChoice(new Pcm16AudioDataFormat(2, fallbackRate, fallbackFrameDuration, false), false);
	}

	private record FormatChoice(AudioDataFormat dataFormat, boolean bigEndian) {}

	private static boolean canOpenLine(int sampleRate, int channels, boolean bigEndian) {
		AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, bigEndian);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		SourceDataLine line = null;
		try {
			line = (SourceDataLine) AudioSystem.getLine(info);
			line.open(format);
			return true;
		} catch (Exception ignored) {
			return false;
		} finally {
			if (line != null) {
				try {
					line.stop();
					line.flush();
					line.close();
				} catch (Exception ignored) {
				}
			}
		}
	}

	public void play(TrackInfo track) {
		if (track == null) {
			return;
		}
		preloadedTrack = null;
		preloadedInfo = null;
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
					maybeLogLoginHint(exception);
				}
			});
		});
	}

	private void maybeLogLoginHint(Throwable error) {
		if (!isLoginRequired(error)) {
			return;
		}
		if (!LOGIN_HINT_SHOWN.compareAndSet(false, true)) {
			return;
		}
		DebugLog.warn("YouTube requires login for playback.");
		DebugLog.warn("Set OAuth fields in noverplay config (refresh token / PO token / visitor data).");
	}

	private boolean isLoginRequired(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current instanceof AllClientsFailedException) {
				String message = current.getMessage();
				if (message != null && message.toLowerCase(Locale.ROOT).contains("login")) {
					return true;
				}
			}
			String message = current.getMessage();
			if (message != null && message.toLowerCase(Locale.ROOT).contains("requires login")) {
				return true;
			}
		}
		return false;
	}

	private void startTrack(AudioTrack audioTrack, TrackInfo trackInfo) {
		currentTrack = trackInfo;
		player.setPaused(false);
		player.startTrack(audioTrack, false);
		DebugLog.info("Start playback: " + formatTrack(trackInfo) + " lavaplayer=" + formatTrack(audioTrack));
		preloadNext();
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
			if (preloadedInfo != null && preloadedTrack != null
				&& preloadedInfo.trackId() != null
				&& preloadedInfo.trackId().equals(next.trackId())) {
				AudioTrack start = preloadedTrack;
				preloadedTrack = null;
				preloadedInfo = null;
				try {
					startTrack(start.makeClone(), next);
				} catch (Exception ignored) {
					startTrack(start, next);
				}
			} else {
				play(next);
			}
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

	private void preloadNext() {
		TrackInfo next = queue.peek();
		if (next == null || resolver == null) {
			preloadedTrack = null;
			preloadedInfo = null;
			return;
		}
		executor.execute(() -> {
			TrackInfo resolved = resolver.resolve(next);
			if (resolved == null || resolved.streamUrl() == null || resolved.streamUrl().isBlank()) {
				return;
			}
			String identifier = resolved.streamUrl();
			playerManager.loadItemOrdered(this, identifier, new AudioLoadResultHandler() {
				@Override
				public void trackLoaded(AudioTrack audioTrack) {
					preloadedInfo = resolved;
					preloadedTrack = audioTrack;
					DebugLog.info("Preloaded next track: " + formatTrack(resolved));
				}

				@Override
				public void playlistLoaded(AudioPlaylist playlist) {
					AudioTrack first = playlist.getSelectedTrack();
					if (first == null && !playlist.getTracks().isEmpty()) {
						first = playlist.getTracks().get(0);
					}
					if (first != null) {
						preloadedInfo = resolved;
						preloadedTrack = first;
						DebugLog.info("Preloaded next track: " + formatTrack(resolved));
					}
				}

				@Override
				public void noMatches() {
				}

				@Override
				public void loadFailed(FriendlyException exception) {
				}
			});
		});
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
