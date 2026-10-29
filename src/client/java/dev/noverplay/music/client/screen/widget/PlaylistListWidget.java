package dev.noverplay.music.client.screen.widget;

import dev.noverplay.music.client.model.PlaylistInfo;
import dev.noverplay.music.client.util.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class PlaylistListWidget extends ObjectSelectionList<PlaylistListWidget.PlaylistEntry> {
	private final List<PlaylistInfo> playlists = new ArrayList<>();
	private final BiConsumer<PlaylistInfo, Integer> onSelect;

	public PlaylistListWidget(Minecraft minecraft, int width, int height, int top, int itemHeight,
							  BiConsumer<PlaylistInfo, Integer> onSelect) {
		super(minecraft, width, height, top, itemHeight);
		this.onSelect = onSelect;
	}

	public void setPlaylists(List<PlaylistInfo> items) {
		setPlaylists(items, false);
	}

	public void setPlaylists(List<PlaylistInfo> items, boolean keepScroll) {
		double scroll = scrollAmount();
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
		setScrollAmount(keepScroll ? scroll : 0.0);
	}

	public List<PlaylistInfo> getPlaylists() {
		return List.copyOf(playlists);
	}

	public double getScrollAmountValue() {
		return scrollAmount();
	}

	public double getMaxScrollAmount() {
		return maxScrollAmount();
	}

	@Override
	protected int scrollBarX() {
		return getRowRight() - 6;
	}

	@Override
	public int getRowWidth() {
		return getWidth() - 12;
	}

	private String trimWithEllipsis(String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		int safeWidth = Math.max(0, maxWidth);
		if (minecraft.font.width(text) <= safeWidth) {
			return text;
		}
		int ellipsisWidth = minecraft.font.width("...");
		int available = Math.max(0, safeWidth - ellipsisWidth);
		if (available <= 0) {
			return minecraft.font.plainSubstrByWidth(text, safeWidth);
		}
		return minecraft.font.plainSubstrByWidth(text, available) + "...";
	}

	public class PlaylistEntry extends ObjectSelectionList.Entry<PlaylistEntry> {
		private final PlaylistInfo playlist;
		private final int index;

		public PlaylistEntry(PlaylistInfo playlist, int index) {
			this.playlist = playlist;
			this.index = index;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			int x = getContentX();
			int yPos = getContentY();
			int entryWidth = getContentWidth();
			int titleColor = 0xFFFFFFFF;
			int metaColor = 0xFFAAAAAA;
			String title = playlist.title();
			String author = playlist.author();
			if (title == null || title.isBlank()) {
				title = "Unknown playlist";
			}
			if (author == null || author.isBlank()) {
				author = "Unknown author";
			}
			String count = playlist.trackCount() > 0 ? playlist.trackCount() + " tracks" : "";
			int countWidth = minecraft.font.width(count);
			int countPad = 6;
			int maxWidth = entryWidth - countWidth - 15;
			String titleText = trimWithEllipsis(title, maxWidth);
			String authorText = trimWithEllipsis(author, maxWidth);
			graphics.text(minecraft.font, titleText, x, yPos + 2, titleColor, true);
			graphics.text(minecraft.font, authorText, x, yPos + 14, metaColor, true);
			graphics.text(minecraft.font, count, x + entryWidth - countWidth - countPad, yPos + 2, metaColor, true);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() == 0 && onSelect != null) {
				onSelect.accept(playlist, index);
				return true;
			}
			return false;
		}

		@Override
		public Component getNarration() {
			return Component.literal(playlist.title() + " - " + playlist.author());
		}
	}
}
