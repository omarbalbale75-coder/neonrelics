package com.neonrelics;

/**
 * Every number in the mod lives here so it is easy to rebalance.
 * Times are in game ticks (20 ticks = 1 second).
 */
public final class Tuning {
	private Tuning() { }

	public static final int SECOND = 20;
	public static final int MINUTE = 60 * SECOND;

	// ---------- Inferno ----------
	/** Burn from a critical hit. "2 ticks" of burn = 2 fire damage ticks = 2 seconds. Doubled when mastered. */
	public static final int INFERNO_CRIT_BURN = 2 * SECOND;
	/** Burn from the slash ability (4 fire damage ticks). Doubled when mastered. */
	public static final int INFERNO_SLASH_BURN = 4 * SECOND;
	public static final float INFERNO_SLASH_TRUE_DAMAGE = 3.0F;
	public static final float INFERNO_SLASH_TRUE_DAMAGE_MASTERED = 4.0F;
	public static final double INFERNO_SLASH_WIDTH = 5.0;   // blocks wide
	public static final double INFERNO_SLASH_REACH = 4.5;   // blocks forward
	public static final int INFERNO_COOLDOWN = 40 * SECOND;
	public static final int INFERNO_MASTERY_KILLS = 3;
	/** A player who dies this soon after being slashed counts as a slash kill. */
	public static final int INFERNO_KILL_WINDOW = 10 * SECOND;

	// ---------- Prosperity ----------
	public static final double PROSPERITY_DOUBLE_TRADE_CHANCE = 0.25;
	public static final int PROSPERITY_EXTRA_TOTEMS = 1; // vanilla drops 1, so evokers drop 2 total
	public static final double PROSPERITY_HERO_V_CHANCE = 0.10;

	// ---------- Weather ----------
	public static final float WEATHER_RAIN_MULTIPLIER = 1.20F;
	public static final float WEATHER_STORM_MULTIPLIER = 1.35F;
	/** Sharpness III = +2.0 damage, Sharpness V = +3.0 damage (vanilla formula 0.5 * level + 0.5). */
	public static final float WEATHER_RAIN_SHARPNESS_BONUS = 2.0F;
	public static final float WEATHER_STORM_SHARPNESS_BONUS = 3.0F;
	public static final int WEATHER_COOLDOWN = 30 * MINUTE;
	public static final int WEATHER_COOLDOWN_MASTERED = 20 * MINUTE;
	public static final int WEATHER_DURATION = 5 * MINUTE;
	public static final int WEATHER_MASTERY_STORMS = 3;
	public static final int WEATHER_CHANNELING_COOLDOWN = SECOND;

	// ---------- Beast ----------
	public static final float BEAST_CLAW_TRUE_DAMAGE = 3.5F;
	public static final float BEAST_BITE_TRUE_DAMAGE = 6.0F;
	public static final int BEAST_BITE_STUN = 2 * SECOND;
	public static final double BEAST_BITE_REACH = 4.5;
	public static final int BEAST_COOLDOWN = 3 * MINUTE;
	public static final double BEAST_LUNGE_TARGET_RANGE = 24.0;
	public static final double BEAST_LUNGE_DASH = 6.0;

	// ---------- Cyber ----------
	public static final int CYBER_STREAK_HITS = 3;
	/** Max gap between hits for them to count as consecutive. */
	public static final int CYBER_STREAK_WINDOW = 3 * SECOND;
	public static final int CYBER_PASSIVE_COOLDOWN = MINUTE;
	public static final int CYBER_IMPLANT_COST_DIAMONDS = 10;
	// Boot Accelerator
	public static final int BOOT_COOLDOWN = 20 * SECOND;
	public static final int BOOT_DURATION = 8 * SECOND;
	public static final double BOOT_RADIUS = 20.0;
	public static final float BOOT_STRAIN_PER_USE = 30.0F;
	/** Strain drops by 1% every this many ticks while not overdriving. */
	public static final int BOOT_STRAIN_DECAY_INTERVAL = 2 * SECOND;
	public static final int BOOT_MANIAC_DURATION = 3 * MINUTE;
	// Chrome Body
	public static final int CHROME_TIMER = 60 * MINUTE;

	// ---------- Bloodlusted ----------
	public static final int BLOOD_KILL_STRENGTH = 30 * SECOND;
	public static final int BLOOD_ABILITY_DURATION = MINUTE;
	public static final float BLOOD_SWORD_MULTIPLIER = 1.5F;
	public static final int BLOOD_COOLDOWN = 3 * MINUTE;

	// ---------- Haste ----------
	public static final int HASTE_ABILITY_DURATION = 20 * SECOND;
	public static final float HASTE_DAMAGE_MULTIPLIER = 1.25F;
	public static final int HASTE_COOLDOWN = 3 * MINUTE;
}
