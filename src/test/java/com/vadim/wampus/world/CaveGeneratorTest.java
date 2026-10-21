package com.vadim.wampus.world;

import com.vadim.wampus.config.WampusProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveGeneratorTest {

    @Test
    void placesObjectsByRulesForDifferentSizes() {
        for (int size = CaveGenerator.MIN_SIZE; size <= 12; size++) {
            for (long seed = 1; seed <= 20; seed++) {
                Cave cave = new CaveGenerator(properties(size, seed)).generate();

                assertEquals(CaveGenerator.pitCount(size, 0.2), cave.getPits().size(), "ямы пропорциональны числу комнат");
                assertFalse(cave.hasPit(cave.getStart()));
                assertFalse(cave.getWampus().equals(cave.getStart()));
                assertTrue(cave.getGold().isAdjacent(cave.getWampus()), "Вампус сторожит золото в соседней комнате");
                assertFalse(cave.hasPit(cave.getGold()));
                assertFalse(cave.hasPit(cave.getWampus()));
            }
        }
    }

    @Test
    void rejectsInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> new CaveGenerator(properties(1, 1)).generate());
    }

    private static WampusProperties properties(int size, long seed) {
        WampusProperties properties = new WampusProperties();
        properties.setSize(size);
        properties.setPitRatio(0.2);
        properties.setSeed(seed);
        return properties;
    }
}
