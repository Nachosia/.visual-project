package com.visualproject.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.Supplier;

public class NonDumpableDynamicTexture extends DynamicTexture {
	public NonDumpableDynamicTexture(Supplier<String> nameSupplier, NativeImage pixels) {
		this(nameSupplier, pixels, true);
	}

	public NonDumpableDynamicTexture(Supplier<String> nameSupplier, NativeImage pixels, boolean smoothFiltering) {
		super(nameSupplier, pixels);
		applyFiltering(smoothFiltering);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void applyFiltering(boolean smoothFiltering) {
		try {
			Method booleanFilter = findMethod("setFilter", boolean.class, boolean.class);
			if (booleanFilter != null) {
				booleanFilter.invoke(this, smoothFiltering, false);
				return;
			}

			Method blurMipmap = findMethod("setBlurMipmap", boolean.class, boolean.class);
			if (blurMipmap != null) {
				blurMipmap.invoke(this, smoothFiltering, false);
				return;
			}

			for (Method method : this.getClass().getMethods()) {
				if (!method.getName().equals("setFilter") || method.getParameterCount() != 2) continue;
				Class<?>[] parameterTypes = method.getParameterTypes();
				if (parameterTypes[1] != boolean.class && parameterTypes[1] != Boolean.class) continue;
				Class<?> first = parameterTypes[0];
				if (!first.isEnum()) continue;
				Object filterValue = null;
				for (Object constant : first.getEnumConstants()) {
					String name = ((Enum) constant).name();
					if (smoothFiltering && ("TRUE".equalsIgnoreCase(name) || "YES".equalsIgnoreCase(name))) {
						filterValue = constant;
						break;
					}
					if (!smoothFiltering && ("FALSE".equalsIgnoreCase(name) || "NO".equalsIgnoreCase(name))) {
						filterValue = constant;
						break;
					}
				}
				if (filterValue != null) {
					method.invoke(this, filterValue, false);
					return;
				}
			}
		} catch (Throwable ignored) {
			// Best-effort texture filtering only.
		}
	}

	private Method findMethod(String name, Class<?>... parameterTypes) {
		Class<?> type = this.getClass();
		while (type != null) {
			try {
				Method method = type.getDeclaredMethod(name, parameterTypes);
				method.setAccessible(true);
				return method;
			} catch (NoSuchMethodException ignored) {
				type = type.getSuperclass();
			}
		}
		return null;
	}

	@Override
	public void dumpContents(Identifier identifier, Path path) throws IOException {
		// Vanilla debug texture export can crash on mod-owned dynamic textures.
	}
}
