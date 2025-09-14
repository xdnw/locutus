package link.locutus.discord.util;

import link.locutus.discord.apiv1.enums.MilitaryUnit;

public enum Operation {
    INTEL(1, 1, null),
    NUKE(5, 18.74971745, MilitaryUnit.NUKE),
    MISSILE(4, 12.49979948, MilitaryUnit.MISSILE),
    SHIPS(3, 8.437383791, MilitaryUnit.SHIP),
    AIRCRAFT(2, 4.999927084, MilitaryUnit.AIRCRAFT),
    TANKS(1.5, 2.343723796, MilitaryUnit.TANK),
    SPIES(1.5, 2.812461264, MilitaryUnit.SPIES),
    SOLDIER(1, 1.249990886, MilitaryUnit.SOLDIER);

    public final double odds;
    public final double lossFactor;
    public final MilitaryUnit unit;

    Operation(double odds, double lossFactor, MilitaryUnit unit) {
        this.odds = odds;
        this.lossFactor = lossFactor;
        this.unit = unit;
    }

    public static final Operation[] values = values();

    public static Operation getByUnit(MilitaryUnit unit) {
        for (Operation op : values) {
            if (op.unit == unit) {
                return op;
            }
        }
        return null;
    }
}
