package com.aristomenis.rareity.client.scatha;

import com.aristomenis.rareity.client.animation.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;

public final class ScathaCommands {
    private ScathaCommands() {}
    public static LiteralArgumentBuilder<FabricClientCommandSource> tests(AnimationManager manager) {
        var node = ClientCommands.literal("scatha").executes(context -> {
            manager.play(new ScathaAnimation()); return 1;
        });
        for (ScathaDrop drop : ScathaDrop.values()) {
            node.then(ClientCommands.literal(drop.command).executes(context -> {
                manager.play(new ScathaAnimation(drop)); return 1;
            }));
        }
        return node;
    }
    public static LiteralArgumentBuilder<FabricClientCommandSource> tools() {
        var node = ClientCommands.literal("scatha")
                .then(ClientCommands.literal("status").executes(context -> {
                    context.getSource().sendFeedback(Component.literal(ScathaDetector.status())); return 1;
                }));
        var debug = ClientCommands.literal("debug");
        for (boolean enabled : new boolean[]{true, false}) {
            debug.then(ClientCommands.literal(enabled ? "on" : "off").executes(context -> {
                context.getSource().sendFeedback(Component.literal(ScathaDetector.debug(enabled))); return 1;
            }));
        }
        node.then(debug);
        var replay = ClientCommands.literal("replay")
                .then(ClientCommands.literal("ordinary").executes(context -> {
                    ScathaDetector.replay(null);
                    context.getSource().sendFeedback(Component.literal("Local Scatha detector replay; no server interaction."));
                    return 1;
                }));
        for (ScathaDrop rarity : new ScathaDrop[]{ScathaDrop.RARE, ScathaDrop.EPIC, ScathaDrop.LEGENDARY}) {
            replay.then(ClientCommands.literal(rarity.command).executes(context -> {
                ScathaDetector.replay(rarity);
                context.getSource().sendFeedback(Component.literal("Local Scatha detector replay; no server interaction."));
                return 1;
            }));
        }
        node.then(replay);
        return node.then(ClientCommands.literal("textures").executes(context -> {
            context.getSource().sendFeedback(Component.literal(ScathaAssets.status())); return 1;
        }));
    }
}