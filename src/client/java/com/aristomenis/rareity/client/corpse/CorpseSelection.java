package com.aristomenis.rareity.client.corpse;

import java.util.Map;
import java.util.function.ToDoubleFunction;

public record CorpseSelection(CorpseDrop drop, int quantity, boolean incomplete) {
    public static CorpseSelection choose(Map<CorpseDrop, Integer> loot,
                                         ToDoubleFunction<CorpseDrop> unitPrice) {
        CorpseDrop winner = null;
        double best = -1;
        boolean missing = false;
        int candidates = 0;
        for (CorpseDrop drop : CorpseDrop.values()) {
            if (loot.getOrDefault(drop, 0) <= 0) continue;
            candidates++;
            double price = unitPrice.applyAsDouble(drop);
            boolean known = Double.isFinite(price) && price > 0;
            if (!known) missing = true;
            if (winner == null || (known && price > best)) {
                winner = drop;
                best = known ? price : -1;
            }
        }
        return new CorpseSelection(winner, winner == null ? 0 : loot.get(winner),
                candidates > 1 && missing);
    }
}