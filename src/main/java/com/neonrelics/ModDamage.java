package com.neonrelics;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Custom damage types (defined as JSON in data/neonrelics/damage_type).
 */
public final class ModDamage {
	private ModDamage() { }

	/** Ignores armor, Resistance, Protection, shields and hit cooldown. */
	public static final ResourceKey<DamageType> TRUE_DAMAGE = key("true_damage");
	/** Mastered Inferno burn that hits through Fire Resistance. */
	public static final ResourceKey<DamageType> HELLFIRE = key("hellfire");
	/** Cyber Maniac running out. Cannot be blocked by anything, including totems. */
	public static final ResourceKey<DamageType> BURNOUT = key("burnout");

	private static ResourceKey<DamageType> key(String path) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, NeonRelics.id(path));
	}

	public static DamageSource source(ServerLevel level, ResourceKey<DamageType> key, @Nullable Entity attacker) {
		Holder<DamageType> type = level.registryAccess()
				.lookupOrThrow(Registries.DAMAGE_TYPE)
				.get(key.identifier())
				.orElseThrow();
		return attacker == null ? new DamageSource(type) : new DamageSource(type, attacker);
	}

	public static boolean isOurs(DamageSource source) {
		return source.is(TRUE_DAMAGE) || source.is(HELLFIRE) || source.is(BURNOUT);
	}

	public static void trueDamage(ServerLevel level, LivingEntity target, @Nullable Entity attacker, float amount) {
		if (target.isAlive()) {
			target.hurtServer(level, source(level, TRUE_DAMAGE, attacker), amount);
		}
	}
}
