package dev.noverplay.music.client.widget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class IconLinkButton extends PressableWidget {
	private final Identifier texture;
	private final Runnable action;

	public IconLinkButton(int x, int y, int size, Identifier texture, Text tooltip, Runnable action) {
		super(x, y, size, size, tooltip != null ? tooltip : Text.literal("Icon"));
		this.texture = texture;
		this.action = action;
		if (tooltip != null) {
			setTooltip(Tooltip.of(tooltip));
		}
	}

	@Override
	public void onPress() {
		action.run();
	}

	@Override
	protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
		context.drawTexture(texture, getX(), getY(), 0, 0, width, height, width, height);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}
}
