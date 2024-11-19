package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.BeigeReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WebMyWars {
    public List<WebMyWar> offensives = new ObjectArrayList<>();
    public List<WebMyWar> defensives = new ObjectArrayList<>();

    public WebTarget me;
    public boolean isFightingActives;
    public int soldier_cap;
    public int tank_cap;
    public int aircraft_cap;
    public int ship_cap;

    public WebMyWars(GuildDB db, DBNation nation, List<DBWar> offensives, List<DBWar> defensives, boolean isFightingActives) {
        me = new WebTarget(nation, 0, 0, 0);
        this.isFightingActives = isFightingActives;
        soldier_cap = nation.getUnitCap(MilitaryUnit.SOLDIER, true);
        tank_cap = nation.getUnitCap(MilitaryUnit.TANK, true);
        aircraft_cap = nation.getUnitCap(MilitaryUnit.AIRCRAFT, true);
        ship_cap = nation.getUnitCap(MilitaryUnit.SHIP, true);

        boolean violationsEnabled = db.getOrNull(GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS) != null;

//        if (!offensives.isEmpty()) {
//            for (DBWar war : offensives) {
//                Set<BeigeReason> reasons;
//                if (violationsEnabled && db.isEnemyAlliance(war.getDefender_aa())) {
//                    reasons = BeigeReason.getAllowedBeigeReasons(db, nation, war, null);
//                } else {
//                    reasons = null;
//                }
//                DBNation other = war.getNation(false);
//                offensives.add(new WebMyWar(db, nation, other, war, true, reasons));
//            }
//        }
//        if (!defensives.isEmpty()) {
//            for (DBWar war : defensives) {
//                DBNation other = war.getNation(true);
//                defensives.add(new WebMyWar(db, nation, other, war, false, null));
//            }
//        }


        // my units
        // my unit cap
        // recommendedAttacks
        // isFightingActives

        // db.getOrNull(GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS) != null && db.isEnemyAlliance(entry.getKey().getDefender_aa())
        // @template.guild.mywartr(ws = ws, db = db, nation = nation, author = author, war = entry.getKey(), enemy = entry.getValue(), warCard = warCards.get(entry.getKey()), recommendedAttack = recommendedAttacks.get(entry.getKey()), isAttacker = false, permitted = null)
    }
}
