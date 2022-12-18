package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.db.entities.DBAlliance;

import java.lang.reflect.Type;
import java.util.function.Function;

public class AllianceInstanceAttribute<T> implements Attribute<DBAlliance, T> {
    private final Function<DBAlliance, T> parent;
    private final Type type;
    private final String name;
    private final String desc;

    public AllianceInstanceAttribute(String name, String desc, Type type, Function<DBAlliance, T> parent) {
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
    public T apply(DBAlliance nation) {
        return parent.apply(nation);
    }
}