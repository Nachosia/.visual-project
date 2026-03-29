/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.client.rendering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.impl.client.rendering.hud.HudElementRegistryImpl;
import net.minecraft.class_11223;
import net.minecraft.class_1657;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_329;
import net.minecraft.class_332;
import net.minecraft.class_365;
import net.minecraft.class_9779;

@Mixin(class_329.class)
abstract class GuiMixin {
	@Shadow
	@Final
	private class_310 minecraft;

	@Inject(method = "render", at = @At(value = "TAIL"))
	public void render(class_332 drawContext, class_9779 tickCounter, CallbackInfo callbackInfo) {
		HudRenderCallback.EVENT.invoker().onHudRender(drawContext, tickCounter);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderCameraOverlays(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapMiscOverlays(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.MISC_OVERLAYS).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapCrosshair(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.CROSSHAIR).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/spectator/SpectatorGui;renderHotbar(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void wrapSpectatorMenu(class_365 instance, class_332 context, Operation<Void> renderVanilla, @Local(argsOnly = true) class_9779 tickCounter) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.SPECTATOR_MENU).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx));
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapHotbar(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.HOTBAR).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V"))
	private void wrapArmorBar(class_332 context, class_1657 player, int i, int j, int k, int x, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.ARMOR_BAR).render(context, minecraft.method_61966(), (ctx, tc) -> renderVanilla.call(ctx, player, i, j, k, x));
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"))
	private void wrapHealthBar(class_329 instance, class_332 context, class_1657 player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.HEALTH_BAR).render(context, minecraft.method_61966(), (ctx, tc) -> renderVanilla.call(instance, ctx, player, x, y, lines, regeneratingHeartIndex, maxHealth, lastHealth, health, absorption, blinking));
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V"))
	private void wrapFoodBar(class_329 instance, class_332 context, class_1657 player, int top, int right, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.FOOD_BAR).render(context, minecraft.method_61966(), (ctx, tc) -> renderVanilla.call(instance, ctx, player, top, right));
	}

	@WrapOperation(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderAirBubbles(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;III)V"))
	private void wrapAirBar(class_329 instance, class_332 context, class_1657 player, int heartCount, int top, int left, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.AIR_BAR).render(context, minecraft.method_61966(), (ctx, tc) -> renderVanilla.call(instance, ctx, player, heartCount, top, left));
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void wrapMountHealth(class_329 instance, class_332 context, Operation<Void> renderVanilla, @Local(argsOnly = true) class_9779 tickCounter) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.MOUNT_HEALTH).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx));
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapRenderBar(class_11223 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.INFO_BAR).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"))
	private void wrapExperienceLevel(class_332 context, class_327 textRenderer, int level, Operation<Void> renderVanilla, @Local(argsOnly = true) class_9779 tickCounter) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.EXPERIENCE_LEVEL).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(ctx, textRenderer, level));
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void wrapHeldItemTooltip(class_329 instance, class_332 context, Operation<Void> renderVanilla, @Local(argsOnly = true) class_9779 tickCounter) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.HELD_ITEM_TOOLTIP).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx));
	}

	@WrapOperation(method = "renderHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/spectator/SpectatorGui;renderAction(Lnet/minecraft/client/gui/GuiGraphics;)V"))
	private void wrapRenderSpectatorHud(class_365 instance, class_332 context, Operation<Void> renderVanilla, @Local(argsOnly = true) class_9779 tickCounter) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.SPECTATOR_TOOLTIP).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapStatusEffectOverlay(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.STATUS_EFFECTS).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderBossOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapBossBarHud(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.BOSS_BAR).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderSleepOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapSleepOverlay(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.SLEEP).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderDemoOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapDemoTimer(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.DEMO_TIMER).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapScoreboardSidebar(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.SCOREBOARD).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderOverlayMessage(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapOverlayMessage(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.OVERLAY_MESSAGE).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderTitle(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapTitleAndSubtitle(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.TITLE_AND_SUBTITLE).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderChat(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapChat(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.CHAT).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;renderTabList(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void wrapPlayerList(class_329 instance, class_332 context, class_9779 tickCounter, Operation<Void> renderVanilla) {
		HudElementRegistryImpl.getRoot(VanillaHudElements.PLAYER_LIST).render(context, tickCounter, (ctx, tc) -> renderVanilla.call(instance, ctx, tc));
	}
}
