package dev.noverplay.music.client.model;

public record TrackInfo(
	String trackId,
	String title,
	String artist,
	int duration,
	ServiceType service,
	String coverUrl,
	String streamUrl
) {
	public String durationText() {
		int total = Math.max(0, duration);
		int hours = total / 3600;
		int minutes = (total % 3600) / 60;
		int seconds = total % 60;
		if (hours > 0) {
			return String.format("%02d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format("%02d:%02d", minutes, seconds);
	}

	public TrackInfo withStreamUrl(String url) {
		return new TrackInfo(trackId, title, artist, duration, service, coverUrl, url);
	}
}
