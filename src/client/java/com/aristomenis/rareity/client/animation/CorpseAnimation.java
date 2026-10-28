package com.aristomenis.rareity.client.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import com.aristomenis.rareity.client.corpse.*;
import java.util.Map;

public final class CorpseAnimation implements DropAnimation {

    // Shake settings.
    // Later, the settings HUD will supply these values.
    private static final double SHAKE_DURATION_SECONDS = 1.75;
    private static final double SHAKE_SPEED = 12.0; // Cycles per second.
    private static final double SHAKE_STRENGTH = 4.0; // GUI pixels.

    // Begin shaking after the third pickaxe strike.
    private static final double SHAKE_START_TIME = 2.60;

    // Automatically explode when the shaking finishes.
    private static final double BREAK_TIME =
            SHAKE_START_TIME + SHAKE_DURATION_SECONDS;

    // Keep the reward visible after the explosion.
    private static final double DURATION =
            BREAK_TIME + 2.35;
    private static final double IMPACT_VISUAL_DELAY = 0.10;

    // Read the texture directly rather than rendering a held SkyBlock item.
    private static final Identifier PICKAXE =
            Identifier.withDefaultNamespace(
                    "textures/item/diamond_pickaxe.png"
            );

    private static final double[] IMPACT_TIMES = {
            0.75, 1.55, 2.35
    };

    private final Map<CorpseDrop, Integer> loot;
    private CorpseSelection selection;
    private long startedNanos;

    public CorpseAnimation() {
        this(Map.of(CorpseDrop.LOCKET, 1));
    }

    public CorpseAnimation(Map<CorpseDrop, Integer> loot) {
        this.loot = Map.copyOf(loot);
    }

    @Override
    public void start() {
        selection = null;
        CorpsePrices.refreshIfNeeded();
        startedNanos = System.nanoTime();
    }

    private double elapsedSeconds() {
        return (System.nanoTime() - startedNanos)
                / 1_000_000_000.0;
    }

    @Override
    public boolean isFinished() {
        return elapsedSeconds() >= DURATION;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics) {
        double time = elapsedSeconds();

        if (time >= DURATION) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;

        // The bottom of the ice sits above the hotbar.
        int entranceOffset = (int) Math.round(
                16 * (1.0 - smooth(time / 0.3))
        );

        int centerY = graphics.guiHeight() - 115
                + entranceOffset;

        if (time < BREAK_TIME) {
            double shake = 0.0;

            for (double impact : IMPACT_TIMES) {
                double age = time - (impact + IMPACT_VISUAL_DELAY);

                if (age >= 0.0 && age < 0.18) {
                    shake += Math.sin(age * 100.0)
                            * 3.0
                            * (1.0 - age / 0.18);
                }
            }

            // Build up a stronger shake immediately before the explosion.
            // Shake continuously, building in strength before the explosion.
            if (time >= SHAKE_START_TIME && time < BREAK_TIME) {
                double shakeElapsed = time - SHAKE_START_TIME;

                double progress = Math.clamp(
                        shakeElapsed / Math.max(
                                SHAKE_DURATION_SECONDS,
                                0.001
                        ),
                        0.0,
                        1.0
                );

                double strength = SHAKE_STRENGTH
                        * (0.35 + 0.65 * progress);

                double phase = shakeElapsed
                        * SHAKE_SPEED
                        * Math.PI
                        * 2.0;

                shake += Math.sin(phase) * strength;
            }
            int iceX = centerX + (int) Math.round(shake);

            drawFrozenCorpse(graphics, iceX, centerY);

            int hits = 0;

            for (double impact : IMPACT_TIMES) {
                if (time >= impact + IMPACT_VISUAL_DELAY) {
                    hits++;
                }
            }

            drawCracks(graphics, iceX, centerY, hits);
            drawPickaxe(graphics, centerX, centerY, time);
        } else {
            double reveal = smooth(
                    (time - BREAK_TIME - 0.15) / 0.45
            );

            double fade = 1.0 - smooth(
                    (time - (DURATION - 0.4)) / 0.4
            );

            // Freeze the winner at reveal time, giving the background cache
            // the whole introduction to finish loading its prices.
            if (selection == null) {
                var prices = CorpsePrices.snapshot();
                selection = CorpseSelection.choose(loot, drop -> {
                    var quote = prices.get(drop);
                    return quote == null ? Double.NaN : quote.unitPrice();
                });
                org.slf4j.LoggerFactory.getLogger("rareity").info(
                        "Corpse reveal: {}; comparison incomplete: {}",
                        selection.drop(), selection.incomplete());
            }

            if (reveal > 0.0 && fade > 0.0) {
                float scale = (float) (2.5 * reveal * fade);
                float rewardY = centerY - 10
                        + (float) (12 * (1.0 - reveal));
                CorpseTextures.render(graphics, selection.drop(), centerX, rewardY, scale);
                int alpha = (int) Math.round(255 * reveal * fade);
                if (alpha > 3) {
                    CorpseDrop drop = selection.drop();
                    String name = drop == null ? "No rare drop!"
                            : drop.displayName + (selection.quantity() > 1 ? " x" + selection.quantity() : "");
                    drawCenteredText(graphics, name, centerX, centerY + 18,
                            (alpha << 24) | (drop == null ? 0xFF5555 : drop.color));
                    drawCenteredText(graphics, drop == null ? "Better luck next corpse!" : CorpsePrices.line(drop),
                            centerX, centerY + 31, (alpha << 24) | 0x55FFFF);
                    if (selection.incomplete()) {
                        drawCenteredText(graphics, "Comparison incomplete: missing prices", centerX,
                                centerY + 44, (alpha << 24) | 0xFFAA00);
                    }
                }
            }

            // Draw smoke last so the reward emerges through it.
            drawSmoke(graphics, centerX, centerY, time);
        }
    }

    private static void drawFrozenCorpse(
            GuiGraphicsExtractor graphics,
            int x,
            int y
    ) {
        // Rear interior of the ice.
        graphics.fill(
                x - 32, y - 44,
                x + 25, y + 39,
                0x99447799
        );

        // A blocky frozen corpse silhouette.
        graphics.fill(
                x - 12, y - 32,
                x + 8, y - 13,
                0xFF344556
        );

        graphics.fill(
                x - 11, y - 12,
                x + 9, y + 14,
                0xFF293B4C
        );

        graphics.fill(
                x - 23, y - 10,
                x - 12, y + 12,
                0xFF344556
        );

        graphics.fill(
                x + 10, y - 10,
                x + 20, y + 12,
                0xFF344556
        );

        graphics.fill(
                x - 11, y + 14,
                x - 2, y + 35,
                0xFF233445
        );

        graphics.fill(
                x + 1, y + 14,
                x + 10, y + 35,
                0xFF2C4053
        );

        // Frozen blue visor.
        graphics.fill(
                x - 9, y - 22,
                x + 6, y - 19,
                0xFF8ACCE8
        );

        // Translucent front face.
        graphics.fill(
                x - 32, y - 39,
                x + 25, y + 40,
                0x507BC7EA
        );

        // Sloping top face, drawn in horizontal strips.
        for (int row = 0; row < 10; row++) {
            int inset = 10 - row;

            graphics.fill(
                    x - 32 + inset,
                    y - 49 + row,
                    x + 25 + inset,
                    y - 48 + row,
                    0x809DDCF2
            );
        }

        // Right face.
        for (int column = 0; column < 10; column++) {
            graphics.fill(
                    x + 25 + column,
                    y - 39 - column,
                    x + 26 + column,
                    y + 40 - column,
                    0x6073B6D7
            );
        }

        // Bright edges.
        drawLine(graphics, x - 32, y - 39,
                x + 25, y - 39, 0xDDE0F8FF);

        drawLine(graphics, x - 32, y - 39,
                x - 32, y + 40, 0xDDE0F8FF);

        drawLine(graphics, x - 32, y + 40,
                x + 25, y + 40, 0xDDE0F8FF);

        drawLine(graphics, x + 25, y - 39,
                x + 25, y + 40, 0xDDE0F8FF);

        drawLine(graphics, x - 32, y - 39,
                x - 22, y - 49, 0xDDE0F8FF);

        drawLine(graphics, x - 22, y - 49,
                x + 35, y - 49, 0xDDE0F8FF);

        drawLine(graphics, x + 25, y - 39,
                x + 35, y - 49, 0xDDE0F8FF);

        drawLine(graphics, x + 35, y - 49,
                x + 35, y + 30, 0xBBD4F2FF);

        drawLine(graphics, x + 25, y + 40,
                x + 35, y + 30, 0xBBD4F2FF);

        // Frost streaks.
        graphics.fill(
                x - 28, y - 34,
                x - 26, y + 29,
                0x55FFFFFF
        );

        graphics.fill(
                x + 20, y - 28,
                x + 22, y + 32,
                0x44FFFFFF
        );
    }

    private static void drawCracks(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int hits
    ) {
        if (hits == 0) {
            return;
        }

        int originX = x + 10;
        int originY = y - 5;

        // Each hit adds more branches.
        int branches = hits * 4;

        for (int i = 0; i < branches; i++) {
            double angle = i * Math.PI * 2.0 / 12.0;

            double length = 13 + (i % 3) * 7
                    + (hits - 1) * 3;

            int middleX = originX + (int) Math.round(
                    Math.cos(angle + 0.15) * length * 0.5
            );

            int middleY = originY + (int) Math.round(
                    Math.sin(angle + 0.15) * length * 0.5
            );

            int endX = Math.clamp(
                    originX + (int) Math.round(
                            Math.cos(angle) * length
                    ),
                    x - 30,
                    x + 23
            );

            int endY = Math.clamp(
                    originY + (int) Math.round(
                            Math.sin(angle) * length
                    ),
                    y - 37,
                    y + 38
            );

            drawLine(
                    graphics,
                    originX, originY,
                    middleX, middleY,
                    0xFFEBFBFF
            );

            drawLine(
                    graphics,
                    middleX, middleY,
                    endX, endY,
                    0xFFEBFBFF
            );
        }
    }

    private static void drawPickaxe(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            double time
    ) {
        float angleDegrees = -65.0F;

        for (double impact : IMPACT_TIMES) {
            double windupStart = impact - 0.4;

            if (time >= windupStart && time < impact) {
                double progress = smooth(
                        (time - windupStart) / 0.4
                );

                angleDegrees = (float) (
                        -65.0 + 85.0 * progress
                );

                break;
            }

            if (time >= impact && time < impact + 0.25) {
                double progress = smooth(
                        (time - impact) / 0.25
                );

                angleDegrees = (float) (
                        20.0 - 85.0 * progress
                );

                break;
            }
        }

        graphics.pose().pushMatrix();

        try {
            // Pivot near the lower end of the handle.
            graphics.pose().translate(x + 40, y + 21);

            graphics.pose().rotate(
                    (float) Math.toRadians(angleDegrees)
            );

            graphics.pose().scale(2.5F, 2.5F);
            graphics.pose().translate(-2.0F, -14.0F);

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    PICKAXE,
                    0, 0,
                    0.0F, 0.0F,
                    16, 16,
                    16, 16
            );
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawSmoke(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            double time
    ) {
        double age = time - BREAK_TIME;
        double lifetime = 1.45;

        if (age < 0.0 || age >= lifetime) {
            return;
        }

        double progress = age / lifetime;

        // Thick initially, then clear away to reveal the item.
        double opacity = 1.0 - smooth(
                (age - 0.18) / (lifetime - 0.18)
        );

        int alpha = (int) Math.round(225 * opacity);

        if (alpha <= 0) {
            return;
        }

        for (int i = 0; i < 90; i++) {
            // Fixed variation keeps each puff moving consistently.
            double angle = i * 2.3999632297;

            double startRadius = 5 + (i % 8) * 3;
            double travel = 20 + (i % 7) * 5;

            double distance = startRadius
                    + travel * Math.sqrt(progress);

            int puffX = x + (int) Math.round(
                    Math.cos(angle) * distance
            );

            int puffY = y + (int) Math.round(
                    Math.sin(angle) * distance * 0.8
                            - age * (12 + i % 9)
            );

            int size = 4 + (i % 5)
                    + (int) Math.round(progress * 4);

            int shade = 155 + (i % 5) * 18;

            int color = (alpha << 24)
                    | (shade << 16)
                    | (Math.min(255, shade + 5) << 8)
                    | Math.min(255, shade + 15);

            graphics.fill(
                    puffX - size,
                    puffY - size,
                    puffX + size,
                    puffY + size,
                    color
            );
        }
    }

    private static void drawCenteredText(
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

    private static double smooth(double progress) {
        double p = Math.clamp(progress, 0.0, 1.0);
        return p * p * (3.0 - 2.0 * p);
    }

    private static void drawLine(
            GuiGraphicsExtractor graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int color
    ) {
        int steps = Math.max(
                1,
                Math.max(
                        Math.abs(x2 - x1),
                        Math.abs(y2 - y1)
                )
        );

        for (int i = 0; i <= steps; i++) {
            double progress = (double) i / steps;

            int x = (int) Math.round(
                    x1 + (x2 - x1) * progress
            );

            int y = (int) Math.round(
                    y1 + (y2 - y1) * progress
            );

            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }
}