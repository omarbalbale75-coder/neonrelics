package com.neonrelics.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.neonrelics.NeonRelics;
import com.neonrelics.network.AbilityPayload;
import com.neonrelics.network.HudPayload;

public class NeonRelicsClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(NeonRelics.id("relics"));

	/** Default: B. Rebind it in Options > Controls > Key Binds > Neon Relics. */
	public static final KeyMapping ABILITY_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.neonrelics.ability", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_B, CATEGORY));

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, context) -> RelicHud.update(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> RelicHud.clear());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ABILITY_KEY.consumeClick()) {
				if (client.player != null && ClientPlayNetworking.canSend(AbilityPayload.TYPE)) {
					ClientPlayNetworking.send(AbilityPayload.INSTANCE);
				}
			}
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, NeonRelics.id("screen_fx"), RelicHud::extractScreenEffects);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, NeonRelics.id("relic_hud"), RelicHud::extract);
	}
}
