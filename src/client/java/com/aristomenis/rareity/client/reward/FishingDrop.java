package com.aristomenis.rareity.client.reward;

public enum FishingDrop {

    TOOTH(
            "tooth",
            "Great White Shark Tooth",
            "",
            0xFFFFAA00
    ),

    EPIC_MEGALODON(
            "epic",
            "Epic Megalodon",
            "EPIC",
            0xFFAA00AA
    ),

    LEGENDARY_MEGALODON(
            "legendary",
            "Legendary Megalodon",
            "LEGENDARY",
            0xFFFFAA00
    );

    public final String command;
    public final String displayName;
    public final String tier;
    public final int color;

    FishingDrop(
            String command,
            String displayName,
            String tier,
            int color
    ) {
        this.command = command;
        this.displayName = displayName;
        this.tier = tier;
        this.color = color;
    }
}