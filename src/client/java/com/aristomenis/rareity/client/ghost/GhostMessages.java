package com.aristomenis.rareity.client.ghost;

import java.util.*;
import java.util.regex.Pattern;

public final class GhostMessages {
    private static final String HEADER = "(?:RARE DROP|VERY RARE DROP|CRAZY RARE DROP|INSANE DROP|RARE LOOT|LEGENDARY LOOT)!\\s+";
    private static final Map<GhostDrop, Pattern> PATTERNS = new EnumMap<>(GhostDrop.class);
    private static final Pattern MIST = Pattern.compile(
            "^(?:(?:⏣|Area:|Location:)\\s*)?(?:The\\s+)?Mist$", Pattern.CASE_INSENSITIVE);
    static {
        for (GhostDrop drop : GhostDrop.values()) {
            PATTERNS.put(drop, Pattern.compile("^" + HEADER
                    + "(?:(?:([0-9][0-9,]*)[x×]|[x×]([0-9][0-9,]*))\\s+)?"
                    + Pattern.quote(drop.displayName)
                    + "(?:\\s+[x×]([0-9][0-9,]*))?"
                    + "(?:\\s+\\([^\\r\\n]*\\))?!?$", Pattern.CASE_INSENSITIVE));
        }
    }
    public record Notice(GhostDrop drop, int quantity) {}
    private GhostMessages() {}
    public static String clean(String text) {
        return text.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "")
                .replace('\u00a0', ' ').replaceAll("[\\t ]+", " ").strip();
    }
    public static Notice parse(String raw) {
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) return null;
        String text = clean(raw);
        for (var entry : PATTERNS.entrySet()) {
            var match = entry.getValue().matcher(text);
            if (!match.matches()) continue;
            String quantity = null;
            for (int group = 1; group <= 3; group++) {
                if (match.group(group) == null) continue;
                if (quantity != null) return null;
                quantity = match.group(group);
            }
            try {
                int amount = quantity == null ? 1 : Integer.parseInt(quantity.replace(",", ""));
                return amount > 0 && amount <= 1_000_000 ? new Notice(entry.getKey(), amount) : null;
            } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
    public static boolean isMistLine(String line) { return MIST.matcher(clean(line)).matches(); }
}