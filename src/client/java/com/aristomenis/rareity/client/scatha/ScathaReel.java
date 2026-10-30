package com.aristomenis.rareity.client.scatha;

import java.util.*;

public final class ScathaReel {
    private static final int START_INDEX = 4;
    private final List<ScathaDrop> cards;
    private final int stopIndex;

    public ScathaReel(ScathaDrop result, Random random) {
        Objects.requireNonNull(result);
        stopIndex = 58 + random.nextInt(15);
        List<ScathaDrop> tape = new ArrayList<>();
        while (tape.size() < stopIndex + 6) {
            List<ScathaDrop> cycle = new ArrayList<>(List.of(ScathaDrop.values()));
            Collections.shuffle(cycle, random);
            tape.addAll(cycle);
        }
        tape.set(stopIndex, result);
        cards = new ArrayList<>(tape);
    }
    public double position(double progress) {
        double p = Math.max(0, Math.min(progress, 1));
        double eased = 1 - Math.pow(1 - p, 3);
        return START_INDEX + (stopIndex - START_INDEX) * eased;
    }
    public ScathaDrop card(int index) { return cards.get(index); }
    public int size() { return cards.size(); }
    public int stopIndex() { return stopIndex; }
    public void replaceWinner(ScathaDrop result) { cards.set(stopIndex, Objects.requireNonNull(result)); }
}