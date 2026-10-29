package dev.noverplay.music.client.screen.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class NoverplaySliderWidget extends AbstractSliderButton {
	private boolean dragging;
	private boolean suppressApply;

	protected NoverplaySliderWidget(int x, int y, int width, int height, Component message, double value) {
		super(x, y, width, height, message, value);
	}

	public void setValueSilently(double value) {
		suppressApply = true;
		setValue(value);
		suppressApply = false;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		dragging = true;
		super.onClick(event, doubleClick);
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
		super.onDrag(event, deltaX, deltaY);
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		super.onRelease(event);
		dragging = false;
	}

	public boolean isDragging() {
		return dragging;
	}

	@Override
	protected void applyValue() {
		if (suppressApply) {
			return;
		}
		onApplyValue(this.value);
	}

	protected abstract void onApplyValue(double value);
}
