package com.aristomenis.rareity.client.animation;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AnimationManager {

    private DropAnimation active;

    public AnimationManager() {
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> active = null
        );
    }

    public void play(DropAnimation animation) {
        active = animation;
        active.start();
    }

    public void render(
            GuiGraphicsExtractor graphics,
            DeltaTracker tracker
    ) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null
                || client.level == null
                || (active != null && active.isFinished())) {
            active = null;
        }

        if (active != null) {
            active.render(graphics);
        }
    }
}