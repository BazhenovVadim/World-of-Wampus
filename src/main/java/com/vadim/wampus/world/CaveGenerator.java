package com.vadim.wampus.world;

import com.vadim.wampus.config.WampusProperties;
import com.vadim.wampus.model.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Случайно расставляет объекты в пещере:
 * Вампус — в любой комнате, кроме стартовой; золото — в соседней с Вампусом комнате;
 * ямы — в оставшихся свободных комнатах, их число пропорционально числу комнат.
 */
@Component
@RequiredArgsConstructor
public class CaveGenerator {

    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 20;

    private final WampusProperties properties;

    public Cave generate() {
        if (properties.isDemo()) {
            return demoCave();
        }
        int size = properties.getSize();
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("размер поля должен быть от " + MIN_SIZE + " до " + MAX_SIZE);
        }
        if (properties.getPitRatio() < 0 || properties.getPitRatio() > 0.5) {
            throw new IllegalArgumentException("доля ям должна быть от 0 до 0.5");
        }
        Random random = properties.getSeed() == 0 ? new Random() : new Random(properties.getSeed());
        Position start = Cave.startOf(size);

        List<Position> cells = new ArrayList<>();
        for (int row = 1; row <= size; row++) {
            for (int col = 1; col <= size; col++) {
                cells.add(new Position(row, col));
            }
        }
        cells.remove(start);
        Collections.shuffle(cells, random);

        Position wampus = cells.get(0);
        List<Position> goldPlaces = new ArrayList<>(wampus.neighbors(size));
        goldPlaces.remove(start);
        Position gold = goldPlaces.get(random.nextInt(goldPlaces.size()));

        List<Position> free = new ArrayList<>(cells);
        free.remove(wampus);
        free.remove(gold);
        int pitCount = Math.min(pitCount(size, properties.getPitRatio()), free.size());
        Set<Position> pits = new HashSet<>(free.subList(0, pitCount));
        return new Cave(size, pits, wampus, gold);
    }

    public static int pitCount(int size, double ratio) {
        return (int) Math.round(size * size * ratio);
    }

    /** Пещера с рисунка из методички. */
    public static Cave demoCave() {
        Set<Position> pits = Set.of(new Position(1, 4), new Position(2, 3), new Position(4, 3));
        return new Cave(4, new HashSet<>(pits), new Position(2, 1), new Position(2, 2));
    }
}
