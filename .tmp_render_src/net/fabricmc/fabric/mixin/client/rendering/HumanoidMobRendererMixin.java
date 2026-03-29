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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_909;

// Allows items with armor renderers to be passed to the armor feature in the first place.
// Vanilla only stores the items with armor models in the entity's render state,
// but we want to store any item with an armor renderer. Otherwise, armor renderers would
// only be called for items with an existing vanilla armor model.
@Mixin(class_909.class)
abstract class HumanoidMobRendererMixin {
	@WrapOperation(method = "getEquipmentIfRenderable", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;shouldRender(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;)Z"))
	private static boolean permitArmorWithCustomRenderers(class_1799 stack, class_1304 slot, Operation<Boolean> original) {
		return original.call(stack, slot) || ArmorRendererRegistryImpl.get(stack.method_7909()) != null;
	}
}
