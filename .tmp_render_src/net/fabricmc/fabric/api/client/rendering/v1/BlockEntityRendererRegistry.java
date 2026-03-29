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

import net.fabricmc.fabric.impl.client.rendering.BlockEntityRendererRegistryImpl;
import net.minecraft.class_11954;
import net.minecraft.class_2586;
import net.minecraft.class_2591;
import net.minecraft.class_5614;
import net.minecraft.class_824;
import net.minecraft.class_827;

/**
 * Helper class for registering BlockEntityRenderers.
 *
 * <p>Use {@link net.minecraft.class_5616#method_32144(class_2591, class_5614)} instead.
 *
 * @deprecated Replaced with transitive access wideners in Fabric Transitive Access Wideners (v1).
 */
@Deprecated
public final class BlockEntityRendererRegistry {
	/**
	 * Register a BlockEntityRenderer for a BlockEntityType. Can be called clientside before the world is rendered.
	 *
	 * @param blockEntityType the {@link class_2591} to register a renderer for
	 * @param blockEntityRendererFactory a {@link class_5614} that creates a {@link class_827}, called
	 *                            when {@link class_824} is initialized or immediately if the dispatcher
	 *                            class is already loaded
	 * @param <E> the {@link class_2586}
	 */
	public static <E extends class_2586, S extends class_11954> void register(class_2591<E> blockEntityType, class_5614<? super E, ? super S> blockEntityRendererFactory) {
		BlockEntityRendererRegistryImpl.register(blockEntityType, blockEntityRendererFactory);
	}

	private BlockEntityRendererRegistry() {
	}
}
