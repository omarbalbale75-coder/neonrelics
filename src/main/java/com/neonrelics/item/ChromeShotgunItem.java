package com.neonrelics.item;

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

import com.neonrelics.RelicAbilities;
import com.neonrelics.Tuning;

/**
 * Chrome Body's heavy shotgun: right-click to blast a cone in front of you for true damage.
 */
public class ChromeShotgunItem extends Item {
	public ChromeShotgunItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (player instanceof ServerPlayer serverPlayer) {
			RelicAbilities.fireShotgun(serverPlayer);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.literal("Right-click: " + (int) Tuning.SHOTGUN_TRUE_DAMAGE + " true damage to everything in the blast").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.literal("Only answers to a Chrome Body.").withStyle(ChatFormatting.LIGHT_PURPLE));
	}
}
