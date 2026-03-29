package com.visualproject.client.mixin;

import com.visualproject.client.hud.itembar.ItemBarHudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiHotbarMixin {
	@Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
	private void visualclient$hideVanillaHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (ItemBarHudModule.shouldHideVanillaHotbar()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void visualclient$hideSelectedItemName(GuiGraphics guiGraphics, CallbackInfo ci) {
		if (ItemBarHudModule.shouldHideVanillaHotbar()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
	private void visualclient$hideVanillaPlayerStatus(GuiGraphics guiGraphics, CallbackInfo ci) {
		if (ItemBarHudModule.shouldHideVanillaStatusBars()) {
			ci.cancel();
		}
	}

	@Redirect(
		method = "renderHotbarAndDecorations",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"
		)
	)
	private void visualclient$hideVanillaExperienceLevel(
		GuiGraphics guiGraphics,
		Font font,
		int x
	) {
		if (!ItemBarHudModule.shouldHideVanillaStatusBars()) {
			ContextualBarRenderer.renderExperienceLevel(guiGraphics, font, x);
		}
	}
}
