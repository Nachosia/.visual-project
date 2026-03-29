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
import net.fabricmc.fabric.impl.client.rendering.EntityModelLayerImpl;
import net.fabricmc.fabric.mixin.client.rendering.ModelLayersAccessor;
import net.minecraft.class_11677;
import net.minecraft.class_5601;
import net.minecraft.class_5607;

/**
 * A helpers for registering entity model layers and providers for the layer's textured model data.
 */
public final class EntityModelLayerRegistry {
	/**
	 * Registers an entity model layer and registers a provider for a {@linkplain class_5607}.
	 *
	 * @param modelLayer the entity model layer
	 * @param provider the provider for the textured model data
	 */
	public static void registerModelLayer(class_5601 modelLayer, TexturedModelDataProvider provider) {
		Objects.requireNonNull(modelLayer, "EntityModelLayer cannot be null");
		Objects.requireNonNull(provider, "TexturedModelDataProvider cannot be null");

		if (EntityModelLayerImpl.PROVIDERS.putIfAbsent(modelLayer, provider) != null) {
			throw new IllegalArgumentException(String.format("Cannot replace registration for entity model layer \"%s\"", modelLayer));
		}

		ModelLayersAccessor.getLayers().add(modelLayer);
	}

	/**
	 * Registers entity equipment model layers and registers a provider for a {@link class_11677} of type {@link class_5607}.
	 * @param equipmentModelData the equipment model data of type {@link class_5601}
	 * @param provider the provider for the textured equipment model data
	 */
	public static void registerEquipmentModelLayers(class_11677<class_5601> equipmentModelData, TexturedEquipmentModelDataProvider provider) {
		Objects.requireNonNull(equipmentModelData, "EquipmentModelData cannot be null");
		Objects.requireNonNull(provider, "TexturedEquipmentModelDataProvider cannot be null");

		if (EntityModelLayerImpl.EQUIPMENT_PROVIDERS.putIfAbsent(equipmentModelData, provider) != null) {
			throw new IllegalArgumentException(String.format("Cannot replace registration for entity equipment model layer \"%s\"", equipmentModelData));
		}

		equipmentModelData.method_72962(ModelLayersAccessor.getLayers()::add);
	}

	private EntityModelLayerRegistry() {
	}

	@FunctionalInterface
	public interface TexturedModelDataProvider {
		/**
		 * Creates the textured model data for use in a {@link class_5601}.
		 *
		 * @return the textured model data for the entity model layer.
		 */
		class_5607 createModelData();
	}

	@FunctionalInterface
	public interface TexturedEquipmentModelDataProvider {
		/**
		 * Creates the textured model data for use in a {@link class_11677} of type {@link class_5607}.
		 *
		 * @return the textured model data for the entity model layer.
		 */
		class_11677<class_5607> createEquipmentModelData();
	}
}
