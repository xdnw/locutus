package com.boydti.discord.commands.manager.v2.impl.pw.binding;

import com.boydti.discord.pnw.DBNation;

import java.lang.reflect.Type;
import java.util.function.Function;

public class NationMetricDouble extends NationMetric {
    public NationMetricDouble(String id, String desc, Function<DBNation, Double> parent) {
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
