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

package net.fabricmc.fabric.api.client.rendering.v1;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.impl.client.rendering.AtlasSourceRegistryImpl;
import net.minecraft.class_2960;
import net.minecraft.class_7948;

/**
 * A registry for custom {@link class_7948}s. Registered types will be automatically available for use in atlas definition JSON files.
 */
public final class AtlasSourceRegistry {
	private AtlasSourceRegistry() {
	}

	/**
	 * Registers a new {@link class_7948} by providing a codec for it.
	 *
	 * @param id the identifier of the atlas source type
	 * @param codec the codec for the atlas source type
	 */
	public static void register(class_2960 id, MapCodec<? extends class_7948> codec) {
		AtlasSourceRegistryImpl.register(id, codec);
	}
}
