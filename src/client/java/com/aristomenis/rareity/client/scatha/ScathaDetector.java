package com.aristomenis.rareity.client.scatha;

import com.aristomenis.rareity.client.animation.*;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ScathaDetector {
    private static final ScathaKillTracker TRACKER = new ScathaKillTracker();
    private static final ScathaRollCoordinator ROLLS = new ScathaRollCoordinator(new Random());
    private static final Map<UUID, ArmorStand> tags = new HashMap<>();
    private static final Map<Long, ScathaAnimation> animations = new HashMap<>();
    private static final String TAIL_SKIN = "9624160eb99bdc651da38db99cd7c09f1da669ed82b9cbc2828746e650c765da";
    private static AnimationManager manager;
    private static Object level;
    private static Vec3 playerPosition;
    private static long tick, lastHintAt, lastXpSeen;
    private static UUID lastHint;
    private static String xpSignature = "", lastDecision = "Waiting for a Scatha";
    private static int kills, pets;
    private static boolean debug, inventoryReady;
    private static Map<ScathaDrop, Integer> previousPets = Map.of(), pendingBaseline;
    private static long pendingUntil, inventoryDeltaAt;
    private static ScathaDrop inventoryDelta;
    private static UUID pendingHint;
    private ScathaDetector() {}

    private static long now() { return System.nanoTime() / 1_000_000; }
    public static void register(AnimationManager animationsManager) {
        manager = animationsManager;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player == Minecraft.getInstance().player && !player.isSpectator() && context()) {
                scanTags();
                UUID id = tagForHit(entity);
                if (id != null) {
                    TRACKER.attack(id, now()); lastHint = id; lastHintAt = now();
                    note("Attack associated with Scatha " + id);
                } else if (debug && entity instanceof ArmorStand) {
                    note("Armor-stand attack could not be linked to a Scatha; waiting for XP evidence");
                }
            }
            return InteractionResult.PASS;
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (context() && world == Minecraft.getInstance().level && tags.containsKey(entity.getUUID())) removed(entity);
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            observeMessage(message, overlay);
            return true;
        });
        // A chat-hiding mod can cancel the message before our ALLOW_GAME listener runs.
        ClientReceiveMessageEvents.GAME_CANCELED.register(ScathaDetector::observeMessage);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset(); level = null; playerPosition = null; kills = pets = 0;
        });
    }
    private static void observeMessage(Component message, boolean overlay) {
        if (!context()) return;
        if (overlay) combatXp(message.getString());
        else petMessage(message);
    }
    private static boolean context() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getCurrentServer() == null) return false;
        String host = client.getCurrentServer().ip.toLowerCase(Locale.ROOT).split(":", 2)[0];
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return false;
        var objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null || !ScathaMessages.clean(objective.getDisplayName().getString())
                .toUpperCase(Locale.ROOT).contains("SKYBLOCK")) return false;
        Vec3 position = client.player.position();
        boolean changed = level != null && (level != client.level
                || (playerPosition != null && playerPosition.distanceToSqr(position) > 400));
        if (changed) { reset(); lastDecision = "World change/teleport: tracking reset"; }
        level = client.level; playerPosition = position;
        return !changed;
    }
    private static void tick() {
        if (!context()) { reset(); return; }
        long now = now();
        if ((tick++ & 1) == 0) scanTags();
        for (var entry : List.copyOf(tags.entrySet())) {
            ArmorStand stand = entry.getValue();
            if (stand.isRemoved()) removed(stand);
            else if (stand.distanceToSqr(Minecraft.getInstance().player) > 48 * 48) {
                TRACKER.ignore(entry.getKey(), now); tags.remove(entry.getKey());
            }
        }
        for (UUID id : TRACKER.poll(now)) {
            kills++; lastHint = id; lastHintAt = now;
            show(ROLLS.kill(id, now));
            tags.remove(id);
            note("Scatha kill confirmed from nearby removal plus attack/XP evidence");
        }
        inventory(now);
        ROLLS.prune(now);
        animations.entrySet().removeIf(entry -> !ROLLS.contains(entry.getKey()) && !manager.contains(entry.getValue()));
    }
    private static void scanTags() {
        Minecraft client = Minecraft.getInstance();
        long now = now();
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class,
                client.player.getBoundingBox().inflate(40, 12, 40))) {
            if (stand.isRemoved() || stand.getCustomName() == null) continue;
            double health = ScathaMessages.health(stand.getCustomName().getString());
            if (!Double.isFinite(health)) continue;
            UUID id = stand.getUUID();
            if (tags.size() >= 32 && !tags.containsKey(id)) continue;
            if (!tags.containsKey(id)) note("Scatha name tag found: " + ScathaMessages.clean(stand.getCustomName().getString()));
            tags.put(id, stand);
            TRACKER.observe(id, health, now);
        }
    }
    private static UUID tagForHit(Entity entity) {
        if (tags.containsKey(entity.getUUID())) return entity.getUUID();
        if (!(entity instanceof ArmorStand stand) || !scathaSegment(stand)) return null;
        UUID best = null;
        double nearest = 24 * 24, second = Double.POSITIVE_INFINITY;
        for (var entry : tags.entrySet()) {
            if (entry.getValue().isRemoved()) continue;
            double distance = entry.getValue().distanceToSqr(entity);
            if (distance < nearest) { second = nearest; nearest = distance; best = entry.getKey(); }
            else second = Math.min(second, distance);
        }
        return best != null && second - nearest > 4 ? best : null;
    }
    private static boolean scathaSegment(ArmorStand stand) {
        var stack = stand.getItemBySlot(EquipmentSlot.HEAD);
        var profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return false;
        for (var property : profile.partialProfile().properties().get("textures")) {
            try {
                if (property.value().length() > 16_384) continue;
                var json = JsonParser.parseString(new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
                String url = json.get("url").getAsString();
                String hash = url.substring(url.lastIndexOf('/') + 1);
                if (hash.equals(ScathaDrop.SCATHA_SKIN) || hash.equals(TAIL_SKIN)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }
    private static void removed(Entity entity) {
        long now = now();
        double distance = entity.distanceToSqr(Minecraft.getInstance().player);
        var reason = entity.getRemovalReason();
        boolean unload = reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION;
        TRACKER.removed(entity.getUUID(), distance <= 20 * 20, unload, now);
        tags.remove(entity.getUUID());
    }
    private static void combatXp(String text) {
        String signature = ScathaMessages.combatSignature(text);
        if (signature == null) return;
        long now = now();
        if (!signature.equals(xpSignature) || now - lastXpSeen > 1_500) {
            TRACKER.combatXp(now);
            note("New combat XP popup observed");
        }
        xpSignature = signature; lastXpSeen = now;
    }
    private static void petMessage(Component component) {
        String text = component.getString();
        if (ScathaMessages.clean(text).equals("You hear the sound of something approaching...")) {
            note("Approaching warning observed; waiting for entity and kill evidence"); return;
        }
        ScathaMessages.PetNotice notice = ScathaMessages.pet(text, itemColor(component));
        if (notice == null) {
            if (debug && text.contains("PET DROP!") && text.contains("Scatha")) note("Unrecognized pet message: " + text);
            return;
        }
        long now = now();
        UUID hint = now - lastHintAt <= 2_500 ? lastHint : null;
        if (notice.rarity() != null) {
            pendingBaseline = null;
            pet(notice.rarity(), hint, now);
        } else if (pendingBaseline == null) {
            pendingBaseline = new EnumMap<>(ScathaDrop.class);
            pendingBaseline.putAll(inventoryReady ? previousPets : countPets());
            pendingHint = hint; pendingUntil = now + 10_000;
            note("Scatha pet message has unreadable/conflicting rarity; waiting for inventory confirmation");
        }
    }
    private static void pet(ScathaDrop rarity, UUID hint, long now) {
        var change = ROLLS.pet(rarity, hint, now);
        if (change == null) return;
        pets++; show(change);
        note("Scatha pet confirmed: " + rarity.displayName);
    }
    public static int itemColor(Component component) {
        String full = component.getString();
        int index = full.toLowerCase(Locale.ROOT).indexOf("scatha");
        if (index < 0) return -1;
        int[] offset = {0}, color = {-1};
        String codes = "0123456789abcdef";
        int[] colors = {0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
                0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
        component.visit((style, part) -> {
            if (index >= offset[0] && index < offset[0] + part.length()) {
                color[0] = style.getColor() == null ? -1 : style.getColor().getValue();
                int local = index - offset[0];
                for (int i = 0; i + 1 < local; i++) {
                    if (part.charAt(i) != '§') continue;
                    char code = Character.toLowerCase(part.charAt(++i));
                    int n = codes.indexOf(code);
                    if (n >= 0) color[0] = colors[n];
                    else if (code == 'r') color[0] = -1;
                }
            }
            offset[0] += part.length();
            return Optional.empty();
        }, Style.EMPTY);
        return color[0];
    }
    private static void inventory(long now) {
        Map<ScathaDrop, Integer> current = countPets();
        if (inventoryReady) {
            ScathaDrop delta = null; boolean ambiguous = false;
            for (ScathaDrop drop : List.of(ScathaDrop.RARE, ScathaDrop.EPIC, ScathaDrop.LEGENDARY)) {
                if (current.getOrDefault(drop, 0) > previousPets.getOrDefault(drop, 0)) {
                    if (delta != null) ambiguous = true;
                    delta = drop;
                }
            }
            if (delta != null && !ambiguous) { inventoryDelta = delta; inventoryDeltaAt = now; }
        }
        if (pendingBaseline != null) {
            if (now > pendingUntil) {
                note("Scatha rarity confirmation timed out; no rarity was guessed"); pendingBaseline = null;
            } else {
                ScathaDrop found = null; boolean ambiguous = false;
                for (ScathaDrop drop : List.of(ScathaDrop.RARE, ScathaDrop.EPIC, ScathaDrop.LEGENDARY)) {
                    if (current.getOrDefault(drop, 0) > pendingBaseline.getOrDefault(drop, 0)) {
                        if (found != null) ambiguous = true;
                        found = drop;
                    }
                }
                if (found == null && inventoryDelta != null && now - inventoryDeltaAt < 1_500) found = inventoryDelta;
                if (found != null && !ambiguous) { pet(found, pendingHint, now); pendingBaseline = null; }
            }
        }
        previousPets = current; inventoryReady = true;
    }
    private static Map<ScathaDrop, Integer> countPets() {
        Map<ScathaDrop, Integer> result = new EnumMap<>(ScathaDrop.class);
        var inventory = Minecraft.getInstance().player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            try {
                var tag = data.copyTag();
                if (!tag.getStringOr("id", "").equals("PET")) continue;
                var pet = JsonParser.parseString(tag.getStringOr("petInfo", "{}")).getAsJsonObject();
                if (!pet.get("type").getAsString().equals("SCATHA")) continue;
                ScathaDrop rarity = ScathaDrop.valueOf(pet.get("tier").getAsString());
                if (rarity.pet()) result.merge(rarity, stack.getCount(), Integer::sum);
            } catch (Exception ignored) {}
        }
        return result;
    }
    private static void show(ScathaRollCoordinator.Change change) {
        if (change == null) return;
        ScathaAnimation existing = animations.get(change.roll().token);
        if (existing != null && manager.contains(existing) && existing.upgradePet(change.roll().result)) return;
        ScathaAnimation animation = new ScathaAnimation(change.roll().result);
        animations.put(change.roll().token, animation);
        manager.enqueue(animation);
    }
    private static void reset() {
        TRACKER.reset(); tags.clear(); ROLLS.reset();
        if (manager != null) for (ScathaAnimation animation : animations.values()) manager.cancel(animation);
        animations.clear(); previousPets = Map.of(); pendingBaseline = null; inventoryReady = false;
        inventoryDelta = null; lastHint = pendingHint = null; lastHintAt = 0;
        xpSignature = ""; lastXpSeen = 0;
    }
    private static void note(String message) {
        lastDecision = message;
        if (debug) LoggerFactory.getLogger("rareity").info("[Scatha] {}", message);
    }
    public static String status() {
        return (context() ? "Listening on Hypixel SkyBlock" : "Inactive outside Hypixel SkyBlock")
                + "; tracking " + TRACKER.size() + " Scathas; kills: " + kills + "; pet messages: " + pets
                + "; " + lastDecision;
    }
    public static String debug(boolean enabled) { debug = enabled; return "Scatha diagnostic logging " + (enabled ? "enabled" : "disabled"); }

    public static void replay(ScathaDrop petRarity) {
        UUID id = UUID.randomUUID();
        ScathaKillTracker tracker = new ScathaKillTracker();
        tracker.observe(id, 5, 1_000); tracker.attack(id, 1_100);
        tracker.removed(id, true, false, 1_200);
        if (!tracker.poll(1_400).equals(List.of(id))) throw new IllegalStateException("Kill replay failed");
        ScathaRollCoordinator coordinator = new ScathaRollCoordinator(new Random());
        var change = coordinator.kill(id, 1_400);
        if (petRarity != null) {
            var notice = ScathaMessages.pet("PET DROP! Scatha", petRarity.color);
            if (notice == null || notice.rarity() != petRarity) throw new IllegalStateException("Pet replay failed");
            change = coordinator.pet(notice.rarity(), id, 1_500);
        }
        manager.play(new ScathaAnimation(change.roll().result));
    }
}