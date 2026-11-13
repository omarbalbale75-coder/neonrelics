package com.neonrelics.item;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import com.neonrelics.NeonRelics;

public final class ModItems {
	private ModItems() { }

	/** Right-click to reroll your relic. Crafted from totems, netherite and a beacon. */
	public static final Item RELIC_REROLL = register("relic_reroll", RelicRerollItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

	/** Chrome Body's sidearm. Only works for Chrome Body players; always comes back. */
	public static final Item CHROME_SHOTGUN = register("chrome_shotgun", ChromeShotgunItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NeonRelics.id(name));
		Item item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(tab -> tab.accept(RELIC_REROLL));
	}
}
