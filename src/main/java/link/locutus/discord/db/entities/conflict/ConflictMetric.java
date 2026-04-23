package link.locutus.discord.db.entities.conflict;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.entities.DBNation;

import java.util.Map;

public enum ConflictMetric {
    NATION(null, true) {
        @Override
        public int get(DBNation nation) {
            return 1;
        }
    },
    BEIGE(null, true) {
        @Override
        public int get(DBNation nation) {
            return nation.isBeige() ? 1 : 0;
        }
    },
    INFRA(null, true) {
        @Override
        public int get(DBNation nation) {
            return (int) nation.getInfra();
        }
    },
    SOLDIER_CAPACITY(null, true) {
        @Override
        public int get(DBNation nation) {
            return MilitaryUnit.SOLDIER.getMaxMMRCap(
                    nation.getCities(),
                    nation.getResearchBits(null),
                    nation::hasProject
            );
        }
    },
    TANK_CAPACITY(null, true) {
        @Override
        public int get(DBNation nation) {
            return MilitaryUnit.TANK.getMaxMMRCap(
                    nation.getCities(),
                    nation.getResearchBits(null),
                    nation::hasProject
            );
        }
    },
    AIRCRAFT_CAPACITY(null, true) {
        @Override
        public int get(DBNation nation) {
            return MilitaryUnit.AIRCRAFT.getMaxMMRCap(
                    nation.getCities(),
                    nation.getResearchBits(null),
                    nation::hasProject
            );
        }
    },
    SHIP_CAPACITY(null, true) {
        @Override
        public int get(DBNation nation) {
            return MilitaryUnit.SHIP.getMaxMMRCap(
                    nation.getCities(),
                    nation.getResearchBits(null),
                    nation::hasProject
            );
        }
    },
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

    public int get(DBNation nation) {
        return nation.getUnits(unit);
    }

    public MilitaryUnit getUnit() {
        return unit;
    }

    public boolean isDay() {
        return isDay;
    }

    public record Entry(ConflictMetric metric, int conflictId, int allianceId, boolean side, long turnOrDay, int city, int value) {
    }
}
