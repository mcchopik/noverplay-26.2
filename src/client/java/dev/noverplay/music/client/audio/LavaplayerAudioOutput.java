package dev.noverplay.music.client.audio;

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormatTools;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import dev.noverplay.music.NoverPlay;
import dev.noverplay.music.client.util.DebugLog;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LavaplayerAudioOutput implements Runnable {
	private static final int FRAME_TIMEOUT_MS = 500;
	private static final int BUFFER_FRAMES = 250;
	private static final long STARVED_LOG_MS = 1000L;

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
		try {
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			line = (SourceDataLine) AudioSystem.getLine(info);
			int bufferSize = dataFormat.maximumChunkSize() * BUFFER_FRAMES;
			line.open(format, bufferSize);
			line.start();
		} catch (LineUnavailableException e) {
			NoverPlay.LOGGER.error("Audio output unavailable", e);
			running = false;
			return;
		}

		byte[] buffer = new byte[dataFormat.maximumChunkSize()];
		byte[] silence = new byte[dataFormat.maximumChunkSize()];
		MutableAudioFrame frame = new MutableAudioFrame(ByteBuffer.wrap(buffer));
		long lastFrameAt = System.currentTimeMillis();
		long lastStarvedLogAt = lastFrameAt;

		while (running) {
			try {
				boolean provided = player.provide(frame, FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				if (!provided) {
					long now = System.currentTimeMillis();
					if (now - lastStarvedLogAt >= STARVED_LOG_MS) {
						long delta = now - lastFrameAt;
						NoverPlay.LOGGER.warn("Audio output starved for {}ms", delta);
						DebugLog.warn("Audio output starved for " + delta + "ms");
						lastStarvedLogAt = now;
					}
					if (line != null && line.available() >= silence.length) {
						line.write(silence, 0, silence.length);
					}
					continue;
				}
				lastFrameAt = System.currentTimeMillis();
				lastStarvedLogAt = lastFrameAt;
				int length = frame.getDataLength();
				if (length > 0) {
					line.write(buffer, 0, length);
				}
			} catch (TimeoutException ignored) {
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
