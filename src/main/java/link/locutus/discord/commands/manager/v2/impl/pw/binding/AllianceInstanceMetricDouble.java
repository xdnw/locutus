package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;

import java.lang.reflect.Type;
import java.util.function.Function;

public class AllianceInstanceMetricDouble extends AllianceInstanceMetric<Double> {
    public AllianceInstanceMetricDouble(String id, String desc, Function<Alliance, Double> parent) {
        super(id, desc, Double.TYPE, (Function) parent);
    }

    @Override
    public Type getType() {
        return Double.class;
    }

    @Override
    public Double apply(Alliance nation) {
        return (Double) super.apply(nation);
    }
}
