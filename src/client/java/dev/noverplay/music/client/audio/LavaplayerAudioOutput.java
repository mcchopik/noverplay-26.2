package dev.noverplay.music.client.audio;

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormatTools;
import com.sedmelluq.discord.lavaplayer.format.AudioPlayerInputStream;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import dev.noverplay.music.NoverPlay;
import dev.noverplay.music.client.util.DebugLog;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.InterruptedIOException;

public class LavaplayerAudioOutput implements Runnable {
	private static final int LINE_BUFFER_FRAMES = 5;

	private final AudioPlayer player;
	private final AudioDataFormat dataFormat;
	private Thread thread;
	private volatile boolean running;
	private SourceDataLine line;

	public LavaplayerAudioOutput(AudioPlayer player, AudioDataFormat dataFormat) {
		this.player = player;
		this.dataFormat = dataFormat;
	}

	public void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this, "Noverplay-AudioOutput");
		thread.setPriority(Thread.NORM_PRIORITY + 1);
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
		AudioFormat format = AudioDataFormatTools.toAudioFormat(dataFormat);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		AudioInputStream stream = AudioPlayerInputStream.createStream(
			player,
			dataFormat,
			dataFormat.frameDuration(),
			false
		);
		byte[] buffer = new byte[dataFormat.chunkSampleCount * dataFormat.channelCount * 2];
		long frameDuration = dataFormat.frameDuration();

		while (running) {
			try {
				if (line == null || !line.isOpen()) {
					closeLine();
					if (!openLine(info, format)) {
						sleepQuiet(500);
						continue;
					}
				}
				if (!player.isPaused()) {
					int chunkSize = stream.read(buffer, 0, buffer.length);
					if (chunkSize >= 0) {
						line.write(buffer, 0, chunkSize);
					} else {
						throw new IllegalStateException("Audio stream ended.");
					}
				} else {
					line.drain();
					sleepQuiet(frameDuration);
					continue;
				}
			} catch (InterruptedIOException ignored) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception ex) {
				NoverPlay.LOGGER.warn("Audio output error", ex);
				DebugLog.error("Audio output error", ex);
				sleepQuiet(100);
			}
		}
		closeLine();
	}

	private boolean openLine(DataLine.Info info, AudioFormat format) {
		try {
			line = (SourceDataLine) AudioSystem.getLine(info);
			int bufferSize = dataFormat.chunkSampleCount * dataFormat.channelCount * 2 * LINE_BUFFER_FRAMES;
			line.open(format, bufferSize);
			line.start();
			return true;
		} catch (LineUnavailableException e) {
			NoverPlay.LOGGER.error("Audio output unavailable", e);
			DebugLog.error("Audio output unavailable", e);
			return false;
		}
	}

	private void sleepQuiet(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
			running = false;
		}
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
