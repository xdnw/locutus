package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.SelectorInfo;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.AllianceInstanceAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.AllianceInstanceAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AlliancePlaceholders extends Placeholders<DBAlliance> {
    private final Map<String, AllianceInstanceAttribute> customMetrics = new HashMap<>();

    public AlliancePlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBAlliance.class, store, validators, permisser);
    }

    @Override
    public String getName(DBAlliance o) {
        return o.getName();
    }

    public List<AllianceInstanceAttribute> getMetrics(ValueStore store) {
        List<AllianceInstanceAttribute> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            TypedFunction<DBAlliance, ?> typeFunction = formatRecursively(store, id, null, 0, false, false);
            if (typeFunction == null) continue;

            AllianceInstanceAttribute metric = new AllianceInstanceAttribute(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getType(), typeFunction);
            result.add(metric);
        }
        return result;
    }

    @Override
    public Set<SelectorInfo> getSelectorInfo() {
        return new LinkedHashSet<>(List.of(
                new SelectorInfo("aa:ALLIANCE_NAME", "aa:Rose", "A qualified alliance name"),
                new SelectorInfo("alliance:ALLIANCE_NAME", "alliance:Eclipse", "A qualified alliance name"),
                new SelectorInfo("alliance/id=ALLIANCE_ID", "alliance/id=790", "An alliance url"),
                new SelectorInfo("ALLIANCE_ID", "790", "An alliance id"),
                new SelectorInfo("coalition:COALITION", "coalition:allies", "A qualified coalition name"),
                new SelectorInfo("~COALITION", "~enemies", "A coalition name"),
                new SelectorInfo("*", null, "All alliances")
        ));
    }

    @Override
    public Set<String> getSheetColumns() {
        return new LinkedHashSet<>(List.of("alliance", "{id}", "{name}", "{getname}", "{getid}"));
    }

    public AllianceInstanceAttributeDouble getMetricDouble(ValueStore store, String id) {
        return getMetricDouble(store, id, false);
    }

    @NoFormat
    @Command(desc = "Add an alias for a selection of alliances")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBAlliance> alliances) {
        return _addSelectionAlias(this, command, db, name, alliances, "alliances");
    }

    @NoFormat
    @Command(desc = "Add columns to a Alliance sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                             @Default TypedFunction<DBAlliance, String> a,
                             @Default TypedFunction<DBAlliance, String> b,
                             @Default TypedFunction<DBAlliance, String> c,
                             @Default TypedFunction<DBAlliance, String> d,
                             @Default TypedFunction<DBAlliance, String> e,
                             @Default TypedFunction<DBAlliance, String> f,
                             @Default TypedFunction<DBAlliance, String> g,
                             @Default TypedFunction<DBAlliance, String> h,
                             @Default TypedFunction<DBAlliance, String> i,
                             @Default TypedFunction<DBAlliance, String> j,
                             @Default TypedFunction<DBAlliance, String> k,
                             @Default TypedFunction<DBAlliance, String> l,
                             @Default TypedFunction<DBAlliance, String> m,
                             @Default TypedFunction<DBAlliance, String> n,
                             @Default TypedFunction<DBAlliance, String> o,
                             @Default TypedFunction<DBAlliance, String> p,
                             @Default TypedFunction<DBAlliance, String> q,
                             @Default TypedFunction<DBAlliance, String> r,
                             @Default TypedFunction<DBAlliance, String> s,
                             @Default TypedFunction<DBAlliance, String> t,
                             @Default TypedFunction<DBAlliance, String> u,
                             @Default TypedFunction<DBAlliance, String> v,
                             @Default TypedFunction<DBAlliance, String> w,
                             @Default TypedFunction<DBAlliance, String> x) throws GeneralSecurityException, IOException {
        return _addColumns(this, command,db, io, author, sheet,
                a, b, c, d, e, f, g, h, i, j,
                k, l, m, n, o, p, q, r, s, t,
                u, v, w, x);
    }

    public AllianceInstanceAttributeDouble getMetricDouble(ValueStore store, String id, boolean ignorePerms) {
        ParametricCallable cmd = get(id);
        if (cmd == null) return null;
        TypedFunction<DBAlliance, ?> typeFunction;
        try {
            typeFunction = formatRecursively(store, id, null, 0, false, true);
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
        TypedFunction<DBAlliance, ?> typeFunction = formatRecursively(store, id, null, 0, false, true);
        if (typeFunction == null) return null;
        return new AllianceInstanceAttribute<>(id, "", typeFunction.getType(), typeFunction);
    }

    @Override
    protected Set<DBAlliance> parseSingleElem(ValueStore store, String input) {
        Set<DBAlliance> selection = PlaceholdersMap.getSelection(this, store, input);
        if (selection != null) return selection;
        if (input.equalsIgnoreCase("*")) {
            return Locutus.imp().getNationDB().getAlliances();
        }
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        if (SpreadSheet.isSheet(input)) {
            return SpreadSheet.parseSheet(input, List.of("alliance", "{name}", "{id}", "{getname}", "{getid}"), true,
                s -> switch (s.toLowerCase()) {
                    case "alliance", "{name}", "{id}", "{getname}", "{getid}" -> 0;
                    default -> null;
                },
                (type, str) -> {
                    return PWBindings.alliance(str);
                });

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
            Set<Set<Integer>> setSet = SpreadSheet.parseSheet(input, List.of("alliance", "{name}", "{id}", "{getname}", "{getid}"), true,
                    s -> switch (s.toLowerCase()) {
                        case "alliance", "{name}", "{id}", "{getname}", "{getid}" -> 0;
                        default -> null;
                    },
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
    public String getDescription() {
        // TODO cm ref
        return "<https://github.com/xdnw/locutus/wiki/alliance_placeholders>";
    }
}