package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.BeigeReason;

import java.util.*;

public class WebMyWars {
    public List<WebMyWar> offensives = new ObjectArrayList<>();
    public List<WebMyWar> defensives = new ObjectArrayList<>();

    public WebTarget me;
    public boolean isFightingActives;
    public int soldier_cap;
    public int tank_cap;
    public int aircraft_cap;
    public int ship_cap;
    public int spy_cap;

    public WebMyWars(GuildDB db, DBNation nation, List<DBWar> offensives, List<DBWar> defensives, boolean isFightingActives) {
        me = new WebTarget(nation, 0, 0, nation.getGroundStrength(true, false));
        this.isFightingActives = isFightingActives;
        soldier_cap = nation.getUnitCap(MilitaryUnit.SOLDIER, true);
        tank_cap = nation.getUnitCap(MilitaryUnit.TANK, true);
        aircraft_cap = nation.getUnitCap(MilitaryUnit.AIRCRAFT, true);
        ship_cap = nation.getUnitCap(MilitaryUnit.SHIP, true);
        spy_cap = nation.getSpyCap();

        boolean violationsEnabled = db.getOrNull(GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS) != null;

        if (!offensives.isEmpty()) {
            for (DBWar war : offensives) {
                Set<BeigeReason> reasons;
                if (violationsEnabled && db.isEnemyAlliance(war.getDefender_aa())) {
                    reasons = BeigeReason.getAllowedBeigeReasons(db, nation, war, null);
                } else {
                    reasons = null;
                }
                DBNation other = war.getNation(false);
                this.offensives.add(new WebMyWar(db, nation, other, war, true, reasons));
            }
        }
        if (!defensives.isEmpty()) {
            for (DBWar war : defensives) {
                DBNation other = war.getNation(true);
                this.defensives.add(new WebMyWar(db, nation, other, war, false, null));
            }
        }
    }
}
