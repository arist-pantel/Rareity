package com.aristomenis.rareity.client.scatha;

import java.util.*;

public final class ScathaRollCoordinator {
    public static final class Roll {
        public final long token, createdAt;
        public UUID entityId;
        public ScathaDrop result;
        private long petAt = -1;
        private Roll(long token, UUID id, ScathaDrop result, long now) {
            this.token = token; this.entityId = id; this.result = result; this.createdAt = now;
        }
    }
    public record Change(Roll roll, boolean created) {}
    private final Deque<Roll> rolls = new ArrayDeque<>();
    private final Random random;
    private long sequence;
    public ScathaRollCoordinator(Random random) { this.random = random; }

    public Change kill(UUID id, long now) {
        prune(now);
        for (Roll roll : rolls) if (id.equals(roll.entityId)) return null;
        for (Iterator<Roll> it = rolls.descendingIterator(); it.hasNext();) {
            Roll roll = it.next();
            if (roll.entityId == null && roll.result.pet() && now - roll.createdAt <= 2_500) {
                roll.entityId = id;
                return null;
            }
        }
        Roll roll = new Roll(++sequence, id, ScathaDrop.randomOrdinary(random), now);
        rolls.addLast(roll);
        return new Change(roll, true);
    }
    public Change pet(ScathaDrop rarity, UUID hint, long now) {
        if (rarity == null || !rarity.pet()) throw new IllegalArgumentException("A pet rarity is required");
        prune(now);
        for (Roll roll : rolls) {
            if (roll.result == rarity && roll.petAt >= 0 && now - roll.petAt < 500
                    && (hint == null || hint.equals(roll.entityId))) return null;
        }
        Roll target = null;
        if (hint != null) {
            for (Roll roll : rolls) if (hint.equals(roll.entityId) && now - roll.createdAt <= 12_000) {
                if (roll.result.pet()) return null;
                target = roll; break;
            }
        }
        if (target == null) {
            for (Iterator<Roll> it = rolls.descendingIterator(); it.hasNext();) {
                Roll roll = it.next();
                if (now - roll.createdAt <= 12_000 && !roll.result.pet()) { target = roll; break; }
            }
        }
        if (target != null) {
            if (hint == null) {
                for (Roll roll : rolls) {
                    if (roll.result == rarity && roll.petAt >= 0 && now - roll.petAt < 12_000
                            && target.createdAt <= roll.petAt) return null;
                }
            }
            target.result = rarity; target.petAt = now;
            return new Change(target, false);
        }
        for (Roll roll : rolls) if (roll.result == rarity && roll.petAt >= 0 && now - roll.petAt < 12_000) return null;
        Roll roll = new Roll(++sequence, hint, rarity, now);
        roll.petAt = now; rolls.addLast(roll);
        return new Change(roll, true);
    }
    public void prune(long now) { rolls.removeIf(roll -> now - roll.createdAt > 45_000); }
    public boolean contains(long token) { return rolls.stream().anyMatch(roll -> roll.token == token); }
    public void reset() { rolls.clear(); }
}