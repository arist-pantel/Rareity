package com.aristomenis.rareity.client.revenant;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class RevenantRewards {
    private static final Map<RevenantDrop, ItemStack> ITEMS = new EnumMap<>(RevenantDrop.class);
    private static final Map<RevenantDrop, CompletableFuture<Optional<PlayerSkin>>> SKINS =
            new EnumMap<>(RevenantDrop.class);
    private RevenantRewards() {}

    private static ItemStack item(RevenantDrop drop) {
        return ITEMS.computeIfAbsent(drop, key -> {
            ItemStack stack;
            if (key.skinHash == null) {
                stack = new ItemStack(Items.DIAMOND);
            } else {
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/"
                        + key.skinHash + "\"}}}";
                String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                var properties = new PropertyMap(ImmutableMultimap.of(
                        "textures", new Property("textures", value)));
                var profile = new GameProfile(UUID.nameUUIDFromBytes(
                        key.skinHash.getBytes(StandardCharsets.UTF_8)), "RareityReward", properties);
                stack = new ItemStack(Items.PLAYER_HEAD);
                stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            }
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, key.glowing);
            return stack;
        });
    }

    public static void warm(RevenantDrop drop) {
        ItemStack stack = item(drop);
        if (drop.skinHash == null || SKINS.containsKey(drop)) return;
        var profile = stack.get(DataComponents.PROFILE);
        if (profile != null) SKINS.put(drop, Minecraft.getInstance().getSkinManager()
                .get(profile.partialProfile()));
    }

    private static boolean ready(RevenantDrop drop) {
        if (drop.skinHash == null) return true;
        var future = SKINS.get(drop);
        return future != null && future.isDone() && !future.isCancelled()
                && !future.isCompletedExceptionally()
                && future.getNow(Optional.empty()).isPresent();
    }

    public static String status(RevenantDrop drop) {
        if (drop.skinHash == null) return "SkyBlock base item: glowing diamond";
        var future = SKINS.get(drop);
        if (future == null) return "skin not requested";
        if (!future.isDone()) return "loading SkyBlock skin...";
        return ready(drop) ? "SkyBlock skin ready" : "skin unavailable; check connectivity/latest.log";
    }

    public static void render(GuiGraphicsExtractor graphics, RevenantDrop drop,
                              int x, int y, float scale) {
        if (scale <= 0.01F) return;
        warm(drop);
        if (!ready(drop)) {
            var font = Minecraft.getInstance().font;
            var future = SKINS.get(drop);
            String message = future != null && !future.isDone()
                    ? "Loading texture..." : "Texture unavailable";
            graphics.text(font, message, x - font.width(message) / 2, y - 4, 0xFFFFFF55, true);
            return;
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            graphics.item(item(drop), -8, -8);
        } finally { graphics.pose().popMatrix(); }
    }
}