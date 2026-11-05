package com.aristomenis.rareity.client.ghost;

import com.aristomenis.rareity.client.animation.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.network.chat.Component;

public final class GhostCommands {
    private GhostCommands() {}
    public static LiteralArgumentBuilder<FabricClientCommandSource> tests(AnimationManager manager) {
        var node = ClientCommands.literal("ghost").executes(context -> {
            manager.play(new GhostAnimation(GhostDrop.PLASMA, 1)); return 1;
        });
        for (GhostDrop drop : GhostDrop.values()) {
            node.then(ClientCommands.literal(drop.command).executes(context -> {
                manager.play(new GhostAnimation(drop, 1)); return 1;
            }));
        }
        return node;
    }
    public static LiteralArgumentBuilder<FabricClientCommandSource> tools() {
        var node = ClientCommands.literal("ghosts")
                .then(ClientCommands.literal("status").executes(context -> {
                    context.getSource().sendFeedback(Component.literal(GhostDetector.status()));
                    context.getSource().sendFeedback(Component.literal(GhostAssets.status()));
                    return 1;
                }));
        var debug = ClientCommands.literal("debug");
        for (boolean enabled : new boolean[]{true, false}) {
            debug.then(ClientCommands.literal(enabled ? "on" : "off").executes(context -> {
                context.getSource().sendFeedback(Component.literal(GhostDetector.debug(enabled))); return 1;
            }));
        }
        var replay = ClientCommands.literal("replay");
        for (GhostDrop drop : GhostDrop.values()) {
            replay.then(ClientCommands.literal(drop.command).executes(context -> {
                GhostDetector.replay(drop);
                context.getSource().sendFeedback(Component.literal("Local Ghost parser replay; session totals unchanged."));
                return 1;
            }));
        }
        return node.then(debug).then(replay);
    }
}