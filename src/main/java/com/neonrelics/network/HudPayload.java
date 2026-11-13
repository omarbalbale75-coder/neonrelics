package com.neonrelics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.neonrelics.NeonRelics;

/** Server -> client: everything the relic HUD needs to draw. Sent a few times per second. */
public record HudPayload(
		String relic,
		int cooldown,
		int cooldownMax,
		boolean mastered,
		int mastery,
		int masteryGoal,
		String implant,
		float strain,
		int maniac,
		int maniacMax,
		int active,
		int activeMax,
		int passiveCooldown
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HudPayload> TYPE = new CustomPacketPayload.Type<>(NeonRelics.id("hud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC = StreamCodec.of(HudPayload::write, HudPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, HudPayload p) {
		buf.writeUtf(p.relic);
		buf.writeVarInt(p.cooldown);
		buf.writeVarInt(p.cooldownMax);
		buf.writeBoolean(p.mastered);
		buf.writeVarInt(p.mastery);
		buf.writeVarInt(p.masteryGoal);
		buf.writeUtf(p.implant);
		buf.writeFloat(p.strain);
		buf.writeVarInt(p.maniac);
		buf.writeVarInt(p.maniacMax);
		buf.writeVarInt(p.active);
		buf.writeVarInt(p.activeMax);
		buf.writeVarInt(p.passiveCooldown);
	}

	private static HudPayload read(RegistryFriendlyByteBuf buf) {
		return new HudPayload(
				buf.readUtf(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readBoolean(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readUtf(),
				buf.readFloat(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
