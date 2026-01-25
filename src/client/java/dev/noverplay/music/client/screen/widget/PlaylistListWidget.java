package dev.noverplay.music.client.screen.widget;

import dev.noverplay.music.client.model.PlaylistInfo;
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

	public class PlaylistEntry extends EntryListWidget.Entry<PlaylistEntry> {
		private final PlaylistInfo playlist;
		private final int index;

		public PlaylistEntry(PlaylistInfo playlist, int index) {
			this.playlist = playlist;
			this.index = index;
		}

		@Override
		public void render(DrawContext context, int index, int y, boolean hovered, float delta) {
			int x = getRowLeft() + 4;
			int titleColor = hovered ? 0xFFFFFFFF : 0xFFE0E0E0;
			int metaColor = hovered ? 0xFFC0C0C0 : 0xFFA0A0A0;
			context.drawTextWithShadow(client.textRenderer, playlist.title(), x, y + 4, titleColor);
			String author = playlist.author();
			context.drawTextWithShadow(client.textRenderer, author, x, y + 16, metaColor);
			String count = playlist.trackCount() > 0 ? playlist.trackCount() + " tracks" : "";
			int width = client.textRenderer.getWidth(count);
			context.drawTextWithShadow(client.textRenderer, count, getRowRight() - width - 6, y + 4, metaColor);
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
