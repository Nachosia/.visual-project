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

import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.class_1792;
import net.minecraft.class_5632;
import net.minecraft.class_5684;

/**
 * Allows registering a mapping from {@link class_5632} to {@link class_5684}.
 * This allows custom tooltips for items: first, override {@link class_1792#method_32346} and return a custom {@code TooltipData}.
 * Second, register a listener to this event and convert the data to your component implementation if it's an instance of your data class.
 *
 * <p>Note that failure to map some data to a component will throw an exception,
 * so make sure that any data you return in {@link class_1792#method_32346} will be handled by one of the callbacks.
 */
public interface TooltipComponentCallback {
	Event<TooltipComponentCallback> EVENT = EventFactory.createArrayBacked(TooltipComponentCallback.class, listeners -> data -> {
		for (TooltipComponentCallback listener : listeners) {
			class_5684 component = listener.getComponent(data);

			if (component != null) {
				return component;
			}
		}

		return null;
	});

	/**
	 * Return the tooltip component for the passed data, or null if none is available.
	 */
	@Nullable
	class_5684 getComponent(class_5632 data);
}
