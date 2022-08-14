package link.locutus.discord.event.nation;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.entities.DBNation;

public class NationChangeUnitEvent extends NationChangeEvent2 {
    private final MilitaryUnit unit;
    private boolean isAttack;

    public NationChangeUnitEvent(DBNation original, DBNation changed, MilitaryUnit unit, boolean isAttack) {
        super(original, changed);
        this.unit = unit;
        this.isAttack = isAttack;
    }

    public NationChangeUnitEvent(DBNation copyOriginal, DBNation changed, MilitaryUnit unit) {
        this(copyOriginal, changed, unit, changed.getUnits(unit) < copyOriginal.getUnits(unit));
        if (isAttack) {
            if (changed.lastActiveMs() > copyOriginal.lastActiveMs() && changed.getUnits(unit) == 0) {
                isAttack = false;
            }
        }

    }

    public MilitaryUnit getUnit() {
        return unit;
    }

    public boolean isAttack() {
        return isAttack;
    }
}
