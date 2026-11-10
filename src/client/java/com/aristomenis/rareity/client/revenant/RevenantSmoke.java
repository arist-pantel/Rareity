package com.aristomenis.rareity.client.revenant;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class RevenantSmoke {
    private static final Identifier[] SPRITES = new Identifier[8];
    static {
        for (int i = 0; i < SPRITES.length; i++) SPRITES[i] = Identifier.withDefaultNamespace(
                "textures/particle/generic_" + (7 - i) + ".png");
    }
    private RevenantSmoke() {}

    public static double coverage(double age, double duration) {
        if (age < 0 || age >= duration) return 0;
        return smooth(age / 0.4)
                * (1 - smooth((age - 0.8) / Math.max(0.1, duration - 0.8)));
    }

    public static void render(GuiGraphicsExtractor graphics, double age,
                              double duration, int count, int originX, int originY) {
        double opacity = coverage(age, duration);
        if (opacity <= 0) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        double spread = smooth(age / 0.55);
        double clearing = smooth((age - 0.8) / Math.max(0.1, duration - 0.8));
        double baseSize = Math.max(44, Math.min(w, h) * 0.30);
        for (int i = 0; i < count; i++) {
            double a = random(i, 1), b = random(i, 2), c = random(i, 3);
            double targetX = a * (w + baseSize) - baseSize / 2;
            double targetY = b * (h + baseSize) - baseSize / 2;
            double x = originX + (targetX - originX) * spread + Math.sin(age * 0.9 + c * 12) * 9;
            double y = originY + (targetY - originY) * spread - age * (5 + c * 10);
            int size = Math.max(1, (int) (baseSize * (0.8 + c * 0.8) * (0.3 + 0.7 * spread)));
            int alpha = (int) (opacity * (150 + a * 100));
            int color = (alpha << 24) | ((int) (56 + a * 32) << 16)
                    | ((int) (145 + b * 55) << 8) | (int) (40 + c * 30);
            int sprite = Math.min(7, (int) (clearing * 6 + c));
            graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES[sprite],
                    (int) x - size / 2, (int) y - size / 2,
                    0F, 0F, size, size, 8, 8, 8, 8, color);
        }
    }

    private static double random(int index, int salt) {
        long value = (index + 1L) * 0x9E3779B9L + salt * 0x85EBCA6BL;
        value ^= value >>> 16;
        value *= 0x45d9f3bL;
        value ^= value >>> 16;
        return (value & 0xFFFFFF) / 16777216.0;
    }

    private static double smooth(double value) {
        double p = Math.max(0, Math.min(1, value));
        return p * p * (3 - 2 * p);
    }
}