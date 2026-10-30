package com.aristomenis.rareity.client.scatha;

import java.util.List;
import java.util.Random;

public enum ScathaDrop {
    TOPAZ("topaz", "Fine Topaz Gemstone", 0x55FFFF, 1,
            "collections/gemstone/topaz/fine_topaz_gem", null, null),
    AMETHYST("amethyst", "Fine Amethyst Gemstone", 0x55FFFF, 1,
            "collections/gemstone/amethyst/fine_amethyst_gem", null, null),
    JADE("jade", "Fine Jade Gemstone", 0x55FFFF, 1,
            "collections/gemstone/jade/fine_jade_gem", null, null),
    AMBER("amber", "Fine Amber Gemstone", 0x55FFFF, 1,
            "collections/gemstone/amber/fine_amber_gem", null, null),
    SAPPHIRE("sapphire", "Fine Sapphire Gemstone", 0x55FFFF, 1,
            "collections/gemstone/sapphire/fine_sapphire_gem", null, null),
    BRAN("bran", "Dwarven O's Block Bran", 0x55FFFF, 1,
            null, "4f6bddba1e3d014fc75e70e27223ca30cbfa42990d448d9a940e500a6435bec", null),
    RARE("rare", "RARE Scatha", 0x5555FF, 1, null, null, "WIN!"),
    EPIC("epic", "EPIC Scatha", 0xAA00AA, 1, null, null, "BIG WIN!"),
    LEGENDARY("legendary", "LEGENDARY Scatha", 0xFFAA00, 1, null, null, "HUGE WIN!"),
    MEMBRANE("membrane", "Worm Membrane", 0x55FFFF, 4,
            "uncategorized/worm_membrane", null, null);

    public static final String SCATHA_SKIN =
            "df03ad96092f3f789902436709cdf69de6b727c121b3c2daef9ffa1ccaed186c";
    public static final List<ScathaDrop> ORDINARY = List.of(
            TOPAZ, AMETHYST, JADE, AMBER, SAPPHIRE, BRAN, MEMBRANE);
    public final String command, displayName, texturePath, skinHash, celebration;
    public final int color, quantity;

    ScathaDrop(String command, String displayName, int color, int quantity,
               String texturePath, String skinHash, String celebration) {
        this.command = command;
        this.displayName = displayName;
        this.color = color;
        this.quantity = quantity;
        this.texturePath = texturePath;
        this.skinHash = skinHash;
        this.celebration = celebration;
    }
    public boolean pet() { return celebration != null; }
    public String label() { return displayName + (quantity > 1 ? " x" + quantity : ""); }
    public static ScathaDrop randomOrdinary(Random random) {
        return ORDINARY.get(random.nextInt(ORDINARY.size()));
    }
}