package dev.noverplay.music;

import dev.noverplay.music.client.audio.NoverplayPlayer;
import dev.noverplay.music.client.config.NoverplayConfig;
import dev.noverplay.music.client.config.NoverplayConfigManager;
import dev.noverplay.music.client.model.PlaylistInfo;
import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.screen.NoverplayScreen;
import dev.noverplay.music.client.service.NoverplayBackend;
import dev.noverplay.music.client.util.DebugLog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class NoverPlayClient implements ClientModInitializer {
	private static NoverplayBackend backend;
	private static NoverplayPlayer player;
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
		Identifier.fromNamespaceAndPath(NoverPlay.MOD_ID, "main")
	);
	private static KeyMapping openKeybind;
	private static ScreenState lastScreenState;

	public static NoverplayBackend getBackend() {
		return backend;
	}

	public static NoverplayPlayer getPlayer() {
		return player;
	}

	public static void saveScreenState(ScreenState state) {
		lastScreenState = state;
	}

	public static ScreenState getScreenState() {
		return lastScreenState;
	}

	public static void openNoverplayScreen(Minecraft client) {
		if (client == null || client.gui.screen() instanceof NoverplayScreen) {
			return;
		}
		Screen parent = client.gui.screen();
		client.gui.setScreen(new NoverplayScreen(parent));
	}

	@Override
	public void onInitializeClient() {
		DebugLog.init();
		DebugLog.info("Client init start");
		NoverplayConfig config = NoverplayConfigManager.get();
		DebugLog.info(
			"Config loaded: volume=" + config.volume
				+ " lastService=" + config.lastService
		);
		player = new NoverplayPlayer(config);
		backend = new NoverplayBackend(player.getPlayerManager());
		player.setResolver(backend);
		openKeybind = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.noverplay.open",
				GLFW.GLFW_KEY_P,
				KEY_CATEGORY
			)
		);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKeybind.consumeClick()) {
				openNoverplayScreen(client);
			}
		});
		DebugLog.info("Client init complete");
	}

	public static class ScreenState {
		public String query = "";
		public boolean showPlaylists;
		public boolean showingPlaylistTracks;
		public String statusMessage = "";
		public int searchOffset;
		public int searchLimit = 50;
		public boolean searchHasMore;
		public double trackScroll;
		public double playlistScroll;
		public PlaylistInfo currentPlaylist;
		public java.util.List<TrackInfo> trackResults = java.util.List.of();
		public java.util.List<PlaylistInfo> playlistResults = java.util.List.of();
		public java.util.List<TrackInfo> playlistTracks = java.util.List.of();
	}
}
