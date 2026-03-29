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

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.fabric.impl.client.rendering.ColorProviderRegistryImpl;
import net.minecraft.class_2248;
import net.minecraft.class_2361;
import net.minecraft.class_322;
import net.minecraft.class_324;
import net.minecraft.class_7923;

@Mixin(class_324.class)
public class BlockColorsMixin implements ColorProviderRegistryImpl.ColorMapperHolder<class_2248, class_322> {
	@Shadow
	@Final
	private class_2361<class_322> blockColors;

	@Inject(method = "createDefault", at = @At("RETURN"))
	private static void create(CallbackInfoReturnable<class_324> info) {
		ColorProviderRegistryImpl.BLOCK.initialize(info.getReturnValue());
	}

	@Override
	public class_322 get(class_2248 block) {
		return blockColors.method_10200(class_7923.field_41175.method_10206(block));
	}
}
