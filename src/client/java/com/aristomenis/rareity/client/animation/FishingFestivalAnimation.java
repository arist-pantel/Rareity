package com.aristomenis.rareity.client.animation;

import com.aristomenis.rareity.client.hud.DropOverlay;
import com.aristomenis.rareity.client.item.RodCache;
import com.aristomenis.rareity.client.texture.RodTextures;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;

public final class FishingFestivalAnimation implements DropAnimation {

    private static final double DURATION = 5.5;
    private static final double INTRO = 2.6;

    // Coordinates inside the clean 16x16 rod texture.
    private static final float HANDLE_X = 1.5F;
    private static final float HANDLE_Y = 14.5F;

    private static final float TIP_X = 14.5F;
    private static final float TIP_Y = 0.5F;

    private static final float SCALE = 2.0F;

    private static final Identifier VANILLA_ROD =
            Identifier.withDefaultNamespace(
                    "textures/item/fishing_rod_cast.png"
            );

    private static final Identifier BOBBER =
            Identifier.withDefaultNamespace(
                    "textures/entity/fishing/fishing_hook.png"
            );

    private long started;
    private Identifier rodTexture;

    @Override
    public void start() {
        var stack = RodCache.capture();

        Identifier downloaded = RodTextures.texture(
                stack.get(DataComponents.ITEM_MODEL)
        );

        rodTexture = downloaded == null ? VANILLA_ROD : downloaded;
        started = System.nanoTime();
    }

    private double elapsed() {
        return (System.nanoTime() - started) / 1_000_000_000.0;
    }

    @Override
    public boolean isFinished() {
        return elapsed() >= DURATION;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics) {
        double time = elapsed();

        if (time >= DURATION) {
            return;
        }

        int center = graphics.guiWidth() / 2;

        int y = graphics.guiHeight() - 115
                + (int) Math.round(
                18 * Math.pow(
                        1 - Math.min(time / 0.35, 1),
                        3
                )
        );

        if (time < INTRO) {
            water(graphics, center, y, time);
            rod(graphics, center, y, time);
        } else {
            // Preview only: not a detected reward or live price.
            DropOverlay.render(
                    graphics,
                    center,
                    y,
                    "RARE DROP! [PREVIEW]",
                    "Great White Shark Tooth",
                    "Example price: 5.55M coins"
            );
        }
    }

    private void water(
            GuiGraphicsExtractor graphics,
            int center,
            int y,
            double time
    ) {
        int surface = y + 30;

        graphics.fill(
                center - 70,
                surface,
                center + 70,
                surface + 10,
                0xCC145A85
        );

        for (int i = 0; i < 14; i++) {
            int x = center - 70 + i * 10;

            int wave = (int) Math.round(
                    Math.sin(time * 6 + i * 0.8) * 2
            );

            graphics.fill(
                    x,
                    surface + wave,
                    x + 10,
                    surface + wave + 2,
                    0xFF55CCEE
            );
        }

        if (time < 1.4) {
            int x = center - 58
                    + (int) Math.round(time / 1.4 * 108);

            for (int row = 0; row < 12; row++) {
                int width = 2 + row;

                graphics.fill(
                        x - width / 2,
                        surface - 13 + row,
                        x + width / 2 + 1,
                        surface - 12 + row,
                        0xFF657786
                );
            }
        }
    }

    private void rod(
            GuiGraphicsExtractor graphics,
            int center,
            int y,
            double time
    ) {
        double cast = smooth(time, 0.2, 0.8);
        double reel = smooth(time, 1.7, 2.6);

        float handleX = center + 58;
        float handleY = y + 10;

        // The clean texture points from bottom-left to top-right.
        // Rotate it toward the water, then lift it again.
        float angle = (float) Math.toRadians(
                250 - 40 * cast + 40 * reel
        );

        double dx = (TIP_X - HANDLE_X) * SCALE;
        double dy = (TIP_Y - HANDLE_Y) * SCALE;

        double tipX = handleX
                + dx * Math.cos(angle)
                - dy * Math.sin(angle);

        double tipY = handleY
                + dx * Math.sin(angle)
                + dy * Math.cos(angle);

        int surface = y + 30;

        if (time >= 0.2) {
            double targetX = center - 8;

            double bx = tipX + (targetX - tipX) * cast;

            double by = tipY
                    + (surface - tipY) * cast
                    - Math.sin(cast * Math.PI) * 12;

            if (time >= 0.8 && time < 1.7) {
                by += Math.sin(time * 12) * 1.5;
            }

            bx += (tipX - bx) * reel;
            by += (tipY - by) * reel;

            line(graphics, tipX, tipY, bx, by);

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    BOBBER,
                    (int) Math.round(bx) - 4,
                    (int) Math.round(by) - 4,
                    0.0F,
                    0.0F,
                    8,
                    8,
                    8,
                    8
            );

            if (time >= 0.8 && time < 1.05) {
                int spread = 3 + (int) ((time - 0.8) * 40);

                graphics.fill(
                        (int) targetX - spread,
                        surface - 4,
                        (int) targetX - spread + 2,
                        surface - 2,
                        0xFFAAEEFF
                );

                graphics.fill(
                        (int) targetX + spread,
                        surface - 4,
                        (int) targetX + spread + 2,
                        surface - 2,
                        0xFFAAEEFF
                );
            }
        }

        graphics.pose().pushMatrix();

        try {
            graphics.pose().translate(handleX, handleY);
            graphics.pose().rotate(angle);
            graphics.pose().scale(SCALE, SCALE);
            graphics.pose().translate(-HANDLE_X, -HANDLE_Y);

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    rodTexture,
                    0,
                    0,
                    0.0F,
                    0.0F,
                    16,
                    16,
                    16,
                    16
            );
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static double smooth(
            double time,
            double start,
            double end
    ) {
        double p = Math.clamp(
                (time - start) / (end - start),
                0.0,
                1.0
        );

        return p * p * (3 - 2 * p);
    }

    private static void line(
            GuiGraphicsExtractor graphics,
            double x1,
            double y1,
            double x2,
            double y2
    ) {
        int steps = Math.max(
                1,
                (int) Math.ceil(
                        Math.max(
                                Math.abs(x2 - x1),
                                Math.abs(y2 - y1)
                        )
                )
        );

        for (int i = 0; i <= steps; i++) {
            double p = (double) i / steps;

            int x = (int) Math.round(x1 + (x2 - x1) * p);
            int y = (int) Math.round(y1 + (y2 - y1) * p);

            graphics.fill(
                    x,
                    y,
                    x + 1,
                    y + 1,
                    0xFFE0DDD5
            );
        }
    }
}