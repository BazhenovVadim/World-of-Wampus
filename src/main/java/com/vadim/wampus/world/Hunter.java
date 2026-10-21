package com.vadim.wampus.world;

import com.vadim.wampus.model.Direction;
import com.vadim.wampus.model.Position;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Hunter {

    private Position position;

    @Builder.Default
    private Direction direction = Direction.RIGHT;

    @Builder.Default
    private int arrows = 1;

    @Builder.Default
    private boolean alive = true;

    private boolean hasGold;

    public boolean hasArrow() {
        return arrows > 0;
    }
}
