package com.visualproject.client.mixin;

import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExperienceBarRenderer.class)
public interface ExperienceBarSpriteAccessor {
	@Accessor("EXPERIENCE_BAR_BACKGROUND_SPRITE")
	static Identifier getExperienceBarBackgroundSprite() {
		throw new AssertionError();
	}

	@Accessor("EXPERIENCE_BAR_PROGRESS_SPRITE")
	static Identifier getExperienceBarProgressSprite() {
		throw new AssertionError();
	}
}
