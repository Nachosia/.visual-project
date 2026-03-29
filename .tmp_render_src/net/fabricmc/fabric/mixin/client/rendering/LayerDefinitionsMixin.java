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

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.impl.client.rendering.EntityModelLayerImpl;
import net.minecraft.class_11677;
import net.minecraft.class_5600;
import net.minecraft.class_5601;
import net.minecraft.class_5607;

@Mixin(class_5600.class)
abstract class LayerDefinitionsMixin {
	@Inject(method = "createRoots", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"))
	private static void registerExtraModelData(CallbackInfoReturnable<Map<class_5601, class_5607>> info, @Local ImmutableMap.Builder<class_5601, class_5607> builder) {
		for (Map.Entry<class_5601, EntityModelLayerRegistry.TexturedModelDataProvider> entry : EntityModelLayerImpl.PROVIDERS.entrySet()) {
			builder.put(entry.getKey(), entry.getValue().createModelData());
		}

		for (Map.Entry<class_11677<class_5601>, EntityModelLayerRegistry.TexturedEquipmentModelDataProvider> entry : EntityModelLayerImpl.EQUIPMENT_PROVIDERS.entrySet()) {
			entry.getKey().method_72960(entry.getValue().createEquipmentModelData(), builder);
		}
	}
}
