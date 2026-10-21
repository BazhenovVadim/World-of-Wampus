package com.vadim.wampus.runner;

import com.vadim.wampus.config.WampusProperties;
import com.vadim.wampus.config.WampusProperties.ControlMode;
import com.vadim.wampus.game.Observation;
import com.vadim.wampus.game.StepReport;
import com.vadim.wampus.game.WampusGame;
import com.vadim.wampus.model.Action;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.world.Cave;
import com.vadim.wampus.world.CaveGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Scanner;

@Component
@RequiredArgsConstructor
public class ConsoleRunner implements CommandLineRunner {

    /** Команды ручного режима. Пустая строка (Enter) — ход агента. */
    private static final Map<String, Action> COMMANDS = Map.of(
            "f", Action.FORWARD,
            "l", Action.TURN_LEFT,
            "r", Action.TURN_RIGHT,
            "s", Action.SHOOT,
            "g", Action.GRAB);

    private final WampusGame game;
    private final WampusProperties properties;

    @Override
    public void run(String... args) {
        try {
            game.newGame();
        } catch (IllegalArgumentException exception) {
            System.out.println("Ошибка настройки: " + exception.getMessage());
            return;
        }
        printHeader();

        Scanner scanner = new Scanner(System.in);
        while (!game.getStatus().isFinished()) {
            Observation observation = game.observe();
            printObservation(observation);

            StepReport report;
            if (properties.getMode() == ControlMode.AUTO) {
                report = game.agentStep();
            } else {
                String command = readCommand(scanner);
                if (command == null) {
                    System.out.println("Игра прервана пользователем.");
                    break;
                }
                report = command.isEmpty() ? game.agentStep() : game.manualStep(COMMANDS.get(command));
            }
            printReport(report);
        }
        printResult();
    }

    private void printHeader() {
        Cave cave = game.getCave();
        System.out.printf("Мир Вампуса: поле %dx%d, ям: %d, режим: %s%s%n",
                cave.getSize(), cave.getSize(), cave.getPits().size(),
                properties.getMode() == ControlMode.AUTO ? "агент играет сам" : "ручной, агент подсказывает",
                properties.isDemo() ? " (мир из методички)" : "");
        System.out.println("Обозначения: " + ConsoleRenderer.KNOWLEDGE_LEGEND);
        if (properties.isShowWorld()) {
            System.out.println("Реальная пещера:");
            System.out.print(ConsoleRenderer.renderCave(cave, game.getHunter()));
        }
        System.out.println("Start");
    }

    private void printObservation(Observation observation) {
        System.out.println();
        System.out.println("Step " + observation.getStep());
        System.out.println("Агент выполняет измерение: " + Percept.SENSORS);
        System.out.println("Результат измерения: " + observation.getPercept() + ";");
        System.out.println("Логический вывод: «" + String.join("; ", observation.getConclusions()) + "»");
        System.out.println("Представление:");
        System.out.print(ConsoleRenderer.renderKnowledge(game.getKnowledge(), game.getHunter()));
        String prefix = properties.getMode() == ControlMode.AUTO ? "Решение: " : "Совет агента: ";
        System.out.println(prefix + observation.getAdvice().getExplanation());
        System.out.println("Действия: " + observation.getAdvice().describeActions());
    }

    /** Возвращает команду из {@link #COMMANDS}, пустую строку для хода агента или null для выхода. */
    private String readCommand(Scanner scanner) {
        while (true) {
            System.out.print("Ваш ход [f — вперёд, l — налево, r — направо, s — выстрел, g — взять, "
                    + "Enter — ход агента, q — выход]: ");
            if (!scanner.hasNextLine()) {
                return null;
            }
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("q")) {
                return null;
            }
            if (input.isEmpty() || COMMANDS.containsKey(input)) {
                return input;
            }
            System.out.println("Неизвестная команда: " + input);
        }
    }

    private void printReport(StepReport report) {
        if (report.isManual()) {
            System.out.println("Вы: " + report.getExecuted().get(0).getTitle());
        } else if (properties.getMode() == ControlMode.MANUAL) {
            System.out.println("Ход агента:");
        }
        report.getEvents().forEach(event -> System.out.println("  • " + event));
    }

    private void printResult() {
        System.out.println();
        String result = game.getStatus().isFinished() ? game.getStatus().getTitle() : "игра прервана";
        System.out.printf("Итог: %s. Шагов: %d, очки: %d%n", result, game.getStep(), game.getScore());
        System.out.println("Реальная пещера:");
        System.out.print(ConsoleRenderer.renderCave(game.getCave(), game.getHunter()));
        if (!properties.isDemo()) {
            int size = game.getCave().getSize();
            System.out.printf("Ям: %d = round(%d * %d * %.2f), количество растёт пропорционально числу комнат%n",
                    CaveGenerator.pitCount(size, properties.getPitRatio()), size, size, properties.getPitRatio());
        }
    }
}
