package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.AllianceInstanceAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.AllianceInstanceAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class AlliancePlaceholders extends Placeholders<DBAlliance> {
    private final Map<String, AllianceInstanceAttribute> customMetrics = new HashMap<>();

    public AlliancePlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBAlliance.class, DBAlliance.getOrCreate(0), store, validators, permisser);
    }

    public List<AllianceInstanceAttribute> getMetrics(ValueStore store) {
        List<AllianceInstanceAttribute> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            Map.Entry<Type, Function<DBAlliance, Object>> typeFunction = getPlaceholderFunction(store, id);
            if (typeFunction == null) continue;

            AllianceInstanceAttribute metric = new AllianceInstanceAttribute(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getKey(), typeFunction.getValue());
            result.add(metric);
        }
        return result;
    }

    public AllianceInstanceAttributeDouble getMetricDouble(ValueStore store, String id) {
        return getMetricDouble(store, id, false);

    }

    public AllianceInstanceAttributeDouble getMetricDouble(ValueStore store, String id, boolean ignorePerms) {
        ParametricCallable cmd = get(id);
        if (cmd == null) return null;
        Map.Entry<Type, Function<DBAlliance, Object>> typeFunction;
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

        Function<DBAlliance, Object> genericFunc = typeFunction.getValue();
        Function<DBAlliance, Double> func;
        Type type = typeFunction.getKey();
        if (type == int.class || type == Integer.class) {
            func = aa -> ((Integer) genericFunc.apply(aa)).doubleValue();
        } else if (type == double.class || type == Double.class) {
            func = aa -> (Double) genericFunc.apply(aa);
        } else if (type == short.class || type == Short.class) {
            func = aa -> ((Short) genericFunc.apply(aa)).doubleValue();
        } else if (type == byte.class || type == Byte.class) {
            func = aa -> ((Byte) genericFunc.apply(aa)).doubleValue();
        } else if (type == long.class || type == Long.class) {
            func = aa -> ((Long) genericFunc.apply(aa)).doubleValue();
        } else if (type == boolean.class || type == Boolean.class) {
            func = aa -> ((Boolean) genericFunc.apply(aa)) ? 1d : 0d;
        } else {
            return null;
        }
        return new AllianceInstanceAttributeDouble(cmd.getPrimaryCommandId(), cmd.simpleDesc(), func);
    }

    public List<AllianceInstanceAttributeDouble> getMetricsDouble(ValueStore store) {
        List<AllianceInstanceAttributeDouble> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            AllianceInstanceAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        for (Map.Entry<String, AllianceInstanceAttribute> entry : customMetrics.entrySet()) {
            String id = entry.getKey();
            AllianceInstanceAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        return result;
    }

    public AllianceInstanceAttribute getMetric(ValueStore<?> store, String id, boolean ignorePerms) {
        Map.Entry<Type, Function<DBAlliance, Object>> typeFunction = getTypeFunction(store, id, ignorePerms);
        if (typeFunction == null) return null;
        return new AllianceInstanceAttribute<>(id, "", typeFunction.getKey(), typeFunction.getValue());
    }

    public String format(Guild guild, DBAlliance alliance, User user, String arg) {
        LocalValueStore locals = new LocalValueStore<>(this.getStore());
        if (alliance != null) {
            locals.addProvider(Key.of(DBAlliance.class, Me.class), alliance);
        }
        if (user != null) {
            locals.addProvider(Key.of(User.class, Me.class), user);
        }
        if (guild != null) {
            locals.addProvider(Key.of(Guild.class, Me.class), guild);
        }
        return format(locals, arg);
    }

    public String format(ValueStore<?> store, String arg) {
        DBAlliance me = store.getProvided(Key.of(DBAlliance.class, Me.class));
        User author = null;
        try {
            author = store.getProvided(Key.of(User.class, Me.class));
        } catch (Exception ignore) {
        }

        if (author != null && arg.contains("%user%")) {
            arg = arg.replace("%user%", author.getAsMention());
        }

        return format(arg, 0, new Function<String, String>() {
            @Override
            public String apply(String placeholder) {
                AllianceInstanceAttribute result = AlliancePlaceholders.this.getMetric(store, placeholder, false);
                if (result == null && !placeholder.startsWith("get")) {
                    result = AlliancePlaceholders.this.getMetric(store, "get" + placeholder, false);
                }
                if (result != null) {
                    Object obj = result.apply(me);
                    if (obj != null) {
                        return obj.toString();
                    }
                }
                return null;
            }
        });
    }

    @Override
    protected Set<DBAlliance> parse(ValueStore store, String input) {
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        return PWBindings.alliances(guild, input);
    }

    @Override
    public String getCommandMention() {
        // TODO cm ref
        return "<https://github.com/xdnw/locutus/wiki/alliance_placeholders>";
    }
}