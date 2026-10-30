package com.aristomenis.rareity.client.animation;

import com.aristomenis.rareity.client.scatha.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.Random;


public final class ScathaAnimation implements DropAnimation {
    private static final double INTRO_SECONDS = 1.15;
    private static final double SPIN_SECONDS = 3.8;
    private static final double REVEAL_SECONDS = 3.2;
    private static final int PARTICLES_RARE = 90;
    private static final int PARTICLES_EPIC = 140;
    private static final int PARTICLES_LEGENDARY = 200;
    private static final double PARTICLE_SPEED = 1.0;
    private static final int CARD_SPACING = 42;
    private static final double REVEAL_AT = INTRO_SECONDS + SPIN_SECONDS;
    private static final Identifier IRON = Identifier.withDefaultNamespace("textures/item/iron_ingot.png");
    private static final Identifier GOLD = Identifier.withDefaultNamespace("textures/item/gold_ingot.png");
    private static final Identifier DIAMOND = Identifier.withDefaultNamespace("textures/item/diamond.png");

    private ScathaDrop result;
    private final ScathaReel reel;
    private final long particleSeed;
    private long started;
    private double revealAt = REVEAL_AT;

    public ScathaAnimation() { this(ScathaDrop.randomOrdinary(new Random())); }
    public ScathaAnimation(ScathaDrop result) {
        this.result = java.util.Objects.requireNonNull(result);
        Random random = new Random();
        reel = new ScathaReel(result, random);
        particleSeed = random.nextLong();
    }
    @Override public void start() {
        ScathaAssets.warmHeads();
        revealAt = REVEAL_AT;
        started = System.nanoTime();
    }
    private double elapsed() { return (System.nanoTime() - started) / 1_000_000_000.0; }
    @Override public boolean isFinished() { return started != 0 && elapsed() >= revealAt + REVEAL_SECONDS; }

    public boolean upgradePet(ScathaDrop rarity) {
        if (rarity == null || !rarity.pet()) return false;
        if (started != 0 && isFinished()) return false;
        result = rarity;
        reel.replaceWinner(rarity);
        if (started != 0 && elapsed() >= revealAt) revealAt = elapsed();
        return true;
    }

    @Override public void render(GuiGraphicsExtractor graphics) {
        double time = elapsed();
        if (time >= revealAt + REVEAL_SECONDS) return;
        int x = graphics.guiWidth() / 2;
        int y = Math.max(78, graphics.guiHeight() - 115);
        if (time < INTRO_SECONDS) drawOpening(graphics, x, y, time);
        if (time >= 0.85) {
            double entrance = smooth((time - 0.85) / 0.3);
            double progress = Math.clamp((time - INTRO_SECONDS) / SPIN_SECONDS, 0, 1);
            int reelY = y + (int) Math.round(16 * (1 - entrance));
            drawReel(graphics, x, reelY, progress);
            if (time < revealAt) text(graphics, "SCATHA", x, reelY - 43, 0xFFFFCC55);
        }
        if (time >= revealAt) {
            double age = time - revealAt;
            int alpha = (int) Math.round(255 * (1 - smooth((time - (revealAt + REVEAL_SECONDS - 0.3)) / 0.3)));
            if (result.pet()) {
                drawCelebration(graphics, x, y, age);
                int rgb = result == ScathaDrop.RARE ? 0xEEEEEE
                        : result == ScathaDrop.EPIC ? 0xFFCC33 : 0x55FFFF;
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(x, y - 59);
                    float pulse = (float) (1.35 + 0.14 * Math.exp(-age * 2) * Math.sin(age * 13));
                    graphics.pose().scale(pulse, pulse);
                    text(graphics, result.celebration, 0, 0, (alpha << 24) | rgb);
                } finally { graphics.pose().popMatrix(); }
            } else {
                text(graphics, "SCATHA", x, y - 43, (alpha << 24) | 0xFFCC55);
            }
            text(graphics, result.label(), x, y + 29, (alpha << 24) | result.color);
        }
    }

    private static void drawOpening(GuiGraphicsExtractor graphics, int x, int y, double time) {
        double grow = smooth(time / 0.35);
        if (time < 0.55) {
            float scale = (float) (3.5 * grow);
            int shake = time > 0.35 ? (int) Math.round(Math.sin(time * 80) * 2) : 0;
            ScathaAssets.drawWorm(graphics, x + shake, y, scale);
            return;
        }
        double burst = smooth((time - 0.55) / 0.55);
        int gap = (int) Math.round(70 * burst);
        float headY = y - (float) (18 * burst);
        float scale = (float) (3.5 * (1 - 0.6 * burst));
        graphics.enableScissor(x - 130, y - 90, x - gap, y + 60);
        try { ScathaAssets.drawWorm(graphics, x - gap, headY, scale); }
        finally { graphics.disableScissor(); }
        graphics.enableScissor(x + gap, y - 90, x + 130, y + 60);
        try { ScathaAssets.drawWorm(graphics, x + gap, headY, scale); }
        finally { graphics.disableScissor(); }
        int alpha = (int) Math.round(210 * (1 - burst));
        for (int i = 0; i < 28; i++) {
            double angle = i * 2.3999632297;
            double distance = burst * (20 + i % 6 * 7);
            int px = x + (int) Math.round(Math.cos(angle) * distance);
            int py = y + (int) Math.round(Math.sin(angle) * distance - burst * 14);
            int size = 2 + i % 3;
            graphics.fill(px - size, py - size, px + size, py + size,
                    (alpha << 24) | (i % 2 == 0 ? 0xAAA299 : 0xD8D2CA));
        }
    }

    private void drawReel(GuiGraphicsExtractor graphics, int x, int y, double progress) {
        int width = Math.max(42, Math.min(264, graphics.guiWidth() - 24));
        int left = x - width / 2, right = x + width / 2;
        graphics.fill(left - 3, y - 24, right + 3, y + 24, 0xFFB88A32);
        graphics.fill(left - 1, y - 22, right + 1, y + 22, 0xFF101522);
        graphics.enableScissor(left, y - 20, right, y + 20);
        try {
            double position = reel.position(progress);
            int middle = (int) Math.floor(position);
            int reach = width / CARD_SPACING / 2 + 2;
            for (int index = Math.max(0, middle - reach); index <= Math.min(reel.size() - 1, middle + reach); index++) {
                float cardX = x + (float) ((index - position) * CARD_SPACING);
                ScathaDrop drop = reel.card(index);
                int cx = Math.round(cardX);
                graphics.fill(cx - 19, y - 18, cx + 19, y + 18, 0xFF20283B);
                graphics.fill(cx - 18, y + 15, cx + 18, y + 18, 0xFF000000 | drop.color);
                ScathaAssets.draw(graphics, drop, cardX, y - 1, 1.65F);
            }
        } finally { graphics.disableScissor(); }
        for (int row = 0; row < 7; row++) {
            int half = 6 - row;
            graphics.fill(x - half, y - 32 + row, x + half + 1, y - 31 + row, 0xFFFFDD66);
        }
        graphics.fill(x - 20, y + 21, x + 21, y + 24, 0xFFFFDD66);
        graphics.fill(x - 21, y - 20, x - 19, y + 21, 0x99FFDD66);
        graphics.fill(x + 19, y - 20, x + 21, y + 21, 0x99FFDD66);
    }

    private void drawCelebration(GuiGraphicsExtractor graphics, int x, int y, double age) {
        Identifier texture = result == ScathaDrop.RARE ? IRON : result == ScathaDrop.EPIC ? GOLD : DIAMOND;
        int count = result == ScathaDrop.RARE ? PARTICLES_RARE
                : result == ScathaDrop.EPIC ? PARTICLES_EPIC : PARTICLES_LEGENDARY;
        Random random = new Random(particleSeed);
        for (int i = 0; i < count; i++) {
            double birth = random.nextDouble() * 0.65;
            double startX = x + (random.nextDouble() - 0.5) * 45;
            double startY = y - 6 - random.nextDouble() * 20;
            double vx = (random.nextDouble() - 0.5) * graphics.guiWidth() * 0.95 * PARTICLE_SPEED;
            double vy = -graphics.guiHeight() * (0.25 + random.nextDouble() * 0.65) * PARTICLE_SPEED;
            float scale = (float) (0.65 + random.nextDouble() * 0.65);
            double rotation = (random.nextDouble() - 0.5) * 8;
            double t = age - birth;
            if (t < 0) continue;
            float px = (float) (startX + vx * t);
            float py = (float) (startY + vy * t + 0.5 * graphics.guiHeight() * 0.95 * t * t);
            if (px < -24 || px > graphics.guiWidth() + 24 || py < -24 || py > graphics.guiHeight() + 24) continue;
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(px, py);
                graphics.pose().rotate((float) (rotation * t));
                graphics.pose().scale(scale, scale);
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -8, -8, 0F, 0F, 16, 16, 16, 16);
            } finally { graphics.pose().popMatrix(); }
        }
    }
    private static double smooth(double value) {
        double p = Math.clamp(value, 0, 1);
        return p * p * (3 - 2 * p);
    }
    private static void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        if ((color >>> 24) < 4) return;
        var font = Minecraft.getInstance().font;
        graphics.text(font, text, x - font.width(text) / 2, y, color, true);
    }
}