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
			int titleColor = 0xFFFFFFFF;
			int metaColor = 0xFFAAAAAA;
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(0.0f, 0.0f);
			context.drawTextWithShadow(client.textRenderer, playlist.title(), x, y + 4, titleColor);
			String author = playlist.author();
			context.drawTextWithShadow(client.textRenderer, author, x, y + 16, metaColor);
			String count = playlist.trackCount() > 0 ? playlist.trackCount() + " tracks" : "";
			int width = client.textRenderer.getWidth(count);
			context.drawTextWithShadow(client.textRenderer, count, getRowRight() - width - 6, y + 4, metaColor);
			context.getMatrices().popMatrix();
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
