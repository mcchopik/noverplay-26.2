package dev.noverplay.music.mixin.client;

import dev.noverplay.music.NoverPlay;
import dev.noverplay.music.client.screen.NoverplayScreen;
import dev.noverplay.music.client.widget.NoverplayIconButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	private static final Identifier LOGO_TEXTURE = Identifier.of(NoverPlay.MOD_ID, "textures/gui/noverplay_logo.png");

	protected TitleScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void noverplay$addLogoButton(CallbackInfo ci) {
		int size = 28;
		int margin = 8;
		int x = this.width - size - margin;
		int y = margin;
		this.addDrawableChild(new NoverplayIconButton(x, y, size, size, LOGO_TEXTURE, () -> {
			MinecraftClient client = MinecraftClient.getInstance();
			client.setScreen(new NoverplayScreen(this));
		}));
	}
}
