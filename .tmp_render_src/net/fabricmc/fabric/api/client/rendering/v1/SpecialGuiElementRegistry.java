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

import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;
import net.fabricmc.fabric.impl.client.rendering.SpecialGuiElementRegistryImpl;
import net.minecraft.class_11239;
import net.minecraft.class_11659;
import net.minecraft.class_310;
import net.minecraft.class_4597;

/**
 * Allows registering {@linkplain class_11239 special gui element renderers},
 * used to render custom gui elements beyond the methods available in {@link net.minecraft.class_332 DrawContext}.
 *
 * <p>To render a custom gui element, first implement and register a {@link class_11239}.
 * When you want to render, add an instance of the corresponding render state to {@link net.minecraft.class_332#field_59826 DrawContext#state} using {@link net.minecraft.class_11246#method_70922(net.minecraft.class_11256)}  GuiRenderState#addSpecialElement(SpecialGuiElementRenderState)}.
 */
public final class SpecialGuiElementRegistry {
	/**
	 * Registers a new {@link Factory} used to create a new {@link class_11239} instance.
	 */
	public static void register(Factory factory) {
		Objects.requireNonNull(factory, "factory");
		SpecialGuiElementRegistryImpl.register(factory);
	}

	/**
	 * A factory to create a new {@link class_11239} instance.
	 */
	@FunctionalInterface
	public interface Factory {
		class_11239<?> createSpecialRenderer(Context ctx);
	}

	@ApiStatus.NonExtendable
	public interface Context {
		/**
		 * @return the {@link class_4597.class_4598}.
		 */
		class_4597.class_4598 vertexConsumers();

		/**
		 * @return the {@link class_310} instance.
		 */
		class_310 client();

		/**
		 * @return the {@link class_11659} instance.
		 */
		class_11659 orderedRenderCommandQueue();
	}
}
