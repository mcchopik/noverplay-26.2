package dev.noverplay.music.client.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import dev.noverplay.music.NoverPlay;
import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.service.TrackResolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoverplayPlayer {
	private final AudioPlayerManager playerManager;
	private final AudioPlayer player;
	private final LavaplayerAudioOutput output;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final Queue<TrackInfo> queue = new ArrayDeque<>();
	private final Deque<TrackInfo> history = new ArrayDeque<>();
	private volatile TrackInfo currentTrack;
	private volatile String playlistTitle = "";
	private volatile float volume = 0.5f;
	private TrackResolver resolver;

	public NoverplayPlayer(float initialVolume) {
		this.playerManager = new DefaultAudioPlayerManager();
		AudioSourceManagers.registerRemoteSources(playerManager);
		AudioSourceManagers.registerLocalSource(playerManager);
		this.player = playerManager.createPlayer();
		this.player.addListener(new TrackScheduler());
		this.output = new LavaplayerAudioOutput(player, 48000, 2);
		this.output.start();
		setVolume(initialVolume);
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
				return;
			}
			String identifier = resolved.streamUrl();
			if (identifier == null || identifier.isBlank()) {
				return;
			}
			playerManager.loadItemOrdered(this, identifier, new AudioLoadResultHandler() {
				@Override
				public void trackLoaded(AudioTrack audioTrack) {
					startTrack(audioTrack, resolved);
				}

				@Override
				public void playlistLoaded(AudioPlaylist playlist) {
					AudioTrack first = playlist.getSelectedTrack();
					if (first == null && !playlist.getTracks().isEmpty()) {
						first = playlist.getTracks().get(0);
					}
					if (first != null) {
						startTrack(first, resolved);
					}
				}

				@Override
				public void noMatches() {
				}

				@Override
				public void loadFailed(FriendlyException exception) {
					NoverPlay.LOGGER.warn("Track load failed", exception);
				}
			});
		});
	}

	private void startTrack(AudioTrack audioTrack, TrackInfo trackInfo) {
		currentTrack = trackInfo;
		player.setPaused(false);
		player.startTrack(audioTrack, false);
	}

	public void pause() {
		player.setPaused(true);
	}

	public void resume() {
		player.setPaused(false);
	}

	public void stop() {
		player.stopTrack();
	}

	public void next() {
		TrackInfo next = queue.poll();
		if (next != null) {
			if (currentTrack != null) {
				history.push(currentTrack);
			}
			play(next);
		} else {
			stop();
		}
	}

	public void previous() {
		TrackInfo prev = history.poll();
		if (prev != null) {
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
	}

	public void setVolume(float volume) {
		float next = Math.max(0.0f, Math.min(1.0f, volume));
		this.volume = next;
		player.setVolume(Math.round(next * 100.0f));
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
	}

	private class TrackScheduler extends AudioEventAdapter {
		@Override
		public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
			if (endReason.mayStartNext) {
				next();
			}
		}
	}
}
