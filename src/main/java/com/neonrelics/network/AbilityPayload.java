package com.neonrelics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.neonrelics.NeonRelics;

/** Client -> server: the relic ability key was pressed. */
public final class AbilityPayload implements CustomPacketPayload {
	public static final AbilityPayload INSTANCE = new AbilityPayload();
	public static final CustomPacketPayload.Type<AbilityPayload> TYPE = new CustomPacketPayload.Type<>(NeonRelics.id("use_ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityPayload> CODEC = StreamCodec.unit(INSTANCE);

	private AbilityPayload() { }

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
