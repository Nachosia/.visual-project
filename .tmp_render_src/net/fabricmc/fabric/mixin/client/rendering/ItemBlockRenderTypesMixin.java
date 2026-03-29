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

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.fabric.impl.client.rendering.BlockRenderLayerMapImpl;
import net.minecraft.class_11515;
import net.minecraft.class_2248;
import net.minecraft.class_3611;
import net.minecraft.class_4696;

@Mixin(class_4696.class)
abstract class ItemBlockRenderTypesMixin {
	@Shadow
	@Final
	private static Map<class_2248, class_11515> TYPE_BY_BLOCK;
	@Shadow
	@Final
	private static Map<class_3611, class_11515> LAYER_BY_FLUID;

	@Inject(method = "<clinit>*", at = @At("RETURN"))
	private static void onInitialize(CallbackInfo ci) {
		BlockRenderLayerMapImpl.setup(TYPE_BY_BLOCK::put, LAYER_BY_FLUID::put);
	}
}
