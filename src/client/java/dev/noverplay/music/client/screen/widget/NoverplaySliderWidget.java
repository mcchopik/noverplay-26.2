package dev.noverplay.music.client.screen.widget;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public abstract class NoverplaySliderWidget extends SliderWidget {
	private boolean dragging;
	private boolean suppressApply;

	protected NoverplaySliderWidget(int x, int y, int width, int height, Text message, double value) {
		super(x, y, width, height, message, value);
	}

	public void setValueSilently(double value) {
		suppressApply = true;
		this.value = MathHelper.clamp(value, 0.0, 1.0);
		updateMessage();
		suppressApply = false;
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		dragging = true;
		super.onClick(mouseX, mouseY);
		applyValue();
	}

	@Override
	protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
		super.onDrag(mouseX, mouseY, deltaX, deltaY);
		applyValue();
	}

	@Override
	public void onRelease(double mouseX, double mouseY) {
		dragging = false;
		super.onRelease(mouseX, mouseY);
		applyValue();
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
