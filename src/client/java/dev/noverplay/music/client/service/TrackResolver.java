package dev.noverplay.music.client.service;

import dev.noverplay.music.client.model.TrackInfo;

public interface TrackResolver {
	TrackInfo resolve(TrackInfo track);
}
