package com.neonrelics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.neonrelics.network.HudPayload;

public final class RelicEvents {
	private RelicEvents() { }

	// ------------------------------------------------------------------
	// Joining, respawning, leaving
	// ------------------------------------------------------------------

	public static void onJoin(ServerPlayer player) {
		RelicData data = NeonRelics.data(player);

		if (data.relic() == null) {
			Relic[] all = Relic.values();
			Relic relic = all[player.getRandom().nextInt(all.length)];
			give(player, relic, true);
		} else {
			reapplyPermanentEffects(player, data);
		}
	}

	/** Give a player a relic (wipes any previous relic progress). */
	public static void give(ServerPlayer player, Relic relic, boolean firstSpawn) {
		RelicData data = NeonRelics.data(player);
		clearRelicEffects(player);
		data.reset(relic.id);
		PlayerState.of(player.getUUID()).clearCombat();
		ServerLevel level = (ServerLevel) player.level();

		Fx.title(player, Component.literal(relic.displayName.toUpperCase()).withStyle(relic.format, ChatFormatting.BOLD),
				Component.literal(firstSpawn ? "A relic has chosen you" : "Your relic has changed").withStyle(ChatFormatting.GRAY));
		Fx.sound(level, player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
		Fx.particles(level, ParticleTypes.TOTEM_OF_UNDYING, player, 50, 0.6, 0.4);
		sendInfo(player, relic);
		reapplyPermanentEffects(player, data);

		if (relic == Relic.CYBER) {
			Nova.greet(player);
		}
	}

	public static void sendInfo(ServerPlayer player, Relic relic) {
		player.sendSystemMessage(Component.literal("=== " + relic.displayName + " Relic ===").withColor(relic.color).withStyle(ChatFormatting.BOLD));

		for (String line : relic.description) {
			player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}

		if (relic.hasAbility()) {
			player.sendSystemMessage(Component.literal("Press ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.keybind("key.neonrelics.ability").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" (or /relic ability) to use " + relic.abilityName + ".").withStyle(ChatFormatting.DARK_GRAY)));
		}
	}

	public static void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		RelicData data = NeonRelics.data(newPlayer);
		PlayerState.of(newPlayer.getUUID()).clearCombat();

		if (!alive) {
			data.activeUntil = 0L;

			if (data.hasChrome()) {
				// A fresh body: the clock restarts.
				data.maniacUntil = NeonRelics.now(newPlayer.level().getServer()) + Tuning.CHROME_TIMER;
			} else {
				data.maniacUntil = 0L;

				if (data.hasBoot()) {
					data.strain = 0.0F;
				}
			}
		}

		reapplyPermanentEffects(newPlayer, data);
	}

	public static void onLeave(ServerPlayer player) {
		PlayerState.remove(player.getUUID());
	}

	private static void reapplyPermanentEffects(ServerPlayer player, RelicData data) {
		if (data.is(Relic.HASTE)) {
			Fx.keepPermanent(player, MobEffects.SPEED, 1);
			Fx.keepPermanent(player, MobEffects.HASTE, 2);
		}

		if (data.hasChrome()) {
			Fx.keepPermanent(player, MobEffects.RESISTANCE, 1);
		}
	}

	public static void clearRelicEffects(ServerPlayer player) {
		Fx.removePermanent(player, MobEffects.SPEED);
		Fx.removePermanent(player, MobEffects.HASTE);
		Fx.removePermanent(player, MobEffects.RESISTANCE);
		player.removeEffect(RelicEffects.CYBER_MANIAC);
		player.removeEffect(RelicEffects.OVERCLOCK);
	}

	// ------------------------------------------------------------------
	// Ticking
	// ------------------------------------------------------------------

	public static void onServerTick(MinecraftServer server) {
		long now = NeonRelics.now(server);
		RelicCombat.tick(server, now);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(server, player, now);
		}
	}

	private static void tickPlayer(MinecraftServer server, ServerPlayer player, long now) {
		RelicData data = NeonRelics.data(player);
		Relic relic = data.relic();
		PlayerState state = PlayerState.of(player.getUUID());

		if (!state.pendingRewards.isEmpty()) {
			for (ItemStack stack : state.pendingRewards) {
				if (!player.getInventory().add(stack) && !stack.isEmpty()) {
					player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack));
				}
			}

			state.pendingRewards.clear();
		}

		if (now % 20 == 0 && player.isAlive()) {
			RelicAbilities.shotgunUpkeep(player, data);
		}

		// Chrome Breaker countdown on the action bar while you hold it.
		if (now % 10 == 0 && data.hasChrome() && (player.getMainHandItem().is(com.neonrelics.item.ModItems.CHROME_SHOTGUN)
				|| player.getOffhandItem().is(com.neonrelics.item.ModItems.CHROME_SHOTGUN))) {
			long left = state.shotgunReadyAt - now;

			if (left > 0) {
				Fx.actionBar(player, Component.literal("Chrome Breaker recharging: " + Fx.time(left)).withStyle(ChatFormatting.LIGHT_PURPLE));
			} else if (left > -10) {
				Fx.actionBar(player, Component.literal("Chrome Breaker READY").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
			}
		}

		if (relic == null || !player.isAlive()) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();

		switch (relic) {
			case HASTE -> {
				reapplyPermanentEffects(player, data);

				if (data.activeUntil != 0L && now >= data.activeUntil) {
					data.activeUntil = 0L;
				} else if (now < data.activeUntil && now % 3 == 0) {
					level.sendParticles(Fx.dust(0xB44CFF, 1.0F), player.xo, player.yo + 0.3, player.zo, 2, 0.2, 0.2, 0.2, 0.0);
				}
			}
			case PROSPERITY -> tickHero(player, state);
			case WEATHER -> {
				if (data.stormActive && !server.overworld().isThundering()) {
					data.stormActive = false;

					if (!data.mastered && !data.stormKill) {
						player.sendSystemMessage(Component.literal("The storm passed without a kill - no mastery progress.").withStyle(ChatFormatting.GRAY));
					}
				}
			}
			case CYBER -> tickCyber(player, data, state, level, now);
			case INFERNO -> {
				if (data.mastered && now % 10 == 0) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.3, 0.0, 0.3, 0.01);
				}
			}
			default -> { }
		}

		if (now % 5 == 0) {
			sendHud(player, data, relic, now);
		}
	}

	private static void tickHero(ServerPlayer player, PlayerState state) {
		MobEffectInstance hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);

		if (hero == null) {
			state.heroSeen = false;
			return;
		}

		if (state.heroSeen) {
			return;
		}

		state.heroSeen = true;

		if (hero.getAmplifier() < 4 && player.getRandom().nextDouble() < Tuning.PROSPERITY_HERO_V_CHANCE) {
			player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, hero.getDuration(), 4, false, false, true));
			Fx.title(player, Component.literal("HERO OF THE VILLAGE V").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
					Component.literal("Prosperity smiles on you").withStyle(ChatFormatting.YELLOW));
			Fx.particles((ServerLevel) player.level(), ParticleTypes.HAPPY_VILLAGER, player, 40, 0.8, 0.2);
		}
	}

	private static void tickCyber(ServerPlayer player, RelicData data, PlayerState state, ServerLevel level, long now) {
		if (data.hasBoot()) {
			if (now < data.activeUntil) {
				RelicAbilities.overdriveTick(player, state, now);
			} else if (data.activeUntil != 0L) {
				data.activeUntil = 0L;
				state.slowedProjectiles.clear();
				Fx.sound(level, player, SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.6F);
			} else if (data.maniacUntil == 0L && data.strain > 0.0F && now % Tuning.BOOT_STRAIN_DECAY_INTERVAL == 0) {
				data.strain = Math.max(0.0F, data.strain - 1.0F);

				if (data.strain < 50.0F) {
					state.strainWarning = 0;
				}
			}
		}

		if (data.hasChrome()) {
			Fx.keepPermanent(player, MobEffects.RESISTANCE, 1);
			RelicAbilities.trackSlam(player, state);

			if (data.maniacUntil == 0L) {
				data.maniacUntil = now + Tuning.CHROME_TIMER;
			}
		}

		if (data.maniacUntil == 0L) {
			return;
		}

		long left = data.maniacUntil - now;

		if (left <= 0) {
			burnout(player, data, level, now);
			return;
		}

		// Keep the Cyber Maniac effect in sync with the death timer (it can't be milked away).
		int amplifier = data.hasChrome() ? 1 : 0;
		MobEffectInstance current = player.getEffect(RelicEffects.CYBER_MANIAC);

		if (current == null || current.getAmplifier() != amplifier || Math.abs(current.getDuration() - left) > 40) {
			player.removeEffect(RelicEffects.CYBER_MANIAC);
			player.addEffect(new MobEffectInstance(RelicEffects.CYBER_MANIAC, (int) left, amplifier, false, true, true));
		}

		if (now % 10 == 0) {
			level.sendParticles(Fx.dust(0xFF1A3C, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
		}

		// NOVA countdown warnings.
		long[] marks = {10 * Tuning.MINUTE, 5 * Tuning.MINUTE, Tuning.MINUTE, 30 * Tuning.SECOND, 10 * Tuning.SECOND};

		for (long mark : marks) {
			if (left <= mark && left > mark - 20 && state.lastTimerWarning != mark) {
				state.lastTimerWarning = mark;
				Nova.warn(player, (data.hasChrome() ? "Chrome burnout in " : "Maniac burnout in ") + Fx.time(left)
						+ (data.hasChrome() ? ". Find something to kill." : "."));
				Fx.sound(level, player, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.0F);
			}
		}
	}

	private static void burnout(ServerPlayer player, RelicData data, ServerLevel level, long now) {
		data.maniacUntil = 0L;
		player.removeEffect(RelicEffects.CYBER_MANIAC);

		if (player.isCreative() || player.isSpectator()) {
			Nova.say(player, "Burnout reached - you're in creative, so I'll let it slide.");

			if (data.hasChrome()) {
				data.maniacUntil = now + Tuning.CHROME_TIMER;
			} else {
				data.strain = 0.0F;
			}

			return;
		}

		Fx.particles(level, Fx.dust(0xFF1A3C, 2.0F), player, 80, 0.6, 0.0);
		Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, player, 60, 0.6, 0.5);
		player.hurtServer(level, ModDamage.source(level, ModDamage.BURNOUT, null), Float.MAX_VALUE);
	}

	private static void sendHud(ServerPlayer player, RelicData data, Relic relic, long now) {
		if (!ServerPlayNetworking.canSend(player, HudPayload.TYPE)) {
			return;
		}

		int cooldownMax = switch (relic) {
			case WEATHER -> data.mastered ? Tuning.WEATHER_COOLDOWN_MASTERED : Tuning.WEATHER_COOLDOWN;
			default -> relic.cooldown;
		};
		int maniacMax = data.hasChrome() ? Tuning.CHROME_TIMER : Tuning.BOOT_MANIAC_DURATION;
		int activeMax = relic == Relic.HASTE ? Tuning.HASTE_ABILITY_DURATION : Tuning.BOOT_DURATION;

		ServerPlayNetworking.send(player, new HudPayload(
				data.relic,
				ticksLeft(data.cooldownUntil, now),
				cooldownMax,
				data.mastered,
				data.mastery,
				relic.masteryGoal,
				data.implant,
				data.strain,
				data.maniacUntil == 0L ? 0 : ticksLeft(data.maniacUntil, now),
				maniacMax,
				ticksLeft(data.activeUntil, now),
				activeMax,
				ticksLeft(data.passiveUntil, now)));
	}

	private static int ticksLeft(long until, long now) {
		return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, until - now));
	}

	// ------------------------------------------------------------------
	// Beast: right-clicking an axe after 3 axe hits = teleport
	// ------------------------------------------------------------------

	public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && NeonRelics.data(serverPlayer).is(Relic.BEAST)) {
			PlayerState state = PlayerState.of(serverPlayer.getUUID());

			if (state.teleportReady && RelicCombat.isAxe(serverPlayer.getItemInHand(hand))
					&& NeonRelics.now(serverPlayer.level().getServer()) > state.primedTick) {
				RelicAbilities.beastTeleport(serverPlayer, state);
			}
		}

		return InteractionResult.PASS;
	}

	// ------------------------------------------------------------------
	// Prosperity: half-price villager trades
	// ------------------------------------------------------------------

	/**
	 * Called by VillagerMixin right after a villager works out its prices for a player
	 * (reputation, Hero of the Village...) and before the trade screen gets them.
	 * Vanilla resets these prices every time trading starts and stops, so this never stacks.
	 */
	public static void applyProsperityDiscount(Player player, MerchantOffers offers) {
		if (!(player instanceof ServerPlayer serverPlayer) || !NeonRelics.data(serverPlayer).is(Relic.PROSPERITY)) {
			return;
		}

		for (MerchantOffer offer : offers) {
			int cost = offer.getCostA().getCount();
			int halved = Math.max(1, cost / 2);

			if (halved < cost) {
				offer.addToSpecialPriceDiff(halved - cost);
			}
		}
	}

	/** Called by MerchantResultSlotMixin whenever a player completes a trade. */
	public static void onTrade(Player player, Merchant merchant, @Nullable MerchantOffer offer, ItemStack taken) {
		if (!(player instanceof ServerPlayer serverPlayer) || offer == null || !NeonRelics.data(serverPlayer).is(Relic.PROSPERITY)) {
			return;
		}

		if (serverPlayer.getRandom().nextDouble() < Tuning.PROSPERITY_DOUBLE_TRADE_CHANCE) {
			PlayerState.of(serverPlayer.getUUID()).pendingRewards.add(offer.getResult().copy());
			Fx.actionBar(serverPlayer, Component.literal("Prosperity doubled your trade!").withStyle(ChatFormatting.YELLOW));
			Fx.particles((ServerLevel) serverPlayer.level(), ParticleTypes.HAPPY_VILLAGER, serverPlayer, 12, 0.5, 0.1);
		}
	}
}
