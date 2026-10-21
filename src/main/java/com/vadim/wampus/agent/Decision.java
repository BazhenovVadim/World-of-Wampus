package com.vadim.wampus.agent;

import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.Position;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;

/** Решение агента: последовательность действий и её объяснение. */
@Value
@Builder
public class Decision {
    List<Action> actions;
    /** Комната, куда направляется агент (null для выстрела/взятия на месте). */
    Position target;
    String explanation;
    boolean risky;

    public String describeActions() {
        return actions.stream().map(Action::getTitle).collect(Collectors.joining(" → "));
    }
}
