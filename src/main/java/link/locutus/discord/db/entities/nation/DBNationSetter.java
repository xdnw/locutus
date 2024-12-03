package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.db.entities.DBNationCache;

public interface DBNationSetter {
    void setNation_id(int nation_id);
    void setNation(String nation);
    void setLeader(String leader);
    void setAlliance_id(int alliance_id);
    void setLast_active(long last_active);
    void setScore(double score);
    void setCities(int cities);
    void setDomestic_policy(DomesticPolicy domestic_policy);
    void setWar_policy(WarPolicy war_policy);
    void setSoldiers(int soldiers);
    void setTanks(int tanks);
    void setAircraft(int aircraft);
    void setShips(int ships);
    void setMissiles(int missiles);
    void setNukes(int nukes);
    void setSpies(int spies);
    void setEntered_vm(long entered_vm);
    void setLeaving_vm(long leaving_vm);
    void setColor(NationColor color);
    void setDate(long date);
    void setRank(Rank rank);
    void setAlliancePosition(int alliancePosition);
    void setContinent(Continent continent);
    void setProjects(long projects);
    void setCityTimer(long cityTimer);
    void setProjectTimer(long projectTimer);
    void setBeigeTimer(long beigeTimer);
    void setWarPolicyTimer(long warPolicyTimer);
    void setDomesticPolicyTimer(long domesticPolicyTimer);
    void setColorTimer(long colorTimer);
    void setEspionageFull(long espionageFull);
    void setDc_turn(int dc_turn);
    void setWars_won(int wars_won);
    void setWars_lost(int wars_lost);
    void setTax_id(int tax_id);
    void setGni(double gni);
    void setCache(DBNationCache cache);
}