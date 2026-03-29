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

import net.fabricmc.fabric.impl.client.rendering.EntityRendererRegistryImpl;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_5617;
import net.minecraft.class_897;
import net.minecraft.class_898;

/**
 * Helper class for registering EntityRenderers.
 *
 * <p>Use {@link net.minecraft.class_5619#method_32173(class_1299, class_5617)} instead.
 *
 * @deprecated Replaced with transitive access wideners in Fabric Transitive Access Wideners (v1).
 */
@Deprecated
public final class EntityRendererRegistry {
	/**
	 * Register an {@link class_897} for an {@link class_1299}. Can be called clientside before the world is rendered.
	 *
	 * @param entityType            the {@link class_1299} to register a renderer for
	 * @param entityRendererFactory a {@link class_5617} that creates a {@link class_897}, called
	 *                              when {@link class_898} is initialized or immediately if the dispatcher
	 *                              class is already loaded
	 * @param <E>                   the {@link class_1297}
	 */
	public static <E extends class_1297> void register(class_1299<? extends E> entityType, class_5617<E> entityRendererFactory) {
		EntityRendererRegistryImpl.register(entityType, entityRendererFactory);
	}

	private EntityRendererRegistry() {
	}
}
