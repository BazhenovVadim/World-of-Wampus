package com.vadim.wampus.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Направление взгляда охотника. Строки нумеруются сверху вниз, поэтому «вверх» уменьшает номер строки. */
@Getter
@RequiredArgsConstructor
public enum Direction {
    UP(-1, 0, '↑', "вверх"),
    RIGHT(0, 1, '→', "вправо"),
    DOWN(1, 0, '↓', "вниз"),
    LEFT(0, -1, '←', "влево");

    private final int rowDelta;
    private final int colDelta;
    private final char arrow;
    private final String title;

    public Direction turnLeft() {
        return values()[(ordinal() + 3) % 4];
    }

    public Direction turnRight() {
        return values()[(ordinal() + 1) % 4];
    }
}
