package com.aristomenis.rareity.client.command;

import com.aristomenis.rareity.client.animation.AnimationManager;
import com.aristomenis.rareity.client.animation.FishingFestivalAnimation;
import com.aristomenis.rareity.client.texture.RodTextures;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import net.minecraft.network.chat.Component;

public final class RareityCommands {

    private RareityCommands() {
    }

    public static void register(AnimationManager manager) {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, context) ->
                        dispatcher.register(
                                ClientCommands.literal("rareity")
                                        .then(
                                                ClientCommands.literal("test")
                                                        .executes(command -> {
                                                            manager.play(
                                                                    new FishingFestivalAnimation()
                                                            );
                                                            return 1;
                                                        })
                                        )
                                        .then(
                                                ClientCommands.literal("textures")
                                                        .executes(command -> {
                                                            command.getSource().sendFeedback(
                                                                    Component.literal(
                                                                            RodTextures.status()
                                                                    )
                                                            );
                                                            return 1;
                                                        })
                                        )
                        )
        );
    }
}