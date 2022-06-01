package link.locutus.discord.apiv1.enums;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum AttackType {
    GROUND(MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT),
    VICTORY,
    FORTIFY,
    A_LOOT("Alliance Loot"),
    AIRSTRIKE1("Airstrike Infrastructure", MilitaryUnit.AIRCRAFT), // infra
    AIRSTRIKE2("Airstrike Soldiers", MilitaryUnit.AIRCRAFT, MilitaryUnit.SOLDIER),
    AIRSTRIKE3("Airstrike Tanks", MilitaryUnit.AIRCRAFT, MilitaryUnit.TANK),
    AIRSTRIKE4("Airstrike Money", MilitaryUnit.AIRCRAFT, MilitaryUnit.MONEY),
    AIRSTRIKE5("Airstrike Ships", MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP),
    AIRSTRIKE6("Dogfight", MilitaryUnit.AIRCRAFT), // airstrike aircraft
    NAVAL(MilitaryUnit.SHIP),
    PEACE,
    MISSILE(MilitaryUnit.MISSILE),
    NUKE(MilitaryUnit.NUKE),
    ;

    private final MilitaryUnit[] units;
    private final String name;

    AttackType(MilitaryUnit... units) {
        this(null, units);
    }

    AttackType(String name, MilitaryUnit... units) {
        this.units = units;
        this.name = name == null ? name() : name;
    }

    public String getName() {
        return name;
    }

    public static final AttackType[] values = values();

    public static AttackType get(String input) {
        if (input.charAt(input.length() - 1) == 'F') {
            return get(input.substring(0, input.length() - 1));
        }
        return valueOf(input);
    }

    public MilitaryUnit[] getUnits() {
        return units;
    }

    public Map<MilitaryUnit, Integer> getLosses(int a, int b, int c) {
        if (a == 0 && b == 0 && c == 0) return Collections.emptyMap();
        HashMap<MilitaryUnit, Integer> map = new HashMap<>(2);
        if (a != 0) {
            map.put(units[0], a);
        }
        if (b != 0) {
            map.put(units[1], b);
        }
        if (c != 0) {
            map.put(units[2], c);
        }
        return map;
    }
}
