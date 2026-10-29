package dev.noverplay.music.mixin.client;

import dev.noverplay.music.NoverPlay;
import dev.noverplay.music.NoverPlayClient;
import dev.noverplay.music.client.widget.NoverplayIconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
	private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(NoverPlay.MOD_ID, "textures/gui/noverplay_logo.png");

	protected PauseScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "createPauseMenu()V", at = @At("TAIL"))
	private void noverplay$addIconButton(CallbackInfo ci) {
		int size = 22;
		int margin = 8;
		int x = this.width - size - margin;
		int y = margin;
		this.addRenderableWidget(new NoverplayIconButton(x, y, size, size, LOGO_TEXTURE, () -> {
			Minecraft client = Minecraft.getInstance();
			NoverPlayClient.openNoverplayScreen(client);
		}));
	}
}
