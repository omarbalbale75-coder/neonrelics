package com.neonrelics.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import com.neonrelics.NeonRelics;
import com.neonrelics.Relic;
import com.neonrelics.RelicEvents;

/**
 * Rerolls your relic. Every other relic has exactly the same chance; you never get the one you already have.
 */
public class RelicRerollItem extends Item {
	public RelicRerollItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		Relic current = NeonRelics.data(serverPlayer).relic();
		List<Relic> options = new ArrayList<>();

		for (Relic relic : Relic.values()) {
			if (relic != current) {
				options.add(relic);
			}
		}

		Relic next = options.get(serverPlayer.getRandom().nextInt(options.size()));
		RelicEvents.give(serverPlayer, next, false);

		if (!serverPlayer.isCreative()) {
			player.getItemInHand(hand).shrink(1);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.literal("Right-click to reroll your relic.").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.literal("Equal odds for every other relic.").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.literal("Wipes mastery and implants.").withStyle(ChatFormatting.RED));
	}
}
