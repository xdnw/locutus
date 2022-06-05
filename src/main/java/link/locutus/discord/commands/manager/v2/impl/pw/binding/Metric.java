package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import java.lang.reflect.Type;
import java.util.function.Function;

public interface Metric<T, R> extends Function<T, R> {
    R apply(T nation);

    public String getName();

    public Type getType();

    public String getDesc();
}
