package com.aristomenis.rareity.client.revenant;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RevenantClip {

    public static final int FPS = 60;
    public static final int FRAME_COUNT = 468;

    public static final int FRAME_WIDTH = 256;
    public static final int FRAME_HEIGHT = 144;

    public static final int COLUMNS = 8;
    public static final int ROWS = 8;
    public static final int PADDING = 1;

    public static final double SHOT_SECONDS = 7.65;

    public static final double DURATION_SECONDS =
            (double) FRAME_COUNT / FPS;

    private static final int CELL_WIDTH =
            FRAME_WIDTH + 2 * PADDING;

    private static final int CELL_HEIGHT =
            FRAME_HEIGHT + 2 * PADDING;

    private static final int FRAMES_PER_PAGE =
            COLUMNS * ROWS;

    private static final int PAGE_COUNT =
            (FRAME_COUNT + FRAMES_PER_PAGE - 1)
                    / FRAMES_PER_PAGE;

    private static final Identifier[] PAGES =
            new Identifier[PAGE_COUNT];

    static {
        for (int i = 0; i < PAGE_COUNT; i++) {
            PAGES[i] = Identifier.fromNamespaceAndPath(
                    "rareity",
                    String.format(
                            Locale.ROOT,
                            "textures/revenant/intro/page_%02d.png",
                            i
                    )
            );
        }
    }

    private RevenantClip() {}

    public static List<String> missingPages() {
        List<String> missing = new ArrayList<>();

        var resources = Minecraft.getInstance()
                .getResourceManager();

        for (Identifier page : PAGES) {
            if (resources.getResource(page).isEmpty()) {
                missing.add(page.toString());
            }
        }

        return missing;
    }

    public static String status() {
        return (PAGE_COUNT - missingPages().size())
                + "/" + PAGE_COUNT
                + " Revenant video sheets found; "
                + "60 fps; 7.8 seconds";
    }

    public static void preload() {
        var textures = Minecraft.getInstance()
                .getTextureManager();

        for (Identifier page : PAGES) {
            textures.getTexture(page);
        }
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            double seconds,
            int x,
            int y,
            int width,
            int height
    ) {
        int frame = Math.max(
                0,
                Math.min(
                        FRAME_COUNT - 1,
                        (int) Math.floor(seconds * FPS + 1e-7)
                )
        );

        int page = frame / FRAMES_PER_PAGE;
        int cell = frame % FRAMES_PER_PAGE;

        float u = (cell % COLUMNS) * CELL_WIDTH + PADDING;
        float v = (cell / COLUMNS) * CELL_HEIGHT + PADDING;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                PAGES[page],
                x,
                y,
                u,
                v,
                width,
                height,
                FRAME_WIDTH,
                FRAME_HEIGHT,
                COLUMNS * CELL_WIDTH,
                ROWS * CELL_HEIGHT
        );
    }
}