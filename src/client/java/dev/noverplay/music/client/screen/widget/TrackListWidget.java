package dev.noverplay.music.client.screen.widget;

import dev.noverplay.music.client.model.TrackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.EntryListWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class TrackListWidget extends EntryListWidget<TrackListWidget.TrackEntry> {
	private final List<TrackInfo> tracks = new ArrayList<>();
	private final BiConsumer<TrackInfo, Integer> onSelect;

	public TrackListWidget(MinecraftClient client, int width, int height, int top, int itemHeight,
						   BiConsumer<TrackInfo, Integer> onSelect) {
		super(client, width, height, top, itemHeight);
		this.onSelect = onSelect;
	}

	public void setTracks(List<TrackInfo> items) {
		tracks.clear();
		clearEntries();
		if (items != null) {
			tracks.addAll(items);
		}
		for (int i = 0; i < tracks.size(); i++) {
			addEntry(new TrackEntry(tracks.get(i), i));
		}
		setScrollY(0.0);
		refreshScroll();
	}

	public List<TrackInfo> getTracks() {
		return List.copyOf(tracks);
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

	public class TrackEntry extends EntryListWidget.Entry<TrackEntry> {
		private final TrackInfo track;
		private final int index;

		public TrackEntry(TrackInfo track, int index) {
			this.track = track;
			this.index = index;
		}

		@Override
		public void render(DrawContext context, int index, int y, boolean hovered, float delta) {
			int x = getRowLeft() + 4;
			int titleColor = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
			int metaColor = hovered ? 0xFFC0C0C0 : 0xFFA0A0A0;
			String title = track.title();
			String artist = track.artist();
			if (title == null || title.isBlank()) {
				title = "Unknown title";
			}
			if (artist == null || artist.isBlank()) {
				artist = "Unknown artist";
			}
			context.drawTextWithShadow(client.textRenderer, title, x, y + 4, titleColor);
			context.drawTextWithShadow(client.textRenderer, artist, x, y + 16, metaColor);
			String duration = track.durationText();
			int width = client.textRenderer.getWidth(duration);
			context.drawTextWithShadow(client.textRenderer, duration, getRowRight() - width - 6, y + 4, metaColor);
		}

		@Override
		public boolean mouseClicked(Click click, boolean doubleClick) {
			if (click.button() == 0 && onSelect != null) {
				onSelect.accept(track, index);
				return true;
			}
			return false;
		}

	}
}
