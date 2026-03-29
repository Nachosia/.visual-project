package com.visualproject.client.mixin;

import com.visualproject.client.visuals.world.WorldCustomizerModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void visualclient$applyWorldCustomizerFog(
        FogData fogData,
        Camera camera,
        ClientLevel level,
        float darkness,
        DeltaTracker deltaTracker,
        CallbackInfo ci
    ) {
        if (!WorldCustomizerModule.isFogDistanceOverrideActive(level)) {
            return;
        }

        int renderDistanceChunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        float start = WorldCustomizerModule.fogStartDistanceBlocks(renderDistanceChunks);
        float end = WorldCustomizerModule.fogEndDistanceBlocks(renderDistanceChunks);

        FogDataAccessor accessor = (FogDataAccessor) fogData;
        accessor.setEnvironmentalStart(start);
        accessor.setRenderDistanceStart(start);
        accessor.setEnvironmentalEnd(end);
        accessor.setRenderDistanceEnd(end);
        accessor.setSkyEnd(end);
        accessor.setCloudEnd(end);
    }
}
