package com.aristomenis.rareity.client.animation;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface DropAnimation {

    void start();

    void render(GuiGraphicsExtractor graphics);

    boolean isFinished();
}