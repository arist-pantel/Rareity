package com.aristomenis.rareity.client.ghost;

public enum GhostDrop {
    SORROW("sorrow", "Sorrow", "SORROW", 0x5555FF),
    PLASMA("plasma", "Plasma", "PLASMA", 0x5555FF),
    VOLTA("volta", "Volta", "VOLTA", 0x5555FF),
    BOOTS("boots", "Ghostly Boots", "GHOST_BOOTS", 0xAA00AA);

    public final String command, displayName, itemId;
    public final int color;
    GhostDrop(String command, String displayName, String itemId, int color) {
        this.command = command; this.displayName = displayName;
        this.itemId = itemId; this.color = color;
    }
}