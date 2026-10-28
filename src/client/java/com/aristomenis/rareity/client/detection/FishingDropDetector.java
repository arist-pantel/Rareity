package com.aristomenis.rareity.client.detection;

import com.aristomenis.rareity.client.animation.AnimationManager;
import com.aristomenis.rareity.client.animation.FishingFestivalAnimation;
import com.aristomenis.rareity.client.item.RodCache;
import com.aristomenis.rareity.client.reward.FishingDrop;

import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.DisplaySlot;

import org.slf4j.LoggerFactory;

import java.util.*;

public final class FishingDropDetector {

    private static AnimationManager manager;

    private static final Map<FishingDrop, Integer> previousPets =
            new EnumMap<>(FishingDrop.class);

    private static Map<FishingDrop, Integer> pendingBaseline;

    private static long pendingUntil;
    private static String lastFingerprint = "";
    private static long lastMessageAt;
    private static int confirmed;
    private static Object lastLevel;

    private FishingDropDetector() {
    }

    public static void register(AnimationManager animations) {
        manager = animations;

        // Observe messages without modifying or injecting them.
        ClientReceiveMessageEvents.ALLOW_GAME.register(
                (message, overlay) -> {
                    if (!overlay && active()) {
                        onMessage(message);
                    }

                    return true;
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(
                client -> tick()
        );

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    reset();
                    confirmed = 0;
                }
        );
    }

    private static boolean active() {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null
                || client.level == null
                || client.getCurrentServer() == null) {
            return false;
        }

        String address = client.getCurrentServer()
                .ip
                .toLowerCase(Locale.ROOT)
                .split(":", 2)[0];

        if (!address.equals("hypixel.net")
                && !address.endsWith(".hypixel.net")) {
            return false;
        }

        var sidebar = client.level
                .getScoreboard()
                .getDisplayObjective(DisplaySlot.SIDEBAR);

        return sidebar != null
                && sidebar.getDisplayName()
                .getString()
                .toUpperCase(Locale.ROOT)
                .contains("SKYBLOCK");
    }

    private static void onMessage(Component message) {
        int color = itemColor(message);

        var notice = FishingDropMessages.parse(
                message.getString(),
                color
        );

        if (notice == null) {
            return;
        }

        long now = System.nanoTime();

        String fingerprint = FishingDropMessages.normalize(
                message.getString()
        ) + "|" + color;

        // Ignore immediately repeated identical notifications.
        if (fingerprint.equals(lastFingerprint)
                && now - lastMessageAt < 250_000_000L) {
            return;
        }

        lastFingerprint = fingerprint;
        lastMessageAt = now;

        if (notice.drop() != null) {
            pendingBaseline = null;
            confirm(notice.drop());

        } else {
            pendingBaseline = new EnumMap<>(FishingDrop.class);
            pendingBaseline.putAll(previousPets);

            pendingUntil = now + 10_000_000_000L;

            LoggerFactory.getLogger("rareity").info(
                    "[Rareity]: Megalodon drop message received without "
                            + "a readable rarity; awaiting inventory confirmation"
            );
        }
    }

    private static int itemColor(Component message) {
        int index = message.getString().indexOf("Megalodon");

        int[] offset = {0};
        int[] color = {-1};

        message.visit(
                (style, part) -> {
                    if (index >= offset[0]
                            && index < offset[0] + part.length()
                            && style.getColor() != null) {
                        color[0] = style.getColor().getValue();
                    }

                    offset[0] += part.length();

                    return Optional.empty();
                },
                Style.EMPTY
        );

        return color[0];
    }

    private static void tick() {
        var client = Minecraft.getInstance();

        if (client.level != lastLevel) {
            reset();
            lastLevel = client.level;
        }

        if (!active()) {
            previousPets.clear();
            pendingBaseline = null;
            return;
        }

        Map<FishingDrop, Integer> current = countPets();

        if (pendingBaseline != null) {
            if (System.nanoTime() > pendingUntil) {
                pendingBaseline = null;

                LoggerFactory.getLogger("rareity").info(
                        "[Rareity]: Megalodon confirmation timed out; "
                                + "no rarity was guessed"
                );

            } else {
                FishingDrop found = null;
                boolean ambiguous = false;

                for (FishingDrop drop : List.of(
                        FishingDrop.EPIC_MEGALODON,
                        FishingDrop.LEGENDARY_MEGALODON
                )) {
                    if (current.getOrDefault(drop, 0)
                            > pendingBaseline.getOrDefault(drop, 0)) {

                        if (found != null) {
                            ambiguous = true;
                        }

                        found = drop;
                    }
                }

                if (found != null && !ambiguous) {
                    confirm(found);
                    pendingBaseline = null;
                }
            }
        }

        previousPets.clear();
        previousPets.putAll(current);
    }

    private static Map<FishingDrop, Integer> countPets() {
        Map<FishingDrop, Integer> counts =
                new EnumMap<>(FishingDrop.class);

        var inventory = Minecraft.getInstance()
                .player
                .getInventory();

        for (int slot = 0;
             slot < inventory.getContainerSize();
             slot++) {

            var stack = inventory.getItem(slot);

            if (!"PET".equals(RodCache.skyblockId(stack))) {
                continue;
            }

            try {
                var extra = stack.get(
                        DataComponents.CUSTOM_DATA
                ).copyTag();

                var pet = JsonParser.parseString(
                        extra.getStringOr("petInfo", "{}")
                ).getAsJsonObject();

                if (!"MEGALODON".equals(
                        pet.get("type").getAsString()
                )) {
                    continue;
                }

                String tier = pet.get("tier").getAsString();
                FishingDrop drop = null;

                if (tier.equals("EPIC")) {
                    drop = FishingDrop.EPIC_MEGALODON;
                } else if (tier.equals("LEGENDARY")) {
                    drop = FishingDrop.LEGENDARY_MEGALODON;
                }

                if (drop != null) {
                    counts.merge(
                            drop,
                            stack.getCount(),
                            Integer::sum
                    );
                }

            } catch (Exception ignored) {
            }
        }

        return counts;
    }

    private static void confirm(FishingDrop drop) {
        confirmed++;

        manager.enqueue(
                new FishingFestivalAnimation(drop, false)
        );

        LoggerFactory.getLogger("rareity").info(
                "[Rareity]: Confirmed Fishing Festival reward: {}",
                drop.displayName
        );
    }

    public static String status() {
        return (
                active()
                        ? "Listening on Hypixel SkyBlock"
                        : "Inactive outside Hypixel SkyBlock"
        )
                + "; confirmed this session: "
                + confirmed
                + (
                pendingBaseline == null
                        ? ""
                        : "; waiting for pet rarity"
        );
    }

    public static void replay(FishingDrop drop) {
        String message = drop == FishingDrop.TOOTH
                ? "RARE DROP! Great White Shark Tooth (+150% Magic Find)"
                : "PET DROP! Megalodon";

        var parsed = FishingDropMessages.parse(
                message,
                drop.color & 0xFFFFFF
        );

        if (parsed == null || parsed.drop() != drop) {
            throw new IllegalStateException(
                    "Replay did not match expected reward"
            );
        }

        manager.enqueue(
                new FishingFestivalAnimation(
                        parsed.drop(),
                        true
                )
        );
    }

    private static void reset() {
        previousPets.clear();
        pendingBaseline = null;
        lastFingerprint = "";
        lastMessageAt = 0;
    }
}