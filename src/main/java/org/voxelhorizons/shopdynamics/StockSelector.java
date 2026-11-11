package org.voxelhorizons.shopdynamics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Draw without replacement from a persisted shuffle bag for broadly even exposure. */
public final class StockSelector {
    private StockSelector() {}

    public record Draw(List<String> selected, List<String> remaining) {}

    public static Draw draw(List<String> catalogue, List<String> previous,
                            List<String> bag, int limit, boolean avoidPrevious, Random random) {
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(catalogue));
        if (unique.size() != catalogue.size()) {
            throw new IllegalArgumentException("duplicate shop item IDs");
        }
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        int count = Math.min(limit, unique.size());
        Set<String> valid = new HashSet<>(unique);
        Set<String> previousSet = new HashSet<>(previous);
        List<String> queue = new ArrayList<>();
        for (String id : bag) {
            if (valid.contains(id) && !queue.contains(id)) queue.add(id);
        }

        List<String> selected = new ArrayList<>();
        while (selected.size() < count) {
            queue.removeIf(selected::contains);
            if (queue.isEmpty()) {
                queue.addAll(unique);
                queue.removeAll(selected);
                java.util.Collections.shuffle(queue, random);
            }
            int chosenIndex = 0;
            if (avoidPrevious) {
                for (int i = 0; i < queue.size(); i++) {
                    if (!previousSet.contains(queue.get(i))) {
                        chosenIndex = i;
                        break;
                    }
                }
            }
            selected.add(queue.remove(chosenIndex));
        }
        return new Draw(List.copyOf(selected), List.copyOf(queue));
    }
}
