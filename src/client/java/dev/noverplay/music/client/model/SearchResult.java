package dev.noverplay.music.client.model;

import java.util.List;

public record SearchResult(
	List<TrackInfo> tracks,
	List<PlaylistInfo> playlists,
	String notice
) {
}
