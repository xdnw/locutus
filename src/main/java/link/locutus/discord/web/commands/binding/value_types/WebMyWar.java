package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.BeigeReason;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WebMyWar {
    public WebTarget target;
    public Map<String, String> beigeReasons;

    public int peace;

    public int blockade;
    public int ac;
    public int gc;
    public int ground_str;

    public int att_res;
    public int def_res;

    public int att_map;
    public int def_map;

    public int iron_dome;
    public int vts;

    public int att_fortified;
    public int def_fortified;

    public WebMyWar(GuildDB db, DBNation self, DBNation enemy, DBWar war, boolean isOffensive, Set<BeigeReason> reasons) {
        double loot = enemy.lootTotal() * war.getWarType().lootModifier();
        target = new WebTarget(enemy, 0, loot, 0);
        this.beigeReasons = new LinkedHashMap<>();
        if (reasons != null) {
            for (BeigeReason reason : reasons) {
                beigeReasons.put(reason.name(), reason.getDescription());
            }
        }
        int blockader = war.getBlockader();
        this.blockade = blockader == 0 ? 0 : blockader == self.getNation_id() ? 1 : -1;
        int ac = war.getAirControl();
        int gc = war.getGroundControl();
        this.ac = ac == 0 ? 0 : ac == self.getNation_id() ? 1 : -1;
        this.gc = gc == 0 ? 0 : gc == self.getNation_id() ? 1 : -1;
        this.ground_str = (int) enemy.getGroundStrength(true, ac == 1);

        List<AbstractCursor> attacks = war.getAttacks2();
        Map.Entry<Integer, Integer> map = war.getMap(attacks);
        this.att_map = map.getKey();
        this.def_map = map.getValue();
        Map.Entry<Integer, Integer> res = war.getResistance(attacks);
        this.att_res = res.getKey();
        this.def_res = res.getValue();
        this.peace = war.getStatus() == WarStatus.ATTACKER_OFFERED_PEACE ? 1 : war.getStatus() == WarStatus.DEFENDER_OFFERED_PEACE ? -1 : 0;
        Map.Entry<Boolean, Boolean> fortified = war.getFortified(attacks);


        /*
        ground strength
        // MAP
        // resistance

        war id
                @if(nation.hasProject(Projects.MISSILE_LAUNCH_PAD) && enemy.hasProject(Projects.IRON_DOME))
            <p>IRON_DOME (50% chance to thwart missiles)</p>
        @endif
        @if(nation.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY) && enemy.hasProject(Projects.VITAL_DEFENSE_SYSTEM))
            <p>VITAL_DEFENSE_SYSTEM (20% chance to thwart nukes)</p>
        @endif
        @if(warCard.attackerFortified)

        @endif
        @if(warCard.defenderFortified)

        @endif
        @if(warCard.blockaded == nation.getNation_id())

        @endif
        @if(warCard.blockaded == enemy.getNation_id())

        @endif
        @if(warCard.groundControl == nation.getNation_id())

        @endif
        @if(warCard.groundControl == enemy.getNation_id())

        @endif
        @if(warCard.airSuperiority == nation.getNation_id())

        @endif
        @if(warCard.airSuperiority == enemy.getNation_id())
         */
    }
}
