package com.neonrelics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Damage modification, hit tracking, kills and burning.
 */
public final class RelicCombat {
	private RelicCombat() { }

	/** Mastered Inferno burn that ignores Fire Resistance: entity UUID -> burn. */
	private static final Map<UUID, Hellfire> HELLFIRE = new HashMap<>();
	/** Players hit by an Inferno slash: victim UUID -> who slashed them and when. */
	private static final Map<UUID, Slash> SLASHED = new HashMap<>();

	private record Hellfire(LivingEntity target, UUID attacker, long until) { }

	private record Slash(UUID attacker, long tick) { }

	// ------------------------------------------------------------------
	// Item helpers
	// ------------------------------------------------------------------

	private static String itemPath(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	public static boolean isSpear(ItemStack stack) {
		return !stack.isEmpty() && itemPath(stack).endsWith("_spear");
	}

	public static boolean isSword(ItemStack stack) {
		return !stack.isEmpty() && itemPath(stack).endsWith("_sword");
	}

	/** A melee hit: the player is both the attacker and the thing that touched the target. */
	@Nullable
	private static ServerPlayer meleeAttacker(DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
			return player;
		}

		return null;
	}

	private static boolean isTridentHit(DamageSource source, ServerPlayer player) {
		Entity direct = source.getDirectEntity();

		if (direct != null && direct.getType() == EntityTypes.TRIDENT) {
			return true;
		}

		return direct == player && player.getMainHandItem().is(Items.TRIDENT);
	}

	// ------------------------------------------------------------------
	// Damage multipliers (called from LivingEntityMixin at the start of hurtServer)
	// ------------------------------------------------------------------

	public static float modifyDamage(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
		if (amount <= 0.0F || ModDamage.isOurs(source) || !(source.getEntity() instanceof ServerPlayer player)) {
			return amount;
		}

		RelicData data = NeonRelics.data(player);
		Relic relic = data.relic();

		if (relic == null) {
			return amount;
		}

		long now = NeonRelics.now(level.getServer());
		boolean melee = source.getDirectEntity() == player;

		switch (relic) {
			case WEATHER -> {
				if (isTridentHit(source, player)) {
					Level weatherLevel = player.level();

					if (weatherLevel.isThundering()) {
						amount = (amount + Tuning.WEATHER_STORM_SHARPNESS_BONUS) * Tuning.WEATHER_STORM_MULTIPLIER;
					} else if (weatherLevel.isRaining()) {
						amount = (amount + Tuning.WEATHER_RAIN_SHARPNESS_BONUS) * Tuning.WEATHER_RAIN_MULTIPLIER;
					}
				}
			}
			case BLOODLUSTED -> {
				if (melee && isSword(player.getMainHandItem())) {
					amount *= Tuning.BLOOD_SWORD_MULTIPLIER;
				}
			}
			case HASTE -> {
				if (melee && now < data.activeUntil) {
					amount *= Tuning.HASTE_DAMAGE_MULTIPLIER;
				}
			}
			case CYBER -> {
				if (melee && data.hasChrome() && player.getMainHandItem().is(Items.MACE)) {
					amount *= 2.0F;
				}
			}
			default -> { }
		}

		return amount;
	}

	// ------------------------------------------------------------------
	// Events
	// ------------------------------------------------------------------

	/** Fires at the very start of a player's melee attack - used to predict critical hits. */
	public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && target instanceof LivingEntity
				&& NeonRelics.data(serverPlayer).is(Relic.INFERNO) && wouldCrit(serverPlayer)) {
			PlayerState state = PlayerState.of(serverPlayer.getUUID());
			state.critTargetId = target.getId();
			state.critTick = NeonRelics.now(serverPlayer.level().getServer());
		}

		return InteractionResult.PASS;
	}

	/** Same rules vanilla uses for a critical hit. */
	private static boolean wouldCrit(ServerPlayer player) {
		return player.getAttackStrengthScale(0.5F) > 0.9F
				&& player.fallDistance > 0.0
				&& !player.onGround()
				&& !player.onClimbable()
				&& !player.isInWater()
				&& !player.hasEffect(MobEffects.BLINDNESS)
				&& !player.isPassenger()
				&& !player.isSprinting();
	}

	/** Chrome Body never takes fall damage. */
	public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (victim instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && NeonRelics.data(player).hasChrome()) {
			return false;
		}

		return true;
	}

	public static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (ModDamage.isOurs(source) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}

		MinecraftServer server = level.getServer();
		long now = NeonRelics.now(server);

		// ---- The attacker's side ----
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && !blocked) {
			RelicData data = NeonRelics.data(attacker);
			PlayerState state = PlayerState.of(attacker.getUUID());
			Relic relic = data.relic();

			if (relic != null) {
				switch (relic) {
					case INFERNO -> {
						if (state.critTargetId == victim.getId() && state.critTick == now && source.getDirectEntity() == attacker) {
							state.critTargetId = -1;
							int burn = data.mastered ? Tuning.INFERNO_CRIT_BURN * 2 : Tuning.INFERNO_CRIT_BURN;
							burn(level, victim, attacker, burn, data.mastered);
							Fx.particles(level, ParticleTypes.FLAME, victim, 12, 0.3, 0.02);
						}
					}
					case BEAST -> onBeastHit(level, attacker, victim, source, state, now);
					case CYBER -> onCyberHit(level, attacker, data, state, victim, now);
					case WEATHER -> {
						if (level.isThundering() && isTridentHit(source, attacker) && now - state.lastChannelTick >= Tuning.WEATHER_CHANNELING_COOLDOWN) {
							state.lastChannelTick = now;
							channel(level, victim, attacker);
						}
					}
					default -> { }
				}
			}
		}

		// ---- The victim's side ----
		if (victim instanceof ServerPlayer hurtPlayer && source.getEntity() instanceof LivingEntity attacker && attacker != victim) {
			RelicData data = NeonRelics.data(hurtPlayer);

			if (data.is(Relic.CYBER)) {
				onCyberHurt(level, hurtPlayer, data, PlayerState.of(hurtPlayer.getUUID()), attacker, now);
			}
		}
	}

	private static void onBeastHit(ServerLevel level, ServerPlayer attacker, LivingEntity victim, DamageSource source, PlayerState state, long now) {
		if (source.getDirectEntity() != attacker) {
			return;
		}

		// The hit AFTER a spear hit becomes a claw strike.
		if (state.clawReady && state.primedTick < now) {
			state.clawReady = false;
			ModDamage.trueDamage(level, victim, attacker, Tuning.BEAST_CLAW_TRUE_DAMAGE);
			Fx.particles(level, ParticleTypes.SWEEP_ATTACK, victim, 3, 0.4, 0.0);
			Fx.particles(level, ParticleTypes.DAMAGE_INDICATOR, victim, 8, 0.3, 0.1);
			Fx.sound(level, victim, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
		}

		if (isSpear(attacker.getMainHandItem())) {
			state.clawReady = true;
			state.lungeReady = true;
			state.primedTick = now;
			Fx.actionBar(attacker, Component.literal("Claw primed - next spear lunge is empowered").withStyle(ChatFormatting.RED));
		}
	}

	private static void onCyberHit(ServerLevel level, ServerPlayer attacker, RelicData data, PlayerState state, LivingEntity victim, long now) {
		state.takenStreak = 0;
		state.takenFrom.clear();

		if (state.hitStreakTarget == victim.getId() && now - state.lastHitTick <= Tuning.CYBER_STREAK_WINDOW) {
			state.hitStreak++;
		} else {
			state.hitStreakTarget = victim.getId();
			state.hitStreak = 1;
		}

		state.lastHitTick = now;

		if (state.hitStreak >= Tuning.CYBER_STREAK_HITS && now >= data.passiveUntil) {
			state.hitStreak = 0;
			data.passiveUntil = now + Tuning.CYBER_PASSIVE_COOLDOWN;
			Fx.effect(victim, MobEffects.SLOWNESS, 3 * Tuning.SECOND, 2);
			Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, victim, 25, 0.4, 0.15);
			Fx.sound(level, victim, SoundEvents.BEACON_DEACTIVATE, 0.8F, 2.0F);
			Fx.actionBar(attacker, Component.literal("NOVA: Target servos jammed - Slowness III").withStyle(ChatFormatting.AQUA));
		}
	}

	private static void onCyberHurt(ServerLevel level, ServerPlayer player, RelicData data, PlayerState state, LivingEntity attacker, long now) {
		state.hitStreak = 0;

		if (now - state.lastTakenTick <= Tuning.CYBER_STREAK_WINDOW) {
			state.takenStreak++;
		} else {
			state.takenStreak = 1;
			state.takenFrom.clear();
		}

		state.lastTakenTick = now;
		state.takenFrom.add(attacker.getUUID());

		if (state.takenStreak >= Tuning.CYBER_STREAK_HITS && now >= data.passiveUntil) {
			state.takenStreak = 0;
			data.passiveUntil = now + Tuning.CYBER_PASSIVE_COOLDOWN;

			for (UUID uuid : state.takenFrom) {
				if (level.getEntity(uuid) instanceof LivingEntity enemy && enemy.isAlive() && enemy.distanceToSqr(player) < 32 * 32) {
					Fx.effect(enemy, MobEffects.SLOWNESS, 5 * Tuning.SECOND, 4);
					Fx.effect(enemy, MobEffects.MINING_FATIGUE, 5 * Tuning.SECOND, 4);
					Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, enemy, 30, 0.4, 0.2);
				}
			}

			state.takenFrom.clear();
			Fx.effect(player, MobEffects.SPEED, 5 * Tuning.SECOND, 2);
			Fx.sound(level, player, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.8F);
			Fx.actionBar(player, Component.literal("NOVA: Countermeasures deployed - get out of there!").withStyle(ChatFormatting.AQUA));
		}
	}

	/** Weather relic channeling: lightning on the target without frying the wielder. */
	private static void channel(ServerLevel level, LivingEntity victim, ServerPlayer attacker) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);

		if (bolt == null) {
			return;
		}

		bolt.setPos(victim.getX(), victim.getY(), victim.getZ());
		bolt.setVisualOnly(true);
		level.addFreshEntity(bolt);
		victim.thunderHit(level, bolt);
		Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, victim, 30, 0.5, 0.3);
	}

	/** A kill happened. */
	public static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return;
		}

		MinecraftServer server = level.getServer();
		long now = NeonRelics.now(server);
		ServerPlayer killer = null;

		if (source.getEntity() instanceof ServerPlayer p) {
			killer = p;
		} else if (victim.getKillCredit() instanceof ServerPlayer p) {
			killer = p;
		}

		Slash slash = SLASHED.remove(victim.getUUID());
		HELLFIRE.remove(victim.getUUID());

		if (killer == null || killer == victim) {
			return;
		}

		RelicData data = NeonRelics.data(killer);
		Relic relic = data.relic();

		if (relic == null) {
			return;
		}

		boolean playerKill = victim instanceof Player;
		boolean countsAsKill = playerKill || victim instanceof Mob;

		switch (relic) {
			case BLOODLUSTED -> {
				if (countsAsKill) {
					Fx.effect(killer, MobEffects.STRENGTH, Tuning.BLOOD_KILL_STRENGTH, 3);
					Fx.particles(level, Fx.dust(0xC00018, 1.4F), killer, 25, 0.4, 0.0);
					Fx.sound(level, killer, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.4F);
				}
			}
			case INFERNO -> {
				if (playerKill && !data.mastered && slash != null && slash.attacker().equals(killer.getUUID())
						&& now - slash.tick() <= Tuning.INFERNO_KILL_WINDOW) {
					addMastery(killer, data, Relic.INFERNO);
				}
			}
			case WEATHER -> {
				if (playerKill && !data.mastered && data.stormActive && !data.stormKill && server.overworld().isThundering()) {
					data.stormKill = true;
					addMastery(killer, data, Relic.WEATHER);
				}
			}
			case CYBER -> {
				boolean hostile = victim instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER;

				if (data.hasChrome() && (playerKill || hostile)) {
					data.maniacUntil = now + Tuning.CHROME_TIMER;
					PlayerState.of(killer.getUUID()).lastTimerWarning = -1L;
					Fx.actionBar(killer, Component.literal("NOVA: Kill confirmed. Chrome stabilized - 60:00").withStyle(ChatFormatting.AQUA));
				}
			}
			default -> { }
		}
	}

	private static void addMastery(ServerPlayer player, RelicData data, Relic relic) {
		data.mastery++;
		ServerLevel level = (ServerLevel) player.level();

		if (data.mastery >= relic.masteryGoal) {
			data.mastered = true;
			Fx.title(player, Component.literal("RELIC MASTERED").withStyle(relic.format, ChatFormatting.BOLD),
					Component.literal(relic.displayName + " has awakened").withStyle(ChatFormatting.GRAY));
			Fx.sound(level, player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
			Fx.particles(level, ParticleTypes.TOTEM_OF_UNDYING, player, 60, 0.6, 0.4);
			level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getName().getString() + " has mastered the ")
					.append(Component.literal(relic.displayName + " Relic").withStyle(relic.format, ChatFormatting.BOLD))
					.append(Component.literal("!")), false);
		} else {
			player.sendSystemMessage(Component.literal("Mastery progress: " + data.mastery + "/" + relic.masteryGoal).withStyle(relic.format));
			Fx.sound(level, player, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.5F);
		}
	}

	// ------------------------------------------------------------------
	// Prosperity: multiplied mob drops
	// ------------------------------------------------------------------

	/** Fabric loot event: runs whenever a loot table rolls. Only touches mobs killed by a Prosperity player. */
	public static void modifyDrops(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (drops.isEmpty() || !context.hasParameter(LootContextParams.THIS_ENTITY) || !context.hasParameter(LootContextParams.DAMAGE_SOURCE)) {
			return;
		}

		if (!(context.getOptional(LootContextParams.THIS_ENTITY) instanceof LivingEntity victim) || victim instanceof Player || !victim.isDeadOrDying()) {
			return;
		}

		DamageSource source = context.getOptional(LootContextParams.DAMAGE_SOURCE);
		ServerPlayer killer = null;

		if (source != null && source.getEntity() instanceof ServerPlayer p) {
			killer = p;
		} else if (victim.getKillCredit() instanceof ServerPlayer p) {
			killer = p;
		}

		if (killer == null || !NeonRelics.data(killer).is(Relic.PROSPERITY)) {
			return;
		}

		boolean jackpot = killer.getRandom().nextDouble() < Tuning.PROSPERITY_JACKPOT_CHANCE;
		int multiplier = jackpot ? Tuning.PROSPERITY_JACKPOT_MULTIPLIER : Tuning.PROSPERITY_DROP_MULTIPLIER;
		List<ItemStack> extra = new ArrayList<>();

		for (ItemStack stack : drops) {
			for (int i = 1; i < multiplier; i++) {
				extra.add(stack.copy());
			}
		}

		drops.addAll(extra);

		if (jackpot && victim.level() instanceof ServerLevel level) {
			Fx.particles(level, ParticleTypes.TOTEM_OF_UNDYING, victim, 40, 0.5, 0.4);
			Fx.sound(level, victim, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.6F);
			Fx.actionBar(killer, Component.literal("JACKPOT! x" + multiplier + " drops").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
	}

	// ------------------------------------------------------------------
	// Burning
	// ------------------------------------------------------------------

	/** Inferno burn: never stacks, it only refreshes up to the new length. */
	public static void burn(ServerLevel level, LivingEntity target, ServerPlayer attacker, int ticks, boolean hellfire) {
		if (target.getRemainingFireTicks() < ticks) {
			target.setRemainingFireTicks(ticks);
		}

		if (hellfire) {
			long until = NeonRelics.now(level.getServer()) + ticks;
			Hellfire existing = HELLFIRE.get(target.getUUID());

			if (existing == null || existing.until() < until) {
				HELLFIRE.put(target.getUUID(), new Hellfire(target, attacker.getUUID(), until));
			}
		}
	}

	public static void markSlashed(LivingEntity victim, ServerPlayer attacker, long now) {
		if (victim instanceof Player) {
			SLASHED.put(victim.getUUID(), new Slash(attacker.getUUID(), now));
		}
	}

	/** Runs every server tick. */
	public static void tick(MinecraftServer server, long now) {
		Iterator<Map.Entry<UUID, Hellfire>> it = HELLFIRE.entrySet().iterator();

		while (it.hasNext()) {
			Hellfire fire = it.next().getValue();
			LivingEntity target = fire.target();

			if (now >= fire.until() || target.isRemoved() || !target.isAlive()) {
				it.remove();
				continue;
			}

			// Vanilla fire already hurts targets without Fire Resistance - hellfire covers the rest.
			if (now % 20 == 0 && target.hasEffect(MobEffects.FIRE_RESISTANCE) && target.level() instanceof ServerLevel level) {
				Entity attacker = server.getPlayerList().getPlayer(fire.attacker());
				target.hurtServer(level, ModDamage.source(level, ModDamage.HELLFIRE, attacker), 1.0F);
				Fx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, target, 10, 0.3, 0.02);

				if (target.getRemainingFireTicks() < 20) {
					target.setRemainingFireTicks(20);
				}
			}
		}

		if (now % 200 == 0) {
			SLASHED.values().removeIf(slash -> now - slash.tick() > Tuning.INFERNO_KILL_WINDOW);
		}
	}
}
