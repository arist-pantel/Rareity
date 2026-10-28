package com.aristomenis.rareity.client;

import com.aristomenis.rareity.client.animation.AnimationManager;
import com.aristomenis.rareity.client.corpse.*;
import com.aristomenis.rareity.client.command.RareityCommands;
import com.aristomenis.rareity.client.item.RodCache;
import com.aristomenis.rareity.client.reward.FishingPrices;
import com.aristomenis.rareity.client.reward.FishingRewardAssets;
import com.aristomenis.rareity.client.texture.RodTextures;
import com.aristomenis.rareity.client.detection.FishingDropDetector;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.resources.Identifier;

public class RareityClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		RodTextures.initialize();
		FishingPrices.initialize();
		CorpsePrices.initialize();
		RodCache.register();

		AnimationManager manager = new AnimationManager();

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(
						"rareity",
						"drop_overlay"
				),
				manager::render
		);

		RareityCommands.register(manager);
		FishingDropDetector.register(manager);
		CorpseDropDetector.register(manager);

		ClientLifecycleEvents.CLIENT_STOPPING.register(
				client -> {
					RodTextures.close();
					FishingRewardAssets.close();
					FishingPrices.close();
					CorpsePrices.close();
					CorpseTextures.close();
				}
		);
	}
}