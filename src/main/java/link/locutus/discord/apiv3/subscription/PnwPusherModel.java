package link.locutus.discord.apiv3.subscription;

import com.politicsandwar.graphql.model.*;
import link.locutus.discord.db.entities.Treaty;

public enum PnwPusherModel {
    ALLIANCE(Alliance.class),
    ALLIANCE_POSITION(AlliancePosition.class),
    BANKREC(Bankrec.class),
    BBGAME(BBGame.class),
    BBTEAM(BBTeam.class),
    BOUNTY(Bounty.class),
    CITY(City.class),
    NATION(Nation.class),
    TAX_BRACKET(TaxBracket.class),
    TRADE(Trade.class),
    TREATY(Treaty.class),
    WARATTACK(WarAttack.class),
    WAR(War.class),
    TREASURE_TRADE(TreasureTrade.class),
    EMBARGO(Embargo.class);

    private final Class<?> clazz;

    PnwPusherModel(Class<?> clazz) {
        this.clazz = clazz;
    }

    public static PnwPusherModel valueOf(Class<?> clazz) {
        for (PnwPusherModel model : values()) {
            if (model.clazz.equals(clazz)) return model;
        }
        throw new IllegalArgumentException("No model found for: " + clazz);

    }
}
