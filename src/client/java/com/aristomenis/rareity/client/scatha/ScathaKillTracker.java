package com.aristomenis.rareity.client.scatha;

import java.util.*;

public final class ScathaKillTracker {
    private static final long ATTACK_MS = 2_000, XP_MS = 1_250, SETTLE_MS = 150;
    private final Map<UUID, Track> tracks = new LinkedHashMap<>();
    private final Map<UUID, Long> completed = new HashMap<>();
    private long combatAt = Long.MIN_VALUE / 2, usedCombatAt = Long.MIN_VALUE / 2;
    private static final class Track {
        final long firstSeen;
        long attackAt = Long.MIN_VALUE / 2, removedAt = -1;
        boolean zeroHealth;
        Track(long now) { firstSeen = now; }
    }
    public void observe(UUID id, double health, long now) {
        if (completed.containsKey(id)) return;
        if (!tracks.containsKey(id) && tracks.size() >= 32) return;
        Track track = tracks.computeIfAbsent(id, key -> new Track(now));
        track.zeroHealth |= health == 0;
        track.removedAt = -1;
    }
    public void attack(UUID id, long now) {
        Track track = tracks.get(id);
        if (track != null) track.attackAt = now;
    }
    public void combatXp(long now) { combatAt = now; }
    public void removed(UUID id, boolean nearby, boolean chunkUnload, long now) {
        Track track = tracks.get(id);
        if (track == null || track.removedAt >= 0) return;
        if (!nearby || chunkUnload || (!track.zeroHealth && now - track.firstSeen >= 28_000)) {
            ignore(id, now); return;
        }
        track.removedAt = now;
    }
    public void ignore(UUID id, long now) { tracks.remove(id); completed.put(id, now); }
    public List<UUID> poll(long now) {
        List<UUID> kills = new ArrayList<>();
        List<UUID> xpCandidates = new ArrayList<>();
        for (var entry : tracks.entrySet()) {
            Track t = entry.getValue();
            if (t.removedAt < 0 || now - t.removedAt < SETTLE_MS) continue;
            if (t.attackAt <= t.removedAt && t.removedAt - t.attackAt <= ATTACK_MS) {
                kills.add(entry.getKey());
            } else if (combatAt != usedCombatAt && Math.abs(combatAt - t.removedAt) <= XP_MS) {
                xpCandidates.add(entry.getKey());
            }
        }
        if (kills.isEmpty() && xpCandidates.size() == 1) {
            kills.add(xpCandidates.getFirst()); usedCombatAt = combatAt;
        } else if (!kills.isEmpty() && Math.abs(now - combatAt) <= XP_MS) {
            usedCombatAt = combatAt;
        }
        for (UUID id : kills) ignore(id, now);
        tracks.entrySet().removeIf(entry -> {
            Track t = entry.getValue();
            return (t.removedAt >= 0 && now - t.removedAt > XP_MS + SETTLE_MS)
                    || now - t.firstSeen > 90_000;
        });
        completed.entrySet().removeIf(entry -> now - entry.getValue() > 60_000);
        return kills;
    }
    public int size() { return tracks.size(); }
    public void reset() {
        tracks.clear(); completed.clear(); combatAt = usedCombatAt = Long.MIN_VALUE / 2;
    }
}