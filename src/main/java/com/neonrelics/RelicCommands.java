package com.neonrelics;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;

/**
 * /relic                       - show your relic
 * /relic ability               - use your ability (same as the key)
 * /relic weather <type>        - Weather relic: clear | rain | thunder
 * /relic implant <type>        - Cyber relic: boot_accelerator | chrome_body
 * /relic set <players> <relic> - (op) give a relic
 * /relic reroll <players>      - (op) random new relic
 * /relic master <players>      - (op) instantly master the relic
 * /relic reset <players>       - (op) clear cooldowns, strain and maniac
 */
public final class RelicCommands {
	private RelicCommands() { }

	private static final List<String> RELIC_IDS = Arrays.stream(Relic.values()).map(r -> r.id).toList();

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("relic")
				.executes(ctx -> info(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("ability").executes(ctx -> {
					RelicAbilities.use(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("weather")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("clear", "rain", "thunder"), builder))
								.executes(ctx -> RelicAbilities.setWeather(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type")) ? 1 : 0)))
				.then(Commands.literal("implant")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of(RelicData.IMPLANT_BOOT, RelicData.IMPLANT_CHROME), builder))
								.executes(ctx -> RelicAbilities.buyImplant(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type")) ? 1 : 0)))
				.then(Commands.literal("set")
						.requires(PermissionPredicates.require(NeonRelics.id("command/admin"), PermissionLevel.ADMINS))
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("relic", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RELIC_IDS, builder))
										.executes(RelicCommands::set))))
				.then(Commands.literal("reroll")
						.requires(PermissionPredicates.require(NeonRelics.id("command/admin"), PermissionLevel.ADMINS))
						.then(Commands.argument("targets", EntityArgument.players()).executes(RelicCommands::reroll)))
				.then(Commands.literal("master")
						.requires(PermissionPredicates.require(NeonRelics.id("command/admin"), PermissionLevel.ADMINS))
						.then(Commands.argument("targets", EntityArgument.players()).executes(RelicCommands::master)))
				.then(Commands.literal("reset")
						.requires(PermissionPredicates.require(NeonRelics.id("command/admin"), PermissionLevel.ADMINS))
						.then(Commands.argument("targets", EntityArgument.players()).executes(RelicCommands::reset))));
	}

	private static int info(ServerPlayer player) {
		RelicData data = NeonRelics.data(player);
		Relic relic = data.relic();

		if (relic == null) {
			player.sendSystemMessage(Component.literal("You have no relic yet.").withStyle(ChatFormatting.GRAY));
			return 0;
		}

		RelicEvents.sendInfo(player, relic);
		long now = NeonRelics.now(player.level().getServer());

		if (relic.masteryGoal > 0) {
			player.sendSystemMessage(Component.literal(data.mastered ? "MASTERED" : "Mastery: " + data.mastery + "/" + relic.masteryGoal)
					.withStyle(data.mastered ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}

		if (relic.hasAbility() && now < data.cooldownUntil) {
			player.sendSystemMessage(Component.literal("Ability ready in " + Fx.time(data.cooldownUntil - now)).withStyle(ChatFormatting.GRAY));
		}

		if (relic == Relic.CYBER) {
			player.sendSystemMessage(Component.literal("Implant: " + (data.implant.isEmpty() ? "none" : data.implant)
					+ (data.hasBoot() ? "  |  Neural load " + Math.round(data.strain) + "%" : "")).withStyle(ChatFormatting.AQUA));
		}

		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Relic relic = Relic.byId(StringArgumentType.getString(ctx, "relic"));

		if (relic == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown relic. Options: " + String.join(", ", RELIC_IDS)));
			return 0;
		}

		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");

		for (ServerPlayer player : targets) {
			RelicEvents.give(player, relic, false);
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Gave the " + relic.displayName + " relic to " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static int reroll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");

		for (ServerPlayer player : targets) {
			Relic[] all = Relic.values();
			RelicEvents.give(player, all[player.getRandom().nextInt(all.length)], false);
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Rerolled relics for " + targets.size() + " player(s)"), true);
		return targets.size();
	}

	private static int master(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");

		for (ServerPlayer player : targets) {
			RelicData data = NeonRelics.data(player);
			Relic relic = data.relic();

			if (relic != null && relic.masteryGoal > 0) {
				data.mastery = relic.masteryGoal;
				data.mastered = true;
				player.sendSystemMessage(Component.literal("Your " + relic.displayName + " relic is now mastered.").withStyle(relic.format));
			}
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Mastered relics for " + targets.size() + " player(s) (only Inferno and Weather have mastery)"), true);
		return targets.size();
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");

		for (ServerPlayer player : targets) {
			RelicData data = NeonRelics.data(player);
			data.cooldownUntil = 0L;
			data.passiveUntil = 0L;
			data.strain = 0.0F;
			data.activeUntil = 0L;
			data.maniacUntil = data.hasChrome() ? NeonRelics.now(player.level().getServer()) + Tuning.CHROME_TIMER : 0L;
			player.removeEffect(RelicEffects.CYBER_MANIAC);
			PlayerState.of(player.getUUID()).clearCombat();
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Reset relic timers for " + targets.size() + " player(s)"), true);
		return targets.size();
	}
}
