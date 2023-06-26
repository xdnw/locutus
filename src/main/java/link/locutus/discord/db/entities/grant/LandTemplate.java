package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;

import java.sql.ResultSet;

public class LandTemplate extends AGrantTemplate{
    //long level
    //boolean onlyNewCities
    public LandTemplate(GuildDB db, int id, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, ResultSet rs) {
        super(db, id, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay);
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.LAND;
    }
}
