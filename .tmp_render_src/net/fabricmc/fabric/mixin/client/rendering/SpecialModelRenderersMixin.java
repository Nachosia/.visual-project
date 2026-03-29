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

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.fabric.impl.client.rendering.SpecialBlockRendererRegistryImpl;
import net.minecraft.class_10515;
import net.minecraft.class_10517;
import net.minecraft.class_2248;

@Mixin(class_10517.class)
abstract class SpecialModelRenderersMixin {
	@Shadow
	@Final
	@Mutable
	private static Map<class_2248, class_10515.class_10516> STATIC_BLOCK_MAPPING;

	@Inject(at = @At("RETURN"), method = "<clinit>*")
	private static void onReturnClinit(CallbackInfo ci) {
		// The map is normally an ImmutableMap.
		if (!(STATIC_BLOCK_MAPPING instanceof HashMap)) {
			STATIC_BLOCK_MAPPING = new HashMap<>(STATIC_BLOCK_MAPPING);
		}

		SpecialBlockRendererRegistryImpl.setup(STATIC_BLOCK_MAPPING::put);
	}
}
