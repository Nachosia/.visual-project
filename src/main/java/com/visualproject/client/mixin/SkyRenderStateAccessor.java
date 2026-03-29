package com.visualproject.client.mixin;

import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkyRenderState.class)
public interface SkyRenderStateAccessor {
    @Accessor("skyColor")
    void setSkyColor(int value);

    @Accessor("sunriseAndSunsetColor")
    void setSunriseAndSunsetColor(int value);
}
