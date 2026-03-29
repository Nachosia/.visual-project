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

import java.util.Set;

import org.jetbrains.annotations.UnmodifiableView;
import net.fabricmc.fabric.impl.client.rendering.ColorResolverRegistryImpl;
import net.minecraft.class_1163;
import net.minecraft.class_1920;
import net.minecraft.class_6539;

/**
 * The registry for custom {@link class_6539}s. Custom resolvers must be registered during client initialization for
 * them to be usable in {@link class_1920#method_23752}. Calling this method may throw an exception if the passed
 * resolver is not registered with this class. Vanilla resolvers found in {@link class_1163} are automatically
 * registered.
 *
 * <p>Other mods may also require custom resolvers to be registered if they provide additional functionality related to
 * color resolvers.
 */
public final class ColorResolverRegistry {
	private ColorResolverRegistry() {
	}

	/**
	 * Registers a custom {@link class_6539} for use in {@link class_1920#method_23752}. This method should be
	 * called during client initialization.
	 *
	 * @param resolver the resolver to register
	 */
	public static void register(class_6539 resolver) {
		ColorResolverRegistryImpl.register(resolver);
	}

	/**
	 * Gets a view of all registered {@link class_6539}s, including all vanilla resolvers.
	 *
	 * @return a view of all registered resolvers
	 */
	@UnmodifiableView
	public static Set<class_6539> getAllResolvers() {
		return ColorResolverRegistryImpl.getAllResolvers();
	}

	/**
	 * Gets a view of all registered {@link class_6539}s, not including vanilla resolvers.
	 *
	 * @return a view of all registered custom resolvers
	 */
	@UnmodifiableView
	public static Set<class_6539> getCustomResolvers() {
		return ColorResolverRegistryImpl.getCustomResolvers();
	}

	/**
	 * Checks whether the given {@link class_6539} is registered. Vanilla resolvers are always registered.
	 *
	 * @param resolver the resolver
	 * @return whether the given resolver is registered
	 */
	public static boolean isRegistered(class_6539 resolver) {
		return getAllResolvers().contains(resolver);
	}
}
