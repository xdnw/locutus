package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NationPlaceholders extends Placeholders<DBNation> {
    private Map<String, NationAttribute> customMetrics = new HashMap<>();

    public NationPlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBNation.class, new DBNation(), store, validators, permisser);
    }

//    public <T> NationMetric addMetric(String name, String desc, Class<T> type, Function<DBNation, T> func) {
//        NationMetric metric = new NationMetric(name, desc, type, (Function<DBNation, Object>) func);
//        customMetrics.put(name, metric);
//    }

    public List<NationAttribute> getMetrics(ValueStore store) {
        List<NationAttribute> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            Map.Entry<Type, Function<DBNation, Object>> typeFunction = getPlaceholderFunction(store, id);
            if (typeFunction == null) continue;

            NationAttribute metric = new NationAttribute(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getKey(), typeFunction.getValue());
            result.add(metric);
        }
        return result;
    }

    public NationAttributeDouble getMetricDouble(ValueStore<?> store, String id) {
        return getMetricDouble(store, id, false);
    }

    public NationAttribute getMetric(ValueStore<?> store, String id, boolean ignorePerms) {
        Map.Entry<Type, Function<DBNation, Object>> typeFunction = getTypeFunction(store, id, ignorePerms);
        if (typeFunction == null) return null;
        return new NationAttribute<>(id, "", typeFunction.getKey(), typeFunction.getValue());
    }

    public Map.Entry<Type, Function<DBNation, Object>> getTypeFunction(ValueStore<?> store, String id, boolean ignorePerms) {
        Map.Entry<Type, Function<DBNation, Object>> typeFunction;
        try {
            typeFunction = getPlaceholderFunction(store, id);
        } catch (CommandUsageException ignore) {
            return null;
        } catch (Exception ignore2) {
            if (!ignorePerms) throw ignore2;
            return null;
        }
        return typeFunction;
    }

    public NationAttributeDouble getMetricDouble(ValueStore store, String id, boolean ignorePerms) {
        ParametricCallable cmd = get(getCmd(id));
        if (cmd == null) return null;
        Map.Entry<Type, Function<DBNation, Object>> typeFunction = getTypeFunction(store, id, ignorePerms);
        if (typeFunction == null) return null;

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
        return new NationAttributeDouble(cmd.getPrimaryCommandId(), cmd.simpleDesc(), func);
    }

    public List<NationAttributeDouble> getMetricsDouble(ValueStore store) {
        List<NationAttributeDouble> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            NationAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        for (Map.Entry<String, NationAttribute> entry : customMetrics.entrySet()) {
            String id = entry.getKey();
            NationAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        return result;
    }

    public String format(ValueStore<?> store, String arg) {
        Guild guild = store.getProvided(Key.of(Me.class, Guild.class));
        MessageChannel channel = store.getProvided(Key.of(Me.class, MessageChannel.class));
        User author = store.getProvided(Key.of(Me.class, User.class));
        DBNation me = store.getProvided(Key.of(Me.class, DBNation.class));

        // todo

        return DiscordUtil.format( guild, channel, author, me, arg);
    }
}