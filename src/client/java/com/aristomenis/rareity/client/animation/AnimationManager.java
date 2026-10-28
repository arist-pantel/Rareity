package com.aristomenis.rareity.client.animation;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;
import java.util.Deque;

public final class AnimationManager {

    private DropAnimation active;

    private final Deque<DropAnimation> queue =
            new ArrayDeque<>();

    public AnimationManager() {
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> clear()
        );
    }

    public void play(DropAnimation animation) {
        queue.clear();
        active = animation;
        active.start();
    }

    public void enqueue(DropAnimation animation) {
        if (queue.size() < 32) {
            queue.addLast(animation);
        }
    }

    private void clear() {
        active = null;
        queue.clear();
    }

    public void render(
            GuiGraphicsExtractor graphics,
            DeltaTracker tracker
    ) {
        var client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            clear();
            return;
        }

        if (active != null && active.isFinished()) {
            active = null;
        }

        if (active == null && !queue.isEmpty()) {
            active = queue.removeFirst();
            active.start();
        }

        if (active != null) {
            active.render(graphics);
        }
    }
}