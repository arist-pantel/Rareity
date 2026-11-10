package com.aristomenis.rareity.client.revenant;

import com.aristomenis.rareity.client.animation.AnimationManager;
import com.aristomenis.rareity.client.animation.RevenantAnimation;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class RevenantCommands {
    private RevenantCommands() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> tests(AnimationManager manager) {
        var root = ClientCommands.literal("revenant")
                .executes(context -> preview(manager, RevenantDrop.HEART, context.getSource()));
        for (RevenantDrop drop : RevenantDrop.values()) {
            root.then(ClientCommands.literal(drop.command)
                    .executes(context -> preview(manager, drop, context.getSource())));
        }
        return root;
    }

    private static int preview(AnimationManager manager, RevenantDrop drop,
                               FabricClientCommandSource source) {
        if (source.getClient().player == null || source.getClient().level == null) {
            source.sendError(Component.literal("Join a world first."));
            return 0;
        }
        var missing = RevenantClip.missingPages();
        if (!missing.isEmpty()) {
            source.sendError(Component.literal("Missing Revenant sheets: "
                    + String.join(", ", missing) + ". Check src/main/resources/assets/rareity/"));
            return 0;
        }
        RevenantClip.preload();
        manager.play(new RevenantAnimation(drop));
        return 1;
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> tools() {
        return ClientCommands.literal("revenant")
                .then(ClientCommands.literal("status").executes(context -> {
                    context.getSource().sendFeedback(Component.literal(RevenantClip.status()
                            + "; preview commands only; live drops not registered yet."));
                    return 1;
                }))
                .then(ClientCommands.literal("textures").executes(context -> {
                    var source = context.getSource();
                    if (source.getClient().player == null || source.getClient().level == null) {
                        source.sendError(Component.literal("Join a world first."));
                        return 0;
                    }
                    for (RevenantDrop drop : RevenantDrop.values()) {
                        RevenantRewards.warm(drop);
                        source.sendFeedback(Component.literal(drop.displayName + ": "
                                + RevenantRewards.status(drop)));
                    }
                    return 1;
                }));
    }
}