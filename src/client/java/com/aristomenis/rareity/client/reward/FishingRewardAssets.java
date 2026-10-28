package com.aristomenis.rareity.client.reward;

import com.google.common.collect.ImmutableMultimap;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

public final class FishingRewardAssets {

    private static final Identifier TOOTH =
            Identifier.fromNamespaceAndPath(
                    "rareity",
                    "reward/great_white_shark_tooth"
            );

    private static final String SKIN_HASH =
            "a94ae433b301c7fb7c68cba625b0bd36b0b14190f20e34a7c8ee0d9de06d53b9";

    private static volatile byte[] toothPng;

    private static boolean uploaded;
    private static ItemStack megalodon;

    private FishingRewardAssets() {
    }

    public static void prepare(Path pack) throws IOException {
        try (var zip = new ZipFile(pack.toFile())) {
            var entry = zip.getEntry(
                    "assets/hypixel_skyblock/textures/item/"
                            + "community_center/mayor/marina/"
                            + "great_white_shark_tooth.png"
            );

            if (entry == null) {
                throw new IOException(
                        "Official shark tooth texture not found"
                );
            }

            try (var input = zip.getInputStream(entry)) {
                byte[] png = input.readNBytes(65537);

                if (png.length > 65536) {
                    throw new IOException("Oversized tooth texture");
                }

                toothPng = png;
            }
        }
    }

    private static ItemStack pet() {
        if (megalodon == null) {
            String json =
                    "{\"textures\":{\"SKIN\":{\"url\":"
                            + "\"https://textures.minecraft.net/texture/"
                            + SKIN_HASH
                            + "\"}}}";

            String value = Base64.getEncoder().encodeToString(
                    json.getBytes(StandardCharsets.UTF_8)
            );

            var properties = new PropertyMap(
                    ImmutableMultimap.of(
                            "textures",
                            new Property("textures", value)
                    )
            );

            var profile = new GameProfile(
                    UUID.nameUUIDFromBytes(
                            SKIN_HASH.getBytes(StandardCharsets.UTF_8)
                    ),
                    "Megalodon",
                    properties
            );

            megalodon = new ItemStack(Items.PLAYER_HEAD);

            megalodon.set(
                    DataComponents.PROFILE,
                    ResolvableProfile.createResolved(profile)
            );
        }

        return megalodon;
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            FishingDrop drop,
            float x,
            float y,
            float scale
    ) {
        if (!uploaded && toothPng != null) {
            try {
                var image = NativeImage.read(
                        new ByteArrayInputStream(toothPng)
                );

                Minecraft.getInstance()
                        .getTextureManager()
                        .register(
                                TOOTH,
                                new DynamicTexture(
                                        () -> "Rareity official shark tooth",
                                        image
                                )
                        );

                uploaded = true;

            } catch (IOException error) {
                toothPng = null;
            }
        }

        graphics.pose().pushMatrix();

        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);

            if (drop == FishingDrop.TOOTH && uploaded) {
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        TOOTH,
                        -8,
                        -8,
                        0F,
                        0F,
                        16,
                        16,
                        16,
                        16
                );

            } else if (drop != FishingDrop.TOOTH) {
                graphics.item(pet(), -8, -8);

            } else {
                // Fallback if the official texture is not ready.
                graphics.item(
                        new ItemStack(Items.GHAST_TEAR),
                        -8,
                        -8
                );
            }

        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static String status() {
        return toothPng == null
                ? "Reward tooth texture loading/unavailable"
                : "Reward tooth texture ready";
    }

    public static void close() {
        if (uploaded) {
            Minecraft.getInstance()
                    .getTextureManager()
                    .release(TOOTH);
        }

        uploaded = false;
        megalodon = null;
    }
}