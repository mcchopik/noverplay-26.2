package dev.noverplay.music.client.service;

import dev.noverplay.music.client.net.HttpHelper;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoundCloudClientIdResolver {
	private static final String USER_AGENT =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
	private static final Pattern SCRIPT_PATTERN =
		Pattern.compile("https://a-v2\\.sndcdn\\.com/assets/[^\"']+\\.js");
	private static final Pattern CLIENT_ID_PATTERN =
		Pattern.compile("client_id\\s*[:=]\\s*\"([a-zA-Z0-9]{20,})\"");

	private final long ttlMillis;
	private String manualId;
	private String cachedId;
	private long cachedAt;

	public SoundCloudClientIdResolver(String manualId, long ttlSeconds) {
		this.ttlMillis = Math.max(1L, ttlSeconds) * 1000L;
		this.manualId = safe(manualId);
		if (!this.manualId.isEmpty()) {
			this.cachedId = this.manualId;
			this.cachedAt = System.currentTimeMillis();
		}
	}

	public synchronized void setManualId(String manualId) {
		this.manualId = safe(manualId);
		if (!this.manualId.isEmpty()) {
			this.cachedId = this.manualId;
			this.cachedAt = System.currentTimeMillis();
		}
	}

	public synchronized String getClientId() {
		if (!manualId.isEmpty()) {
			return manualId;
		}
		if (cachedId != null && !isExpired()) {
			return cachedId;
		}
		String resolved = resolveFromWeb();
		if (resolved != null && !resolved.isEmpty()) {
			cachedId = resolved;
			cachedAt = System.currentTimeMillis();
		}
		return cachedId;
	}

	private boolean isExpired() {
		if (cachedAt <= 0) {
			return true;
		}
		return System.currentTimeMillis() - cachedAt > ttlMillis;
	}

	private String resolveFromWeb() {
		try {
			String home = HttpHelper.getText(
				"https://soundcloud.com",
				Map.of(),
				Map.of("User-Agent", USER_AGENT, "Accept", "text/html,application/xhtml+xml")
			);
			if (home == null || home.isBlank()) {
				return null;
			}
			Matcher matcher = SCRIPT_PATTERN.matcher(home);
			int count = 0;
			while (matcher.find() && count < 6) {
				count++;
				String scriptUrl = matcher.group();
				String script = HttpHelper.getText(
					scriptUrl,
					Map.of(),
					Map.of("User-Agent", USER_AGENT, "Accept", "*/*")
				);
				if (script == null || script.isBlank()) {
					continue;
				}
				Matcher idMatcher = CLIENT_ID_PATTERN.matcher(script);
				if (idMatcher.find()) {
					return idMatcher.group(1);
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private String safe(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}
}
