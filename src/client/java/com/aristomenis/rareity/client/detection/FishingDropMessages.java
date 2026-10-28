package com.aristomenis.rareity.client.detection;

import com.aristomenis.rareity.client.reward.FishingDrop;

import java.util.regex.Pattern;

public final class FishingDropMessages {

    private static final String PREFIX =
            "(?:RARE DROP|VERY RARE DROP|CRAZY RARE DROP|"
                    + "INSANE DROP|PET DROP|UNCOMMON LOOT|"
                    + "RARE LOOT|LEGENDARY LOOT)!\\s+";

    private static final String SUFFIX =
            "(?:\\s+\\([^\\r\\n]*\\))?!?";

    private static final Pattern TOOTH = Pattern.compile(
            "^"
                    + PREFIX
                    + "(?:1x\\s+)?Great White Shark Tooth"
                    + "(?:\\s+x1)?"
                    + SUFFIX
                    + "$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PET = Pattern.compile(
            "^"
                    + PREFIX
                    + "(?:\\[Lvl 1]\\s+)?"
                    + "(?:(EPIC|LEGENDARY)\\s+)?"
                    + "Megalodon(?: Pet)?"
                    + SUFFIX
                    + "$",
            Pattern.CASE_INSENSITIVE
    );

    public record Notice(FishingDrop drop, boolean pet) {
    }

    private FishingDropMessages() {
    }

    public static String normalize(String text) {
        return text.replaceAll(
                        "§[0-9A-FK-ORa-fk-or]",
                        ""
                )
                .replace('\u00A0', ' ')
                .strip();
    }

    public static Notice parse(String raw, int itemColor) {
        String text = normalize(raw);

        if (TOOTH.matcher(text).matches()) {
            return new Notice(FishingDrop.TOOTH, false);
        }

        var match = PET.matcher(text);

        if (!match.matches()) {
            return null;
        }

        FishingDrop named = null;

        if (match.group(1) != null) {
            named = match.group(1).equalsIgnoreCase("EPIC")
                    ? FishingDrop.EPIC_MEGALODON
                    : FishingDrop.LEGENDARY_MEGALODON;
        }

        FishingDrop colored = null;

        if (itemColor == 0xAA00AA) {
            colored = FishingDrop.EPIC_MEGALODON;
        } else if (itemColor == 0xFFAA00) {
            colored = FishingDrop.LEGENDARY_MEGALODON;
        }

        if (named != null && colored != null && named != colored) {
            return new Notice(null, true);
        }

        return new Notice(
                named == null ? colored : named,
                true
        );
    }

    public static String selfTest() {
        check(
                parse(
                        "RARE DROP! Great White Shark Tooth "
                                + "(+150% Magic Find)",
                        -1
                ).drop() == FishingDrop.TOOTH
        );

        check(
                parse(
                        "PET DROP! Megalodon",
                        0xAA00AA
                ).drop() == FishingDrop.EPIC_MEGALODON
        );

        check(
                parse(
                        "PET DROP! Megalodon",
                        0xFFAA00
                ).drop() == FishingDrop.LEGENDARY_MEGALODON
        );

        check(
                parse(
                        "PET DROP! Megalodon",
                        -1
                ).drop() == null
        );

        check(
                parse(
                        "[MVP+] Someone: PET DROP! Megalodon",
                        0xFFAA00
                ) == null
        );

        check(
                parse(
                        "You bought a Great White Shark Tooth!",
                        -1
                ) == null
        );

        check(
                parse(
                        "PET DROP! Baby Yeti",
                        0xFFAA00
                ) == null
        );

        check(
                parse(
                        "PET DROP! Megalodon Skin",
                        0xFFAA00
                ) == null
        );

        check(
                parse(
                        "PET DROP! EPIC Megalodon",
                        0xFFAA00
                ).drop() == null
        );

        return "9/9 parser checks passed. "
                + "Live server formatting still needs event verification.";
    }

    private static void check(boolean valid) {
        if (!valid) {
            throw new IllegalStateException(
                    "Drop parser self-test failed"
            );
        }
    }
}