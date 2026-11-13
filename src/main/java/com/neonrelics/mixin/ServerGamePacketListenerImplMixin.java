package com.neonrelics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import com.neonrelics.RelicEvents;

/**
 * Detects the player swinging (left click), used for the Beast relic's empowered spear lunge.
 * Injected after the packet has been moved onto the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V", shift = At.Shift.BEFORE))
	private void neonrelics$onPunch(ServerboundPunchPacket packet, CallbackInfo ci) {
		RelicEvents.onPunch(this.player);
	}
}
