package com.aristomenis.rareity.client.corpse;

public enum CorpseDrop {
    UMBER("umber", "Umber Key", "UMBER_KEY", 0xAA00AA,
            "glacite/umber/umber_key", null),
    TUNGSTEN("tungsten", "Tungsten Key", "TUNGSTEN_KEY", 0xAA00AA,
            "glacite/tungsten/tungsten_key", null),
    SKELETON("skeleton", "Skeleton Key", "SKELETON_KEY", 0xFFAA00,
            "glacite/uncategorized/skeleton_key", null),
    WISP("wisp", "Caged Wisp", "CAGED_WISP", 0xFFAA00, null,
            "eaf9810a74e7a82c0c2c44566d13e050519ac3520f0da58c074ae07f0385b479"),
    LOCKET("locket", "Shattered Locket", "SHATTERED_PENDANT", 0xFFAA00, null,
            "d185a3ddade2cdadf404c91457331764fadc920a2142197f995910686e8bd688"),
    DYE("dye", "Frostbitten Dye", "DYE_FROSTBITTEN", 0x00AAAA, null,
            "e22b92e8c9b9db35f5ac2ad1235655cead444e50753c56e9a7184bca3a745729");

    public final String command, displayName, itemId, texturePath, skinHash;
    public final int color;

    CorpseDrop(String command, String displayName, String itemId, int color,
               String texturePath, String skinHash) {
        this.command = command;
        this.displayName = displayName;
        this.itemId = itemId;
        this.color = color;
        this.texturePath = texturePath;
        this.skinHash = skinHash;
    }

    public boolean bazaar() { return texturePath != null; }
}