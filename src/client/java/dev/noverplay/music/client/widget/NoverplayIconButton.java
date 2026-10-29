package dev.noverplay.music.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class NoverplayIconButton extends AbstractButton {
	private final Identifier texture;
	private final Runnable action;

	public NoverplayIconButton(int x, int y, int width, int height, Identifier texture, Runnable action) {
		super(x, y, width, height, Component.literal("Noverplay"));
		this.texture = texture;
		this.action = action;
		setTooltip(Tooltip.create(Component.literal("Open Noverplay")));
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
