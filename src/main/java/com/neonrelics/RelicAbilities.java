package com.neonrelics;

import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.neonrelics.item.ModItems;

/**
 * Everything that happens when the ability key (default B) is pressed.
 */
public final class RelicAbilities {
	private RelicAbilities() { }

	public static void use(ServerPlayer player) {
		RelicData data = NeonRelics.data(player);
		Relic relic = data.relic();

		if (relic == null || !player.isAlive() || player.isSpectator()) {
			return;
		}

		long now = NeonRelics.now(player.level().getServer());

		switch (relic) {
			case PROSPERITY -> Fx.actionBar(player, Component.literal("Prosperity has no active ability - its power is always on.").withStyle(ChatFormatting.YELLOW));
			case WEATHER -> weatherMenu(player, data, now);
			case CYBER -> cyber(player, data, now);
			default -> {
				if (now < data.cooldownUntil) {
					cooldownMessage(player, relic, data.cooldownUntil - now);
					return;
				}

				boolean used = switch (relic) {
					case INFERNO -> infernoSlash(player, data, now);
					case BEAST -> beastBite(player, now);
					case BLOODLUSTED -> bloodFrenzy(player);
					case HASTE -> overclock(player, data, now);
					default -> false;
				};

				if (used) {
					data.cooldownUntil = now + relic.cooldown;
				}
			}
		}
	}

	private static void cooldownMessage(ServerPlayer player, Relic relic, long left) {
		Fx.actionBar(player, Component.literal(relic.abilityName + " recharging: " + Fx.time(left)).withStyle(ChatFormatting.GRAY));
	}

	// ------------------------------------------------------------------
	// Targeting helpers
	// ------------------------------------------------------------------

	private static boolean validTarget(ServerPlayer player, Entity entity) {
		return entity instanceof LivingEntity && entity != player && entity.isAlive() && !entity.isSpectator() && !player.isAlliedTo(entity);
	}

	/** The living entity the player is looking at, within range and not behind a wall. */
	@Nullable
	public static LivingEntity lookTarget(ServerPlayer player, double range) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));

		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}

		AABB search = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;

		for (Entity entity : level.getEntities(player, search, e -> validTarget(player, e))) {
			AABB box = entity.getBoundingBox().inflate(0.3);
			Optional<Vec3> hit = box.clip(eye, end);

			if (box.contains(eye)) {
				return (LivingEntity) entity;
			}

			if (hit.isPresent()) {
				double dist = eye.distanceToSqr(hit.get());

				if (dist < bestDist) {
					bestDist = dist;
					best = (LivingEntity) entity;
				}
			}
		}

		return best;
	}

	private static void teleport(ServerPlayer player, Vec3 pos, float yaw, float pitch) {
		player.teleportTo((ServerLevel) player.level(), pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true);
		player.resetFallDistance();
	}

	// ------------------------------------------------------------------
	// Inferno
	// ------------------------------------------------------------------

	private static boolean infernoSlash(ServerPlayer player, RelicData data, long now) {
		ServerLevel level = (ServerLevel) player.level();
		float yawRad = player.getYRot() * ((float) Math.PI / 180.0F);
		Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 origin = player.position();
		double halfWidth = Tuning.INFERNO_SLASH_WIDTH / 2.0;
		float trueDamage = data.mastered ? Tuning.INFERNO_SLASH_TRUE_DAMAGE_MASTERED : Tuning.INFERNO_SLASH_TRUE_DAMAGE;
		int burn = data.mastered ? Tuning.INFERNO_SLASH_BURN * 2 : Tuning.INFERNO_SLASH_BURN;

		AABB area = player.getBoundingBox().inflate(Tuning.INFERNO_SLASH_REACH + 1.0, 2.0, Tuning.INFERNO_SLASH_REACH + 1.0);
		int hits = 0;

		for (Entity entity : level.getEntities(player, area, e -> validTarget(player, e))) {
			Vec3 offset = entity.position().subtract(origin);
			double along = offset.dot(forward);
			double side = offset.dot(right);

			if (along < -0.5 || along > Tuning.INFERNO_SLASH_REACH || Math.abs(side) > halfWidth || Math.abs(offset.y) > 2.5) {
				continue;
			}

			LivingEntity target = (LivingEntity) entity;
			RelicCombat.markSlashed(target, player, now);
			RelicCombat.burn(level, target, player, burn, data.mastered);
			ModDamage.trueDamage(level, target, player, trueDamage);
			Fx.particles(level, data.mastered ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, target, 15, 0.3, 0.05);
			hits++;
		}

		// The flaming arc
		Vec3 center = origin.add(0.0, player.getBbHeight() * 0.6, 0.0);

		for (double s = -halfWidth; s <= halfWidth; s += 0.25) {
			double bow = 2.2 + 0.8 * Math.cos(s / halfWidth * Math.PI / 2.0);
			Vec3 point = center.add(forward.scale(bow)).add(right.scale(s));
			level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 2, 0.05, 0.08, 0.05, 0.01);

			if (data.mastered) {
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y + 0.15, point.z, 1, 0.05, 0.05, 0.05, 0.01);
			}
		}

		for (int i = -1; i <= 1; i++) {
			Vec3 point = center.add(forward.scale(2.6)).add(right.scale(i * 1.6));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		}

		level.sendParticles(ParticleTypes.LAVA, center.x + forward.x * 2, center.y, center.z + forward.z * 2, 8, 1.2, 0.3, 1.2, 0.0);
		Fx.sound(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
		Fx.sound(level, player, SoundEvents.BLAZE_SHOOT, 1.0F, 0.8F);
		Fx.actionBar(player, Component.literal("Infernal Slash" + (hits > 0 ? " - hit " + hits : "")).withStyle(ChatFormatting.GOLD));
		return true;
	}

	// ------------------------------------------------------------------
	// Weather
	// ------------------------------------------------------------------

	private static void weatherMenu(ServerPlayer player, RelicData data, long now) {
		if (now < data.cooldownUntil) {
			cooldownMessage(player, Relic.WEATHER, data.cooldownUntil - now);
			return;
		}

		player.sendSystemMessage(Component.literal("Stormcall - choose the sky:").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.literal("   ")
				.append(Nova.button("[Clear]", ChatFormatting.YELLOW, "/relic weather clear", "Clear skies (no trident buffs)"))
				.append(Component.literal("   "))
				.append(Nova.button("[Rain]", ChatFormatting.BLUE, "/relic weather rain", "Tridents: +20% damage, Sharpness III"))
				.append(Component.literal("   "))
				.append(Nova.button("[Thunderstorm]", ChatFormatting.DARK_PURPLE, "/relic weather thunder",
						"Tridents: +35% damage, Sharpness V, Channeling\nKill a player during it for mastery progress")));
	}

	/** Called by /relic weather. Returns false if nothing happened. */
	public static boolean setWeather(ServerPlayer player, String type) {
		RelicData data = NeonRelics.data(player);
		MinecraftServer server = player.level().getServer();
		long now = NeonRelics.now(server);

		if (!data.is(Relic.WEATHER)) {
			player.sendSystemMessage(Component.literal("Only the Weather relic can bend the sky.").withStyle(ChatFormatting.RED));
			return false;
		}

		if (now < data.cooldownUntil) {
			cooldownMessage(player, Relic.WEATHER, data.cooldownUntil - now);
			return false;
		}

		int duration = Tuning.WEATHER_DURATION;
		String label;

		switch (type) {
			case "clear" -> {
				server.setWeatherParameters(duration, 0, false, false);
				label = "Clear Skies";
			}
			case "rain" -> {
				server.setWeatherParameters(0, duration, true, false);
				label = "Rain";
			}
			case "thunder" -> {
				server.setWeatherParameters(0, duration, true, true);
				label = "Thunderstorm";
				data.stormActive = true;
				data.stormKill = false;
			}
			default -> {
				player.sendSystemMessage(Component.literal("Unknown weather: " + type).withStyle(ChatFormatting.RED));
				return false;
			}
		}

		data.cooldownUntil = now + (data.mastered ? Tuning.WEATHER_COOLDOWN_MASTERED : Tuning.WEATHER_COOLDOWN);
		ServerLevel level = (ServerLevel) player.level();
		Fx.sound(level, player, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
		Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, player, 40, 0.8, 0.4);
		server.getPlayerList().broadcastSystemMessage(Component.literal(player.getName().getString() + " bends the sky: ")
				.withStyle(ChatFormatting.AQUA)
				.append(Component.literal(label).withStyle(ChatFormatting.BOLD)), false);
		return true;
	}

	// ------------------------------------------------------------------
	// Beast
	// ------------------------------------------------------------------

	private static boolean beastBite(ServerPlayer player, long now) {
		LivingEntity target = lookTarget(player, Tuning.BEAST_BITE_REACH);

		if (target == null) {
			Fx.actionBar(player, Component.literal("Nothing in reach to bite.").withStyle(ChatFormatting.GRAY));
			return false;
		}

		ServerLevel level = (ServerLevel) player.level();
		ModDamage.trueDamage(level, target, player, Tuning.BEAST_BITE_TRUE_DAMAGE);

		if (target.isAlive()) {
			target.addEffect(new MobEffectInstance(RelicEffects.STUNNED, Tuning.BEAST_BITE_STUN, 0, false, true, true));
			Fx.effect(target, MobEffects.SLOWNESS, Tuning.BEAST_BITE_STUN, 9);
			Fx.effect(target, MobEffects.WEAKNESS, Tuning.BEAST_BITE_STUN, 9);
			Fx.effect(target, MobEffects.MINING_FATIGUE, Tuning.BEAST_BITE_STUN, 4);
			target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
			target.syncVelocity = true;
		}

		Fx.particles(level, ParticleTypes.DAMAGE_INDICATOR, target, 15, 0.3, 0.2);
		Fx.particles(level, ParticleTypes.CRIT, target, 25, 0.4, 0.3);
		Fx.particles(level, Fx.dust(0x9E0B0B, 1.5F), target, 20, 0.35, 0.0);
		Fx.sound(level, target, SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 0.7F);
		Fx.sound(level, player, SoundEvents.RAVAGER_ROAR, 0.6F, 1.6F);
		return true;
	}

	/** Beast passive: after 3 axe hits, right-click the axe to teleport. */
	public static void beastTeleport(ServerPlayer player, PlayerState state) {
		state.teleportReady = false;
		ServerLevel level = (ServerLevel) player.level();
		Vec3 start = player.position();
		Fx.particles(level, ParticleTypes.REVERSE_PORTAL, player, 40, 0.4, 0.1);
		LivingEntity target = lookTarget(player, Tuning.BEAST_TELEPORT_TARGET_RANGE);

		if (target != null) {
			// Leap straight at the enemy and land right in front of it.
			Vec3 toPlayer = start.subtract(target.position());
			Vec3 flat = new Vec3(toPlayer.x, 0.0, toPlayer.z);
			flat = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().scale(-1.0) : flat.normalize();
			double gap = target.getBbWidth() / 2.0 + player.getBbWidth() / 2.0 + 0.4;
			Vec3 dest = target.position().add(flat.scale(gap));

			if (!level.noCollision(player, player.getBoundingBox().move(dest.subtract(start)))) {
				dest = target.position();
			}

			Vec3 look = target.getEyePosition().subtract(dest.add(0.0, player.getEyeHeight(), 0.0));
			float yaw = (float) (Math.atan2(look.z, look.x) * 180.0 / Math.PI) - 90.0F;
			float pitch = (float) -(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * 180.0 / Math.PI);
			trail(level, start, dest);
			teleport(player, dest, yaw, pitch);
			Fx.actionBar(player, Component.literal("TELEPORT STRIKE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		} else {
			// Nothing to hunt - blink 6 blocks forward (escape / travel).
			Vec3 look = player.getLookAngle();
			Vec3 dir = new Vec3(look.x, Math.max(-0.3, Math.min(0.6, look.y)), look.z).normalize();
			Vec3 dest = start;

			for (double d = Tuning.BEAST_TELEPORT_DISTANCE; d > 0.4; d -= 0.5) {
				Vec3 candidate = start.add(dir.scale(d));

				if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(start)))
						&& clearPath(player, start, candidate)) {
					dest = candidate;
					break;
				}
			}

			if (dest == start) {
				return;
			}

			trail(level, start, dest);
			teleport(player, dest, player.getYRot(), player.getXRot());
			Fx.actionBar(player, Component.literal("TELEPORT +6").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}

		Fx.particles(level, ParticleTypes.PORTAL, player, 60, 0.4, 0.6);
		Fx.sound(level, player, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
	}

	private static boolean clearPath(ServerPlayer player, Vec3 from, Vec3 to) {
		Vec3 lift = new Vec3(0.0, 0.5, 0.0);
		BlockHitResult hit = player.level().clip(new ClipContext(from.add(lift), to.add(lift), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		int points = (int) Math.max(4, step.length() * 3);

		for (int i = 0; i <= points; i++) {
			Vec3 p = from.add(step.scale(i / (double) points)).add(0.0, 1.0, 0.0);
			level.sendParticles(Fx.dust(0xB01515, 1.2F), p.x, p.y, p.z, 2, 0.2, 0.4, 0.2, 0.0);
		}
	}

	// ------------------------------------------------------------------
	// Cyber
	// ------------------------------------------------------------------

	private static void cyber(ServerPlayer player, RelicData data, long now) {
		if (data.implant.isEmpty()) {
			Nova.say(player, "No implant installed. Choose one:");
			Nova.shop(player);
			return;
		}

		if (data.hasChrome()) {
			Nova.say(player, "Chrome Body has no active ability. Systems at 200%. Time until burnout: "
					+ Fx.time(Math.max(0, data.maniacUntil - now)) + ". Keep killing.");
			return;
		}

		// Boot Accelerator
		if (now < data.activeUntil) {
			return;
		}

		if (now < data.cooldownUntil) {
			Fx.actionBar(player, Component.literal("Boot Accelerator cooling: " + Fx.time(data.cooldownUntil - now)
					+ "  |  Neural load " + Math.round(data.strain) + "%").withStyle(ChatFormatting.GRAY));
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		data.activeUntil = now + Tuning.BOOT_DURATION;
		data.cooldownUntil = now + Tuning.BOOT_COOLDOWN;
		PlayerState.of(player.getUUID()).slowedProjectiles.clear();
		Fx.sound(level, player, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.9F);
		Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, player, 40, 0.6, 0.3);

		if (data.maniacUntil > 0) {
			return; // already burning out - strain is pinned at 100%
		}

		data.strain = Math.min(100.0F, data.strain + Tuning.BOOT_STRAIN_PER_USE);
		PlayerState state = PlayerState.of(player.getUUID());

		if (data.strain >= 100.0F) {
			startManiac(player, data, now, Tuning.BOOT_MANIAC_DURATION);
		} else if (data.strain >= 85.0F && state.strainWarning < 2) {
			state.strainWarning = 2;
			Nova.warn(player, "WARNING: neural load at " + Math.round(data.strain) + "%. One more burst and you go maniac. STOP.");
		} else if (data.strain >= 60.0F && state.strainWarning < 1) {
			state.strainWarning = 1;
			Nova.say(player, "Neural load at " + Math.round(data.strain) + "%. Ease off the accelerator - watch the bar.");
		}
	}

	public static void startManiac(ServerPlayer player, RelicData data, long now, int ticks) {
		data.maniacUntil = now + ticks;
		data.strain = 100.0F;
		ServerLevel level = (ServerLevel) player.level();
		player.addEffect(new MobEffectInstance(RelicEffects.CYBER_MANIAC, ticks, data.hasChrome() ? 1 : 0, false, true, true));
		Fx.title(player, Component.literal("CYBER MANIAC").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.literal("You have " + Fx.time(ticks) + ". Make it count.").withStyle(ChatFormatting.GRAY));
		Fx.sound(level, player, SoundEvents.WARDEN_ROAR, 1.0F, 1.3F);
		Fx.particles(level, Fx.dust(0xFF1A3C, 1.8F), player, 60, 0.6, 0.0);
		Nova.warn(player, "Neural load critical. Limiters gone. I can't pull you back from this one.");
	}

	public static boolean buyImplant(ServerPlayer player, String implant) {
		RelicData data = NeonRelics.data(player);

		if (!data.is(Relic.CYBER)) {
			player.sendSystemMessage(Component.literal("Only a Cyber relic can take implants.").withStyle(ChatFormatting.RED));
			return false;
		}

		if (!implant.equals(RelicData.IMPLANT_BOOT) && !implant.equals(RelicData.IMPLANT_CHROME)) {
			Nova.warn(player, "Unknown implant. Options: " + RelicData.IMPLANT_BOOT + ", " + RelicData.IMPLANT_CHROME);
			return false;
		}

		if (!data.implant.isEmpty()) {
			Nova.say(player, "You already run a " + (data.hasChrome() ? "Chrome Body" : "Boot Accelerator") + ". Implants are permanent.");
			return false;
		}

		int cost = Tuning.CYBER_IMPLANT_COST_DIAMONDS;

		if (!player.isCreative()) {
			Inventory inventory = player.getInventory();
			int have = 0;

			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);

				if (stack.is(Items.DIAMOND)) {
					have += stack.getCount();
				}
			}

			if (have < cost) {
				Nova.warn(player, "Insufficient funds. The install costs " + cost + " diamonds - you have " + have + ".");
				return false;
			}

			int left = cost;

			for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
				ItemStack stack = inventory.getItem(i);

				if (stack.is(Items.DIAMOND)) {
					int take = Math.min(left, stack.getCount());
					stack.shrink(take);
					left -= take;
				}
			}
		}

		long now = NeonRelics.now(player.level().getServer());
		ServerLevel level = (ServerLevel) player.level();
		data.implant = implant;
		Fx.sound(level, player, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
		Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, player, 60, 0.6, 0.4);

		if (data.hasChrome()) {
			Fx.keepPermanent(player, MobEffects.RESISTANCE, 1);
			startManiac(player, data, now, Tuning.CHROME_TIMER);
			shotgunUpkeep(player, data);
			Nova.say(player, "Chrome Body online. Resistance II, double mace damage, zero fall damage.");
			Nova.say(player, "Shotgun linked to your chrome. Lose it and I'll print you another.");
			Nova.warn(player, "You now run permanently at 200% Cyber Maniac. You burn out in 1 hour - every kill resets the clock.");
		} else {
			Nova.say(player, "Boot Accelerator installed. Press your ability key to overdrive - the world slows, you don't.");
			Nova.say(player, "Watch the NEURAL LOAD bar on the right of your screen. If it hits 100% you go Cyber Maniac");
			Nova.warn(player, "...and Cyber Maniac kills you after 3 minutes. Don't spam it.");
		}

		return true;
	}

	/** Runs every tick while the Boot Accelerator is overdriving. */
	public static void overdriveTick(ServerPlayer player, PlayerState state, long now) {
		ServerLevel level = (ServerLevel) player.level();

		if (now % 5 == 0) {
			Fx.effect(player, MobEffects.SPEED, 15, 2);
			Fx.effect(player, MobEffects.HASTE, 15, 1);
			AABB area = player.getBoundingBox().inflate(Tuning.BOOT_RADIUS);

			for (Entity entity : level.getEntities(player, area, e -> e.isAlive() && !e.isSpectator())) {
				if (entity instanceof LivingEntity living) {
					living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 3, false, false, true));
					living.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 15, 1, false, false, true));
				}
			}
		}

		// Freeze incoming projectiles in place.
		AABB area = player.getBoundingBox().inflate(Tuning.BOOT_RADIUS);

		for (Entity entity : level.getEntities(player, area, e -> e instanceof net.minecraft.world.entity.projectile.Projectile)) {
			net.minecraft.world.entity.projectile.Projectile projectile = (net.minecraft.world.entity.projectile.Projectile) entity;

			if (projectile.getOwner() != player && state.slowedProjectiles.add(projectile.getUUID())) {
				projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.2));
				projectile.syncVelocity = true;
			}
		}

		// Neon afterimages.
		if (now % 2 == 0) {
			int[] colors = {0x00FFC8, 0x00B4FF, 0x7A3CFF, 0xFF2EA6, 0xB6FF00};
			int color = colors[(int) ((now / 2) % colors.length)];

			for (double y = 0.2; y < player.getBbHeight(); y += 0.4) {
				level.sendParticles(Fx.dust(color, 1.3F), player.xo, player.yo + y, player.zo, 2, 0.12, 0.08, 0.12, 0.0);
			}
		}
	}

	// ------------------------------------------------------------------
	// Chrome Body shotgun
	// ------------------------------------------------------------------

	public static void fireShotgun(ServerPlayer player) {
		RelicData data = NeonRelics.data(player);
		ServerLevel level = (ServerLevel) player.level();

		if (!data.hasChrome()) {
			Fx.actionBar(player, Component.literal("The grip won't lock to flesh. Chrome Body only.").withStyle(ChatFormatting.GRAY));
			return;
		}

		PlayerState state = PlayerState.of(player.getUUID());
		long now = NeonRelics.now(level.getServer());

		if (now < state.shotgunReadyAt) {
			return;
		}

		state.shotgunReadyAt = now + Tuning.SHOTGUN_COOLDOWN;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double minDot = Math.cos(Math.toRadians(Tuning.SHOTGUN_CONE));
		AABB area = player.getBoundingBox().inflate(Tuning.SHOTGUN_RANGE);

		for (Entity entity : level.getEntities(player, area, e -> validTarget(player, e))) {
			Vec3 center = entity.getBoundingBox().getCenter();
			Vec3 toTarget = center.subtract(eye);
			double distance = toTarget.length();

			if (distance > Tuning.SHOTGUN_RANGE || distance < 1.0E-3 || toTarget.normalize().dot(look) < minDot) {
				continue;
			}

			BlockHitResult wall = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

			if (wall.getType() != HitResult.Type.MISS) {
				continue;
			}

			LivingEntity target = (LivingEntity) entity;
			ModDamage.trueDamage(level, target, player, Tuning.SHOTGUN_TRUE_DAMAGE);
			target.setDeltaMovement(target.getDeltaMovement().add(look.x * 0.9, 0.25, look.z * 0.9));
			target.syncVelocity = true;
			Fx.particles(level, ParticleTypes.CRIT, target, 12, 0.3, 0.2);
		}

		// Pellet trails
		var random = player.getRandom();

		for (int i = 0; i < 9; i++) {
			Vec3 dir = look.add((random.nextDouble() - 0.5) * 0.35, (random.nextDouble() - 0.5) * 0.35, (random.nextDouble() - 0.5) * 0.35).normalize();

			for (double d = 1.0; d < Tuning.SHOTGUN_RANGE; d += 0.8) {
				Vec3 p = eye.add(dir.scale(d));
				level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		// Muzzle blast
		Vec3 muzzle = eye.add(look.scale(1.2)).add(0.0, -0.2, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 10, 0.15, 0.15, 0.15, 0.05);
		level.sendParticles(Fx.dust(0xFF2244, 1.5F), muzzle.x, muzzle.y, muzzle.z, 10, 0.2, 0.2, 0.2, 0.0);
		Fx.sound(level, player, SoundEvents.GENERIC_EXPLODE, 0.7F, 1.8F);
		Fx.sound(level, player, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 0.6F);

		// Recoil
		player.setDeltaMovement(player.getDeltaMovement().subtract(look.scale(0.35)));
		player.syncVelocity = true;
	}

	/**
	 * Chrome Body always carries exactly one shotgun (with Curse of Vanishing, so it never drops on death).
	 * Anyone else who ends up holding one loses it.
	 */
	public static void shotgunUpkeep(ServerPlayer player, RelicData data) {
		Inventory inventory = player.getInventory();
		boolean chrome = data.hasChrome();
		int found = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(ModItems.CHROME_SHOTGUN)) {
				if (chrome) {
					found++;
				} else {
					inventory.setItem(i, ItemStack.EMPTY);
				}
			}
		}

		boolean carried = player.containerMenu.getCarried().is(ModItems.CHROME_SHOTGUN);

		if (!chrome) {
			if (carried) {
				player.containerMenu.setCarried(ItemStack.EMPTY);
			}

			return;
		}

		if (found == 0 && !carried) {
			ServerLevel level = (ServerLevel) player.level();
			ItemStack gun = new ItemStack(ModItems.CHROME_SHOTGUN);
			gun.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE), 1);

			if (!inventory.add(gun)) {
				level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), gun));
			}
		}
	}

	// ------------------------------------------------------------------
	// Bloodlusted & Haste
	// ------------------------------------------------------------------

	private static boolean bloodFrenzy(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Fx.effect(player, MobEffects.STRENGTH, Tuning.BLOOD_ABILITY_DURATION, 3);
		Fx.effect(player, MobEffects.SPEED, Tuning.BLOOD_ABILITY_DURATION, 2);
		Fx.particles(level, Fx.dust(0xC00018, 2.0F), player, 60, 0.7, 0.0);
		Fx.particles(level, ParticleTypes.DAMAGE_INDICATOR, player, 15, 0.5, 0.2);
		Fx.sound(level, player, SoundEvents.RAVAGER_ROAR, 1.0F, 0.8F);
		Fx.actionBar(player, Component.literal("BLOOD FRENZY").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		return true;
	}

	private static boolean overclock(ServerPlayer player, RelicData data, long now) {
		ServerLevel level = (ServerLevel) player.level();
		data.activeUntil = now + Tuning.HASTE_ABILITY_DURATION;
		player.addEffect(new MobEffectInstance(RelicEffects.OVERCLOCK, Tuning.HASTE_ABILITY_DURATION, 0, false, true, true));
		Fx.effect(player, MobEffects.SPEED, Tuning.HASTE_ABILITY_DURATION, 2);
		Fx.particles(level, Fx.dust(0xB44CFF, 1.6F), player, 50, 0.6, 0.0);
		Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, player, 30, 0.5, 0.3);
		Fx.sound(level, player, SoundEvents.BEACON_ACTIVATE, 1.0F, 2.0F);
		Fx.actionBar(player, Component.literal("OVERCLOCK").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		return true;
	}
}
