package com.vadim.wampus.game;

import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.GameStatus;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/** Итог шага: какие действия выполнены и что из этого вышло. */
@Value
@Builder
public class StepReport {
    Observation observation;
    List<Action> executed;
    List<String> events;
    /** true — действие выбрал пользователь, а не агент. */
    boolean manual;
    GameStatus status;
    int score;
}
