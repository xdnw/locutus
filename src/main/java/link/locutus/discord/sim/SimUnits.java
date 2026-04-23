package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class SimUnits {
    public static final MilitaryUnit[] PURCHASABLE_UNITS = {
            MilitaryUnit.SOLDIER,
            MilitaryUnit.TANK,
            MilitaryUnit.AIRCRAFT,
            MilitaryUnit.SHIP,
            MilitaryUnit.MISSILE,
            MilitaryUnit.NUKE
    };

    private static final Set<MilitaryUnit> PURCHASABLE_UNIT_SET = Collections.unmodifiableSet(
            EnumSet.copyOf(Arrays.asList(PURCHASABLE_UNITS))
    );
    private static final int[] PURCHASABLE_INDEX_BY_ORDINAL;

    static {
        PURCHASABLE_INDEX_BY_ORDINAL = new int[MilitaryUnit.values.length];
        Arrays.fill(PURCHASABLE_INDEX_BY_ORDINAL, -1);
        for (int i = 0; i < PURCHASABLE_UNITS.length; i++) {
            PURCHASABLE_INDEX_BY_ORDINAL[PURCHASABLE_UNITS[i].ordinal()] = i;
        }
    }

    private SimUnits() {
    }

    public static boolean isPurchasable(MilitaryUnit unit) {
        return unit != null && PURCHASABLE_UNIT_SET.contains(unit);
    }

    public static int purchasableIndex(MilitaryUnit unit) {
        if (unit == null) {
            return -1;
        }
        return PURCHASABLE_INDEX_BY_ORDINAL[unit.ordinal()];
    }
}
