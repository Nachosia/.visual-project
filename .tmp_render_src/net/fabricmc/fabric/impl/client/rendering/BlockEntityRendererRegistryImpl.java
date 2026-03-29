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
import java.util.function.BiConsumer;
import net.minecraft.class_11954;
import net.minecraft.class_2586;
import net.minecraft.class_2591;
import net.minecraft.class_5614;

public final class BlockEntityRendererRegistryImpl {
	private static final HashMap<class_2591<?>, class_5614<?, ?>> MAP = new HashMap<>();
	private static BiConsumer<class_2591<?>, class_5614<?, ?>> handler = (type, function) -> MAP.put(type, function);

	public static <E extends class_2586, S extends class_11954> void register(class_2591<E> blockEntityType, class_5614<? super E, ? super S> blockEntityRendererFactory) {
		handler.accept(blockEntityType, blockEntityRendererFactory);
	}

	public static void setup(BiConsumer<class_2591<?>, class_5614<?, ?>> vanillaHandler) {
		MAP.forEach(vanillaHandler);
		handler = vanillaHandler;
	}

	private BlockEntityRendererRegistryImpl() {
	}
}
