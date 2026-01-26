package dev.noverplay.music.client.screen.widget;

import dev.noverplay.music.client.model.PlaylistInfo;
import dev.noverplay.music.client.util.DebugLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.EntryListWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class PlaylistListWidget extends EntryListWidget<PlaylistListWidget.PlaylistEntry> {
	private final List<PlaylistInfo> playlists = new ArrayList<>();
	private final BiConsumer<PlaylistInfo, Integer> onSelect;

	public PlaylistListWidget(MinecraftClient client, int width, int height, int top, int itemHeight,
							  BiConsumer<PlaylistInfo, Integer> onSelect) {
		super(client, width, height, top, itemHeight);
		this.onSelect = onSelect;
	}

	public void setPlaylists(List<PlaylistInfo> items) {
		playlists.clear();
		clearEntries();
		if (items != null) {
			playlists.addAll(items);
		}
		DebugLog.info("Playlist list updated count=" + playlists.size());
		for (int i = 0; i < playlists.size(); i++) {
			PlaylistInfo playlist = playlists.get(i);
			if (playlist == null) {
				continue;
			}
			DebugLog.info("Playlist list item[" + i + "] id=" + DebugLog.preview(playlist.playlistId())
				+ " title=" + DebugLog.preview(playlist.title())
				+ " author=" + DebugLog.preview(playlist.author())
				+ " tracks=" + playlist.trackCount()
				+ " service=" + playlist.service());
		}
		for (int i = 0; i < playlists.size(); i++) {
			addEntry(new PlaylistEntry(playlists.get(i), i));
		}
		setScrollY(0.0);
		refreshScroll();
	}

	@Override
	protected int getScrollbarX() {
		return getRowRight() - 6;
	}

	@Override
	public int getRowWidth() {
		return width - 12;
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		this.appendDefaultNarrations(builder);
	}

	private String trimWithEllipsis(String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		int safeWidth = Math.max(0, maxWidth);
		if (client.textRenderer.getWidth(text) <= safeWidth) {
			return text;
		}
		int ellipsisWidth = client.textRenderer.getWidth("...");
		int available = Math.max(0, safeWidth - ellipsisWidth);
		if (available <= 0) {
			return client.textRenderer.trimToWidth(text, safeWidth);
		}
		return client.textRenderer.trimToWidth(text, available) + "...";
	}

	public class PlaylistEntry extends EntryListWidget.Entry<PlaylistEntry> {
		private final PlaylistInfo playlist;
		private final int index;

		public PlaylistEntry(PlaylistInfo playlist, int index) {
			this.playlist = playlist;
			this.index = index;
		}

		@Override
		public void render(DrawContext context, int index, int y, boolean hovered, float delta) {
			int x = getContentX();
			int yPos = getContentY();
			int titleColor = 0xFFFFFFFF;
			int metaColor = 0xFFAAAAAA;
			String count = playlist.trackCount() > 0 ? playlist.trackCount() + " tracks" : "";
			int countWidth = client.textRenderer.getWidth(count);
			int maxWidth = getContentWidth() - countWidth - 15;
			String titleText = trimWithEllipsis(playlist.title(), maxWidth);
			String authorText = trimWithEllipsis(playlist.author(), maxWidth);
			context.drawTextWithShadow(client.textRenderer, titleText, x, yPos + 2, titleColor);
			context.drawTextWithShadow(client.textRenderer, authorText, x, yPos + 14, metaColor);
			context.drawTextWithShadow(client.textRenderer, count, getContentRightEnd() - countWidth, yPos + 2, metaColor);
		}

		@Override
		public boolean mouseClicked(Click click, boolean doubleClick) {
			if (click.button() == 0 && onSelect != null) {
				onSelect.accept(playlist, index);
				return true;
			}
			return false;
		}
	}
}
