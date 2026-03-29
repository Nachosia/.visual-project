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

import org.jetbrains.annotations.ApiStatus;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.class_10017;
import net.minecraft.class_1299;
import net.minecraft.class_1309;
import net.minecraft.class_3887;
import net.minecraft.class_5617;
import net.minecraft.class_583;
import net.minecraft.class_922;
import net.minecraft.class_978;

/**
 * Called when {@link class_3887 feature renderers} for a {@link class_922 living entity renderer} are registered.
 *
 * <p>Feature renderers are typically used for rendering additional objects on an entity, such as armor, an elytra or {@link class_978 Deadmau5's ears}.
 * This callback lets developers add additional feature renderers for use in entity rendering.
 * Listeners should filter out the specific entity renderer they want to hook into, usually through {@code instanceof} checks or filtering by entity type.
 * Once listeners find a suitable entity renderer, they should register their feature renderer via the registration helper.
 *
 * <p>For example, to register a feature renderer for a player model, the example below may be used:
 * <blockquote><pre>
 * LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper) -> {
 * 	if (entityRenderer instanceof AvatarRenderer&lt;?&gt; avatarEntityRenderer) {
 * 		registrationHelper.register(new MyFeatureRenderer(avatarEntityRenderer, context.getModelSet()));
 * 	}
 * });
 * </pre></blockquote>
 */
@FunctionalInterface
public interface LivingEntityFeatureRendererRegistrationCallback {
	Event<LivingEntityFeatureRendererRegistrationCallback> EVENT = EventFactory.createArrayBacked(LivingEntityFeatureRendererRegistrationCallback.class, callbacks -> (entityType, entityRenderer, registrationHelper, context) -> {
		for (LivingEntityFeatureRendererRegistrationCallback callback : callbacks) {
			callback.registerRenderers(entityType, entityRenderer, registrationHelper, context);
		}
	});

	/**
	 * Called when feature renderers may be registered.
	 *
	 * @param entityType     the entity type of the renderer
	 * @param entityRenderer the entity renderer
	 */
	void registerRenderers(class_1299<? extends class_1309> entityType, class_922<?, ?, ?> entityRenderer, RegistrationHelper registrationHelper, class_5617.class_5618 context);

	/**
	 * A delegate object used to help register feature renderers for an entity renderer.
	 *
	 * <p>This is not meant for implementation by users of the API.
	 */
	@ApiStatus.NonExtendable
	interface RegistrationHelper {
		/**
		 * Adds a feature renderer to the entity renderer.
		 *
		 * @param featureRenderer the feature renderer
		 * @param <T> the type of entity
		 */
		<T extends class_10017> void register(class_3887<T, ? extends class_583<T>> featureRenderer);
	}
}
