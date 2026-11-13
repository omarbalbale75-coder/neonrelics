package com.neonrelics.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;

import com.neonrelics.RelicEvents;

/**
 * Detects a completed villager trade for the Prosperity relic's 25% double-trade chance.
 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
	@Shadow
	@Final
	private Merchant merchant;

	@Shadow
	@Final
	private MerchantContainer slots;

	@Inject(method = "onTake", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V"))
	private void neonrelics$onTrade(Player player, ItemStack stack, CallbackInfo ci) {
		if (!player.level().isClientSide()) {
			RelicEvents.onTrade(player, this.merchant, this.slots.getActiveOffer(), stack);
		}
	}
}
