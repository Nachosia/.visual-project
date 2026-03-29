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
import net.minecraft.class_3879;

/**
 * A model that copies transforms from a source model to a delegate model.
 *
 * <p>Useful in cases where an overlay model may not be a subclass of its base model.
 * @param <S> type of the source model state
 * @param <D> type of the delegate model state
 */
public final class TransformCopyingModel<S, D> extends class_3879<Pair<S, D>> {
	private final class_3879<? super S> source;
	private final class_3879<? super D> delegate;
	private final boolean setDelegateAngles;

	/**
	 * @param source the model whose transforms will be copied
	 * @param delegate the model that will be rendered with transforms copied from the source model
	 * @param setDelegateAngles {@code true} if the {@link class_3879#method_2819(Object)} method should be called for the
	 *                                         delegate model after it is called for the source model
	 */
	public static <S, D> TransformCopyingModel<S, D> create(class_3879<? super S> source, class_3879<? super D> delegate, boolean setDelegateAngles) {
		return new TransformCopyingModel<>(source, delegate, setDelegateAngles);
	}

	private TransformCopyingModel(class_3879<? super S> source, class_3879<? super D> delegate, boolean setDelegateAngles) {
		super(delegate.method_63512(), delegate::method_23500);
		this.source = source;
		this.delegate = delegate;
		this.setDelegateAngles = setDelegateAngles;
	}

	@Override
	public void setupAnim(Pair<S, D> state) {
		method_63514();
		source.method_2819(state.getFirst());
		delegate.copyTransforms(source);

		if (setDelegateAngles) {
			delegate.method_2819(state.getSecond());
		}
	}
}
