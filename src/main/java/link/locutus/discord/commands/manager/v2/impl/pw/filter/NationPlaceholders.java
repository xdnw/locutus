package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.SelectorInfo;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NationPlaceholders extends Placeholders<DBNation, NationModifier> {

    public NationPlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBNation.class, NationModifier.class, store, validators, permisser);
    }

    @Override
    public Set<SelectorInfo> getSelectorInfo() {
        return new ObjectLinkedOpenHashSet<>(List.of(
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
                new SelectorInfo("TAX_ID", "tax_id=1234", "A tax bracket id or url"),
                new SelectorInfo("*", null, "All nations"),
                new SelectorInfo("nation(<timestamp>,[includeVM:bool]):SELECTOR", "nation(5d,true):*", "Snapshot at a specific date, optionally including vacation mode")
        ));
    }

    @Override
    public Set<String> getSheetColumns() {
        return new ObjectLinkedOpenHashSet<>(List.of("nation", "leader", "{id}", "{nation}", "{leader}"));
    }

    @Override
    public String getName(DBNation o) {
        return o.getName();
    }

    @Override
    public Set<DBNation> deserializeSelection(ValueStore store, String input, NationModifier modifier) {
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
        }).collect(Collectors.toCollection(ObjectLinkedOpenHashSet::new));
    }

    @NoFormat
    @Command(desc = "Nation snapshot settings (optional)")
    public NationModifier create(@Arg("The date to use, rounded to the nearest day") @Switch("t") @Timestamp Long timestamp,
                                 @Switch("d") boolean allow_deleted,
                                 @Arg("If VM nations are fetched if a snapshot date is specified")@Switch("v") boolean load_snapshot_vm) {
        return new NationModifier(timestamp, allow_deleted, load_snapshot_vm);
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

    @Override
    public NationModifier parseModifierLegacy(ValueStore store, String input) {
        return NationModifier.parse(input);
    }

    public static Set<DBNation> getByRole(Guild guild, String name, Role role, INationSnapshot snapshot) {
        if (role == null) throw new IllegalArgumentException("Invalid role: `" + name + "`");
        List<Member> members = guild.getMembersWithRoles(role);
        Set<DBNation> nations = new ObjectLinkedOpenHashSet<>();
        for (Member member : members) {
            DBNation nation = snapshot.getNationByUser(member.getUser());
            if (nation != null) nations.add(nation);
        }
        return nations;
    }

    private INationSnapshot getSnapshot(NationModifier modifier) {
        if (modifier != null && modifier.timestamp != null) {
            DataDumpParser parser = Locutus.imp().getDataDumper(true);
            try {
                parser.load();
                Long day = TimeUtil.getDay(modifier.timestamp);
                if (day != null && day != TimeUtil.getDay()) {
                    return parser.getSnapshotDelegate(day, true, modifier.load_snapshot_vm);
                }
            } catch (ParseException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return Locutus.imp().getNationDB();
    }

    @Override
    public Set<DBNation> parseSet(ValueStore store2, String input, NationModifier modifier) {
        INationSnapshot snapshot = getSnapshot(modifier);
        return parseSet(store2, input, snapshot, modifier != null && modifier.allow_deleted);
    }

    public Set<DBNation> parseSet(ValueStore store2, String input, INationSnapshot snapshot, boolean allowDeleted) {
        input = wrapHashLegacy(store2, input);
        return ArrayUtil.resolveQuery(input,
                f -> {
            long start = System.currentTimeMillis();
                    Set<DBNation> result = parseSingleElem(store2, f, snapshot, allowDeleted);
                    long diff = System.currentTimeMillis() - start;
                    if (diff > 1) {
                        Logg.text("parseSingleElem took " + diff + "ms for " + f);
                    }
                    return result;
                },
                s -> {
                    long start = System.currentTimeMillis();
                    Predicate<DBNation> result = getSingleFilter(store2, s);
                    long diff = System.currentTimeMillis() - start;
                    if (diff > 1) {
                        Logg.text("getSingleFilter took " + diff + "ms for " + s);
                    }
                    return result;
                });
    }

    @Override
    public Set<DBNation> parseSingleElem(ValueStore store, String name) {
        return parseSingleElem(store, name, Locutus.imp().getNationDB(), true);
    }

    public Set<DBNation> parseSingleElem(ValueStore store, String name, boolean allowDeleted) {
        return parseSingleElem(store, name, Locutus.imp().getNationDB(), allowDeleted);
    }

    public Set<DBNation> parseSingleElem(ValueStore store, String name, INationSnapshot snapshot, boolean allowDeleted) {
        name = name.trim();
        long start = System.currentTimeMillis();
        Set<DBNation> selection = PlaceholdersMap.getSelection(this, store, name);
        if (selection != null) return selection;
        String nameLower = name.toLowerCase(Locale.ROOT);
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        if (name.equals("*")) {
            Set<DBNation> allNations = snapshot.getAllNations();
            return allNations;
        } else if (name.contains("tax_id=")) {
            int taxId = PW.parseTaxId(name);
            return snapshot.getNationsByBracket(taxId);
        } else if (SpreadSheet.isSheet(nameLower)) {
            Set<DBNation> nations = SpreadSheet.parseSheet(name, List.of("nation", "leader", "{nation}", "{leader}", "{id}"), true,
                    s -> switch (s.toLowerCase(Locale.ROOT)) {
                        case "nation", "{nation}", "{id}" -> 0;
                        case "leader", "{leader}" -> 1;
                        default -> null;
                    }, (type, input) -> {
                        return switch (type) {
                            case 0 -> snapshot.getNationByInput(input, allowDeleted, true, guild);
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
        } else if (nameLower.startsWith("<@&") && guild != null) {
            Role role = DiscordUtil.getRole(guild, name);
            return getByRole(guild, name, role, snapshot);
        } else if (nameLower.startsWith("<@") || nameLower.startsWith("<!@")) {
            User user = DiscordUtil.getUser(nameLower, guild);
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

        Set<DBNation> nations = new ObjectLinkedOpenHashSet<>();
        boolean containsAA = nameLower.contains("/alliance/");
        String errMsg = "";
        DBNation nation = null;
        if (!containsAA) {
            try {
                nation = snapshot.getNationByInput(name, allowDeleted, true, guild);
            } catch (IllegalArgumentException e) {
                errMsg = "\n" + e.getMessage();
            }
        }
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
                        throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`" + errMsg);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`" + errMsg);
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
            return Predicates.alwaysTrue();
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
                            case 0 -> snapshot.getNationByInput(input, true, true, guild);
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
        String errMsg = "";
        DBNation nation = null;
        if (!containsAA) {
            try {
                nation = snapshot.getNationByInput(name, true, true, guild);
            } catch (IllegalArgumentException e) {
                errMsg = "\n" + e.getMessage();
            }
        }
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
                        return member.getUnsortedRoles().contains(role);
                    };
                } else if (name.contains("#")) {
                    String[] split = name.split("#");
                    PNWUser user = Locutus.imp().getDiscordDB().getUser(null, split[0], name);
                    if (user != null) {
                        nation = snapshot.getNationById(user.getNationId());
                    }
                    if (nation == null) {
                        throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`" + errMsg);
                    }
                    int id = nation.getId();
                    return f -> f.getId() == id;
                } else {
                    throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`" + errMsg);
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