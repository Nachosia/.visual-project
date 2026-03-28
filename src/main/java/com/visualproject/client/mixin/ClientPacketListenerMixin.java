package com.visualproject.client.mixin;

import com.visualproject.client.hud.shared.HudRuntimeStats;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void visualclient$trackServerTps(ClientboundSetTimePacket packet, CallbackInfo ci) {
        HudRuntimeStats.onTimeUpdatePacketReceived();
    }
}