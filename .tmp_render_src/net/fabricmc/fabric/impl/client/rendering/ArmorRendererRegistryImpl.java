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

package net.fabricmc.fabric.impl.client.rendering;

import java.util.HashMap;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.class_1792;
import net.minecraft.class_1935;
import net.minecraft.class_5617;
import net.minecraft.class_7923;

public class ArmorRendererRegistryImpl {
	private static final HashMap<class_1792, ArmorRenderer.Factory> FACTORIES = new HashMap<>();
	private static final HashMap<class_1792, ArmorRenderer> RENDERERS = new HashMap<>();

	public static void register(ArmorRenderer.Factory factory, class_1935... items) {
		Objects.requireNonNull(factory, "renderer factory is null");

		if (items.length == 0) {
			throw new IllegalArgumentException("Armor renderer registered for no item");
		}

		for (class_1935 item : items) {
			Objects.requireNonNull(item.method_8389(), "armor item is null");

			if (FACTORIES.putIfAbsent(item.method_8389(), factory) != null) {
				throw new IllegalArgumentException("Custom armor renderer already exists for " + class_7923.field_41178.method_10221(item.method_8389()));
			}
		}
	}

	public static void register(ArmorRenderer renderer, class_1935... items) {
		Objects.requireNonNull(renderer, "renderer is null");
		register(context -> renderer, items);
	}

	@Nullable
	public static ArmorRenderer get(class_1792 item) {
		return RENDERERS.get(item);
	}

	public static void createArmorRenderers(class_5617.class_5618 context) {
		RENDERERS.clear();
		FACTORIES.forEach((item, factory) -> RENDERERS.put(item, factory.createArmorRenderer(context)));
	}
}
