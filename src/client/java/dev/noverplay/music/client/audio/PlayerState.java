package dev.noverplay.music.client.audio;

import dev.noverplay.music.client.model.TrackInfo;

import java.util.List;

public record PlayerState(
	TrackInfo track,
	boolean playing,
	boolean buffering,
	int positionSeconds,
	int durationSeconds,
	float volume,
	List<TrackInfo> queue,
	String playlistTitle
) {
}
