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

package net.fabricmc.fabric.api.client.rendering.v1.world;

import net.minecraft.class_4184;
import net.minecraft.class_4604;
import net.minecraft.class_638;
import net.minecraft.class_761;
import net.minecraft.class_9779;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4fc;

@ApiStatus.NonExtendable
public interface WorldExtractionContext extends AbstractWorldRenderContext {
	/**
	 * Convenient access to {@link class_761#level}.
	 *
	 * @return the world renderer's client world instance
	 */
	@SuppressWarnings("JavadocReference")
	class_638 world();

	class_4184 camera();

	class_4604 frustum();

	class_9779 tickCounter();

	Matrix4fc viewMatrix();

	Matrix4fc cullProjectionMatrix();

	boolean blockOutlines();
}
