package link.locutus.discord.apiv1.enums;

import it.unimi.dsi.fastutil.objects.Object2ByteArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteMaps;
import org.checkerframework.checker.units.qual.A;

import java.util.*;

public enum AttackType {
    GROUND(3, 10, MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT),
    VICTORY(0, 0),
    FORTIFY(3, 0),
    A_LOOT("Alliance Loot", 0, 0),
    AIRSTRIKE1("Airstrike Infrastructure", 4, 12, MilitaryUnit.AIRCRAFT), // infra
    AIRSTRIKE2("Airstrike Soldiers", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.SOLDIER),
    AIRSTRIKE3("Airstrike Tanks", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.TANK),
    AIRSTRIKE4("Airstrike Money", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.MONEY),
    AIRSTRIKE5("Airstrike Ships", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP),
    AIRSTRIKE6("Dogfight", 4, 12, MilitaryUnit.AIRCRAFT), // airstrike aircraft
    NAVAL(4, 14, MilitaryUnit.SHIP),
    PEACE(0, 0),
    MISSILE(8, 18, MilitaryUnit.MISSILE),
    NUKE(12, 25, MilitaryUnit.NUKE),
    ;

    private final MilitaryUnit[] units;
    private final String name;
    private final int mapUsed;
    private final int resistanceIT;

    AttackType(int mapUsed, int resistanceIT, MilitaryUnit... units) {
        this(null, mapUsed, resistanceIT, units);
    }

    AttackType(String name, int mapUsed, int resistanceIT, MilitaryUnit... units) {
        this.units = units;
        this.name = name == null ? name() : name;
        this.mapUsed = mapUsed;
        this.resistanceIT = resistanceIT;
    }

    public int getResistanceIT() {
        return resistanceIT;
    }

    public int getMapUsed() {
        return mapUsed;
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
