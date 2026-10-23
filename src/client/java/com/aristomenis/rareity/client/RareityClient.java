package com.aristomenis.rareity.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

public class RareityClient implements ClientModInitializer {
	private static boolean previewActive = false;
	private static long previewStartedNanos;

	private static final double PREVIEW_DURATION_SECONDS = 4.0;
	private static final double SLIDE_DURATION_SECONDS = 0.35;

	@Override
	public void onInitializeClient() {
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("rareity", "drop_overlay"),
				RareityClient::renderDropOverlay
		);

		// Typing /rareity test starts or restarts the preview.
		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, commandBuildContext) -> {
					dispatcher.register(
							ClientCommands.literal("rareity")
									.then(ClientCommands.literal("test")
											.executes(context -> {
												previewStartedNanos = System.nanoTime();
												previewActive = true;
												return 1;
											}))
					);
				}
		);
	}

	private static void renderDropOverlay(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker
	) {
		Minecraft client = Minecraft.getInstance();

		// Only display the overlay while a world is loaded.
		// Leaving the world cancels the preview.
		if (client.player == null || client.level == null) {
			previewActive = false;
			return;
		}

		if (!previewActive) {
			return;
		}

// Measure elapsed seconds with a monotonic clock.
		double elapsedSeconds =
				(System.nanoTime() - previewStartedNanos) / 1_000_000_000.0;

// Stop drawing after four seconds.
		if (elapsedSeconds >= PREVIEW_DURATION_SECONDS) {
			previewActive = false;
			return;
		}

// Progress increases from 0 to 1 during the entrance.
		double progress = Math.min(
				elapsedSeconds / SLIDE_DURATION_SECONDS,
				1.0
		);

// Ease out: move quickly initially, then slow down.
		double easedProgress = 1.0 - Math.pow(1.0 - progress, 3);

		int centerX = graphics.guiWidth() / 2;

// Start 18 GUI pixels below the final position and slide upward.
		int slideOffset = (int) Math.round(18 * (1.0 - easedProgress));
		int topY = graphics.guiHeight() - 100 + slideOffset;

		// Play the fishing introduction before showing the reward.
		if (elapsedSeconds < 1.5) {
			renderFishingIntro(graphics, centerX, topY, elapsedSeconds);
			return;
		}

		// Placeholder content: no actual drop or live price yet.
		String title = "RARE DROP!";
		String itemName = "Great White Shark Tooth";
		String price = "Lbin: 5.55M coins";

		drawCenteredText(
				graphics, client, title,
				centerX, topY, 0xFFFFAA00
		);

		drawCenteredText(
				graphics, client, itemName,
				centerX, topY + 12, 0xFFFF55FF
		);

		drawCenteredText(
				graphics, client, price,
				centerX, topY + 24, 0xFF55FFFF
		);
	}

	private static void drawCenteredText(
			GuiGraphicsExtractor graphics,
			Minecraft client,
			String text,
			int centerX,
			int y,
			int color
	) {
		// Subtract half the text width to centre it horizontally.
		int x = centerX - client.font.width(text) / 2;

		graphics.text(client.font, text, x, y, color, true);
	}
	private static void renderFishingIntro(
			GuiGraphicsExtractor graphics,
			int centerX,
			int topY,
			double elapsedSeconds
	) {
		int waterLeft = centerX - 70;
		int waterRight = centerX + 70;
		int waterY = topY + 18;

		// Dark water beneath the surface.
		graphics.fill(
				waterLeft, waterY,
				waterRight, waterY + 10,
				0xCC145A85
		);

		// Small moving surface segments.
		for (int i = 0; i < 14; i++) {
			int segmentX = waterLeft + i * 10;

			int waveOffset = (int) Math.round(
					Math.sin(elapsedSeconds * 6.0 + i * 0.8) * 2
			);

			graphics.fill(
					segmentX, waterY + waveOffset,
					segmentX + 10, waterY + waveOffset + 2,
					0xFF55CCEE
			);
		}

		// Move the fin from left to right over 1.5 seconds.
		double progress = Math.min(elapsedSeconds / 1.5, 1.0);
		int finX = waterLeft + 12 + (int) Math.round(progress * 108);
		int finBaseY = waterY - 1;

		// Build a stepped triangular fin from horizontal strips.
		for (int row = 0; row < 12; row++) {
			int stripWidth = 2 + row;
			int stripY = finBaseY - 12 + row;

			graphics.fill(
					finX - stripWidth / 2, stripY,
					finX + stripWidth / 2 + 1, stripY + 1,
					0xFF657786
			);
		}

		// Small highlight along the fin's base.
		graphics.fill(
				finX - 5, finBaseY - 2,
				finX + 6, finBaseY,
				0xFF97AAB8
		);
	}
}