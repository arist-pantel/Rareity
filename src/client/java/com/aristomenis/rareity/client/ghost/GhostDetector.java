package com.aristomenis.rareity.client.ghost;

import com.aristomenis.rareity.client.animation.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.*;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class GhostDetector {
    private static final GhostDropLedger LEDGER = new GhostDropLedger();
    private static final Set<GhostAnimation> liveAnimations = new HashSet<>();
    private static AnimationManager manager;
    private static Object level;
    private static boolean debug;
    private static String lastDecision = "Waiting for a rare Ghost drop";
    private GhostDetector() {}
    private static long now() { return System.nanoTime() / 1_000_000; }

    public static void register(AnimationManager animationManager) {
        manager = animationManager;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) observe(message);
            return true;
        });
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) observe(message);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            syncWorld();
            LEDGER.prune(now());
            liveAnimations.removeIf(animation -> !manager.contains(animation));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearPending());
    }
    private static void observe(Component message) {
        GhostMessages.Notice notice = GhostMessages.parse(message.getString());
        if (notice == null) {
            if (debug && inMist() && GhostMessages.clean(message.getString()).startsWith("RARE DROP!")) {
                note("Unrecognized/unlisted rare message: " + GhostMessages.clean(message.getString()));
            }
            return;
        }
        syncWorld();
        if (!inMist()) {
            if (debug) note("Ignored " + notice.drop().displayName + " outside The Mist; sidebar=" + sidebarLines());
            return;
        }
        if (!LEDGER.accept(message, notice, now())) return;
        GhostAnimation animation = new GhostAnimation(notice.drop(), notice.quantity());
        liveAnimations.add(animation);
        manager.enqueue(animation);
        note("Ghost drop: " + notice.drop().displayName + " x" + notice.quantity());
    }
    public static boolean inMist() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getCurrentServer() == null) return false;
        String host = client.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":", 2)[0];
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return false;
        var sidebar = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null || !GhostMessages.clean(sidebar.getDisplayName().getString())
                .toUpperCase(Locale.ROOT).contains("SKYBLOCK")) return false;
        return sidebarLines().stream().anyMatch(GhostMessages::isMistLine);
    }
    private static List<String> sidebarLines() {
        var client = Minecraft.getInstance();
        if (client.level == null) return List.of();
        var scoreboard = client.level.getScoreboard();
        var objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return List.of();
        List<String> lines = new ArrayList<>();
        for (var score : scoreboard.listPlayerScores(objective)) {
            if (score.owner().startsWith("#")) continue;
            var team = scoreboard.getPlayersTeam(score.owner());
            lines.add(GhostMessages.clean(PlayerTeam.formatNameForTeam(team, score.ownerName()).getString()));
        }
        return lines;
    }
    private static void syncWorld() {
        Object current = Minecraft.getInstance().level;
        if (current != level) { clearPending(); level = current; }
    }
    private static void clearPending() {
        if (manager != null) for (GhostAnimation animation : liveAnimations) manager.cancel(animation);
        liveAnimations.clear(); LEDGER.clearMessages();
    }
    private static void note(String text) {
        lastDecision = text;
        if (debug) LoggerFactory.getLogger("rareity").info("[Ghost] {}", text);
    }
    public static String status() {
        StringJoiner counts = new StringJoiner(", ");
        for (GhostDrop drop : GhostDrop.values()) counts.add(drop.displayName + ": " + LEDGER.count(drop));
        return (inMist() ? "Listening in The Mist" : "Inactive: requires Hypixel SkyBlock / The Mist")
                + "; session notified drops: " + counts + "; " + lastDecision;
    }
    public static String debug(boolean enabled) {
        debug = enabled;
        if (enabled) note("Debug enabled; sidebar=" + sidebarLines());
        return "Ghost diagnostic logging " + (enabled ? "enabled" : "disabled");
    }
    public static void replay(GhostDrop drop) {
        var notice = GhostMessages.parse("RARE DROP! " + drop.displayName + " (+210% ✯ Magic Find)");
        if (notice == null || notice.drop() != drop) throw new IllegalStateException("Ghost parser replay failed");
        manager.play(new GhostAnimation(notice.drop(), notice.quantity()));
    }
}