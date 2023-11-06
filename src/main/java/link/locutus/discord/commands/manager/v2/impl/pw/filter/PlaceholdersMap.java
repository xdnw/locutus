package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
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
import link.locutus.discord.commands.manager.v2.binding.annotation.AllowDeleted;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBBan;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.binding.BindingHelper.emumSet;

public class PlaceholdersMap {
    private final Map<Class<?>, Placeholders<?>> placeholders = new ConcurrentHashMap<>();
    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public PlaceholdersMap(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        this.store = store;
        this.validators = validators;
        this.permisser = permisser;

        this.placeholders.put(DBNation.class, new NationPlaceholders(store, validators, permisser));
        this.placeholders.put(DBAlliance.class, new AlliancePlaceholders(store, validators, permisser));
        this.placeholders.put(NationOrAlliance.class, createNationOrAlliances());
        this.placeholders.put(Continent.class, createContinents());
        this.placeholders.put(GuildDB.class, createGuildDB());
        //- Projects
        this.placeholders.put(Project.class, createProjects());
        //- Treaty
        // - *, alliances
        this.placeholders.put(Treaty.class, createTreaty());
        //- Bans
        // - *, nation, user mention
        this.placeholders.put(DBBan.class, createBans());
        //- resource type
        this.placeholders.put(ResourceType.class, createResourceType());
        //- attack type
        this.placeholders.put(AttackType.class, createAttackTypes());
        //- military unit
        this.placeholders.put(MilitaryUnit.class, createMilitaryUnit());
        //- treaty type
        this.placeholders.put(TreatyType.class, createTreatyType());
        //- Treasure
        this.placeholders.put(DBTreasure.class, createAuditType());
        //- Color bloc
        this.placeholders.put(NationColor.class, createNationColor());
        //- building
        this.placeholders.put(Building.class, createBuilding());
        //-AuditType
        this.placeholders.put(IACheckup.AuditType.class, createAuditType());
        // NationList
        // uses square brackets ?? [*,#position>1] or coalitions ~odoo or ~allies
        this.placeholders.put(NationList.class, createNationList());
        //- Bounties
        this.placeholders.put(DBBounty.class, createBounties());
        //- Cities
        // - *, nations
        this.placeholders.put(DBCity.class, createCities());

        this.placeholders.put(Transaction2.class, createTransactions());
        this.placeholders.put(DBTrade.class, createTrades());
        this.placeholders.put(TaxBracket.class, createBrackets());
        this.placeholders.put(IAttack.class, createAttacks());
        this.placeholders.put(BankDB.TaxDeposit.class, createTaxDeposit());

//        //-GuildKey
//        this.placeholders.put(GuildSetting.class, createGuildSetting());
//        //- Tax records
//        // - * (within aa)
//        this.placeholders.put(AGrantTemplate.class, createGrantTemplates());
    }

    public <T> Placeholders<T> get(Class<T> type) {
        return (Placeholders<T>) this.placeholders.get(type);
    }

    private Placeholders<Continent> createContinents() {
        return Placeholders.createStatic(Continent.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(Continent.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("continent"), true, (type, str) -> PWBindings.continent(str));
                    }
                    return emumSet(Continent.class, input);
                });
    }

    private Set<NationOrAlliance> nationOrAlliancesSingle(ValueStore store, String input) {
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
        if (input.equalsIgnoreCase("*")) {
            return new ObjectOpenHashSet<>(Locutus.imp().getNationDB().getNations().values());
        }
        if (MathMan.isInteger(input)) {
            long id = Long.parseLong(input);
            if (id < Integer.MAX_VALUE) {
                int idInt = (int) id;
                DBAlliance alliance = DBAlliance.get(idInt);
                if (alliance != null) return Set.of(alliance);
                DBNation nation = DBNation.getById(idInt);
                if (nation != null) return Set.of(nation);
            } else if (db != null){
                Role role = db.getGuild().getRoleById(id);
                return (Set) NationPlaceholders.getByRole(db.getGuild(), input, role);
            }
        }
        Integer aaId = PnwUtil.parseAllianceId(input);
        if (aaId != null) {
            return Set.of(DBAlliance.getOrCreate(aaId));
        }
        Integer nationId = DiscordUtil.parseNationId(input);
        if (nationId != null) {
            return Set.of(DBNation.getOrCreate(nationId));
        }

        if (input.charAt(0) == '~') input = input.substring(1);
        if (input.startsWith("coalition:")) input = input.substring("coalition:".length());
        if (input.startsWith("<@&") && db != null) {
            Role role = db.getGuild().getRoleById(input.substring(3, input.length() - 1));
            return (Set) NationPlaceholders.getByRole(db.getGuild(), input, role);
        }
        Set<Integer> coalition = db.getCoalition(input);
        if (!coalition.isEmpty()) {
            return coalition.stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
        }
        if (db != null) {
            // get role by name
            String finalInput = input;
            Role role = db.getGuild().getRoles().stream().filter(f -> f.getName().equalsIgnoreCase(finalInput)).findFirst().orElse(null);
            if (role != null) {
                return (Set) NationPlaceholders.getByRole(db.getGuild(), input, role);
            }
        }
        throw new IllegalArgumentException("Invalid nation or alliance: `" + input + "`");
    }

    private Placeholders<NationOrAlliance> createNationOrAlliances() {
        NationPlaceholders nationPlaceholders = (NationPlaceholders) get(DBNation.class);
        return new Placeholders<NationOrAlliance>(NationOrAlliance.class, store, validators, permisser) {
            @Override
            public String getCommandMention() {
                return "TODO";
            }

            @Override
            public Set<NationOrAlliance> parseSet(ValueStore store2, String input) {
                if (input.contains("#")) {
                    return (Set) nationPlaceholders.parseSet(store2, input);
                }
                return super.parseSet(store2, input);
            }

            @Override
            protected Set<NationOrAlliance> parseSingleElem(ValueStore store, String input) {
                if (SpreadSheet.isSheet(input)) {
                    return SpreadSheet.parseSheet(input, List.of("nation", "alliance"), true, (type, str) -> {
                        switch (type) {
                            case 0:
                                return PWBindings.nation(null, str);
                            case 1:
                                return PWBindings.alliance(str);
                        }
                        return null;
                    });
                }
                return nationOrAlliancesSingle(store, input);
            }

            @Override
            protected Predicate<NationOrAlliance> parseSingleFilter(ValueStore store, String input) {
                if (input.equalsIgnoreCase("*")) {
                    return f -> true;
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
        };
    }

    private Placeholders<GuildDB> createGuildDB() {
        return Placeholders.create(GuildDB.class, store, validators, permisser,
                "TODO CM Ref",
                (store, input) -> {
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
                        return SpreadSheet.parseSheet(input, List.of("guild"), true,
                                (type, str) -> PWBindings.guild(PrimitiveBindings.Long(str)));
                    }
                    long id = PrimitiveBindings.Long(input);
                    GuildDB guild = PWBindings.guild(id);
                    if (!admin && guild.getGuild().getMember(user) == null) {
                        throw new IllegalArgumentException("You (" + user + ") are not in the guild with id: `" + id + "`");
                    }
                    return Set.of(guild);
                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) {
                        return f -> true;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Long> sheet = SpreadSheet.parseSheet(input, List.of("guild"), true,
                                (type, str) -> PrimitiveBindings.Long(str));
                        return f -> sheet.contains(f.getIdLong());
                    }
                    long id = PrimitiveBindings.Long(input);
                    return f -> f.getIdLong() == id;
                });
    }

    private Placeholders<DBBan> createBans() {
        return Placeholders.create(DBBan.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) {
                        return new HashSet<>(Locutus.imp().getNationDB().getBansByNation().values());
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("bans"), true, (type, str) -> PWBindings.ban(str));
                    }
                    return Set.of(PWBindings.ban(input));
                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return f -> true;
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> sheet = SpreadSheet.parseSheet(input, List.of("treaty"), true,
                                (type, str) -> DiscordUtil.parseNationId(str));
                        return f -> sheet.contains(f.getNation_id());
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return f -> f.getNation_id() == id;
                    }
                    NationPlaceholders nationPlaceholders = (NationPlaceholders) get(DBNation.class);
                    Predicate<DBNation> filter = nationPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        DBNation nation = DBNation.getById(f.nation_id);
                        if (nation == null && f.discord_id > 0) {
                            nation = DiscordUtil.getNation(f.discord_id);
                        }
                        if (nation == null) return false;
                        return filter.test(nation);
                    };
                });
    }

//    private Placeholders<NationList> createCities() {
//        // integer = city id
//        // nation or alliance -> cities in that nation or alliance
//
//        // restrict to 10k results
//    }

    private Placeholders<NationList> createNationList() {
        return Placeholders.create(NationList.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
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
                        NationPlaceholders placeholders = (NationPlaceholders) get(DBNation.class);
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
                            lists.add(new SimpleNationList(Locutus.imp().getNationDB().getNations(db.getCoalition(coalition))).setFilter(filterName));
                        }
                    } else {
                        NationPlaceholders placeholders = (NationPlaceholders) get(DBNation.class);
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
                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return f -> true;
                    throw new IllegalArgumentException("NationList predicates other than `*` are unsupported. Please use DBNation instead");
                });
    }

    private Set<DBCity> parseCitiesSingle(ValueStore store, String input) {
        if (MathMan.isInteger(input) || input.contains("/city/id=")) {
            return Set.of(PWBindings.cityUrl(input));
        }
        NationPlaceholders nationPlaceholders = (NationPlaceholders) get(DBNation.class);
        Set<DBNation> nations = nationPlaceholders.parseSingleElem(store, input);
        Set<DBCity> cities = new LinkedHashSet<>();
        for (DBNation nation : nations) {
            cities.addAll(nation._getCitiesV3().values());
        }
        return cities;
    }

    private Placeholders<DBCity> createCities() {
        return Placeholders.create(DBCity.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) {
                        Locutus.imp().getNationDB().getCities();
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Set<DBCity>> result = SpreadSheet.parseSheet(input, List.of("cities"), true, (type, str) -> parseCitiesSingle(store, str));
                        Set<DBCity> cities = new LinkedHashSet<>();
                        for (Set<DBCity> set : result) {
                            cities.addAll(set);
                        }
                        return cities;
                    }
                    return parseCitiesSingle(store, input);
                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return f -> true;
                    if (MathMan.isInteger(input) || input.contains("/city/id=")) {
                        DBCity city = PWBindings.cityUrl(input);
                        return f -> f.id == city.id;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Set<DBCity>> result = SpreadSheet.parseSheet(input, List.of("cities"), true, (type, str) -> parseCitiesSingle(store, str));
                        Set<Integer> cityIds = new IntOpenHashSet();
                        for (Set<DBCity> set : result) {
                            for (DBCity city : set) {
                                cityIds.add(city.id);
                            }
                        }
                        return f -> cityIds.contains(f.id);
                    }
                    NationPlaceholders nationPlaceholders = (NationPlaceholders) get(DBNation.class);
                    Predicate<DBNation> filter = nationPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        DBNation nation = DBNation.getById(f.nation_id);
                        if (nation == null) return false;
                        return filter.test(nation);
                    };
                });
    }

    private Predicate<Transaction2> getAllowed(DBNation nation, User user, GuildDB db) {
        Predicate<Integer> allowAlliance;
        if (user != null && db != null) {
            Set<Integer> aaIds = db.getAllianceIds();
            boolean canSee = Roles.hasAny(user, db.getGuild(), Roles.ECON_STAFF, Roles.INTERNAL_AFFAIRS);
            if (canSee) {
                allowAlliance = aaIds::contains;
            } else {
                allowAlliance = f -> false;
            }
        } else {
            allowAlliance = f -> false;
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
        NationPlaceholders natFormat = (NationPlaceholders) get(DBNation.class);
        Set<DBNation> nations = natFormat.parseSingleElem(store, input);
        Set<TaxBracket> brackets = new ObjectOpenHashSet<>();
        Set<Integer> ids = new IntOpenHashSet();
        for (DBNation nation : nations) {
            if (nation.getPositionEnum().id <= Rank.APPLICANT.id || ids.contains(nation.getTax_id())) continue;
            ids.add(nation.getTax_id());
            brackets.add(nation.getTaxBracket());
        }
        return brackets;
    }


    private Predicate<BankDB.TaxDeposit> getCanView(ValueStore store) {
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
        User user = (User) store.getProvided(Key.of(User.class, Me.class), false);
        DBNation nation = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
        boolean hasEcon = user != null && db != null && Roles.ECON_STAFF.has(user, db.getGuild());
        return new Predicate<BankDB.TaxDeposit>() {
            @Override
            public boolean test(BankDB.TaxDeposit record) {
                if (nation != null && nation.getId() == record.nationId) return true;
                if (db == null) return false;
                if (!db.isAllianceId(record.allianceId)) return false;
                return hasEcon;
            }
        };
    }

    private Set<BankDB.TaxDeposit> getTaxes(ValueStore store, Set<Integer> ids, Set<Integer> taxIds, Set<Integer> nations) {
        BankDB bankDb = Locutus.imp().getBankDB();
        Predicate<BankDB.TaxDeposit> canView = getCanView(store);
        Set<BankDB.TaxDeposit> result = new ObjectLinkedOpenHashSet<>();

        if (ids != null && !ids.isEmpty()) {
            bankDb.getTaxesByIds(ids).stream().filter(canView).forEach(result::add);
        }
        if (taxIds != null && !taxIds.isEmpty()) {
            bankDb.getTaxesByBrackets(taxIds).stream().filter(canView).forEach(result::add);
        }
        if (nations != null && !nations.isEmpty()) {
            bankDb.getTaxesByNations(nations).stream().filter(canView).forEach(result::add);
        }
        return result;
    }

    public Placeholders<BankDB.TaxDeposit> createTaxDeposit() {
        return Placeholders.create(BankDB.TaxDeposit.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    Predicate<BankDB.TaxDeposit> canView = getCanView(store);
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = new IntOpenHashSet();
                        Set<Integer> taxIds = new IntOpenHashSet();
                        Set<Integer> nations = new IntOpenHashSet();
                        SpreadSheet.parseSheet(input, List.of("id", "tax_id", "nation"), true, (type, str) -> {
                            switch (type) {
                                case 0 -> ids.add(Integer.parseInt(str));
                                case 1 -> taxIds.add(Integer.parseInt(str));
                                case 2 -> nations.add(DiscordUtil.parseNationId(str));
                            }
                            return null;
                        });
                        return getTaxes(store, ids, taxIds, nations);
                    }
                    if (MathMan.isInteger(input)) {
                        return getTaxes(store, Set.of(Integer.parseInt(input)), null, null);
                    }
                    if (input.contains("tax_id=")) {
                        int id = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                        return getTaxes(store, null, Set.of(id), null);
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    User author = (User) store.getProvided(Key.of(User.class, Me.class), false);
                    DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class), false);
                    Set<DBNation> nations = PWBindings.nations(null, guild, input, author, me);
                    Set<Integer> ids = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
                    return getTaxes(store, null, null, ids);

                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return f -> true;
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = new IntOpenHashSet();
                        Set<Integer> taxIds = new IntOpenHashSet();
                        Set<Integer> nations = new IntOpenHashSet();
                        SpreadSheet.parseSheet(input, List.of("id", "tax_id", "nation"), true, (type, str) -> {
                            switch (type) {
                                case 0 -> ids.add(Integer.parseInt(str));
                                case 1 -> taxIds.add(Integer.parseInt(str));
                                case 2 -> nations.add(DiscordUtil.parseNationId(str));
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
                    NationPlaceholders nationPlaceholders = (NationPlaceholders) get(DBNation.class);
                    Predicate<DBNation> nationFilter = nationPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        DBNation nation = DBNation.getOrCreate(f.nationId);
                        return nationFilter.test(nation);
                    };
                });
    }

    private Set<IAttack> getAttacks(Set<Integer> attackIds, Set<Integer> warIds) {
        Set<IAttack> attacks = new ObjectOpenHashSet<>();
        if (warIds != null && !warIds.isEmpty()) {
            Set<DBWar> wars = Locutus.imp().getWarDb().getWarsById(warIds);
            if (!wars.isEmpty()) {
                attacks.addAll(Locutus.imp().getWarDb().getAttacksByWars(wars));
            }
        }
        if (attackIds != null && !attackIds.isEmpty()) {
            attacks.addAll(Locutus.imp().getWarDb().getAttacksById(attackIds));
        }
        return attacks;
    }

    public Placeholders<IAttack> createAttacks() {
        return Placeholders.create(IAttack.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> attackIds = new ObjectOpenHashSet<>();
                        Set<Integer> warIds = new ObjectOpenHashSet<>();
                        SpreadSheet.parseSheet(input, List.of("id", "war_id"), true, (type, str) -> {
                            switch (type) {
                                case 0 -> attackIds.add(Integer.parseInt(str));
                                case 1 -> warIds.add(Integer.parseInt(str));
                            }
                            return null;
                        });
                        return getAttacks(attackIds, warIds);
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        Set<IAttack> attacks = Locutus.imp().getWarDb().getAttacksById(Set.of(id));
                        return attacks;
                    }
                    if (input.contains("/war/id=")) {
                        int warId = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                        return getAttacks(Set.of(), Set.of(warId));
                    }
                    throw new UnsupportedOperationException("Filters must begin with `#`. Please use the attack selector argument to specify participants.");
                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return f -> true;
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> attackIds = new ObjectOpenHashSet<>();
                        Set<Integer> warIds = new ObjectOpenHashSet<>();
                        SpreadSheet.parseSheet(input, List.of("id", "war_id"), true, (type, str) -> {
                            switch (type) {
                                case 0 -> attackIds.add(Integer.parseInt(str));
                                case 1 -> warIds.add(Integer.parseInt(str));
                            }
                            return null;
                        });
                        if (!attackIds.isEmpty() || !warIds.isEmpty()) {
                            return f -> {
                                if (attackIds.contains(f.getWar_attack_id())) return true;
                                return warIds.contains(f.getWar_id());
                            };
                        }
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
                }
        );
    }

    public Placeholders<TaxBracket> createBrackets() {
        return Placeholders.create(TaxBracket.class, store, validators, permisser,
                "TODO CM REF",
                (store2, input) -> {
                    GuildDB db = (GuildDB) store2.getProvided(Key.of(GuildDB.class, Me.class), false);
                    if (input.equalsIgnoreCase("*")) {
                        if (db == null) {
                            AllianceList aaList = db.getAllianceList();
                            if (aaList != null) {
                                return new HashSet<TaxBracket>(aaList.getTaxBrackets(true).values());
                            }
                        }
                        Map<Integer, Integer> ids = Locutus.imp().getNationDB().getAllianceIdByTaxId();
                        return ids.entrySet().stream().map(f -> new TaxBracket(f.getKey(), f.getValue(), "", -1, -1, 0)).collect(Collectors.toSet());
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Set<TaxBracket>> result = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> bracketSingle(store2, db, input));
                        Set<TaxBracket> brackets = new ObjectOpenHashSet<>();
                        for (Set<TaxBracket> set : result) {
                            brackets.addAll(set);
                        }
                        return brackets;
                    }
                    return bracketSingle(store2, db, input);
                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) {
                        return f -> true;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> Integer.parseInt(str));
                        return f -> ids.contains(f.getId());
                    }
                    if (input.contains("tx_id=") || MathMan.isInteger(input)) {
                        int id = PnwUtil.parseTaxId(input);
                        return f -> f.getId() == id;
                    }
                    AlliancePlaceholders aaPlaceholders = (AlliancePlaceholders) get(DBAlliance.class);
                    Predicate<DBAlliance> filter = aaPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        if (f.getId() == 0) return false;
                        DBAlliance aa = f.getAlliance();
                        if (aa == null) return false;
                        return filter.test(aa);
                    };
                }
        );
    }

    private Placeholders<DBTrade> createTrades() {
        return Placeholders.create(DBTrade.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
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
                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) {
                        return f -> true;
                    }
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> ids = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> Integer.parseInt(str));
                        return f -> ids.contains(f.getTradeId());
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return f -> f.getTradeId() == id;
                    }
                    NationPlaceholders nationPlaceholders = (NationPlaceholders) get(DBNation.class);
                    Predicate<DBNation> filter = nationPlaceholders.parseSingleFilter(store, input);
                    return f -> {
                        DBNation sender = DBNation.getById(f.getSeller());
                        DBNation receiver = DBNation.getById(f.getBuyer());
                        if (sender != null && filter.test(sender)) return true;
                        if (receiver != null && filter.test(receiver)) return true;
                        return false;
                    };
                }
        );
    }

    private Placeholders<Transaction2> createTransactions() {
        return Placeholders.create(Transaction2.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
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
                }, (store, input) -> {
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
                });
    }

    private Placeholders<DBBounty> createBounties() {
        return Placeholders.create(DBBounty.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
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
                    Set<DBBounty> bountySet = new LinkedHashSet<>();
                    for (DBNation nation : nations) {
                        List<DBBounty> list = bounties.get(nation.getId());
                        if (list != null) {
                            bountySet.addAll(list);
                        }
                    }
                    return bountySet;
                }, (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return f -> true;
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> sheet = SpreadSheet.parseSheet(input, List.of("bounty"), true,
                                (type, str) -> PrimitiveBindings.Integer(str));
                        return f -> sheet.contains(f.getId());
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return f -> f.getId() == id;
                    }
                    NationPlaceholders natPlac = (NationPlaceholders) get(DBNation.class);
                    Predicate<DBNation> filter = natPlac.parseSingleFilter(store, input);
                    return f -> {
                        DBNation nation = DBNation.getById(f.getNationId());
                        if (nation == null) return false;
                        return filter.test(nation);
                    };
                });
    }

    private Placeholders<Treaty> createTreaty() {
        return Placeholders.create(Treaty.class, store, validators, permisser,
        "TODO CM REF",
        (store, input) -> {
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
            if (aa1 == null) throw new IllegalArgumentException("Invalid alliance or coalition: `" + split.get(0) + "`");
            if (aa2 == null) throw new IllegalArgumentException("Invalid alliance or coalition: `" + split.get(1) + "`");
            return Locutus.imp().getNationDB().getTreatiesMatching(f -> {
                return (aa1.contains(f.getFromId())) && (aa2.contains(f.getToId())) || (aa1.contains(f.getToId())) && (aa2.contains(f.getFromId()));
            });
        }, (store, input) -> {
            if (input.equalsIgnoreCase("*")) return f -> true;
            if (SpreadSheet.isSheet(input)) {
                Set<Treaty> sheet = SpreadSheet.parseSheet(input, List.of("treaty"), true,
                        (type, str) -> PWBindings.treaty(str));

                Map<Integer, Set<Integer>> treatyIds = new HashMap<>();
                for (Treaty treaty : sheet) {
                    treatyIds.computeIfAbsent(treaty.getFromId(), k -> new HashSet<>()).add(treaty.getToId());
                    treatyIds.computeIfAbsent(treaty.getToId(), k -> new HashSet<>()).add(treaty.getFromId());
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
                    return db.getCoalitionById(coalitionId1).contains(f);
                }
            };
            Predicate<Integer> contains2 = f -> {
                if (aa2 != null) {
                    return aa2.contains(f);
                } else {
                    return db.getCoalitionById(coalitionId2).contains(f);
                }
            };
            return f -> (contains1.test(f.getFromId()) && contains2.test(f.getToId()))
                    || (contains1.test(f.getToId()) && contains2.test(f.getFromId()));
        });
    }

    private Placeholders<Project> createProjects() {
        return Placeholders.createStatic(Project.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
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
                });
    }

    private Placeholders<ResourceType> createResourceType() {
        return Placeholders.createStatic(ResourceType.class, store, validators, permisser,
        "TODO CM REF",
        (store, input) -> {
            if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(ResourceType.values));
            if (SpreadSheet.isSheet(input)) {
                return SpreadSheet.parseSheet(input, List.of("resource"), true, (type, str) -> PWBindings.resource(str));
            }
            return Set.of(PWBindings.resource(input));
        });
    }

    private Placeholders<AttackType> createAttackTypes() {
        return Placeholders.createStatic(AttackType.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(AttackType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("attack_type"), true, (type, str) -> PWBindings.attackType(str));
                    }
                    return Set.of(PWBindings.attackType(input));
                });
    }

    private Placeholders<MilitaryUnit> createMilitaryUnit() {
        return Placeholders.createStatic(MilitaryUnit.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(MilitaryUnit.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("unit"), true, (type, str) -> PWBindings.unit(str));
                    }
                    return Set.of(PWBindings.unit(input));
                });
    }

    private Placeholders<TreatyType> createTreatyType() {
        return Placeholders.createStatic(TreatyType.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(TreatyType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("treaty_type"), true, (type, str) -> PWBindings.TreatyType(str));
                    }
                    return Set.of(PWBindings.TreatyType(input));
                });
    }

    private Placeholders<IACheckup.AuditType> createAuditType() {
        return Placeholders.createStatic(IACheckup.AuditType.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(IACheckup.AuditType.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("audit"), true, (type, str) -> PWBindings.auditType(str));
                    }
                    return Set.of(PWBindings.auditType(input));
                });
    }

    private Placeholders<NationColor> createNationColor() {
        return Placeholders.createStatic(NationColor.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(NationColor.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("color"), true, (type, str) -> PWBindings.NationColor(str));
                    }
                    return Set.of(PWBindings.NationColor(input));
                });
    }

    private Placeholders<Building> createBuilding() {
        return Placeholders.createStatic(Building.class, store, validators, permisser,
                "TODO CM REF",
                (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(Buildings.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("attack_type"), true, (type, str) -> PWBindings.getBuilding(str));
                    }
                    return Set.of(PWBindings.getBuilding(input));
                });
    }



}
