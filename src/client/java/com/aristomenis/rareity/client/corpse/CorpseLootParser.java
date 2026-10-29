package com.aristomenis.rareity.client.corpse;

import java.util.*;
import java.util.regex.Pattern;

/** Collects one server loot block, rather than reacting to individual item names. */
public final class CorpseLootParser {
    private static final Pattern HEADER = Pattern.compile(
            "^(LAPIS|UMBER|TUNGSTEN|VANGUARD) CORPSE LOOT!$");
    private static final Pattern BORDER = Pattern.compile("^[▬━─=\\-+]{8,}$");
    private static final long QUIET_MS = 750, MAX_MS = 5_000;
    private final EnumMap<CorpseDrop, Integer> drops = new EnumMap<>(CorpseDrop.class);
    private boolean collecting, rewards, hasRows;
    private long startedAt, lastRowAt;

    public record Batch(Map<CorpseDrop, Integer> drops) {
        public Batch { drops = Map.copyOf(drops); }
    }

    public List<Batch> accept(String message, long now) {
        List<Batch> completed = new ArrayList<>();
        // Some servers send the complete panel in one component, others send rows.
        for (String original : message.split("\\R")) {
            String line = clean(original);
            var header = HEADER.matcher(line);
            if (header.matches()) {
                reset();
                collecting = header.group(1).equals("VANGUARD");
                startedAt = now;
                continue;
            }
            if (!collecting) continue;
            if (now - startedAt > MAX_MS) { reset(); continue; }
            if (line.equals("REWARDS")) { rewards = true; continue; }
            if (BORDER.matcher(line).matches()) {
                if (rewards && hasRows) completed.add(finish());
                else reset();
                continue;
            }
            // SkyHanni profit, player chat and unrelated messages are never loot rows.
            if (!rewards || line.isBlank() || line.startsWith("[")) continue;
            boolean recognized = false;
            for (CorpseDrop drop : CorpseDrop.values()) {
                var row = Pattern.compile("^" + Pattern.quote(drop.displayName)
                        + "(?:\\s+[x×]([0-9][0-9,]*))?$").matcher(line);
                if (!row.matches()) continue;
                try {
                    int count = row.group(1) == null ? 1
                            : Integer.parseInt(row.group(1).replace(",", ""));
                    if (count > 0 && count <= 1_000_000) {
                        drops.merge(drop, count, (a, b) -> Math.min(1_000_000, a + b));
                        recognized = true;
                    }
                } catch (NumberFormatException ignored) {}
                break;
            }
            // Ordinary loot is needed to distinguish a genuine dud from an empty panel.
            String plain = original.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "")
                    .replace('\u00a0', ' ');
            if (recognized || plain.matches("^\\s{3,}[^\\[+].*")) {
                hasRows = true;
                lastRowAt = now;
            }
        }
        return completed;
    }

    public Optional<Batch> poll(long now) {
        // Support panels without a closing border, but wait for the whole burst.
        if (collecting && rewards && hasRows && now - lastRowAt >= QUIET_MS) {
            return Optional.of(finish());
        }
        if (collecting && now - startedAt > MAX_MS) reset();
        return Optional.empty();
    }

    public void reset() {
        collecting = false; rewards = false; hasRows = false; drops.clear();
    }

    private Batch finish() {
        Batch batch = new Batch(drops);
        reset();
        return batch;
    }

    public static String clean(String text) {
        return text.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "")
                .replace('\u00a0', ' ').strip().replaceFirst("\\s+\\+$", "").strip();
    }
}