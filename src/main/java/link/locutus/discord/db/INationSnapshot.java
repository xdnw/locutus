package link.locutus.discord.db;

import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;

import java.util.Set;

public interface INationSnapshot {
    default DBNation getNationById(int id, boolean allowDeleted) {
        DBNation nation = getNationById(id);
        if (nation == null && allowDeleted) {
            nation = new SimpleDBNation(new DBNationData());
            nation.edit().setNation_id(id);
        }
        return nation;
    }
    DBNation getNationById(int id);

    Set<DBNation> getNationsByAlliance(Set<Integer> alliances);

    DBNation getNationByLeader(String input);

    DBNation getNationByName(String input);

    default DBNation getNationByNameOrLeader(String input) {
        DBNation nation = getNationByName(input);
        if (nation == null) {
            nation = getNationByLeader(input);
        }
        return nation;
    }

    Set<DBNation> getAllNations();

    Set<DBNation> getNationsByBracket(int taxId);

    Set<DBNation> getNationsByAlliance(int id);

    Set<DBNation> getNationsByColor(NationColor color);
}
