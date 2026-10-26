package com.aristomenis.rareity.client.texture;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;

public final class RodTextures {

    private static final Logger LOG =
            LoggerFactory.getLogger("rareity");

    private static final Map<String, Identifier> UPLOADED =
            new HashMap<>();

    private static volatile Map<String, HypixelPackCache.Asset> assets =
            Map.of();

    private static volatile String status =
            "Loading official rod textures...";

    private static final ExecutorService WORKER =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(
                        task,
                        "rareity-pack-download"
                );

                thread.setDaemon(true);
                return thread;
            });

    private RodTextures() {
    }

    public static void initialize() {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        var directory = FabricLoader.getInstance()
                                .getGameDir()
                                .resolve(".rareity-cache/hypixel");

                        var result = HypixelPackCache.load(directory);

                        assets = result.rods();

                        status = "Ready: "
                                + assets.size()
                                + " official rod textures"
                                + (result.offline()
                                ? " (offline cache)"
                                : "");

                        LOG.info(
                                "{}; pack {}",
                                status,
                                result.hash()
                        );

                    } catch (Exception error) {
                        status =
                                "Official pack unavailable; using vanilla rod";

                        LOG.warn(status, error);
                    }
                },
                WORKER
        );
    }

    public static String status() {
        return status;
    }

    public static boolean recognizes(
            Identifier model,
            String skyblockId
    ) {
        String expected =
                "hypixel_skyblock:item/fishing/rod/"
                        + skyblockId.toLowerCase(Locale.ROOT);

        return model.toString().equals(expected)
                && assets.containsKey(expected);
    }

    // Called on Minecraft's client thread.
    public static Identifier texture(Identifier model) {
        if (model == null) {
            return null;
        }

        String key = model.toString();

        if (UPLOADED.containsKey(key)) {
            return UPLOADED.get(key);
        }

        var asset = assets.get(key);

        if (asset == null) {
            return null;
        }

        try {
            NativeImage image = NativeImage.read(
                    new ByteArrayInputStream(asset.png())
            );

            Identifier location = Identifier.fromNamespaceAndPath(
                    "rareity",
                    "downloaded/"
                            + model.getNamespace()
                            + "/"
                            + model.getPath()
            );

            DynamicTexture texture = new DynamicTexture(
                    () -> "Rareity official rod " + key,
                    image
            );

            Minecraft.getInstance()
                    .getTextureManager()
                    .register(location, texture);

            UPLOADED.put(key, location);
            return location;

        } catch (Exception error) {
            LOG.warn(
                    "Could not upload rod texture {}",
                    key,
                    error
            );

            UPLOADED.put(key, null);
            return null;
        }
    }

    public static void close() {
        for (Identifier id : UPLOADED.values()) {
            if (id != null) {
                Minecraft.getInstance()
                        .getTextureManager()
                        .release(id);
            }
        }

        UPLOADED.clear();
        WORKER.shutdownNow();
    }
}