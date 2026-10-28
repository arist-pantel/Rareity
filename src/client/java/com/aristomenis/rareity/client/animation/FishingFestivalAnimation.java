package com.aristomenis.rareity.client.animation;

import com.aristomenis.rareity.client.item.RodCache;
import com.aristomenis.rareity.client.reward.FishingDrop;
import com.aristomenis.rareity.client.reward.FishingPrices;
import com.aristomenis.rareity.client.reward.FishingRewardAssets;
import com.aristomenis.rareity.client.texture.RodTextures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class FishingFestivalAnimation implements DropAnimation {

    private static final double DURATION = 6.2;
    private static final double INTRO = 2.6;

    private static final float HANDLE_X = 1.5F;
    private static final float HANDLE_Y = 14.5F;

    private static final float TIP_X = 14.5F;
    private static final float TIP_Y = 0.5F;

    private static final float SCALE = 2F;

    private static final Identifier VANILLA_ROD =
            Identifier.withDefaultNamespace(
                    "textures/item/fishing_rod_cast.png"
            );

    private static final Identifier BOBBER =
            Identifier.withDefaultNamespace(
                    "textures/entity/fishing/fishing_hook.png"
            );

    private final FishingDrop drop;
    private final boolean preview;
    private final ItemStack rodSnapshot;

    private Identifier rodTexture;
    private long started;

    public FishingFestivalAnimation() {
        this(FishingDrop.TOOTH, true);
    }

    public FishingFestivalAnimation(FishingDrop drop) {
        this(drop, true);
    }

    public FishingFestivalAnimation(
            FishingDrop drop,
            boolean preview
    ) {
        this.drop = drop;
        this.preview = preview;

        // Capture the rod when this animation is created,
        this.rodSnapshot = RodCache.capture();
    }

    @Override
    public void start() {
        Identifier texture = RodTextures.texture(
                rodSnapshot.get(DataComponents.ITEM_MODEL)
        );

        rodTexture = texture == null
                ? VANILLA_ROD
                : texture;

        FishingPrices.refreshIfNeeded();

        started = System.nanoTime();
    }

    private double elapsed() {
        return (System.nanoTime() - started)
                / 1_000_000_000.0;
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

        int centerX = graphics.guiWidth() / 2;

        // Slide the animation upward during its entrance.
        double entrance = Math.min(time / 0.35, 1.0);

        int slideOffset = (int) Math.round(
                18 * Math.pow(1.0 - entrance, 3)
        );

        int topY = graphics.guiHeight() - 115 + slideOffset;

        if (time < INTRO) {
            renderWater(graphics, centerX, topY, time);
            renderCastAndCatch(graphics, centerX, topY, time);

        } else {
            renderReward(graphics, centerX, topY, time);
        }
    }

    private void renderReward(
            GuiGraphicsExtractor graphics,
            int centerX,
            int topY,
            double time
    ) {
        double enter = smooth(
                time,
                INTRO,
                INTRO + 0.25
        );

        double exit = smooth(
                time,
                DURATION - 0.35,
                DURATION
        );

        float iconScale = (float) (2.0 * (1.0 - exit));

        FishingRewardAssets.render(
                graphics,
                drop,
                centerX,
                topY - 18,
                iconScale
        );

        int alpha = (int) Math.round(
                255 * enter * (1.0 - exit)
        );

        int textY = topY + (int) Math.round(
                6 * (1.0 - enter)
        );

        drawCenteredText(
                graphics,
                preview
                        ? "RARE DROP! [TEST]"
                        : "RARE DROP!",
                centerX,
                textY - 49,
                0xFFAA00,
                alpha
        );

        drawCenteredText(
                graphics,
                drop.displayName,
                centerX,
                textY + 6,
                drop.color & 0xFFFFFF,
                alpha
        );

        drawCenteredText(
                graphics,
                FishingPrices.line(drop),
                centerX,
                textY + 19,
                0x55FFFF,
                alpha
        );
    }

    private static void drawCenteredText(
            GuiGraphicsExtractor graphics,
            String text,
            int centerX,
            int y,
            int rgb,
            int alpha
    ) {
        if (alpha < 4) {
            return;
        }

        var font = Minecraft.getInstance().font;

        int x = centerX - font.width(text) / 2;
        int color = (alpha << 24) | rgb;

        graphics.text(
                font,
                text,
                x,
                y,
                color,
                true
        );
    }

    private static void renderWater(
            GuiGraphicsExtractor graphics,
            int centerX,
            int topY,
            double time
    ) {
        int surfaceY = topY + 30;

        graphics.fill(
                centerX - 70,
                surfaceY,
                centerX + 70,
                surfaceY + 10,
                0xCC145A85
        );

        // Moving surface segments.
        for (int i = 0; i < 14; i++) {
            int x = centerX - 70 + i * 10;

            int waveOffset = (int) Math.round(
                    Math.sin(time * 6 + i * 0.8) * 2
            );

            graphics.fill(
                    x,
                    surfaceY + waveOffset,
                    x + 10,
                    surfaceY + waveOffset + 2,
                    0xFF55CCEE
            );
        }

        if (time < 1.4) {
            int finX = centerX - 58
                    + (int) Math.round(time / 1.4 * 108);

            for (int row = 0; row < 12; row++) {
                int width = 2 + row;

                graphics.fill(
                        finX - width / 2,
                        surfaceY - 13 + row,
                        finX + width / 2 + 1,
                        surfaceY - 12 + row,
                        0xFF657786
                );
            }
        }
    }

    private void renderCastAndCatch(
            GuiGraphicsExtractor graphics,
            int centerX,
            int topY,
            double time
    ) {
        double cast = smooth(time, 0.2, 0.8);
        double reel = smooth(time, 1.7, INTRO);

        float handleX = centerX + 58;
        float handleY = topY + 10;

        float angle = (float) Math.toRadians(
                250 - 40 * cast + 40 * reel
        );


        double localTipX = (TIP_X - HANDLE_X) * SCALE;
        double localTipY = (TIP_Y - HANDLE_Y) * SCALE;

        double tipX = handleX
                + localTipX * Math.cos(angle)
                - localTipY * Math.sin(angle);

        double tipY = handleY
                + localTipX * Math.sin(angle)
                + localTipY * Math.cos(angle);

        int surfaceY = topY + 30;
        double landingX = centerX - 8;

        if (time >= 0.2) {
            double bobberX = tipX
                    + (landingX - tipX) * cast;

            double bobberY = tipY
                    + (surfaceY - tipY) * cast
                    - Math.sin(cast * Math.PI) * 12;

            if (time < 1.7) {
                if (time >= 0.8) {
                    bobberY += Math.sin(time * 12) * 1.5;
                }

                drawLine(
                        graphics,
                        tipX,
                        tipY,
                        bobberX,
                        bobberY
                );

                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        BOBBER,
                        (int) bobberX - 4,
                        (int) bobberY - 4,
                        0F,
                        0F,
                        8,
                        8,
                        8,
                        8
                );

            } else {
                // Replace the bobber with the caught reward
                double itemX = landingX
                        + (centerX - landingX) * reel;

                double itemY = surfaceY
                        + (topY - 18 - surfaceY) * reel
                        - Math.sin(reel * Math.PI) * 8;

                float itemScale = (float) (
                        0.5 + 1.5 * reel
                );

                drawLine(
                        graphics,
                        tipX,
                        tipY,
                        itemX,
                        itemY - itemScale * 6
                );

                FishingRewardAssets.render(
                        graphics,
                        drop,
                        (float) itemX,
                        (float) itemY,
                        itemScale
                );
            }

            boolean landingSplash =
                    time >= 0.8 && time < 1.05;

            boolean catchSplash =
                    time >= 1.7 && time < 1.95;

            if (landingSplash || catchSplash) {
                double splashStart = landingSplash
                        ? 0.8
                        : 1.7;

                int spread = 3 + (int) (
                        (time - splashStart) * 40
                );

                int splashY = surfaceY - 4
                        - (int) (
                        (time - splashStart) * 20
                );

                graphics.fill(
                        (int) landingX - spread,
                        splashY,
                        (int) landingX - spread + 2,
                        splashY + 2,
                        0xFFAAEEFF
                );

                graphics.fill(
                        (int) landingX + spread,
                        splashY,
                        (int) landingX + spread + 2,
                        splashY + 2,
                        0xFFAAEEFF
                );
            }
        }

        // Draw the clean rod texture around its handle.
        graphics.pose().pushMatrix();

        try {
            graphics.pose().translate(handleX, handleY);
            graphics.pose().rotate(angle);
            graphics.pose().scale(SCALE, SCALE);
            graphics.pose().translate(
                    -HANDLE_X,
                    -HANDLE_Y
            );

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    rodTexture,
                    0,
                    0,
                    0F,
                    0F,
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
        double progress = Math.clamp(
                (time - start) / (end - start),
                0.0,
                1.0
        );

        return progress * progress
                * (3.0 - 2.0 * progress);
    }

    private static void drawLine(
            GuiGraphicsExtractor graphics,
            double startX,
            double startY,
            double endX,
            double endY
    ) {
        int steps = Math.max(
                1,
                (int) Math.ceil(
                        Math.max(
                                Math.abs(endX - startX),
                                Math.abs(endY - startY)
                        )
                )
        );

        for (int i = 0; i <= steps; i++) {
            double progress = (double) i / steps;

            int x = (int) Math.round(
                    startX + (endX - startX) * progress
            );

            int y = (int) Math.round(
                    startY + (endY - startY) * progress
            );

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