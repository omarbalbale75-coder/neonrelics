package com.neonrelics;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Small helpers for particles, sounds, titles and effects. */
public final class Fx {
	private Fx() { }

	public static void particles(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
		level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
	}

	public static void particles(ServerLevel level, ParticleOptions particle, Entity entity, int count, double spread, double speed) {
		level.sendParticles(particle, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), count, spread, entity.getBbHeight() * 0.3, spread, speed);
	}

	public static DustParticleOptions dust(int rgb, float scale) {
		return new DustParticleOptions(0xFF000000 | rgb, scale);
	}

	public static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static void sound(ServerLevel level, Entity at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static void actionBar(ServerPlayer player, Component message) {
		player.connection.send(new ClientboundSetActionBarTextPacket(message));
	}

	public static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	public static void effect(LivingEntity entity, Holder<MobEffect> effect, int ticks, int amplifier) {
		entity.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true, true));
	}

	/** Keep a permanent, particle-free effect on an entity without fighting stronger temporary versions. */
	public static void keepPermanent(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = entity.getEffect(effect);

		if (current == null || current.getAmplifier() < amplifier
				|| (current.getAmplifier() == amplifier && !current.isInfiniteDuration())) {
			entity.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, true, false, true));
		}
	}

	/** Remove an effect only if it is one of our permanent ones. */
	public static void removePermanent(LivingEntity entity, Holder<MobEffect> effect) {
		MobEffectInstance current = entity.getEffect(effect);

		if (current != null && current.isInfiniteDuration()) {
			entity.removeEffect(effect);
		}
	}

	public static String time(long ticks) {
		long seconds = Math.max(0, (ticks + 19) / 20);
		return seconds >= 60 ? String.format("%d:%02d", seconds / 60, seconds % 60) : seconds + "s";
	}
}
