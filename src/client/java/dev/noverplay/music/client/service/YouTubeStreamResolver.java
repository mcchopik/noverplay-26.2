package dev.noverplay.music.client.service;

import dev.noverplay.music.client.model.TrackInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.localization.Localization;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class YouTubeStreamResolver {
	private static final String USER_AGENT =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
	private static final String WATCH_PREFIX = "https://www.youtube.com/watch?v=";
	private static final long CACHE_TTL_MS = 15 * 60 * 1000L;

	private static final Object INIT_LOCK = new Object();
	private static volatile boolean initialized;

	private final Map<String, CachedStream> cache = new ConcurrentHashMap<>();

	public TrackInfo resolve(TrackInfo track) {
		if (track == null) {
			return null;
		}
		String existing = track.streamUrl();
		if (existing != null && !existing.isBlank() && !isYouTubeUrl(existing)) {
			return track;
		}
		String id = normalizeId(track.trackId() == null ? existing : track.trackId());
		if (id == null || id.isBlank()) {
			return track;
		}
		String streamUrl = resolveStreamUrl(id);
		if (streamUrl == null || streamUrl.isBlank()) {
			return track;
		}
		return track.withStreamUrl(streamUrl);
	}

	public String resolveStreamUrl(String idOrUrl) {
		String id = normalizeId(idOrUrl);
		if (id == null || id.isBlank()) {
			return null;
		}
		CachedStream cached = cache.get(id);
		if (cached != null && !cached.isExpired()) {
			return cached.url;
		}
		ensureInit();
		try {
			StreamInfo info = StreamInfo.getInfo(WATCH_PREFIX + id);
			List<AudioStream> streams = info.getAudioStreams();
			if (streams == null || streams.isEmpty()) {
				return null;
			}
			AudioStream selected = selectBest(streams);
			if (selected == null) {
				return null;
			}
			String url = selected.getUrl();
			if (url != null && !url.isBlank()) {
				cache.put(id, new CachedStream(url));
			}
			return url;
		} catch (ExtractionException | RuntimeException | java.io.IOException ignored) {
			return null;
		}
	}

	private void ensureInit() {
		if (initialized) {
			return;
		}
		synchronized (INIT_LOCK) {
			if (initialized) {
				return;
			}
			NewPipe.init(new NewPipeDownloader(USER_AGENT), Localization.fromLocale(Locale.US));
			initialized = true;
		}
	}

	private AudioStream selectBest(List<AudioStream> streams) {
		AudioStream progressive = streams.stream()
			.filter(stream -> stream != null && stream.isUrl() && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP)
			.max(Comparator.comparingInt(this::bitrate))
			.orElse(null);
		if (progressive != null) {
			return progressive;
		}
		AudioStream dash = streams.stream()
			.filter(stream -> stream != null && stream.isUrl() && stream.getDeliveryMethod() == DeliveryMethod.DASH)
			.max(Comparator.comparingInt(this::bitrate))
			.orElse(null);
		if (dash != null) {
			return dash;
		}
		return streams.stream()
			.filter(stream -> stream != null && stream.isUrl())
			.findFirst()
			.orElse(null);
	}

	private int bitrate(AudioStream stream) {
		int bitrate = stream.getAverageBitrate();
		if (bitrate <= 0) {
			bitrate = stream.getBitrate();
		}
		return Math.max(0, bitrate);
	}

	private boolean isYouTubeUrl(String value) {
		if (value == null) {
			return false;
		}
		String lower = value.toLowerCase(Locale.ROOT);
		return lower.contains("youtube.com") || lower.contains("youtu.be");
	}

	private String normalizeId(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		int vIndex = trimmed.indexOf("v=");
		if (vIndex >= 0) {
			String part = trimmed.substring(vIndex + 2);
			int amp = part.indexOf("&");
			if (amp >= 0) {
				part = part.substring(0, amp);
			}
			return part;
		}
		if (trimmed.contains("youtu.be/")) {
			int idx = trimmed.indexOf("youtu.be/");
			String part = trimmed.substring(idx + "youtu.be/".length());
			int qm = part.indexOf("?");
			if (qm >= 0) {
				part = part.substring(0, qm);
			}
			return part;
		}
		return trimmed;
	}

	private static final class CachedStream {
		private final String url;
		private final long createdAt;

		private CachedStream(String url) {
			this.url = url;
			this.createdAt = System.currentTimeMillis();
		}

		private boolean isExpired() {
			return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
		}
	}
}
