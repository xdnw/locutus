package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.db.entities.DBAlliance;

import java.lang.reflect.Type;
import java.util.function.Function;

public class AllianceInstanceAttributeDouble extends AllianceInstanceAttribute<Double> {
    public AllianceInstanceAttributeDouble(String id, String desc, Function<DBAlliance, Double> parent) {
        super(id, desc, Double.TYPE, parent);
    }

    @Override
    public Type getType() {
        return Double.class;
    }

    @Override
    public Double apply(DBAlliance nation) {
        return super.apply(nation);
    }
}
