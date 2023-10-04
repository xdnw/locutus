package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.AllianceInstanceAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.AllianceInstanceAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AlliancePlaceholders extends Placeholders<DBAlliance> {
    private final Map<String, AllianceInstanceAttribute> customMetrics = new HashMap<>();

    public AlliancePlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBAlliance.class, store, validators, permisser);
        this.getCommands().registerCommands(new DefaultPlaceholders());
    }

    public List<AllianceInstanceAttribute> getMetrics(ValueStore store) {
        List<AllianceInstanceAttribute> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            TypedFunction<DBAlliance, ?> typeFunction = formatRecursively(store, id, null, 0, false);
            if (typeFunction == null) continue;

            AllianceInstanceAttribute metric = new AllianceInstanceAttribute(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getType(), typeFunction);
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
        TypedFunction<DBAlliance, ?> typeFunction;
        try {
            typeFunction = formatRecursively(store, id, null, 0, true);
        } catch (CommandUsageException ignore) {
            return null;
        } catch (Throwable ignore2) {
            if (!ignorePerms) throw ignore2;
            return null;
        }
        if (typeFunction == null) {
            return null;
        }

        Function<DBAlliance, ?> genericFunc = typeFunction;
        Function<DBAlliance, Double> func;
        Type type = typeFunction.getType();
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
        TypedFunction<DBAlliance, ?> typeFunction = formatRecursively(store, id, null, 0, true);
        if (typeFunction == null) return null;
        return new AllianceInstanceAttribute<>(id, "", typeFunction.getType(), typeFunction);
    }

    @Override
    protected Set<DBAlliance> parseSingleElem(ValueStore store, String input) {
        if (input.equalsIgnoreCase("*")) {
            return Locutus.imp().getNationDB().getAlliances();
        }
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        if (SpreadSheet.isSheet(input)) {
            return SpreadSheet.parseSheet(input, List.of("alliance"), true,
                    s -> s.equalsIgnoreCase("alliance") ? 0 : null,
                    (type, str) -> PWBindings.alliance(str));
        }
        return parseIds(guild, input, true);
    }

    @Override
    protected Predicate<DBAlliance> parseSingleFilter(ValueStore store, String input) {
        if (input.equalsIgnoreCase("*")) {
            return f -> true;
        }
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        GuildDB db = guild == null ? null : Locutus.imp().getGuildDB(guild);
        if (SpreadSheet.isSheet(input)) {
            Set<Set<Integer>> setSet = SpreadSheet.parseSheet(input, List.of("alliance"), true,
                    s -> s.equalsIgnoreCase("alliance") ? 0 : null,
                    (type, str) -> DiscordUtil.parseAllianceIds(guild, str));

            Set<Integer> ids = setSet.stream().flatMap(Collection::stream).collect(Collectors.toSet());
            return f -> ids.contains(f.getId());
        }
        Set<Integer> aaIds = DiscordUtil.parseAllianceIds(guild, input, false);
        if (aaIds == null) {
            if (db != null) {
                if (input.startsWith("~")) {
                    input = input.substring(1);
                }
                long coalitionId = db.getCoalitionId(input, true);
                return f -> {
                    Set<Long> coalition = db.getCoalitionById(coalitionId);
                    return coalition.contains(f.getIdLong());
                };
            }
            throw new IllegalArgumentException("Invalid alliances: " + input);
        }
        return f -> aaIds.contains(f.getId());
    }

    private Set<DBAlliance> parseIds(Guild guild, String input, boolean throwError) {
        Set<Integer> aaIds = DiscordUtil.parseAllianceIds(guild, input, true);
        if (aaIds == null) {
            if (!throwError) return null;
            throw new IllegalArgumentException("Invalid alliances: " + input);
        }
        Set<DBAlliance> alliances = new HashSet<>();
        for (Integer aaId : aaIds) {
            alliances.add(DBAlliance.getOrCreate(aaId));
        }
        return alliances;
    }

    @Override
    public String getCommandMention() {
        // TODO cm ref
        return "<https://github.com/xdnw/locutus/wiki/alliance_placeholders>";
    }
}