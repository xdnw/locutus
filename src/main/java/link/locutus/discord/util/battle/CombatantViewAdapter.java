package link.locutus.discord.util.battle;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.sim.combat.state.CombatantSnapshots;

public final class CombatantViewAdapter {
    private CombatantViewAdapter() {
    }

    public static CombatantView of(DBNation nation) {
        return CombatantSnapshots.snapshotOf(nation);
    }

    public static CombatantView of(DBNation nation, Double maxCityInfraOverride) {
        return CombatantSnapshots.snapshotOf(nation, maxCityInfraOverride);
    }
}
