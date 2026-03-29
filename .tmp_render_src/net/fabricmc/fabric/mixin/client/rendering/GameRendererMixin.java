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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.fabric.impl.client.rendering.GuiRendererExtensions;
import net.minecraft.class_11228;
import net.minecraft.class_11661;
import net.minecraft.class_310;
import net.minecraft.class_4599;
import net.minecraft.class_757;
import net.minecraft.class_759;
import net.minecraft.class_776;

@Mixin(class_757.class)
public class GameRendererMixin {
	@Shadow
	@Final
	private class_11228 guiRenderer;

	@Shadow
	@Final
	private class_11661 submitNodeStorage;

	@Inject(method = "<init>", at = @At(value = "RETURN"))
	private void guiRendererReady(class_310 client, class_759 firstPersonHeldItemRenderer, class_4599 buffers, class_776 blockRenderManager, CallbackInfo ci) {
		GuiRendererExtensions guiRenderer = (GuiRendererExtensions) this.guiRenderer;
		guiRenderer.fabric_onReady(this.submitNodeStorage);
	}
}
