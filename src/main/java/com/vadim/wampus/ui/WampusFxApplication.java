package com.vadim.wampus.ui;

import com.vadim.wampus.WampusApplication;
import com.vadim.wampus.agent.Decision;
import com.vadim.wampus.config.WampusProperties;
import com.vadim.wampus.game.Observation;
import com.vadim.wampus.game.StepReport;
import com.vadim.wampus.game.WampusGame;
import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.GameStatus;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.world.CaveGenerator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/** JavaFX интерфейс: реальная пещера, представление агента, журнал рассуждений, автоматический и ручной режим. */
public class WampusFxApplication extends Application {

    private static final double MIN_TICK_MS = 150;
    private static final double MAX_TICK_MS = 1500;
    private static final double DEFAULT_TICK_MS = 600;

    private final List<Button> gameButtons = new ArrayList<>();

    private WampusGame game;
    private WampusProperties properties;

    private TabPane tabPane;
    private Tab gameTab;
    private BoardView caveBoard;
    private BoardView knowledgeBoard;
    private CheckBox revealCave;
    private TextArea journal;
    private Label stepLabel;
    private Label statsLabel;
    private Label perceptLabel;
    private Label conclusionLabel;
    private Label adviceLabel;
    private Label statusLabel;
    private Button automaticButton;
    private double tickMillis = DEFAULT_TICK_MS;
    private Timeline timer;

    private TextField sizeField;
    private TextField pitRatioField;
    private TextField seedField;
    private TextField maxRiskField;
    private TextField maxStepsField;
    private CheckBox demoBox;
    private Label settingsStatus;

    @Override
    public void start(Stage stage) {
        game = WampusApplication.getBean(WampusGame.class);
        properties = WampusApplication.getBean(WampusProperties.class);

        gameTab = createGameTab();
        tabPane = new TabPane(gameTab, createSettingsTab());

        Label title = new Label("Мир Вампуса — логический агент");
        title.getStyleClass().add("app-title");
        VBox header = new VBox(title);
        header.setPadding(new Insets(16, 20, 4, 20));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(header);
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1360, 880);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        // фильтр, а не обработчик: иначе журнал (TextArea) в фокусе забирает Enter и стрелки
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKey);

        stage.setTitle("Мир Вампуса");
        stage.setScene(scene);
        stage.setMinWidth(1050);
        stage.setMinHeight(720);
        stage.setOnCloseRequest(event -> {
            stopTimer();
            WampusApplication.closeContext();
            Platform.exit();
        });
        stage.setMaximized(true);
        stage.show();
        newGame();
    }

    // ---------------- вкладка "Игра" ----------------

    private Tab createGameTab() {
        stepLabel = new Label("Step 0");
        stepLabel.getStyleClass().add("step-label");
        statsLabel = new Label();
        statsLabel.getStyleClass().add("stats-label");
        HBox infoBar = new HBox(28, stepLabel, statsLabel);
        infoBar.setAlignment(Pos.CENTER_LEFT);

        caveBoard = new BoardView();
        revealCave = new CheckBox("показать");
        revealCave.setSelected(true);
        revealCave.setFocusTraversable(false);
        revealCave.setTooltip(new Tooltip("Скрыть пещеру, чтобы видеть только то, что знает агент"));
        revealCave.selectedProperty().addListener((obs, oldV, newV) -> redraw());
        VBox cavePanel = boardPanel("Реальная пещера", revealCave, caveBoard);

        knowledgeBoard = new BoardView();
        VBox knowledgePanel = boardPanel("Представление агента", null, knowledgeBoard);

        journal = new TextArea();
        journal.setEditable(false);
        journal.setWrapText(true);
        journal.setFocusTraversable(false);
        journal.getStyleClass().add("journal");
        VBox.setVgrow(journal, Priority.ALWAYS);
        Label journalTitle = new Label("Журнал рассуждений");
        journalTitle.getStyleClass().add("panel-title");
        VBox journalPanel = new VBox(8, journalTitle, journal);
        journalPanel.setPrefWidth(400);
        journalPanel.setMinWidth(300);

        HBox boards = new HBox(16, cavePanel, knowledgePanel, journalPanel);
        HBox.setHgrow(cavePanel, Priority.ALWAYS);
        HBox.setHgrow(knowledgePanel, Priority.ALWAYS);
        VBox.setVgrow(boards, Priority.ALWAYS);

        perceptLabel = new Label();
        perceptLabel.getStyleClass().add("percept-label");
        conclusionLabel = new Label();
        conclusionLabel.setWrapText(true);
        conclusionLabel.getStyleClass().add("reasoning-label");
        adviceLabel = new Label();
        adviceLabel.setWrapText(true);
        adviceLabel.getStyleClass().add("advice-label");
        VBox reasoning = new VBox(4, perceptLabel, conclusionLabel, adviceLabel);
        reasoning.getStyleClass().add("reasoning-box");

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        Label legend = new Label("OK — безопасно  •  P? / P! — яма возможна / точно  •  W? / W! — Вампус  •  "
                + "Wx — убитый Вампус  •  G? / G! — золото  •  серая клетка — посещена");
        legend.getStyleClass().add("legend-label");

        VBox content = new VBox(12, infoBar, boards, reasoning, statusLabel, createControls(), legend);
        content.setPadding(new Insets(16, 20, 16, 20));

        Tab tab = new Tab("▦  Игра", content);
        tab.setClosable(false);
        return tab;
    }

    private VBox boardPanel(String title, CheckBox option, BoardView board) {
        Label caption = new Label(title);
        caption.getStyleClass().add("panel-title");
        HBox header = new HBox(12, caption);
        header.setAlignment(Pos.CENTER_LEFT);
        if (option != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            header.getChildren().addAll(spacer, option);
        }
        VBox.setVgrow(board, Priority.ALWAYS);
        VBox panel = new VBox(8, header, board);
        panel.setMinWidth(240);
        return panel;
    }

    private HBox createControls() {
        automaticButton = button("▶  Авто", "button-play", "Агент играет сам, пока игра не закончится");
        automaticButton.setOnAction(event -> toggleAutomatic());

        Label speedCaption = new Label("Скорость:");
        speedCaption.getStyleClass().add("field-label");
        Slider speedSlider = new Slider(MIN_TICK_MS, MAX_TICK_MS, MAX_TICK_MS - DEFAULT_TICK_MS + MIN_TICK_MS);
        speedSlider.setPrefWidth(110);
        speedSlider.setFocusTraversable(false);
        speedSlider.getStyleClass().add("speed-slider");
        // ползунок инвертирован: вправо - быстрее (меньше задержка между шагами)
        speedSlider.valueProperty().addListener((obs, oldV, newV) -> {
            tickMillis = MAX_TICK_MS - newV.doubleValue() + MIN_TICK_MS;
            if (timer != null && timer.getStatus() == Timeline.Status.RUNNING) {
                timer.stop();
                startTimer();
            }
        });
        HBox speedGroup = new HBox(8, speedCaption, speedSlider);
        speedGroup.getStyleClass().add("steps-group");
        speedGroup.setAlignment(Pos.CENTER);

        Button agentStep = button("⏭  Ход агента", "button-step", "Агент выполняет своё решение (Enter)");
        agentStep.setOnAction(event -> agentStep());

        Button forward = manualButton("↑  Вперёд", Action.FORWARD, "Шаг вперёд (↑ или W)");
        Button left = manualButton("↺  Налево", Action.TURN_LEFT, "Поворот на 90° налево (← или A)");
        Button right = manualButton("↻  Направо", Action.TURN_RIGHT, "Поворот на 90° направо (→ или D)");
        Button shoot = manualButton("➶  Выстрел", Action.SHOOT, "Выстрел вперёд, стрела одна (пробел)");
        Button grab = manualButton("★  Взять", Action.GRAB, "Взять золото (G)");
        Label manualCaption = new Label("Вручную:");
        manualCaption.getStyleClass().add("field-label");
        HBox manualGroup = new HBox(6, manualCaption, forward, left, right, shoot, grab);
        manualGroup.getStyleClass().add("steps-group");
        manualGroup.setAlignment(Pos.CENTER);

        Button reset = button("🔄  Новая пещера", "button-reset", "Создать новую пещеру с текущими настройками (N)");
        reset.setOnAction(event -> newGame());
        gameButtons.remove(reset);

        HBox bar = new HBox(14, automaticButton, speedGroup, agentStep, manualGroup, reset);
        bar.getStyleClass().add("control-bar");
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private Button manualButton(String text, Action action, String tooltip) {
        Button button = button(text, "button-manual", tooltip);
        button.setOnAction(event -> manualStep(action));
        return button;
    }

    private Button button(String text, String styleClass, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().addAll("action-button", styleClass);
        button.setTooltip(new Tooltip(tooltip));
        // кнопки не забирают фокус, чтобы пробел и Enter работали как горячие клавиши игры
        button.setFocusTraversable(false);
        gameButtons.add(button);
        return button;
    }

    private void handleKey(KeyEvent event) {
        boolean typing = event.getTarget() instanceof TextInputControl input && input.isEditable();
        if (tabPane.getSelectionModel().getSelectedItem() != gameTab || typing) {
            return;
        }
        switch (event.getCode()) {
            case UP, W -> manualStep(Action.FORWARD);
            case LEFT, A -> manualStep(Action.TURN_LEFT);
            case RIGHT, D -> manualStep(Action.TURN_RIGHT);
            case SPACE -> manualStep(Action.SHOOT);
            case G -> manualStep(Action.GRAB);
            case ENTER -> agentStep();
            case N -> newGame();
            default -> {
                return;
            }
        }
        event.consume();
    }

    // ---------------- вкладка "Настройки" ----------------

    private Tab createSettingsTab() {
        GridPane caveGrid = settingsGrid();
        sizeField = addField(caveGrid, "Размер поля N (" + CaveGenerator.MIN_SIZE + "…" + CaveGenerator.MAX_SIZE + ")",
                Integer.toString(properties.getSize()));
        pitRatioField = addField(caveGrid, "Доля комнат с ямами (0…0.5)", Double.toString(properties.getPitRatio()));
        seedField = addField(caveGrid, "Зерно генератора (0 — случайно)", Long.toString(properties.getSeed()));
        demoBox = new CheckBox("Пещера из методички (4x4)");
        demoBox.setSelected(properties.isDemo());
        caveGrid.add(demoBox, 0, caveGrid.getRowCount(), 2, 1);

        GridPane agentGrid = settingsGrid();
        maxRiskField = addField(agentGrid, "Допустимый риск гибели (0…1, 0 — не рисковать)",
                Double.toString(properties.getMaxRisk()));
        maxStepsField = addField(agentGrid, "Лимит шагов", Integer.toString(properties.getMaxSteps()));

        VBox sections = new VBox(12, settingsSection("Пещера", caveGrid), settingsSection("Агент", agentGrid));

        Button apply = new Button("🔄  Применить настройки и создать новую пещеру");
        apply.getStyleClass().addAll("action-button", "button-reset");
        apply.setOnAction(event -> {
            if (newGame()) {
                tabPane.getSelectionModel().select(gameTab);
            }
        });
        HBox applyBar = new HBox(apply);
        applyBar.setAlignment(Pos.CENTER);
        applyBar.setPadding(new Insets(14, 0, 4, 0));

        settingsStatus = new Label();
        settingsStatus.getStyleClass().addAll("status-label", "status-error");

        VBox content = new VBox(8, sections, applyBar, settingsStatus);
        content.setPadding(new Insets(16, 20, 16, 20));
        content.setMaxWidth(720);

        Tab tab = new Tab("⚙  Настройки", content);
        tab.setClosable(false);
        return tab;
    }

    private GridPane settingsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(7);
        grid.setPadding(new Insets(10, 4, 4, 4));
        return grid;
    }

    private TitledPane settingsSection(String title, GridPane grid) {
        TitledPane pane = new TitledPane(title, grid);
        pane.setCollapsible(false);
        pane.getStyleClass().add("settings-section");
        return pane;
    }

    private TextField addField(GridPane grid, String label, String value) {
        int row = grid.getRowCount();
        TextField field = new TextField(value);
        field.setPrefColumnCount(8);
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        grid.add(fieldLabel, 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private void applySettings() {
        properties.setSize(parseInt(sizeField, "размер поля"));
        properties.setPitRatio(parseDouble(pitRatioField, "доля ям"));
        properties.setSeed(parseLong(seedField, "зерно генератора"));
        properties.setDemo(demoBox.isSelected());
        double maxRisk = parseDouble(maxRiskField, "допустимый риск");
        if (maxRisk < 0 || maxRisk > 1) {
            throw new IllegalArgumentException("допустимый риск должен быть от 0 до 1");
        }
        properties.setMaxRisk(maxRisk);
        int maxSteps = parseInt(maxStepsField, "лимит шагов");
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("лимит шагов должен быть больше нуля");
        }
        properties.setMaxSteps(maxSteps);
    }

    private static int parseInt(TextField field, String name) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("введите целое число для «" + name + "»");
        }
    }

    private static long parseLong(TextField field, String name) {
        try {
            return Long.parseLong(field.getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("введите целое число для «" + name + "»");
        }
    }

    private static double parseDouble(TextField field, String name) {
        try {
            double value = Double.parseDouble(field.getText().trim().replace(',', '.'));
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("введите число для «" + name + "»");
        }
    }

    // ---------------- игра ----------------

    /** Создаёт новую пещеру по настройкам. При ошибке оставляет текущую игру и показывает причину. */
    private boolean newGame() {
        stopTimer();
        try {
            applySettings();
            game.newGame();
        } catch (IllegalArgumentException exception) {
            settingsStatus.setText("Ошибка настройки: " + exception.getMessage());
            tabPane.getSelectionModel().select(1);
            return false;
        }
        settingsStatus.setText("");
        journal.clear();
        journal.appendText(String.format("Пещера %dx%d, ям: %d%s. Start%n",
                game.getCave().getSize(), game.getCave().getSize(), game.getCave().getPits().size(),
                properties.isDemo() ? " (мир из методички)" : ""));
        setStatus("Новая пещера создана. Агент может играть сам, а можно ходить вручную — агент подскажет.", null);
        refresh();
        return true;
    }

    private boolean isPlayable() {
        return game.getStatus() != null && !game.getStatus().isFinished();
    }

    private void agentStep() {
        if (!isPlayable()) {
            stopTimer();
            return;
        }
        handle(game.agentStep());
    }

    private void manualStep(Action action) {
        if (!isPlayable()) {
            return;
        }
        stopTimer();
        handle(game.manualStep(action));
    }

    private void handle(StepReport report) {
        journal.appendText(formatReport(report));
        if (report.getStatus().isFinished()) {
            stopTimer();
            String text = String.format("Итог: %s. Шагов: %d, очки: %d",
                    report.getStatus().getTitle(), game.getStep(), report.getScore());
            setStatus(text, report.getStatus() == GameStatus.WON ? "status-won" : "status-lost");
            journal.appendText(text + System.lineSeparator());
        } else {
            setStatus(String.join("  •  ", report.getEvents()), null);
        }
        refresh();
    }

    private String formatReport(StepReport report) {
        Observation observation = report.getObservation();
        StringBuilder text = new StringBuilder();
        text.append(System.lineSeparator())
                .append("Step ").append(observation.getStep())
                .append(" · (").append(observation.getPosition()).append(") ")
                .append(observation.getDirection().getArrow()).append(System.lineSeparator())
                .append("Измерение: ").append(observation.getPercept()).append(System.lineSeparator())
                .append("Вывод: «").append(String.join("; ", observation.getConclusions())).append("»")
                .append(System.lineSeparator());
        Decision advice = observation.getAdvice();
        if (report.isManual()) {
            text.append("Совет агента: ").append(advice.getExplanation()).append(System.lineSeparator())
                    .append("Вы: ").append(report.getExecuted().get(0).getTitle()).append(System.lineSeparator());
        } else {
            text.append("Решение: ").append(advice.getExplanation()).append(System.lineSeparator())
                    .append("Действия: ").append(advice.describeActions()).append(System.lineSeparator());
        }
        report.getEvents().forEach(event -> text.append("  • ").append(event).append(System.lineSeparator()));
        return text.toString();
    }

    /** Делает измерение для следующего шага, чтобы сразу показать сигналы, выводы и совет агента. */
    private void refresh() {
        boolean finished = game.getStatus().isFinished();
        if (!finished) {
            Observation observation = game.observe();
            stepLabel.setText("Step " + observation.getStep());
            perceptLabel.setText("Измерение " + Percept.SENSORS + ": " + observation.getPercept());
            conclusionLabel.setText("Логический вывод: «" + String.join("; ", observation.getConclusions()) + "»");
            Decision advice = observation.getAdvice();
            adviceLabel.setText("Решение агента: " + advice.getExplanation() + "  [" + advice.describeActions() + "]");
        } else {
            adviceLabel.setText("Игра окончена: " + game.getStatus().getTitle() + ". Нажмите «Новая пещера».");
        }
        gameButtons.forEach(button -> button.setDisable(finished));
        statsLabel.setText(String.format("Очки: %d  •  Стрелы: %d  •  Вампус: %s  •  Золото: %s",
                game.getScore(), game.getHunter().getArrows(),
                game.getCave().isWampusAlive() ? "жив" : "убит",
                game.getHunter().isHasGold() ? "у охотника" : "не найдено"));
        redraw();
    }

    private void redraw() {
        if (game.getCave() == null) {
            return;
        }
        int size = game.getCave().getSize();
        boolean revealed = revealCave.isSelected() || game.getStatus().isFinished();
        caveBoard.show(size, CellPainters.cave(game.getCave(), game.getHunter(), game.getKnowledge(), revealed));
        knowledgeBoard.show(size, CellPainters.knowledge(game.getKnowledge(), game.getHunter()));
    }

    private void setStatus(String text, String styleClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("status-error", "status-won", "status-lost");
        if (styleClass != null) {
            statusLabel.getStyleClass().add(styleClass);
        }
    }

    // ---------------- автоматический режим ----------------

    private void toggleAutomatic() {
        if (timer != null && timer.getStatus() == Timeline.Status.RUNNING) {
            stopTimer();
        } else if (isPlayable()) {
            startTimer();
            automaticButton.setText("⏸  Остановить");
            automaticButton.getStyleClass().add("running");
        }
    }

    private void startTimer() {
        timer = new Timeline(new KeyFrame(Duration.millis(tickMillis), tick -> agentStep()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
        }
        if (automaticButton != null) {
            automaticButton.setText("▶  Авто");
            automaticButton.getStyleClass().remove("running");
        }
    }
}
