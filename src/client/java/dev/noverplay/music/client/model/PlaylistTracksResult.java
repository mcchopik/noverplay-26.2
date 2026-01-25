package dev.noverplay.music.client.model;

import java.util.List;

public record PlaylistTracksResult(
	List<TrackInfo> tracks,
	Integer totalCount
) {
}
