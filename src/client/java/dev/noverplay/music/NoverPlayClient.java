package dev.noverplay.music;

import dev.noverplay.music.client.audio.NoverplayPlayer;
import dev.noverplay.music.client.config.NoverplayConfig;
import dev.noverplay.music.client.config.NoverplayConfigManager;
import dev.noverplay.music.client.service.NoverplayBackend;
import net.fabricmc.api.ClientModInitializer;

public class NoverPlayClient implements ClientModInitializer {
	private static NoverplayBackend backend;
	private static NoverplayPlayer player;

	public static NoverplayBackend getBackend() {
		return backend;
	}

	public static NoverplayPlayer getPlayer() {
		return player;
	}

	@Override
	public void onInitializeClient() {
		NoverplayConfig config = NoverplayConfigManager.get();
		player = new NoverplayPlayer(config.volume);
		backend = new NoverplayBackend(player.getPlayerManager(), config);
		player.setResolver(backend);
	}
}
