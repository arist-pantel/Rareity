package com.aristomenis.rareity.client.animation;

import com.aristomenis.rareity.client.revenant.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class RevenantAnimation implements DropAnimation {
    private static final int PANEL_WIDTH = 256;
    private static final int BOTTOM_GAP = 65;
    private static final double INTRO_SPEED = 1.0;
    private static final double SMOKE_SECONDS = 3.0;
    private static final double REWARD_HOLD_SECONDS = 3.0;
    private static final int SMOKE_COUNT = 180;
    private static final float SHOT_VOLUME = 0.65F;
    private static final double SHOT_AT = RevenantClip.SHOT_SECONDS / INTRO_SPEED;
    private static final double END_AT = SHOT_AT + SMOKE_SECONDS + REWARD_HOLD_SECONDS;

    private final RevenantDrop drop;
    private long startedNanos;
    private boolean shotPlayed;

    public RevenantAnimation() { this(RevenantDrop.HEART); }

    public RevenantAnimation(RevenantDrop drop) {
        this.drop = java.util.Objects.requireNonNull(drop);
    }

    @Override public void start() {
        RevenantRewards.warm(drop);
        startedNanos = System.nanoTime();
        shotPlayed = false;
    }

    private double elapsedSeconds() {
        return (System.nanoTime() - startedNanos) / 1_000_000_000.0;
    }

    @Override public boolean isFinished() { return elapsedSeconds() >= END_AT; }

    @Override public void render(GuiGraphicsExtractor graphics) {
        double elapsed = elapsedSeconds();
        if (elapsed >= END_AT) return;

        double scale = Math.min(1.0, Math.min(
                Math.max(1, graphics.guiWidth() - 16) / (double) PANEL_WIDTH,
                Math.max(1, graphics.guiHeight() - BOTTOM_GAP - 8)
                        / (PANEL_WIDTH * 9.0 / 16.0)));
        int width = Math.max(1, (int) Math.round(PANEL_WIDTH * scale));
        int height = Math.max(1, (int) Math.round(width * 9.0 / 16.0));
        int x = (graphics.guiWidth() - width) / 2;
        int y = Math.max(4, graphics.guiHeight() - BOTTOM_GAP - height);
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        double smokeAge = elapsed - SHOT_AT;
        double clipTime = elapsed * INTRO_SPEED;

        if (smokeAge < 0.55) {
            RevenantClip.render(graphics, clipTime, x, y, width, height);
        }

        if (!shotPlayed && smokeAge >= 0) {
            shotPlayed = true;
            if (smokeAge < 0.5 && SHOT_VOLUME > 0) {
                var sound = SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath("rareity", "revenant_shot"));
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(sound, 1.0F, SHOT_VOLUME));
            }
        }

        if (smokeAge >= 0.75) {
            double enter = smooth((smokeAge - 0.75) / 0.7);
            double fade = 1 - smooth((elapsed - END_AT + 0.4) / 0.4);
            int alpha = (int) (255 * enter * fade);
            float iconScale = (float) (3.0 * scale * enter * fade);
            RevenantRewards.render(graphics, drop, centerX, centerY, iconScale);
            text(graphics, "RARE DROP!", centerX, centerY - 43,
                    (alpha << 24) | 0xFFAA00);
            text(graphics, drop.displayName, centerX, centerY + 30,
                    (alpha << 24) | drop.color);
        }

        if (smokeAge >= 0) {
            RevenantSmoke.render(graphics, smokeAge, SMOKE_SECONDS, SMOKE_COUNT,
                    centerX, y + height / 3);
        }
    }

    private static void text(GuiGraphicsExtractor graphics, String text,
                             int centerX, int y, int color) {
        if ((color >>> 24) < 4) return;
        var font = Minecraft.getInstance().font;
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, true);
    }

    private static double smooth(double value) {
        double p = Math.max(0, Math.min(1, value));
        return p * p * (3 - 2 * p);
    }
}