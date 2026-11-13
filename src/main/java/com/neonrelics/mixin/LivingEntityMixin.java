package com.neonrelics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import com.neonrelics.RelicCombat;

/**
 * Lets relics change incoming damage (Weather tridents, Bloodlusted swords,
 * Haste Overclock, Chrome Body maces) before armor and effects are applied.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float neonrelics$modifyDamage(float amount, ServerLevel level, DamageSource source) {
		return RelicCombat.modifyDamage((LivingEntity) (Object) this, level, source, amount);
	}
}
