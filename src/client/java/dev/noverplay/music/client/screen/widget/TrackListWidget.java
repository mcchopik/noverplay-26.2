package dev.noverplay.music.client.screen.widget;

import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.util.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class TrackListWidget extends ObjectSelectionList<TrackListWidget.TrackEntry> {
	private final List<TrackInfo> tracks = new ArrayList<>();
	private final BiConsumer<TrackInfo, Integer> onSelect;

	public TrackListWidget(Minecraft minecraft, int width, int height, int top, int itemHeight,
						   BiConsumer<TrackInfo, Integer> onSelect) {
		super(minecraft, width, height, top, itemHeight);
		this.onSelect = onSelect;
	}

	public void setTracks(List<TrackInfo> items) {
		setTracks(items, false);
	}

	public void setTracks(List<TrackInfo> items, boolean keepScroll) {
		double scroll = scrollAmount();
		tracks.clear();
		clearEntries();
		if (items != null) {
			tracks.addAll(items);
		}
		DebugLog.info("Track list updated count=" + tracks.size());
		for (int i = 0; i < tracks.size(); i++) {
			TrackInfo track = tracks.get(i);
			if (track == null) {
				continue;
			}
			String title = track.title();
			String artist = track.artist();
			if (title == null || title.isBlank()) {
				title = "Unknown title";
			}
			if (artist == null || artist.isBlank()) {
				artist = "Unknown artist";
			}
			DebugLog.info("Track list item[" + i + "] id=" + track.trackId()
				+ " title=" + DebugLog.preview(title)
				+ " artist=" + DebugLog.preview(artist)
				+ " duration=" + track.duration()
				+ " service=" + track.service());
		}
		for (int i = 0; i < tracks.size(); i++) {
			addEntry(new TrackEntry(tracks.get(i), i));
		}
		setScrollAmount(keepScroll ? scroll : 0.0);
	}

	public List<TrackInfo> getTracks() {
		return List.copyOf(tracks);
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

	public class TrackEntry extends ObjectSelectionList.Entry<TrackEntry> {
		private final TrackInfo track;
		private final int index;

		public TrackEntry(TrackInfo track, int index) {
			this.track = track;
			this.index = index;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			int x = getContentX();
			int yPos = getContentY();
			int entryWidth = getContentWidth();
			int titleColor = 0xFFFFFFFF;
			int metaColor = 0xFFAAAAAA;
			String title = track.title();
			String artist = track.artist();
			if (title == null || title.isBlank()) {
				title = "Unknown title";
			}
			if (artist == null || artist.isBlank()) {
				artist = "Unknown artist";
			}
			String duration = track.durationText();
			int durationWidth = minecraft.font.width(duration);
			int durationPad = 25;
			int maxWidth = entryWidth - durationWidth - 15 - durationPad;
			String titleText = trimWithEllipsis(title, maxWidth);
			String artistText = trimWithEllipsis(artist, maxWidth);
			graphics.text(minecraft.font, titleText, x, yPos + 2, titleColor, true);
			graphics.text(minecraft.font, artistText, x, yPos + 14, metaColor, true);
			graphics.text(minecraft.font, duration, x + entryWidth - durationWidth - durationPad, yPos + 2, metaColor, true);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() == 0 && onSelect != null) {
				onSelect.accept(track, index);
				return true;
			}
			return false;
		}

		@Override
		public Component getNarration() {
			return Component.literal(track.title() + " - " + track.artist());
		}

	}
}
