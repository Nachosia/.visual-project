package com.visualproject.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$ClientLevelData")
public interface ClientLevelDataAccessor {
    @Accessor("dayTime")
    void setDayTime(long dayTime);
}
