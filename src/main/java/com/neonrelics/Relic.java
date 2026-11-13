package com.neonrelics;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;

public enum Relic {
	INFERNO("inferno", "Inferno", 0xFF8A1E, ChatFormatting.GOLD, "Infernal Slash", Tuning.INFERNO_COOLDOWN, Tuning.INFERNO_MASTERY_KILLS,
			List.of(
					"Passive: critical hits set the target ablaze (no stacking).",
					"Ability: a 5-block-wide flaming slash - 3 true damage + burn.",
					"Mastery: kill 3 players with the slash. True damage becomes 4,",
					"burn doubles and Fire Resistance no longer protects them.")),
	PROSPERITY("prosperity", "Prosperity", 0xFFD43B, ChatFormatting.YELLOW, null, 0, 0,
			List.of(
					"Passive: villager trades cost half and always pay out double.",
					"Passive: mobs you kill drop 2x loot (10% chance of 5x) -",
					"evokers drop 2+ totems. Winning a raid has a 10% chance",
					"to grant Hero of the Village V.",
					"No ability, no mastery - pure progression.")),
	WEATHER("weather", "Weather", 0x38C9FF, ChatFormatting.AQUA, "Stormcall", Tuning.WEATHER_COOLDOWN, Tuning.WEATHER_MASTERY_STORMS,
			List.of(
					"Passive: in rain tridents deal +20% and act as Sharpness III.",
					"In thunderstorms: +35%, Sharpness V and Channeling on every hit.",
					"Ability: set the weather to clear, rain or thunder (30 min cooldown).",
					"Mastery: summon 3 storms and kill a player in each (cooldown 20 min).")),
	BEAST("beast", "Beast", 0xFF3030, ChatFormatting.RED, "Savage Bite", Tuning.BEAST_COOLDOWN, 0,
			List.of(
					"Passive: after a spear hit your next hit is a Claw (3.5 true damage)",
					"and your next spear lunge either leaps straight at the enemy you're",
					"looking at, or dashes you 6 blocks forward.",
					"Ability: a bite that stuns and deals 6 true damage (3 min cooldown).")),
	CYBER("cyber", "Cyber", 0x3CF6FF, ChatFormatting.DARK_AQUA, "Implant", Tuning.BOOT_COOLDOWN, 0,
			List.of(
					"Passive: hit someone 3 times in a row -> they get Slowness III.",
					"Get hit 3 times in a row -> attackers get Slowness V + Mining",
					"Fatigue V and you get Speed III (1 min cooldown).",
					"Your AI, NOVA, sells you a Boot Accelerator or a Chrome Body.")),
	BLOODLUSTED("bloodlusted", "Bloodlusted", 0xE0102A, ChatFormatting.DARK_RED, "Blood Frenzy", Tuning.BLOOD_COOLDOWN, 0,
			List.of(
					"Passive: every kill grants Strength IV for 30 seconds.",
					"Passive: swords deal 1.5x damage.",
					"Ability: Strength IV + Speed III for 1 minute (3 min cooldown).")),
	HASTE("haste", "Haste", 0xB44CFF, ChatFormatting.LIGHT_PURPLE, "Overclock", Tuning.HASTE_COOLDOWN, 0,
			List.of(
					"Passive: permanent Speed II and Haste III.",
					"Ability: attack twice as fast, 1.25x damage and Speed III",
					"for 20 seconds (3 min cooldown)."));

	public final String id;
	public final String displayName;
	public final int color;
	public final ChatFormatting format;
	@Nullable
	public final String abilityName;
	public final int cooldown;
	public final int masteryGoal;
	public final List<String> description;

	Relic(String id, String displayName, int color, ChatFormatting format, @Nullable String abilityName, int cooldown, int masteryGoal, List<String> description) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.format = format;
		this.abilityName = abilityName;
		this.cooldown = cooldown;
		this.masteryGoal = masteryGoal;
		this.description = description;
	}

	public boolean hasAbility() {
		return this.abilityName != null;
	}

	@Nullable
	public static Relic byId(String id) {
		for (Relic relic : values()) {
			if (relic.id.equals(id)) {
				return relic;
			}
		}

		return null;
	}
}
