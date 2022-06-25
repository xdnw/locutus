package link.locutus.discord.util.update;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.city.CityInfraBuyEvent;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.AuditType;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;

import java.util.function.Function;

public class CityUpdateProcessor {
    public void onInfraBuy(CityInfraBuyEvent event) {
        DBCity city = event.getCurrent();

        DBNation nation = DBNation.byId(event.getNationId());

        if (city.infra % 50 != 0 && nation != null) {
            AlertUtil.auditAlert(nation, AuditType.UNEVEN_INFRA, new Function<GuildDB, String>() {
                @Override
                public String apply(GuildDB guildDB) {
                    int ideal = (int) (city.infra - city.infra % 50);
                    String msg = AuditType.UNEVEN_INFRA.message
                            .replace("{city}", PnwUtil.getCityUrl(city.id));
                    return "You bought uneven infra in <" + PnwUtil.getCityUrl(city.id) + "> (" + MathMan.format(city.infra) + " infra) but only get a building slot every `50` infra.\n" +
                            "You can enter e.g. `@" + ideal + "` to buy up to that amount";
                }
            });
        }
    }
}
