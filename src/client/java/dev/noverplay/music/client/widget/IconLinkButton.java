package dev.noverplay.music.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class IconLinkButton extends AbstractButton {
	private final Identifier texture;
	private final Runnable action;

	public IconLinkButton(int x, int y, int size, Identifier texture, Component tooltip, Runnable action) {
		super(x, y, size, size, tooltip != null ? tooltip : Component.literal("Icon"));
		this.texture = texture;
		this.action = action;
		if (tooltip != null) {
			setTooltip(Tooltip.create(tooltip));
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.blit(texture, getX(), getY(), getRight(), getBottom(), 0.0f, 1.0f, 0.0f, 1.0f);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
