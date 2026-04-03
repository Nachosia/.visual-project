package com.visualproject.client.mixin;

import com.visualproject.client.render.shadertoy.ShadertoyProgramRegistry;
import com.visualproject.client.render.sdf.SdfShaderRegistry;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "preloadUiShader", at = @At("TAIL"))
	private void visualclient$onPreloadUiShader(ResourceProvider resourceProvider, CallbackInfo ci) {
		SdfShaderRegistry.INSTANCE.loadCustomShaders(resourceProvider);
		ShadertoyProgramRegistry.INSTANCE.loadCustomShaders(resourceProvider);
	}
}
