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
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.class_11677;
import net.minecraft.class_5601;

public final class EntityModelLayerImpl {
	public static final Map<class_5601, EntityModelLayerRegistry.TexturedModelDataProvider> PROVIDERS = new HashMap<>();
	public static final Map<class_11677<class_5601>, EntityModelLayerRegistry.TexturedEquipmentModelDataProvider> EQUIPMENT_PROVIDERS = new HashMap<>();

	private EntityModelLayerImpl() {
	}
}
