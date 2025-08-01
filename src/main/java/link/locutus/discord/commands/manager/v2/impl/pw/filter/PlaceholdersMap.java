package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.*;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.pnw.*;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.scheduler.ThrowingTriFunction;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.binding.BindingHelper.emumSet;

public class PlaceholdersMap {

    private static PlaceholdersMap INSTANCE;

    public static String getClassName(String simpleName) {
            return simpleName.replace("DB", "").replace("Wrapper", "")
                    .replaceAll("[0-9]", "")
                    .toLowerCase(Locale.ROOT);
    }
    public static String getClassName(Class clazz) {
        return getClassName(clazz.getSimpleName());
    }

    public static Placeholders<DBNation, NationModifier> NATIONS = null;
    public static Placeholders<DBAlliance, Void> ALLIANCES = null;
    public static Placeholders<NationOrAlliance, Void> NATION_OR_ALLIANCE = null;
    public static Placeholders<Continent, Void> CONTINENTS = null;
    public static Placeholders<GuildDB, Void> GUILDS = null;
    public static Placeholders<Project, Void> PROJECTS = null;
    public static Placeholders<Treaty, Void> TREATIES = null;
    public static Placeholders<DBBan, Void> BANS = null;
    public static Placeholders<ResourceType, Void> RESOURCE_TYPES = null;
    public static Placeholders<AttackType, Void> ATTACK_TYPES = null;
    public static Placeholders<MilitaryUnit, Void> MILITARY_UNITS = null;
    public static Placeholders<TreatyType, Void> TREATY_TYPES = null;
    public static Placeholders<DBTreasure, Void> TREASURES = null;
    public static Placeholders<NationColor, Void> NATION_COLORS = null;
    public static Placeholders<Building, Void> BUILDINGS = null;
    public static Placeholders<IACheckup.AuditType, Void> AUDIT_TYPES = null;
    public static Placeholders<NationList, Void> NATION_LIST = null;
    public static Placeholders<DBBounty, Void> BOUNTIES = null;
    public static Placeholders<DBCity, Void> CITIES = null;
    public static Placeholders<TaxBracket, Void> TAX_BRACKETS = null;
    public static Placeholders<UserWrapper, Void> USERS = null;
    public static Placeholders<Transaction2, Void> TRANSACTIONS = null;
    public static Placeholders<DBTrade, Void> TRADES = null;
    public static Placeholders<IAttack, Void> ATTACKS = null;
    public static Placeholders<DBWar, Void> WARS = null;
    public static Placeholders<TaxDeposit, Void> TAX_DEPOSITS = null;
    public static Placeholders<GuildSetting, Void> SETTINGS = null;
    public static Placeholders<Conflict, Void> CONFLICTS = null;
    public static Placeholders<TextChannelWrapper, Void> CHANNELS = null;

    // --------------------------------------------------------------------


    private final Map<Class<?>, Placeholders<?, ?>> placeholders = new ConcurrentHashMap<>();
    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public Set<Class<?>> getTypes() {
        return placeholders.keySet();
    }

    public PlaceholdersMap(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        if (INSTANCE != null) {
            throw new IllegalStateException("Already initialized");
        }
        INSTANCE = this;
        
        this.store = store;
        this.validators = validators;
        this.permisser = permisser;

        this.placeholders.put(DBNation.class, new NationPlaceholders(store, validators, permisser));
        this.placeholders.put(DBAlliance.class, new AlliancePlaceholders(store, validators, permisser));
        this.placeholders.put(NationOrAlliance.class, createNationOrAlliances());
        this.placeholders.put(Continent.class, createContinents());
        this.placeholders.put(GuildDB.class, createGuildDB());
        this.placeholders.put(Project.class, createProjects());
        this.placeholders.put(Treaty.class, createTreaty());
        this.placeholders.put(DBBan.class, createBans());
        this.placeholders.put(ResourceType.class, createResourceType());
        this.placeholders.put(AttackType.class, createAttackTypes());
        this.placeholders.put(MilitaryUnit.class, createMilitaryUnit());
        this.placeholders.put(TreatyType.class, createTreatyType());
        this.placeholders.put(DBTreasure.class, createTreasure());
        this.placeholders.put(NationColor.class, createNationColor());
        this.placeholders.put(Building.class, createBuilding());
        this.placeholders.put(IACheckup.AuditType.class, createAuditType());
        this.placeholders.put(NationList.class, createNationList());
        this.placeholders.put(DBBounty.class, createBounties());
        this.placeholders.put(DBCity.class, createCities());
        this.placeholders.put(TaxBracket.class, createBrackets());
        this.placeholders.put(UserWrapper.class, createUsers());
        this.placeholders.put(TextChannelWrapper.class, createChannels());
        this.placeholders.put(Transaction2.class, createTransactions());
        this.placeholders.put(DBTrade.class, createTrades());
        this.placeholders.put(IAttack.class, createAttacks());
        this.placeholders.put(DBWar.class, createWars());
        this.placeholders.put(TaxDeposit.class, createTaxDeposit());
        this.placeholders.put(GuildSetting.class, createGuildSettings());
        this.placeholders.put(Conflict.class, createConflicts());

        Map<Class, Field> fields = new HashMap<>();
        for (Field field : PlaceholdersMap.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!Placeholders.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Class enclosedType = (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            fields.put(enclosedType, field);
        }

        for (Map.Entry<Class<?>, Placeholders<?, ?>> entry : this.placeholders.entrySet()) {
            Class<?> enclosedType = entry.getKey();
            Field field = fields.get(enclosedType);
            if (field == null) {
                throw new IllegalStateException("Missing field for `" + enclosedType + "`. Options:\n- " + StringMan.join(fields.keySet(), "\n- "));
            }
            try {
                field.set(null, entry.getValue());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

//        //-GuildKey
//        this.placeholders.put(GuildSetting.class, createGuildSetting());
//        //- Tax records
//        // - * (within aa)
//        this.placeholders.put(AGrantTemplate.class, createGrantTemplates());
    }

    public <T, M> Placeholders<T, M> get(Class<T> type) {
        return (Placeholders<T, M>) this.placeholders.get(type);
    }

    public <T> Class<T> parseType(String name) {
        name = getClassName(name);
        String finalName = name;
        return (Class<T>) this.placeholders.keySet().stream().filter(f -> getClassName(f).equalsIgnoreCase(finalName)).findFirst().orElse(null);
    }

    public static <T, M> Set<T> getSelection(Placeholders<T, M> instance, ValueStore store, String input) {
        return getSelection(instance, store, input, true);
    }
    public static <T, M> Set<T> getSelection(Placeholders<T, M> instance, ValueStore store, String input, boolean throwError) {
        Class<T> type = instance.getType();
        boolean isSelection = false;
        String inputAlias = input;
        if (input.startsWith("$") && input.length() > 1) {
            isSelection = true;
            inputAlias = input.substring(1);
        } else if (input.startsWith("select:")) {
            isSelection = true;
            inputAlias = input.substring("select:".length());
        } else if (input.startsWith("selection:")) {
            isSelection = true;
            inputAlias = input.substring("selection:".length());
        } else if (input.startsWith("alias:")) {
            isSelection = true;
            inputAlias = input.substring("alias:".length());
        }
        if (isSelection) {
            GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
            if (db != null) {
                SelectionAlias<T> selection = db.getSheetManager().getSelectionAlias(inputAlias, type);
                if (selection != null) {
                    String query = selection.getSelection();
                    String modifierStr = selection.getModifier();
                    M modifier = modifierStr == null || modifierStr.isEmpty() ? null : instance.parseModifierLegacy(store, modifierStr);
                    return instance.parseSet(store, query, modifier);
                }
                if (throwError) {
                    Map<String, SelectionAlias<T>> options = db.getSheetManager().getSelectionAliases(type);
                    if (options.isEmpty()) {
                        throw new IllegalArgumentException("No selection aliases for type: `" + type.getSimpleName() + "` Create one with `/selection_alias add " + getClassName(type) + "`");
                    }
                    throw new IllegalArgumentException("Invalid selection alias: `" + inputAlias + "`. Options: `" + StringMan.join(options.keySet(), "`, `") + "` (use `$` or `select:` as the prefix). See also: " + CM.selection_alias.list.cmd.toSlashMention());
                }
            }
        }
        return null;
    }

    private Placeholders<Continent, Void> createContinents() {
        return new StaticPlaceholders<Continent>(Continent.class, Continent::values, store, validators, permisser,
                "One of the game continents",
                (ThrowingTriFunction<Placeholders<Continent, Void>, ValueStore, String, Set<Continent>>) (inst, store, input) -> {
                    Set<Continent> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(Continent.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("continent"), true, (type, str) -> PWBindings.continent(str));
                    }
                    return emumSet(Continent.class, input);
                }) {

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("continent");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of continents")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Continent> continents) {
                return _addSelectionAlias(this, command, db, name, continents, "continents");
            }

            @NoFormat
            @Command(desc = "Add columns to a Continent sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                      @Default TypedFunction<Continent, String> a,
                                      @Default TypedFunction<Continent, String> b,
                                      @Default TypedFunction<Continent, String> c,
                                      @Default TypedFunction<Continent, String> d,
                                      @Default TypedFunction<Continent, String> e,
                                      @Default TypedFunction<Continent, String> f,
                                      @Default TypedFunction<Continent, String> g,
                                      @Default TypedFunction<Continent, String> h,
                                      @Default TypedFunction<Continent, String> i,
                                      @Default TypedFunction<Continent, String> j,
                                      @Default TypedFunction<Continent, String> k,
                                      @Default TypedFunction<Continent, String> l,
                                      @Default TypedFunction<Continent, String> m,
                                      @Default TypedFunction<Continent, String> n,
                                      @Default TypedFunction<Continent, String> o,
                                      @Default TypedFunction<Continent, String> p,
                                      @Default TypedFunction<Continent, String> q,
                                      @Default TypedFunction<Continent, String> r,
                                      @Default TypedFunction<Continent, String> s,
                                      @Default TypedFunction<Continent, String> t,
                                      @Default TypedFunction<Continent, String> u,
                                      @Default TypedFunction<Continent, String> v,
                                      @Default TypedFunction<Continent, String> w,
                                      @Default TypedFunction<Continent, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                                k, l, m, n, o, p, q, r, s, t,
                                u, v, w, x);
            }
        };
    }

    private Set<NationOrAlliance> nationOrAlliancesSingle(ValueStore store, String input, boolean allowStar) {
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
        Guild guild = db == null ? null : db.getGuild();
        DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
        if (input.equalsIgnoreCase("*") && allowStar) {
            return new ObjectOpenHashSet<>(Locutus.imp().getNationDB().getAllNations());
        }
        if (MathMan.isInteger(input)) {
            long id = Long.parseLong(input);
            if (id < Integer.MAX_VALUE) {
                int idInt = (int) id;
                DBAlliance alliance = DBAlliance.get(idInt);
                if (alliance != null) return Set.of(alliance);
                DBNation nation = DBNation.getById(idInt);
                if (nation != null) return Set.of(nation);
            } else {
                User user = Locutus.imp().getDiscordApi().getUserById(id);
                if (user != null) {
                    DBNation nation = DiscordUtil.getNation(user);
                    if (nation == null) {
                        throw new IllegalArgumentException("User `" + DiscordUtil.getFullUsername(user) + "` is not registered. See " + CM.register.cmd.toSlashMention());
                    }
                    return Set.of(nation);
                }
                if (db != null) {
                    Role role = db.getGuild().getRoleById(id);
                    if (role != null) {
                        return (Set) NationPlaceholders.getByRole(db.getGuild(), input, role, Locutus.imp().getNationDB());
                    }
                } else {
                    DBNation nation = DiscordUtil.getNation(id);
                    if (nation != null) {
                        return Set.of(nation);
                    }
                }
            }
        }
        Integer aaId = PW.parseAllianceId(input);
        if (aaId != null) {
            return Set.of(DBAlliance.getOrCreate(aaId));
        }
        DBNation argNation = DiscordUtil.parseNation(input, true, false, guild);
        if (argNation != null) {
            return Set.of(argNation);
        }
        if (input.contains("tax_id=")) {
            int taxId = PW.parseTaxId(input);
            return (Set) Locutus.imp().getNationDB().getNationsByBracket(taxId);
        }
        if (input.startsWith("<@&") && db != null) {
            Role role = db.getGuild().getRoleById(input.substring(3, input.length() - 1));
            return (Set) NationPlaceholders.getByRole(db.getGuild(), input, role, Locutus.imp().getNationDB());
        }
        boolean isCoalition = false;
        String coalitionStr = input;
        if (input.charAt(0) == '~') {
            isCoalition = true;
            coalitionStr = input.substring(1);
        }
        if (input.startsWith("coalition:")) {
            coalitionStr = input.substring("coalition:".length());
            isCoalition = true;
        }
        Set<Integer> coalition = db.getCoalition(coalitionStr);
        if (!coalition.isEmpty()) {
            return coalition.stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
        }
        if (isCoalition) {
            throw new IllegalArgumentException("No alliances found for coalition `" + coalitionStr + "`. See " + CM.coalition.add.cmd.toSlashMention());
        }
        if (db != null) {
            // get role by name
            String finalInput = input;
            Role role = db.getGuild().getRoles().stream().filter(f -> f.getName().equalsIgnoreCase(finalInput)).findFirst().orElse(null);
            if (role != null) {
                return (Set) NationPlaceholders.getByRole(db.getGuild(), input, role, Locutus.imp().getNationDB());
            }
            for (Member member : db.getGuild().getMembers()) {
                User user = member.getUser();
                DBNation nation = DiscordUtil.getNation(user);
                if (nation == null) continue;
                if (member.getEffectiveName().equalsIgnoreCase(input) || user.getName().equalsIgnoreCase(input) || input.equalsIgnoreCase(user.getGlobalName())) {
                    return Set.of(nation);
                }
            }
        }
        if (!MathMan.isInteger(input)) {
            String inputLower = input.toLowerCase(Locale.ROOT);
            String best = null;
            double bestScore = Double.MAX_VALUE;
            for (DBNation nation : Locutus.imp().getNationDB().getAllNations()) {
                String name = nation.getName();
                double score = StringMan.distanceWeightedQwertSift4(name.toLowerCase(Locale.ROOT), inputLower);
                if (score < bestScore) {
                    bestScore = score;
                    best = "nation:" + name;
                }
                String leader = nation.getLeader();
                if (leader != null) {
                    score = StringMan.distanceWeightedQwertSift4(leader.toLowerCase(Locale.ROOT), inputLower);
                    if (score < bestScore) {
                        bestScore = score;
                        best = "nation:" + nation.getName();
                    }
                }
            }
            for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
                String name = alliance.getName();
                double score = StringMan.distanceWeightedQwertSift4(name.toLowerCase(Locale.ROOT), inputLower);
                if (score < bestScore) {
                    bestScore = score;
                    best = "aa:" + name;
                }
            }
            if (best != null) {
                throw new IllegalArgumentException("Invalid nation or alliance: `" + input + "`. Did you mean: `" + best + "`?");
            }
        }
        throw new IllegalArgumentException("Invalid nation or alliance: `" + input + "`");
    }

    private Placeholders<NationOrAlliance, NationModifier> createNationOrAlliances() {
        NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
        AlliancePlaceholders alliancePlaceholders = (AlliancePlaceholders) (Placeholders) get(DBAlliance.class);
        return new Placeholders<NationOrAlliance, NationModifier>(NationOrAlliance.class, NationModifier.class, store, validators, permisser) {
            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("nation", "alliance"));
            }

            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                Set<SelectorInfo> selectors = new ObjectLinkedOpenHashSet<>(NATIONS.getSelectorInfo());
                selectors.addAll(ALLIANCES.getSelectorInfo());
                return selectors;
            }

            @Override
            public String getDescription() {
                return "A nation or alliance";
            }

            @Override
            public String getName(NationOrAlliance o) {
                return o.getName();
            }

            @Binding(value = "A comma separated list of items")
            @Override
            public Set<NationOrAlliance> parseSet(ValueStore store2, String input) {
                if (input.contains("#")) {
                    return (Set) nationPlaceholders.parseSet(store2, input);
                }
                return super.parseSet(store2, input);
            }

            @Override
            protected Set<NationOrAlliance> parseSingleElem(ValueStore store, String input) {
                Set<DBNation> selection2 = getSelection(nationPlaceholders, store, input, false);
                if (selection2 != null) return (Set) selection2;
                Set<DBAlliance> selection3 = getSelection(alliancePlaceholders, store, input, false);
                if (selection3 != null) return (Set) selection3;
                Set<NationOrAlliance> selection = getSelection(this, store, input, true);
                if (selection != null) return selection;
                if (SpreadSheet.isSheet(input)) {
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    return SpreadSheet.parseSheet(input, List.of("nation", "alliance"), true, (type, str) -> {
                        switch (type) {
                            case 0:
                                return PWBindings.nation(null, guild, str);
                            case 1:
                                return PWBindings.alliance(str);
                        }
                        return null;
                    });
                }
                return nationOrAlliancesSingle(store, input, true);
            }

            @Override
            protected Predicate<NationOrAlliance> parseSingleFilter(ValueStore store, String input) {
                if (input.equalsIgnoreCase("*")) {
                    return Predicates.alwaysTrue();
                }
                Predicate<DBNation> predicate = nationPlaceholders.parseSingleFilter(store, input);
                return new Predicate<NationOrAlliance>() {
                    @Override
                    public boolean test(NationOrAlliance nationOrAlliance) {
                        if (nationOrAlliance.isNation()) {
                            return predicate.test(nationOrAlliance.asNation());
                        }
                        return false;
                    }
                };
            }
            @NoFormat
            @Command(desc = "Add an alias for a selection of NationOrAlliances")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<NationOrAlliance> nationoralliances) {
                return _addSelectionAlias(this, command, db, name, nationoralliances, "nationoralliances");
            }

            @NoFormat
            @Command(desc = "Add columns to a NationOrAlliance sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<NationOrAlliance, String> a,
                                     @Default TypedFunction<NationOrAlliance, String> b,
                                     @Default TypedFunction<NationOrAlliance, String> c,
                                     @Default TypedFunction<NationOrAlliance, String> d,
                                     @Default TypedFunction<NationOrAlliance, String> e,
                                     @Default TypedFunction<NationOrAlliance, String> f,
                                     @Default TypedFunction<NationOrAlliance, String> g,
                                     @Default TypedFunction<NationOrAlliance, String> h,
                                     @Default TypedFunction<NationOrAlliance, String> i,
                                     @Default TypedFunction<NationOrAlliance, String> j,
                                     @Default TypedFunction<NationOrAlliance, String> k,
                                     @Default TypedFunction<NationOrAlliance, String> l,
                                     @Default TypedFunction<NationOrAlliance, String> m,
                                     @Default TypedFunction<NationOrAlliance, String> n,
                                     @Default TypedFunction<NationOrAlliance, String> o,
                                     @Default TypedFunction<NationOrAlliance, String> p,
                                     @Default TypedFunction<NationOrAlliance, String> q,
                                     @Default TypedFunction<NationOrAlliance, String> r,
                                     @Default TypedFunction<NationOrAlliance, String> s,
                                     @Default TypedFunction<NationOrAlliance, String> t,
                                     @Default TypedFunction<NationOrAlliance, String> u,
                                     @Default TypedFunction<NationOrAlliance, String> v,
                                     @Default TypedFunction<NationOrAlliance, String> w,
                                     @Default TypedFunction<NationOrAlliance, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }

        };
    }

    private Placeholders<GuildDB, Void> createGuildDB() {
        return new SimpleVoidPlaceholders<GuildDB>(GuildDB.class, store, validators, permisser,
                "A discord guild",
                (ThrowingTriFunction<Placeholders<GuildDB, Void>, ValueStore, String, Set<GuildDB>>) (inst, store, input) -> {
                    Set<GuildDB> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    User user = (User) store.getProvided(Key.of(User.class, Me.class), true);
                    boolean admin = Roles.ADMIN.hasOnRoot(user);
                    if (input.equalsIgnoreCase("*")) {
                        if (admin) {
                            return new HashSet<>(Locutus.imp().getGuildDatabases().values());
                        }
                        List<Guild> guilds = user.getMutualGuilds();
                        Set<GuildDB> dbs = new HashSet<>();
                        for (Guild guild : guilds) {
                            GuildDB db = Locutus.imp().getGuildDB(guild);
                            if (db != null) {
                                dbs.add(db);
                            }
                        }
                        return dbs;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("guild", "guild_id"), true,
                                (type, str) -> PWBindings.guild(PrimitiveBindings.Long(str)));
                    }
                    long id = PrimitiveBindings.Long(input);
                    GuildDB guild = PWBindings.guild(id);
                    if (!admin && guild.getGuild().getMember(user) == null) {
                        throw new IllegalArgumentException("You (" + user + ") are not in the guild with id: `" + id + "`");
                    }
                    return Set.of(guild);
                }, (ThrowingTriFunction<Placeholders<GuildDB, Void>, ValueStore, String, Predicate<GuildDB>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) {
                return Predicates.alwaysTrue();
            }
            if (SpreadSheet.isSheet(input)) {
                Set<Long> sheet = SpreadSheet.parseSheet(input, List.of("guild"), true,
                        (type, str) -> PrimitiveBindings.Long(str));
                return f -> sheet.contains(f.getIdLong());
            }
            long id = PrimitiveBindings.Long(input);
            return f -> f.getIdLong() == id;
        }, new Function<GuildDB, String>() {
            @Override
            public String apply(GuildDB guildDB) {
                return guildDB.getGuild().toString();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("GUILD", "123456789012345678", "Guild ID"),
                        new SelectorInfo("*", null, "All shared guilds")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("guild");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of guilds")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<GuildDB> guilds) {
                return _addSelectionAlias(this, command, db, name, guilds, "guilds");
            }

            @NoFormat
            @Command(desc = "Add columns to a Guild sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<GuildDB, String> a,
                                     @Default TypedFunction<GuildDB, String> b,
                                     @Default TypedFunction<GuildDB, String> c,
                                     @Default TypedFunction<GuildDB, String> d,
                                     @Default TypedFunction<GuildDB, String> e,
                                     @Default TypedFunction<GuildDB, String> f,
                                     @Default TypedFunction<GuildDB, String> g,
                                     @Default TypedFunction<GuildDB, String> h,
                                     @Default TypedFunction<GuildDB, String> i,
                                     @Default TypedFunction<GuildDB, String> j,
                                     @Default TypedFunction<GuildDB, String> k,
                                     @Default TypedFunction<GuildDB, String> l,
                                     @Default TypedFunction<GuildDB, String> m,
                                     @Default TypedFunction<GuildDB, String> n,
                                     @Default TypedFunction<GuildDB, String> o,
                                     @Default TypedFunction<GuildDB, String> p,
                                     @Default TypedFunction<GuildDB, String> q,
                                     @Default TypedFunction<GuildDB, String> r,
                                     @Default TypedFunction<GuildDB, String> s,
                                     @Default TypedFunction<GuildDB, String> t,
                                     @Default TypedFunction<GuildDB, String> u,
                                     @Default TypedFunction<GuildDB, String> v,
                                     @Default TypedFunction<GuildDB, String> w,
                                     @Default TypedFunction<GuildDB, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<DBBan, Void> createBans() {
        return new SimpleVoidPlaceholders<DBBan>(DBBan.class,  store, validators, permisser,
                "A game ban",
                (ThrowingTriFunction<Placeholders<DBBan, Void>, ValueStore, String, Set<DBBan>>) (inst, store, input) -> {
                    Set<DBBan> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) {
                        return new HashSet<>(Locutus.imp().getNationDB().getBansByNation().values());
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("bans"), true, (type, str) -> PWBindings.ban(guild, str));
                    }
                    return Set.of(PWBindings.ban(guild, input));
                }, (ThrowingTriFunction<Placeholders<DBBan, Void>, ValueStore, String, Predicate<DBBan>>) (inst, store, input) -> {
                    if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
                    if (SpreadSheet.isSheet(input)) {
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        Set<Integer> sheet = SpreadSheet.parseSheet(input, List.of("bans"), true,
                                (type, str) -> DiscordUtil.parseNation(str, true, true, guild).getId());
                        return f -> sheet.contains(f.getNation_id());
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return f -> f.getNation_id() == id;
                    }
                    NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
                    Predicate<DBNation> filter = nationPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        DBNation nation = DBNation.getById(f.nation_id);
                        if (nation == null && f.discord_id > 0) {
                            nation = DiscordUtil.getNation(f.discord_id);
                        }
                        if (nation == null) return false;
                        return filter.test(nation);
                    };
                }, new Function<DBBan, String>() {
                    @Override
                    public String apply(DBBan dbBan) {
                        return dbBan.getNation_id() + "";
                    }
                }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("BAN", "1234", "Ban ID"),
                        new SelectorInfo("NATION", "189573", "Nation id, name, leader, url, user id or mention (see nation type)"),
                        new SelectorInfo("*", null, "All bans")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("bans");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of bans")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBBan> bans) {
                return _addSelectionAlias(this, command, db, name, bans, "bans");
            }

            @NoFormat
            @Command(desc = "Add columns to a Ban sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBBan, String> a,
                                     @Default TypedFunction<DBBan, String> b,
                                     @Default TypedFunction<DBBan, String> c,
                                     @Default TypedFunction<DBBan, String> d,
                                     @Default TypedFunction<DBBan, String> e,
                                     @Default TypedFunction<DBBan, String> f,
                                     @Default TypedFunction<DBBan, String> g,
                                     @Default TypedFunction<DBBan, String> h,
                                     @Default TypedFunction<DBBan, String> i,
                                     @Default TypedFunction<DBBan, String> j,
                                     @Default TypedFunction<DBBan, String> k,
                                     @Default TypedFunction<DBBan, String> l,
                                     @Default TypedFunction<DBBan, String> m,
                                     @Default TypedFunction<DBBan, String> n,
                                     @Default TypedFunction<DBBan, String> o,
                                     @Default TypedFunction<DBBan, String> p,
                                     @Default TypedFunction<DBBan, String> q,
                                     @Default TypedFunction<DBBan, String> r,
                                     @Default TypedFunction<DBBan, String> s,
                                     @Default TypedFunction<DBBan, String> t,
                                     @Default TypedFunction<DBBan, String> u,
                                     @Default TypedFunction<DBBan, String> v,
                                     @Default TypedFunction<DBBan, String> w,
                                     @Default TypedFunction<DBBan, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<NationList, NationModifier> createNationList() {
        return new SimplePlaceholders<NationList, NationModifier>(NationList.class, NationModifier.class, store, validators, permisser,
                "One or more groups of nations",
                (ThrowingTriFunction<Placeholders<NationList, NationModifier>, ValueStore, String, Set<NationList>>) (inst, store, input) -> {
                    Set<NationList> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    User author = (User) store.getProvided(Key.of(User.class, Me.class), false);
                    DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);

                    if (SpreadSheet.isSheet(input)) {
                        Set<String> inputs = SpreadSheet.parseSheet(input, List.of("nations"), true, (type, str) -> str);
                        Set<NationList> lists = new HashSet<>();
                        for (String str : inputs) {
                            Set<DBNation> nations = PWBindings.nations(null, guild, str, author, me);
                            lists.add(new SimpleNationList(nations).setFilter(str));
                        }
                        return lists;
                    }
                    Predicate<DBNation> filter = null;
                    String filterName = "";
                    int index = input.indexOf('[');
                    if (index != -1) {
                        String filterStr = input.substring(index + 1, input.length() - 1);
                        filterName = "[" + filterStr + "]";
                        input = input.substring(0, index);
                        NationPlaceholders placeholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
                        filter = placeholders.parseFilter(store, filterStr);
                    }

                    List<NationList> lists = new ArrayList<>();

                    if (input.isEmpty() || input.equalsIgnoreCase("*")) {
                        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances();
                        for (DBAlliance alliance : alliances) {
                            lists.add(new SimpleNationList(alliance.getNations(filter)).setFilter(filterName));
                        }
                    } else if (input.equalsIgnoreCase("~")) {
                        GuildDB db = guild == null ? null : Locutus.imp().getGuildDB(guild);
                        if (db == null) {
                            db = Locutus.imp().getRootCoalitionServer();
                        }
                        if (db == null) {
                            throw new IllegalArgumentException("No coalition server found, please have the bot owner set one in the `config.yaml`");
                        }
                        for (String coalition : db.getCoalitionNames()) {
                            lists.add(new SimpleNationList(Locutus.imp().getNationDB().getNationsByAlliance(db.getCoalition(coalition))).setFilter(filterName));
                        }
                    } else {
                        NationPlaceholders placeholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
                        lists.add(new SimpleNationList(placeholders.parseSet(store, input)).setFilter(filterName));
                    }
                    Set<NationList> result = new HashSet<>();
                    if (filter != null) {
                        for (NationList list : lists) {
                            List<DBNation> newNations = list.getNations().stream().filter(filter).toList();
                            if (!newNations.isEmpty()) {
                                result.add(new SimpleNationList(newNations).setFilter(list.getFilter()));
                            }
                        }
                    } else {
                        result.addAll(lists);
                    }
                    return result;
                }, (ThrowingTriFunction<Placeholders<NationList, NationModifier>, ValueStore, String, Predicate<NationList>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            throw new IllegalArgumentException("NationList predicates other than `*` are unsupported. Please use DBNation instead");
        }, new Function<NationList, String>() {
            @Override
            public String apply(NationList nationList) {
                return nationList.getFilter();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("*", null, "A single list with all nations"),
                        new SelectorInfo("~", null, "A set of nation lists for each coalition in the server"),
                        new SelectorInfo("coalition:COALITION_NAME", "coalition:allies", "A single list with the nations in the coalition"),
                        new SelectorInfo("NATION", "Borg", "Nation name, id, leader, url, user id or mention (see nation type)"),
                        new SelectorInfo("ALLIANCE", "AA:Rose", "Alliance id, name, url or mention (see alliance type)"),
                        new SelectorInfo("SELECTOR[FILTER]", "`*[#cities>10]`, `AA:Rose[#position>1,#vm_turns=0]`", "A single nation list based on a selector with an optional filter")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("nations");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of nationlists")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<NationList> nationlists) {
                return _addSelectionAlias(this, command, db, name, nationlists, "nationlists");
            }

            @NoFormat
            @Command(desc = "Add columns to a NationList sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<NationList, String> a,
                                     @Default TypedFunction<NationList, String> b,
                                     @Default TypedFunction<NationList, String> c,
                                     @Default TypedFunction<NationList, String> d,
                                     @Default TypedFunction<NationList, String> e,
                                     @Default TypedFunction<NationList, String> f,
                                     @Default TypedFunction<NationList, String> g,
                                     @Default TypedFunction<NationList, String> h,
                                     @Default TypedFunction<NationList, String> i,
                                     @Default TypedFunction<NationList, String> j,
                                     @Default TypedFunction<NationList, String> k,
                                     @Default TypedFunction<NationList, String> l,
                                     @Default TypedFunction<NationList, String> m,
                                     @Default TypedFunction<NationList, String> n,
                                     @Default TypedFunction<NationList, String> o,
                                     @Default TypedFunction<NationList, String> p,
                                     @Default TypedFunction<NationList, String> q,
                                     @Default TypedFunction<NationList, String> r,
                                     @Default TypedFunction<NationList, String> s,
                                     @Default TypedFunction<NationList, String> t,
                                     @Default TypedFunction<NationList, String> u,
                                     @Default TypedFunction<NationList, String> v,
                                     @Default TypedFunction<NationList, String> w,
                                     @Default TypedFunction<NationList, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Set<DBCity> parseCitiesSingle(ValueStore store, String input) {
        if (MathMan.isInteger(input) || input.contains("/city/id=")) {
            return Set.of(PWBindings.cityUrl(input));
        }
        NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
        Set<DBNation> nations = nationPlaceholders.parseSingleElem(store, input, false);
        Set<DBCity> cities = new ObjectLinkedOpenHashSet<>();
        for (DBNation nation : nations) {
            cities.addAll(nation._getCitiesV3().values());
        }
        return cities;
    }

    private Set<UserWrapper> parseUserSingle(Guild guild, String input) {
        // *
        if (input.equalsIgnoreCase("*")) {
            return guild.getMembers().stream().map(UserWrapper::new).collect(Collectors.toSet());
        }
        // username
        List<Member> members = guild.getMembersByName(input, true);
        if (!members.isEmpty()) {
            return members.stream().map(UserWrapper::new).collect(Collectors.toSet());
        }
        // user id / mention
        User user = DiscordUtil.getUser(input, guild);
        if (user != null) {
            return new ObjectLinkedOpenHashSet<>(List.of(new UserWrapper(guild, user)));
        }
        // Role
        Role role = DiscordUtil.getRole(guild, input);
        if (role != null) {
            return guild.getMembersWithRoles(role).stream().map(UserWrapper::new).collect(Collectors.toSet());
        }
        NationOrAlliance natOrAA = PWBindings.nationOrAlliance(input, guild);
        if (natOrAA.isNation()) {
            user = natOrAA.asNation().getUser();
            if (user == null) {
                throw new IllegalArgumentException("Nation " + natOrAA.getMarkdownUrl() + " is not registered. See: " + CM.register.cmd.toSlashMention());
            }
            return new ObjectLinkedOpenHashSet<>(List.of(new UserWrapper(guild, user)));
        }
        return natOrAA.asAlliance().getNations().stream().map(f -> {
            Long id = f.getUserId();
            return id != null ? guild.getMemberById(id) : null;
        }).filter(Objects::nonNull).map(UserWrapper::new).collect(Collectors.toSet());
    }

    private Set<TextChannelWrapper> parseChannelSingle(Guild guild, String input) {
        if (input.equals("*")) {
            return guild.getTextChannels().stream().map(TextChannelWrapper::new).collect(Collectors.toSet());
        }
        TextChannel channel = DiscordBindings.textChannel(guild, input);
        return Set.of(new TextChannelWrapper(channel));
    }

    private Predicate<TextChannelWrapper> parseChannelPredicate(Guild guild, String input) {
        if (input.equals("*")) return Predicates.alwaysTrue();
        Long channelId = DiscordUtil.getChannelId(guild, input);
        if (channelId != null) {
            return f -> f.getId() == channelId;
        }
        String inputFinal = input.startsWith("#") ? input.substring(1) : input;
        return f -> {
            TextChannel channel = f.getChannel();
            return channel != null && channel.getName().equalsIgnoreCase(inputFinal);
        };
    }

    private Predicate<UserWrapper> parseUserPredicate(Guild guild, String input) {
        boolean canRole;
        boolean canUser;
        if (input.startsWith("<@&")) {
            canRole = true;
            canUser = false;
            input = input.substring(3, input.length() - 1);
        } else {
            canUser = true;
            if (input.startsWith("<@!") || input.startsWith("<@")) {
                canRole = false;
                input = input.replace("!", "");
                input = input.substring(2, input.length() - 1);
            } else {
                canRole = true;
            }
        }
        if (MathMan.isInteger(input)) {
            long id = Long.parseLong(input);
            if (id > Integer.MAX_VALUE) {
                return f -> {
                    if (canUser && f.getUserId() == id) return true;
                    if (canRole) {
                        Member member = f.getMember();
                        if (member != null) {
                            for (Role role : member.getUnsortedRoles()) {
                                if (role.getIdLong() == id) return true;
                            }
                        }
                    }
                    return false;
                };
            }
            int intId = (int) id;
            return f -> {
                DBNation nation = f.getNation();
                if (nation != null) {
                    return nation.getId() == intId || nation.getAlliance_id() == intId;
                }
                return false;
            };
        }
        Long id = DiscordUtil.parseUserId(guild, input);
        if (id != null) {
            return f -> f.getUserId() == id;
        }
        DBNation argNation = DiscordUtil.parseNation(input, true, false, guild);
        if (argNation != null) {
            int nationId = argNation.getId();
            return f -> {
                DBNation nation = f.getNation();
                if (nation != null) {
                    return nation.getId() == nationId;
                }
                return false;
            };
        }
        Set<Integer> allianceId = DiscordUtil.parseAllianceIds(guild, input);
        if (allianceId != null && !allianceId.isEmpty()) {
            return f -> {
                DBNation nation = f.getNation();
                if (nation != null) {
                    return allianceId.contains(nation.getAlliance_id());
                }
                return false;
            };
        }
        String finalInput = input;
        return f -> {
            Member member = f.getMember();
            if (member != null) {
                for (Role role : member.getUnsortedRoles()) {
                    if (role.getName().equalsIgnoreCase(finalInput)) return true;
                }
            }
            return false;
        };
    }

    private Placeholders<UserWrapper, Void> createUsers() {
        return new SimpleVoidPlaceholders<UserWrapper>(UserWrapper.class, store, validators, permisser,
                "A discord user",
                (ThrowingTriFunction<Placeholders<UserWrapper, Void>, ValueStore, String, Set<UserWrapper>>) (inst, store, input) -> {
                    Set<UserWrapper> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                    Guild guild = db.getGuild();
                    if (SpreadSheet.isSheet(input)) {
                        Set<Member> member = SpreadSheet.parseSheet(input, List.of("user"), true, (type, str) -> DiscordBindings.member(guild, null, str));
                        return member.stream().map(UserWrapper::new).collect(Collectors.toSet());
                    }
                    return parseUserSingle(guild, input);
                }, (ThrowingTriFunction<Placeholders<UserWrapper, Void>, ValueStore, String, Predicate<UserWrapper>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();

            GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
            Guild guild = db.getGuild();

            if (SpreadSheet.isSheet(input)) {
                Set<Long> sheet = SpreadSheet.parseSheet(input, List.of("user"), true,
                        (type, str) -> DiscordUtil.parseUserId(guild, str));
                return f -> sheet.contains(f.getUserId());
            }
            return parseUserPredicate(guild, input);
        }, new Function<UserWrapper, String>() {
            @Override
            public String apply(UserWrapper userWrapper) {
                return userWrapper.getUserName();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("USER", "Borg", "Discord user name"),
                        new SelectorInfo("USER_ID", "123456789012345678", "Discord user id"),
                        new SelectorInfo("@ROLE", "@Member", "All users with a discord role by a given name or mention"),
                        new SelectorInfo("ROLE_ID", "123456789012345678", "All users with the discord role by a given id"),
                        new SelectorInfo("NATION", "Borg", "Nation name, id, leader, url, user id or mention (see nation type) - only if registered with Locutus"),
                        new SelectorInfo("ALLIANCE", "AA:Rose", "Alliance id, name, url or mention (see alliance type), resolves to the users registered with Locutus"),
                        new SelectorInfo("*", null, "All shared users")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("user");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of users")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<UserWrapper> users) {
                return _addSelectionAlias(this, command, db, name, users, "users");
            }

            @NoFormat
            @Command(desc = "Add columns to a User sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<UserWrapper, String> a,
                                     @Default TypedFunction<UserWrapper, String> b,
                                     @Default TypedFunction<UserWrapper, String> c,
                                     @Default TypedFunction<UserWrapper, String> d,
                                     @Default TypedFunction<UserWrapper, String> e,
                                     @Default TypedFunction<UserWrapper, String> f,
                                     @Default TypedFunction<UserWrapper, String> g,
                                     @Default TypedFunction<UserWrapper, String> h,
                                     @Default TypedFunction<UserWrapper, String> i,
                                     @Default TypedFunction<UserWrapper, String> j,
                                     @Default TypedFunction<UserWrapper, String> k,
                                     @Default TypedFunction<UserWrapper, String> l,
                                     @Default TypedFunction<UserWrapper, String> m,
                                     @Default TypedFunction<UserWrapper, String> n,
                                     @Default TypedFunction<UserWrapper, String> o,
                                     @Default TypedFunction<UserWrapper, String> p,
                                     @Default TypedFunction<UserWrapper, String> q,
                                     @Default TypedFunction<UserWrapper, String> r,
                                     @Default TypedFunction<UserWrapper, String> s,
                                     @Default TypedFunction<UserWrapper, String> t,
                                     @Default TypedFunction<UserWrapper, String> u,
                                     @Default TypedFunction<UserWrapper, String> v,
                                     @Default TypedFunction<UserWrapper, String> w,
                                     @Default TypedFunction<UserWrapper, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };

    }

    private Placeholders<TextChannelWrapper, Void> createChannels() {
        return new SimpleVoidPlaceholders<TextChannelWrapper>(TextChannelWrapper.class, store, validators, permisser,
                "A discord channel",
                (ThrowingTriFunction<Placeholders<TextChannelWrapper, Void>, ValueStore, String, Set<TextChannelWrapper>>) (inst, store, input) -> {
                    Set<TextChannelWrapper> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                    Guild guild = db.getGuild();
                    if (SpreadSheet.isSheet(input)) {
                        Set<TextChannel> channels = SpreadSheet.parseSheet(input, List.of("channel"), true, (type, str) -> DiscordBindings.textChannel(guild, str));
                        return channels.stream().map(TextChannelWrapper::new).collect(Collectors.toSet());
                    }
                    return parseChannelSingle(guild, input);
                }, (ThrowingTriFunction<Placeholders<TextChannelWrapper, Void>, ValueStore, String, Predicate<TextChannelWrapper>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();

            GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
            Guild guild = db.getGuild();

            if (SpreadSheet.isSheet(input)) {
                Set<Long> sheet = SpreadSheet.parseSheet(input, List.of("channel"), true,
                        (type, str) -> DiscordUtil.getChannelId(guild, str));
                return f -> sheet.contains(f.getId());
            }
            return parseChannelPredicate(guild, input);
        }, new Function<TextChannelWrapper, String>() {
            @Override
            public String apply(TextChannelWrapper channelWrapper) {
                return channelWrapper.getMention();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("#CHANNEL", "#general", "Discord channel name"),
                        new SelectorInfo("CHANNEL_ID", "123456789012345678", "Discord channel id"),
                        new SelectorInfo("*", null, "All guild channels")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("channel");
            }
//
//            @NoFormat
//            @Command(desc = "Add an alias for a selection of channels")
//            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
//            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<TextChannelWrapper> channels) {
//                return _addSelectionAlias(this, command, db, name, channels, "channels");
//            }
//
//            @NoFormat
//            @Command(desc = "Add columns to a channel sheet")
//            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
//            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
//                                     @Default TypedFunction<TextChannelWrapper, String> a,
//                                     @Default TypedFunction<TextChannelWrapper, String> b,
//                                     @Default TypedFunction<TextChannelWrapper, String> c,
//                                     @Default TypedFunction<TextChannelWrapper, String> d,
//                                     @Default TypedFunction<TextChannelWrapper, String> e,
//                                     @Default TypedFunction<TextChannelWrapper, String> f,
//                                     @Default TypedFunction<TextChannelWrapper, String> g,
//                                     @Default TypedFunction<TextChannelWrapper, String> h,
//                                     @Default TypedFunction<TextChannelWrapper, String> i,
//                                     @Default TypedFunction<TextChannelWrapper, String> j,
//                                     @Default TypedFunction<TextChannelWrapper, String> k,
//                                     @Default TypedFunction<TextChannelWrapper, String> l,
//                                     @Default TypedFunction<TextChannelWrapper, String> m,
//                                     @Default TypedFunction<TextChannelWrapper, String> n,
//                                     @Default TypedFunction<TextChannelWrapper, String> o,
//                                     @Default TypedFunction<TextChannelWrapper, String> p,
//                                     @Default TypedFunction<TextChannelWrapper, String> q,
//                                     @Default TypedFunction<TextChannelWrapper, String> r,
//                                     @Default TypedFunction<TextChannelWrapper, String> s,
//                                     @Default TypedFunction<TextChannelWrapper, String> t,
//                                     @Default TypedFunction<TextChannelWrapper, String> u,
//                                     @Default TypedFunction<TextChannelWrapper, String> v,
//                                     @Default TypedFunction<TextChannelWrapper, String> w,
//                                     @Default TypedFunction<TextChannelWrapper, String> x) throws GeneralSecurityException, IOException {
//                return Placeholders._addColumns(this, command,db, io, author, sheet,
//                        a, b, c, d, e, f, g, h, i, j,
//                        k, l, m, n, o, p, q, r, s, t,
//                        u, v, w, x);
//            }
        };

    }

    private Placeholders<DBCity, Void> createCities() {
        return new SimpleVoidPlaceholders<DBCity>(DBCity.class, store, validators, permisser,
                "A city",
                (ThrowingTriFunction<Placeholders<DBCity, Void>, ValueStore, String, Set<DBCity>>) (inst, store, input) -> {
                    Set<DBCity> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) {
                        Locutus.imp().getNationDB().getCities();
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Set<DBCity>> result = SpreadSheet.parseSheet(input, List.of("city", "cities"), true, (type, str) -> parseCitiesSingle(store, str));
                        Set<DBCity> cities = new ObjectLinkedOpenHashSet<>();
                        for (Set<DBCity> set : result) {
                            cities.addAll(set);
                        }
                        return cities;
                    }
                    return parseCitiesSingle(store, input);
                }, (ThrowingTriFunction<Placeholders<DBCity, Void>, ValueStore, String, Predicate<DBCity>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            if (MathMan.isInteger(input) || input.contains("/city/id=")) {
                DBCity city = PWBindings.cityUrl(input);
                return f -> f.getId() == city.getId();
            }
            if (SpreadSheet.isSheet(input)) {
                Set<Set<DBCity>> result = SpreadSheet.parseSheet(input, List.of("city", "cities"), true, (type, str) -> parseCitiesSingle(store, str));
                Set<Integer> cityIds = new IntOpenHashSet();
                for (Set<DBCity> set : result) {
                    for (DBCity city : set) {
                        cityIds.add(city.getId());
                    }
                }
                return f -> cityIds.contains(f.getId());
            }
            NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
            Predicate<DBNation> filter = nationPlaceholders.parseSingleFilter(store, input);
            return f -> {
                DBNation nation = DBNation.getById(f.getNation_id());
                if (nation == null) return false;
                return filter.test(nation);
            };
        }, new Function<DBCity, String>() {
            @Override
            public String apply(DBCity dbCity) {
                return dbCity.getId() + "";
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("CITY_ID", "12345", "City ID"),
                        new SelectorInfo("CITY_URL", "/city/id=12345", "City URL"),
                        new SelectorInfo("NATION", "Borg", "Nation name, id, leader, url, user id or mention (see nation type)"),
                        new SelectorInfo("*", null, "All cities")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("city", "cities"));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of cities")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBCity> cities) {
                return _addSelectionAlias(this, command, db, name, cities, "cities");
            }

            @NoFormat
            @Command(desc = "Add columns to a City sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBCity, String> a,
                                     @Default TypedFunction<DBCity, String> b,
                                     @Default TypedFunction<DBCity, String> c,
                                     @Default TypedFunction<DBCity, String> d,
                                     @Default TypedFunction<DBCity, String> e,
                                     @Default TypedFunction<DBCity, String> f,
                                     @Default TypedFunction<DBCity, String> g,
                                     @Default TypedFunction<DBCity, String> h,
                                     @Default TypedFunction<DBCity, String> i,
                                     @Default TypedFunction<DBCity, String> j,
                                     @Default TypedFunction<DBCity, String> k,
                                     @Default TypedFunction<DBCity, String> l,
                                     @Default TypedFunction<DBCity, String> m,
                                     @Default TypedFunction<DBCity, String> n,
                                     @Default TypedFunction<DBCity, String> o,
                                     @Default TypedFunction<DBCity, String> p,
                                     @Default TypedFunction<DBCity, String> q,
                                     @Default TypedFunction<DBCity, String> r,
                                     @Default TypedFunction<DBCity, String> s,
                                     @Default TypedFunction<DBCity, String> t,
                                     @Default TypedFunction<DBCity, String> u,
                                     @Default TypedFunction<DBCity, String> v,
                                     @Default TypedFunction<DBCity, String> w,
                                     @Default TypedFunction<DBCity, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Predicate<Transaction2> getAllowed(DBNation nation, User user, GuildDB db) {
        Predicate<Integer> allowAlliance;
        if (user != null && db != null) {
            Set<Integer> aaIds = db.getAllianceIds();
            boolean canSee = Roles.hasAny(user, db.getGuild(), Roles.ECON_STAFF, Roles.INTERNAL_AFFAIRS);
            if (canSee) {
                allowAlliance = aaIds::contains;
            } else {
                allowAlliance = Predicates.alwaysFalse();
            }
        } else {
            allowAlliance = Predicates.alwaysFalse();
        }
        return f -> {
            if (f.isSenderNation() || f.isReceiverNation()) {
                return false;
            }
            if (nation != null && f.banker_nation == nation.getId()) {
                return false;
            }
            boolean allowSender = allowAlliance.test((int) f.getSender());
            boolean allowReceiver = allowAlliance.test((int) f.getReceiver());
            return allowSender || allowReceiver;
        };
    }

    private Set<Transaction2> filterTransactions(DBNation nation, User user, GuildDB db, List<Transaction2> records) {
        Predicate<Transaction2> filter = getAllowed(nation, user, db);
        Set<Transaction2> result = new ObjectLinkedOpenHashSet<>(records.size());
        for (Transaction2 record : records) {
            if (filter.test(record)) {
                result.add(record);
            }
        }
        return result;
    }

    private Set<TaxBracket> bracketSingle(ValueStore store, GuildDB db, String input) {
        if (input.contains("tx_id=") || MathMan.isInteger(input)) {
            TaxBracket bracket = PWBindings.bracket(db, input);
            return Set.of(bracket);
        }
        NationPlaceholders natFormat = (NationPlaceholders) (Placeholders) get(DBNation.class);
        Set<DBNation> nations = natFormat.parseSingleElem(store, input, false);
        Set<TaxBracket> brackets = new ObjectOpenHashSet<>();
        Set<Integer> ids = new IntOpenHashSet();
        for (DBNation nation : nations) {
            if (nation.getPositionEnum().id <= Rank.APPLICANT.id || ids.contains(nation.getTax_id())) continue;
            ids.add(nation.getTax_id());
            brackets.add(nation.getTaxBracket());
        }
        return brackets;
    }


    private Predicate<TaxDeposit> getCanView(ValueStore store) {
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
        User user = (User) store.getProvided(Key.of(User.class, Me.class), false);
        DBNation nation = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
        boolean hasEcon = user != null && db != null && Roles.ECON_STAFF.has(user, db.getGuild());
        return new Predicate<TaxDeposit>() {
            @Override
            public boolean test(TaxDeposit record) {
                if (nation != null && nation.getId() == record.nationId) return true;
                if (db == null) return false;
                if (!db.isAllianceId(record.allianceId)) return false;
                return hasEcon;
            }
        };
    }

    private Set<TaxDeposit> getTaxes(ValueStore store, Set<Integer> ids, Set<Integer> taxIds, Set<Integer> nations, Set<Integer> aaIds) {
        BankDB bankDb = Locutus.imp().getBankDB();
        Predicate<TaxDeposit> canView = getCanView(store);
        Set<TaxDeposit> result = new ObjectLinkedOpenHashSet<>();

        if (ids != null && !ids.isEmpty()) {
            bankDb.getTaxesByIds(ids).stream().filter(canView).forEach(result::add);
        }
        if (taxIds != null && !taxIds.isEmpty()) {
            bankDb.getTaxesByBrackets(taxIds).stream().filter(canView).forEach(result::add);
        }
        if (nations != null && !nations.isEmpty()) {
            bankDb.getTaxesByNations(nations).stream().filter(canView).forEach(result::add);
        }
        if (aaIds != null && !aaIds.isEmpty()) {
            bankDb.getTaxesByAA(aaIds).stream().filter(canView).forEach(result::add);
        }
        return result;
    }

    public Placeholders<TaxDeposit, Void> createTaxDeposit() {
        return new SimpleVoidPlaceholders<TaxDeposit>(TaxDeposit.class, store, validators, permisser,
                "A tax record",
                (ThrowingTriFunction<Placeholders<TaxDeposit, Void>, ValueStore, String, Set<TaxDeposit>>) (inst, store, input) -> {
                    Set<TaxDeposit> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) {
                        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                        Set<Integer> aaIds = db.getAllianceIds();
                        if (aaIds.isEmpty()) {
                            return new HashSet<>();
                        }
                        return getTaxes(store, null, null, null, aaIds);
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = new IntOpenHashSet();
                        Set<Integer> taxIds = new IntOpenHashSet();
                        Set<Integer> nations = new IntOpenHashSet();
                        SpreadSheet.parseSheet(input, List.of("id", "tax_id", "nation"), true, (type, str) -> {
                            switch (type) {
                                case 0 -> ids.add(Integer.parseInt(str));
                                case 1 -> taxIds.add(Integer.parseInt(str));
                                case 2 -> nations.add(DiscordUtil.parseNation(str, true, true, guild).getId());
                            }
                            return null;
                        });
                        return getTaxes(store, ids, taxIds, nations, null);
                    }
                    if (MathMan.isInteger(input)) {
                        return getTaxes(store, Set.of(Integer.parseInt(input)), null, null, null);
                    }
                    if (input.contains("tax_id=")) {
                        int id = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                        return getTaxes(store, null, Set.of(id), null, null);
                    }
                    User author = (User) store.getProvided(Key.of(User.class, Me.class), false);
                    DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
                    Set<DBNation> nations = PWBindings.nations(null, guild, input, author, me);
                    Set<Integer> ids = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
                    return getTaxes(store, null, null, ids, null);

                }, (ThrowingTriFunction<Placeholders<TaxDeposit, Void>, ValueStore, String, Predicate<TaxDeposit>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            if (SpreadSheet.isSheet(input)) {
                Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                Set<Integer> ids = new IntOpenHashSet();
                Set<Integer> taxIds = new IntOpenHashSet();
                Set<Integer> nations = new IntOpenHashSet();
                SpreadSheet.parseSheet(input, List.of("id", "tax_id", "nation"), true, (type, str) -> {
                    switch (type) {
                        case 0 -> ids.add(Integer.parseInt(str));
                        case 1 -> taxIds.add(Integer.parseInt(str));
                        case 2 -> nations.add(DiscordUtil.parseNation(str, true, true, guild).getId());
                    }
                    return null;
                });
                return f -> {
                    if (ids.contains(f.index)) return true;
                    if (taxIds.contains(f.tax_id)) return true;
                    return nations.contains(f.nationId);
                };
            }
            if (MathMan.isInteger(input)) {
                int id = Integer.parseInt(input);
                return f -> f.tax_id == id;
            }
            if (input.contains("tax_id=")) {
                int id = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                return f -> f.tax_id == id;
            }
            NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
            Predicate<DBNation> nationFilter = nationPlaceholders.parseSingleFilter(store, input);
            return f -> {
                DBNation nation = DBNation.getOrCreate(f.nationId);
                return nationFilter.test(nation);
            };
        }, new Function<TaxDeposit, String>() {
            @Override
            public String apply(TaxDeposit taxDeposit) {
                return taxDeposit.toString();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("TAX_ID", "12345", "Tax record ID"),
                        new SelectorInfo("TAX_URL", "/tax/id=12345", "Tax URL"),
                        new SelectorInfo("NATION", "Borg", "Nation name, id, leader, url, user id or mention (see nation type) - if in this guild's alliance"),
                        new SelectorInfo("ALLIANCE", "AA:Rose", "Alliance id, name, url or mention (see alliance type) - if in this guild"),
                        new SelectorInfo("*", null, "All tax records with the guild")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id", "tax_id", "nation"));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of tax records")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<TaxDeposit> taxes) {
                return _addSelectionAlias(this, command, db, name, taxes, "taxes");
            }

            @NoFormat
            @Command(desc = "Add columns to a Bank TaxDeposit sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<TaxDeposit, String> a,
                                     @Default TypedFunction<TaxDeposit, String> b,
                                     @Default TypedFunction<TaxDeposit, String> c,
                                     @Default TypedFunction<TaxDeposit, String> d,
                                     @Default TypedFunction<TaxDeposit, String> e,
                                     @Default TypedFunction<TaxDeposit, String> f,
                                     @Default TypedFunction<TaxDeposit, String> g,
                                     @Default TypedFunction<TaxDeposit, String> h,
                                     @Default TypedFunction<TaxDeposit, String> i,
                                     @Default TypedFunction<TaxDeposit, String> j,
                                     @Default TypedFunction<TaxDeposit, String> k,
                                     @Default TypedFunction<TaxDeposit, String> l,
                                     @Default TypedFunction<TaxDeposit, String> m,
                                     @Default TypedFunction<TaxDeposit, String> n,
                                     @Default TypedFunction<TaxDeposit, String> o,
                                     @Default TypedFunction<TaxDeposit, String> p,
                                     @Default TypedFunction<TaxDeposit, String> q,
                                     @Default TypedFunction<TaxDeposit, String> r,
                                     @Default TypedFunction<TaxDeposit, String> s,
                                     @Default TypedFunction<TaxDeposit, String> t,
                                     @Default TypedFunction<TaxDeposit, String> u,
                                     @Default TypedFunction<TaxDeposit, String> v,
                                     @Default TypedFunction<TaxDeposit, String> w,
                                     @Default TypedFunction<TaxDeposit, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    public Placeholders<Conflict, Void> createConflicts() {
        return new SimpleVoidPlaceholders<Conflict>(Conflict.class, store, validators, permisser,
                "Public and registered alliance conflicts added to the bot\n" +
                        "Unlisted conflicts are not supported by conflict selectors",
                (ThrowingTriFunction<Placeholders<Conflict, Void>, ValueStore, String, Set<Conflict>>) (inst, store, input) -> {
                    Set<Conflict> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    ConflictManager manager = Locutus.imp().getWarDb().getConflicts();
                    if (input.equalsIgnoreCase("*")) {
                        return new ObjectLinkedOpenHashSet<>(manager.getConflictMap().values());
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("conflict"), true, (type, str) -> PWBindings.conflict(manager, str));
                    }
                    return Set.of(PWBindings.conflict(manager, input));
                }, (ThrowingTriFunction<Placeholders<Conflict, Void>, ValueStore, String, Predicate<Conflict>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            ConflictManager cMan = Locutus.imp().getWarDb().getConflicts();
            if (SpreadSheet.isSheet(input)) {
                Set<Conflict> conflicts = SpreadSheet.parseSheet(input, List.of("conflict"), true, (type, str) -> PWBindings.conflict(cMan, str));
                Set<Integer> ids = conflicts.stream().map(Conflict::getId).collect(Collectors.toSet());
                return f -> ids.contains(f.getId());
            }
            Conflict setting = PWBindings.conflict(cMan, input);
            return f -> f == setting;
        }, new Function<Conflict, String>() {
            @Override
            public String apply(Conflict conflict) {
                return conflict.getId() + "";
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("CONFLICT_ID", "12345", "Public Conflict ID"),
                        new SelectorInfo("CONFLICT_NAME", "Duck Hunt", "Public Conflict name, as stored by the bot"),
                        new SelectorInfo("*", null, "All public conflicts")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("conflict");
            }

            //            @NoFormat
//            @Command(desc = "Add an alias for a selection of conflicts")
//            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
//            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Conflict> conflicts) {
//                return _addSelectionAlias(this, command, db, name, conflicts, "conflicts");
//            }
//
//            @NoFormat
//            @Command(desc = "Add columns to a conflict sheet")
//            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
//            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
//                                     @Default TypedFunction<Conflict, String> a,
//                                     @Default TypedFunction<Conflict, String> b,
//                                     @Default TypedFunction<Conflict, String> c,
//                                     @Default TypedFunction<Conflict, String> d,
//                                     @Default TypedFunction<Conflict, String> e,
//                                     @Default TypedFunction<Conflict, String> f,
//                                     @Default TypedFunction<Conflict, String> g,
//                                     @Default TypedFunction<Conflict, String> h,
//                                     @Default TypedFunction<Conflict, String> i,
//                                     @Default TypedFunction<Conflict, String> j,
//                                     @Default TypedFunction<Conflict, String> k,
//                                     @Default TypedFunction<Conflict, String> l,
//                                     @Default TypedFunction<Conflict, String> m,
//                                     @Default TypedFunction<Conflict, String> n,
//                                     @Default TypedFunction<Conflict, String> o,
//                                     @Default TypedFunction<Conflict, String> p,
//                                     @Default TypedFunction<Conflict, String> q,
//                                     @Default TypedFunction<Conflict, String> r,
//                                     @Default TypedFunction<Conflict, String> s,
//                                     @Default TypedFunction<Conflict, String> t,
//                                     @Default TypedFunction<Conflict, String> u,
//                                     @Default TypedFunction<Conflict, String> v,
//                                     @Default TypedFunction<Conflict, String> w,
//                                     @Default TypedFunction<Conflict, String> x) throws GeneralSecurityException, IOException {
//                return Placeholders._addColumns(this, command,db, io, author, sheet,
//                        a, b, c, d, e, f, g, h, i, j,
//                        k, l, m, n, o, p, q, r, s, t,
//                        u, v, w, x);
//            }
        };
    }

    public Placeholders<GuildSetting, Void> createGuildSettings() {
        return new SimpleVoidPlaceholders<GuildSetting>(GuildSetting.class, store, validators, permisser,
                "A bot setting in a guild",
                (ThrowingTriFunction<Placeholders<GuildSetting, Void>, ValueStore, String, Set<GuildSetting>>) (inst, store, input) -> {
                    Set<GuildSetting> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) {
                        return new ObjectLinkedOpenHashSet<>(Arrays.asList(GuildKey.values()));
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("setting"), true, (type, str) -> PWBindings.key(str));
                    }
                    return Set.of(PWBindings.key(input));
                }, (ThrowingTriFunction<Placeholders<GuildSetting, Void>, ValueStore, String, Predicate<GuildSetting>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            if (SpreadSheet.isSheet(input)) {
                Set<GuildSetting> settings = SpreadSheet.parseSheet(input, List.of("setting"), true, (type, str) -> PWBindings.key(str));
                Set<GuildSetting> ids = new ObjectOpenHashSet<>(settings);
                return ids::contains;
            }
            GuildSetting setting = PWBindings.key(input);
            return f -> f == setting;
        }, new Function<GuildSetting, String>() {
            @Override
            public String apply(GuildSetting setting) {
                return setting.name();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("SETTING", GuildKey.ALLIANCE_ID.name(), "Guild setting name"),
                        new SelectorInfo("*", null, "All guild settings")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("setting");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of guild settings")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<GuildSetting> settings) {
                return _addSelectionAlias(this, command, db, name, settings, "settings");
            }

            @NoFormat
            @Command(desc = "Add columns to a Guild Setting sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<GuildSetting, String> a,
                                     @Default TypedFunction<GuildSetting, String> b,
                                     @Default TypedFunction<GuildSetting, String> c,
                                     @Default TypedFunction<GuildSetting, String> d,
                                     @Default TypedFunction<GuildSetting, String> e,
                                     @Default TypedFunction<GuildSetting, String> f,
                                     @Default TypedFunction<GuildSetting, String> g,
                                     @Default TypedFunction<GuildSetting, String> h,
                                     @Default TypedFunction<GuildSetting, String> i,
                                     @Default TypedFunction<GuildSetting, String> j,
                                     @Default TypedFunction<GuildSetting, String> k,
                                     @Default TypedFunction<GuildSetting, String> l,
                                     @Default TypedFunction<GuildSetting, String> m,
                                     @Default TypedFunction<GuildSetting, String> n,
                                     @Default TypedFunction<GuildSetting, String> o,
                                     @Default TypedFunction<GuildSetting, String> p,
                                     @Default TypedFunction<GuildSetting, String> q,
                                     @Default TypedFunction<GuildSetting, String> r,
                                     @Default TypedFunction<GuildSetting, String> s,
                                     @Default TypedFunction<GuildSetting, String> t,
                                     @Default TypedFunction<GuildSetting, String> u,
                                     @Default TypedFunction<GuildSetting, String> v,
                                     @Default TypedFunction<GuildSetting, String> w,
                                     @Default TypedFunction<GuildSetting, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Set<IAttack> getAttacks(Set<Integer> attackIds, Set<Integer> warIds, Set<Integer> nationIds, Set<Integer> alliances) {
        Set<IAttack> attacks = new ObjectOpenHashSet<>();
        if (warIds != null && !warIds.isEmpty()) {
            Set<DBWar> wars = Locutus.imp().getWarDb().getWarsById(warIds);
            if (!wars.isEmpty()) {
                Locutus.imp().getWarDb().iterateAttacksByWars(wars, (war, attack) -> {
                    attacks.add(attack);
                });
            }
        }
        if (attackIds != null && !attackIds.isEmpty()) {
            attacks.addAll(Locutus.imp().getWarDb().getAttacksById(attackIds));
        }
        if ((attackIds == null || attackIds.isEmpty()) && (warIds == null || warIds.isEmpty())) {
            if (nationIds != null && !nationIds.isEmpty()) {
                Locutus.imp().getWarDb().iterateAttacks(nationIds, 0, (war, attack) -> {
                    attacks.add(attack);
                });
            }
            if (alliances != null && !alliances.isEmpty()) {
                Set<DBWar> allWars = new ObjectOpenHashSet<>();
                for (Integer aaId : alliances) {
                    allWars.addAll(Locutus.imp().getWarDb().getWarsByAlliance(aaId));
                }
                Locutus.imp().getWarDb().iterateAttacksByWars(allWars, (war, attack) -> {
                    attacks.add(attack);
                });
            }
        }
        return attacks;
    }

    public Placeholders<IAttack, Void> createAttacks() {
        return new SimpleVoidPlaceholders<IAttack>(IAttack.class, store, validators, permisser,
                "An attack in a war",
                (ThrowingTriFunction<Placeholders<IAttack, Void>, ValueStore, String, Set<IAttack>>) (inst, store, input) -> {
                    Set<IAttack> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> attackIds = new IntOpenHashSet();
                        Set<Integer> warIds = new IntOpenHashSet();
                        Set<Integer> nationIds = new IntOpenHashSet();
                        Set<Integer> allianceIds = new IntOpenHashSet();
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        SpreadSheet.parseSheet(input, List.of("id", "war_id", "nation", "leader", "alliance"), true, (type, str) -> {
                            switch (type) {
                                case 0 -> attackIds.add(Integer.parseInt(str));
                                case 1 -> warIds.add(Integer.parseInt(str));
                                case 2 -> {
                                    DBNation nation = DiscordUtil.parseNation(str, true, guild);
                                    if (nation == null) throw new IllegalArgumentException("Invalid nation: `" + str + "`");
                                    nationIds.add(nation.getId());
                                }
                                case 3 -> {
                                    DBNation nation = Locutus.imp().getNationDB().getNationByLeader(str);
                                    if (nation == null) throw new IllegalArgumentException("Invalid nation leader: `" + str + "`");
                                    nationIds.add(nation.getId());
                                }
                                case 4 -> {
                                    DBAlliance alliance = PWBindings.alliance(str);
                                    if (alliance == null) throw new IllegalArgumentException("Invalid alliance: `" + str + "`");
                                    allianceIds.add(alliance.getId());
                                }
                            }
                            return null;
                        });
                        return getAttacks(attackIds, warIds, nationIds, allianceIds);
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        Set attacks = Locutus.imp().getWarDb().getAttacksById(Set.of(id));
                        return attacks;
                    }
                    if (input.contains("/war/id=")) {
                        int warId = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                        return getAttacks(Set.of(), Set.of(warId), null, null);
                    }
                    Set<NationOrAlliance> natOrAA = nationOrAlliancesSingle(store, input, false);
                    Set<Integer> nationIds = natOrAA.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
                    Set<Integer> aaIds = natOrAA.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
                    return getAttacks(Set.of(), Set.of(), nationIds, aaIds);
                }, (ThrowingTriFunction<Placeholders<IAttack, Void>, ValueStore, String, Predicate<IAttack>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            if (SpreadSheet.isSheet(input)) {
                Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                Set<Integer> attackIds = new IntOpenHashSet();
                Set<Integer> warIds = new IntOpenHashSet();
                Set<Integer> nationIds = new IntOpenHashSet();
                Set<Integer> allianceIds = new IntOpenHashSet();
                SpreadSheet.parseSheet(input, List.of("id", "war_id", "nation", "leader", "alliance"), true, (type, str) -> {
                    switch (type) {
                        case 0 -> attackIds.add(Integer.parseInt(str));
                        case 1 -> warIds.add(Integer.parseInt(str));
                        case 2 -> {
                            DBNation nation = DiscordUtil.parseNation(str, true, guild);
                            if (nation == null) throw new IllegalArgumentException("Invalid nation: `" + str + "`");
                            nationIds.add(nation.getId());
                        }
                        case 3 -> {
                            DBNation nation = Locutus.imp().getNationDB().getNationByLeader(str);
                            if (nation == null) throw new IllegalArgumentException("Invalid nation leader: `" + str + "`");
                            nationIds.add(nation.getId());
                        }
                        case 4 -> {
                            DBAlliance alliance = PWBindings.alliance(str);
                            if (alliance == null) throw new IllegalArgumentException("Invalid alliance: `" + str + "`");
                            allianceIds.add(alliance.getId());
                        }
                    }
                    return null;
                });
                Predicate<IAttack> filter = null;
                if (!attackIds.isEmpty()) {
                    filter = f -> attackIds.contains(f.getWar_attack_id());
                }
                if (!warIds.isEmpty()) {
                    Predicate<IAttack> warFilter = f -> warIds.contains(f.getWar_id());
                    filter = filter == null ? warFilter : filter.or(warFilter);
                }
                if (!nationIds.isEmpty()) {
                    Predicate<IAttack> nationFilter = f -> nationIds.contains(f.getAttacker_id()) || nationIds.contains(f.getDefender_id());
                    filter = filter == null ? nationFilter : filter.or(nationFilter);
                }
                if (!allianceIds.isEmpty()) {
                    Predicate<IAttack> aaFilter = f -> {
                        DBWar war = f.getWar();
                        if (war != null) {
                            return allianceIds.contains(war.getAttacker_aa()) || allianceIds.contains(war.getDefender_aa());
                        }
                        return false;
                    };
                    filter = filter == null ? aaFilter : filter.or(aaFilter);
                }
                if (filter == null) filter = Predicates.alwaysFalse();
                return filter;
            }
            if (input.contains("/war/id=")) {
                int id = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                return f -> f.getWar_id() == id;
            }
            if (MathMan.isInteger(input)) {
                int id = Integer.parseInt(input);
                return f -> f.getWar_attack_id() == id;
            }
            Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
            User author = (User) store.getProvided(Key.of(User.class, Me.class), false);
            DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
            Set<NationOrAlliance> allowed = PWBindings.nationOrAlliance(null, guild, input, true, author, me);
            return f -> {
                DBWar war = f.getWar();
                DBNation attacker = DBNation.getOrCreate(f.getAttacker_id());
                DBNation defender = DBNation.getOrCreate(f.getDefender_id());
                if (allowed.contains(attacker) || allowed.contains(defender)) return true;
                if (war == null) return false;
                DBAlliance attackerAA = war.getAttacker_aa() != 0 ? DBAlliance.getOrCreate(war.getAttacker_aa()) : null;
                if (attackerAA != null && allowed.contains(attackerAA)) return true;
                DBAlliance defenderAA = war.getDefender_aa() != 0 ? DBAlliance.getOrCreate(war.getDefender_aa()) : null;
                if (defenderAA != null && allowed.contains(defenderAA)) return true;
                return false;
            };
        }, new Function<IAttack, String>() {
            @Override
            public String apply(IAttack iAttack) {
                return iAttack.getWar_attack_id() + "";
            }
        }
        ) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                Set<SelectorInfo> mySet = new ObjectLinkedOpenHashSet<>(List.of(new SelectorInfo("ATTACK_ID", "123456", "Attack ID")));
                mySet.addAll(WARS.getSelectorInfo());
                return mySet;
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id", "war_id", "nation", "leader", "alliance"));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of attacks")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<IAttack> attacks) {
                return _addSelectionAlias(this, command, db, name, attacks, "attacks");
            }

            @NoFormat
            @Command(desc = "Add columns to an Attack sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<IAttack, String> a,
                                     @Default TypedFunction<IAttack, String> b,
                                     @Default TypedFunction<IAttack, String> c,
                                     @Default TypedFunction<IAttack, String> d,
                                     @Default TypedFunction<IAttack, String> e,
                                     @Default TypedFunction<IAttack, String> f,
                                     @Default TypedFunction<IAttack, String> g,
                                     @Default TypedFunction<IAttack, String> h,
                                     @Default TypedFunction<IAttack, String> i,
                                     @Default TypedFunction<IAttack, String> j,
                                     @Default TypedFunction<IAttack, String> k,
                                     @Default TypedFunction<IAttack, String> l,
                                     @Default TypedFunction<IAttack, String> m,
                                     @Default TypedFunction<IAttack, String> n,
                                     @Default TypedFunction<IAttack, String> o,
                                     @Default TypedFunction<IAttack, String> p,
                                     @Default TypedFunction<IAttack, String> q,
                                     @Default TypedFunction<IAttack, String> r,
                                     @Default TypedFunction<IAttack, String> s,
                                     @Default TypedFunction<IAttack, String> t,
                                     @Default TypedFunction<IAttack, String> u,
                                     @Default TypedFunction<IAttack, String> v,
                                     @Default TypedFunction<IAttack, String> w,
                                     @Default TypedFunction<IAttack, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    public Placeholders<DBWar, Void> createWars() {
        return new SimpleVoidPlaceholders<DBWar>(DBWar.class, store, validators, permisser,
                "A war",
                (ThrowingTriFunction<Placeholders<DBWar, Void>, ValueStore, String, Set<DBWar>>) (inst, store, input) -> {
                    Set<DBWar> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (SpreadSheet.isSheet(input)) {
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        Set<Integer> warIds = new IntOpenHashSet();
                        Set<Integer> nationIds = new IntOpenHashSet();
                        Set<Integer> allianceIds = new IntOpenHashSet();
                        SpreadSheet.parseSheet(input, List.of("id", "war_id", "nation", "leader", "alliance"), true, (type, str) -> {
                            switch (type) {
                                case 0, 1 -> warIds.add(Integer.parseInt(str));
                                case 2 -> {
                                    DBNation nation = DiscordUtil.parseNation(str, true, guild);
                                    if (nation == null) throw new IllegalArgumentException("Invalid nation: `" + str + "`");
                                    nationIds.add(nation.getId());
                                }
                                case 3 -> {
                                    DBNation nation = Locutus.imp().getNationDB().getNationByLeader(str);
                                    if (nation == null) throw new IllegalArgumentException("Invalid nation leader: `" + str + "`");
                                    nationIds.add(nation.getId());
                                }
                                case 4 -> {
                                    DBAlliance alliance = PWBindings.alliance(str);
                                    if (alliance == null) throw new IllegalArgumentException("Invalid alliance: `" + str + "`");
                                    allianceIds.add(alliance.getId());
                                }
                            }
                            return null;
                        });
                        if (!warIds.isEmpty()) {
                            return Locutus.imp().getWarDb().getWarsById(warIds);
                        }
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return Locutus.imp().getWarDb().getWarsById(Set.of(id));
                    }
                    if (input.contains("/war/id=")) {
                        int warId = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                        return Locutus.imp().getWarDb().getWarsById(Set.of(warId));
                    }
                    Set<NationOrAlliance> natOrAA = nationOrAlliancesSingle(store, input, false);
                    Set<Integer> natIds = natOrAA.stream().filter(NationOrAlliance::isNation).map(NationOrAlliance::getId).collect(Collectors.toSet());
                    Set<Integer> aaIds = natOrAA.stream().filter(NationOrAlliance::isAlliance).map(NationOrAlliance::getId).collect(Collectors.toSet());
                    return Locutus.imp().getWarDb().getWarsForNationOrAlliance(natIds, aaIds);
                }, (ThrowingTriFunction<Placeholders<DBWar, Void>, ValueStore, String, Predicate<DBWar>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            if (SpreadSheet.isSheet(input)) {
                Set<Integer> warIds = new IntOpenHashSet();
                SpreadSheet.parseSheet(input, List.of("id", "war_id"), true, (type, str) -> {
                    switch (type) {
                        case 0, 1 -> warIds.add(Integer.parseInt(str));
                    }
                    return null;
                });
                if (!warIds.isEmpty()) {
                    return f -> warIds.contains(f.getWarId());
                }
            }
            if (input.contains("/war/id=")) {
                int id = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                return f -> f.getWarId() == id;
            }
            if (MathMan.isInteger(input)) {
                int id = Integer.parseInt(input);
                return f -> f.getWarId() == id;
            }
            Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
            User author = (User) store.getProvided(Key.of(User.class, Me.class), false);
            DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
            Set<NationOrAlliance> allowed = PWBindings.nationOrAlliance(null, guild, input, true, author, me);
            return war -> {
                DBNation attacker = DBNation.getOrCreate(war.getAttacker_id());
                DBNation defender = DBNation.getOrCreate(war.getDefender_id());
                if (allowed.contains(attacker) || allowed.contains(defender)) return true;
                DBAlliance attackerAA = war.getAttacker_aa() != 0 ? DBAlliance.getOrCreate(war.getAttacker_aa()) : null;
                if (attackerAA != null && allowed.contains(attackerAA)) return true;
                DBAlliance defenderAA = war.getDefender_aa() != 0 ? DBAlliance.getOrCreate(war.getDefender_aa()) : null;
                if (defenderAA != null && allowed.contains(defenderAA)) return true;
                return false;
            };
        }, new Function<DBWar, String>() {
            @Override
            public String apply(DBWar dbWar) {
                return dbWar.getWarId() + "";
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("WAR_ID", "12345", "War ID"),
                        new SelectorInfo("NATION", "Borg", "Nation name, id, leader, url, user id or mention (see nation type)"),
                        new SelectorInfo("ALLIANCE", "AA:Rose", "Alliance id, name, url or mention (see alliance type)"),
                        new SelectorInfo("*", null, "All wars")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id", "war_id", "nation", "leader", "alliance"));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of wars")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBWar> wars) {
                return _addSelectionAlias(this, command, db, name, wars, "wars");
            }
            @NoFormat
            @Command(desc = "Add columns to a War sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBWar, String> a,
                                     @Default TypedFunction<DBWar, String> b,
                                     @Default TypedFunction<DBWar, String> c,
                                     @Default TypedFunction<DBWar, String> d,
                                     @Default TypedFunction<DBWar, String> e,
                                     @Default TypedFunction<DBWar, String> f,
                                     @Default TypedFunction<DBWar, String> g,
                                     @Default TypedFunction<DBWar, String> h,
                                     @Default TypedFunction<DBWar, String> i,
                                     @Default TypedFunction<DBWar, String> j,
                                     @Default TypedFunction<DBWar, String> k,
                                     @Default TypedFunction<DBWar, String> l,
                                     @Default TypedFunction<DBWar, String> m,
                                     @Default TypedFunction<DBWar, String> n,
                                     @Default TypedFunction<DBWar, String> o,
                                     @Default TypedFunction<DBWar, String> p,
                                     @Default TypedFunction<DBWar, String> q,
                                     @Default TypedFunction<DBWar, String> r,
                                     @Default TypedFunction<DBWar, String> s,
                                     @Default TypedFunction<DBWar, String> t,
                                     @Default TypedFunction<DBWar, String> u,
                                     @Default TypedFunction<DBWar, String> v,
                                     @Default TypedFunction<DBWar, String> w,
                                     @Default TypedFunction<DBWar, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    public Placeholders<TaxBracket, Void> createBrackets() {
        return new SimpleVoidPlaceholders<TaxBracket>(TaxBracket.class, store, validators, permisser,
                "A tax bracket",
                (ThrowingTriFunction<Placeholders<TaxBracket, Void>, ValueStore, String, Set<TaxBracket>>) (inst, store2, input) -> {
                    Set<TaxBracket> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    GuildDB db = (GuildDB) store2.getProvided(Key.of(GuildDB.class, Me.class), false);
                    if (input.equalsIgnoreCase("*")) {
                        if (db != null) {
                            AllianceList aaList = db.getAllianceList();
                            if (aaList != null) {
                                return new HashSet<TaxBracket>(aaList.getTaxBrackets(TimeUnit.MINUTES.toMillis(5)).values());
                            }
                        }
                        Map<Integer, Integer> ids = Locutus.imp().getNationDB().getAllianceIdByTaxId();
                        return ids.entrySet().stream().map(f -> new TaxBracket(f.getKey(), f.getValue(), "", -1, -1, 0)).collect(Collectors.toSet());
                    }
                    if (SpreadSheet.isSheet(input)) {
                        String finalInput = input;
                        Set<Set<TaxBracket>> result = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> bracketSingle(store2, db, finalInput));
                        Set<TaxBracket> brackets = new ObjectOpenHashSet<>();
                        for (Set<TaxBracket> set : result) {
                            brackets.addAll(set);
                        }
                        return brackets;
                    }
                    return bracketSingle(store2, db, input);
                }, (ThrowingTriFunction<Placeholders<TaxBracket, Void>, ValueStore, String, Predicate<TaxBracket>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) {
                return Predicates.alwaysTrue();
            }
            if (SpreadSheet.isSheet(input)) {
                Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> Integer.parseInt(str));
                return f -> ids.contains(f.getId());
            }
            if (input.contains("tx_id=") || MathMan.isInteger(input)) {
                int id = PW.parseTaxId(input);
                return f -> f.getId() == id;
            }
            AlliancePlaceholders aaPlaceholders = (AlliancePlaceholders) (Placeholders) get(DBAlliance.class);
            Predicate<DBAlliance> filter = aaPlaceholders.parseSingleFilter(store, input);
            return f -> {
                if (f.getId() == 0) return false;
                DBAlliance aa = f.getAlliance();
                if (aa == null) return false;
                return filter.test(aa);
            };
        }, new Function<TaxBracket, String>() {
            @Override
            public String apply(TaxBracket taxBracket) {
                return taxBracket.toString();
            }
        }
        ) {
            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id"));
            }

            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("TAX_ID", "tx_id=12345", "Tax Bracket ID"),
                        new SelectorInfo("ALLIANCE", "AA:Rose", "Alliance id, name, url or mention (see alliance type)"),
                        new SelectorInfo("*", null, "All tax brackets in this guilds alliances, else all tax brackets")
                ));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of tax brackets")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<TaxBracket> taxbrackets) {
                return _addSelectionAlias(this, command, db, name, taxbrackets, "taxbrackets");
            }

            @NoFormat
            @Command(desc = "Add columns to a TaxBracket sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<TaxBracket, String> a,
                                     @Default TypedFunction<TaxBracket, String> b,
                                     @Default TypedFunction<TaxBracket, String> c,
                                     @Default TypedFunction<TaxBracket, String> d,
                                     @Default TypedFunction<TaxBracket, String> e,
                                     @Default TypedFunction<TaxBracket, String> f,
                                     @Default TypedFunction<TaxBracket, String> g,
                                     @Default TypedFunction<TaxBracket, String> h,
                                     @Default TypedFunction<TaxBracket, String> i,
                                     @Default TypedFunction<TaxBracket, String> j,
                                     @Default TypedFunction<TaxBracket, String> k,
                                     @Default TypedFunction<TaxBracket, String> l,
                                     @Default TypedFunction<TaxBracket, String> m,
                                     @Default TypedFunction<TaxBracket, String> n,
                                     @Default TypedFunction<TaxBracket, String> o,
                                     @Default TypedFunction<TaxBracket, String> p,
                                     @Default TypedFunction<TaxBracket, String> q,
                                     @Default TypedFunction<TaxBracket, String> r,
                                     @Default TypedFunction<TaxBracket, String> s,
                                     @Default TypedFunction<TaxBracket, String> t,
                                     @Default TypedFunction<TaxBracket, String> u,
                                     @Default TypedFunction<TaxBracket, String> v,
                                     @Default TypedFunction<TaxBracket, String> w,
                                     @Default TypedFunction<TaxBracket, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<DBTrade, Void> createTrades() {
        return new SimpleVoidPlaceholders<DBTrade>(DBTrade.class, store, validators, permisser,
                "A completed trade",
                (ThrowingTriFunction<Placeholders<DBTrade, Void>, ValueStore, String, Set<DBTrade>>) (inst, store, input) -> {
                    Set<DBTrade> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) {
                        throw new UnsupportedOperationException("`*` is not supported. Only trade ids are supported");
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> Integer.parseInt(str));
                        return new ObjectOpenHashSet<>(Locutus.imp().getTradeManager().getTradeDb().getTradesById(ids));
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return Set.of(Locutus.imp().getTradeManager().getTradeDb().getTradeById(id));
                    }
                    throw new IllegalArgumentException("Only trade ids are supported, not `" + input + "`");
                }, (ThrowingTriFunction<Placeholders<DBTrade, Void>, ValueStore, String, Predicate<DBTrade>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) {
                return Predicates.alwaysTrue();
            }
            if (SpreadSheet.isSheet(input)) {
                Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> Integer.parseInt(str));
                return f -> ids.contains(f.getTradeId());
            }
            if (MathMan.isInteger(input)) {
                int id = Integer.parseInt(input);
                return f -> f.getTradeId() == id;
            }
            NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
            Predicate<DBNation> filter = nationPlaceholders.parseSingleFilter(store, input);
            return f -> {
                DBNation sender = DBNation.getById(f.getSeller());
                DBNation receiver = DBNation.getById(f.getBuyer());
                if (sender != null && filter.test(sender)) return true;
                if (receiver != null && filter.test(receiver)) return true;
                return false;
            };
        }, new Function<DBTrade, String>() {
            @Override
            public String apply(DBTrade dbTrade) {
                return dbTrade.getTradeId() + "";
            }
        }
        ){
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("TRADE_ID", "12345", "Trade ID"),
                        new SelectorInfo("NATION", "Borg", "Nation name, id, leader, url, user id or mention (see nation type)")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id"));
            }

            @NoFormat
            @Command(desc = "Add columns to a Trade sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBTrade, String> a,
                                     @Default TypedFunction<DBTrade, String> b,
                                     @Default TypedFunction<DBTrade, String> c,
                                     @Default TypedFunction<DBTrade, String> d,
                                     @Default TypedFunction<DBTrade, String> e,
                                     @Default TypedFunction<DBTrade, String> f,
                                     @Default TypedFunction<DBTrade, String> g,
                                     @Default TypedFunction<DBTrade, String> h,
                                     @Default TypedFunction<DBTrade, String> i,
                                     @Default TypedFunction<DBTrade, String> j,
                                     @Default TypedFunction<DBTrade, String> k,
                                     @Default TypedFunction<DBTrade, String> l,
                                     @Default TypedFunction<DBTrade, String> m,
                                     @Default TypedFunction<DBTrade, String> n,
                                     @Default TypedFunction<DBTrade, String> o,
                                     @Default TypedFunction<DBTrade, String> p,
                                     @Default TypedFunction<DBTrade, String> q,
                                     @Default TypedFunction<DBTrade, String> r,
                                     @Default TypedFunction<DBTrade, String> s,
                                     @Default TypedFunction<DBTrade, String> t,
                                     @Default TypedFunction<DBTrade, String> u,
                                     @Default TypedFunction<DBTrade, String> v,
                                     @Default TypedFunction<DBTrade, String> w,
                                     @Default TypedFunction<DBTrade, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }


        };
    }

    private Placeholders<Transaction2, Void> createTransactions() {
        return new SimpleVoidPlaceholders<Transaction2>(Transaction2.class, store, validators, permisser,
                "A bank transaction",
                (ThrowingTriFunction<Placeholders<Transaction2, Void>, ValueStore, String, Set<Transaction2>>) (inst, store, input) -> {
                    Set<Transaction2> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
                    User user = (User) store.getProvided(Key.of(User.class, Me.class), false);
                    DBNation nation = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);

                    if (input.equalsIgnoreCase("*")) {
                        throw new UnsupportedOperationException("`*` is not supported. Only transaction ids are supported");
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> Integer.parseInt(str));
                        List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsbyId(ids);
                        return filterTransactions(nation, user, db, transactions);
                    }
                    if (MathMan.isInteger(input)) {
                        List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsbyId(
                                Set.of(Integer.parseInt(input)));
                        return filterTransactions(nation, user, db, transactions);
                    }
                    throw new IllegalArgumentException("Invalid transaction id: " + input);
                }, (ThrowingTriFunction<Placeholders<Transaction2, Void>, ValueStore, String, Predicate<Transaction2>>) (inst, store, input) -> {
            GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
            User user = (User) store.getProvided(Key.of(User.class, Me.class), false);
            DBNation nation = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);

            Predicate<Transaction2> filter = getAllowed(nation, user, db);

            if (input.equalsIgnoreCase("*")) return filter;
            if (SpreadSheet.isSheet(input)) {
                Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> Integer.parseInt(str));
                return filter.and(f -> ids.contains(f.tx_id));
            }
            if (MathMan.isInteger(input)) {
                int id = Integer.parseInt(input);
                return filter.and(f -> f.tx_id == id);
            }
            throw new IllegalArgumentException("Invalid transaction id: " + input);
        }, new Function<Transaction2, String>() {
            @Override
            public String apply(Transaction2 transaction2) {
                return transaction2.toString();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("TX_ID", "12345", "Bank Transaction ID"),
                        new SelectorInfo("NATION", "Borg", "Nation name, id, leader, url, user id or mention (see nation type)")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id"));
            }

            @Override
            public Set<Transaction2> deserializeSelection(ValueStore store, String input, Void modifier) {
                String type = input.substring(0, input.indexOf("{"));
                input = input.substring(input.indexOf("{"));

                JSONObject json = new JSONObject(input);
                Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                User author = (User) store.getProvided(Key.of(User.class, Me.class), false);
                DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);

                switch (type) {
                    case "all" -> {
                        try {
                            String sendersStr = json.optString("sender", null);
                            Set<NationOrAlliance> senders = sendersStr == null ? null : PWBindings.nationOrAlliance(null, guild, sendersStr, true, author, me);

                            String receiversStr = json.optString("receiver", null);
                            Set<NationOrAlliance> receivers = receiversStr == null ? null : PWBindings.nationOrAlliance(null, guild, receiversStr, true, author, me);

                            String bankersStr = json.optString("banker", null);
                            Set<NationOrAlliance> bankers = bankersStr == null ? null : PWBindings.nationOrAlliance(null, guild, bankersStr, true, author, me);

                            Predicate<Transaction2> transactionFilter = json.has("transactionFilter") ? parseFilter(store, json.getString("transactionFilter")) : null;

                            Long startTime = json.has("startTime") ? PrimitiveBindings.timestamp(json.getString("startTime")) : null;

                            Long endTime = json.has("endTime") ? PrimitiveBindings.timestamp(json.getString("endTime")) : null;

                            Boolean includeOffset = json.has("includeOffset") ? json.getBoolean("includeOffset") : null;

                            List<Transaction2> transfers = Locutus.imp().getBankDB().getAllTransactions(senders, receivers, bankers, startTime, endTime);
                            return new ObjectLinkedOpenHashSet<>(transfers);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    case "deposits" -> {
                        try {
                            GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                            // "nationOrAllianceOrGuild", "transactionFilter", "startTime", "endTime", "startTime", "endTime", "includeOffset"
                            String nationOrAllianceOrGuildStr = json.optString("nationOrAllianceOrGuild", null);
                            NationOrAllianceOrGuild nationOrAllianceOrGuild = nationOrAllianceOrGuildStr == null ? null :
                                    PWBindings.nationOrAllianceOrGuild(nationOrAllianceOrGuildStr);

                            Predicate<Transaction2> transactionFilter = json.has("transactionFilter") ? parseFilter(store, json.getString("transactionFilter")) : Predicates.alwaysTrue();

                            long startTime = json.has("startTime") ? PrimitiveBindings.timestamp(json.getString("startTime")) : 0;

                            long endTime = json.has("endTime") ? PrimitiveBindings.timestamp(json.getString("endTime")) : Long.MAX_VALUE;

                            boolean excludeOffset = json.has("excludeOffset") && json.getBoolean("excludeOffset");
                            boolean excludeTaxes = json.has("excludeTaxes") && json.getBoolean("excludeTaxes");
                            boolean includeFullTaxes = json.has("includeFullTaxes") && json.getBoolean("includeFullTaxes");

                            Predicate<Transaction2> combinedFilter = transactionFilter;
                            if (startTime > 0) {
                                combinedFilter = combinedFilter.and(f -> f.getDate() >= startTime);
                            }
                            if (endTime < Long.MAX_VALUE) {
                                combinedFilter = combinedFilter.and(f -> f.getDate() <= endTime);
                            }
                            Predicate<Transaction2> filterFinal = combinedFilter;
                            // get guild db
                            if (nationOrAllianceOrGuild.isNation()) {
                                DBNation nation = nationOrAllianceOrGuild.asNation();
                                List<Map.Entry<Integer, Transaction2>> transactions = nation.getTransactions(db, null, !excludeTaxes, !includeFullTaxes, !excludeOffset, -1, startTime, endTime, false);
                                return transactions.stream()
                                        .filter(f -> filterFinal.test(f.getValue()))
                                        .map(Map.Entry::getValue)
                                        .collect(Collectors.toCollection(ObjectLinkedOpenHashSet::new));
                            } else {
                                OffshoreInstance offshore = db.getOffshore();
                                if (offshore == null) {
                                    throw new IllegalArgumentException("This guild does not have an offshore. See: " + CM.offshore.add.cmd.toSlashMention());
                                }
                                List<Transaction2> transfers;
                                if (db.isOffshore()) {
                                    if (nationOrAllianceOrGuild.isAlliance()) {
                                        transfers = offshore.getTransactionsAA(nationOrAllianceOrGuild.getId(), false, startTime, endTime);
                                    } else {
                                        transfers = offshore.getTransactionsGuild(nationOrAllianceOrGuild.getIdLong(), false, startTime, endTime);
                                    }
                                } else {
                                    if (nationOrAllianceOrGuild.isAlliance()) {
                                        DBAlliance aa = nationOrAllianceOrGuild.asAlliance();
                                        if (!db.isAllianceId(aa.getId())) {
                                            throw new IllegalArgumentException("The alliance " + aa.getMarkdownUrl() + " is not registered to this guild " + guild.toString());
                                        }
                                        transfers = offshore.getTransactionsAA(aa.getId(), false, startTime, endTime);
                                    } else {
                                        GuildDB account = nationOrAllianceOrGuild.asGuild();
                                        if (account != db) {
                                            throw new IllegalArgumentException("You cannot check the balance of " + account.getGuild() + " from this guild " + guild.toString());
                                        }
                                        transfers = offshore.getTransactionsGuild(account.getIdLong(), false, startTime, endTime);
                                    }
                                }
                                return transfers.stream()
                                        .filter(filterFinal::test)
                                        .collect(Collectors.toCollection(ObjectLinkedOpenHashSet::new));
                            }
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                throw new UnsupportedOperationException("Currently not supported");
            }

            @NoFormat
            @Command(desc = "Add columns to a Transaction sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String bankRecordsAllAlias(@Me JSONObject command, @Me GuildDB db, String name,
                                              @Default Set<NationOrAlliance> sender,
                                              @Default Set<NationOrAlliance> receiver,
                                              @Default Set<NationOrAlliance> senderOrReceiver,
                                              @Default Set<NationOrAlliance> banker, @Default Predicate<Transaction2> transactionFilter, @Default @Timestamp Long startTime, @Default @Timestamp Long endTime, @Switch("o") Boolean includeOffsets) {
                if (senderOrReceiver != null && (sender != null || receiver != null)) {
                    throw new IllegalArgumentException("Cannot specify `sender` or `receiver` when `senderOrReceiver` is specified");
                }
                if (startTime != null && endTime != null && startTime > endTime) {
                    throw new IllegalArgumentException("Start time cannot be after end time");
                }
                return _addSelectionAlias(this, "all", command, db, name, "sender", "receiver", "banker", "transactionFilter", "startTime", "endTime", "includeOffset");
            }

            @NoFormat
            @Command(desc = "Add columns to a Transaction sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String bankRecordsDeposits(@Me JSONObject command, @Me GuildDB db, String name,
                                              NationOrAllianceOrGuild nationOrAllianceOrGuild, @Default Predicate<Transaction2> transactionFilter, @Default @Timestamp Long startTime, @Default @Timestamp Long endTime, @Switch("o") Boolean excludeOffset, @Switch("t") Boolean excludeTaxes, @Switch("i") Boolean includeFullTaxes) {
                return _addSelectionAlias(this, "deposits", command, db, name, "nationOrAllianceOrGuild", "transactionFilter", "startTime", "endTime", "startTime", "endTime", "excludeOffset", "excludeTaxes", "includeFullTaxes");
            }

            @NoFormat
            @Command(desc = "Add columns to a Transaction sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<Transaction2, String> a,
                                     @Default TypedFunction<Transaction2, String> b,
                                     @Default TypedFunction<Transaction2, String> c,
                                     @Default TypedFunction<Transaction2, String> d,
                                     @Default TypedFunction<Transaction2, String> e,
                                     @Default TypedFunction<Transaction2, String> f,
                                     @Default TypedFunction<Transaction2, String> g,
                                     @Default TypedFunction<Transaction2, String> h,
                                     @Default TypedFunction<Transaction2, String> i,
                                     @Default TypedFunction<Transaction2, String> j,
                                     @Default TypedFunction<Transaction2, String> k,
                                     @Default TypedFunction<Transaction2, String> l,
                                     @Default TypedFunction<Transaction2, String> m,
                                     @Default TypedFunction<Transaction2, String> n,
                                     @Default TypedFunction<Transaction2, String> o,
                                     @Default TypedFunction<Transaction2, String> p,
                                     @Default TypedFunction<Transaction2, String> q,
                                     @Default TypedFunction<Transaction2, String> r,
                                     @Default TypedFunction<Transaction2, String> s,
                                     @Default TypedFunction<Transaction2, String> t,
                                     @Default TypedFunction<Transaction2, String> u,
                                     @Default TypedFunction<Transaction2, String> v,
                                     @Default TypedFunction<Transaction2, String> w,
                                     @Default TypedFunction<Transaction2, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<DBBounty, Void> createBounties() {
        return new SimpleVoidPlaceholders<DBBounty>(DBBounty.class, store, validators, permisser,
                "A bounty",
                (ThrowingTriFunction<Placeholders<DBBounty, Void>, ValueStore, String, Set<DBBounty>>) (inst, store, input) -> {
                    Set<DBBounty> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) {
                        Set<DBBounty> result = new HashSet<>();
                        Locutus.imp().getWarDb().getBountiesByNation().values().forEach(result::addAll);
                        return result;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("bounty"), true, (type, str) -> PWBindings.bounty(str));
                    }
                    if (MathMan.isInteger(input)) {
                        return Set.of(PWBindings.bounty(input));
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    User author = (User) store.getProvided(Key.of(User.class, Me.class), false);
                    DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
                    Set<DBNation> nations = PWBindings.nations(null, guild, input, author, me);
                    Map<Integer, List<DBBounty>> bounties = Locutus.imp().getWarDb().getBountiesByNation();
                    Set<DBBounty> bountySet = new ObjectLinkedOpenHashSet<>();
                    for (DBNation nation : nations) {
                        List<DBBounty> list = bounties.get(nation.getId());
                        if (list != null) {
                            bountySet.addAll(list);
                        }
                    }
                    return bountySet;
                }, (ThrowingTriFunction<Placeholders<DBBounty, Void>, ValueStore, String, Predicate<DBBounty>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            if (SpreadSheet.isSheet(input)) {
                Set<Integer> sheet = SpreadSheet.parseSheet(input, List.of("bounty"), true,
                        (type, str) -> PrimitiveBindings.Integer(str));
                return f -> sheet.contains(f.getId());
            }
            if (MathMan.isInteger(input)) {
                int id = Integer.parseInt(input);
                return f -> f.getId() == id;
            }
            NationPlaceholders natPlac = (NationPlaceholders) (Placeholders) get(DBNation.class);
            Predicate<DBNation> filter = natPlac.parseSingleFilter(store, input);
            return f -> {
                DBNation nation = DBNation.getById(f.getNationId());
                if (nation == null) return false;
                return filter.test(nation);
            };
        }, new Function<DBBounty, String>() {
            @Override
            public String apply(DBBounty dbBounty) {
                return dbBounty.toString();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                Set<SelectorInfo> result = new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("BOUNTY_ID", "12345", "Bounty ID"),
                        new SelectorInfo("*", null, "All bounties")
                ));
                result.addAll(NATIONS.getSelectorInfo());
                return result;
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("bounty");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of bounties")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBBounty> bounties) {
                return _addSelectionAlias(this, command, db, name, bounties, "bounties");
            }

            @NoFormat
            @Command(desc = "Add columns to a bounty sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBBounty, String> a,
                                     @Default TypedFunction<DBBounty, String> b,
                                     @Default TypedFunction<DBBounty, String> c,
                                     @Default TypedFunction<DBBounty, String> d,
                                     @Default TypedFunction<DBBounty, String> e,
                                     @Default TypedFunction<DBBounty, String> f,
                                     @Default TypedFunction<DBBounty, String> g,
                                     @Default TypedFunction<DBBounty, String> h,
                                     @Default TypedFunction<DBBounty, String> i,
                                     @Default TypedFunction<DBBounty, String> j,
                                     @Default TypedFunction<DBBounty, String> k,
                                     @Default TypedFunction<DBBounty, String> l,
                                     @Default TypedFunction<DBBounty, String> m,
                                     @Default TypedFunction<DBBounty, String> n,
                                     @Default TypedFunction<DBBounty, String> o,
                                     @Default TypedFunction<DBBounty, String> p,
                                     @Default TypedFunction<DBBounty, String> q,
                                     @Default TypedFunction<DBBounty, String> r,
                                     @Default TypedFunction<DBBounty, String> s,
                                     @Default TypedFunction<DBBounty, String> t,
                                     @Default TypedFunction<DBBounty, String> u,
                                     @Default TypedFunction<DBBounty, String> v,
                                     @Default TypedFunction<DBBounty, String> w,
                                     @Default TypedFunction<DBBounty, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<Treaty, Void> createTreaty() {
        return new SimpleVoidPlaceholders<Treaty>(Treaty.class, store, validators, permisser,
                "A treaty between two alliances",
                (ThrowingTriFunction<Placeholders<Treaty, Void>, ValueStore, String, Set<Treaty>>) (inst, store, input) -> {
                    Set<Treaty> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) {
                        return Locutus.imp().getNationDB().getTreaties();
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("treaty"), true, (type, str) -> PWBindings.treaty(str));
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    GuildDB db = guild == null ? null : Locutus.imp().getGuildDB(guild);
                    List<String> split = StringMan.split(input, (s, index) -> switch (s.charAt(index)) {
                        case ':', '>', '<' -> 1;
                        default -> null;
                    }, 2);
                    if (split.size() != 2) {
                        throw new IllegalArgumentException("Invalid treaty format: `" + input + "`");
                    }
                    Set<Integer> aa1 = DiscordUtil.parseAllianceIds(guild, split.get(0), true);
                    Set<Integer> aa2 = DiscordUtil.parseAllianceIds(guild, split.get(1), true);
                    if (aa1 == null)
                        throw new IllegalArgumentException("Invalid alliance or coalition: `" + split.get(0) + "`");
                    if (aa2 == null)
                        throw new IllegalArgumentException("Invalid alliance or coalition: `" + split.get(1) + "`");
                    return Locutus.imp().getNationDB().getTreatiesMatching(f -> {
                        return (aa1.contains(f.getFromId())) && (aa2.contains(f.getToId())) || (aa1.contains(f.getToId())) && (aa2.contains(f.getFromId()));
                    });
                }, (ThrowingTriFunction<Placeholders<Treaty, Void>, ValueStore, String, Predicate<Treaty>>) (inst, store, input) -> {
            if (input.equalsIgnoreCase("*")) return Predicates.alwaysTrue();
            if (SpreadSheet.isSheet(input)) {
                Set<Treaty> sheet = SpreadSheet.parseSheet(input, List.of("treaty"), true,
                        (type, str) -> PWBindings.treaty(str));

                Map<Integer, Set<Integer>> treatyIds = new HashMap<>();
                for (Treaty treaty : sheet) {
                    treatyIds.computeIfAbsent(treaty.getFromId(), k -> new IntOpenHashSet()).add(treaty.getToId());
                    treatyIds.computeIfAbsent(treaty.getToId(), k -> new IntOpenHashSet()).add(treaty.getFromId());
                }
                return f -> treatyIds.getOrDefault(f.getFromId(), Collections.emptySet()).contains(f.getToId());
            }
            Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
            GuildDB db = guild == null ? null : Locutus.imp().getGuildDB(guild);
            List<String> split = StringMan.split(input, (s, index) -> switch (s.charAt(index)) {
                case ':', '>', '<' -> 1;
                default -> null;
            }, 2);
            if (split.size() != 2) {
                throw new IllegalArgumentException("Invalid treaty format: `" + input + "`");
            }
            Set<Integer> aa1 = DiscordUtil.parseAllianceIds(guild, split.get(0), false);
            Long coalitionId1 = aa1 != null || db == null ? null : db.getCoalitionId(split.get(0), true);

            Set<Integer> aa2 = DiscordUtil.parseAllianceIds(guild, split.get(1), false);
            Long coalitionId2 = aa2 != null || db == null ? null : db.getCoalitionId(split.get(1), true);

            if (aa1 == null && coalitionId2 == null) {
                throw new IllegalArgumentException("Invalid treaty alliance or coalition: `" + split.get(0) + "`");
            }
            if (aa2 == null && coalitionId2 == null) {
                throw new IllegalArgumentException("Invalid treaty alliance or coalition: `" + split.get(1) + "`");
            }

            Predicate<Integer> contains1 = f -> {
                if (aa1 != null) {
                    return aa1.contains(f);
                } else {
                    return db.getCoalitionById(coalitionId1).contains(f.longValue());
                }
            };
            Predicate<Integer> contains2 = f -> {
                if (aa2 != null) {
                    return aa2.contains(f);
                } else {
                    return db.getCoalitionById(coalitionId2).contains(f.longValue());
                }
            };
            return f -> (contains1.test(f.getFromId()) && contains2.test(f.getToId()))
                    || (contains1.test(f.getToId()) && contains2.test(f.getFromId()));
        }, new Function<Treaty, String>() {
            @Override
            public String apply(Treaty treaty) {
                return treaty.toString();
            }
        }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("ALLIANCES:ALLIANCES", "`Rose:Eclipse`, `Rose,Eclipse:~allies`", "A treaty between two sets of alliances or coalitions (direction agnostic)"),
                        new SelectorInfo("ALLIANCES>ALLIANCES", "Rose>Eclipse", "A treaty from one alliance or coalition to another"),
                        new SelectorInfo("ALLIANCES<ALLIANCES", "Rose<Eclipse", "A treaty from one alliance or coalition to another"),
                        new SelectorInfo("*", null, "All treaties")
                ));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("treaty");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of treaties")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Treaty> treaties) {
                return _addSelectionAlias(this, command, db, name, treaties, "treaties");
            }

            @NoFormat
            @Command(desc = "Add columns to a Treaty sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<Treaty, String> a,
                                     @Default TypedFunction<Treaty, String> b,
                                     @Default TypedFunction<Treaty, String> c,
                                     @Default TypedFunction<Treaty, String> d,
                                     @Default TypedFunction<Treaty, String> e,
                                     @Default TypedFunction<Treaty, String> f,
                                     @Default TypedFunction<Treaty, String> g,
                                     @Default TypedFunction<Treaty, String> h,
                                     @Default TypedFunction<Treaty, String> i,
                                     @Default TypedFunction<Treaty, String> j,
                                     @Default TypedFunction<Treaty, String> k,
                                     @Default TypedFunction<Treaty, String> l,
                                     @Default TypedFunction<Treaty, String> m,
                                     @Default TypedFunction<Treaty, String> n,
                                     @Default TypedFunction<Treaty, String> o,
                                     @Default TypedFunction<Treaty, String> p,
                                     @Default TypedFunction<Treaty, String> q,
                                     @Default TypedFunction<Treaty, String> r,
                                     @Default TypedFunction<Treaty, String> s,
                                     @Default TypedFunction<Treaty, String> t,
                                     @Default TypedFunction<Treaty, String> u,
                                     @Default TypedFunction<Treaty, String> v,
                                     @Default TypedFunction<Treaty, String> w,
                                     @Default TypedFunction<Treaty, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<Project, Void> createProjects() {
        return new StaticPlaceholders<Project>(Project.class, Projects::values, store, validators, permisser,
                "A project",
                (ThrowingTriFunction<Placeholders<Project, Void>, ValueStore, String, Set<Project>>) (inst, store, input) -> {
                    Set<Project> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(Projects.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("project"), true, (type, str) -> PWBindings.project(str));
                    }
                    Set<Project> result = new HashSet<>();
                    for (String type : input.split(",")) {
                        Project project = Projects.get(type);
                        if (project == null) throw new IllegalArgumentException("Invalid project: `" + type + "`");
                        result.add(project);
                    }
                    return result;
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("project");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Projects")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Project> projects) {
                return _addSelectionAlias(this, command, db, name, projects, "projects");
            }

            @NoFormat
            @Command(desc = "Add columns to a Project sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<Project, String> a,
                                     @Default TypedFunction<Project, String> b,
                                     @Default TypedFunction<Project, String> c,
                                     @Default TypedFunction<Project, String> d,
                                     @Default TypedFunction<Project, String> e,
                                     @Default TypedFunction<Project, String> f,
                                     @Default TypedFunction<Project, String> g,
                                     @Default TypedFunction<Project, String> h,
                                     @Default TypedFunction<Project, String> i,
                                     @Default TypedFunction<Project, String> j,
                                     @Default TypedFunction<Project, String> k,
                                     @Default TypedFunction<Project, String> l,
                                     @Default TypedFunction<Project, String> m,
                                     @Default TypedFunction<Project, String> n,
                                     @Default TypedFunction<Project, String> o,
                                     @Default TypedFunction<Project, String> p,
                                     @Default TypedFunction<Project, String> q,
                                     @Default TypedFunction<Project, String> r,
                                     @Default TypedFunction<Project, String> s,
                                     @Default TypedFunction<Project, String> t,
                                     @Default TypedFunction<Project, String> u,
                                     @Default TypedFunction<Project, String> v,
                                     @Default TypedFunction<Project, String> w,
                                     @Default TypedFunction<Project, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<ResourceType, Void> createResourceType() {
        return new StaticPlaceholders<ResourceType>(ResourceType.class, ResourceType::values, store, validators, permisser,
        "A game resource",
        (ThrowingTriFunction<Placeholders<ResourceType, Void>, ValueStore, String, Set<ResourceType>>) (inst, store, input) -> {
            Set<ResourceType> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
            if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(ResourceType.values));
            if (SpreadSheet.isSheet(input)) {
                return SpreadSheet.parseSheet(input, List.of("resource"), true, (type, str) -> PWBindings.resource(str));
            }
            return Set.of(PWBindings.resource(input));
        }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("resource");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of resources")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<ResourceType> resources) {
                return _addSelectionAlias(this, command, db, name, resources, "resources");
            }

            @NoFormat
            @Command(desc = "Add columns to a Resource sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<ResourceType, String> a,
                                     @Default TypedFunction<ResourceType, String> b,
                                     @Default TypedFunction<ResourceType, String> c,
                                     @Default TypedFunction<ResourceType, String> d,
                                     @Default TypedFunction<ResourceType, String> e,
                                     @Default TypedFunction<ResourceType, String> f,
                                     @Default TypedFunction<ResourceType, String> g,
                                     @Default TypedFunction<ResourceType, String> h,
                                     @Default TypedFunction<ResourceType, String> i,
                                     @Default TypedFunction<ResourceType, String> j,
                                     @Default TypedFunction<ResourceType, String> k,
                                     @Default TypedFunction<ResourceType, String> l,
                                     @Default TypedFunction<ResourceType, String> m,
                                     @Default TypedFunction<ResourceType, String> n,
                                     @Default TypedFunction<ResourceType, String> o,
                                     @Default TypedFunction<ResourceType, String> p,
                                     @Default TypedFunction<ResourceType, String> q,
                                     @Default TypedFunction<ResourceType, String> r,
                                     @Default TypedFunction<ResourceType, String> s,
                                     @Default TypedFunction<ResourceType, String> t,
                                     @Default TypedFunction<ResourceType, String> u,
                                     @Default TypedFunction<ResourceType, String> v,
                                     @Default TypedFunction<ResourceType, String> w,
                                     @Default TypedFunction<ResourceType, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<AttackType, Void> createAttackTypes() {
        return new StaticPlaceholders<AttackType>(AttackType.class, AttackType::values, store, validators, permisser,
                "A war attack type",
                (ThrowingTriFunction<Placeholders<AttackType, Void>, ValueStore, String, Set<AttackType>>) (inst, store, input) -> {
                    Set<AttackType> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(AttackType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("attack_type"), true, (type, str) -> PWBindings.attackType(str));
                    }
                    return Set.of(PWBindings.attackType(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("attack_type");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of attack types")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<AttackType> attack_types) {
                return _addSelectionAlias(this, command, db, name, attack_types, "attack_types");
            }

            @NoFormat
            @Command(desc = "Add columns to a AttackType sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<AttackType, String> a,
                                     @Default TypedFunction<AttackType, String> b,
                                     @Default TypedFunction<AttackType, String> c,
                                     @Default TypedFunction<AttackType, String> d,
                                     @Default TypedFunction<AttackType, String> e,
                                     @Default TypedFunction<AttackType, String> f,
                                     @Default TypedFunction<AttackType, String> g,
                                     @Default TypedFunction<AttackType, String> h,
                                     @Default TypedFunction<AttackType, String> i,
                                     @Default TypedFunction<AttackType, String> j,
                                     @Default TypedFunction<AttackType, String> k,
                                     @Default TypedFunction<AttackType, String> l,
                                     @Default TypedFunction<AttackType, String> m,
                                     @Default TypedFunction<AttackType, String> n,
                                     @Default TypedFunction<AttackType, String> o,
                                     @Default TypedFunction<AttackType, String> p,
                                     @Default TypedFunction<AttackType, String> q,
                                     @Default TypedFunction<AttackType, String> r,
                                     @Default TypedFunction<AttackType, String> s,
                                     @Default TypedFunction<AttackType, String> t,
                                     @Default TypedFunction<AttackType, String> u,
                                     @Default TypedFunction<AttackType, String> v,
                                     @Default TypedFunction<AttackType, String> w,
                                     @Default TypedFunction<AttackType, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<MilitaryUnit, Void> createMilitaryUnit() {
        return new StaticPlaceholders<MilitaryUnit>(MilitaryUnit.class, MilitaryUnit::values, store, validators, permisser,
                "A military unit type",
                (ThrowingTriFunction<Placeholders<MilitaryUnit, Void>, ValueStore, String, Set<MilitaryUnit>>) (inst, store, input) -> {
                    Set<MilitaryUnit> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(MilitaryUnit.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("unit"), true, (type, str) -> PWBindings.unit(str));
                    }
                    return Set.of(PWBindings.unit(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("unit");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Military Units")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<MilitaryUnit> military_units) {
                return _addSelectionAlias(this, command, db, name, military_units, "military_units");
            }

            @NoFormat
            @Command(desc = "Add columns to a Military Unit sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<MilitaryUnit, String> a,
                                     @Default TypedFunction<MilitaryUnit, String> b,
                                     @Default TypedFunction<MilitaryUnit, String> c,
                                     @Default TypedFunction<MilitaryUnit, String> d,
                                     @Default TypedFunction<MilitaryUnit, String> e,
                                     @Default TypedFunction<MilitaryUnit, String> f,
                                     @Default TypedFunction<MilitaryUnit, String> g,
                                     @Default TypedFunction<MilitaryUnit, String> h,
                                     @Default TypedFunction<MilitaryUnit, String> i,
                                     @Default TypedFunction<MilitaryUnit, String> j,
                                     @Default TypedFunction<MilitaryUnit, String> k,
                                     @Default TypedFunction<MilitaryUnit, String> l,
                                     @Default TypedFunction<MilitaryUnit, String> m,
                                     @Default TypedFunction<MilitaryUnit, String> n,
                                     @Default TypedFunction<MilitaryUnit, String> o,
                                     @Default TypedFunction<MilitaryUnit, String> p,
                                     @Default TypedFunction<MilitaryUnit, String> q,
                                     @Default TypedFunction<MilitaryUnit, String> r,
                                     @Default TypedFunction<MilitaryUnit, String> s,
                                     @Default TypedFunction<MilitaryUnit, String> t,
                                     @Default TypedFunction<MilitaryUnit, String> u,
                                     @Default TypedFunction<MilitaryUnit, String> v,
                                     @Default TypedFunction<MilitaryUnit, String> w,
                                     @Default TypedFunction<MilitaryUnit, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<TreatyType, Void> createTreatyType() {
        return new StaticPlaceholders<TreatyType>(TreatyType.class, TreatyType::values, store, validators, permisser,
                "A treaty type",
                (ThrowingTriFunction<Placeholders<TreatyType, Void>, ValueStore, String, Set<TreatyType>>) (inst, store, input) -> {
                    Set<TreatyType> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(TreatyType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("treaty_type"), true, (type, str) -> PWBindings.TreatyType(str));
                    }
                    return Set.of(PWBindings.TreatyType(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("treaty_type");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Treaty Types")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<TreatyType> treaty_types) {
                return _addSelectionAlias(this, command, db, name, treaty_types, "treaty_types");
            }

            @NoFormat
            @Command(desc = "Add columns to a TreatyType sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<TreatyType, String> a,
                                     @Default TypedFunction<TreatyType, String> b,
                                     @Default TypedFunction<TreatyType, String> c,
                                     @Default TypedFunction<TreatyType, String> d,
                                     @Default TypedFunction<TreatyType, String> e,
                                     @Default TypedFunction<TreatyType, String> f,
                                     @Default TypedFunction<TreatyType, String> g,
                                     @Default TypedFunction<TreatyType, String> h,
                                     @Default TypedFunction<TreatyType, String> i,
                                     @Default TypedFunction<TreatyType, String> j,
                                     @Default TypedFunction<TreatyType, String> k,
                                     @Default TypedFunction<TreatyType, String> l,
                                     @Default TypedFunction<TreatyType, String> m,
                                     @Default TypedFunction<TreatyType, String> n,
                                     @Default TypedFunction<TreatyType, String> o,
                                     @Default TypedFunction<TreatyType, String> p,
                                     @Default TypedFunction<TreatyType, String> q,
                                     @Default TypedFunction<TreatyType, String> r,
                                     @Default TypedFunction<TreatyType, String> s,
                                     @Default TypedFunction<TreatyType, String> t,
                                     @Default TypedFunction<TreatyType, String> u,
                                     @Default TypedFunction<TreatyType, String> v,
                                     @Default TypedFunction<TreatyType, String> w,
                                     @Default TypedFunction<TreatyType, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<DBTreasure, Void> createTreasure() {
        Supplier<DBTreasure[]> treasures = () -> Locutus.imp().getNationDB().getTreasuresByName().values().toArray(new DBTreasure[0]);
        return new StaticPlaceholders<DBTreasure>(DBTreasure.class, treasures, store, validators, permisser,
                "A treasure",
                (ThrowingTriFunction<Placeholders<DBTreasure, Void>, ValueStore, String, Set<DBTreasure>>) (inst, store, input) -> {
                    Set<DBTreasure> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Locutus.imp().getNationDB().getTreasuresByName().values());
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("treasure"), true, (type, str) -> PWBindings.treasure(str));
                    }
                    try {
                        return Set.of(PWBindings.treasure(input));
                    } catch (IllegalArgumentException e) {
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        User author = (User) store.getProvided(Key.of(User.class, Me.class), false);
                        DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
                        Set<DBNation> nations = PWBindings.nations(null, guild, input, author, me);
                        Set<DBTreasure> result = new ObjectOpenHashSet<>();
                        for (DBNation nation : nations) {
                            result.addAll(nation.getTreasures());
                        }
                        return result;
                    }
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("treasure");
            }

            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                Set<SelectorInfo> selectors = new ObjectLinkedOpenHashSet<>(super.getSelectorInfo());
                selectors.addAll(NATIONS.getSelectorInfo());
                selectors.addAll(ALLIANCES.getSelectorInfo());
                return selectors;
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Treasures")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBTreasure> treasures) {
                return _addSelectionAlias(this, command, db, name, treasures, "treasures");
            }

            @NoFormat
            @Command(desc = "Add columns to a treasure sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBTreasure, String> a,
                                     @Default TypedFunction<DBTreasure, String> b,
                                     @Default TypedFunction<DBTreasure, String> c,
                                     @Default TypedFunction<DBTreasure, String> d,
                                     @Default TypedFunction<DBTreasure, String> e,
                                     @Default TypedFunction<DBTreasure, String> f,
                                     @Default TypedFunction<DBTreasure, String> g,
                                     @Default TypedFunction<DBTreasure, String> h,
                                     @Default TypedFunction<DBTreasure, String> i,
                                     @Default TypedFunction<DBTreasure, String> j,
                                     @Default TypedFunction<DBTreasure, String> k,
                                     @Default TypedFunction<DBTreasure, String> l,
                                     @Default TypedFunction<DBTreasure, String> m,
                                     @Default TypedFunction<DBTreasure, String> n,
                                     @Default TypedFunction<DBTreasure, String> o,
                                     @Default TypedFunction<DBTreasure, String> p,
                                     @Default TypedFunction<DBTreasure, String> q,
                                     @Default TypedFunction<DBTreasure, String> r,
                                     @Default TypedFunction<DBTreasure, String> s,
                                     @Default TypedFunction<DBTreasure, String> t,
                                     @Default TypedFunction<DBTreasure, String> u,
                                     @Default TypedFunction<DBTreasure, String> v,
                                     @Default TypedFunction<DBTreasure, String> w,
                                     @Default TypedFunction<DBTreasure, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<IACheckup.AuditType, Void> createAuditType() {
        return new StaticPlaceholders<IACheckup.AuditType>(IACheckup.AuditType.class, IACheckup.AuditType::values, store, validators, permisser,
                "A bot audit type for a nation",
                (ThrowingTriFunction<Placeholders<IACheckup.AuditType, Void>, ValueStore, String, Set<IACheckup.AuditType>>) (inst, store, input) -> {
                    Set<IACheckup.AuditType> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(IACheckup.AuditType.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("audit"), true, (type, str) -> PWBindings.auditType(str));
                    }
                    return Set.of(PWBindings.auditType(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("audit");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Audit Types")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<IACheckup.AuditType> audit_types) {
                return _addSelectionAlias(this, command, db, name, audit_types, "audit_types");
            }

            @NoFormat
            @Command(desc = "Add columns to a Audit Type sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<IACheckup.AuditType, String> a,
                                     @Default TypedFunction<IACheckup.AuditType, String> b,
                                     @Default TypedFunction<IACheckup.AuditType, String> c,
                                     @Default TypedFunction<IACheckup.AuditType, String> d,
                                     @Default TypedFunction<IACheckup.AuditType, String> e,
                                     @Default TypedFunction<IACheckup.AuditType, String> f,
                                     @Default TypedFunction<IACheckup.AuditType, String> g,
                                     @Default TypedFunction<IACheckup.AuditType, String> h,
                                     @Default TypedFunction<IACheckup.AuditType, String> i,
                                     @Default TypedFunction<IACheckup.AuditType, String> j,
                                     @Default TypedFunction<IACheckup.AuditType, String> k,
                                     @Default TypedFunction<IACheckup.AuditType, String> l,
                                     @Default TypedFunction<IACheckup.AuditType, String> m,
                                     @Default TypedFunction<IACheckup.AuditType, String> n,
                                     @Default TypedFunction<IACheckup.AuditType, String> o,
                                     @Default TypedFunction<IACheckup.AuditType, String> p,
                                     @Default TypedFunction<IACheckup.AuditType, String> q,
                                     @Default TypedFunction<IACheckup.AuditType, String> r,
                                     @Default TypedFunction<IACheckup.AuditType, String> s,
                                     @Default TypedFunction<IACheckup.AuditType, String> t,
                                     @Default TypedFunction<IACheckup.AuditType, String> u,
                                     @Default TypedFunction<IACheckup.AuditType, String> v,
                                     @Default TypedFunction<IACheckup.AuditType, String> w,
                                     @Default TypedFunction<IACheckup.AuditType, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<NationColor, Void> createNationColor() {
        return new StaticPlaceholders<NationColor>(NationColor.class, NationColor::values, store, validators, permisser,
                "A nation color",
                (ThrowingTriFunction<Placeholders<NationColor, Void>, ValueStore, String, Set<NationColor>>) (inst, store, input) -> {
                    Set<NationColor> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(NationColor.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("color"), true, (type, str) -> PWBindings.NationColor(str));
                    }
                    return Set.of(PWBindings.NationColor(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("color");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Nation Colors")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<NationColor> colors) {
                return _addSelectionAlias(this, command, db, name, colors, "colors");
            }

            @NoFormat
            @Command(desc = "Add columns to a Nation Color sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<NationColor, String> a,
                                     @Default TypedFunction<NationColor, String> b,
                                     @Default TypedFunction<NationColor, String> c,
                                     @Default TypedFunction<NationColor, String> d,
                                     @Default TypedFunction<NationColor, String> e,
                                     @Default TypedFunction<NationColor, String> f,
                                     @Default TypedFunction<NationColor, String> g,
                                     @Default TypedFunction<NationColor, String> h,
                                     @Default TypedFunction<NationColor, String> i,
                                     @Default TypedFunction<NationColor, String> j,
                                     @Default TypedFunction<NationColor, String> k,
                                     @Default TypedFunction<NationColor, String> l,
                                     @Default TypedFunction<NationColor, String> m,
                                     @Default TypedFunction<NationColor, String> n,
                                     @Default TypedFunction<NationColor, String> o,
                                     @Default TypedFunction<NationColor, String> p,
                                     @Default TypedFunction<NationColor, String> q,
                                     @Default TypedFunction<NationColor, String> r,
                                     @Default TypedFunction<NationColor, String> s,
                                     @Default TypedFunction<NationColor, String> t,
                                     @Default TypedFunction<NationColor, String> u,
                                     @Default TypedFunction<NationColor, String> v,
                                     @Default TypedFunction<NationColor, String> w,
                                     @Default TypedFunction<NationColor, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }

    private Placeholders<Building, Void> createBuilding() {
        return new StaticPlaceholders<Building>(Building.class, Buildings::values, store, validators, permisser,
                "A city building type",
                (ThrowingTriFunction<Placeholders<Building, Void>, ValueStore, String, Set<Building>>) (inst, store, input) -> {
                    Set<Building> selection = getSelection(inst, store, input);
                    if (selection != null) return selection;
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(Buildings.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("building"), true, (type, str) -> PWBindings.getBuilding(str));
                    }
                    return Set.of(PWBindings.getBuilding(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("building");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Buildings")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Building> Buildings) {
                return _addSelectionAlias(this, command, db, name, Buildings, "Buildings");
            }

            @NoFormat
            @Command(desc = "Add columns to a Building sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<Building, String> a,
                                     @Default TypedFunction<Building, String> b,
                                     @Default TypedFunction<Building, String> c,
                                     @Default TypedFunction<Building, String> d,
                                     @Default TypedFunction<Building, String> e,
                                     @Default TypedFunction<Building, String> f,
                                     @Default TypedFunction<Building, String> g,
                                     @Default TypedFunction<Building, String> h,
                                     @Default TypedFunction<Building, String> i,
                                     @Default TypedFunction<Building, String> j,
                                     @Default TypedFunction<Building, String> k,
                                     @Default TypedFunction<Building, String> l,
                                     @Default TypedFunction<Building, String> m,
                                     @Default TypedFunction<Building, String> n,
                                     @Default TypedFunction<Building, String> o,
                                     @Default TypedFunction<Building, String> p,
                                     @Default TypedFunction<Building, String> q,
                                     @Default TypedFunction<Building, String> r,
                                     @Default TypedFunction<Building, String> s,
                                     @Default TypedFunction<Building, String> t,
                                     @Default TypedFunction<Building, String> u,
                                     @Default TypedFunction<Building, String> v,
                                     @Default TypedFunction<Building, String> w,
                                     @Default TypedFunction<Building, String> x) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        a, b, c, d, e, f, g, h, i, j,
                        k, l, m, n, o, p, q, r, s, t,
                        u, v, w, x);
            }
        };
    }



}
