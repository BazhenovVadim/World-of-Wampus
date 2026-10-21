package com.vadim.wampus.model;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Комната пещеры. Нумерация как в методичке: (строка, столбец), обе с единицы,
 * строка 1 — верхняя, охотник стартует в левом нижнем углу (N, 1).
 */
@Value
public class Position {
    int row;
    int col;

    public Position neighbor(Direction direction) {
        return new Position(row + direction.getRowDelta(), col + direction.getColDelta());
    }

    public boolean isInside(int size) {
        return row >= 1 && row <= size && col >= 1 && col <= size;
    }

    public List<Position> neighbors(int size) {
        List<Position> result = new ArrayList<>(4);
        for (Direction direction : Direction.values()) {
            Position next = neighbor(direction);
            if (next.isInside(size)) {
                result.add(next);
            }
        }
        return result;
    }

    public boolean isAdjacent(Position other) {
        return Math.abs(row - other.row) + Math.abs(col - other.col) == 1;
    }

    @Override
    public String toString() {
        return row + "," + col;
    }
}
