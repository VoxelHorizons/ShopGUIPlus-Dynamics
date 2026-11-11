package org.voxelhorizons.shopdynamics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class StockSelectorTest {
    @Test
    void overflowStockIsUniqueAndAllItemsBecomeAvailable() {
        List<String> catalogue = new ArrayList<>();
        for (int i = 1; i <= 53; i++) catalogue.add(Integer.toString(i));
        List<String> previous = List.of();
        List<String> bag = List.of();
        Set<String> encountered = new HashSet<>();
        Random random = new Random(12);

        for (int day = 0; day < 3; day++) {
            StockSelector.Draw draw = StockSelector.draw(catalogue, previous, bag, 20, true, random);
            assertEquals(20, draw.selected().size());
            assertEquals(20, new HashSet<>(draw.selected()).size());
            assertTrue(catalogue.containsAll(draw.selected()));
            assertTrue(previous.stream().noneMatch(draw.selected()::contains),
                "53 items leave enough new choices to avoid consecutive repeats");
            encountered.addAll(draw.selected());
            previous = draw.selected();
            bag = draw.remaining();
        }
        assertEquals(53, encountered.size(), "shuffle bag should expose every entry within three rotations");
    }

    @Test
    void doesNotSelectDuplicatesOrUnknownBagEntries() {
        StockSelector.Draw draw = StockSelector.draw(
            List.of("sword", "axe", "shovel"), List.of("sword"),
            List.of("deleted", "axe", "axe", "shovel"), 2, true, new Random(3));
        assertEquals(List.of("axe", "shovel"), draw.selected());
        assertTrue(draw.remaining().isEmpty());
    }

    @Test
    void smallCatalogueIsNeverOverfilled() {
        StockSelector.Draw draw = StockSelector.draw(
            List.of("a", "b", "c"), List.of(), List.of(), 20, true, new Random(1));
        assertEquals(Set.of("a", "b", "c"), new HashSet<>(draw.selected()));
    }

    @Test
    void rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () ->
            StockSelector.draw(List.of("same", "same"), List.of(), List.of(), 1, true, new Random(1)));
    }
}
