package com.visualproject.client.mixin;

import com.visualproject.client.notifications.CombatSoundController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerAttackSoundMixin {
	@Inject(method = "playServerSideSound(Lnet/minecraft/sounds/SoundEvent;)V", at = @At("HEAD"), cancellable = true)
	private void visualclient$replaceOwnAttackSound(SoundEvent soundEvent, CallbackInfo ci) {
		if (!(((Object) this) instanceof LocalPlayer)) {
			return;
		}

		if (CombatSoundController.replaceLocalAttackSound(soundEvent)) {
			ci.cancel();
		}
	}
}
