package com.vadim.wampus.ui;

import com.vadim.wampus.model.Position;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/** Квадратное поле N * N на Canvas. Перерисовывается при изменении размера окна. */
public class BoardView extends Pane {

    private static final double PADDING = 8;
    private static final Color GRID_COLOR = Color.web("#3b5b86");

    /** Рисует содержимое одной комнаты в квадрате (x, y, size). */
    @FunctionalInterface
    public interface CellPainter {
        void paint(GraphicsContext g, Position cell, double x, double y, double size);
    }

    private final Canvas canvas = new Canvas();
    private int boardSize;
    private CellPainter painter;

    public BoardView() {
        canvas.setManaged(false);
        getChildren().add(canvas);
        getStyleClass().add("board-frame");
        setMinSize(160, 160);
        setPrefSize(420, 420);
        widthProperty().addListener((obs, oldV, newV) -> redraw());
        heightProperty().addListener((obs, oldV, newV) -> redraw());
    }

    public void show(int boardSize, CellPainter painter) {
        this.boardSize = boardSize;
        this.painter = painter;
        redraw();
    }

    public void redraw() {
        double width = getWidth();
        double height = getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);
        if (painter == null || boardSize == 0) {
            return;
        }
        double cell = Math.floor(Math.min(width - 2 * PADDING, height - 2 * PADDING) / boardSize);
        if (cell <= 4) {
            return;
        }
        double offsetX = (width - cell * boardSize) / 2;
        double offsetY = (height - cell * boardSize) / 2;

        for (int row = 1; row <= boardSize; row++) {
            for (int col = 1; col <= boardSize; col++) {
                double x = offsetX + (col - 1) * cell;
                double y = offsetY + (row - 1) * cell;
                g.save();
                g.beginPath();
                g.rect(x, y, cell, cell);
                g.clip();
                painter.paint(g, new Position(row, col), x, y, cell);
                g.restore();
            }
        }

        g.setStroke(GRID_COLOR);
        g.setLineWidth(Math.max(1.5, cell * 0.03));
        for (int i = 0; i <= boardSize; i++) {
            g.strokeLine(offsetX + i * cell, offsetY, offsetX + i * cell, offsetY + boardSize * cell);
            g.strokeLine(offsetX, offsetY + i * cell, offsetX + boardSize * cell, offsetY + i * cell);
        }
    }
}
