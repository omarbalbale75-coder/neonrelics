package com.neonrelics;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class RelicEffects {
	private RelicEffects() { }

	/**
	 * Cyber Maniac: only obtainable from the Cyber relic. Everything is enhanced
	 * (damage, speed, attack speed, armor, knockback resistance, regeneration) - but
	 * when the timer runs out, you die. Level II (Chrome Body) is twice as strong.
	 */
	public static final Holder<MobEffect> CYBER_MANIAC = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, NeonRelics.id("cyber_maniac"),
			new CyberManiacEffect()
					.addAttributeModifier(Attributes.ATTACK_DAMAGE, NeonRelics.id("effect.cyber_maniac.damage"), 3.0, AttributeModifier.Operation.ADD_VALUE)
					.addAttributeModifier(Attributes.MOVEMENT_SPEED, NeonRelics.id("effect.cyber_maniac.speed"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.ATTACK_SPEED, NeonRelics.id("effect.cyber_maniac.attack_speed"), 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.ARMOR, NeonRelics.id("effect.cyber_maniac.armor"), 4.0, AttributeModifier.Operation.ADD_VALUE)
					.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, NeonRelics.id("effect.cyber_maniac.knockback"), 0.25, AttributeModifier.Operation.ADD_VALUE));

	/** Haste relic ability: attacks recharge twice as fast. */
	public static final Holder<MobEffect> OVERCLOCK = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, NeonRelics.id("overclock"),
			new RelicEffect(MobEffectCategory.BENEFICIAL, 0xB44CFF)
					.addAttributeModifier(Attributes.ATTACK_SPEED, NeonRelics.id("effect.overclock.attack_speed"), 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	/** Beast bite: rooted in place. */
	public static final Holder<MobEffect> STUNNED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, NeonRelics.id("stunned"),
			new RelicEffect(MobEffectCategory.HARMFUL, 0x8A1B1B)
					.addAttributeModifier(Attributes.MOVEMENT_SPEED, NeonRelics.id("effect.stunned.speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.JUMP_STRENGTH, NeonRelics.id("effect.stunned.jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	public static void init() {
		// Loads the class so the static fields above get registered.
	}

	public static class RelicEffect extends MobEffect {
		public RelicEffect(MobEffectCategory category, int color) {
			super(category, color);
		}
	}

	public static class CyberManiacEffect extends RelicEffect {
		public CyberManiacEffect() {
			super(MobEffectCategory.NEUTRAL, 0xFF2E63);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			int interval = 50 >> amplifier;
			return interval <= 0 || duration % interval == 0;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			if (entity.getHealth() < entity.getMaxHealth()) {
				entity.heal(1.0F);
			}

			return true;
		}
	}
}
