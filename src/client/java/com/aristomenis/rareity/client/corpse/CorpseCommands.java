package com.aristomenis.rareity.client.corpse;

import com.aristomenis.rareity.client.animation.AnimationManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;
import java.util.Map;

public final class CorpseCommands {
    private CorpseCommands() {}
    public static LiteralArgumentBuilder<FabricClientCommandSource> tests(AnimationManager manager) {
        var node = ClientCommands.literal("com/aristomenis/rareity/client/corpse").executes(context -> {
            CorpseDropDetector.preview(manager, Map.of(CorpseDrop.LOCKET, 1)); return 1;
        });
        for (CorpseDrop drop : CorpseDrop.values()) {
            node.then(ClientCommands.literal(drop.command).executes(context -> {
                CorpseDropDetector.preview(manager, Map.of(drop, 1)); return 1;
            }));
        }
        node.then(ClientCommands.literal("dud").executes(context -> {
            CorpseDropDetector.preview(manager, Map.of()); return 1;
        }));
        node.then(ClientCommands.literal("multiple").executes(context -> {
            CorpseDropDetector.preview(manager, Map.of(
                    CorpseDrop.UMBER, 10, CorpseDrop.TUNGSTEN, 5, CorpseDrop.SKELETON, 1,
                    CorpseDrop.WISP, 1, CorpseDrop.LOCKET, 1, CorpseDrop.DYE, 1));
            context.getSource().sendFeedback(Component.literal("Local Vanguard replay: six qualifying items; winner uses price per item, never stack value."));
            return 1;
        }));
        return node;
    }
    public static LiteralArgumentBuilder<FabricClientCommandSource> tools() {
        return ClientCommands.literal("corpses")
                .then(ClientCommands.literal("status").executes(context -> {
                    context.getSource().sendFeedback(Component.literal(CorpseDropDetector.status()));
                    context.getSource().sendFeedback(Component.literal(CorpseTextures.status()));
                    return 1;
                }))
                .then(ClientCommands.literal("prices").executes(context -> {
                    CorpsePrices.refreshIfNeeded();
                    context.getSource().sendFeedback(Component.literal(CorpsePrices.status()));
                    for (CorpseDrop drop : CorpseDrop.values()) {
                        context.getSource().sendFeedback(Component.literal(drop.displayName + ": " + CorpsePrices.line(drop)));
                    }
                    return 1;
                }));
    }
}