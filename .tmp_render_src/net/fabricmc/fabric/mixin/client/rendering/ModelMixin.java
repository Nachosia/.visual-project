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
import java.util.function.Function;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.fabricmc.fabric.api.client.rendering.v1.FabricModel;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_3879;
import net.minecraft.class_630;

@Mixin(class_3879.class)
abstract class ModelMixin<S> implements FabricModel<S> {
	@Shadow
	public abstract class_630 root();

	@Unique
	private final Map<String, class_630> childPartMap = new Object2ObjectOpenHashMap<>();

	@Inject(method = "<init>", at = @At("TAIL"))
	private void fillChildPartMap(class_630 root, Function<class_2960, class_1921> layerFactory, CallbackInfo ci) {
		((ModelPartAccessor) (Object) root).fabric$callForEachChild(childPartMap::putIfAbsent);
	}

	@Override
	@Nullable
	public class_630 getChildPart(String name) {
		return childPartMap.get(name);
	}

	@Override
	public void copyTransforms(class_3879<?> model) {
		copyTransforms(model.method_63512(), root());
		((ModelPartAccessor) (Object) model.method_63512()).fabric$callForEachChild((name, part) -> {
			class_630 childPart = getChildPart(name);

			if (childPart != null) {
				copyTransforms(part, childPart);
			}
		});
	}

	@Unique
	private static void copyTransforms(class_630 from, class_630 to) {
		to.field_3657 = from.field_3657;
		to.field_3656 = from.field_3656;
		to.field_3655 = from.field_3655;
		to.field_3654 = from.field_3654;
		to.field_3675 = from.field_3675;
		to.field_3674 = from.field_3674;
		to.field_37938 = from.field_37938;
		to.field_37939 = from.field_37939;
		to.field_37940 = from.field_37940;
	}
}
