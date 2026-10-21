package com.vadim.wampus.agent;

import com.vadim.wampus.model.Belief;
import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.model.Position;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * База знаний охотника. Хранит факты (посещённые комнаты и показания датчиков в них)
 * и после каждого измерения заново выводит из них представление о каждой комнате:
 * есть ли там яма, Вампус или золото.
 *
 * <p>Правила, которые знает агент:
 * <ul>
 *     <li>в комнате, где агент жив, нет ни ямы, ни живого Вампуса;</li>
 *     <li>нет ветерка — в соседних комнатах нет ям; есть ветерок — хотя бы в одной соседней яма;</li>
 *     <li>нет запаха — в соседних комнатах нет Вампуса; запах — Вампус в одной из соседних (он один);</li>
 *     <li>стрела, пролетевшая без крика, — на её линии Вампуса нет; крик — Вампус убит;</li>
 *     <li>блеск — золото в этой или соседней комнате; золото лежит в комнате, соседней с Вампусом.</li>
 * </ul>
 */
public class KnowledgeBase {

    /** Больше клеток в связанной группе — точный перебор слишком дорог, берём оценку. */
    private static final int MAX_ENUMERATED_CELLS = 18;

    @Getter
    private final int size;
    private final List<Position> cells = new ArrayList<>();
    private final Map<Position, Percept> visited = new LinkedHashMap<>();
    private final Set<Position> missedShotCells = new HashSet<>();
    private final Set<Position> noGold = new HashSet<>();

    private Belief[][] pit;
    private Belief[][] wampus;
    private Belief[][] gold;

    private Position pendingShotFrom;
    private Direction pendingShotDirection;

    @Getter
    private boolean wampusDead;
    /** Где лежит убитый Вампус, если агент смог это установить. */
    @Getter
    private Position deadWampusAt;

    public KnowledgeBase(int size) {
        this.size = size;
        for (int row = 1; row <= size; row++) {
            for (int col = 1; col <= size; col++) {
                cells.add(new Position(row, col));
            }
        }
        infer();
    }

    // ---------------- пополнение базы ----------------

    /**
     * Добавляет в базу результат измерения в комнате {@code at} и возвращает
     * новые логические выводы, которые появились благодаря этому измерению.
     */
    public List<String> tell(Position at, Percept percept) {
        Snapshot before = snapshot();
        visited.put(at, percept);
        resolvePendingShot(percept.isScream());
        infer();
        return describeChanges(before);
    }

    /** Агент выстрелил: результат (крик или тишина) станет известен при следующем измерении. */
    public void rememberShot(Position from, Direction direction) {
        pendingShotFrom = from;
        pendingShotDirection = direction;
    }

    /** Попытка взять золото не удалась — в этой комнате золота нет. */
    public void tellNoGold(Position at) {
        noGold.add(at);
        infer();
    }

    private void resolvePendingShot(boolean scream) {
        if (pendingShotFrom == null) {
            return;
        }
        List<Position> line = shotLine(pendingShotFrom, pendingShotDirection);
        if (scream) {
            List<Position> candidates = line.stream().filter(cell -> get(wampus, cell) != Belief.ABSENT).toList();
            wampusDead = true;
            deadWampusAt = candidates.size() == 1 ? candidates.get(0) : null;
        } else {
            missedShotCells.addAll(line);
        }
        pendingShotFrom = null;
        pendingShotDirection = null;
    }

    public List<Position> shotLine(Position from, Direction direction) {
        List<Position> line = new ArrayList<>();
        Position cell = from.neighbor(direction);
        while (cell.isInside(size)) {
            line.add(cell);
            cell = cell.neighbor(direction);
        }
        return line;
    }

    // ---------------- логический вывод ----------------

    private void infer() {
        inferPits();
        inferWampus();
        inferGold();
    }

    private void inferPits() {
        pit = filled(Belief.UNKNOWN);
        visited.forEach((cell, percept) -> {
            set(pit, cell, Belief.ABSENT);
            if (!percept.isBreeze()) {
                cell.neighbors(size).forEach(neighbor -> set(pit, neighbor, Belief.ABSENT));
            }
        });
        List<List<Position>> clauses = breezeClauses();
        for (List<Position> candidates : clauses) {
            if (candidates.size() == 1) {
                set(pit, candidates.get(0), Belief.PRESENT);
            }
        }
        for (List<Position> candidates : clauses) {
            for (Position candidate : candidates) {
                if (get(pit, candidate) != Belief.PRESENT) {
                    set(pit, candidate, Belief.POSSIBLE);
                }
            }
        }
    }

    /** Для каждой комнаты с ветерком — соседние комнаты, где ещё может быть яма. */
    private List<List<Position>> breezeClauses() {
        List<List<Position>> clauses = new ArrayList<>();
        visited.forEach((cell, percept) -> {
            if (percept.isBreeze()) {
                clauses.add(cell.neighbors(size).stream().filter(n -> get(pit, n) != Belief.ABSENT).toList());
            }
        });
        return clauses;
    }

    private void inferWampus() {
        wampus = filled(Belief.UNKNOWN);
        if (wampusDead) {
            wampus = filled(Belief.ABSENT);
            return;
        }
        Set<Position> candidates = new HashSet<>(cells);
        candidates.removeAll(missedShotCells);
        boolean anyStench = false;
        for (Map.Entry<Position, Percept> entry : visited.entrySet()) {
            Position cell = entry.getKey();
            candidates.remove(cell);
            if (entry.getValue().isStench()) {
                anyStench = true;
                candidates.retainAll(cell.neighbors(size));
            } else {
                cell.neighbors(size).forEach(candidates::remove);
            }
        }
        for (Position cell : cells) {
            if (!candidates.contains(cell)) {
                set(wampus, cell, Belief.ABSENT);
            } else if (anyStench) {
                set(wampus, cell, candidates.size() == 1 ? Belief.PRESENT : Belief.POSSIBLE);
            }
        }
    }

    private void inferGold() {
        gold = filled(Belief.ABSENT);
        Set<Position> candidates = new HashSet<>(cells);
        candidates.removeAll(noGold);
        candidates.removeIf(cell -> get(pit, cell) == Belief.PRESENT);
        boolean informative = false;
        for (Map.Entry<Position, Percept> entry : visited.entrySet()) {
            Set<Position> area = new HashSet<>(entry.getKey().neighbors(size));
            area.add(entry.getKey());
            if (entry.getValue().isGlitter()) {
                informative = true;
                candidates.retainAll(area);
            } else {
                candidates.removeAll(area);
            }
        }
        // Вампус сторожит золото в соседней комнате.
        Set<Position> wampusCells = wampusDead
                ? (deadWampusAt == null ? null : Set.of(deadWampusAt))
                : cells.stream().filter(cell -> get(wampus, cell) != Belief.ABSENT).collect(Collectors.toSet());
        if (wampusCells != null) {
            candidates.removeIf(cell -> wampusCells.stream().noneMatch(cell::isAdjacent));
            informative |= wampusCells.size() == 1;
        }
        for (Position cell : candidates) {
            if (!informative) {
                set(gold, cell, Belief.UNKNOWN);
            } else {
                set(gold, cell, candidates.size() == 1 ? Belief.PRESENT : Belief.POSSIBLE);
            }
        }
    }

    // ---------------- запросы к базе ----------------

    public Belief pit(Position cell) {
        return get(pit, cell);
    }

    public Belief wampus(Position cell) {
        return get(wampus, cell);
    }

    public Belief gold(Position cell) {
        return get(gold, cell);
    }

    /** Комната безопасна (OK): в ней точно нет ни ямы, ни живого Вампуса. */
    public boolean isSafe(Position cell) {
        return pit(cell) == Belief.ABSENT && wampus(cell) == Belief.ABSENT;
    }

    public boolean isVisited(Position cell) {
        return visited.containsKey(cell);
    }

    public Percept perceptAt(Position cell) {
        return visited.get(cell);
    }

    public List<Position> getCells() {
        return cells;
    }

    /**
     * Краткая запись представления о комнате, как на рисунках методички:
     * OK — безопасно, P?/P! — яма, W?/W! — Вампус, Wx — убитый Вампус, G?/G! — золото.
     */
    public String marks(Position cell) {
        List<String> tokens = new ArrayList<>();
        if (isSafe(cell)) {
            tokens.add("OK");
        }
        if (pit(cell) == Belief.POSSIBLE) {
            tokens.add("P?");
        } else if (pit(cell) == Belief.PRESENT) {
            tokens.add("P!");
        }
        if (wampus(cell) == Belief.POSSIBLE) {
            tokens.add("W?");
        } else if (wampus(cell) == Belief.PRESENT) {
            tokens.add("W!");
        } else if (cell.equals(deadWampusAt)) {
            tokens.add("Wx");
        }
        if (gold(cell) == Belief.POSSIBLE) {
            tokens.add("G?");
        } else if (gold(cell) == Belief.PRESENT) {
            tokens.add("G!");
        }
        return String.join(" ", tokens);
    }

    /** Точное местоположение живого Вампуса, если оно выведено. */
    public Position knownWampus() {
        return cells.stream().filter(cell -> wampus(cell) == Belief.PRESENT).findFirst().orElse(null);
    }

    public List<Position> cellsWhere(Function<Position, Belief> belief, Belief value) {
        return cells.stream().filter(cell -> belief.apply(cell) == value).toList();
    }

    /**
     * Вероятность погибнуть, войдя в комнату: яма или живой Вампус.
     *
     * @param pitPrior априорная вероятность ямы в комнате (доля ям в пещере)
     */
    public double risk(Position cell, double pitPrior) {
        double pitRisk = pitProbability(cell, pitPrior);
        long wampusCandidates = cells.stream().filter(c -> wampus(c) != Belief.ABSENT).count();
        double wampusRisk = switch (wampus(cell)) {
            case ABSENT -> 0;
            case PRESENT -> 1;
            case UNKNOWN, POSSIBLE -> 1.0 / Math.max(1, wampusCandidates);
        };
        return 1 - (1 - pitRisk) * (1 - wampusRisk);
    }

    /**
     * Вероятность ямы по правилу Байеса: перебираются все расстановки ям в клетках P?,
     * согласованные с каждым ветерком, и взвешиваются априорной вероятностью.
     * Перебор идёт только по связанной группе дизъюнктов, где участвует клетка.
     */
    public double pitProbability(Position cell, double pitPrior) {
        Belief belief = pit(cell);
        if (belief != Belief.POSSIBLE) {
            return belief == Belief.PRESENT ? 1 : belief == Belief.ABSENT ? 0 : pitPrior;
        }
        // дизъюнкты, уже выполненные известной ямой, ничего не ограничивают
        List<List<Position>> open = breezeClauses().stream()
                .filter(clause -> clause.stream().noneMatch(c -> pit(c) == Belief.PRESENT))
                .toList();
        List<Position> variables = new ArrayList<>(List.of(cell));
        List<List<Position>> component = new ArrayList<>();
        boolean grown = true;
        while (grown) {
            grown = false;
            for (List<Position> clause : open) {
                if (!component.contains(clause) && clause.stream().anyMatch(variables::contains)) {
                    component.add(clause);
                    clause.stream().filter(c -> !variables.contains(c)).forEach(variables::add);
                    grown = true;
                }
            }
        }
        if (component.isEmpty()) {
            return pitPrior;
        }
        if (variables.size() > MAX_ENUMERATED_CELLS) {
            return component.stream().filter(c -> c.contains(cell)).mapToDouble(c -> 1.0 / c.size()).max().orElse(pitPrior);
        }
        double withPit = 0;
        double total = 0;
        for (long mask = 0; mask < (1L << variables.size()); mask++) {
            final long assignment = mask;
            boolean consistent = component.stream().allMatch(clause ->
                    clause.stream().anyMatch(c -> (assignment & (1L << variables.indexOf(c))) != 0));
            if (!consistent) {
                continue;
            }
            int pits = Long.bitCount(mask);
            double weight = Math.pow(pitPrior, pits) * Math.pow(1 - pitPrior, variables.size() - pits);
            total += weight;
            if ((mask & 1) != 0) {
                withPit += weight;
            }
        }
        return total == 0 ? pitPrior : withPit / total;
    }

    // ---------------- описание выводов ----------------

    private record Snapshot(Belief[][] pit, Belief[][] wampus, Belief[][] gold, boolean wampusDead) {
    }

    private Snapshot snapshot() {
        return new Snapshot(copy(pit), copy(wampus), copy(gold), wampusDead);
    }

    private List<String> describeChanges(Snapshot before) {
        List<String> result = new ArrayList<>();
        if (wampusDead && !before.wampusDead()) {
            result.add(deadWampusAt == null
                    ? "Слышен крик — Вампус убит, Вампуса в пещере больше нет"
                    : "Слышен крик — Вампус в клетке " + deadWampusAt + " убит");
        }
        addChange(result, "В %s нет ямы", "В клетках %s нет ям",
                cell -> get(before.pit(), cell) == Belief.POSSIBLE && pit(cell) == Belief.ABSENT, " и ");
        addChange(result, "Клетка %s безопасна (OK)", "Клетки %s безопасны (OK)",
                cell -> !isVisited(cell) && isSafe(cell)
                        && !(get(before.pit(), cell) == Belief.ABSENT && get(before.wampus(), cell) == Belief.ABSENT), ", ");
        addChange(result, "Яма в клетке %s", "Ямы в клетках %s",
                cell -> pit(cell) == Belief.PRESENT && get(before.pit(), cell) != Belief.PRESENT, ", ");
        addChange(result, "В клетке %s может быть яма", "Яма может быть в клетках %s (или в нескольких сразу)",
                cell -> pit(cell) == Belief.POSSIBLE && get(before.pit(), cell) == Belief.UNKNOWN, " или ");
        if (!wampusDead) {
            addChange(result, "В %s нет Вампуса", "В клетках %s нет Вампуса",
                    cell -> get(before.wampus(), cell) == Belief.POSSIBLE && wampus(cell) == Belief.ABSENT, " и ");
            addChange(result, "Вампус в клетке %s", "Вампус в клетке %s",
                    cell -> wampus(cell) == Belief.PRESENT && get(before.wampus(), cell) != Belief.PRESENT, ", ");
            addChange(result, "Вампус может быть в клетке %s", "Вампус в одной из клеток %s",
                    cell -> wampus(cell) == Belief.POSSIBLE && get(before.wampus(), cell) == Belief.UNKNOWN, " или ");
        }
        addChange(result, "Золото в клетке %s", "Золото в клетке %s",
                cell -> gold(cell) == Belief.PRESENT && get(before.gold(), cell) != Belief.PRESENT, ", ");
        addChange(result, "Золото может быть в клетке %s", "Золото в одной из клеток %s",
                cell -> gold(cell) == Belief.POSSIBLE && get(before.gold(), cell) == Belief.UNKNOWN, " или ");
        if (result.isEmpty()) {
            result.add("Новых выводов нет");
        }
        return result;
    }

    private void addChange(List<String> result, String single, String plural,
                           java.util.function.Predicate<Position> changed, String separator) {
        List<String> matched = cells.stream().filter(changed).map(Position::toString).toList();
        if (matched.size() == 1) {
            result.add(String.format(single, matched.get(0)));
        } else if (matched.size() > 1) {
            result.add(String.format(plural, String.join(separator, matched)));
        }
    }

    // ---------------- служебное ----------------

    private Belief[][] filled(Belief value) {
        Belief[][] grid = new Belief[size + 1][size + 1];
        for (Belief[] row : grid) {
            Arrays.fill(row, value);
        }
        return grid;
    }

    private static Belief[][] copy(Belief[][] grid) {
        Belief[][] result = new Belief[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            result[i] = grid[i].clone();
        }
        return result;
    }

    private static Belief get(Belief[][] grid, Position cell) {
        return grid[cell.getRow()][cell.getCol()];
    }

    private static void set(Belief[][] grid, Position cell, Belief value) {
        grid[cell.getRow()][cell.getCol()] = value;
    }
}
