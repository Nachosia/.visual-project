package com.visualproject.client.mixin;

import com.visualproject.client.hud.itembar.ItemBarHudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBarRenderer.class)
public class ContextualBarRendererMixin {
	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void visualclient$hideVanillaExperienceBackground(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (ItemBarHudModule.shouldHideVanillaStatusBars()) {
			ci.cancel();
		}
	}

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void visualclient$hideVanillaExperienceProgress(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (ItemBarHudModule.shouldHideVanillaStatusBars()) {
			ci.cancel();
		}
	}
}
