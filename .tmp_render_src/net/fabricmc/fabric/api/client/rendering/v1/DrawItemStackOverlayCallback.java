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

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.class_1799;
import net.minecraft.class_327;
import net.minecraft.class_332;

@FunctionalInterface
public interface DrawItemStackOverlayCallback {
	/**
	 * Fires at the end of {@link class_332#method_51432(class_327, class_1799, int, int, String)} and allows
	 * for drawing custom item stack decorations.
	 *
	 * <p>In vanilla these are: durability bar, cooldown overlay and stack count.
	 */
	Event<DrawItemStackOverlayCallback> EVENT = EventFactory.createArrayBacked(DrawItemStackOverlayCallback.class,
			callbacks -> (context, textRenderer, stack, x, y) -> {
				for (DrawItemStackOverlayCallback callback : callbacks) {
					callback.onDrawItemStackOverlay(context, textRenderer, stack, x, y);
				}
			});

	/**
	 * @param context      the draw context
	 * @param textRenderer the text renderer
	 * @param stack        the item stack
	 * @param x            the x-position of the item stack
	 * @param y            the y-position of the item stack
	 */
	void onDrawItemStackOverlay(class_332 context, class_327 textRenderer, class_1799 stack, int x, int y);
}
