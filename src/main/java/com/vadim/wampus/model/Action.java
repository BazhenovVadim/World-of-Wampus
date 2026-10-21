package com.vadim.wampus.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Приводы охотника. */
@Getter
@RequiredArgsConstructor
public enum Action {
    FORWARD("вперёд"),
    TURN_LEFT("налево"),
    TURN_RIGHT("направо"),
    SHOOT("выстрел"),
    GRAB("взять золото"),
    STOP("завершить поход");

    private final String title;
}
