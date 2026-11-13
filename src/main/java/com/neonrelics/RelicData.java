package com.neonrelics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

/**
 * Per-player relic data. Saved with the player and kept through death.
 * All times are absolute overworld game times (see {@link NeonRelics#now}).
 */
public final class RelicData {
	public static final String IMPLANT_BOOT = "boot_accelerator";
	public static final String IMPLANT_CHROME = "chrome_body";

	public static final Codec<RelicData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("relic", "").forGetter(d -> d.relic),
			Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(d -> d.cooldownUntil),
			Codec.BOOL.optionalFieldOf("mastered", false).forGetter(d -> d.mastered),
			Codec.INT.optionalFieldOf("mastery", 0).forGetter(d -> d.mastery),
			Codec.STRING.optionalFieldOf("implant", "").forGetter(d -> d.implant),
			Codec.FLOAT.optionalFieldOf("strain", 0.0F).forGetter(d -> d.strain),
			Codec.LONG.optionalFieldOf("maniac_until", 0L).forGetter(d -> d.maniacUntil),
			Codec.LONG.optionalFieldOf("active_until", 0L).forGetter(d -> d.activeUntil),
			Codec.BOOL.optionalFieldOf("storm_active", false).forGetter(d -> d.stormActive),
			Codec.BOOL.optionalFieldOf("storm_kill", false).forGetter(d -> d.stormKill),
			Codec.LONG.optionalFieldOf("passive_until", 0L).forGetter(d -> d.passiveUntil)
	).apply(instance, RelicData::new));

	/** Relic id, or "" if the player has not been given one yet. */
	public String relic;
	public long cooldownUntil;
	public boolean mastered;
	public int mastery;
	/** Cyber only: "", {@link #IMPLANT_BOOT} or {@link #IMPLANT_CHROME}. */
	public String implant;
	/** Cyber Boot Accelerator neural load, 0-100. */
	public float strain;
	/** When Cyber Maniac kills the player, 0 = not a maniac. */
	public long maniacUntil;
	/** End of a timed ability (Haste Overclock, Boot Accelerator overdrive). */
	public long activeUntil;
	/** Weather mastery: a storm this player summoned is running. */
	public boolean stormActive;
	/** Weather mastery: a player was killed during the current storm. */
	public boolean stormKill;
	/** Cyber passive cooldown. */
	public long passiveUntil;

	public RelicData() {
		this("", 0L, false, 0, "", 0.0F, 0L, 0L, false, false, 0L);
	}

	public RelicData(String relic, long cooldownUntil, boolean mastered, int mastery, String implant, float strain,
			long maniacUntil, long activeUntil, boolean stormActive, boolean stormKill, long passiveUntil) {
		this.relic = relic;
		this.cooldownUntil = cooldownUntil;
		this.mastered = mastered;
		this.mastery = mastery;
		this.implant = implant;
		this.strain = strain;
		this.maniacUntil = maniacUntil;
		this.activeUntil = activeUntil;
		this.stormActive = stormActive;
		this.stormKill = stormKill;
		this.passiveUntil = passiveUntil;
	}

	@Nullable
	public Relic relic() {
		return Relic.byId(this.relic);
	}

	public boolean is(Relic relic) {
		return relic.id.equals(this.relic);
	}

	public boolean hasBoot() {
		return this.is(Relic.CYBER) && IMPLANT_BOOT.equals(this.implant);
	}

	public boolean hasChrome() {
		return this.is(Relic.CYBER) && IMPLANT_CHROME.equals(this.implant);
	}

	/** Wipe everything and hand out a (possibly different) relic. */
	public void reset(String newRelic) {
		this.relic = newRelic;
		this.cooldownUntil = 0L;
		this.mastered = false;
		this.mastery = 0;
		this.implant = "";
		this.strain = 0.0F;
		this.maniacUntil = 0L;
		this.activeUntil = 0L;
		this.stormActive = false;
		this.stormKill = false;
		this.passiveUntil = 0L;
	}
}
