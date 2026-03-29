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

import com.mojang.datafixers.util.Pair;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.impl.client.rendering.ArmorRendererRegistryImpl;
import net.minecraft.class_10034;
import net.minecraft.class_1058;
import net.minecraft.class_11659;
import net.minecraft.class_11785;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_1921;
import net.minecraft.class_1935;
import net.minecraft.class_3879;
import net.minecraft.class_3887;
import net.minecraft.class_4587;
import net.minecraft.class_5617;
import net.minecraft.class_572;

/**
 * Armor renderers render worn armor items with custom code.
 * They may be used to render armor with special models or effects.
 *
 * <p>The renderers are registered with {@link net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer#register(Factory, class_1935...)}
 * or {@link net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer#register(ArmorRenderer, class_1935...)}.
 */
@FunctionalInterface
public interface ArmorRenderer {
	/**
	 * Registers the armor renderer for the specified items.
	 * @param factory   the renderer factory
	 * @param items     the items
	 * @throws IllegalArgumentException if an item already has a registered armor renderer
	 * @throws NullPointerException if either an item or the factory is null
	 */
	static void register(ArmorRenderer.Factory factory, class_1935... items) {
		ArmorRendererRegistryImpl.register(factory, items);
	}

	/**
	 * Registers the armor renderer for the specified items.
	 * @param renderer  the renderer
	 * @param items     the items
	 * @throws IllegalArgumentException if an item already has a registered armor renderer
	 * @throws NullPointerException if either an item or the renderer is null
	 */
	static void register(ArmorRenderer renderer, class_1935... items) {
		ArmorRendererRegistryImpl.register(renderer, items);
	}

	/**
	 * Helper method for rendering a {@link TransformCopyingModel}, which will copy transforms from a source model to
	 * a delegate model when it is rendered.
	 * @param sourceModel           the model whose transforms will be copied
	 * @param sourceModelState      the model state of the source model
	 * @param delegateModel         the model that will be rendered with transforms copied from the source model
	 * @param delegateModelState    the model state of the delegate model
	 * @param setDelegateAngles     {@code true} if the {@link class_3879#method_2819(Object)} method should be called for the
	 *                                             delegate model after it is called for the source model
	 * @param queue                 the {@link class_11785}
	 * @param matrices              the matrix stack
	 * @param renderLayer           the render layer
	 * @param light                 packed lightmap coordinates
	 * @param overlay               packed overlay texture coordinates
	 * @param tintedColor           the color to tint the model with
	 * @param sprite                the sprite to render the model with, or {@code null} to use the render layer instead
	 * @param outlineColor          the outline color of the model
	 * @param crumblingOverlay      the crumbling overlay, or {@code null} for no crumbling overlay
	 * @param <S>                   state type of the source model
	 * @param <D>                   state type of the delegate model
	 */
	static <S, D> void submitTransformCopyingModel(class_3879<? super S> sourceModel, S sourceModelState, class_3879<? super D> delegateModel, D delegateModelState, boolean setDelegateAngles, class_11785 queue, class_4587 matrices, class_1921 renderLayer, int light, int overlay, int tintedColor, @Nullable class_1058 sprite, int outlineColor, class_11683.@Nullable class_11792 crumblingOverlay) {
		queue.method_73490(TransformCopyingModel.create(sourceModel, delegateModel, setDelegateAngles), Pair.of(sourceModelState, delegateModelState), matrices, renderLayer, light, overlay, tintedColor, sprite, outlineColor, crumblingOverlay);
	}

	/**
	 * Helper method for rendering a {@link TransformCopyingModel}, which will copy transforms from its source model to
	 * its delegate model when it is rendered.
	 * @param sourceModel           the model whose transforms will be copied
	 * @param sourceModelState      the model state of the source model
	 * @param delegateModel         the model that will be rendered with transforms copied from the source model
	 * @param delegateModelState    the model state of the delegate model
	 * @param setDelegateAngles     {@code true} if the {@link class_3879#method_2819(Object)} method should be called for the
	 *                                             delegate model after it is called for the source model
	 * @param queue                 the {@link class_11785}
	 * @param matrices              the matrix stack
	 * @param renderLayer           the render layer
	 * @param light                 packed lightmap coordinates
	 * @param overlay               packed overlay texture coordinates
	 * @param outlineColor          the outline color of the model
	 * @param crumblingOverlay      the crumbling overlay, or {@code null} for no crumbling overlay
	 * @param <S>                   state type of the source model
	 * @param <D>                   state type of the delegate model
	 */
	static <S, D> void submitTransformCopyingModel(class_3879<? super S> sourceModel, S sourceModelState, class_3879<? super D> delegateModel, D delegateModelState, boolean setDelegateAngles, class_11785 queue, class_4587 matrices, class_1921 renderLayer, int light, int overlay, int outlineColor, class_11683.@Nullable class_11792 crumblingOverlay) {
		queue.method_73489(TransformCopyingModel.create(sourceModel, delegateModel, setDelegateAngles), Pair.of(sourceModelState, delegateModelState), matrices, renderLayer, light, overlay, outlineColor, crumblingOverlay);
	}

	/**
	 * Renders an armor part.
	 *
	 * @param matrices                  the matrix stack
	 * @param orderedRenderCommandQueue the {@link class_11659} instance
	 * @param stack                     the item stack of the armor item
	 * @param bipedEntityRenderState    the render state of the entity
	 * @param slot                      the equipment slot in which the armor stack is worn
	 * @param light                     packed lightmap coordinates
	 * @param contextModel              the model provided by {@link class_3887#method_17165()}
	 */
	void render(class_4587 matrices, class_11659 orderedRenderCommandQueue, class_1799 stack, class_10034 bipedEntityRenderState, class_1304 slot, int light, class_572<class_10034> contextModel);

	/**
	 * Checks whether an item stack equipped on the head should also be
	 * rendered as an item. By default, vanilla renders most items with their models (or special item renderers)
	 * around or on top of the entity's head, but this is often unwanted for custom equipment.
	 *
	 * <p>This method only applies to items registered with this renderer.
	 *
	 * <p>Note that the item will never be rendered by vanilla code if it has an armor model defined
	 * by the {@link net.minecraft.class_9334#field_54196 minecraft:equippable} component.
	 * This method cannot be used to overwrite that check to re-enable also rendering the item model.
	 * See {@link net.minecraft.class_970#method_64081(class_1799, class_1304)}.
	 *
	 * @param entity the equipping entity
	 * @param stack  the item stack equipped on the head
	 * @return {@code true} if the head item should be rendered, {@code false} otherwise
	 */
	default boolean shouldRenderDefaultHeadItem(class_1309 entity, class_1799 stack) {
		return true;
	}

	/**
	 * A factory to create an {@link ArmorRenderer} instance.
	 */
	@FunctionalInterface
	interface Factory {
		ArmorRenderer createArmorRenderer(class_5617.class_5618 context);
	}
}
