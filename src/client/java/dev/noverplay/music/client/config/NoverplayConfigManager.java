package dev.noverplay.music.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NoverplayConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("noverplay.json");
	private static NoverplayConfig config;

	private NoverplayConfigManager() {
	}

	public static synchronized NoverplayConfig get() {
		if (config == null) {
			config = load();
		}
		return config;
	}

	public static synchronized void save() {
		if (config == null) {
			return;
		}
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException ignored) {
		}
	}

	private static NoverplayConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			return new NoverplayConfig();
		}
		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			NoverplayConfig loaded = GSON.fromJson(reader, NoverplayConfig.class);
			if (loaded == null) {
				return new NoverplayConfig();
			}
			return loaded;
		} catch (IOException | JsonSyntaxException ignored) {
			return new NoverplayConfig();
		}
	}
}
