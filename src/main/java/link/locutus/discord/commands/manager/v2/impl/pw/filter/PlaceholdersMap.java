package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.LiveAppPlaceholderExtension;
import link.locutus.discord.commands.manager.v2.binding.bindings.LiveAppPlaceholderRegistry;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderEngine;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.SelectorInfo;
import link.locutus.discord.commands.manager.v2.binding.bindings.SimplePlaceholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.SimpleVoidPlaceholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.StaticPlaceholders;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBBan;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.TextChannelWrapper;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.UserWrapper;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.scheduler.ThrowingTriFunction;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.ia.AuditType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.binding.BindingHelper.emum;
import static link.locutus.discord.commands.manager.v2.binding.BindingHelper.emumSet;

public class PlaceholdersMap extends PlaceholderEngine implements LiveAppPlaceholderRegistry {

    public static String getClassName(String simpleName) {
        return simpleName.replace("DB", "").replace("Wrapper", "")
                .replaceAll("[0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    public static String getClassName(Class clazz) {
        return getClassName(clazz.getSimpleName());
    }

    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final CommandRuntimeServices services;

    public PlaceholdersMap(ValueStore store, ValidatorStore validators, PermissionHandler permisser,
            CommandRuntimeServices services) {
        super(store, validators, permisser, services);
        this.store = store;
        this.validators = validators;
        this.permisser = permisser;
        this.services = services;

        add(new NationPlaceholders(store, validators, permisser, services));
        add(new AlliancePlaceholders(store, validators, permisser, services));
        add(createNationOrAlliances());
        add(createContinents());
        add(createGuildDB());
        add(createProjects());
        add(createTreaty());
        add(createBans());
        add(createResourceType());
        add(createAttackTypes());
        add(createMilitaryUnit());
        add(createTreatyType());
        add(createTreasure());
        add(createNationColor());
        add(createBuilding());
        add(createAuditType());
        add(createNationList());
        add(createBounties());
        add(createCities());
        add(createBrackets());
        add(createUsers());
        add(createChannels());
        add(createTransactions());
        add(createTrades());
        add(createAttacks());
        add(createWars());
        add(createTaxDeposit());
        add(createGuildSettings());
        add(createConflicts());

        // //-GuildKey
        // this.placeholders.put(GuildSetting.class, createGuildSetting());
        // //- Tax records
        // // - * (within aa)
        // this.placeholders.put(AGrantTemplate.class, createGrantTemplates());
    }

    @Override
    public PlaceholdersMap initParsing() {
        super.initParsing();
        return this;
    }

    @Override
    public PlaceholdersMap initCommands() {
        super.initCommands();
        return this;
    }

    public PlaceholdersMap initAppCommands() {
        initCommands();
        for (Placeholders<?, ?> placeholders : getAllPlaceholders()) {
            registerLiveAppEntityCommands(placeholders);
        }
        store.addLazyProvider(Key.of(LiveAppPlaceholderRegistry.class), () -> this);
        return this;
    }

    @Override
    public Set<Class<?>> getAppOnlyEntityCommandTypes() {
        Set<Class<?>> ownerTypes = new LinkedHashSet<>();
        for (Placeholders<?, ?> placeholders : getAllPlaceholders()) {
            ownerTypes.add(placeholders.getType());
            if (placeholders instanceof LiveAppPlaceholderExtension extension) {
                ownerTypes.addAll(extension.getAdditionalLiveAppEntityCommandTypes());
            }
        }
        return Collections.unmodifiableSet(ownerTypes);
    }

    private void registerLiveAppEntityCommands(Placeholders<?, ?> placeholders) {
        placeholders.getCommands().registerCommandsClass(placeholders.getType());
        if (placeholders instanceof LiveAppPlaceholderExtension extension) {
            extension.registerAdditionalLiveAppEntityCommands();
        }
    }

    private INationSnapshot nationSnapshot() {
        return services.lookup().currentSnapshot();
    }

    private NationDB nationDb() {
        return services.nationDb();
    }

    private GuildDB guildDb(Guild guild) {
        return services.getGuildDb(guild);
    }

    private WarDB warDb() {
        return services.warDb();
    }

    private BankDB bankDb() {
        return services.bankDb();
    }

    private TradeDB tradeDb() {
        return services.tradeDb();
    }

    private DBNation nationById(int nationId) {
        return nationSnapshot().getNationById(nationId);
    }

    private DBNation nationOrCreate(int nationId) {
        return services.lookup().getNationOrCreate(nationId);
    }

    private DBAlliance allianceById(int allianceId) {
        return services.lookup().getAllianceById(allianceId);
    }

    private DBAlliance allianceOrCreate(int allianceId) {
        return services.lookup().getAllianceOrCreate(allianceId);
    }

    private NationPlaceholders nationPlaceholders() {
        return (NationPlaceholders) (Placeholders) get(DBNation.class);
    }

    private Set<DBNation> parseNations(ValueStore store, String input, boolean allowDeleted) {
        return nationPlaceholders().parseSet(store, input, new NationModifier(null, allowDeleted, false));
    }

    private DBBan parseBan(ValueStore store, Guild guild, String input) {
        if (MathMan.isInteger(input)) {
            DBBan ban = nationDb().getBanById(Integer.parseInt(input));
            if (ban == null) {
                throw new IllegalArgumentException("No ban found for id `" + input + "`");
            }
            return ban;
        }
        Set<DBNation> nations = nationPlaceholders().parseSingleElem(store, input, false);
        if (nations.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one nation for ban input: `" + input + "`");
        }
        DBNation nation = nations.iterator().next();
        List<DBBan> bans = nationDb().getBansForNation(nation.getId());
        if (bans.isEmpty()) {
            throw new IllegalArgumentException("No bans found for nation `" + nation.getName() + "`");
        }
        return bans.get(0);
    }

    private DBBounty parseBounty(String input) {
        int id = PrimitiveBindings.Integer(input);
        DBBounty bounty = warDb().getBountyById(id);
        if (bounty == null) {
            throw new IllegalArgumentException("No bounty found with id: `" + id + "`");
        }
        return bounty;
    }

    private DBTreasure parseTreasure(String input) {
        Map<String, DBTreasure> treasures = nationDb().getTreasuresByName();
        DBTreasure treasure = treasures.get(input.toLowerCase(Locale.ROOT));
        if (treasure == null) {
            throw new IllegalArgumentException(
                    "No treasure found with name: `" + input + "`. Options " + StringMan.getString(treasures.keySet()));
        }
        return treasure;
    }

    public <T> Class<T> parseType(String name) {
        name = getClassName(name);
        String finalName = name;
        return (Class<T>) getTypes().stream().filter(f -> getClassName(f).equalsIgnoreCase(finalName))
                .findFirst().orElse(null);
    }

    public static <T, M> Set<T> getSelection(Placeholders<T, M> instance, ValueStore store, String input) {
        return getSelection(instance, store, input, true);
    }

    public static <T, M> Set<T> getSelection(Placeholders<T, M> instance, ValueStore store, String input,
            boolean throwError) {
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
                    M modifier = modifierStr == null || modifierStr.isEmpty() ? null
                            : instance.parseModifierLegacy(store, modifierStr);
                    return instance.parseSet(store, query, modifier);
                }
                if (throwError) {
                    Map<String, SelectionAlias<T>> options = db.getSheetManager().getSelectionAliases(type);
                    if (options.isEmpty()) {
                        throw new IllegalArgumentException("No selection aliases for type: `" + type.getSimpleName()
                                + "` Create one with `/selection_alias add " + getClassName(type) + "`");
                    }
                    throw new IllegalArgumentException("Invalid selection alias: `" + inputAlias + "`. Options: `"
                            + StringMan.join(options.keySet(), "`, `")
                            + "` (use `$` or `select:` as the prefix). See also: "
                            + CM.selection_alias.list.cmd.toSlashMention());
                }
            }
        }
        return null;
    }

    private Placeholders<Continent, Void> createContinents() {
        return new StaticPlaceholders<Continent>(Continent.class, Continent::values, store, validators, permisser,
                "One of the game continents",
                (ThrowingTriFunction<Placeholders<Continent, Void>, ValueStore, String, Set<Continent>>) (inst, store,
                        input) -> {
                    Set<Continent> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(Continent.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("continent"), true,
                                (type, str) -> PWBindings.continent(str));
                    }
                    return emumSet(Continent.class, input);
                }) {

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("continent");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of continents")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<Continent> continents) {
                return _addSelectionAlias(this, command, db, name, continents, "continents");
            }
        };
    }

    private Set<NationOrAlliance> nationOrAlliancesSingle(ValueStore store, String input, boolean allowStar) {
        input = input.trim();
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
        Guild guild = db == null ? null : db.getGuild();
        INationSnapshot snapshot = nationSnapshot();
        if (input.equalsIgnoreCase("*") && allowStar) {
            return new ObjectOpenHashSet<>(snapshot.getAllNations());
        }
        if (MathMan.isInteger(input)) {
            long id = Long.parseLong(input);
            if (id < Integer.MAX_VALUE) {
                int idInt = (int) id;
                DBAlliance alliance = allianceById(idInt);
                if (alliance != null)
                    return Set.of(alliance);
                DBNation nation = nationById(idInt);
                if (nation != null)
                    return Set.of(nation);
            } else {
                User user = services.getDiscordUserById(id);
                if (user != null) {
                    DBNation nation = services.lookup().getNationByUser(snapshot, user);
                    if (nation == null) {
                        throw new IllegalArgumentException("User `" + DiscordUtil.getFullUsername(user)
                                + "` is not registered. See " + CM.register.cmd.toSlashMention());
                    }
                    return Set.of(nation);
                }
                if (db != null) {
                    Role role = db.getGuild().getRoleById(id);
                    if (role != null) {
                        return (Set) NationPlaceholders.getByRole(services, db.getGuild(), input, role,
                                snapshot);
                    }
                } else {
                    DBNation nation = services.lookup().getNationByUser(snapshot, id, false);
                    if (nation != null) {
                        return Set.of(nation);
                    }
                }
            }
        }
        Integer aaId = PW.parseAllianceId(input);
        if (aaId != null) {
            return Set.of(allianceOrCreate(aaId));
        }
        DBNation argNation = services.lookup().parseNation(input, true, false, guild);
        if (argNation != null) {
            return Set.of(argNation);
        }
        if (input.contains("tax_id=")) {
            int taxId = PW.parseTaxId(input);
            return (Set) snapshot.getNationsByBracket(taxId);
        }
        if (input.startsWith("<@&") && db != null) {
            Role role = db.getGuild().getRoleById(input.substring(3, input.length() - 1));
            return (Set) NationPlaceholders.getByRole(services, db.getGuild(), input, role, snapshot);
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
        Set<Integer> coalition = db == null ? null : db.getCoalition(coalitionStr);
        if (coalition != null && !coalition.isEmpty()) {
            return coalition.stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
        }
        if (isCoalition) {
            throw new IllegalArgumentException("No alliances found for coalition `" + coalitionStr + "`. See "
                    + CM.coalition.add.cmd.toSlashMention());
        }
        if (db != null) {
            // get role by name
            String finalInput = input;
            Role role = db.getGuild().getRoles().stream().filter(f -> f.getName().equalsIgnoreCase(finalInput))
                    .findFirst().orElse(null);
            if (role != null) {
                return (Set) NationPlaceholders.getByRole(services, db.getGuild(), input, role, snapshot);
            }
            for (Member member : db.getGuild().getMembers()) {
                User user = member.getUser();
                DBNation nation = services.lookup().getNationByUser(snapshot, user);
                if (nation == null)
                    continue;
                if (member.getEffectiveName().equalsIgnoreCase(input) || user.getName().equalsIgnoreCase(input)
                        || input.equalsIgnoreCase(user.getGlobalName())) {
                    return Set.of(nation);
                }
            }
        }
        if (!MathMan.isInteger(input)) {
            String inputLower = input.toLowerCase(Locale.ROOT);
            String best = null;
            double bestScore = Double.MAX_VALUE;
            for (DBNation nation : snapshot.getAllNations()) {
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
            for (DBAlliance alliance : services.getAlliances()) {
                String name = alliance.getName();
                double score = StringMan.distanceWeightedQwertSift4(name.toLowerCase(Locale.ROOT), inputLower);
                if (score < bestScore) {
                    bestScore = score;
                    best = "aa:" + name;
                }
            }
            if (best != null) {
                throw new IllegalArgumentException(
                        "Invalid nation or alliance: `" + input + "`. Did you mean: `" + best + "`?");
            }
        }
        throw new IllegalArgumentException("Invalid nation or alliance: `" + input + "`");
    }

    private Placeholders<NationOrAlliance, NationModifier> createNationOrAlliances() {
        NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
        AlliancePlaceholders alliancePlaceholders = (AlliancePlaceholders) (Placeholders) get(DBAlliance.class);
        return new Placeholders<NationOrAlliance, NationModifier>(NationOrAlliance.class, NationModifier.class, store,
                validators, permisser) {
            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("nation", "alliance"));
            }

            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                Set<SelectorInfo> selectors = new ObjectLinkedOpenHashSet<>(PlaceholdersMap.this.get(DBNation.class).getSelectorInfo());
                selectors.addAll(PlaceholdersMap.this.get(DBAlliance.class).getSelectorInfo());
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
                if (selection2 != null)
                    return (Set) selection2;
                Set<DBAlliance> selection3 = getSelection(alliancePlaceholders, store, input, false);
                if (selection3 != null)
                    return (Set) selection3;
                Set<NationOrAlliance> selection = getSelection(this, store, input, true);
                if (selection != null)
                    return selection;
                if (SpreadSheet.isSheet(input)) {
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    return SpreadSheet.parseSheet(input, List.of("nation", "alliance"), true, (type, str) -> {
                        switch (type) {
                            case 0:
                                return PWBindings.parseNation(services, null, guild, str, null);
                            case 1:
                                return PWBindings.alliance(services, str);
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
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<NationOrAlliance> nationoralliances) {
                return _addSelectionAlias(this, command, db, name, nationoralliances, "nationoralliances");
            }
        };
    }

    private Placeholders<GuildDB, Void> createGuildDB() {
        return new SimpleVoidPlaceholders<GuildDB>(GuildDB.class, store, validators, permisser,
                "A discord guild",
                (ThrowingTriFunction<Placeholders<GuildDB, Void>, ValueStore, String, Set<GuildDB>>) (inst, store,
                        input) -> {
                    Set<GuildDB> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    User user = (User) store.getProvided(Key.of(User.class, Me.class), true);
                    boolean admin = Roles.ADMIN.hasOnRoot(user);
                    if (input.equalsIgnoreCase("*")) {
                        if (admin) {
                            return new HashSet<>(services.getGuildDatabases());
                        }
                        Set<Guild> guilds = services.getMutualGuilds(user);
                        Set<GuildDB> dbs = new ObjectOpenHashSet<>();
                        for (Guild guild : guilds) {
                            GuildDB db = services.getGuildDb(guild);
                            if (db != null) {
                                dbs.add(db);
                            }
                        }
                        return dbs;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("guild", "guild_id"), true,
                                (type, str) -> PWBindings.resolveGuildDb(services, PrimitiveBindings.Long(str)));
                    }
                    long id = PrimitiveBindings.Long(input);
                    GuildDB guild = PWBindings.resolveGuildDb(services, id);
                    if (!admin && guild.getGuild().getMember(user) == null) {
                        throw new IllegalArgumentException(
                                "You (" + user + ") are not in the guild with id: `" + id + "`");
                    }
                    return Set.of(guild);
                }, (ThrowingTriFunction<Placeholders<GuildDB, Void>, ValueStore, String, Predicate<GuildDB>>) (inst,
                        store, input) -> {
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
                        new SelectorInfo("*", null, "All shared guilds")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("guild");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of guilds")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<GuildDB> guilds) {
                return _addSelectionAlias(this, command, db, name, guilds, "guilds");
            }
        };
    }

    private Placeholders<DBBan, Void> createBans() {
        return new SimpleVoidPlaceholders<DBBan>(DBBan.class, store, validators, permisser,
                "A game ban",
                (ThrowingTriFunction<Placeholders<DBBan, Void>, ValueStore, String, Set<DBBan>>) (inst, store,
                        input) -> {
                    Set<DBBan> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*")) {
                        return new HashSet<>(nationDb().getBansByNation().values());
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("bans"), true,
                                (type, str) -> parseBan(store, guild, str));
                    }
                    return Set.of(parseBan(store, guild, input));
                }, (ThrowingTriFunction<Placeholders<DBBan, Void>, ValueStore, String, Predicate<DBBan>>) (inst, store,
                        input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
                    if (SpreadSheet.isSheet(input)) {
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        Set<Integer> sheet = SpreadSheet.parseSheet(input, List.of("bans"), true,
                            (type, str) -> services.lookup().parseNation(str, true, true, guild).getId());
                        return f -> sheet.contains(f.getNation_id());
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return f -> f.getNation_id() == id;
                    }
                    NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
                    Predicate<DBNation> filter = nationPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        DBNation nation = nationById(f.nation_id);
                        if (nation == null && f.discord_id > 0) {
                            nation = services.lookup().getNationByUser(nationSnapshot(), f.discord_id, false);
                        }
                        if (nation == null)
                            return false;
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
                        new SelectorInfo("NATION", "189573",
                                "Nation id, name, leader, url, user id or mention (see nation type)"),
                        new SelectorInfo("*", null, "All bans")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("bans");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of bans")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBBan> bans) {
                return _addSelectionAlias(this, command, db, name, bans, "bans");
            }
        };
    }

    private Placeholders<NationList, NationModifier> createNationList() {
        return new SimplePlaceholders<NationList, NationModifier>(NationList.class, NationModifier.class, store,
                validators, permisser,
                "One or more groups of nations",
                (ThrowingTriFunction<Placeholders<NationList, NationModifier>, ValueStore, String, Set<NationList>>) (
                        inst, store, input) -> {
                    Set<NationList> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);

                    if (SpreadSheet.isSheet(input)) {
                        Set<String> inputs = SpreadSheet.parseSheet(input, List.of("nations"), true,
                                (type, str) -> str);
                        Set<NationList> lists = new HashSet<>();
                        for (String str : inputs) {
                            Set<DBNation> nations = parseNations(store, str, false);
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
                        Set<DBAlliance> alliances = services.getAlliances();
                        for (DBAlliance alliance : alliances) {
                            lists.add(new SimpleNationList(alliance.getNations(filter)).setFilter(filterName));
                        }
                    } else if (input.equalsIgnoreCase("~")) {
                        GuildDB db = guild == null ? null : guildDb(guild);
                        if (db == null) {
                            db = services.getRootCoalitionServer();
                        }
                        if (db == null) {
                            throw new IllegalArgumentException(
                                    "No coalition server found, please have the bot owner set one in the `config.yaml`");
                        }
                        for (String coalition : db.getCoalitionNames()) {
                            lists.add(new SimpleNationList(
                                    nationSnapshot().getNationsByAlliance(db.getCoalition(coalition)))
                                    .setFilter(filterName));
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
                },
                (ThrowingTriFunction<Placeholders<NationList, NationModifier>, ValueStore, String, Predicate<NationList>>) (
                        inst, store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
                    throw new IllegalArgumentException(
                            "NationList predicates other than `*` are unsupported. Please use DBNation instead");
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
                        new SelectorInfo("coalition:COALITION_NAME", "coalition:allies",
                                "A single list with the nations in the coalition"),
                        new SelectorInfo("NATION", "Borg",
                                "Nation name, id, leader, url, user id or mention (see nation type)"),
                        new SelectorInfo("ALLIANCE", "AA:Rose",
                                "Alliance id, name, url or mention (see alliance type)"),
                        new SelectorInfo("SELECTOR[FILTER]", "`*[#cities>10]`, `AA:Rose[#position>1,#vm_turns=0]`",
                                "A single nation list based on a selector with an optional filter")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("nations");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of nationlists")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<NationList> nationlists) {
                return _addSelectionAlias(this, command, db, name, nationlists, "nationlists");
            }
        };
    }

    private Set<DBCity> parseCitiesSingle(ValueStore store, String input) {
        if (MathMan.isInteger(input) || input.contains("/city/id=")) {
            return Set.of(PWBindings.parseCityUrl(nationDb(), input));
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
        User user = services.lookup().getUser(input, guild);
        if (user != null) {
            return new ObjectLinkedOpenHashSet<>(List.of(new UserWrapper(guild, user)));
        }
        // Role
        Role role = DiscordUtil.getRole(guild, input);
        if (role != null) {
            return guild.getMembersWithRoles(role).stream().map(UserWrapper::new).collect(Collectors.toSet());
        }
        NationOrAlliance natOrAA = PWBindings.parseNationOrAlliance(services, null, input, false, guild);
        if (natOrAA.isNation()) {
            user = natOrAA.asNation().getUser();
            if (user == null) {
                throw new IllegalArgumentException("Nation " + natOrAA.getMarkdownUrl() + " is not registered. See: "
                        + CM.register.cmd.toSlashMention());
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
        if (input.equals("*"))
            return Predicates.alwaysTrue();
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
                    if (canUser && f.getUserId() == id)
                        return true;
                    if (canRole) {
                        Member member = f.getMember();
                        if (member != null) {
                            for (Role role : member.getUnsortedRoles()) {
                                if (role.getIdLong() == id)
                                    return true;
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
        Long id = services.lookup().parseUserId(guild, input);
        if (id != null) {
            return f -> f.getUserId() == id;
        }
        DBNation argNation = services.lookup().parseNation(input, true, false, guild);
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
                    if (role.getName().equalsIgnoreCase(finalInput))
                        return true;
                }
            }
            return false;
        };
    }

    private Placeholders<UserWrapper, Void> createUsers() {
        return new SimpleVoidPlaceholders<UserWrapper>(UserWrapper.class, store, validators, permisser,
                "A discord user",
                (ThrowingTriFunction<Placeholders<UserWrapper, Void>, ValueStore, String, Set<UserWrapper>>) (inst,
                        store, input) -> {
                    Set<UserWrapper> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                    Guild guild = db.getGuild();
                    if (SpreadSheet.isSheet(input)) {
                        Set<Member> member = SpreadSheet.parseSheet(input, List.of("user"), true,
                                (type, str) -> DiscordBindings.member(guild, null, str));
                        return member.stream().map(UserWrapper::new).collect(Collectors.toSet());
                    }
                    return parseUserSingle(guild, input);
                }, (ThrowingTriFunction<Placeholders<UserWrapper, Void>, ValueStore, String, Predicate<UserWrapper>>) (
                        inst, store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();

                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                    Guild guild = db.getGuild();

                    if (SpreadSheet.isSheet(input)) {
                        Set<Long> sheet = SpreadSheet.parseSheet(input, List.of("user"), true,
                                (type, str) -> services.lookup().parseUserId(guild, str));
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
                        new SelectorInfo("@ROLE", "@Member",
                                "All users with a discord role by a given name or mention"),
                        new SelectorInfo("ROLE_ID", "123456789012345678",
                                "All users with the discord role by a given id"),
                        new SelectorInfo("NATION", "Borg",
                                "Nation name, id, leader, url, user id or mention (see nation type) - only if registered with Locutus"),
                        new SelectorInfo("ALLIANCE", "AA:Rose",
                                "Alliance id, name, url or mention (see alliance type), resolves to the users registered with Locutus"),
                        new SelectorInfo("*", null, "All shared users")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("user");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of users")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<UserWrapper> users) {
                return _addSelectionAlias(this, command, db, name, users, "users");
            }
        };

    }

    private Placeholders<TextChannelWrapper, Void> createChannels() {
        return new SimpleVoidPlaceholders<TextChannelWrapper>(TextChannelWrapper.class, store, validators, permisser,
                "A discord channel",
                (ThrowingTriFunction<Placeholders<TextChannelWrapper, Void>, ValueStore, String, Set<TextChannelWrapper>>) (
                        inst, store, input) -> {
                    Set<TextChannelWrapper> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                    Guild guild = db.getGuild();
                    if (SpreadSheet.isSheet(input)) {
                        Set<TextChannel> channels = SpreadSheet.parseSheet(input, List.of("channel"), true,
                                (type, str) -> DiscordBindings.textChannel(guild, str));
                        return channels.stream().map(TextChannelWrapper::new).collect(Collectors.toSet());
                    }
                    return parseChannelSingle(guild, input);
                },
                (ThrowingTriFunction<Placeholders<TextChannelWrapper, Void>, ValueStore, String, Predicate<TextChannelWrapper>>) (
                        inst, store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();

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
                        new SelectorInfo("*", null, "All guild channels")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("channel");
            }
        };

    }

    private Placeholders<DBCity, Void> createCities() {
        return new SimpleVoidPlaceholders<DBCity>(DBCity.class, store, validators, permisser,
                "A city",
                (ThrowingTriFunction<Placeholders<DBCity, Void>, ValueStore, String, Set<DBCity>>) (inst, store,
                        input) -> {
                    Set<DBCity> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*")) {
                        nationDb().getCities();
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Set<DBCity>> result = SpreadSheet.parseSheet(input, List.of("city", "cities"), true,
                                (type, str) -> parseCitiesSingle(store, str));
                        Set<DBCity> cities = new ObjectLinkedOpenHashSet<>();
                        for (Set<DBCity> set : result) {
                            cities.addAll(set);
                        }
                        return cities;
                    }
                    return parseCitiesSingle(store, input);
                }, (ThrowingTriFunction<Placeholders<DBCity, Void>, ValueStore, String, Predicate<DBCity>>) (inst,
                        store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
                    if (MathMan.isInteger(input) || input.contains("/city/id=")) {
                        DBCity city = PWBindings.parseCityUrl(nationDb(), input);
                        return f -> f.getId() == city.getId();
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Set<DBCity>> result = SpreadSheet.parseSheet(input, List.of("city", "cities"), true,
                                (type, str) -> parseCitiesSingle(store, str));
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
                        DBNation nation = nationById(f.getNation_id());
                        if (nation == null)
                            return false;
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
                        new SelectorInfo("NATION", "Borg",
                                "Nation name, id, leader, url, user id or mention (see nation type)"),
                        new SelectorInfo("*", null, "All cities")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("city", "cities"));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of cities")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBCity> cities) {
                return _addSelectionAlias(this, command, db, name, cities, "cities");
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
            TaxBracket bracket = PWBindings.parseBracket(nationDb(), db, input, TimeUnit.MINUTES.toMillis(1), true);
            return Set.of(bracket);
        }
        NationPlaceholders natFormat = (NationPlaceholders) (Placeholders) get(DBNation.class);
        Set<DBNation> nations = natFormat.parseSingleElem(store, input, false);
        Set<TaxBracket> brackets = new ObjectOpenHashSet<>();
        Set<Integer> ids = new IntOpenHashSet();
        for (DBNation nation : nations) {
            if (nation.getPositionEnum().id <= Rank.APPLICANT.id || ids.contains(nation.getTax_id()))
                continue;
            ids.add(nation.getTax_id());
            brackets.add(nation.getTaxBracket().withLookup(nationDb()));
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
                if (nation != null && nation.getId() == record.nationId)
                    return true;
                if (db == null)
                    return false;
                if (!db.isAllianceId(record.allianceId))
                    return false;
                return hasEcon;
            }
        };
    }

    private Set<TaxDeposit> getTaxes(ValueStore store, Set<Integer> ids, Set<Integer> taxIds, Set<Integer> nations,
            Set<Integer> aaIds) {
        BankDB bankDb = bankDb();
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
                (ThrowingTriFunction<Placeholders<TaxDeposit, Void>, ValueStore, String, Set<TaxDeposit>>) (inst, store,
                        input) -> {
                    Set<TaxDeposit> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
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
                                case 2 -> nations.add(services.lookup().parseNation(str, true, true, guild).getId());
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
                    Set<DBNation> nations = parseNations(store, input, false);
                    Set<Integer> ids = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
                    return getTaxes(store, null, null, ids, null);

                },
                (ThrowingTriFunction<Placeholders<TaxDeposit, Void>, ValueStore, String, Predicate<TaxDeposit>>) (inst,
                        store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
                    if (SpreadSheet.isSheet(input)) {
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        Set<Integer> ids = new IntOpenHashSet();
                        Set<Integer> taxIds = new IntOpenHashSet();
                        Set<Integer> nations = new IntOpenHashSet();
                        SpreadSheet.parseSheet(input, List.of("id", "tax_id", "nation"), true, (type, str) -> {
                            switch (type) {
                                case 0 -> ids.add(Integer.parseInt(str));
                                case 1 -> taxIds.add(Integer.parseInt(str));
                                case 2 -> nations.add(services.lookup().parseNation(str, true, true, guild).getId());
                            }
                            return null;
                        });
                        return f -> {
                            if (ids.contains(f.index))
                                return true;
                            if (taxIds.contains(f.tax_id))
                                return true;
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
                        DBNation nation = nationOrCreate(f.nationId);
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
                        new SelectorInfo("TAX_URL", "/tax/id=12345", "Tax path"),
                        new SelectorInfo("NATION", "Borg",
                                "Nation name, id, leader, url, user id or mention (see nation type) - if in this guild's alliance"),
                        new SelectorInfo("ALLIANCE", "AA:Rose",
                                "Alliance id, name, url or mention (see alliance type) - if in this guild"),
                        new SelectorInfo("*", null, "All tax records with the guild")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id", "tax_id", "nation"));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of tax records")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<TaxDeposit> taxes) {
                return _addSelectionAlias(this, command, db, name, taxes, "taxes");
            }
        };
    }

    public Placeholders<Conflict, Void> createConflicts() {
        return new SimpleVoidPlaceholders<Conflict>(Conflict.class, store, validators, permisser,
                "Public and registered alliance conflicts added to the bot\n" +
                        "Unlisted conflicts are not supported by conflict selectors",
                (ThrowingTriFunction<Placeholders<Conflict, Void>, ValueStore, String, Set<Conflict>>) (inst, store,
                        input) -> {
                    Set<Conflict> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    ConflictManager manager = warDb().getConflicts();
                    if (input.equalsIgnoreCase("*")) {
                        return new ObjectLinkedOpenHashSet<>(manager.getConflictMap().values());
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("conflict"), true,
                                (type, str) -> PWBindings.conflict(manager, str));
                    }
                    return Set.of(PWBindings.conflict(manager, input));
                }, (ThrowingTriFunction<Placeholders<Conflict, Void>, ValueStore, String, Predicate<Conflict>>) (inst,
                        store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
                    ConflictManager cMan = warDb().getConflicts();
                    if (SpreadSheet.isSheet(input)) {
                        Set<Conflict> conflicts = SpreadSheet.parseSheet(input, List.of("conflict"), true,
                                (type, str) -> PWBindings.conflict(cMan, str));
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
                        new SelectorInfo("*", null, "All public conflicts")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("conflict");
            }

             @NoFormat
             @Command(desc = "Add an alias for a selection of conflicts")
             @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM,
             Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON,
             Roles.FOREIGN_AFFAIRS}, any = true)
             public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db,
             String name, Set<Conflict> conflicts) {
                return _addSelectionAlias(this, command, db, name, conflicts, "conflicts");
             }
        };
    }

    public Placeholders<GuildSetting, Void> createGuildSettings() {
        return new SimpleVoidPlaceholders<GuildSetting>(GuildSetting.class, store, validators, permisser,
                "A bot setting in a guild",
                (ThrowingTriFunction<Placeholders<GuildSetting, Void>, ValueStore, String, Set<GuildSetting>>) (inst,
                        store, input) -> {
                    Set<GuildSetting> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*")) {
                        return new ObjectLinkedOpenHashSet<>(Arrays.asList(GuildKey.values()));
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("setting"), true,
                                (type, str) -> PWBindings.key(str));
                    }
                    return Set.of(PWBindings.key(input));
                },
                (ThrowingTriFunction<Placeholders<GuildSetting, Void>, ValueStore, String, Predicate<GuildSetting>>) (
                        inst, store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
                    if (SpreadSheet.isSheet(input)) {
                        Set<GuildSetting> settings = SpreadSheet.parseSheet(input, List.of("setting"), true,
                                (type, str) -> PWBindings.key(str));
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
                        new SelectorInfo("*", null, "All guild settings")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("setting");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of guild settings")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<GuildSetting> settings) {
                return _addSelectionAlias(this, command, db, name, settings, "settings");
            }
        };
    }

    private Set<IAttack> getAttacks(Set<Integer> attackIds, Set<Integer> warIds, Set<Integer> nationIds,
            Set<Integer> alliances) {
        Set<IAttack> attacks = new ObjectOpenHashSet<>();
        if (warIds != null && !warIds.isEmpty()) {
            Set<DBWar> wars = warDb().getWarsById(warIds);
            if (!wars.isEmpty()) {
                warDb().iterateAttacksByWars(wars, (war, attack) -> {
                    attacks.add(attack);
                });
            }
        }
        if (attackIds != null && !attackIds.isEmpty()) {
            attacks.addAll(warDb().getAttacksById(attackIds));
        }
        if ((attackIds == null || attackIds.isEmpty()) && (warIds == null || warIds.isEmpty())) {
            if (nationIds != null && !nationIds.isEmpty()) {
                warDb().iterateAttacks(nationIds, 0, (war, attack) -> {
                    attacks.add(attack);
                });
            }
            if (alliances != null && !alliances.isEmpty()) {
                Set<DBWar> allWars = new ObjectOpenHashSet<>();
                for (Integer aaId : alliances) {
                    allWars.addAll(warDb().getWarsByAlliance(aaId));
                }
                warDb().iterateAttacksByWars(allWars, (war, attack) -> {
                    attacks.add(attack);
                });
            }
        }
        return attacks;
    }

    public Placeholders<IAttack, Void> createAttacks() {
        return new SimpleVoidPlaceholders<IAttack>(IAttack.class, store, validators, permisser,
                "An attack in a war",
                (ThrowingTriFunction<Placeholders<IAttack, Void>, ValueStore, String, Set<IAttack>>) (inst, store,
                        input) -> {
                    Set<IAttack> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*")) {
                        Set<IAttack> attacks = new ObjectLinkedOpenHashSet<>();
                        warDb().iterateAttacks(0, Long.MAX_VALUE, Predicates.alwaysTrue(),
                                (war, attack) -> attacks.add(attack));
                        return attacks;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> attackIds = new IntOpenHashSet();
                        Set<Integer> warIds = new IntOpenHashSet();
                        Set<Integer> nationIds = new IntOpenHashSet();
                        Set<Integer> allianceIds = new IntOpenHashSet();
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        SpreadSheet.parseSheet(input, List.of("id", "war_id", "nation", "leader", "alliance"), true,
                                (type, str) -> {
                                    switch (type) {
                                        case 0 -> attackIds.add(Integer.parseInt(str));
                                        case 1 -> warIds.add(Integer.parseInt(str));
                                        case 2 -> {
                                            DBNation nation = services.lookup().parseNation(str, true, guild);
                                            if (nation == null)
                                                throw new IllegalArgumentException("Invalid nation: `" + str + "`");
                                            nationIds.add(nation.getId());
                                        }
                                        case 3 -> {
                                            DBNation nation = nationSnapshot().getNationByLeader(str);
                                            if (nation == null)
                                                throw new IllegalArgumentException(
                                                        "Invalid nation leader: `" + str + "`");
                                            nationIds.add(nation.getId());
                                        }
                                        case 4 -> {
                                            DBAlliance alliance = PWBindings.alliance(services, str);
                                            if (alliance == null)
                                                throw new IllegalArgumentException("Invalid alliance: `" + str + "`");
                                            allianceIds.add(alliance.getId());
                                        }
                                    }
                                    return null;
                                });
                        return getAttacks(attackIds, warIds, nationIds, allianceIds);
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        Set attacks = warDb().getAttacksById(Set.of(id));
                        return attacks;
                    }
                    if (input.contains("/war/id=")) {
                        int warId = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                        return getAttacks(Set.of(), Set.of(warId), null, null);
                    }
                    Set<NationOrAlliance> natOrAA = nationOrAlliancesSingle(store, input, false);
                    Set<Integer> nationIds = natOrAA.stream().filter(NationOrAlliance::isNation)
                            .map(NationOrAlliance::getId).collect(Collectors.toSet());
                    Set<Integer> aaIds = natOrAA.stream().filter(NationOrAlliance::isAlliance)
                            .map(NationOrAlliance::getId).collect(Collectors.toSet());
                    return getAttacks(Set.of(), Set.of(), nationIds, aaIds);
                }, (ThrowingTriFunction<Placeholders<IAttack, Void>, ValueStore, String, Predicate<IAttack>>) (inst,
                        store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
                    if (SpreadSheet.isSheet(input)) {
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        Set<Integer> attackIds = new IntOpenHashSet();
                        Set<Integer> warIds = new IntOpenHashSet();
                        Set<Integer> nationIds = new IntOpenHashSet();
                        Set<Integer> allianceIds = new IntOpenHashSet();
                        SpreadSheet.parseSheet(input, List.of("id", "war_id", "nation", "leader", "alliance"), true,
                                (type, str) -> {
                                    switch (type) {
                                        case 0 -> attackIds.add(Integer.parseInt(str));
                                        case 1 -> warIds.add(Integer.parseInt(str));
                                        case 2 -> {
                                            DBNation nation = services.lookup().parseNation(str, true, guild);
                                            if (nation == null)
                                                throw new IllegalArgumentException("Invalid nation: `" + str + "`");
                                            nationIds.add(nation.getId());
                                        }
                                        case 3 -> {
                                            DBNation nation = nationSnapshot().getNationByLeader(str);
                                            if (nation == null)
                                                throw new IllegalArgumentException(
                                                        "Invalid nation leader: `" + str + "`");
                                            nationIds.add(nation.getId());
                                        }
                                        case 4 -> {
                                            DBAlliance alliance = PWBindings.alliance(services, str);
                                            if (alliance == null)
                                                throw new IllegalArgumentException("Invalid alliance: `" + str + "`");
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
                            Predicate<IAttack> nationFilter = f -> nationIds.contains(f.getAttacker_id())
                                    || nationIds.contains(f.getDefender_id());
                            filter = filter == null ? nationFilter : filter.or(nationFilter);
                        }
                        if (!allianceIds.isEmpty()) {
                            Predicate<IAttack> aaFilter = f -> {
                                DBWar war = f.getWar();
                                if (war != null) {
                                    return allianceIds.contains(war.getAttacker_aa())
                                            || allianceIds.contains(war.getDefender_aa());
                                }
                                return false;
                            };
                            filter = filter == null ? aaFilter : filter.or(aaFilter);
                        }
                        if (filter == null)
                            filter = Predicates.alwaysFalse();
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
                        Set<NationOrAlliance> allowed = PWBindings.nationOrAlliance(store, null, guild, input, true, author,
                            me);
                    return f -> {
                        DBWar war = f.getWar();
                        DBNation attacker = nationOrCreate(f.getAttacker_id());
                        DBNation defender = nationOrCreate(f.getDefender_id());
                        if (allowed.contains(attacker) || allowed.contains(defender))
                            return true;
                        if (war == null)
                            return false;
                        DBAlliance attackerAA = war.getAttacker_aa() != 0 ? allianceOrCreate(war.getAttacker_aa())
                                : null;
                        if (attackerAA != null && allowed.contains(attackerAA))
                            return true;
                        DBAlliance defenderAA = war.getDefender_aa() != 0 ? allianceOrCreate(war.getDefender_aa())
                                : null;
                        if (defenderAA != null && allowed.contains(defenderAA))
                            return true;
                        return false;
                    };
                }, new Function<IAttack, String>() {
                    @Override
                    public String apply(IAttack iAttack) {
                        return iAttack.getWar_attack_id() + "";
                    }
                }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                Set<SelectorInfo> mySet = new ObjectLinkedOpenHashSet<>(
                        List.of(new SelectorInfo("ATTACK_ID", "123456", "Attack ID")));
                mySet.addAll(PlaceholdersMap.this.get(DBWar.class).getSelectorInfo());
                return mySet;
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id", "war_id", "nation", "leader", "alliance"));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of attacks")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<IAttack> attacks) {
                return _addSelectionAlias(this, command, db, name, attacks, "attacks");
            }
        };
    }

    public Placeholders<DBWar, Void> createWars() {
        return new SimpleVoidPlaceholders<DBWar>(DBWar.class, store, validators, permisser,
                "A war",
                (ThrowingTriFunction<Placeholders<DBWar, Void>, ValueStore, String, Set<DBWar>>) (inst, store,
                        input) -> {
                    Set<DBWar> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (SpreadSheet.isSheet(input)) {
                        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                        Set<Integer> warIds = new IntOpenHashSet();
                        Set<Integer> nationIds = new IntOpenHashSet();
                        Set<Integer> allianceIds = new IntOpenHashSet();
                        SpreadSheet.parseSheet(input, List.of("id", "war_id", "nation", "leader", "alliance"), true,
                                (type, str) -> {
                                    switch (type) {
                                        case 0, 1 -> warIds.add(Integer.parseInt(str));
                                        case 2 -> {
                                            DBNation nation = services.lookup().parseNation(str, true, guild);
                                            if (nation == null)
                                                throw new IllegalArgumentException("Invalid nation: `" + str + "`");
                                            nationIds.add(nation.getId());
                                        }
                                        case 3 -> {
                                            DBNation nation = nationSnapshot().getNationByLeader(str);
                                            if (nation == null)
                                                throw new IllegalArgumentException(
                                                        "Invalid nation leader: `" + str + "`");
                                            nationIds.add(nation.getId());
                                        }
                                        case 4 -> {
                                            DBAlliance alliance = PWBindings.alliance(services, str);
                                            if (alliance == null)
                                                throw new IllegalArgumentException("Invalid alliance: `" + str + "`");
                                            allianceIds.add(alliance.getId());
                                        }
                                    }
                                    return null;
                                });
                        if (!warIds.isEmpty()) {
                            return warDb().getWarsById(warIds);
                        }
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return warDb().getWarsById(Set.of(id));
                    }
                    if (input.contains("/war/id=")) {
                        int warId = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                        return warDb().getWarsById(Set.of(warId));
                    }
                    Set<NationOrAlliance> natOrAA = nationOrAlliancesSingle(store, input, false);
                    Set<Integer> natIds = natOrAA.stream().filter(NationOrAlliance::isNation)
                            .map(NationOrAlliance::getId).collect(Collectors.toSet());
                    Set<Integer> aaIds = natOrAA.stream().filter(NationOrAlliance::isAlliance)
                            .map(NationOrAlliance::getId).collect(Collectors.toSet());
                    return warDb().getWarsForNationOrAlliance(natIds, aaIds);
                }, (ThrowingTriFunction<Placeholders<DBWar, Void>, ValueStore, String, Predicate<DBWar>>) (inst, store,
                        input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
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
                        Set<NationOrAlliance> allowed = PWBindings.nationOrAlliance(store, null, guild, input, true, author,
                            me);
                    return war -> {
                        DBNation attacker = nationOrCreate(war.getAttacker_id());
                        DBNation defender = nationOrCreate(war.getDefender_id());
                        if (allowed.contains(attacker) || allowed.contains(defender))
                            return true;
                        DBAlliance attackerAA = war.getAttacker_aa() != 0 ? allianceOrCreate(war.getAttacker_aa())
                                : null;
                        if (attackerAA != null && allowed.contains(attackerAA))
                            return true;
                        DBAlliance defenderAA = war.getDefender_aa() != 0 ? allianceOrCreate(war.getDefender_aa())
                                : null;
                        if (defenderAA != null && allowed.contains(defenderAA))
                            return true;
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
                        new SelectorInfo("NATION", "Borg",
                                "Nation name, id, leader, url, user id or mention (see nation type)"),
                        new SelectorInfo("ALLIANCE", "AA:Rose",
                                "Alliance id, name, url or mention (see alliance type)"),
                        new SelectorInfo("*", null, "All wars")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id", "war_id", "nation", "leader", "alliance"));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of wars")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBWar> wars) {
                return _addSelectionAlias(this, command, db, name, wars, "wars");
            }
        };
    }

    public Placeholders<TaxBracket, Void> createBrackets() {
        return new SimpleVoidPlaceholders<TaxBracket>(TaxBracket.class, store, validators, permisser,
                "A tax bracket",
                (ThrowingTriFunction<Placeholders<TaxBracket, Void>, ValueStore, String, Set<TaxBracket>>) (inst,
                        store2, input) -> {
                    Set<TaxBracket> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    GuildDB db = (GuildDB) store2.getProvided(Key.of(GuildDB.class, Me.class), false);
                    if (input.equalsIgnoreCase("*")) {
                        if (db != null) {
                            AllianceList aaList = db.getAllianceList();
                            if (aaList != null) {
                                return new HashSet<TaxBracket>(
                                        aaList.getTaxBrackets(services.allianceLookup(), TimeUnit.MINUTES.toMillis(5)).values());
                            }
                        }
                        Map<Integer, Integer> ids = nationDb().getAllianceIdByTaxId();
                        return ids.entrySet().stream()
                            .map(f -> new TaxBracket(f.getKey(), f.getValue(), "", -1, -1, 0)
                                .withLookup(nationDb()))
                                .collect(Collectors.toSet());
                    }
                    if (SpreadSheet.isSheet(input)) {
                        String finalInput = input;
                        Set<Set<TaxBracket>> result = SpreadSheet.parseSheet(input, List.of("id"), true,
                                (type, str) -> bracketSingle(store2, db, finalInput));
                        Set<TaxBracket> brackets = new ObjectOpenHashSet<>();
                        for (Set<TaxBracket> set : result) {
                            brackets.addAll(set);
                        }
                        return brackets;
                    }
                    return bracketSingle(store2, db, input);
                },
                (ThrowingTriFunction<Placeholders<TaxBracket, Void>, ValueStore, String, Predicate<TaxBracket>>) (inst,
                        store, input) -> {
                    if (input.equalsIgnoreCase("*")) {
                        return Predicates.alwaysTrue();
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true,
                                (type, str) -> Integer.parseInt(str));
                        return f -> ids.contains(f.getId());
                    }
                    if (input.contains("tx_id=") || MathMan.isInteger(input)) {
                        int id = PW.parseTaxId(input);
                        return f -> f.getId() == id;
                    }
                    AlliancePlaceholders aaPlaceholders = (AlliancePlaceholders) (Placeholders) get(DBAlliance.class);
                    Predicate<DBAlliance> filter = aaPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        if (f.getId() == 0)
                            return false;
                        DBAlliance aa = f.getAlliance();
                        if (aa == null)
                            return false;
                        return filter.test(aa);
                    };
                }, new Function<TaxBracket, String>() {
                    @Override
                    public String apply(TaxBracket taxBracket) {
                        return taxBracket.toString();
                    }
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id"));
            }

            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("TAX_ID", "tx_id=12345", "Tax Bracket ID"),
                        new SelectorInfo("ALLIANCE", "AA:Rose",
                                "Alliance id, name, url or mention (see alliance type)"),
                        new SelectorInfo("*", null,
                                "All tax brackets in this guilds alliances, else all tax brackets")));
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of tax brackets")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<TaxBracket> taxbrackets) {
                return _addSelectionAlias(this, command, db, name, taxbrackets, "taxbrackets");
            }
        };
    }

    private Placeholders<DBTrade, Void> createTrades() {
        return new SimpleVoidPlaceholders<DBTrade>(DBTrade.class, store, validators, permisser,
                "A completed trade",
                (ThrowingTriFunction<Placeholders<DBTrade, Void>, ValueStore, String, Set<DBTrade>>) (inst, store,
                        input) -> {
                    Set<DBTrade> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*")) {
                        throw new UnsupportedOperationException("`*` is not supported. Only trade ids are supported");
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true,
                                (type, str) -> Integer.parseInt(str));
                        return new ObjectOpenHashSet<>(tradeDb().getTradesById(ids));
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return Set.of(tradeDb().getTradeById(id));
                    }
                    throw new IllegalArgumentException("Only trade ids are supported, not `" + input + "`");
                }, (ThrowingTriFunction<Placeholders<DBTrade, Void>, ValueStore, String, Predicate<DBTrade>>) (inst,
                        store, input) -> {
                    if (input.equalsIgnoreCase("*")) {
                        return Predicates.alwaysTrue();
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true,
                                (type, str) -> Integer.parseInt(str));
                        return f -> ids.contains(f.getTradeId());
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return f -> f.getTradeId() == id;
                    }
                    NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders) get(DBNation.class);
                    Predicate<DBNation> filter = nationPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        DBNation sender = nationById(f.getSeller());
                        DBNation receiver = nationById(f.getBuyer());
                        if (sender != null && filter.test(sender))
                            return true;
                        if (receiver != null && filter.test(receiver))
                            return true;
                        return false;
                    };
                }, new Function<DBTrade, String>() {
                    @Override
                    public String apply(DBTrade dbTrade) {
                        return dbTrade.getTradeId() + "";
                    }
                }) {
            @Override
            public Set<SelectorInfo> getSelectorInfo() {
                return new ObjectLinkedOpenHashSet<>(List.of(
                        new SelectorInfo("TRADE_ID", "12345", "Trade ID"),
                        new SelectorInfo("NATION", "Borg",
                                "Nation name, id, leader, url, user id or mention (see nation type)")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return new ObjectLinkedOpenHashSet<>(List.of("id"));
            }
        };
    }

    private Placeholders<Transaction2, Void> createTransactions() {
        return new SimpleVoidPlaceholders<Transaction2>(Transaction2.class, store, validators, permisser,
                "A bank transaction",
                (ThrowingTriFunction<Placeholders<Transaction2, Void>, ValueStore, String, Set<Transaction2>>) (inst,
                        store, input) -> {
                    Set<Transaction2> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
                    User user = (User) store.getProvided(Key.of(User.class, Me.class), false);
                    DBNation nation = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);

                    if (input.equalsIgnoreCase("*")) {
                        throw new UnsupportedOperationException(
                                "`*` is not supported. Only transaction ids are supported");
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true,
                                (type, str) -> Integer.parseInt(str));
                        List<Transaction2> transactions = bankDb().getTransactionsbyId(ids);
                        return filterTransactions(nation, user, db, transactions);
                    }
                    if (MathMan.isInteger(input)) {
                        List<Transaction2> transactions = bankDb().getTransactionsbyId(
                                Set.of(Integer.parseInt(input)));
                        return filterTransactions(nation, user, db, transactions);
                    }
                    throw new IllegalArgumentException("Invalid transaction id: " + input);
                },
                (ThrowingTriFunction<Placeholders<Transaction2, Void>, ValueStore, String, Predicate<Transaction2>>) (
                        inst, store, input) -> {
                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
                    User user = (User) store.getProvided(Key.of(User.class, Me.class), false);
                    DBNation nation = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);

                    Predicate<Transaction2> filter = getAllowed(nation, user, db);

                    if (input.equalsIgnoreCase("*"))
                        return filter;
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true,
                                (type, str) -> Integer.parseInt(str));
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
                        new SelectorInfo("NATION", "Borg",
                                "Nation name, id, leader, url, user id or mention (see nation type)")));
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
                            Set<NationOrAlliance> senders = sendersStr == null ? null
                                : PWBindings.nationOrAlliance(store, null, guild, sendersStr, true, author, me);

                            String receiversStr = json.optString("receiver", null);
                            Set<NationOrAlliance> receivers = receiversStr == null ? null
                                : PWBindings.nationOrAlliance(store, null, guild, receiversStr, true, author, me);

                            String bankersStr = json.optString("banker", null);
                            Set<NationOrAlliance> bankers = bankersStr == null ? null
                                : PWBindings.nationOrAlliance(store, null, guild, bankersStr, true, author, me);

                            Predicate<Transaction2> transactionFilter = json.has("transactionFilter")
                                    ? parseFilter(store, json.getString("transactionFilter"))
                                    : null;

                            Long startTime = json.has("startTime")
                                    ? PrimitiveBindings.timestamp(json.getString("startTime"))
                                    : null;

                            Long endTime = json.has("endTime") ? PrimitiveBindings.timestamp(json.getString("endTime"))
                                    : null;

                            Boolean includeOffset = json.has("includeOffset") ? json.getBoolean("includeOffset") : null;

                            List<Transaction2> transfers = bankDb().getAllTransactions(senders,
                                    receivers, bankers, startTime, endTime);
                            return new ObjectLinkedOpenHashSet<>(transfers);
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    case "deposits" -> {
                        try {
                            GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                            // "nationOrAllianceOrGuild", "transactionFilter", "startTime", "endTime",
                            // "startTime", "endTime", "includeOffset"
                            String nationOrAllianceOrGuildStr = json.optString("nationOrAllianceOrGuild", null);
                                NationOrAllianceOrGuild nationOrAllianceOrGuild = nationOrAllianceOrGuildStr == null ? null
                                    : (NationOrAllianceOrGuild) PWBindings.parseNationOrAllianceOrGuildOrTaxId(
                                        services, nationOrAllianceOrGuildStr, false, null, null);

                            Predicate<Transaction2> transactionFilter = json.has("transactionFilter")
                                    ? parseFilter(store, json.getString("transactionFilter"))
                                    : Predicates.alwaysTrue();

                            long startTime = json.has("startTime")
                                    ? PrimitiveBindings.timestamp(json.getString("startTime"))
                                    : 0;

                            long endTime = json.has("endTime") ? PrimitiveBindings.timestamp(json.getString("endTime"))
                                    : Long.MAX_VALUE;

                            boolean excludeOffset = json.has("excludeOffset") && json.getBoolean("excludeOffset");
                            boolean excludeTaxes = json.has("excludeTaxes") && json.getBoolean("excludeTaxes");
                            boolean includeFullTaxes = json.has("includeFullTaxes")
                                    && json.getBoolean("includeFullTaxes");

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
                                List<Map.Entry<Integer, Transaction2>> transactions = nation.getTransactions(db, null,
                                        !excludeTaxes, !includeFullTaxes, !excludeOffset, -1, startTime, endTime,
                                        false);
                                return transactions.stream()
                                        .filter(f -> filterFinal.test(f.getValue()))
                                        .map(Map.Entry::getValue)
                                        .collect(Collectors.toCollection(ObjectLinkedOpenHashSet::new));
                            } else {
                                OffshoreInstance offshore = db.getOffshore();
                                if (offshore == null) {
                                    throw new IllegalArgumentException("This guild does not have an offshore. See: "
                                            + CM.offshore.add.cmd.toSlashMention());
                                }
                                List<Transaction2> transfers;
                                if (db.isOffshore()) {
                                    if (nationOrAllianceOrGuild.isAlliance()) {
                                        transfers = offshore.getTransactionsAA(nationOrAllianceOrGuild.getId(), false,
                                                startTime, endTime);
                                    } else {
                                        transfers = offshore.getTransactionsGuild(nationOrAllianceOrGuild.getIdLong(),
                                                false, startTime, endTime);
                                    }
                                } else {
                                    if (nationOrAllianceOrGuild.isAlliance()) {
                                        DBAlliance aa = nationOrAllianceOrGuild.asAlliance();
                                        if (!db.isAllianceId(aa.getId())) {
                                            throw new IllegalArgumentException("The alliance " + aa.getMarkdownUrl()
                                                    + " is not registered to this guild " + guild.toString());
                                        }
                                        transfers = offshore.getTransactionsAA(aa.getId(), false, startTime, endTime);
                                    } else {
                                        GuildDB account = nationOrAllianceOrGuild.asGuild();
                                        if (account != db) {
                                            throw new IllegalArgumentException("You cannot check the balance of "
                                                    + account.getGuild() + " from this guild " + guild.toString());
                                        }
                                        transfers = offshore.getTransactionsGuild(account.getIdLong(), false, startTime,
                                                endTime);
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
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String bankRecordsAllAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    @Default Set<NationOrAlliance> sender,
                    @Default Set<NationOrAlliance> receiver,
                    @Default Set<NationOrAlliance> senderOrReceiver,
                    @Default Set<NationOrAlliance> banker, @Default Predicate<Transaction2> transactionFilter,
                    @Default @Timestamp Long startTime, @Default @Timestamp Long endTime,
                    @Switch("o") Boolean includeOffsets) {
                if (senderOrReceiver != null && (sender != null || receiver != null)) {
                    throw new IllegalArgumentException(
                            "Cannot specify `sender` or `receiver` when `senderOrReceiver` is specified");
                }
                if (startTime != null && endTime != null && startTime > endTime) {
                    throw new IllegalArgumentException("Start time cannot be after end time");
                }
                return _addSelectionAlias(this, "all", command, db, name, "sender", "receiver", "banker",
                        "transactionFilter", "startTime", "endTime", "includeOffset");
            }

            @NoFormat
            @Command(desc = "Add columns to a Transaction sheet")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String bankRecordsDeposits(@Me JSONObject command, @Me GuildDB db, String name,
                    NationOrAllianceOrGuild nationOrAllianceOrGuild, @Default Predicate<Transaction2> transactionFilter,
                    @Default @Timestamp Long startTime, @Default @Timestamp Long endTime,
                    @Switch("o") Boolean excludeOffset, @Switch("t") Boolean excludeTaxes,
                    @Switch("i") Boolean includeFullTaxes) {
                return _addSelectionAlias(this, "deposits", command, db, name, "nationOrAllianceOrGuild",
                        "transactionFilter", "startTime", "endTime", "startTime", "endTime", "excludeOffset",
                        "excludeTaxes", "includeFullTaxes");
            }
        };
    }

    private Placeholders<DBBounty, Void> createBounties() {
        return new SimpleVoidPlaceholders<DBBounty>(DBBounty.class, store, validators, permisser,
                "A bounty",
                (ThrowingTriFunction<Placeholders<DBBounty, Void>, ValueStore, String, Set<DBBounty>>) (inst, store,
                        input) -> {
                    Set<DBBounty> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*")) {
                        Set<DBBounty> result = new HashSet<>();
                        warDb().getBountiesByNation().values().forEach(result::addAll);
                        return result;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("bounty"), true,
                                (type, str) -> parseBounty(str));
                    }
                    if (MathMan.isInteger(input)) {
                        return Set.of(parseBounty(input));
                    }
                    Set<DBNation> nations = parseNations(store, input, false);
                    Map<Integer, List<DBBounty>> bounties = warDb().getBountiesByNation();
                    Set<DBBounty> bountySet = new ObjectLinkedOpenHashSet<>();
                    for (DBNation nation : nations) {
                        List<DBBounty> list = bounties.get(nation.getId());
                        if (list != null) {
                            bountySet.addAll(list);
                        }
                    }
                    return bountySet;
                }, (ThrowingTriFunction<Placeholders<DBBounty, Void>, ValueStore, String, Predicate<DBBounty>>) (inst,
                        store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
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
                        DBNation nation = nationById(f.getNationId());
                        if (nation == null)
                            return false;
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
                        new SelectorInfo("*", null, "All bounties")));
                result.addAll(PlaceholdersMap.this.get(DBNation.class).getSelectorInfo());
                return result;
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("bounty");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of bounties")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<DBBounty> bounties) {
                return _addSelectionAlias(this, command, db, name, bounties, "bounties");
            }
        };
    }

    private Placeholders<Treaty, Void> createTreaty() {
        return new SimpleVoidPlaceholders<Treaty>(Treaty.class, store, validators, permisser,
                "A treaty between two alliances",
                (ThrowingTriFunction<Placeholders<Treaty, Void>, ValueStore, String, Set<Treaty>>) (inst, store,
                        input) -> {
                    Set<Treaty> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*")) {
                        return nationDb().getTreaties();
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("treaty"), true,
                                (type, str) -> PWBindings.treaty(services, str));
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    GuildDB db = guild == null ? null : guildDb(guild);
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
                    return nationDb().getTreatiesMatching(f -> {
                        return (aa1.contains(f.getFromId())) && (aa2.contains(f.getToId()))
                                || (aa1.contains(f.getToId())) && (aa2.contains(f.getFromId()));
                    });
                }, (ThrowingTriFunction<Placeholders<Treaty, Void>, ValueStore, String, Predicate<Treaty>>) (inst,
                        store, input) -> {
                    if (input.equalsIgnoreCase("*"))
                        return Predicates.alwaysTrue();
                    if (SpreadSheet.isSheet(input)) {
                        Set<Treaty> sheet = SpreadSheet.parseSheet(input, List.of("treaty"), true,
                                (type, str) -> PWBindings.treaty(services, str));

                        Map<Integer, Set<Integer>> treatyIds = new HashMap<>();
                        for (Treaty treaty : sheet) {
                            treatyIds.computeIfAbsent(treaty.getFromId(), k -> new IntOpenHashSet())
                                    .add(treaty.getToId());
                            treatyIds.computeIfAbsent(treaty.getToId(), k -> new IntOpenHashSet())
                                    .add(treaty.getFromId());
                        }
                        return f -> treatyIds.getOrDefault(f.getFromId(), Collections.emptySet()).contains(f.getToId());
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    GuildDB db = guild == null ? null : guildDb(guild);
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
                        throw new IllegalArgumentException(
                                "Invalid treaty alliance or coalition: `" + split.get(0) + "`");
                    }
                    if (aa2 == null && coalitionId2 == null) {
                        throw new IllegalArgumentException(
                                "Invalid treaty alliance or coalition: `" + split.get(1) + "`");
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
                        new SelectorInfo("ALLIANCES:ALLIANCES", "`Rose:Eclipse`, `Rose,Eclipse:~allies`",
                                "A treaty between two sets of alliances or coalitions (direction agnostic)"),
                        new SelectorInfo("ALLIANCES>ALLIANCES", "Rose>Eclipse",
                                "A treaty from one alliance or coalition to another"),
                        new SelectorInfo("ALLIANCES<ALLIANCES", "Rose<Eclipse",
                                "A treaty from one alliance or coalition to another"),
                        new SelectorInfo("*", null, "All treaties")));
            }

            @Override
            public Set<String> getSheetColumns() {
                return Set.of("treaty");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of treaties")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Treaty> treaties) {
                return _addSelectionAlias(this, command, db, name, treaties, "treaties");
            }
        };
    }

    private Placeholders<Project, Void> createProjects() {
        return new StaticPlaceholders<Project>(Project.class, Projects::values, store, validators, permisser,
                "A project",
                (ThrowingTriFunction<Placeholders<Project, Void>, ValueStore, String, Set<Project>>) (inst, store,
                        input) -> {
                    Set<Project> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(Projects.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("project"), true,
                                (type, str) -> PWBindings.project(str));
                    }
                    Set<Project> result = new HashSet<>();
                    for (String type : input.split(",")) {
                        Project project = Projects.get(type);
                        if (project == null)
                            throw new IllegalArgumentException("Invalid project: `" + type + "`");
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
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<Project> projects) {
                return _addSelectionAlias(this, command, db, name, projects, "projects");
            }
        };
    }

    private Placeholders<ResourceType, Void> createResourceType() {
        return new StaticPlaceholders<ResourceType>(ResourceType.class, ResourceType::values, store, validators,
                permisser,
                "A game resource",
                (ThrowingTriFunction<Placeholders<ResourceType, Void>, ValueStore, String, Set<ResourceType>>) (inst,
                                                                                                                store, input) -> {
                    Set<ResourceType> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(ResourceType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("resource"), true,
                                (type, str) -> PWBindings.resource(str));
                    }
                    return Set.of(PWBindings.resource(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("resource");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of resources")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                                            Set<ResourceType> resources) {
                return _addSelectionAlias(this, command, db, name, resources, "resources");
            }
        };
    }

    private Placeholders<AttackType, Void> createAttackTypes() {
        return new StaticPlaceholders<AttackType>(AttackType.class, AttackType::values, store, validators, permisser,
                "A war attack type",
                (ThrowingTriFunction<Placeholders<AttackType, Void>, ValueStore, String, Set<AttackType>>) (inst, store,
                        input) -> {
                    Set<AttackType> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(AttackType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("attack_type"), true,
                                (type, str) -> PWBindings.attackType(str));
                    }
                    return Set.of(PWBindings.attackType(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("attack_type");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of attack types")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<AttackType> attack_types) {
                return _addSelectionAlias(this, command, db, name, attack_types, "attack_types");
            }
        };
    }

    private Placeholders<MilitaryUnit, Void> createMilitaryUnit() {
        return new StaticPlaceholders<MilitaryUnit>(MilitaryUnit.class, MilitaryUnit::values, store, validators,
                permisser,
                "A military unit type",
                (ThrowingTriFunction<Placeholders<MilitaryUnit, Void>, ValueStore, String, Set<MilitaryUnit>>) (inst,
                        store, input) -> {
                    Set<MilitaryUnit> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(MilitaryUnit.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("unit"), true,
                                (type, str) -> PWBindings.unit(str));
                    }
                    return Set.of(PWBindings.unit(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("unit");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Military Units")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<MilitaryUnit> military_units) {
                return _addSelectionAlias(this, command, db, name, military_units, "military_units");
            }
        };
    }

    private Placeholders<TreatyType, Void> createTreatyType() {
        return new StaticPlaceholders<TreatyType>(TreatyType.class, TreatyType::values, store, validators, permisser,
                "A treaty type",
                (ThrowingTriFunction<Placeholders<TreatyType, Void>, ValueStore, String, Set<TreatyType>>) (inst, store,
                        input) -> {
                    Set<TreatyType> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(TreatyType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("treaty_type"), true,
                                (type, str) -> PWBindings.TreatyType(str));
                    }
                    return Set.of(PWBindings.TreatyType(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("treaty_type");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Treaty Types")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<TreatyType> treaty_types) {
                return _addSelectionAlias(this, command, db, name, treaty_types, "treaty_types");
            }
        };
    }

    private Placeholders<DBTreasure, Void> createTreasure() {
        Supplier<DBTreasure[]> treasures = () -> nationDb().getTreasuresByName().values()
                .toArray(new DBTreasure[0]);
        return new StaticPlaceholders<DBTreasure>(DBTreasure.class, treasures, store, validators, permisser,
                "A treasure",
                (ThrowingTriFunction<Placeholders<DBTreasure, Void>, ValueStore, String, Set<DBTreasure>>) (inst, store,
                        input) -> {
                    Set<DBTreasure> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(nationDb().getTreasuresByName().values());
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("treasure"), true,
                                (type, str) -> parseTreasure(str));
                    }
                    try {
                        return Set.of(parseTreasure(input));
                    } catch (IllegalArgumentException e) {
                        Set<DBNation> nations = parseNations(store, input, false);
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
                selectors.addAll(PlaceholdersMap.this.get(DBNation.class).getSelectorInfo());
                selectors.addAll(PlaceholdersMap.this.get(DBAlliance.class).getSelectorInfo());
                return selectors;
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Treasures")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<DBTreasure> treasures) {
                return _addSelectionAlias(this, command, db, name, treasures, "treasures");
            }
        };
    }

    private Placeholders<AuditType, Void> createAuditType() {
        return new StaticPlaceholders<AuditType>(AuditType.class, AuditType::values, store, validators, permisser,
                "A bot audit type for a nation",
                (ThrowingTriFunction<Placeholders<AuditType, Void>, ValueStore, String, Set<AuditType>>) (inst, store,
                        input) -> {
                    Set<AuditType> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(AuditType.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("audit"), true,
                                (type, str) -> PWBindings.auditType(str));
                    }
                    return Set.of(PWBindings.auditType(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("audit");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Audit Types")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<AuditType> audit_types) {
                return _addSelectionAlias(this, command, db, name, audit_types, "audit_types");
            }
        };
    }

    private Placeholders<NationColor, Void> createNationColor() {
        return new StaticPlaceholders<NationColor>(NationColor.class, NationColor::values, store, validators, permisser,
                "A nation color",
                (ThrowingTriFunction<Placeholders<NationColor, Void>, ValueStore, String, Set<NationColor>>) (inst,
                        store, input) -> {
                    Set<NationColor> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(NationColor.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("color"), true,
                                (type, str) -> PWBindings.NationColor(str));
                    }
                    return Set.of(PWBindings.NationColor(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("color");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Nation Colors")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<NationColor> colors) {
                return _addSelectionAlias(this, command, db, name, colors, "colors");
            }
        };
    }

    private Placeholders<Building, Void> createBuilding() {
        return new StaticPlaceholders<Building>(Building.class, Buildings::values, store, validators, permisser,
                "A city building type",
                (ThrowingTriFunction<Placeholders<Building, Void>, ValueStore, String, Set<Building>>) (inst, store,
                        input) -> {
                    Set<Building> selection = getSelection(inst, store, input);
                    if (selection != null)
                        return selection;
                    if (input.equalsIgnoreCase("*"))
                        return new HashSet<>(Arrays.asList(Buildings.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("building"), true,
                                (type, str) -> PWBindings.getBuilding(str));
                    }
                    return Set.of(PWBindings.getBuilding(input));
                }) {
            @Override
            public Set<String> getSheetColumns() {
                return Set.of("building");
            }

            @NoFormat
            @Command(desc = "Add an alias for a selection of Buildings")
            @RolePermission(value = { Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF,
                    Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS }, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name,
                    Set<Building> Buildings) {
                return _addSelectionAlias(this, command, db, name, Buildings, "Buildings");
            }
        };
    }

}
