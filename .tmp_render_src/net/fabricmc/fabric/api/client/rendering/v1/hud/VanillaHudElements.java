/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.class_2960;

/**
 * A hud element that has an identifier attached for use in {@link HudElementRegistry}.
 *
 * <p>The identifiers in this interface are the vanilla hud layers in the order they are drawn in.
 * The first element is drawn first, which means it is at the bottom.
 * All vanilla layers except {@link #SLEEP} are in sub drawers and have a render condition attached ({@link net.minecraft.class_315#field_1842}).
 * Operations relative to any element will generally inherit that element's render condition.
 * There is currently no mechanism to change the render condition of an element.
 *
 * <p>For common use cases and more details on how this API deals with render condition, see {@link HudElementRegistry}.
 */
public final class VanillaHudElements {
	/**
	 * The identifier for the vanilla miscellaneous overlays (such as vignette, spyglass, and powder snow) element.
	 */
	public static final class_2960 MISC_OVERLAYS = class_2960.method_60656("misc_overlays");
	/**
	 * The identifier for the vanilla crosshair element.
	 */
	public static final class_2960 CROSSHAIR = class_2960.method_60656("crosshair");
	/**
	 * The identifier for the vanilla spectator menu.
	 */
	public static final class_2960 SPECTATOR_MENU = class_2960.method_60656("spectator_menu");
	/**
	 * The identifier for the vanilla hotbar.
	 */
	public static final class_2960 HOTBAR = class_2960.method_60656("hotbar");
	/**
	 * The identifier for the player armor level bar.
	 */
	public static final class_2960 ARMOR_BAR = class_2960.method_60656("armor_bar");
	/**
	 * The identifier for the player health bar.
	 */
	public static final class_2960 HEALTH_BAR = class_2960.method_60656("health_bar");
	/**
	 * The identifier for the player hunger level bar.
	 */
	public static final class_2960 FOOD_BAR = class_2960.method_60656("food_bar");
	/**
	 * The identifier for the player air level bar.
	 */
	public static final class_2960 AIR_BAR = class_2960.method_60656("air_bar");
	/**
	 * The identifier for the vanilla mount health.
	 */
	public static final class_2960 MOUNT_HEALTH = class_2960.method_60656("mount_health");
	/**
	 * The identifier for the info bar, either empty, experience bar, locator, or jump bar.
	 */
	public static final class_2960 INFO_BAR = class_2960.method_60656("info_bar");
	/**
	 * The identifier for experience level tooltip.
	 */
	public static final class_2960 EXPERIENCE_LEVEL = class_2960.method_60656("experience_level");
	/**
	 * The identifier for held item tooltip.
	 */
	public static final class_2960 HELD_ITEM_TOOLTIP = class_2960.method_60656("held_item_tooltip");
	/**
	 * The identifier for the vanilla spectator tooltip.
	 */
	public static final class_2960 SPECTATOR_TOOLTIP = class_2960.method_60656("spectator_tooltip");
	/**
	 * The identifier for the vanilla status effects element.
	 */
	public static final class_2960 STATUS_EFFECTS = class_2960.method_60656("status_effects");
	/**
	 * The identifier for the vanilla boss bar element.
	 */
	public static final class_2960 BOSS_BAR = class_2960.method_60656("boss_bar");
	/**
	 * The identifier for the vanilla sleep overlay element.
	 */
	public static final class_2960 SLEEP = class_2960.method_60656("sleep");
	/**
	 * The identifier for the vanilla demo timer element.
	 */
	public static final class_2960 DEMO_TIMER = class_2960.method_60656("demo_timer");
	/**
	 * The identifier for the vanilla scoreboard element.
	 */
	public static final class_2960 SCOREBOARD = class_2960.method_60656("scoreboard");
	/**
	 * The identifier for the vanilla overlay message element.
	 */
	public static final class_2960 OVERLAY_MESSAGE = class_2960.method_60656("overlay_message");
	/**
	 * The identifier for the vanilla title and subtitle element.
	 *
	 * <p>Note that this is not the sound subtitles.
	 */
	public static final class_2960 TITLE_AND_SUBTITLE = class_2960.method_60656("title_and_subtitle");
	/**
	 * The identifier for the vanilla chat element.
	 */
	public static final class_2960 CHAT = class_2960.method_60656("chat");
	/**
	 * The identifier for the vanilla player list element.
	 */
	public static final class_2960 PLAYER_LIST = class_2960.method_60656("player_list");
	/**
	 * The identifier for the vanilla sound subtitles element.
	 */
	public static final class_2960 SUBTITLES = class_2960.method_60656("subtitles");

	private VanillaHudElements() {
	}
}
