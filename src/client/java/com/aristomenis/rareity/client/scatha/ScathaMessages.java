package com.aristomenis.rareity.client.scatha;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ScathaMessages {
    private static final Pattern PET = Pattern.compile(
            "^PET DROP!\\s+(?:(RARE|EPIC|LEGENDARY)\\s+)?(?:\\[Lvl \\d+\\]\\s+)?Scatha(?: Pet)?(?:\\s+\\([^\\r\\n]*\\))?!?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile(
            "^(?:\\[Lv\\d+\\]\\s*)?Scatha\\s+([0-9,.]+[kKmMbB]?)(?:/[0-9,.]+[kKmMbB]?)?\\s*[❤♥](?:\\s.*)?$");
    private static final Pattern XP = Pattern.compile("(?:^|\\s)(\\+[0-9,.]+[kKmMbB]?\\s+Combat)(?:\\s|\\(|$)");
    public record PetNotice(ScathaDrop rarity) {}
    private ScathaMessages() {}

    public static String clean(String text) {
        return text.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "").replace('\u00a0', ' ').strip();
    }
    public static PetNotice pet(String text, int itemColor) {
        var match = PET.matcher(clean(text));
        if (!match.matches()) return null;
        ScathaDrop named = match.group(1) == null ? null
                : ScathaDrop.valueOf(match.group(1).toUpperCase(Locale.ROOT));
        ScathaDrop colored = switch (itemColor & 0xFFFFFF) {
            case 0x5555FF -> ScathaDrop.RARE;
            case 0xAA00AA -> ScathaDrop.EPIC;
            case 0xFFAA00 -> ScathaDrop.LEGENDARY;
            default -> null;
        };
        return new PetNotice(named != null && colored != null && named != colored
                ? null : named != null ? named : colored);
    }
    public static double health(String name) {
        var match = TAG.matcher(clean(name));
        if (!match.matches()) return Double.NaN;
        String value = match.group(1).replace(",", "").toLowerCase(Locale.ROOT);
        double multiplier = value.endsWith("k") ? 1_000 : value.endsWith("m") ? 1_000_000
                : value.endsWith("b") ? 1_000_000_000 : 1;
        if (multiplier != 1) value = value.substring(0, value.length() - 1);
        try { return Double.parseDouble(value) * multiplier; }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }
    public static String combatSignature(String actionBar) {
        var match = XP.matcher(clean(actionBar));
        return match.find() ? match.group(1) : null;
    }
}