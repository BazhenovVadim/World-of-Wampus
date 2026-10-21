package com.vadim.wampus.game;

import com.vadim.wampus.agent.Decision;
import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.model.Position;
import lombok.Value;

import java.util.List;

/** Что произошло в начале шага: измерение, логический вывод и решение (совет) агента. */
@Value
public class Observation {
    int step;
    Position position;
    Direction direction;
    Percept percept;
    List<String> conclusions;
    Decision advice;
}
