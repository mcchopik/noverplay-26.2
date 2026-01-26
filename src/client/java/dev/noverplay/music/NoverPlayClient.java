package dev.noverplay.music;

import dev.noverplay.music.client.audio.NoverplayPlayer;
import dev.noverplay.music.client.config.NoverplayConfig;
import dev.noverplay.music.client.config.NoverplayConfigManager;
import dev.noverplay.music.client.service.NoverplayBackend;
import dev.noverplay.music.client.util.DebugLog;
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
		DebugLog.init();
		DebugLog.info("Client init start");
		NoverplayConfig config = NoverplayConfigManager.get();
		String scToken = config.soundcloudClientId == null ? "" : config.soundcloudClientId;
		DebugLog.info(
			"Config loaded: volume=" + config.volume
				+ " lastService=" + config.lastService
				+ " scClientLen=" + scToken.length()
				+ " scClientPreview=" + DebugLog.preview(scToken)
		);
		player = new NoverplayPlayer(config);
		backend = new NoverplayBackend(player.getPlayerManager(), config);
		player.setResolver(backend);
		DebugLog.info("Client init complete");
	}
}
