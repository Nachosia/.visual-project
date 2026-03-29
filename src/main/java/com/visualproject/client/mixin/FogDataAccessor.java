package com.visualproject.client.mixin;

import net.minecraft.client.renderer.fog.FogData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FogData.class)
public interface FogDataAccessor {
    @Accessor("environmentalStart")
    void setEnvironmentalStart(float value);

    @Accessor("renderDistanceStart")
    void setRenderDistanceStart(float value);

    @Accessor("environmentalEnd")
    void setEnvironmentalEnd(float value);

    @Accessor("renderDistanceEnd")
    void setRenderDistanceEnd(float value);

    @Accessor("skyEnd")
    void setSkyEnd(float value);

    @Accessor("cloudEnd")
    void setCloudEnd(float value);
}
