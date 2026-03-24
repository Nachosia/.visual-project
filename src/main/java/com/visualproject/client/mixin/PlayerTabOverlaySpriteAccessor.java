package com.visualproject.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlaySpriteAccessor {
	@Accessor("HEART_CONTAINER_SPRITE")
	static Identifier getHeartContainerSprite() {
		throw new AssertionError();
	}

	@Accessor("HEART_FULL_SPRITE")
	static Identifier getHeartFullSprite() {
		throw new AssertionError();
	}

	@Accessor("HEART_HALF_SPRITE")
	static Identifier getHeartHalfSprite() {
		throw new AssertionError();
	}
}
