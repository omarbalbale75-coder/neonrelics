package com.neonrelics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.neonrelics.network.AbilityPayload;
import com.neonrelics.network.HudPayload;

public class NeonRelics implements ModInitializer {
	public static final String MOD_ID = "neonrelics";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final AttachmentType<RelicData> DATA = AttachmentRegistry.<RelicData>create(id("relic_data"), builder -> builder
			.initializer(RelicData::new)
			.persistent(RelicData.CODEC)
			.copyOnDeath());

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static RelicData data(ServerPlayer player) {
		return player.getAttachedOrCreate(DATA);
	}

	/** Absolute game time - keeps counting across restarts, so cooldowns and timers survive. */
	public static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	@Override
	public void onInitialize() {
		RelicEffects.init();

		PayloadTypeRegistry.serverboundPlay().register(AbilityPayload.TYPE, AbilityPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HudPayload.TYPE, HudPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE,
				(payload, context) -> context.server().execute(() -> RelicAbilities.use(context.player())));

		ServerPlayerEvents.JOIN.register(RelicEvents::onJoin);
		ServerPlayerEvents.LEAVE.register(RelicEvents::onLeave);
		ServerPlayerEvents.AFTER_RESPAWN.register(RelicEvents::onRespawn);
		ServerTickEvents.END_SERVER_TICK.register(RelicEvents::onServerTick);

		AttackEntityCallback.EVENT.register(RelicCombat::onAttack);
		UseEntityCallback.EVENT.register(RelicEvents::onUseEntity);
		UseItemCallback.EVENT.register(RelicEvents::onUseItem);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RelicCombat::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(RelicCombat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(RelicCombat::afterDeath);
		LootTableEvents.MODIFY_DROPS.register(RelicCombat::modifyDrops);

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> RelicCommands.register(dispatcher));

		LOGGER.info("Neon Relics loaded - 7 relics ready.");
	}
}
