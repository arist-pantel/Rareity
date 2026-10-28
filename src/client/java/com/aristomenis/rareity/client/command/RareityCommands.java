package com.aristomenis.rareity.client.command;

import com.aristomenis.rareity.client.animation.AnimationManager;
import com.aristomenis.rareity.client.animation.FishingFestivalAnimation;
import com.aristomenis.rareity.client.detection.FishingDropDetector;
import com.aristomenis.rareity.client.detection.FishingDropMessages;
import com.aristomenis.rareity.client.reward.*;
import com.aristomenis.rareity.client.texture.RodTextures;
import com.aristomenis.rareity.client.animation.CorpseAnimation;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import net.minecraft.network.chat.Component;

public final class RareityCommands {

    private RareityCommands() {
    }

    public static void register(AnimationManager manager) {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, context) -> {
                    var test = ClientCommands.literal("test")
                            .executes(command -> {
                                manager.play(
                                        new FishingFestivalAnimation(
                                                FishingDrop.TOOTH
                                        )
                                );

                                return 1;
                            });

                    for (FishingDrop drop : FishingDrop.values()) {
                        test.then(
                                ClientCommands.literal(drop.command)
                                        .executes(command -> {
                                            manager.play(
                                                    new FishingFestivalAnimation(
                                                            drop
                                                    )
                                            );

                                            return 1;
                                        })
                        );
                    }
                    test.then(
                            ClientCommands.literal("corpse")
                                    .executes(command -> {
                                        manager.play(new CorpseAnimation());
                                        return 1;
                                    })
                    );
                    var replay = ClientCommands.literal("replay");

                    for (FishingDrop drop : FishingDrop.values()) {
                        replay.then(
                                ClientCommands.literal(drop.command)
                                        .executes(command -> {
                                            FishingDropDetector.replay(drop);

                                            command.getSource().sendFeedback(
                                                    Component.literal(
                                                            "Local detector replay queued; "
                                                                    + "no server message was sent."
                                                    )
                                            );

                                            return 1;
                                        })
                        );
                    }

                    var drops = ClientCommands.literal("drops")
                            .then(
                                    ClientCommands.literal("status")
                                            .executes(command -> {
                                                command.getSource().sendFeedback(
                                                        Component.literal(
                                                                FishingDropDetector.status()
                                                        )
                                                );

                                                return 1;
                                            })
                            )
                            .then(
                                    ClientCommands.literal("selftest")
                                            .executes(command -> {
                                                command.getSource().sendFeedback(
                                                        Component.literal(
                                                                FishingDropMessages.selfTest()
                                                        )
                                                );

                                                return 1;
                                            })
                            )
                            .then(replay);

                    dispatcher.register(
                            ClientCommands.literal("rareity")
                                    .then(test)
                                    .then(drops)
                                    .then(
                                            ClientCommands.literal("textures")
                                                    .executes(command -> {
                                                        command.getSource().sendFeedback(
                                                                Component.literal(
                                                                        RodTextures.status()
                                                                                + "; "
                                                                                + FishingRewardAssets.status()
                                                                )
                                                        );

                                                        return 1;
                                                    })
                                    )
                                    .then(
                                            ClientCommands.literal("prices")
                                                    .executes(command -> {
                                                        FishingPrices.refreshIfNeeded();

                                                        command.getSource().sendFeedback(
                                                                Component.literal(
                                                                        FishingPrices.status()
                                                                )
                                                        );

                                                        for (FishingDrop drop :
                                                                FishingDrop.values()) {
                                                            command.getSource().sendFeedback(
                                                                    Component.literal(
                                                                            drop.displayName
                                                                                    + ": "
                                                                                    + FishingPrices.line(drop)
                                                                    )
                                                            );
                                                        }

                                                        return 1;
                                                    })
                                    )
                    );
                }
        );
    }
}