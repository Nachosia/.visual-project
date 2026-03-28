package com.visualproject.client.texture;

import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TextureFiltering {
	private static final Set<String> smoothedTextures = ConcurrentHashMap.newKeySet();

	private TextureFiltering() {
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void ensureSmooth(Object textureManager, Identifier textureId) {
		if (textureManager == null || textureId == null) {
			return;
		}

		String key = textureId.toString();
		if (!smoothedTextures.add(key)) {
			return;
		}

		try {
			Object texture = resolveTexture(textureManager, textureId);
			if (texture == null) {
				smoothedTextures.remove(key);
				return;
			}

			Method booleanFilter = findMethod(texture.getClass(), "setFilter", boolean.class, boolean.class);
			if (booleanFilter != null) {
				booleanFilter.invoke(texture, true, false);
				return;
			}

			Method blurMipmap = findMethod(texture.getClass(), "setBlurMipmap", boolean.class, boolean.class);
			if (blurMipmap != null) {
				blurMipmap.invoke(texture, true, false);
				return;
			}

			for (Method method : texture.getClass().getMethods()) {
				if (!method.getName().equals("setFilter") || method.getParameterCount() != 2) continue;
				Class<?>[] parameterTypes = method.getParameterTypes();
				if (parameterTypes[1] != boolean.class && parameterTypes[1] != Boolean.class) continue;
				Class<?> first = parameterTypes[0];
				if (!first.isEnum()) continue;

				Object trueValue = null;
				for (Object constant : first.getEnumConstants()) {
					String name = ((Enum) constant).name();
					if ("TRUE".equalsIgnoreCase(name) || "YES".equalsIgnoreCase(name)) {
						trueValue = constant;
						break;
					}
				}

				if (trueValue != null) {
					method.invoke(texture, trueValue, false);
					return;
				}
			}
		} catch (Throwable ignored) {
			smoothedTextures.remove(key);
		}
	}

	private static Object resolveTexture(Object textureManager, Identifier textureId) throws ReflectiveOperationException {
		for (Method method : textureManager.getClass().getMethods()) {
			if (!method.getName().equals("getTexture") || method.getParameterCount() != 1) continue;
			Class<?> parameterType = method.getParameterTypes()[0];
			if (!parameterType.isAssignableFrom(textureId.getClass())) continue;
			return method.invoke(textureManager, textureId);
		}
		return null;
	}

	private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
		Class<?> current = type;
		while (current != null) {
			try {
				Method method = current.getDeclaredMethod(name, parameterTypes);
				method.setAccessible(true);
				return method;
			} catch (NoSuchMethodException ignored) {
				current = current.getSuperclass();
			}
		}
		return null;
	}
}
