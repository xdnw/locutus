package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import io.javalin.http.RedirectResponse;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllowEmpty;
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
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NationPlaceholders extends Placeholders<DBNation> {
    private final Map<String, NationAttribute> customMetrics = new HashMap<>();

    public NationPlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBNation.class, store, validators, permisser);
    }

    @Override
    public Set<SelectorInfo> getSelectorInfo() {
        return new LinkedHashSet<>(List.of(
                new SelectorInfo("nation:NATION_NAME", "nation:Borg", "A qualified nation name"),
                new SelectorInfo("leader:NATION_NAME", "leader:Danzek", "A qualified leader name"),
                new SelectorInfo("aa:ALLIANCE_NAME", "aa:Rose", "A qualified alliance name"),
                new SelectorInfo("alliance:ALLIANCE_NAME", "alliance:Eclipse", "A qualified alliance name"),
                new SelectorInfo("nation/id=NATION_ID", "nation/id=6", "A nation url"),
                new SelectorInfo("alliance/id=ALLIANCE_ID", "alliance/id=790", "An alliance url"),
                new SelectorInfo("coalition:COALITION", "coalition:allies", "A qualified coalition name"),
                new SelectorInfo("~COALITION", "~enemies", "A coalition name"),
                new SelectorInfo("NATION_NAME", "Borg", "An unqualified nation name"),
                new SelectorInfo("LEADER_NAME", "Danzek", "An unqualified leader name"),
                new SelectorInfo("NATION_ID", "189573", "A nation id"),
                new SelectorInfo("ALLIANCE_ID", "790", "An alliance id"),
                new SelectorInfo("@ROLE_MENTION", "@Member", "A discord role mention or name"),
                new SelectorInfo("ROLE_ID", "123456789012345678", "A discord role id"),
                new SelectorInfo("@USER_MENTION", "@xdnw", "A discord user mention or name"),
                new SelectorInfo("USER_ID", "123456789012345678", "A discord user id"),
                new SelectorInfo("https://politicsandwar.com/index.php?id=15&tax_id=TAX_ID", "https://politicsandwar.com/index.php?id=15&tax_id=1234", "A full tax url"),
                new SelectorInfo("TAX_ID", "tax_id=1234", "A tax bracket id or url"),
                new SelectorInfo("*", null, "All nations")
        ));
    }

    @Override
    public Set<String> getSheetColumns() {
        return new LinkedHashSet<>(List.of("nation", "leader"));
    }

    @Override
    public String getName(DBNation o) {
        return o.getName();
    }

    @Override
    public Set<DBNation> deserializeSelection(ValueStore store, String input, String modifier) {
        Set<DBNation> superSet = super.deserializeSelection(store, input, modifier);
        List<Function<DBNation, Double>> sortCriteria = List.of(
                n -> (double) n.getAlliance_id(),
                n -> (double) n.getCities(),
                n -> (double) n.getId()
        );
        return superSet.stream().sorted((n1, n2) -> {
            for (Function<DBNation, Double> criteria : sortCriteria) {
                double val1 = criteria.apply(n1);
                double val2 = criteria.apply(n2);
                if (val1 != val2) {
                    return Double.compare(val1, val2);
                }
            }
            return 0;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @NoFormat
    @Command(desc = "Add an alias for a selection of Nations")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, @AllowEmpty Set<DBNation> nations) {
        return _addSelectionAlias(this, command, db, name, nations, "nations");
    }

    @NoFormat
    @Command(desc = "Add columns to a Nation sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                             @Default TypedFunction<DBNation, String> a,
                             @Default TypedFunction<DBNation, String> b,
                             @Default TypedFunction<DBNation, String> c,
                             @Default TypedFunction<DBNation, String> d,
                             @Default TypedFunction<DBNation, String> e,
                             @Default TypedFunction<DBNation, String> f,
                             @Default TypedFunction<DBNation, String> g,
                             @Default TypedFunction<DBNation, String> h,
                             @Default TypedFunction<DBNation, String> i,
                             @Default TypedFunction<DBNation, String> j,
                             @Default TypedFunction<DBNation, String> k,
                             @Default TypedFunction<DBNation, String> l,
                             @Default TypedFunction<DBNation, String> m,
                             @Default TypedFunction<DBNation, String> n,
                             @Default TypedFunction<DBNation, String> o,
                             @Default TypedFunction<DBNation, String> p,
                             @Default TypedFunction<DBNation, String> q,
                             @Default TypedFunction<DBNation, String> r,
                             @Default TypedFunction<DBNation, String> s,
                             @Default TypedFunction<DBNation, String> t,
                             @Default TypedFunction<DBNation, String> u,
                             @Default TypedFunction<DBNation, String> v,
                             @Default TypedFunction<DBNation, String> w,
                             @Default TypedFunction<DBNation, String> x) throws GeneralSecurityException, IOException {
        return Placeholders._addColumns(this, command,db, io, author, sheet,
                a, b, c, d, e, f, g, h, i, j,
                k, l, m, n, o, p, q, r, s, t,
                u, v, w, x);
    }

    @Override
    public String getDescription() {
        return CM.help.find_nation_placeholder.cmd.toSlashMention();
    }

    public List<NationAttribute> getMetrics(ValueStore store) {
        List<NationAttribute> result = new ArrayList<>();
        for (CommandCallable callable : getFilterCallables()) {
            ParametricCallable cmd = (ParametricCallable) callable;
            if (cmd.getUserParameters().stream().anyMatch(f -> !f.isOptional())) continue;
            try {
                String id = cmd.aliases().get(0);
                try {
                    TypedFunction<DBNation, ?> typeFunction = formatRecursively(store, id, null, 0, false, false);
                    if (typeFunction == null) continue;
                    NationAttribute metric = new NationAttribute(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getType(), typeFunction);
                    result.add(metric);
                } catch (IllegalStateException | CommandUsageException ignore) {
                    continue;
                }
            } catch (RedirectResponse ignore) {}
        }
        return result;
    }

    public NationAttributeDouble getMetricDouble(ValueStore<?> store, String id) {
        return getMetricDouble(store, id, false);
    }

    public NationAttribute getMetric(ValueStore<?> store, String id, boolean ignorePerms) {
        TypedFunction<DBNation, ?> typeFunction = formatRecursively(store, "{" + id + "}", null, 0, false,true);
        if (typeFunction == null) return null;
        return new NationAttribute<>(id, "", typeFunction.getType(), typeFunction);
    }

    public NationAttributeDouble getMetricDouble(ValueStore store, String id, boolean ignorePerms) {
        TypedFunction<DBNation, ?> typeFunction = formatRecursively(store, "{" + id + "}", null, 0, false, true);
        if (typeFunction == null) return null;

        TypedFunction<DBNation, ?> genericFunc = typeFunction;
        Function<DBNation, Double> func;
        Type type = typeFunction.getType();
        if (type == int.class || type == Integer.class) {
            func = nation -> ((Integer) genericFunc.applyCached(nation)).doubleValue();
        } else if (type == double.class || type == Double.class) {
            func = nation -> (Double) genericFunc.applyCached(nation);
        } else if (type == short.class || type == Short.class) {
            func = nation -> ((Short) genericFunc.applyCached(nation)).doubleValue();
        } else if (type == byte.class || type == Byte.class) {
            func = nation -> ((Byte) genericFunc.applyCached(nation)).doubleValue();
        } else if (type == long.class || type == Long.class) {
            func = nation -> ((Long) genericFunc.applyCached(nation)).doubleValue();
        } else if (type == boolean.class || type == Boolean.class) {
            func = nation -> ((Boolean) genericFunc.applyCached(nation)) ? 1d : 0d;
        } else {
            return null;
        }
        return new NationAttributeDouble(id, "", func);
    }

    public List<NationAttributeDouble> getMetricsDouble(ValueStore store) {
        List<NationAttributeDouble> result = new ArrayList<>();
        for (CommandCallable callable : getFilterCallables()) {
            ParametricCallable cmd = (ParametricCallable) callable;
            if (cmd.getUserParameters().stream().anyMatch(f -> !f.isOptional())) continue;
            try {
                String id = cmd.aliases().get(0);
                NationAttributeDouble metric = getMetricDouble(store, id, true);
                if (metric != null) {
                    result.add(metric);
                }
            } catch (RedirectResponse ignore) {}
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

    public static Set<DBNation> getByRole(Guild guild, String name, Role role, INationSnapshot snapshot) {
        if (role == null) throw new IllegalArgumentException("Invalid role: `" + name + "`");
        List<Member> members = guild.getMembersWithRoles(role);
        Set<DBNation> nations = new LinkedHashSet<>();
        for (Member member : members) {
            DBNation nation = snapshot.getNationByUser(member.getUser());
            if (nation != null) nations.add(nation);
        }
        return nations;
    }

    @Override
    public Set<DBNation> parseSet(ValueStore store2, String input, String modifier) {
        INationSnapshot snapshot = getSnapshot(modifier);
        input = wrapHashLegacy(input);
        return ArrayUtil.resolveQuery(input,
                f -> parseSingleElem(store2, f, snapshot),
                s -> getSingleFilter(store2, s));
    }

    @Override
    public Set<DBNation> parseSingleElem(ValueStore store, String name) {
        return parseSingleElem(store, name, Locutus.imp().getNationDB());
    }

    public Set<DBNation> parseSingleElem(ValueStore store, String name, INationSnapshot snapshot) {
        Set<DBNation> selection = PlaceholdersMap.getSelection(this, store, name);
        if (selection != null) return selection;
        String nameLower = name.toLowerCase(Locale.ROOT);
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        if (name.equals("*")) {
            return new ObjectArraySet<>(snapshot.getAllNations());
        } else if (name.contains("tax_id=")) {
            int taxId = PW.parseTaxId(name);
            return snapshot.getNationsByBracket(taxId);
        } else if (SpreadSheet.isSheet(nameLower)) {
            Set<DBNation> nations = SpreadSheet.parseSheet(name, List.of("nation", "leader"), true,
                    s -> switch (s.toLowerCase(Locale.ROOT)) {
                        case "nation" -> 0;
                        case "leader" -> 1;
                        default -> null;
                    }, (type, input) -> {
                        return switch (type) {
                            case 0 -> snapshot.getNationByName(input);
                            case 1 -> snapshot.getNationByLeader(input);
                            default -> null;
                        };
                    });
            return nations;
        }  else if (nameLower.startsWith("aa:") || nameLower.startsWith("alliance:")) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name.split(":", 2)[1].trim());
            if (alliances == null) throw new IllegalArgumentException("Invalid alliance: `" + name + "`");
            Set<DBNation> allianceMembers = snapshot.getNationsByAlliance(alliances);
            return allianceMembers;
            // role
        } else if (nameLower.startsWith("<@&") && guild != null) {
            Role role = DiscordUtil.getRole(guild, name);
            return getByRole(guild, name, role, snapshot);
        } else if (nameLower.startsWith("<@") || nameLower.startsWith("<!@")) {
            User user = DiscordUtil.getUser(nameLower);
            if (user != null) {
                DBNation nation = snapshot.getNationByUser(user);
                if (nation == null) {
                    throw new IllegalArgumentException("User `" + DiscordUtil.getFullUsername(user) + "` is not registered. See " + CM.register.cmd.toSlashMention());
                }
                return Set.of(nation);
            }
        } else if (MathMan.isInteger(nameLower)) {
            long id = Long.parseLong(nameLower);
            if (id > Integer.MAX_VALUE && guild != null) {
                User user = Locutus.imp().getDiscordApi().getUserById(id);
                if (user != null) {
                    DBNation nation = snapshot.getNationByUser(user);
                    if (nation == null) {
                        throw new IllegalArgumentException("User `" + DiscordUtil.getFullUsername(user) + "` is not registered. See " + CM.register.cmd.toSlashMention());
                    }
                    return Set.of(nation);
                }
                Role role = DiscordUtil.getRole(guild, name);
                if (role != null) {
                    return getByRole(guild, name, role, snapshot);
                }
            }
            DBNation nation = snapshot.getNationById((int) id);
            if (nation != null) return Set.of(nation);
            Set<DBNation> nations = snapshot.getNationsByAlliance((int) id);
            if (!nations.isEmpty()) {
                return nations;
            }
//            DBAlliance alliance = DBAlliance.get((int) id);
//            if (alliance != null) return alliance.getNations();
        }

        Set<DBNation> nations = new LinkedHashSet<>();
        boolean containsAA = nameLower.contains("/alliance/");
        DBNation nation = containsAA ? null : snapshot.getNationByName(name);
        if (nation == null || containsAA) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name);
            if (alliances == null) {
                Role role = guild != null ? DiscordUtil.getRole(guild, name) : null;
                if (role != null) {
                    return getByRole(guild, name, role, snapshot);
                } else if (name.contains("#")) {
                    String[] split = name.split("#");
                    PNWUser user = Locutus.imp().getDiscordDB().getUser(null, split[0], name);
                    if (user != null) {
                        nation = snapshot.getNationById(user.getNationId());
                    }
                    if (nation == null) {
                        throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                    }
                } else {
                    throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                }
            } else {
                Set<DBNation> allianceMembers = snapshot.getNationsByAlliance(alliances);
                nations.addAll(allianceMembers);
            }
        } else {
            nations.add(nation);
        }
        return nations;
    }

    @Override
    public Predicate<DBNation> parseSingleFilter(ValueStore store, String name) {
        return parseSingleFilter(store, name, Locutus.imp().getNationDB());
    }

    public Predicate<DBNation> parseSingleFilter(ValueStore store, String name, INationSnapshot snapshot) {
        String nameLower = name.toLowerCase(Locale.ROOT);
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        if (name.equals("*")) {
            return f -> true;
        } else if (name.contains("tax_id=")) {
            int taxId = PW.parseTaxId(name);
            return f -> f.getTax_id() == taxId;
        } else if (SpreadSheet.isSheet(nameLower)) {
            Set<DBNation> nations = SpreadSheet.parseSheet(name, List.of("nation", "leader"), true,
                    s -> switch (s.toLowerCase(Locale.ROOT)) {
                        case "nation" -> 0;
                        case "leader" -> 1;
                        default -> null;
                    }, (type, input) -> {
                        return switch (type) {
                            case 0 -> snapshot.getNationByName(input);
                            case 1 -> snapshot.getNationByLeader(input);
                            default -> null;
                        };
                    });
            Set<Integer> ids = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
            return f -> ids.contains(f.getId());
        }  else if (nameLower.startsWith("aa:") || nameLower.startsWith("alliance:")) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name.split(":", 2)[1].trim());
            if (alliances == null) throw new IllegalArgumentException("Invalid alliance: `" + name + "`");
            return f -> alliances.contains(f.getAlliance_id());
        } else if (MathMan.isInteger(nameLower)) {
            int id = Integer.parseInt(nameLower);
            return f -> f.getId() == id || f.getAlliance_id() == id;
        } else if (nameLower.contains("tax_id=")) {
            int taxId = PW.parseTaxId(name);
            return f -> f.getTax_id() == taxId;
        }
        boolean containsAA = nameLower.contains("/alliance/");
        DBNation nation = containsAA ? null : snapshot.getNationByName(name);
        if (nation == null) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name);
            if (alliances == null) {
                Role role = guild != null ? DiscordUtil.getRole(guild, name) : null;
                if (role != null) {
                    return f -> {
                        User user = f.getUser();
                        if (user == null) return false;
                        Member member = role.getGuild().getMember(user);
                        if (member == null) return false;
                        return member.getRoles().contains(role);
                    };
                } else if (name.contains("#")) {
                    String[] split = name.split("#");
                    PNWUser user = Locutus.imp().getDiscordDB().getUser(null, split[0], name);
                    if (user != null) {
                        nation = snapshot.getNationById(user.getNationId());
                    }
                    if (nation == null) {
                        throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                    }
                    int id = nation.getId();
                    return f -> f.getId() == id;
                } else {
                    throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                }
            } else {
                return f -> alliances.contains(f.getAlliance_id());
            }
        } else {
            int id = nation.getId();
            return f -> f.getId() == id;
        }
    }
}