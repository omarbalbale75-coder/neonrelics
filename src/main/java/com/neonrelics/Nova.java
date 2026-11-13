package com.neonrelics;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * NOVA - the Cyber relic's personal AI assistant. Talks to the player in chat,
 * sells implants and nags them when they push the Boot Accelerator too hard.
 */
public final class Nova {
	private Nova() { }

	private static MutableComponent prefix() {
		return Component.literal("[NOVA] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
	}

	public static void say(ServerPlayer player, String message) {
		player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
	}

	public static void warn(ServerPlayer player, String message) {
		player.sendSystemMessage(prefix().append(Component.literal(message).withStyle(ChatFormatting.RED)));
	}

	public static void greet(ServerPlayer player) {
		say(player, "Neural link established. I'm NOVA, your personal assistant.");
		say(player, "Your chrome is bare. Pick ONE permanent implant (" + Tuning.CYBER_IMPLANT_COST_DIAMONDS + " diamonds):");
		shop(player);
	}

	public static void shop(ServerPlayer player) {
		MutableComponent boot = button("[Boot Accelerator]", ChatFormatting.GREEN, "/relic implant " + RelicData.IMPLANT_BOOT,
				"Ability: Overdrive - the world slows to a crawl while you move at full speed.\n"
						+ "Every use raises your Neural Load bar. Hit 100% and you go Cyber Maniac:\n"
						+ "massively enhanced for 3 minutes... then you die.");
		MutableComponent chrome = button("[Chrome Body]", ChatFormatting.LIGHT_PURPLE, "/relic implant " + RelicData.IMPLANT_CHROME,
				"Passives: permanent Resistance II, double mace damage, no fall damage.\n"
						+ "You are permanently at 200% Cyber Maniac. You die after 1 hour\n"
						+ "unless you keep killing - every kill resets the timer.");
		player.sendSystemMessage(Component.literal("   ").append(boot).append(Component.literal("   ")).append(chrome));
	}

	public static MutableComponent button(String label, ChatFormatting color, String command, String hover) {
		return Component.literal(label).withStyle(style -> style
				.withColor(color)
				.withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}
}
