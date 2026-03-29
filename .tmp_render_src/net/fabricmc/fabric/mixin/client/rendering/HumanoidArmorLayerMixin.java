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

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.class_10034;
import net.minecraft.class_11659;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_3883;
import net.minecraft.class_3887;
import net.minecraft.class_4587;
import net.minecraft.class_572;
import net.minecraft.class_970;

@Mixin(class_970.class)
public abstract class HumanoidArmorLayerMixin<S extends class_10034, M extends class_572<S>, A extends class_572<S>> extends class_3887<S, M> {
	@Unique
	private class_10034 bipedRenderState;

	public HumanoidArmorLayerMixin(class_3883<S, M> featureRendererContext) {
		super(featureRendererContext);
	}

	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"))
	private void render(class_4587 matrixStack, class_11659 orderedRenderCommandQueue, int i, S bipedEntityRenderState, float f, float g, CallbackInfo ci) {
		this.bipedRenderState = bipedEntityRenderState;
	}

	@Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
	private void renderArmor(class_4587 matrices, class_11659 orderedRenderCommandQueue, class_1799 stack, class_1304 armorSlot, int light, S bipedEntityRenderState, CallbackInfo ci) {
		ArmorRenderer renderer = ArmorRendererRegistryImpl.get(stack.method_7909());

		if (renderer != null) {
			renderer.render(matrices, orderedRenderCommandQueue, stack, bipedRenderState, armorSlot, light, (class_572<class_10034>) method_17165());
			ci.cancel();
		}
	}
}
