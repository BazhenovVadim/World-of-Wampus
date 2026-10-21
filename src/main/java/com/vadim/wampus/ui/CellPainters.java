package com.vadim.wampus.ui;

import com.vadim.wampus.agent.KnowledgeBase;
import com.vadim.wampus.model.Belief;
import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.model.Position;
import com.vadim.wampus.world.Cave;
import com.vadim.wampus.world.Hunter;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;

/** Отрисовка комнат: реальная пещера в стиле рисунка из методички и представление агента. */
public final class CellPainters {

    private static final String FONT = "Segoe UI";

    private static final Color HUNTER = Color.web("#d62828");
    private static final Color COORDS = Color.web("#6b7785");
    private static final Color VISITED = Color.web("#c9ced6");
    private static final Color SAFE = Color.web("#e4f4e8");
    private static final Color DANGER = Color.web("#f6d2cd");
    private static final Color MAYBE = Color.web("#fff0c4");
    private static final Color HIDDEN = Color.web("#e6eaf0");

    private CellPainters() {
    }

    /** Реальная пещера. Если {@code revealed == false}, видны только охотник и посещённые агентом комнаты. */
    public static BoardView.CellPainter cave(Cave cave, Hunter hunter, KnowledgeBase knowledge, boolean revealed) {
        return (g, cell, x, y, size) -> {
            boolean visible = revealed || knowledge.isVisited(cell);
            g.setFill(visible ? Color.web("#fbfcfd") : HIDDEN);
            g.fillRect(x, y, size, size);
            coords(g, cell, x, y, size);
            if (!visible) {
                text(g, "?", x + size / 2, y + size / 2, size * 0.3, FontWeight.BOLD, Color.web("#b4bcc8"));
            } else {
                Percept percept = cave.perceive(cell, false, false);
                if (percept.isStench()) {
                    cloud(g, x + size * 0.27, y + size * 0.3, size);
                }
                if (percept.isGlitter()) {
                    sparkle(g, x + size * 0.8, y + size * 0.22, size * 0.11);
                }
                if (percept.isBreeze()) {
                    breeze(g, x + size * 0.5, y + size * 0.82, size);
                }
                boolean withHunter = hunter.getPosition().equals(cell);
                double objectX = x + size * (withHunter ? 0.66 : 0.5);
                double objectY = y + size * 0.56;
                if (cave.hasPit(cell)) {
                    pit(g, objectX, objectY, size);
                } else if (cave.getWampus().equals(cell)) {
                    wampus(g, objectX, objectY, size, cave.isWampusAlive());
                } else if (cave.hasGold(cell)) {
                    gold(g, objectX, objectY, size);
                }
            }
            if (hunter.getPosition().equals(cell)) {
                hunter(g, hunter, x + size * 0.3, y + size * 0.52, size);
            }
        };
    }

    /** Представление агента: OK, P?, P!, W?, W!, G?, G! и сигналы в посещённых комнатах. */
    public static BoardView.CellPainter knowledge(KnowledgeBase knowledge, Hunter hunter) {
        return (g, cell, x, y, size) -> {
            g.setFill(background(knowledge, cell));
            g.fillRect(x, y, size, size);
            coords(g, cell, x, y, size);

            double centerX = x + size / 2;
            List<Mark> marks = marks(knowledge, cell);
            double lineHeight = size * 0.19;
            double startY = y + size * 0.48 - (marks.size() - 1) * lineHeight / 2;
            for (int i = 0; i < marks.size(); i++) {
                Mark mark = marks.get(i);
                text(g, mark.text(), centerX, startY + i * lineHeight, size * 0.17, FontWeight.BOLD, mark.color());
            }

            if (knowledge.isVisited(cell)) {
                String signals = signals(knowledge.perceptAt(cell), size >= 90);
                if (!signals.isEmpty()) {
                    // шрифт подбирается так, чтобы строка помещалась в клетку
                    double fontSize = Math.min(size * (size >= 90 ? 0.1 : 0.16), size * 0.9 / (signals.length() * 0.55));
                    text(g, signals, centerX, y + size * 0.86, fontSize, FontWeight.NORMAL, Color.web("#38424f"));
                }
            }
            if (hunter.getPosition().equals(cell)) {
                double inset = Math.max(2, size * 0.04);
                g.setStroke(HUNTER);
                g.setLineWidth(Math.max(1.5, size * 0.025));
                g.strokeRect(x + inset, y + inset, size - 2 * inset, size - 2 * inset);
                hunter(g, hunter, x + size * 0.8, y + size * 0.24, size * 0.5);
            }
        };
    }

    // ---------------- представление агента ----------------

    private record Mark(String text, Color color) {
    }

    private static List<Mark> marks(KnowledgeBase knowledge, Position cell) {
        List<Mark> marks = new ArrayList<>();
        if (knowledge.isSafe(cell)) {
            marks.add(new Mark("OK", Color.web("#1f7a3a")));
        }
        String dangers = String.join(" ", danger(knowledge.pit(cell), "P"), danger(knowledge.wampus(cell), "W")).trim();
        if (cell.equals(knowledge.getDeadWampusAt())) {
            dangers = (dangers + " Wx").trim();
        }
        if (!dangers.isEmpty()) {
            boolean certain = dangers.contains("!");
            marks.add(new Mark(dangers, certain ? Color.web("#b3261e") : Color.web("#c0392b")));
        }
        String gold = danger(knowledge.gold(cell), "G");
        if (!gold.isEmpty()) {
            marks.add(new Mark(gold, Color.web("#b07d00")));
        }
        return marks;
    }

    private static String danger(Belief belief, String letter) {
        return switch (belief) {
            case POSSIBLE -> letter + "?";
            case PRESENT -> letter + "!";
            default -> "";
        };
    }

    private static Color background(KnowledgeBase knowledge, Position cell) {
        if (knowledge.isVisited(cell)) {
            return VISITED;
        }
        if (knowledge.pit(cell) == Belief.PRESENT || knowledge.wampus(cell) == Belief.PRESENT) {
            return DANGER;
        }
        if (knowledge.isSafe(cell)) {
            return SAFE;
        }
        if (knowledge.pit(cell) == Belief.POSSIBLE || knowledge.wampus(cell) == Belief.POSSIBLE) {
            return MAYBE;
        }
        return Color.WHITE;
    }

    private static String signals(Percept percept, boolean words) {
        List<String> result = new ArrayList<>();
        if (percept.isStench()) {
            result.add(words ? "stench" : "S");
        }
        if (percept.isBreeze()) {
            result.add(words ? "breeze" : "B");
        }
        if (percept.isGlitter()) {
            result.add(words ? "glitter" : "G");
        }
        return String.join(" ", result);
    }

    // ---------------- объекты пещеры ----------------

    private static void coords(GraphicsContext g, Position cell, double x, double y, double size) {
        if (size < 34) {
            return;
        }
        g.setFill(COORDS);
        g.setFont(Font.font(FONT, FontWeight.NORMAL, Math.max(9, size * 0.11)));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        g.fillText(cell.toString(), x + size * 0.06, y + size * 0.05);
    }

    private static void cloud(GraphicsContext g, double cx, double cy, double size) {
        double r = size * 0.075;
        g.setFill(Color.web("#7cb9ec"));
        g.setStroke(Color.web("#1f6fb2"));
        g.setLineWidth(Math.max(1, size * 0.012));
        double[][] puffs = {{-1.3, 0.2}, {-0.4, -0.45}, {0.6, -0.35}, {1.3, 0.25}, {0, 0.45}};
        for (double[] puff : puffs) {
            g.strokeOval(cx + puff[0] * r - r, cy + puff[1] * r - r, 2 * r, 2 * r);
        }
        for (double[] puff : puffs) {
            g.fillOval(cx + puff[0] * r - r, cy + puff[1] * r - r, 2 * r, 2 * r);
        }
    }

    private static void sparkle(GraphicsContext g, double cx, double cy, double r) {
        double inner = r * 0.32;
        double[] xs = new double[8];
        double[] ys = new double[8];
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i - Math.PI / 2;
            double radius = i % 2 == 0 ? r : inner;
            xs[i] = cx + Math.cos(angle) * radius;
            ys[i] = cy + Math.sin(angle) * radius;
        }
        g.setFill(Color.web("#f5c518"));
        g.fillPolygon(xs, ys, 8);
        g.setStroke(Color.web("#c99700"));
        g.setLineWidth(1);
        g.strokePolygon(xs, ys, 8);
    }

    private static void breeze(GraphicsContext g, double cx, double cy, double size) {
        double width = size * 0.62;
        double height = size * 0.17;
        g.setFill(Color.web("#dcdfe3"));
        g.setStroke(Color.web("#8a9099"));
        g.setLineWidth(Math.max(1, size * 0.012));
        g.fillRoundRect(cx - width / 2, cy - height / 2, width, height, height * 0.6, height * 0.6);
        g.strokeRoundRect(cx - width / 2, cy - height / 2, width, height, height * 0.6, height * 0.6);
        if (size >= 70) {
            text(g, "ветерок", cx, cy, size * 0.09, FontWeight.NORMAL, Color.web("#3d434b"));
        }
    }

    private static void pit(GraphicsContext g, double cx, double cy, double size) {
        double width = size * 0.5;
        double height = size * 0.3;
        g.setFill(Color.web("#141414"));
        g.fillOval(cx - width / 2, cy - height / 2, width, height);
        g.setStroke(Color.web("#4a4a4a"));
        g.setLineWidth(Math.max(1, size * 0.015));
        g.strokeOval(cx - width / 2, cy - height / 2, width, height);
        if (size >= 50) {
            text(g, "яма", cx, cy, size * 0.12, FontWeight.BOLD, Color.WHITE);
        }
    }

    private static void wampus(GraphicsContext g, double cx, double cy, double size, boolean alive) {
        text(g, "W", cx, cy, size * 0.36, FontWeight.EXTRA_BOLD, alive ? Color.web("#111111") : Color.web("#9aa0a6"));
        if (!alive) {
            double d = size * 0.15;
            g.setStroke(Color.web("#d62828"));
            g.setLineWidth(Math.max(1.5, size * 0.025));
            g.strokeLine(cx - d, cy - d, cx + d, cy + d);
            g.strokeLine(cx - d, cy + d, cx + d, cy - d);
        }
    }

    private static void gold(GraphicsContext g, double cx, double cy, double size) {
        double width = size * 0.3;
        double height = size * 0.14;
        g.setFill(Color.web("#f7d046"));
        g.fillRoundRect(cx - width / 2, cy - height / 2, width, height, 4, 4);
        g.setStroke(Color.web("#e09b00"));
        g.setLineWidth(Math.max(1.5, size * 0.025));
        g.strokeRoundRect(cx - width / 2, cy - height / 2, width, height, 4, 4);
    }

    private static void hunter(GraphicsContext g, Hunter hunter, double cx, double cy, double size) {
        Color color = hunter.isAlive() ? HUNTER : Color.web("#555555");
        text(g, "H", cx, cy, size * 0.3, FontWeight.EXTRA_BOLD, color);
        if (!hunter.isAlive()) {
            return;
        }
        Direction direction = hunter.getDirection();
        double gap = size * 0.17;
        double length = size * 0.13;
        double startX = cx + direction.getColDelta() * gap;
        double startY = cy + direction.getRowDelta() * gap;
        double endX = startX + direction.getColDelta() * length;
        double endY = startY + direction.getRowDelta() * length;
        g.setStroke(color);
        g.setFill(color);
        g.setLineWidth(Math.max(1.5, size * 0.022));
        g.strokeLine(startX, startY, endX, endY);
        double head = size * 0.05;
        double px = -direction.getRowDelta();
        double py = direction.getColDelta();
        g.fillPolygon(
                new double[]{endX + direction.getColDelta() * head, endX + px * head, endX - px * head},
                new double[]{endY + direction.getRowDelta() * head, endY + py * head, endY - py * head},
                3);
    }

    private static void text(GraphicsContext g, String text, double cx, double cy, double fontSize,
                             FontWeight weight, Color color) {
        g.setFill(color);
        g.setFont(Font.font(FONT, weight, Math.max(8, fontSize)));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, cx, cy);
    }
}
