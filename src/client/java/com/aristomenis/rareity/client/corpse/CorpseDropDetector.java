package com.aristomenis.rareity.client.corpse;

import com.aristomenis.rareity.client.animation.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class CorpseDropDetector {
    private static final com.aristomenis.rareity.client.corpse.CorpseLootParser PARSER = new com.aristomenis.rareity.client.corpse.CorpseLootParser();
    private static Object lastLevel;
    private static long nextPriceCheck;
    private static int confirmed;
    private CorpseDropDetector() {}

    public static void register(AnimationManager manager) {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay && active()) {
                Object level = Minecraft.getInstance().level;
                if (level != lastLevel) { PARSER.reset(); lastLevel = level; }
                for (var batch : PARSER.accept(message.getString(), now())) confirm(manager, batch);
            }
            return true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != lastLevel) { PARSER.reset(); lastLevel = client.level; }
            if (!active()) { PARSER.reset(); return; }
            long now = now();
            PARSER.poll(now).ifPresent(batch -> confirm(manager, batch));
            if (now >= nextPriceCheck) { nextPriceCheck = now + 60_000; com.aristomenis.rareity.client.corpse.CorpsePrices.refreshIfNeeded(); }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PARSER.reset(); lastLevel = null; confirmed = 0; nextPriceCheck = 0;
        });
    }
    private static long now() { return System.nanoTime() / 1_000_000; }
    private static boolean active() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getCurrentServer() == null) return false;
        String host = client.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":", 2)[0];
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return false;
        var sidebar = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return sidebar != null && sidebar.getDisplayName().getString().toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }
    private static void confirm(AnimationManager manager, CorpseLootParser.Batch batch) {
        confirmed++;
        manager.enqueue(new CorpseAnimation(batch.drops()));
        LoggerFactory.getLogger("rareity").info("Vanguard loot block confirmed: {}", batch.drops());
    }
    public static String status() {
        return (active() ? "Listening for Vanguard corpse panels" : "Inactive outside Hypixel SkyBlock")
                + "; Vanguard panels this session: " + confirmed;
    }
    public static void preview(AnimationManager manager, Map<CorpseDrop, Integer> drops) {
        // Feed a local loot panel through the same parser. Nothing is sent to the server.
        CorpseLootParser parser = new CorpseLootParser();
        StringBuilder panel = new StringBuilder("  VANGUARD CORPSE LOOT!\n  REWARDS\n");
        if (drops.isEmpty()) panel.append("    Refined Mithril x2\n");
        for (CorpseDrop drop : CorpseDrop.values()) {
            if (drops.getOrDefault(drop, 0) > 0) panel.append("    ").append(drop.displayName)
                    .append(" x").append(drops.get(drop)).append('\n');
        }
        panel.append("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        List<CorpseLootParser.Batch> result = parser.accept(panel.toString(), now());
        if (result.size() != 1) throw new IllegalStateException("Corpse replay did not complete");
        manager.play(new CorpseAnimation(result.getFirst().drops()));
    }
}