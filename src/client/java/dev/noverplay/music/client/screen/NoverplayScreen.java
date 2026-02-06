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
import dev.noverplay.music.client.widget.IconLinkButton;
import dev.noverplay.music.client.service.NoverplayBackend;
import dev.noverplay.music.client.util.DebugLog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NoverplayScreen extends Screen {
	private static final Identifier LOGO_TEXTURE = new Identifier(NoverPlay.MOD_ID, "textures/gui/noverplay_logo.png");
	private static final Identifier TELEGRAM_TEXTURE = new Identifier(NoverPlay.MOD_ID, "textures/gui/telegram.png");
	private static final int LOGO_TEXTURE_SIZE = 256;
	private static final String TELEGRAM_URL = "https://t.me/NoverPlay";

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
	private boolean loadingMore;
	private String statusMessage = "";
	private String searchQuery = "";
	private int searchOffset;
	private int searchLimit = 50;
	private boolean searchHasMore;

	private TextFieldWidget searchField;
	private ButtonWidget searchButton;
	private ButtonWidget closeButton;
	private IconLinkButton telegramButton;

	private ButtonWidget tracksButton;
	private ButtonWidget playlistsButton;
	private ButtonWidget backButton;


	private TrackListWidget trackList;
	private PlaylistListWidget playlistList;
	private boolean trackListAdded;
	private boolean playlistListAdded;

	private ButtonWidget prevButton;
	private ButtonWidget playPauseButton;
	private ButtonWidget nextButton;

	private NoverplaySliderWidget progressSlider;
	private NoverplaySliderWidget volumeSlider;

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
	private int lastMouseX;
	private int lastMouseY;
	private float lastDelta;

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

		NoverPlayClient.ScreenState saved = NoverPlayClient.getScreenState();
		if (saved != null) {
			searchQuery = saved.query == null ? "" : saved.query;
			showPlaylists = saved.showPlaylists;
			showingPlaylistTracks = saved.showingPlaylistTracks;
			statusMessage = saved.statusMessage == null ? "" : saved.statusMessage;
			searchOffset = saved.searchOffset;
			searchLimit = saved.searchLimit > 0 ? saved.searchLimit : 50;
			searchHasMore = saved.searchHasMore;
			currentPlaylist = saved.currentPlaylist;
			trackResults.clear();
			trackResults.addAll(saved.trackResults == null ? List.of() : saved.trackResults);
			playlistResults.clear();
			playlistResults.addAll(saved.playlistResults == null ? List.of() : saved.playlistResults);
			playlistTracks.clear();
			playlistTracks.addAll(saved.playlistTracks == null ? List.of() : saved.playlistTracks);
		}

		String searchText = searchField != null ? searchField.getText() : searchQuery;

		clearChildren();
		trackListAdded = false;
		playlistListAdded = false;

		layout();

		int iconSize = 16;
		int closeWidth = 54;
		int closeHeight = 16;
		int closeY = panelY + 6;
		int iconX = panelX + panelWidth - iconSize - 8;
		int closeX = iconX - closeWidth - 6;
		telegramButton = new IconLinkButton(iconX, closeY, iconSize, TELEGRAM_TEXTURE, Text.literal("Telegram"), () ->
			openExternalLink(TELEGRAM_URL));
		addDrawableChild(telegramButton);
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

		int modeY = searchY + ROW_HEIGHT + ROW_GAP;
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
		trackList.updateSize(leftWidth, listHeight, listY, listY + listHeight);
		trackList.setLeftPos(leftX);
		trackList.setRenderBackground(false);
		trackList.setRenderHorizontalShadows(false);
		if (showingPlaylistTracks) {
			trackList.setTracks(playlistTracks);
		} else {
			trackList.setTracks(trackResults);
		}
		if (saved != null) {
			trackList.setScrollAmount(saved.trackScroll);
		}

		playlistList = new PlaylistListWidget(client, leftWidth, listHeight, listY, LIST_ITEM_HEIGHT, this::selectPlaylist);
		playlistList.updateSize(leftWidth, listHeight, listY, listY + listHeight);
		playlistList.setLeftPos(leftX);
		playlistList.setRenderBackground(false);
		playlistList.setRenderHorizontalShadows(false);
		playlistList.setPlaylists(playlistResults);
		if (saved != null) {
			playlistList.setScrollAmount(saved.playlistScroll);
		}

		int rightPadding = 6;
		int sliderHeight = 16;
		int rightContentX = rightX + rightPadding;
		int controlWidth = rightWidth - rightPadding * 2;

		nowPlayingY = contentTop + 4;
		titleY = nowPlayingY + textRenderer.fontHeight + 2;
		artistY = titleY + textRenderer.fontHeight + 2;
		progressY = artistY + textRenderer.fontHeight + 6;
		timeY = progressY + sliderHeight + 2;
		controlsY = timeY + textRenderer.fontHeight + 6;
		volumeLabelY = controlsY + ROW_HEIGHT + 6;
		volumeSliderY = volumeLabelY + textRenderer.fontHeight + 2;

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

		updateModeButtons();
		updateListVisibility();
		refreshLoadingState();
		DebugLog.info("Screen init complete");
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchField != null && searchField.isFocused()
			&& (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE)) {
			performSearch();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		NoverPlayClient.saveScreenState(buildState());
		persistConfig();
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		lastDelta = delta;
		drawBackground(context);
		PlayerState state = player != null ? player.snapshot() : null;
		updatePlaybackWidgets(state);
		drawPanels(context, state);
		maybeLoadMore();
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		boolean handled = super.mouseScrolled(mouseX, mouseY, amount);
		if (handled) {
			maybeLoadMore();
		}
		return handled;
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
		if (client != null && client.world != null) {
			applyBlurIfPossible();
			context.fill(0, 0, width, height, 0x88000000);
			return;
		}
		renderParentBackground(context);
		applyBlurIfPossible();
		context.fill(0, 0, width, height, 0x66000000);
	}

	private void renderParentBackground(DrawContext context) {
		if (parent == null || parent == this) {
			return;
		}
		if (invokeParentMethod(context, "renderBackgroundTexture")) {
			return;
		}
		if (invokeParentMethod(context, "renderPanorama")) {
			return;
		}
		if (invokeParentMethod(context, "renderBackground")) {
			return;
		}
	}

	private boolean invokeParentMethod(DrawContext context, String name) {
		java.lang.reflect.Method method = findDeclaredMethod(parent.getClass(), name, DrawContext.class);
		if (method != null) {
			try {
				method.invoke(parent, context);
				return true;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		method = findDeclaredMethod(parent.getClass(), name, DrawContext.class, float.class);
		if (method != null) {
			try {
				method.invoke(parent, context, lastDelta);
				return true;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		method = findDeclaredMethod(parent.getClass(), name, DrawContext.class, int.class, int.class, float.class);
		if (method != null) {
			try {
				method.invoke(parent, context, lastMouseX, lastMouseY, lastDelta);
				return true;
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return false;
	}

	private java.lang.reflect.Method findDeclaredMethod(Class<?> type, String name, Class<?>... params) {
		Class<?> current = type;
		while (current != null) {
			try {
				java.lang.reflect.Method method = current.getDeclaredMethod(name, params);
				method.setAccessible(true);
				return method;
			} catch (ReflectiveOperationException ignored) {
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private void drawPanels(DrawContext context, PlayerState state) {
		context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF171B21);
		context.fill(panelX, panelY, panelX + panelWidth, panelY + 28, 0xFF202733);

		int logoSize = 18;
		int logoX = panelX + 8;
		int logoY = panelY + 5;
		context.getMatrices().push();
		context.getMatrices().translate(logoX, logoY, 0);
		float logoScale = (float) logoSize / (float) LOGO_TEXTURE_SIZE;
		context.getMatrices().scale(logoScale, logoScale, 1.0f);
		context.drawTexture(LOGO_TEXTURE, 0, 0, 0, 0, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
		context.getMatrices().pop();
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
		int textX = rightX + 6;
		int maxTextWidth = Math.max(0, rightWidth - 12);
		drawMarqueeText(context, title, textX, titleY, maxTextWidth, 0xFFE6E8EC);
		context.drawTextWithShadow(textRenderer, meta, textX, artistY, 0xFF9AA3AE);

		int position = state != null ? state.positionSeconds() : 0;
		int duration = state != null ? state.durationSeconds() : 0;
		String positionText = formatTime(position);
		String durationText = formatTime(duration);
		context.drawTextWithShadow(textRenderer, positionText, rightX + 6, timeY, 0xFF8B94A0);
		int durationWidth = textRenderer.getWidth(durationText);
		context.drawTextWithShadow(textRenderer, durationText, rightX + rightWidth - durationWidth - 6, timeY, 0xFF8B94A0);

		context.drawTextWithShadow(textRenderer, "Volume", rightX + 6, volumeLabelY, 0xFF9AA3AE);
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
		loadingMore = false;
		searchQuery = query;
		searchOffset = 0;
		searchLimit = 50;
		searchHasMore = true;
		DebugLog.info("Search start service=" + currentService + " playlists=" + showPlaylists
			+ " query=" + DebugLog.preview(query));
		statusMessage = "Searching...";
		refreshLoadingState();
		showingPlaylistTracks = false;
		currentPlaylist = null;
		final boolean playlistSearch = showPlaylists;
		final ServiceType service = currentService;
		CompletableFuture<SearchResult> future = playlistSearch
			? backend.searchPlaylists(service, query, searchOffset, searchLimit)
			: backend.searchTracks(service, query, searchOffset, searchLimit);
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
					searchHasMore = result.playlists().size() >= searchLimit;
					if (playlistList != null) {
						playlistList.setPlaylists(playlistResults);
						playlistList.setScrollAmount(0.0);
					}
					DebugLog.info("Search playlists applied count=" + playlistResults.size());
				} else {
					trackResults.clear();
					trackResults.addAll(result.tracks());
					searchHasMore = result.tracks().size() >= searchLimit;
					if (trackList != null) {
						trackList.setTracks(trackResults);
						trackList.setScrollAmount(0.0);
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
	}

	private void drawMarqueeText(DrawContext context, String text, int x, int y, int maxWidth, int color) {
		if (text == null) {
			return;
		}
		int textWidth = textRenderer.getWidth(text);
		if (textWidth <= maxWidth) {
			context.drawTextWithShadow(textRenderer, text, x, y, color);
			return;
		}
		long now = System.currentTimeMillis();
		int travel = textWidth + maxWidth + 20;
		int offset = (int) ((now / 30L) % travel);
		int drawX = x - offset;
		context.enableScissor(x, y - 2, x + maxWidth, y + textRenderer.fontHeight + 2);
		context.drawTextWithShadow(textRenderer, text, drawX, y, color);
		context.drawTextWithShadow(textRenderer, text, drawX + travel, y, color);
		context.disableScissor();
	}

	private void maybeLoadMore() {
		if (loading || loadingMore || !searchHasMore || searchQuery.isBlank() || showingPlaylistTracks) {
			return;
		}
		if (showPlaylists && playlistList != null) {
			double maxScroll = playlistList.getMaxScrollAmount();
			double currentScroll = playlistList.getScrollAmountValue();
			if (maxScroll - currentScroll <= LIST_ITEM_HEIGHT * 2) {
				loadMoreResults();
			}
		} else if (trackList != null) {
			double maxScroll = trackList.getMaxScrollAmount();
			double currentScroll = trackList.getScrollAmountValue();
			if (maxScroll - currentScroll <= LIST_ITEM_HEIGHT * 2) {
				loadMoreResults();
			}
		}
	}

	private void loadMoreResults() {
		if (backend == null || loading || loadingMore || !searchHasMore || searchQuery.isBlank()) {
			return;
		}
		loadingMore = true;
		int nextOffset = searchOffset + searchLimit;
		final boolean playlistSearch = showPlaylists;
		final ServiceType service = currentService;
		CompletableFuture<SearchResult> future = playlistSearch
			? backend.searchPlaylists(service, searchQuery, nextOffset, searchLimit)
			: backend.searchTracks(service, searchQuery, nextOffset, searchLimit);
		future.whenComplete((result, error) -> {
			if (client == null) {
				return;
			}
			client.execute(() -> {
				loadingMore = false;
				if (error != null || result == null) {
					searchHasMore = false;
					DebugLog.error("Search load more failed", error);
					return;
				}
				int added;
				if (playlistSearch) {
					added = result.playlists().size();
					if (added > 0) {
						playlistResults.addAll(result.playlists());
						if (playlistList != null) {
							playlistList.setPlaylists(playlistResults, true);
						}
					}
				} else {
					added = result.tracks().size();
					if (added > 0) {
						trackResults.addAll(result.tracks());
						if (trackList != null) {
							trackList.setTracks(trackResults, true);
						}
					}
				}
				if (added > 0) {
					searchOffset = nextOffset;
				}
				searchHasMore = added >= searchLimit;
				if (!statusMessage.isBlank()) {
					statusMessage = (playlistSearch ? "Playlists: " : "Tracks: ")
						+ (playlistSearch ? playlistResults.size() : trackResults.size());
				}
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

	private NoverPlayClient.ScreenState buildState() {
		NoverPlayClient.ScreenState state = new NoverPlayClient.ScreenState();
		state.query = searchField != null ? searchField.getText() : searchQuery;
		state.showPlaylists = showPlaylists;
		state.showingPlaylistTracks = showingPlaylistTracks;
		state.statusMessage = statusMessage == null ? "" : statusMessage;
		state.searchOffset = searchOffset;
		state.searchLimit = searchLimit;
		state.searchHasMore = searchHasMore;
		state.trackScroll = trackList != null ? trackList.getScrollAmountValue() : 0.0;
		state.playlistScroll = playlistList != null ? playlistList.getScrollAmountValue() : 0.0;
		state.currentPlaylist = currentPlaylist;
		state.trackResults = new ArrayList<>(trackResults);
		state.playlistResults = new ArrayList<>(playlistResults);
		state.playlistTracks = new ArrayList<>(playlistTracks);
		return state;
	}

	private void applyBlurIfPossible() {
		if (client == null || client.gameRenderer == null) {
			return;
		}
		Object renderer = client.gameRenderer;
		try {
			java.lang.reflect.Method method = renderer.getClass().getMethod("renderBlur", float.class);
			method.invoke(renderer, 0.5f);
			return;
		} catch (ReflectiveOperationException ignored) {
		}
		try {
			java.lang.reflect.Method method = renderer.getClass().getMethod("renderBlur");
			method.invoke(renderer);
		} catch (ReflectiveOperationException ignored) {
		}
	}

	private void openExternalLink(String url) {
		try {
			Util.getOperatingSystem().open(URI.create(url));
		} catch (Exception ex) {
			DebugLog.error("Failed to open link " + url, ex);
		}
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
