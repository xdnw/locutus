package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationMetric;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationMetricDouble;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.pnw.DBNation;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NationPlaceholders extends Placeholders<DBNation> {
    private Map<String, NationMetric> customMetrics = new HashMap<>();

    public NationPlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBNation.class, new DBNation(), store, validators, permisser);
    }

//    public <T> NationMetric addMetric(String name, String desc, Class<T> type, Function<DBNation, T> func) {
//        NationMetric metric = new NationMetric(name, desc, type, (Function<DBNation, Object>) func);
//        customMetrics.put(name, metric);
//    }

    public List<NationMetric> getMetrics(ValueStore store) {
        List<NationMetric> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            Map.Entry<Type, Function<DBNation, Object>> typeFunction = getPlaceholderFunction(store, id);
            if (typeFunction == null) continue;

            NationMetric metric = new NationMetric(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getKey(), typeFunction.getValue());
            result.add(metric);
        }
        return result;
    }

    public NationMetricDouble getMetricDouble(ValueStore store, String id) {
        return getMetricDouble(store, id, false);

    }

    public NationMetricDouble getMetricDouble(ValueStore store, String id, boolean ignorePerms) {
        ParametricCallable cmd = get(id);
        if (cmd == null) return null;
        Map.Entry<Type, Function<DBNation, Object>> typeFunction;
        try {
            typeFunction = getPlaceholderFunction(store, id);
        } catch (CommandUsageException ignore) {
            return null;
        } catch (Throwable ignore2) {
            if (!ignorePerms) throw ignore2;
            return null;
        }
        if (typeFunction == null) {
            return null;
        }

        Function<DBNation, Object> genericFunc = typeFunction.getValue();
        Function<DBNation, Double> func;
        Type type = typeFunction.getKey();
        if (type == int.class || type == Integer.class) {
            func = nation -> ((Integer) genericFunc.apply(nation)).doubleValue();
        } else if (type == double.class || type == Double.class) {
            func = nation -> (Double) genericFunc.apply(nation);
        } else if (type == short.class || type == Short.class) {
            func = nation -> ((Short) genericFunc.apply(nation)).doubleValue();
        } else if (type == byte.class || type == Byte.class) {
            func = nation -> ((Byte) genericFunc.apply(nation)).doubleValue();
        } else if (type == long.class || type == Long.class) {
            func = nation -> ((Long) genericFunc.apply(nation)).doubleValue();
        } else if (type == boolean.class || type == Boolean.class) {
            func = nation -> ((Boolean) genericFunc.apply(nation)) ? 1d : 0d;
        } else {
            return null;
        }
        return new NationMetricDouble(cmd.getPrimaryCommandId(), cmd.simpleDesc(), func);
    }

    public List<NationMetricDouble> getMetricsDouble(ValueStore store) {
        List<NationMetricDouble> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            NationMetricDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        for (Map.Entry<String, NationMetric> entry : customMetrics.entrySet()) {
            String id = entry.getKey();
            NationMetricDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        return result;
    }
}