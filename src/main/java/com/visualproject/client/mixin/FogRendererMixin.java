package com.visualproject.client.mixin;

import com.visualproject.client.visuals.world.WorldCustomizerModule;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private void visualclient$applyWorldCustomizerFogColor(
        Camera camera,
        float partialTick,
        ClientLevel level,
        int skyDarken,
        float darkness,
        CallbackInfoReturnable<Vector4f> cir
    ) {
        if (!WorldCustomizerModule.isFogColorOverrideActive(level)) {
            return;
        }

        int color = WorldCustomizerModule.fogColor();
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        float alpha = cir.getReturnValue().w;
        cir.setReturnValue(new Vector4f(red, green, blue, alpha));
    }
}
