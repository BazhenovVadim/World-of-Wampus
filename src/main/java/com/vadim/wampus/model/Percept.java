package com.vadim.wampus.model;

import lombok.Builder;
import lombok.Value;

/** Результат измерения датчиков: (Stench, Breeze, Glitter, Bump, Scream). */
@Value
@Builder
public class Percept {
    boolean stench;
    boolean breeze;
    boolean glitter;
    boolean bump;
    boolean scream;

    public static final String SENSORS = "(Stench, Breeze, Glitter, Bump, Scream)";

    @Override
    public String toString() {
        return "(" + String.join(", ",
                stench ? "Stench" : "none",
                breeze ? "Breeze" : "none",
                glitter ? "Glitter" : "none",
                bump ? "Bump" : "none",
                scream ? "Scream" : "none") + ")";
    }
}
