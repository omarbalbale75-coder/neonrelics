package com.neonrelics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import com.neonrelics.RelicEvents;

/**
 * Prosperity relic: halve villager prices. Runs after the villager has reset and
 * recalculated its special prices, and before the offers are sent to the player.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
	@Inject(method = "updateSpecialPrices", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/npc/villager/Villager;getTradingPlayer()Lnet/minecraft/world/entity/player/Player;"))
	private void neonrelics$prosperityDiscount(Player player, CallbackInfo ci) {
		RelicEvents.applyProsperityDiscount(player, ((Villager) (Object) this).getOffers());
	}
}
