package com.vadim.wampus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wampus")
public class WampusProperties {

    /** Кто управляет охотником в консоли: агент сам или пользователь с подсказками агента. */
    private ControlMode mode = ControlMode.AUTO;

    /** Сторона поля N: пещера состоит из N * N комнат. */
    private int size;

    /** Доля комнат с ямами: количество ям растёт пропорционально числу комнат. */
    private double pitRatio;

    /** Зерно генератора. 0 — каждый запуск новая случайная пещера. */
    private long seed;

    /** Загрузить мир из методички (4x4) вместо случайного. */
    private boolean demo;

    /**
     * Допустимая вероятность гибели при рискованном ходе, когда безопасных клеток не осталось.
     * 0 — агент никогда не рискует и завершает поход.
     */
    private double maxRisk;

    /** Ограничение на число шагов (решений агента) в одной игре. */
    private int maxSteps;

    /** Показать реальную пещеру в начале игры (в конце она печатается всегда). */
    private boolean showWorld;

    public enum ControlMode {
        AUTO,
        MANUAL
    }
}
