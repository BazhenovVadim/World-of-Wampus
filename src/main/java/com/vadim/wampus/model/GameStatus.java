package com.vadim.wampus.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameStatus {
    IN_PROGRESS("поход продолжается"),
    WON("золото найдено — победа"),
    FELL_INTO_PIT("охотник упал в яму и погиб"),
    EATEN_BY_WAMPUS("охотника съел Вампус"),
    GAVE_UP("охотник прекратил поход"),
    OUT_OF_STEPS("закончился лимит шагов");

    private final String title;

    public boolean isFinished() {
        return this != IN_PROGRESS;
    }
}
