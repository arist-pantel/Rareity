package com.aristomenis.rareity.client.revenant;

/ublic enum RevenantDrop {
    HAND("severed_hand", "Severed Hand", 0xFFAA00, "111ee65b56d50cd1ef81a7d33ae2421927592ccd92ad7c2ec4fb1aac29ba422f", true),
    PARAGON("paragon_shard", "Paragon Shard", 0xFFAA00, "75b449420f3c1891bf663c0623f1210bbbe066866cdc4c8da1cd1f9cbfa1eee", false),
    REVENANT("revenant_shard", "Revenant Shard", 0x5555FF, "1fc0184473fe882d2895ce7cbc8197bd40ff70bf10d3745de97b6c2a9c5fc78f", false),
    HORROR("beheaded_horror", "Beheaded Horror", 0xAA00AA, "dbad99ed3c820b7978190ad08a934a68dfa90d9986825da1c97f6f21f49ad626", false),
    BLADE("scythe_blade", "Scythe Blade", 0xFFAA00, null, true),
    SINEW("shredded_sinew", "Shredded Sinew", 0xFFAA00, "b10857e872711e7e6925c9ef1115353b47f58ef07eb5c2d565f1f86023e7a284", false),
    HEART("warden_heart", "Warden Heart", 0xFFAA00, "19a3b5fab01dfabf1f8a814440a8ce80de109b135a51aef225c9ee98fe447e0", false),
    DYE("matcha_dye", "Matcha Dye", 0x74A12E, "86402b7f728ff299df1238721264131860f239cd8015e3f026005197307d53ea", false);

    public final String command, displayName, skinHash;
    public final int color;
    public final boolean glowing;

    RevenantDrop(String command, String displayName, int color,
                 String skinHash, boolean glowing) {
        this.command = command;
        this.displayName = displayName;
        this.color = color;
        this.skinHash = skinHash;
        this.glowing = glowing;
    }
}