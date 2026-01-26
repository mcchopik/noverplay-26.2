package dev.noverplay.music.client.screen;

import dev.noverplay.music.NoverPlay;
import dev.noverplay.music.NoverPlayClient;
import dev.noverplay.music.client.audio.NoverplayPlayer;
import dev.noverplay.music.client.audio.PlayerState;
import dev.noverplay.music.client.config.NoverplayConfig;
import dev.noverplay.music.client.config.NoverplayConfigManager;
import dev.noverplay.music.client.model.PlaylistInfo;
import dev.noverplay.music.client.model.PlaylistTracksResult;
import dev.noverplay.music.client.model.SearchResult;
import dev.noverplay.music.client.model.ServiceType;
import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.screen.widget.NoverplaySliderWidget;
import dev.noverplay.music.client.screen.widget.PlaylistListWidget;
import dev.noverplay.music.client.screen.widget.TrackListWidget;
import dev.noverplay.music.client.service.NoverplayBackend;
import dev.noverplay.music.client.util.DebugLog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NoverplayScreen extends Screen {
	private static final Identifier LOGO_TEXTURE = Identifier.of(NoverPlay.MOD_ID, "textures/gui/noverplay_logo.png");
	private static final int LOGO_TEXTURE_SIZE = 256;

	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 6;
	private static final int PADDING = 12;
	private static final int LIST_ITEM_HEIGHT = 32;

	private final Screen parent;

	private NoverplayBackend backend;
	private NoverplayPlayer player;
	private NoverplayConfig config;

	private ServiceType currentService = ServiceType.YOUTUBE;
	private boolean showPlaylists;
	private boolean showingPlaylistTracks;
	private boolean loading;
	private boolean resolvingSoundcloud;
	private String statusMessage = "";

	private TextFieldWidget searchField;
	private ButtonWidget searchButton;
	private ButtonWidget closeButton;

	private ButtonWidget tracksButton;
	private ButtonWidget playlistsButton;
	private ButtonWidget backButton;

	private ButtonWidget youtubeButton;
	private ButtonWidget soundcloudButton;

	private TrackListWidget trackList;
	private PlaylistListWidget playlistList;
	private boolean trackListAdded;
	private boolean playlistListAdded;

	private ButtonWidget prevButton;
	private ButtonWidget playPauseButton;
	private ButtonWidget nextButton;

	private NoverplaySliderWidget progressSlider;
	private NoverplaySliderWidget volumeSlider;

	private TextFieldWidget soundcloudField;
	private ButtonWidget saveTokensButton;
	private ButtonWidget resolveSoundcloudButton;
	private ButtonWidget soundcloudResetButton;

	private final List<TrackInfo> trackResults = new ArrayList<>();
	private final List<PlaylistInfo> playlistResults = new ArrayList<>();
	private final List<TrackInfo> playlistTracks = new ArrayList<>();
	private PlaylistInfo currentPlaylist;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int leftX;
	private int leftWidth;
	private int rightX;
	private int rightWidth;
	private int contentTop;
	private int contentBottom;
	private int listHeaderY;
	private int statusY;
	private int listY;
	private int listHeight;
	private int nowPlayingY;
	private int titleY;
	private int artistY;
	private int progressY;
	private int timeY;
	private int controlsY;
	private int volumeLabelY;
	private int volumeSliderY;
	private int scLabelY;
	private int scFieldY;
	private int tokenButtonsY;

	public NoverplayScreen(Screen parent) {
		super(Text.literal("Noverplay"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (client == null) {
			return;
		}
		DebugLog.info("Screen init start width=" + width + " height=" + height);
		backend = NoverPlayClient.getBackend();
		player = NoverPlayClient.getPlayer();
		config = NoverplayConfigManager.get();
		currentService = ServiceType.fromId(config.lastService);

		String searchText = searchField != null ? searchField.getText() : "";
		String scText = soundcloudField != null ? soundcloudField.getText() : config.soundcloudClientId;

		clearChildren();
		trackListAdded = false;
		playlistListAdded = false;

		layout();

		int closeWidth = 54;
		int closeHeight = 16;
		int closeX = panelX + panelWidth - closeWidth - 8;
		int closeY = panelY + 6;
		closeButton = ButtonWidget.builder(Text.literal("Close"), button -> close())
			.dimensions(closeX, closeY, closeWidth, closeHeight)
			.build();
		addDrawableChild(closeButton);

		int searchButtonWidth = 72;
		int searchFieldWidth = leftWidth - searchButtonWidth - ROW_GAP;
		int searchY = contentTop;
		searchField = new TextFieldWidget(textRenderer, leftX, searchY, searchFieldWidth, ROW_HEIGHT, Text.literal("Search"));
		searchField.setText(searchText);
		searchField.setMaxLength(256);
		searchField.setPlaceholder(Text.literal("Search"));
		searchField.setChangedListener(value ->
			DebugLog.info("Search input changed len=" + value.length() + " value=" + DebugLog.preview(value)));
		addDrawableChild(searchField);

		searchButton = ButtonWidget.builder(Text.literal("Search"), button -> performSearch())
			.dimensions(leftX + searchFieldWidth + ROW_GAP, searchY, searchButtonWidth, ROW_HEIGHT)
			.build();
		addDrawableChild(searchButton);

		int serviceY = searchY + ROW_HEIGHT + ROW_GAP;
		int serviceButtonWidth = (leftWidth - ROW_GAP) / 2;
		youtubeButton = ButtonWidget.builder(Text.literal("YouTube"), button -> setService(ServiceType.YOUTUBE))
			.dimensions(leftX, serviceY, serviceButtonWidth, ROW_HEIGHT)
			.build();
		soundcloudButton = ButtonWidget.builder(Text.literal("SoundCloud"), button -> setService(ServiceType.SOUNDCLOUD))
			.dimensions(leftX + serviceButtonWidth + ROW_GAP, serviceY, serviceButtonWidth, ROW_HEIGHT)
			.build();
		addDrawableChild(youtubeButton);
		addDrawableChild(soundcloudButton);

		int modeY = serviceY + ROW_HEIGHT + ROW_GAP;
		int backWidth = 60;
		int toggleWidth = Math.max(60, (leftWidth - backWidth - ROW_GAP * 2) / 2);
		int backX = leftX + toggleWidth * 2 + ROW_GAP * 2;
		tracksButton = ButtonWidget.builder(Text.literal("Tracks"), button -> setShowPlaylists(false))
			.dimensions(leftX, modeY, toggleWidth, ROW_HEIGHT)
			.build();
		playlistsButton = ButtonWidget.builder(Text.literal("Playlists"), button -> setShowPlaylists(true))
			.dimensions(leftX + toggleWidth + ROW_GAP, modeY, toggleWidth, ROW_HEIGHT)
			.build();
		backButton = ButtonWidget.builder(Text.literal("Back"), button -> exitPlaylistView())
			.dimensions(backX, modeY, backWidth, ROW_HEIGHT)
			.build();
		addDrawableChild(tracksButton);
		addDrawableChild(playlistsButton);
		addDrawableChild(backButton);

		listHeaderY = modeY + ROW_HEIGHT + ROW_GAP;
		statusY = listHeaderY + textRenderer.fontHeight + 2;
		listY = statusY + textRenderer.fontHeight + ROW_GAP;
		listHeight = Math.max(60, contentBottom - listY);

		trackList = new TrackListWidget(client, leftWidth, listHeight, listY, LIST_ITEM_HEIGHT, this::selectTrack);
		trackList.position(leftWidth, listHeight, leftX, listY);
		if (showingPlaylistTracks) {
			trackList.setTracks(playlistTracks);
		} else {
			trackList.setTracks(trackResults);
		}

		playlistList = new PlaylistListWidget(client, leftWidth, listHeight, listY, LIST_ITEM_HEIGHT, this::selectPlaylist);
		playlistList.position(leftWidth, listHeight, leftX, listY);
		playlistList.setPlaylists(playlistResults);

		int rightPadding = 6;
		int sliderHeight = 16;
		int fieldHeight = 18;
		int rightContentX = rightX + rightPadding;
		int controlWidth = rightWidth - rightPadding * 2;
		int resetButtonWidth = 50;
		int tokenFieldWidth = Math.max(80, controlWidth - resetButtonWidth - ROW_GAP);

		nowPlayingY = contentTop + 4;
		titleY = nowPlayingY + textRenderer.fontHeight + 2;
		artistY = titleY + textRenderer.fontHeight + 2;
		progressY = artistY + textRenderer.fontHeight + 6;
		timeY = progressY + sliderHeight + 2;
		controlsY = timeY + textRenderer.fontHeight + 6;
		volumeLabelY = controlsY + ROW_HEIGHT + 6;
		volumeSliderY = volumeLabelY + textRenderer.fontHeight + 2;
		scLabelY = volumeSliderY + sliderHeight + 8;
		scFieldY = scLabelY + textRenderer.fontHeight + 2;
		tokenButtonsY = scFieldY + fieldHeight + 6;

		progressSlider = new NoverplaySliderWidget(rightContentX, progressY, controlWidth, sliderHeight, Text.literal("Progress"), 0.0) {
			@Override
			protected void updateMessage() {
				setMessage(Text.literal("Progress"));
			}

			@Override
			protected void onApplyValue(double value) {
				if (player == null) {
					return;
				}
				PlayerState state = player.snapshot();
				int duration = state.durationSeconds();
				if (duration <= 0) {
					return;
				}
				player.seek(value * duration);
			}
		};
		addDrawableChild(progressSlider);

		int controlButtonWidth = (controlWidth - ROW_GAP * 2) / 3;
		prevButton = ButtonWidget.builder(Text.literal("Prev"), button -> {
			if (player != null) {
				player.previous();
			}
		}).dimensions(rightContentX, controlsY, controlButtonWidth, ROW_HEIGHT).build();
		playPauseButton = ButtonWidget.builder(Text.literal("Play"), button -> togglePlayback())
			.dimensions(rightContentX + controlButtonWidth + ROW_GAP, controlsY, controlButtonWidth, ROW_HEIGHT)
			.build();
		nextButton = ButtonWidget.builder(Text.literal("Next"), button -> {
			if (player != null) {
				player.next();
			}
		}).dimensions(rightContentX + (controlButtonWidth + ROW_GAP) * 2, controlsY, controlButtonWidth, ROW_HEIGHT).build();
		addDrawableChild(prevButton);
		addDrawableChild(playPauseButton);
		addDrawableChild(nextButton);

		volumeSlider = new NoverplaySliderWidget(rightContentX, volumeSliderY, controlWidth, sliderHeight, Text.literal("Volume"), config.volume) {
			@Override
			protected void updateMessage() {
				int percent = (int) Math.round(value * 100.0);
				setMessage(Text.literal("Volume " + percent + "%"));
			}

			@Override
			protected void onApplyValue(double value) {
				if (player != null) {
					player.setVolume((float) value);
				}
				if (config != null) {
					config.volume = (float) value;
				}
			}
		};
		addDrawableChild(volumeSlider);

		soundcloudField = new TextFieldWidget(textRenderer, rightContentX, scFieldY, tokenFieldWidth, fieldHeight, Text.literal("SoundCloud Client ID"));
		soundcloudField.setText(scText);
		soundcloudField.setMaxLength(512);
		soundcloudField.setPlaceholder(Text.literal("SoundCloud client_id"));
		soundcloudField.setChangedListener(value ->
			DebugLog.info("SoundCloud client_id changed len=" + value.length() + " value=" + DebugLog.preview(value)));
		addDrawableChild(soundcloudField);
		soundcloudResetButton = ButtonWidget.builder(Text.literal("Reset"), button -> clearSoundcloudToken())
			.dimensions(rightContentX + tokenFieldWidth + ROW_GAP, scFieldY, resetButtonWidth, fieldHeight)
			.build();
		addDrawableChild(soundcloudResetButton);

		int tokenButtonWidth = (controlWidth - ROW_GAP) / 2;
		saveTokensButton = ButtonWidget.builder(Text.literal("Save"), button -> saveTokens())
			.dimensions(rightContentX, tokenButtonsY, tokenButtonWidth, ROW_HEIGHT)
			.build();
		resolveSoundcloudButton = ButtonWidget.builder(Text.literal("Auto"), button -> resolveSoundcloudId())
			.dimensions(rightContentX + tokenButtonWidth + ROW_GAP, tokenButtonsY, tokenButtonWidth, ROW_HEIGHT)
			.build();
		addDrawableChild(saveTokensButton);
		addDrawableChild(resolveSoundcloudButton);

		updateModeButtons();
		updateServiceButtons();
		updateListVisibility();
		refreshLoadingState();
		DebugLog.info("Screen init complete");
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (searchField != null && searchField.isFocused() && input.isEnterOrSpace()) {
			performSearch();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void close() {
		persistConfig();
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		drawBackground(context);
		PlayerState state = player != null ? player.snapshot() : null;
		updatePlaybackWidgets(state);
		drawPanels(context, state);
		super.render(context, mouseX, mouseY, delta);
	}

	private void layout() {
		panelWidth = Math.min(760, this.width - 40);
		panelHeight = Math.min(440, this.height - 40);
		panelX = (this.width - panelWidth) / 2;
		panelY = (this.height - panelHeight) / 2;

		int innerWidth = panelWidth - PADDING * 2;
		int gap = 12;
		rightWidth = Math.max(220, innerWidth / 3);
		leftWidth = innerWidth - rightWidth - gap;
		if (leftWidth < 240) {
			leftWidth = Math.max(200, innerWidth - rightWidth - gap);
			rightWidth = innerWidth - leftWidth - gap;
		}
		leftX = panelX + PADDING;
		rightX = leftX + leftWidth + gap;
		contentTop = panelY + 34;
		contentBottom = panelY + panelHeight - PADDING;
	}

	private void drawBackground(DrawContext context) {
		context.fill(0, 0, width, height, 0xA0000000);
	}

	private void drawPanels(DrawContext context, PlayerState state) {
		context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF171B21);
		context.fill(panelX, panelY, panelX + panelWidth, panelY + 28, 0xFF202733);

		int logoSize = 18;
		int logoX = panelX + 8;
		int logoY = panelY + 5;
		int color = ColorHelper.getWhite(1.0f);
		context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, logoX, logoY, 0.0f, 0.0f, logoSize, logoSize, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, color);
		context.drawTextWithShadow(textRenderer, "Noverplay", logoX + logoSize + 6, panelY + 9, 0xFFE6E8EC);

		context.fill(leftX - 2, listY - 2, leftX + leftWidth + 2, contentBottom, 0xFF12161B);
		context.fill(rightX - 2, contentTop - 2, rightX + rightWidth + 2, contentBottom, 0xFF12161B);

		String listTitle = showingPlaylistTracks && currentPlaylist != null
			? "Playlist: " + currentPlaylist.title()
			: (showPlaylists ? "Playlists" : "Tracks");
		context.drawTextWithShadow(textRenderer, listTitle, leftX, listHeaderY, 0xFFC9CFD6);
		if (statusMessage != null && !statusMessage.isBlank()) {
			int statusColor = statusMessage.toLowerCase().contains("error") || statusMessage.toLowerCase().contains("required")
				? 0xFFBE7070
				: 0xFF9AA3AE;
			context.drawTextWithShadow(textRenderer, statusMessage, leftX, statusY, statusColor);
		}

		context.drawTextWithShadow(textRenderer, "Now Playing", rightX + 6, nowPlayingY, 0xFFC9CFD6);
		TrackInfo track = state != null ? state.track() : null;
		String title = track != null && !track.title().isBlank() ? track.title() : "No track";
		String meta = buildMetaLine(track, state);
		context.drawTextWithShadow(textRenderer, title, rightX + 6, titleY, 0xFFE6E8EC);
		context.drawTextWithShadow(textRenderer, meta, rightX + 6, artistY, 0xFF9AA3AE);

		int position = state != null ? state.positionSeconds() : 0;
		int duration = state != null ? state.durationSeconds() : 0;
		String positionText = formatTime(position);
		String durationText = formatTime(duration);
		context.drawTextWithShadow(textRenderer, positionText, rightX + 6, timeY, 0xFF8B94A0);
		int durationWidth = textRenderer.getWidth(durationText);
		context.drawTextWithShadow(textRenderer, durationText, rightX + rightWidth - durationWidth - 6, timeY, 0xFF8B94A0);

		context.drawTextWithShadow(textRenderer, "Volume", rightX + 6, volumeLabelY, 0xFF9AA3AE);
		context.drawTextWithShadow(textRenderer, "SoundCloud Client ID", rightX + 6, scLabelY, 0xFF9AA3AE);
	}

	private void updatePlaybackWidgets(PlayerState state) {
		if (playPauseButton != null) {
			String label = state != null && state.playing() ? "Pause" : "Play";
			playPauseButton.setMessage(Text.literal(label));
		}
		if (nextButton != null && state != null) {
			nextButton.active = state.queue() != null && !state.queue().isEmpty();
		}
		if (progressSlider != null && state != null && !progressSlider.isDragging()) {
			int duration = state.durationSeconds();
			double value = duration > 0 ? (double) state.positionSeconds() / (double) duration : 0.0;
			progressSlider.setValueSilently(value);
		}
		if (volumeSlider != null && state != null && !volumeSlider.isDragging()) {
			volumeSlider.setValueSilently(state.volume());
		}
	}

	private void performSearch() {
		if (backend == null || loading) {
			return;
		}
		String query = searchField != null ? searchField.getText().trim() : "";
		if (query.isBlank()) {
			statusMessage = "Enter a search query";
			DebugLog.warn("Search blocked: empty query");
			return;
		}
		loading = true;
		DebugLog.info("Search start service=" + currentService + " playlists=" + showPlaylists
			+ " query=" + DebugLog.preview(query));
		statusMessage = "Searching...";
		refreshLoadingState();
		showingPlaylistTracks = false;
		currentPlaylist = null;
		final boolean playlistSearch = showPlaylists;
		final ServiceType service = currentService;
		CompletableFuture<SearchResult> future = playlistSearch
			? backend.searchPlaylists(service, query, 0, 50)
			: backend.searchTracks(service, query, 0, 50);
		future.whenComplete((result, error) -> {
			if (client == null) {
				return;
			}
			client.execute(() -> {
				loading = false;
				refreshLoadingState();
				if (error != null || result == null) {
					statusMessage = "Search failed";
					DebugLog.error("Search failed", error);
					return;
				}
				int count = playlistSearch ? result.playlists().size() : result.tracks().size();
				String notice = result.notice();
				if (notice != null && !notice.isBlank()) {
					statusMessage = notice;
				} else if (count == 0) {
					statusMessage = "No results";
				} else {
					statusMessage = (playlistSearch ? "Playlists: " : "Tracks: ") + count;
				}
				if (playlistSearch) {
					playlistResults.clear();
					playlistResults.addAll(result.playlists());
					if (playlistList != null) {
						playlistList.setPlaylists(playlistResults);
						playlistList.setScrollY(0.0);
					}
					DebugLog.info("Search playlists applied count=" + playlistResults.size());
				} else {
					trackResults.clear();
					trackResults.addAll(result.tracks());
					if (trackList != null) {
						trackList.setTracks(trackResults);
						trackList.setScrollY(0.0);
					}
					DebugLog.info("Search tracks applied count=" + trackResults.size());
				}
				updateListVisibility();
			});
		});
	}

	private void selectTrack(TrackInfo track, Integer index) {
		if (player == null || track == null) {
			return;
		}
		List<TrackInfo> source = showingPlaylistTracks ? playlistTracks : trackResults;
		if (source.isEmpty()) {
			return;
		}
		DebugLog.info("Track selected index=" + index
			+ " title=" + DebugLog.preview(track.title())
			+ " artist=" + DebugLog.preview(track.artist())
			+ " service=" + track.service());
		player.playFromList(source, index);
		if (showingPlaylistTracks && currentPlaylist != null) {
			player.setPlaylistTitle(currentPlaylist.title());
		} else {
			player.setPlaylistTitle("");
		}
	}

	private void selectPlaylist(PlaylistInfo playlist, Integer index) {
		if (backend == null || playlist == null || loading) {
			return;
		}
		DebugLog.info("Playlist selected index=" + index
			+ " id=" + DebugLog.preview(playlist.playlistId())
			+ " title=" + DebugLog.preview(playlist.title())
			+ " service=" + playlist.service());
		loading = true;
		statusMessage = "Loading playlist...";
		refreshLoadingState();
		currentPlaylist = playlist;
		final ServiceType service = playlist.service();
		CompletableFuture<PlaylistTracksResult> future = backend.loadPlaylist(service, playlist.playlistId(), 0, 200);
		future.whenComplete((result, error) -> {
			if (client == null) {
				return;
			}
			client.execute(() -> {
				loading = false;
				refreshLoadingState();
				if (error != null || result == null) {
					statusMessage = "Playlist load failed";
					DebugLog.error("Playlist load failed", error);
					return;
				}
				playlistTracks.clear();
				playlistTracks.addAll(result.tracks());
				showingPlaylistTracks = true;
				if (trackList != null) {
					trackList.setTracks(playlistTracks);
				}
				DebugLog.info("Playlist tracks applied count=" + playlistTracks.size());
				if (playlistTracks.isEmpty()) {
					statusMessage = "Playlist is empty";
				} else {
					statusMessage = "";
				}
				updateListVisibility();
				updateModeButtons();
			});
		});
	}

	private void togglePlayback() {
		if (player == null) {
			return;
		}
		PlayerState state = player.snapshot();
		if (state.playing()) {
			DebugLog.info("Toggle playback: pause");
			player.pause();
		} else {
			DebugLog.info("Toggle playback: resume");
			player.resume();
		}
	}

	private void setService(ServiceType service) {
		if (service == null || service == currentService) {
			return;
		}
		DebugLog.info("Service changed from " + currentService + " to " + service);
		currentService = service;
		if (config != null) {
			config.lastService = service.getId();
		}
		updateServiceButtons();
	}

	private void setShowPlaylists(boolean show) {
		if (showPlaylists == show && !showingPlaylistTracks) {
			return;
		}
		DebugLog.info("Toggle view playlists=" + show);
		showPlaylists = show;
		showingPlaylistTracks = false;
		currentPlaylist = null;
		updateModeButtons();
		updateListVisibility();
	}

	private void exitPlaylistView() {
		if (!showingPlaylistTracks) {
			return;
		}
		DebugLog.info("Exit playlist view");
		showingPlaylistTracks = false;
		currentPlaylist = null;
		updateModeButtons();
		updateListVisibility();
	}

	private void updateServiceButtons() {
		if (youtubeButton != null) {
			youtubeButton.active = currentService != ServiceType.YOUTUBE;
		}
		if (soundcloudButton != null) {
			soundcloudButton.active = currentService != ServiceType.SOUNDCLOUD;
		}
	}

	private void updateModeButtons() {
		if (tracksButton != null) {
			tracksButton.active = showPlaylists;
		}
		if (playlistsButton != null) {
			playlistsButton.active = !showPlaylists;
		}
		if (backButton != null) {
			backButton.active = showingPlaylistTracks;
			backButton.visible = showingPlaylistTracks;
		}
	}

	private void updateListVisibility() {
		boolean showTrackList = !showPlaylists || showingPlaylistTracks;
		if (showTrackList) {
			if (playlistListAdded && playlistList != null) {
				remove(playlistList);
				playlistListAdded = false;
			}
			if (!trackListAdded && trackList != null) {
				addDrawableChild(trackList);
				trackListAdded = true;
			}
		} else {
			if (trackListAdded && trackList != null) {
				remove(trackList);
				trackListAdded = false;
			}
			if (!playlistListAdded && playlistList != null) {
				addDrawableChild(playlistList);
				playlistListAdded = true;
			}
		}
	}

	private void refreshLoadingState() {
		if (searchButton != null) {
			searchButton.active = !loading;
		}
		if (resolveSoundcloudButton != null) {
			resolveSoundcloudButton.active = !resolvingSoundcloud;
		}
	}

	private void saveTokens() {
		if (config == null || backend == null) {
			return;
		}
		String scClientId = soundcloudField != null ? soundcloudField.getText().trim() : "";
		DebugLog.info("Save SoundCloud client_id len=" + scClientId.length()
			+ " preview=" + DebugLog.preview(scClientId));
		config.soundcloudClientId = scClientId;
		backend.setSoundCloudClientId(scClientId);
		NoverplayConfigManager.save();
		statusMessage = "Tokens saved";
	}

	private void clearSoundcloudToken() {
		if (config != null) {
			config.soundcloudClientId = "";
		}
		if (backend != null) {
			backend.setSoundCloudClientId("");
		}
		if (soundcloudField != null) {
			soundcloudField.setText("");
		}
		NoverplayConfigManager.save();
		DebugLog.info("SoundCloud client_id cleared");
		announceTokenCleared();
	}

	private void announceTokenCleared() {
		statusMessage = "Token cleared";
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Token cleared"), false);
		}
	}

	private void resolveSoundcloudId() {
		if (backend == null || resolvingSoundcloud) {
			return;
		}
		DebugLog.info("Resolve SoundCloud client_id start");
		resolvingSoundcloud = true;
		statusMessage = "Resolving SoundCloud client_id...";
		refreshLoadingState();
		CompletableFuture.supplyAsync(backend::resolveSoundCloudClientId)
			.whenComplete((value, error) -> {
				if (client == null) {
					return;
				}
				client.execute(() -> {
					resolvingSoundcloud = false;
					refreshLoadingState();
					if (error != null || value == null || value.isBlank()) {
						statusMessage = "SoundCloud client_id not found";
						DebugLog.error("Resolve SoundCloud client_id failed", error);
						return;
					}
					DebugLog.info("Resolve SoundCloud client_id success len=" + value.length()
						+ " preview=" + DebugLog.preview(value));
					if (soundcloudField != null) {
						soundcloudField.setText(value);
					}
					if (config != null) {
						config.soundcloudClientId = value;
					}
					if (backend != null) {
						backend.setSoundCloudClientId(value);
					}
					NoverplayConfigManager.save();
					statusMessage = "SoundCloud client_id updated";
				});
			});
	}

	private void persistConfig() {
		if (config == null) {
			return;
		}
		config.lastService = currentService.getId();
		NoverplayConfigManager.save();
	}

	private String buildMetaLine(TrackInfo track, PlayerState state) {
		String artist = track != null ? track.artist() : "";
		String service = track != null && track.service() != null ? track.service().getLabel() : "";
		String status = state == null ? "Idle" : (state.buffering() ? "Buffering" : (state.playing() ? "Playing" : "Paused"));
		StringBuilder builder = new StringBuilder();
		appendMeta(builder, artist);
		appendMeta(builder, service);
		appendMeta(builder, status);
		return builder.toString();
	}

	private void appendMeta(StringBuilder builder, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		if (builder.length() > 0) {
			builder.append(" • ");
		}
		builder.append(value);
	}

	private String formatTime(int seconds) {
		int total = Math.max(0, seconds);
		int hours = total / 3600;
		int minutes = (total % 3600) / 60;
		int secs = total % 60;
		if (hours > 0) {
			return String.format("%02d:%02d:%02d", hours, minutes, secs);
		}
		return String.format("%02d:%02d", minutes, secs);
	}
}
