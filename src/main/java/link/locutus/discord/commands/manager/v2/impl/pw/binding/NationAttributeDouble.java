package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.db.entities.DBNation;

import java.lang.reflect.Type;
import java.util.function.Function;

public class NationAttributeDouble extends NationAttribute<Double> {
    public NationAttributeDouble(String id, String desc, Function<DBNation, Double> parent) {
        super(id, desc, Double.TYPE, (Function) parent);
    }

    @Override
    public Type getType() {
        return Double.class;
    }

    @Override
    public Double apply(DBNation nation) {
        return (Double) super.apply(nation);
    }
}
