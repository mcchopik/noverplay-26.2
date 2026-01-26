package dev.noverplay.music;

import dev.noverplay.music.client.audio.NoverplayPlayer;
import dev.noverplay.music.client.config.NoverplayConfig;
import dev.noverplay.music.client.config.NoverplayConfigManager;
import dev.noverplay.music.client.screen.NoverplayScreen;
import dev.noverplay.music.client.service.NoverplayBackend;
import dev.noverplay.music.client.util.DebugLog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class NoverPlayClient implements ClientModInitializer {
	private static NoverplayBackend backend;
	private static NoverplayPlayer player;
	private static KeyBinding openKeybind;
	private static KeyBinding.Category keyCategory;

	public static NoverplayBackend getBackend() {
		return backend;
	}

	public static NoverplayPlayer getPlayer() {
		return player;
	}

	public static void openNoverplayScreen(MinecraftClient client) {
		if (client == null || client.currentScreen instanceof NoverplayScreen) {
			return;
		}
		Screen parent = client.currentScreen;
		client.setScreen(new NoverplayScreen(parent));
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
		keyCategory = KeyBinding.Category.create(Identifier.of(NoverPlay.MOD_ID, "noverplay"));
		openKeybind = KeyBindingHelper.registerKeyBinding(
			new KeyBinding(
				"key.noverplay.open",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				keyCategory
			)
		);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKeybind.wasPressed()) {
				openNoverplayScreen(client);
			}
		});
		DebugLog.info("Client init complete");
	}
}
