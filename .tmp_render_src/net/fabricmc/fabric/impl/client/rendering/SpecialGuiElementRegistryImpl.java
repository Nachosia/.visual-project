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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.class_11233;
import net.minecraft.class_11234;
import net.minecraft.class_11235;
import net.minecraft.class_11236;
import net.minecraft.class_11237;
import net.minecraft.class_11238;
import net.minecraft.class_11239;
import net.minecraft.class_11250;
import net.minecraft.class_11251;
import net.minecraft.class_11252;
import net.minecraft.class_11253;
import net.minecraft.class_11254;
import net.minecraft.class_11255;
import net.minecraft.class_11256;
import net.minecraft.class_11659;
import net.minecraft.class_310;
import net.minecraft.class_4597;

public final class SpecialGuiElementRegistryImpl {
	private static final List<SpecialGuiElementRegistry.Factory> FACTORIES = new ArrayList<>();
	private static final Map<Class<? extends class_11256>, SpecialGuiElementRegistry.Factory> REGISTERED_FACTORIES = new HashMap<>();
	private static boolean frozen;

	private SpecialGuiElementRegistryImpl() {
	}

	public static void register(SpecialGuiElementRegistry.Factory factory) {
		if (frozen) {
			throw new IllegalStateException("Too late to register, GuiRenderer has already been initialized.");
		}

		FACTORIES.add(factory);
	}

	// Called after the vanilla special renderers are created.
	public static void onReady(class_310 client, class_4597.class_4598 immediate, class_11659 orderedRenderCommandQueue, Map<Class<? extends class_11256>, class_11239<?>> specialElementRenderers) {
		frozen = true;

		registerVanillaFactories();

		ContextImpl context = new ContextImpl(client, immediate, orderedRenderCommandQueue);

		for (SpecialGuiElementRegistry.Factory factory : FACTORIES) {
			class_11239<?> elementRenderer = factory.createSpecialRenderer(context);
			specialElementRenderers.put(elementRenderer.method_70903(), elementRenderer);
			REGISTERED_FACTORIES.put(elementRenderer.method_70903(), factory);
		}
	}

	// null for render states registered outside FAPI
	@Nullable
	public static <S extends class_11256> class_11239<S> createNewRenderer(S state, class_310 client, class_4597.class_4598 immediate, class_11659 orderedRenderCommandQueue) {
		SpecialGuiElementRegistry.Factory factory = REGISTERED_FACTORIES.get(state.getClass());
		return factory == null ? null : (class_11239<S>) factory.createSpecialRenderer(new ContextImpl(client, immediate, orderedRenderCommandQueue));
	}

	private static void registerVanillaFactories() {
		// Vanilla creates its special element renderers in the GameRenderer constructor
		REGISTERED_FACTORIES.put(class_11252.class, context -> new class_11235(context.vertexConsumers(), context.client().method_1561()));
		REGISTERED_FACTORIES.put(class_11255.class, context -> new class_11238(context.vertexConsumers()));
		REGISTERED_FACTORIES.put(class_11251.class, context -> new class_11234(context.vertexConsumers()));
		REGISTERED_FACTORIES.put(class_11250.class, context -> new class_11233(context.vertexConsumers(), context.client().method_72703()));
		REGISTERED_FACTORIES.put(class_11254.class, context -> new class_11237(context.vertexConsumers(), context.client().method_72703()));
		REGISTERED_FACTORIES.put(class_11253.class, context -> new class_11236(context.vertexConsumers()));
	}

	@VisibleForTesting
	public static Collection<Class<? extends class_11256>> getRegisteredFactoryStateClasses() {
		return REGISTERED_FACTORIES.keySet();
	}

	record ContextImpl(class_310 client, class_4597.class_4598 vertexConsumers, class_11659 orderedRenderCommandQueue) implements SpecialGuiElementRegistry.Context { }
}
