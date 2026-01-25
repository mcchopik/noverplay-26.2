package dev.noverplay.music.client.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.noverplay.music.NoverPlay;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class LavaplayerAudioOutput implements Runnable {
	private final AudioPlayer player;
	private final int sampleRate;
	private final int channels;
	private Thread thread;
	private volatile boolean running;
	private SourceDataLine line;

	public LavaplayerAudioOutput(AudioPlayer player, int sampleRate, int channels) {
		this.player = player;
		this.sampleRate = sampleRate;
		this.channels = channels;
	}

	public void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this, "Noverplay-AudioOutput");
		thread.setDaemon(true);
		thread.start();
	}

	public void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
		}
		closeLine();
	}

	@Override
	public void run() {
		AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
		try {
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			line = (SourceDataLine) AudioSystem.getLine(info);
			line.open(format);
			line.start();
		} catch (LineUnavailableException e) {
			NoverPlay.LOGGER.error("Audio output unavailable", e);
			running = false;
			return;
		}

		while (running) {
			try {
				AudioFrame frame = player.provide();
				if (frame == null) {
					Thread.sleep(10);
					continue;
				}
				byte[] data = frame.getData();
				if (data != null && data.length > 0) {
					line.write(data, 0, data.length);
				}
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			} catch (Exception ex) {
				NoverPlay.LOGGER.warn("Audio output error", ex);
			}
		}
		closeLine();
	}

	private void closeLine() {
		if (line != null) {
			try {
				line.stop();
				line.flush();
				line.close();
			} catch (Exception ignored) {
			}
			line = null;
		}
	}
}
