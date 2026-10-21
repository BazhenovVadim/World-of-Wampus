package com.vadim.wampus.game;

import com.vadim.wampus.config.WampusProperties;
import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.GameStatus;
import com.vadim.wampus.model.Position;
import com.vadim.wampus.world.CaveGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WampusGameTest {

    @Test
    void agentSolvesWorldFromAssignment() {
        WampusProperties properties = properties(4, 0);
        properties.setDemo(true);
        WampusGame game = new WampusGame(properties, new CaveGenerator(properties));
        game.newGame();

        List<Position> route = new ArrayList<>();
        while (!game.getStatus().isFinished()) {
            game.agentStep();
            route.add(game.getHunter().getPosition());
        }

        assertEquals(GameStatus.WON, game.getStatus());
        assertEquals(List.of(new Position(4, 2), new Position(3, 1)), route.subList(0, 2), "шаги 1–2 из методички");
        assertTrue(game.getScore() > 0);
    }

    /** Агент погибает только при осознанном рискованном ходе — никогда не идёт в клетку, считая её безопасной. */
    @Test
    void agentNeverDiesOnSafeMove() {
        for (long seed = 1; seed <= 300; seed++) {
            WampusProperties properties = properties(4 + (int) (seed % 7), seed);
            WampusGame game = new WampusGame(properties, new CaveGenerator(properties));
            game.newGame();
            StepReport last = null;
            while (!game.getStatus().isFinished()) {
                last = game.agentStep();
            }
            if (!game.getHunter().isAlive()) {
                assertTrue(last.getObservation().getAdvice().isRisky(), "seed " + seed);
            }
        }
    }

    @Test
    void manualModeReportsBumpAndKeepsAdvice() {
        WampusProperties properties = properties(4, 0);
        properties.setDemo(true);
        WampusGame game = new WampusGame(properties, new CaveGenerator(properties));
        game.newGame();

        game.manualStep(Action.TURN_LEFT);
        game.manualStep(Action.TURN_LEFT);
        StepReport bump = game.manualStep(Action.FORWARD);
        Observation next = game.observe();

        assertTrue(bump.isManual());
        assertTrue(next.getPercept().isBump());
        assertEquals(new Position(4, 1), next.getPosition());
        assertTrue(next.getAdvice().getActions().size() > 0);
    }

    private static WampusProperties properties(int size, long seed) {
        WampusProperties properties = new WampusProperties();
        properties.setSize(size);
        properties.setPitRatio(0.2);
        properties.setSeed(seed);
        properties.setMaxRisk(0.35);
        properties.setMaxSteps(1000);
        return properties;
    }
}
