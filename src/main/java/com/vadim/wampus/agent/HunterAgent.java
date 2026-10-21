package com.vadim.wampus.agent;

import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.Belief;
import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Position;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Логический агент-охотник. Решения принимает только по базе знаний — реальную
 * пещеру он не видит. Приоритеты:
 * <ol>
 *     <li>взять золото, если оно может быть здесь и Вампус уже убит;</li>
 *     <li>известно, где Вампус, — встать на линию выстрела и выстрелить;</li>
 *     <li>Вампус убит — идти к безопасной клетке, где может быть золото;</li>
 *     <li>идти к ближайшей безопасной непосещённой клетке;</li>
 *     <li>стрелять наугад по клеткам, где может быть Вампус;</li>
 *     <li>рискнуть и пойти в клетку с наименьшим риском, если он не выше допустимого;</li>
 *     <li>завершить поход.</li>
 * </ol>
 */
@RequiredArgsConstructor
public class HunterAgent {

    private final KnowledgeBase knowledge;
    /** Максимальная вероятность гибели, на которую агент готов пойти. 0 — никогда не рисковать. */
    private final double maxRisk;
    private final double pitPrior;

    public Decision decide(Position at, Direction facing, boolean hasArrow) {
        return tryGrab(at)
                .or(() -> tryHunt(at, facing, hasArrow))
                .or(() -> tryReachGold(at, facing))
                .or(() -> tryExplore(at, facing))
                .or(() -> tryBlindShot(at, facing, hasArrow))
                .or(() -> tryRisk(at, facing))
                .orElseGet(() -> Decision.builder()
                        .actions(List.of(Action.STOP))
                        .explanation(String.format(
                                "Безопасных ходов не осталось, а любой ход опаснее допустимых %.0f%% — агент завершает поход",
                                maxRisk * 100))
                        .build());
    }

    private Optional<Decision> tryGrab(Position at) {
        Belief gold = knowledge.gold(at);
        if (!knowledge.isWampusDead() || (gold != Belief.PRESENT && gold != Belief.POSSIBLE)) {
            return Optional.empty();
        }
        return Optional.of(Decision.builder()
                .actions(List.of(Action.GRAB))
                .explanation(gold == Belief.PRESENT
                        ? "Золото в этой клетке, Вампус убит — агент берёт золото"
                        : "Золото может быть в этой клетке, Вампус убит — агент пробует взять золото")
                .build());
    }

    private Optional<Decision> tryHunt(Position at, Direction facing, boolean hasArrow) {
        Position target = knowledge.knownWampus();
        if (target == null || !hasArrow) {
            return Optional.empty();
        }
        return RoutePlanner.plan(knowledge.getSize(), at, facing, knowledge::isSafe,
                        state -> knowledge.isSafe(state.position())
                                && knowledge.shotLine(state.position(), state.direction()).contains(target))
                .map(route -> {
                    List<Action> actions = new ArrayList<>(route);
                    actions.add(Action.SHOOT);
                    return Decision.builder()
                            .actions(actions)
                            .target(target)
                            .explanation("Вампус в клетке " + target
                                    + " — агент встаёт на линию выстрела и стреляет, чтобы добраться до золота")
                            .build();
                });
    }

    private Optional<Decision> tryReachGold(Position at, Direction facing) {
        if (!knowledge.isWampusDead()) {
            return Optional.empty();
        }
        Set<Position> targets = knowledge.getCells().stream()
                .filter(knowledge::isSafe)
                .filter(cell -> knowledge.gold(cell) == Belief.PRESENT || knowledge.gold(cell) == Belief.POSSIBLE)
                .collect(Collectors.toSet());
        return goTo(at, facing, targets, cell -> knowledge.gold(cell) == Belief.PRESENT
                ? "в клетке " + cell + " золото"
                : "в клетке " + cell + " может быть золото");
    }

    private Optional<Decision> tryExplore(Position at, Direction facing) {
        Set<Position> targets = knowledge.getCells().stream()
                .filter(cell -> knowledge.isSafe(cell) && !knowledge.isVisited(cell))
                .collect(Collectors.toSet());
        return goTo(at, facing, targets, cell -> {
            String reason = "клетка " + cell + " безопасна";
            List<String> fears = dangerousNeighbors(at);
            return fears.isEmpty() ? reason : reason + ", а идти в " + String.join(" или ", fears)
                    + " агент боится";
        });
    }

    private Optional<Decision> tryBlindShot(Position at, Direction facing, boolean hasArrow) {
        List<Position> suspects = knowledge.cellsWhere(knowledge::wampus, Belief.POSSIBLE);
        if (!hasArrow || knowledge.isWampusDead() || suspects.isEmpty()) {
            return Optional.empty();
        }
        return RoutePlanner.plan(knowledge.getSize(), at, facing, knowledge::isSafe,
                        state -> knowledge.isSafe(state.position())
                                && knowledge.shotLine(state.position(), state.direction()).stream().anyMatch(suspects::contains))
                .map(route -> {
                    List<Action> actions = new ArrayList<>(route);
                    actions.add(Action.SHOOT);
                    return Decision.builder()
                            .actions(actions)
                            .explanation("Безопасных клеток не осталось, Вампус может быть в "
                                    + suspects.stream().map(Position::toString).collect(Collectors.joining(" или "))
                                    + " — агент стреляет наугад: тишина тоже даст знание")
                            .build();
                });
    }

    private Optional<Decision> tryRisk(Position at, Direction facing) {
        record Candidate(Position cell, double risk, List<Action> route) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Position cell : knowledge.getCells()) {
            boolean frontier = !knowledge.isVisited(cell)
                    && cell.neighbors(knowledge.getSize()).stream().anyMatch(knowledge::isVisited);
            if (!frontier || knowledge.pit(cell) == Belief.PRESENT || knowledge.wampus(cell) == Belief.PRESENT) {
                continue;
            }
            RoutePlanner.plan(knowledge.getSize(), at, facing,
                            next -> knowledge.isSafe(next) || next.equals(cell),
                            state -> state.position().equals(cell))
                    .ifPresent(route -> candidates.add(new Candidate(cell, knowledge.risk(cell, pitPrior), route)));
        }
        return candidates.stream()
                .filter(candidate -> candidate.risk() <= maxRisk)
                .min(Comparator.comparingDouble(Candidate::risk).thenComparingInt(c -> c.route().size()))
                .map(best -> Decision.builder()
                        .actions(best.route())
                        .target(best.cell())
                        .risky(true)
                        .explanation(String.format(
                                "Безопасных клеток не осталось — рискованный ход в (%s), вероятность гибели %.0f%%",
                                best.cell(), best.risk() * 100))
                        .build());
    }

    private Optional<Decision> goTo(Position at, Direction facing, Set<Position> targets,
                                    java.util.function.Function<Position, String> reason) {
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        return RoutePlanner.plan(knowledge.getSize(), at, facing, knowledge::isSafe,
                        state -> targets.contains(state.position()))
                .filter(route -> !route.isEmpty())
                .map(route -> {
                    Position target = destination(at, facing, route);
                    return Decision.builder()
                            .actions(route)
                            .target(target)
                            .explanation("Агент идёт в (" + target + "): " + reason.apply(target))
                            .build();
                });
    }

    private List<String> dangerousNeighbors(Position at) {
        return at.neighbors(knowledge.getSize()).stream()
                .filter(cell -> !knowledge.isSafe(cell))
                .map(cell -> {
                    String marks = knowledge.marks(cell);
                    return cell + " (" + (marks.isEmpty() ? "неизвестно" : marks) + ")";
                })
                .toList();
    }

    private static Position destination(Position at, Direction facing, List<Action> route) {
        Position position = at;
        Direction direction = facing;
        for (Action action : route) {
            switch (action) {
                case FORWARD -> position = position.neighbor(direction);
                case TURN_LEFT -> direction = direction.turnLeft();
                case TURN_RIGHT -> direction = direction.turnRight();
                default -> {
                }
            }
        }
        return position;
    }
}
