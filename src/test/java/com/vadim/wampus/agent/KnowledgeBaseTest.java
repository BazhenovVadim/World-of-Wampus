package com.vadim.wampus.agent;

import com.vadim.wampus.model.Belief;
import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.model.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseTest {

    private static final Percept NONE = Percept.builder().build();
    private static final Percept BREEZE = Percept.builder().breeze(true).build();
    private static final Percept STENCH = Percept.builder().stench(true).build();

    /** Сценарий из методички: шаги 1–3. */
    @Test
    void reproducesInferenceFromAssignment() {
        KnowledgeBase knowledge = new KnowledgeBase(4);

        knowledge.tell(p(4, 1), NONE);
        assertTrue(knowledge.isSafe(p(3, 1)));
        assertTrue(knowledge.isSafe(p(4, 2)));

        knowledge.tell(p(4, 2), BREEZE);
        assertEquals(Belief.POSSIBLE, knowledge.pit(p(3, 2)));
        assertEquals(Belief.POSSIBLE, knowledge.pit(p(4, 3)));

        List<String> conclusions = knowledge.tell(p(3, 1), STENCH);
        assertEquals(Belief.ABSENT, knowledge.pit(p(3, 2)));
        assertTrue(knowledge.isSafe(p(3, 2)));
        assertEquals(Belief.PRESENT, knowledge.pit(p(4, 3)));
        assertEquals(Belief.PRESENT, knowledge.wampus(p(2, 1)));
        assertEquals(p(2, 1), knowledge.knownWampus());
        assertEquals(List.of("В 3,2 нет ямы", "Клетка 3,2 безопасна (OK)", "Яма в клетке 4,3", "Вампус в клетке 2,1",
                "Золото в одной из клеток 1,1 или 2,2"), conclusions);
    }

    @Test
    void screamMakesWholeCaveFreeOfWampus() {
        KnowledgeBase knowledge = new KnowledgeBase(4);
        knowledge.tell(p(4, 1), NONE);
        knowledge.tell(p(4, 2), NONE);
        knowledge.tell(p(3, 1), STENCH);
        knowledge.rememberShot(p(3, 1), Direction.UP);

        knowledge.tell(p(3, 1), Percept.builder().stench(true).scream(true).build());

        assertTrue(knowledge.isWampusDead());
        assertEquals(p(2, 1), knowledge.getDeadWampusAt());
        assertTrue(knowledge.isSafe(p(2, 1)));
        assertEquals("OK Wx", knowledge.marks(p(2, 1)));
    }

    @Test
    void missedShotClearsWampusFromArrowLine() {
        KnowledgeBase knowledge = new KnowledgeBase(4);
        knowledge.tell(p(4, 1), STENCH);
        assertEquals(Belief.POSSIBLE, knowledge.wampus(p(4, 2)));
        knowledge.rememberShot(p(4, 1), Direction.RIGHT);

        knowledge.tell(p(4, 1), STENCH);

        assertFalse(knowledge.isWampusDead());
        assertEquals(Belief.ABSENT, knowledge.wampus(p(4, 2)));
        assertEquals(Belief.PRESENT, knowledge.wampus(p(3, 1)));
    }

    @Test
    void glitterAndNoGlitterLocateGold() {
        KnowledgeBase knowledge = new KnowledgeBase(4);
        knowledge.tell(p(4, 1), NONE);
        knowledge.tell(p(3, 1), Percept.builder().glitter(true).build());
        knowledge.tell(p(4, 2), Percept.builder().glitter(true).build());

        assertEquals(Belief.PRESENT, knowledge.gold(p(3, 2)));
    }

    @Test
    void pitProbabilityFollowsBayesRule() {
        KnowledgeBase knowledge = new KnowledgeBase(4);
        knowledge.tell(p(4, 1), BREEZE);

        // P(яма в 3,1 | ветерок в 4,1) = p / (1 - (1 - p)^2)
        double prior = 0.2;
        double expected = prior / (1 - Math.pow(1 - prior, 2));
        assertEquals(expected, knowledge.pitProbability(p(3, 1), prior), 1e-9);
        assertEquals(prior, knowledge.pitProbability(p(1, 1), prior), 1e-9);
    }

    private static Position p(int row, int col) {
        return new Position(row, col);
    }
}
