package link.locutus.discord.apiv3.subscription;

import com.politicsandwar.graphql.model.*;
import link.locutus.discord.db.entities.Treaty;

public enum PnwPusherModel {
    alliance(Alliance.class),
    alliance_position(AlliancePosition.class),
    bankrec(Bankrec.class),
    bbgame(BBGame.class),
    bbteam(BBTeam.class),
    bounty(Bounty.class),
    city(City.class),
    nation(Nation.class),
    tax_bracket(TaxBracket.class),
    trade(Trade.class),
    treaty(Treaty.class),
    warattack(WarAttack.class),
    war(War.class),
    treasure_trade(TreasureTrade.class),
    embargo(Embargo.class);

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
