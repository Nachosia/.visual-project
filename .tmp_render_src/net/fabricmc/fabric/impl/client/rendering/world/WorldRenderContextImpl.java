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

import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.client.rendering.v1.world.AbstractWorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldTerrainRenderContext;
import net.minecraft.class_11532;
import net.minecraft.class_11658;
import net.minecraft.class_11659;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_757;
import net.minecraft.class_761;

public final class WorldRenderContextImpl implements AbstractWorldRenderContext, WorldTerrainRenderContext, WorldRenderContext {
	private class_757 gameRenderer;
	private class_761 worldRenderer;
	private class_11658 worldRenderState;

	private class_11532 sectionRenderState;
	private class_11659 commandQueue;
	@Nullable
	private class_4587 matrixStack;
	private class_4597 consumers;

	public void prepare(
			class_757 gameRenderer,
			class_761 worldRenderer,
			class_11658 worldRenderState,
			class_11532 sectionRenderState,
			class_11659 commandQueue,
			class_4597 consumers
	) {
		this.gameRenderer = gameRenderer;
		this.worldRenderer = worldRenderer;
		this.worldRenderState = worldRenderState;
		this.sectionRenderState = sectionRenderState;

		this.commandQueue = commandQueue;
		this.consumers = consumers;

		matrixStack = null;
	}

	public void setMatrixStack(@Nullable class_4587 matrixStack) {
		this.matrixStack = matrixStack;
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
	public class_11532 sectionState() {
		return sectionRenderState;
	}

	@Override
	public class_11659 commandQueue() {
		return commandQueue;
	}

	@Override
	@Nullable
	public class_4587 matrices() {
		return matrixStack;
	}

	@Override
	public class_4597 consumers() {
		return consumers;
	}
}
