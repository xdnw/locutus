package link.locutus.discord.db.entities.conflict;

import link.locutus.discord.apiv1.enums.MilitaryUnit;

import java.util.Map;

public enum ConflictMetric {
    NATION(null, true),
    BEIGE(null, true),
    INFRA(null, true),
    SOLDIER(MilitaryUnit.SOLDIER, false),
    TANK(MilitaryUnit.TANK, false),
    AIRCRAFT(MilitaryUnit.AIRCRAFT, false),
    SHIP(MilitaryUnit.SHIP, false),
    SPIES(MilitaryUnit.SPIES, false),


    ;

    public static final ConflictMetric[] values = values();
    public static final Map<MilitaryUnit, ConflictMetric> BY_UNIT = Map.of(
            MilitaryUnit.SOLDIER, SOLDIER,
            MilitaryUnit.TANK, TANK,
            MilitaryUnit.AIRCRAFT, AIRCRAFT,
            MilitaryUnit.SHIP, SHIP,
            MilitaryUnit.SPIES, SPIES
    );

    private final MilitaryUnit unit;
    private final boolean isDay;

    ConflictMetric(MilitaryUnit unit, boolean isDay) {
        this.unit = unit;
        this.isDay = isDay;
    }

    public MilitaryUnit getUnit() {
        return unit;
    }

    public boolean isDay() {
        return isDay;
    }

    public record Entry(ConflictMetric metric, int conflictId, boolean side, long turnOrDay, int city, int value) {
    }
}
