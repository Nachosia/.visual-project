package com.visualproject.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiSpriteAccessor {
	@Accessor("ARMOR_EMPTY_SPRITE")
	static Identifier getArmorEmptySprite() {
		throw new AssertionError();
	}

	@Accessor("ARMOR_HALF_SPRITE")
	static Identifier getArmorHalfSprite() {
		throw new AssertionError();
	}

	@Accessor("ARMOR_FULL_SPRITE")
	static Identifier getArmorFullSprite() {
		throw new AssertionError();
	}

	@Accessor("FOOD_EMPTY_SPRITE")
	static Identifier getFoodEmptySprite() {
		throw new AssertionError();
	}

	@Accessor("FOOD_HALF_SPRITE")
	static Identifier getFoodHalfSprite() {
		throw new AssertionError();
	}

	@Accessor("FOOD_FULL_SPRITE")
	static Identifier getFoodFullSprite() {
		throw new AssertionError();
	}

	@Accessor("FOOD_EMPTY_HUNGER_SPRITE")
	static Identifier getFoodEmptyHungerSprite() {
		throw new AssertionError();
	}

	@Accessor("FOOD_HALF_HUNGER_SPRITE")
	static Identifier getFoodHalfHungerSprite() {
		throw new AssertionError();
	}

	@Accessor("FOOD_FULL_HUNGER_SPRITE")
	static Identifier getFoodFullHungerSprite() {
		throw new AssertionError();
	}
}
