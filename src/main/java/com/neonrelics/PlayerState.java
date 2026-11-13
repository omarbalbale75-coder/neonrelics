package com.neonrelics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;

/**
 * Short-lived combat state that does not need to survive a restart.
 */
public final class PlayerState {
	private static final Map<UUID, PlayerState> STATES = new HashMap<>();

	public static PlayerState of(UUID uuid) {
		return STATES.computeIfAbsent(uuid, u -> new PlayerState());
	}

	public static void remove(UUID uuid) {
		STATES.remove(uuid);
	}

	// Inferno: a critical hit predicted at the start of an attack
	public int critTargetId = -1;
	public long critTick = -1L;

	// Cyber: consecutive hits dealt
	public int hitStreakTarget = -1;
	public int hitStreak;
	public long lastHitTick = -1000L;
	// Cyber: consecutive hits taken
	public int takenStreak;
	public long lastTakenTick = -1000L;
	public final Set<UUID> takenFrom = new HashSet<>();
	// Cyber: NOVA warnings already given this cycle
	public int strainWarning;
	public long lastTimerWarning = -1L;
	public final Set<UUID> slowedProjectiles = new HashSet<>();

	// Beast
	public boolean clawReady;
	public boolean teleportReady;
	public long primedTick = -1L;
	public int axeHits;
	public long lastAxeHit = -1000L;

	// Chrome Body shotgun
	public long shotgunReadyAt;
	// Chrome Body ground slam: highest point since leaving the ground
	public double airPeakY = Double.NaN;
	public boolean slamInvalid;

	// Prosperity
	public boolean heroSeen;
	public final List<ItemStack> pendingRewards = new ArrayList<>();

	// Weather
	public long lastChannelTick = -1000L;

	public void clearCombat() {
		this.critTargetId = -1;
		this.hitStreak = 0;
		this.hitStreakTarget = -1;
		this.takenStreak = 0;
		this.takenFrom.clear();
		this.clawReady = false;
		this.teleportReady = false;
		this.axeHits = 0;
		this.slowedProjectiles.clear();
		this.strainWarning = 0;
	}
}
