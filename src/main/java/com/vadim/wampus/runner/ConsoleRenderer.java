package com.vadim.wampus.runner;

import com.vadim.wampus.agent.KnowledgeBase;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.model.Position;
import com.vadim.wampus.world.Cave;
import com.vadim.wampus.world.Hunter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Рисует пещеру таблицей из рамок, как на рисунках методички. Каждая комната —
 * три строки фиксированной ширины: координаты и охотник, содержимое, сигналы.
 */
public final class ConsoleRenderer {

    private static final int CELL_WIDTH = 9;

    public static final String KNOWLEDGE_LEGEND =
            "H — охотник (стрелка — взгляд), OK — безопасно, P — яма, W — Вампус, Wx — убитый Вампус, G — золото, "
                    + "? — возможно, ! — точно; * — посещённая клетка, S — запах, B — ветерок, G — блеск";

    private ConsoleRenderer() {
    }

    /** Представление агента: что он знает о каждой комнате. */
    public static String renderKnowledge(KnowledgeBase knowledge, Hunter hunter) {
        return render(knowledge.getSize(), hunter, cell -> new String[]{
                knowledge.marks(cell),
                knowledge.isVisited(cell) ? ("* " + signals(knowledge.perceptAt(cell))).trim() : ""
        });
    }

    /** Реальная пещера — то, чего агент не видит. */
    public static String renderCave(Cave cave, Hunter hunter) {
        return render(cave.getSize(), hunter, cell -> new String[]{
                cellContent(cave, cell),
                signals(cave.perceive(cell, false, false))
        });
    }

    private static String cellContent(Cave cave, Position cell) {
        if (cave.hasPit(cell)) {
            return "яма";
        }
        if (cave.getWampus().equals(cell)) {
            return cave.isWampusAlive() ? "W" : "W убит";
        }
        if (cave.hasGold(cell)) {
            return "золото";
        }
        return "";
    }

    private static String signals(Percept percept) {
        List<String> result = new ArrayList<>();
        if (percept.isStench()) {
            result.add("S");
        }
        if (percept.isBreeze()) {
            result.add("B");
        }
        if (percept.isGlitter()) {
            result.add("G");
        }
        return String.join(" ", result);
    }

    private static String render(int size, Hunter hunter, Function<Position, String[]> content) {
        StringBuilder out = new StringBuilder();
        out.append(border('┌', '┬', '┐', size));
        for (int row = 1; row <= size; row++) {
            String[] header = new String[size];
            String[] middle = new String[size];
            String[] bottom = new String[size];
            for (int col = 1; col <= size; col++) {
                Position cell = new Position(row, col);
                String hunterMark = hunter.getPosition().equals(cell)
                        ? (hunter.isAlive() ? "H" + hunter.getDirection().getArrow() : "H✝")
                        : "";
                header[col - 1] = String.format("%-6s%3s", " " + cell, hunterMark + " ");
                String[] lines = content.apply(cell);
                middle[col - 1] = " " + lines[0];
                bottom[col - 1] = " " + lines[1];
            }
            out.append(line(header)).append(line(middle)).append(line(bottom));
            out.append(row < size ? border('├', '┼', '┤', size) : border('└', '┴', '┘', size));
        }
        return out.toString();
    }

    private static String line(String[] parts) {
        StringBuilder builder = new StringBuilder("│");
        for (String part : parts) {
            String text = part.length() > CELL_WIDTH ? part.substring(0, CELL_WIDTH) : part;
            builder.append(text).append(" ".repeat(CELL_WIDTH - text.length())).append('│');
        }
        return builder.append(System.lineSeparator()).toString();
    }

    private static String border(char left, char middle, char right, int size) {
        StringBuilder builder = new StringBuilder().append(left);
        for (int col = 0; col < size; col++) {
            builder.append("─".repeat(CELL_WIDTH)).append(col < size - 1 ? middle : right);
        }
        return builder.append(System.lineSeparator()).toString();
    }
}
