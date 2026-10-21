package com.vadim.wampus.world;

import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Percept;
import com.vadim.wampus.model.Position;
import lombok.Getter;

import java.util.Collections;
import java.util.Set;

/**
 * Окружение — пещера N * N комнат. Знает истинное расположение ям, Вампуса и золота
 * и формирует показания датчиков охотника.
 */
@Getter
public class Cave {

    private final int size;
    private final Set<Position> pits;
    private final Position wampus;
    private final Position gold;
    private final Position start;

    private boolean wampusAlive = true;
    private boolean goldTaken;

    public Cave(int size, Set<Position> pits, Position wampus, Position gold) {
        this.size = size;
        this.pits = Collections.unmodifiableSet(pits);
        this.wampus = wampus;
        this.gold = gold;
        this.start = startOf(size);
    }

    public static Position startOf(int size) {
        return new Position(size, 1);
    }

    public boolean hasPit(Position position) {
        return pits.contains(position);
    }

    public boolean hasLiveWampus(Position position) {
        return wampusAlive && wampus.equals(position);
    }

    public boolean hasGold(Position position) {
        return !goldTaken && gold.equals(position);
    }

    /** Ветерок — около ямы, запах — около Вампуса (и в его комнате), блеск — около золота и на нём. */
    public Percept perceive(Position at, boolean bump, boolean scream) {
        return Percept.builder()
                .stench(wampus.equals(at) || wampus.isAdjacent(at))
                .breeze(at.neighbors(size).stream().anyMatch(this::hasPit))
                .glitter(!goldTaken && (gold.equals(at) || gold.isAdjacent(at)))
                .bump(bump)
                .scream(scream)
                .build();
    }

    /** Стрела летит по прямой до стены. Возвращает true, если Вампус убит. */
    public boolean shoot(Position from, Direction direction) {
        Position cell = from.neighbor(direction);
        while (cell.isInside(size)) {
            if (hasLiveWampus(cell)) {
                wampusAlive = false;
                return true;
            }
            cell = cell.neighbor(direction);
        }
        return false;
    }

    public void takeGold() {
        goldTaken = true;
    }
}
