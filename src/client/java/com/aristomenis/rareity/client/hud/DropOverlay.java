package com.aristomenis.rareity.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class DropOverlay {

    private DropOverlay() {
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            int centerX,
            int y,
            String title,
            String item,
            String price
    ) {
        line(graphics, title, centerX, y, 0xFFFFAA00);
        line(graphics, item, centerX, y + 12, 0xFFFF55FF);
        line(graphics, price, centerX, y + 24, 0xFF55FFFF);
    }

    private static void line(
            GuiGraphicsExtractor graphics,
            String text,
            int x,
            int y,
            int color
    ) {
        var font = Minecraft.getInstance().font;

        graphics.text(
                font,
                text,
                x - font.width(text) / 2,
                y,
                color,
                true
        );
    }
}