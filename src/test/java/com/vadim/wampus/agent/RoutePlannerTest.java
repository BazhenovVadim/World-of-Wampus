package com.vadim.wampus.agent;

import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerTest {

    @Test
    void findsShortestRouteThroughAllowedCellsOnly() {
        Set<Position> allowed = Set.of(new Position(4, 1), new Position(4, 2), new Position(3, 1));

        List<Action> route = RoutePlanner.plan(4, new Position(4, 2), Direction.RIGHT, allowed::contains,
                state -> state.position().equals(new Position(3, 1))).orElseThrow();

        assertEquals(List.of(Action.TURN_LEFT, Action.TURN_LEFT, Action.FORWARD, Action.TURN_RIGHT, Action.FORWARD), route);
    }

    @Test
    void returnsEmptyWhenGoalUnreachable() {
        assertTrue(RoutePlanner.plan(4, new Position(4, 1), Direction.RIGHT, cell -> false,
                state -> state.position().equals(new Position(1, 1))).isEmpty());
    }
}
