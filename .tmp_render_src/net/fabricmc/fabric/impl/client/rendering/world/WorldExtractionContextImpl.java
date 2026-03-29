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

package net.fabricmc.fabric.impl.client.rendering.world;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.class_11658;
import net.minecraft.class_4184;
import net.minecraft.class_4604;
import net.minecraft.class_638;
import net.minecraft.class_757;
import net.minecraft.class_761;
import net.minecraft.class_9779;

public class WorldExtractionContextImpl implements WorldExtractionContext {
	private class_757 gameRenderer;
	private class_761 worldRenderer;
	private class_11658 worldRenderState;
	private class_638 world;
	private class_4184 camera;
	@Nullable
	private class_4604 frustum;
	private class_9779 tickCounter;
	private Matrix4f viewMatrix;
	private Matrix4f cullProjectionMatrix;
	private boolean blockOutlines;

	public void prepare(
			class_757 gameRenderer,
			class_761 worldRenderer,
			class_11658 worldRenderState,
			class_638 world,
			class_9779 tickCounter,
			boolean blockOutlines,
			class_4184 camera,
			Matrix4f viewMatrix,
			Matrix4f cullProjectionMatrix
	) {
		this.gameRenderer = gameRenderer;
		this.worldRenderer = worldRenderer;
		this.worldRenderState = worldRenderState;
		this.world = world;

		this.tickCounter = tickCounter;
		this.blockOutlines = blockOutlines;
		this.camera = camera;
		this.viewMatrix = viewMatrix;
		this.cullProjectionMatrix = cullProjectionMatrix;

		frustum = null;
	}

	public void setFrustum(@Nullable class_4604 frustum) {
		this.frustum = frustum;
	}

	@Override
	public class_757 gameRenderer() {
		return gameRenderer;
	}

	@Override
	public class_761 worldRenderer() {
		return worldRenderer;
	}

	@Override
	public class_11658 worldState() {
		return worldRenderState;
	}

	@Override
	public class_638 world() {
		return world;
	}

	@Override
	public class_4184 camera() {
		return camera;
	}

	@Override
	@Nullable
	public class_4604 frustum() {
		return frustum;
	}

	@Override
	public class_9779 tickCounter() {
		return this.tickCounter;
	}

	@Override
	public Matrix4fc viewMatrix() {
		return viewMatrix;
	}

	@Override
	public Matrix4fc cullProjectionMatrix() {
		return cullProjectionMatrix;
	}

	@Override
	public boolean blockOutlines() {
		return blockOutlines;
	}
}
