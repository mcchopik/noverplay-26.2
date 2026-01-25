package dev.noverplay.music.client.model;

public record PlaylistInfo(
	String playlistId,
	String title,
	String author,
	int trackCount,
	String coverUrl,
	ServiceType service
) {
}
