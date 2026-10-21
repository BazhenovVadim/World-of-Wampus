package com.vadim.wampus.agent;

import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Position;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Поиск в ширину по состояниям (комната, направление). Каждое действие
 * (вперёд, налево, направо) стоит одинаково, поэтому найденный маршрут
 * содержит минимальное число действий.
 */
public final class RoutePlanner {

    public record State(Position position, Direction direction) {
    }

    private record Step(State previous, Action action) {
    }

    private RoutePlanner() {
    }

    /**
     * @param passable по каким комнатам разрешено идти
     * @param goal     в каком состоянии маршрут считается завершённым
     * @return список действий или пусто, если цель недостижима
     */
    public static Optional<List<Action>> plan(int size, Position from, Direction facing,
                                              Predicate<Position> passable, Predicate<State> goal) {
        State start = new State(from, facing);
        Map<State, Step> parents = new HashMap<>();
        parents.put(start, null);
        Deque<State> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            State state = queue.poll();
            if (goal.test(state)) {
                return Optional.of(restore(parents, state));
            }
            for (Action action : List.of(Action.FORWARD, Action.TURN_LEFT, Action.TURN_RIGHT)) {
                State next = apply(state, action, size, passable);
                if (next != null && !parents.containsKey(next)) {
                    parents.put(next, new Step(state, action));
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    private static State apply(State state, Action action, int size, Predicate<Position> passable) {
        return switch (action) {
            case TURN_LEFT -> new State(state.position(), state.direction().turnLeft());
            case TURN_RIGHT -> new State(state.position(), state.direction().turnRight());
            case FORWARD -> {
                Position next = state.position().neighbor(state.direction());
                yield next.isInside(size) && passable.test(next) ? new State(next, state.direction()) : null;
            }
            default -> null;
        };
    }

    private static List<Action> restore(Map<State, Step> parents, State goal) {
        List<Action> actions = new ArrayList<>();
        Step step = parents.get(goal);
        while (step != null) {
            actions.add(step.action());
            step = parents.get(step.previous());
        }
        Collections.reverse(actions);
        return actions;
    }
}
