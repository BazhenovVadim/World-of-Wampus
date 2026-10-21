package com.vadim.wampus.game;

import com.vadim.wampus.agent.HunterAgent;
import com.vadim.wampus.agent.KnowledgeBase;
import com.vadim.wampus.config.WampusProperties;
import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.GameStatus;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.model.Position;
import com.vadim.wampus.world.Cave;
import com.vadim.wampus.world.CaveGenerator;
import com.vadim.wampus.world.Hunter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Игровой цикл: измерение → пополнение базы знаний → логический вывод → решение → действие.
 * Очки как в классической постановке: −1 за действие, −10 за выстрел, +1000 за золото, −1000 за гибель.
 */
@Component
@RequiredArgsConstructor
public class WampusGame {

    public static final int ACTION_COST = 1;
    public static final int ARROW_COST = 10;
    public static final int GOLD_REWARD = 1000;
    public static final int DEATH_PENALTY = 1000;

    private final WampusProperties properties;
    private final CaveGenerator generator;

    @Getter
    private Cave cave;
    @Getter
    private Hunter hunter;
    @Getter
    private KnowledgeBase knowledge;
    private HunterAgent agent;

    @Getter
    private int step;
    @Getter
    private int score;
    @Getter
    private GameStatus status;

    private boolean bumped;
    private boolean screamed;
    private Observation observation;

    public void newGame() {
        cave = generator.generate();
        hunter = Hunter.builder().position(cave.getStart()).build();
        knowledge = new KnowledgeBase(cave.getSize());
        agent = new HunterAgent(knowledge, properties.getMaxRisk(), properties.getPitRatio());
        step = 0;
        score = 0;
        status = GameStatus.IN_PROGRESS;
        bumped = false;
        screamed = false;
        observation = null;
    }

    /**
     * Измерение в текущей комнате. Повторный вызов до выполнения действия
     * возвращает то же наблюдение — база знаний пополняется один раз за шаг.
     */
    public Observation observe() {
        if (status.isFinished()) {
            throw new IllegalStateException("игра окончена: " + status.getTitle());
        }
        if (observation == null) {
            step++;
            Position at = hunter.getPosition();
            Percept percept = cave.perceive(at, bumped, screamed);
            List<String> conclusions = knowledge.tell(at, percept);
            observation = new Observation(step, at, hunter.getDirection(), percept, conclusions,
                    agent.decide(at, hunter.getDirection(), hunter.hasArrow()));
        }
        return observation;
    }

    /** Агент выполняет своё решение целиком: маршрут до цели, выстрел или взятие золота. */
    public StepReport agentStep() {
        Observation current = observe();
        return perform(current, current.getAdvice().getActions(), false);
    }

    /** Пользователь выполняет одно действие; совет агента остаётся в наблюдении. */
    public StepReport manualStep(Action action) {
        return perform(observe(), List.of(action), true);
    }

    private StepReport perform(Observation current, List<Action> actions, boolean manual) {
        List<Action> executed = new ArrayList<>();
        List<String> events = new ArrayList<>();
        bumped = false;
        screamed = false;
        for (Action action : actions) {
            executed.add(action);
            events.add(execute(action));
            if (status.isFinished()) {
                break;
            }
        }
        if (!status.isFinished() && step >= properties.getMaxSteps()) {
            status = GameStatus.OUT_OF_STEPS;
        }
        observation = null;
        return StepReport.builder()
                .observation(current)
                .executed(executed)
                .events(events)
                .manual(manual)
                .status(status)
                .score(score)
                .build();
    }

    private String execute(Action action) {
        if (action != Action.STOP) {
            score -= ACTION_COST;
        }
        return switch (action) {
            case FORWARD -> moveForward();
            case TURN_LEFT -> {
                hunter.setDirection(hunter.getDirection().turnLeft());
                yield "Поворот налево, охотник смотрит " + hunter.getDirection().getTitle();
            }
            case TURN_RIGHT -> {
                hunter.setDirection(hunter.getDirection().turnRight());
                yield "Поворот направо, охотник смотрит " + hunter.getDirection().getTitle();
            }
            case SHOOT -> shoot();
            case GRAB -> grab();
            case STOP -> {
                status = GameStatus.GAVE_UP;
                yield "Охотник прекращает поход";
            }
        };
    }

    private String moveForward() {
        Position next = hunter.getPosition().neighbor(hunter.getDirection());
        if (!next.isInside(cave.getSize())) {
            bumped = true;
            return "Удар о стену (Bump) — охотник остаётся в (" + hunter.getPosition() + ")";
        }
        hunter.setPosition(next);
        if (cave.hasPit(next)) {
            die(GameStatus.FELL_INTO_PIT);
            return "Охотник шагнул в (" + next + ") и упал в яму";
        }
        if (cave.hasLiveWampus(next)) {
            die(GameStatus.EATEN_BY_WAMPUS);
            return "Охотник шагнул в (" + next + ") — там его съел Вампус";
        }
        return "Охотник перешёл в (" + next + ")";
    }

    private String shoot() {
        if (!hunter.hasArrow()) {
            return "Стрел не осталось";
        }
        hunter.setArrows(hunter.getArrows() - 1);
        score -= ARROW_COST;
        knowledge.rememberShot(hunter.getPosition(), hunter.getDirection());
        screamed = cave.shoot(hunter.getPosition(), hunter.getDirection());
        return "Выстрел " + hunter.getDirection().getTitle() + (screamed
                ? " — стрела попала, раздаётся крик Вампуса"
                : " — стрела ушла в стену");
    }

    private String grab() {
        Position at = hunter.getPosition();
        if (!cave.hasGold(at)) {
            knowledge.tellNoGold(at);
            return "В (" + at + ") золота нет";
        }
        if (cave.isWampusAlive()) {
            return "Золото охраняет живой Вампус — сначала нужно убить его";
        }
        cave.takeGold();
        hunter.setHasGold(true);
        score += GOLD_REWARD;
        status = GameStatus.WON;
        return "Золото взято!";
    }

    private void die(GameStatus reason) {
        hunter.setAlive(false);
        score -= DEATH_PENALTY;
        status = reason;
    }
}
