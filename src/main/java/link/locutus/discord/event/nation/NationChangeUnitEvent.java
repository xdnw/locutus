package link.locutus.discord.event.nation;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.entities.DBNation;

public class NationChangeUnitEvent extends NationChangeEvent2 {
    private final MilitaryUnit unit;

    public NationChangeUnitEvent(DBNation original, DBNation changed, MilitaryUnit unit) {
        super(original, changed);
        this.unit = unit;
    }

    public MilitaryUnit getUnit() {
        return unit;
    }
}
