package dev.noverplay.music.client.service;

import dev.noverplay.music.client.model.PlaylistTracksResult;
import dev.noverplay.music.client.model.SearchResult;
import dev.noverplay.music.client.model.ServiceType;

public interface MusicService {
	ServiceType getServiceType();

	SearchResult searchTracks(String query, int offset, int limit);

	SearchResult searchPlaylists(String query, int offset, int limit);

	PlaylistTracksResult loadPlaylist(String playlistId, int offset, int limit);
}
