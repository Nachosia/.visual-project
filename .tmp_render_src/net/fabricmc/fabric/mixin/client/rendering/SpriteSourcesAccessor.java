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

import com.mojang.serialization.MapCodec;
import net.minecraft.class_2960;
import net.minecraft.class_5699;
import net.minecraft.class_7948;
import net.minecraft.class_7952;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_7952.class)
public interface SpriteSourcesAccessor {
	@Accessor("ID_MAPPER")
	static class_5699.class_10388<class_2960, MapCodec<? extends class_7948>> getAtlasSourceCodecs() {
		throw new AssertionError();
	}
}
