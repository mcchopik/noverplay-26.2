package dev.noverplay.music.client.screen.widget;

import dev.noverplay.music.client.model.TrackInfo;
import dev.noverplay.music.client.util.DebugLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class TrackListWidget extends EntryListWidget<TrackListWidget.TrackEntry> {
	private final List<TrackInfo> tracks = new ArrayList<>();
	private final BiConsumer<TrackInfo, Integer> onSelect;

	public TrackListWidget(MinecraftClient client, int width, int height, int top, int itemHeight,
						   BiConsumer<TrackInfo, Integer> onSelect) {
		super(client, width, height, top, top + height, itemHeight);
		this.onSelect = onSelect;
	}

	public void setTracks(List<TrackInfo> items) {
		setTracks(items, false);
	}

	public void setTracks(List<TrackInfo> items, boolean keepScroll) {
		double scroll = getScrollAmount();
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
		return getScrollAmount();
	}

	public double getMaxScrollAmount() {
		return getMaxScroll();
	}

	@Override
	protected int getScrollbarPositionX() {
		return getRowRight() - 6;
	}

	@Override
	public int getRowWidth() {
		return width - 12;
	}

	@Override
	public void appendNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, Text.literal("Tracks"));
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

	public class TrackEntry extends EntryListWidget.Entry<TrackEntry> {
		private final TrackInfo track;
		private final int index;

		public TrackEntry(TrackInfo track, int index) {
			this.track = track;
			this.index = index;
		}

		@Override
		public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
						   int mouseX, int mouseY, boolean hovered, float delta) {
			int yPos = y;
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
			int durationWidth = client.textRenderer.getWidth(duration);
			int durationPad = 25;
			int maxWidth = entryWidth - durationWidth - 15 - durationPad;
			String titleText = trimWithEllipsis(title, maxWidth);
			String artistText = trimWithEllipsis(artist, maxWidth);
			context.drawTextWithShadow(client.textRenderer, titleText, x, yPos + 2, titleColor);
			context.drawTextWithShadow(client.textRenderer, artistText, x, yPos + 14, metaColor);
			context.drawTextWithShadow(client.textRenderer, duration, x + entryWidth - durationWidth - durationPad, yPos + 2, metaColor);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 0 && onSelect != null) {
				onSelect.accept(track, index);
				return true;
			}
			return false;
		}

	}
}
