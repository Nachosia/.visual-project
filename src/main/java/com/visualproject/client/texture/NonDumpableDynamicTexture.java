package com.visualproject.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

public class NonDumpableDynamicTexture extends DynamicTexture {
	public NonDumpableDynamicTexture(Supplier<String> nameSupplier, NativeImage pixels) {
		super(nameSupplier, pixels);
	}

	@Override
	public void dumpContents(Identifier identifier, Path path) throws IOException {
		// Vanilla debug texture export can crash on mod-owned dynamic textures.
	}
}
