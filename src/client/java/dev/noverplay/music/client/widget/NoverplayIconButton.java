package dev.noverplay.music.client.widget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class NoverplayIconButton extends PressableWidget {
	private static final int TEXTURE_SIZE = 256;

	private final Identifier texture;
	private final Runnable action;

	public NoverplayIconButton(int x, int y, int width, int height, Identifier texture, Runnable action) {
		super(x, y, width, height, Text.literal("Noverplay"));
		this.texture = texture;
		this.action = action;
		setTooltip(Tooltip.of(Text.literal("Open Noverplay")));
	}

	@Override
	public void onPress() {
		action.run();
	}

	@Override
	protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
		context.drawTexture(texture, this.getX(), this.getY(), 0, 0, this.width, this.height, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		this.appendDefaultNarrations(builder);
	}
}
