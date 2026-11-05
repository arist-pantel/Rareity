package com.aristomenis.rareity.client.animation;

import com.aristomenis.rareity.client.ghost.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class GhostAnimation implements DropAnimation {
    private static final double APPEAR_SECONDS = 0.30;
    private static final double CHARGE_SECONDS = 0.65;
    private static final double SWING_SECONDS = 0.45;
    private static final double SPLIT_SECONDS = 0.65;
    private static final double HOLD_SECONDS = 3.30;
    private static final float MODEL_SCALE = 3.0F;
    private static final double SWING_AT = APPEAR_SECONDS + CHARGE_SECONDS;
    private static final double CUT_AT = SWING_AT + SWING_SECONDS * 0.45;
    private static final double REVEAL_AT = CUT_AT + SPLIT_SECONDS;
    private static final double DURATION = REVEAL_AT + HOLD_SECONDS;
    private static final Identifier CREEPER = Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png");
    private static final Identifier CHARGED = Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private final GhostDrop drop;
    private final int quantity;
    private long started;

    public GhostAnimation(GhostDrop drop, int quantity) {
        this.drop = java.util.Objects.requireNonNull(drop);
        if (quantity < 1) throw new IllegalArgumentException("Positive quantity required");
        this.quantity = quantity;
    }
    @Override public void start() { started = System.nanoTime(); }
    private double elapsed() { return (System.nanoTime() - started) / 1_000_000_000.0; }
    @Override public boolean isFinished() { return elapsed() >= DURATION; }

    @Override public void render(GuiGraphicsExtractor graphics) {
        double time = elapsed();
        if (time >= DURATION) return;
        int x = graphics.guiWidth() / 2;
        int y = Math.max(78, graphics.guiHeight() - 115);
        double split = smooth((time - CUT_AT) / SPLIT_SECONDS);
        if (split < 1) {
            float entrance = (float) smooth(time / APPEAR_SECONDS);
            float figureY = y + (1 - entrance) * 15;
            drawHalf(graphics, x, figureY, time, split, entrance, false);
            drawHalf(graphics, x, figureY, time, split, entrance, true);
        }
        if (time >= SWING_AT && time < SWING_AT + SWING_SECONDS) {
            drawKatana(graphics, x, y, smooth((time - SWING_AT) / SWING_SECONDS));
        }
        if (time >= CUT_AT) drawCut(graphics, x, y, time - CUT_AT);
        if (time >= CUT_AT + 0.18) {
            double enter = smooth((time - CUT_AT - 0.18) / Math.max(0.1, SPLIT_SECONDS - 0.18));
            double fade = 1 - smooth((time - DURATION + 0.35) / 0.35);
            float iconY = y - 12 + (float) (14 * (1 - enter));
            GhostAssets.render(graphics, drop, x, iconY, (float) (2.2 * enter * fade));
            int alpha = (int) Math.round(255 * enter * fade);
            if (alpha > 3) {
                text(graphics, "RARE DROP!", x, y - 53, (alpha << 24) | 0xFFAA00);
                String name = drop.displayName + (quantity > 1 ? " x" + quantity : "");
                text(graphics, name, x, y + 15, (alpha << 24) | drop.color);
            }
        }
    }

    private static void drawHalf(GuiGraphicsExtractor graphics, float x, float y, double time,
                                 double split, float entrance, boolean right) {
        int side = right ? 1 : -1;
        float dx = (float) (side * 48 * split);
        float dy = (float) ((right ? 20 : -14) * split);
        float scale = (float) (MODEL_SCALE * entrance * (1 - 0.45 * split));
        int alpha = (int) Math.round(235 * (1 - split));
        int color = (alpha << 24) | 0xB8DFFF;
        int localX = right ? 0 : -4;
        int sourceOffset = right ? 4 : 0;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x + dx, y + dy);
            graphics.pose().rotate((float) (side * split * 0.45));
            graphics.pose().scale(scale, scale);
            int glow = ((int) (55 * (1 - split)) << 24) | 0x66BBFF;
            graphics.fill(localX - 1, -16, localX + 5, -6, glow);
            graphics.fill(localX - 1, -8, localX + 5, 6, glow);
            graphics.blit(RenderPipelines.GUI_TEXTURED, CREEPER, localX, -15,
                    8F + sourceOffset, 8F, 4, 8, 64, 32, color);
            graphics.blit(RenderPipelines.GUI_TEXTURED, CREEPER, localX, -7,
                    20F + sourceOffset, 20F, 4, 12, 64, 32, color);
            float u = (float) ((time * 11) % 48) + sourceOffset;
            float v = (float) ((time * 7) % 16);
            int energy = ((int) ((75 + Math.sin(time * 15) * 20) * (1 - split)) << 24) | 0x99CCFF;
            graphics.blit(RenderPipelines.GUI_TEXTURED, CHARGED, localX, -15, u, v, 4, 8, 64, 32, energy);
            graphics.blit(RenderPipelines.GUI_TEXTURED, CHARGED, localX, -7, u, v, 4, 12, 64, 32, energy);
            int edge = right ? 5 : -5;
            int previousX = edge, previousY = -16;
            for (int i = 1; i <= 5; i++) {
                int nx = edge + (int) Math.round(Math.sin(time * 17 + i * 2.1) * 1.2);
                int ny = -16 + i * 4;
                line(graphics, previousX, previousY, nx, ny,
                        ((int) (185 * (1 - split)) << 24) | 0xBBDDFF);
                previousX = nx; previousY = ny;
            }
        } finally { graphics.pose().popMatrix(); }
    }

    private static void drawKatana(GuiGraphicsExtractor graphics, int x, int y, double progress) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) (x + 72 - 130 * progress), y + 28);
            graphics.pose().rotate(-0.35F);
            graphics.pose().scale(1.15F, 1.15F);
            for (int row = 0; row < 60; row++) {
                double curve = (60 - row) / 60.0;
                int bend = (int) Math.round(curve * curve * 3);
                int width = row == 0 ? 1 : row < 3 ? 2 : 3;
                graphics.fill(bend, -61 + row, bend + width, -60 + row, 0xFFE6F5FF);
                if (row > 3) graphics.fill(bend + width - 1, -61 + row, bend + width, -60 + row, 0xFF89ABBF);
            }
            graphics.fill(-1, -1, 3, 13, 0xFF171C2B);
            for (int row = 1; row < 12; row += 3) graphics.fill(0, row, 2, row + 1, 0xFFB8C9D9);
            graphics.fill(-5, -2, 7, 0, 0xFFDBB653);
            graphics.fill(-1, 12, 3, 14, 0xFFDBB653);
        } finally { graphics.pose().popMatrix(); }
    }

    private static void drawCut(GuiGraphicsExtractor graphics, int x, int y, double age) {
        if (age < 0.22) {
            int alpha = (int) Math.round(255 * (1 - age / 0.22));
            line(graphics, x - 5, y - 53, x + 5, y + 22, (alpha << 24) | 0xFFFFFF);
            line(graphics, x - 3, y - 53, x + 7, y + 22, (alpha << 24) | 0x88CCFF);
        }
        if (age >= 0.85) return;
        double p = age / 0.85;
        int alpha = (int) Math.round(220 * (1 - p));
        for (int i = 0; i < 36; i++) {
            double angle = i * 2.3999632297;
            double distance = (10 + i % 7 * 6) * Math.sqrt(p);
            int px = x + (int) Math.round(Math.cos(angle) * distance);
            int py = y - 12 + (int) Math.round(Math.sin(angle) * distance - p * 14 + p * p * 20);
            int size = 1 + i % 2;
            graphics.fill(px, py, px + size, py + size, (alpha << 24) | (i % 2 == 0 ? 0xBBDDFF : 0xFFFFFF));
        }
    }
    private static double smooth(double value) {
        double p = Math.clamp(value, 0, 1); return p * p * (3 - 2 * p);
    }
    private static void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, text, x - font.width(text) / 2, y, color, true);
    }
    private static void line(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        for (int i = 0; i <= steps; i++) {
            double p = (double) i / steps;
            int x = (int) Math.round(x1 + (x2 - x1) * p), y = (int) Math.round(y1 + (y2 - y1) * p);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }
}