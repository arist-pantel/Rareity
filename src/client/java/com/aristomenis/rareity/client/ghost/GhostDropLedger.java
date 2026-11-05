package com.aristomenis.rareity.client.ghost;

import java.util.*;

public final class GhostDropLedger {
    private final IdentityHashMap<Object, Long> seen = new IdentityHashMap<>();
    private final Map<GhostDrop, Long> counts = new EnumMap<>(GhostDrop.class);
    public boolean accept(Object messageIdentity, GhostMessages.Notice notice, long now) {
        Objects.requireNonNull(messageIdentity); Objects.requireNonNull(notice);
        prune(now);
        if (seen.containsKey(messageIdentity)) return false;
        if (seen.size() >= 512) {
            var oldest = Collections.min(seen.entrySet(), Comparator.comparingLong(Map.Entry::getValue));
            seen.remove(oldest.getKey());
        }
        seen.put(messageIdentity, now);
        counts.merge(notice.drop(), (long) notice.quantity(), Long::sum);
        return true;
    }
    public long count(GhostDrop drop) { return counts.getOrDefault(drop, 0L); }
    public void prune(long now) { seen.entrySet().removeIf(entry -> now - entry.getValue() > 5_000); }
    public void clearMessages() { seen.clear(); }
}