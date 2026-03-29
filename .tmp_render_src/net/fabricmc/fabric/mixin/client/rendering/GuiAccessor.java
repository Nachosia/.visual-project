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

package net.fabricmc.fabric.mixin.client.rendering;

import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_329.class)
public interface GuiAccessor {
	@Accessor("displayHealth")
	int fabric$getRenderHealthValue();

	@Invoker("getPlayerVehicleWithHealth")
	class_1309 fabric$callGetRiddenEntity();

	@Invoker("getVehicleMaxHearts")
	int fabric$callGetHeartCount(class_1309 entity);

	@Invoker("getVisibleVehicleHeartRows")
	int fabric$callGetHeartRows(int health);

	@Invoker("getCameraPlayer")
	class_1657 fabric$callGetCameraPlayer();
}
