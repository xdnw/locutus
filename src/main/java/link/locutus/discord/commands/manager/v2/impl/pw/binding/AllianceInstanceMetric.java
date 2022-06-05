package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;

import java.lang.reflect.Type;
import java.util.function.Function;

public class AllianceInstanceMetric<T> implements Metric<Alliance, T> {
    private final Function<Alliance, T> parent;
    private final Type type;
    private final String name;
    private final String desc;

    public AllianceInstanceMetric(String name, String desc, Type type, Function<Alliance, T> parent) {
        this.type = type;
        this.parent = parent;
        this.name = name;
        this.desc = desc;
    }


    public String getName() {
        return this.name;
    }

    @Override
    public Type getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public T apply(Alliance nation) {
        return parent.apply(nation);
    }
}