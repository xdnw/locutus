package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
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
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.SimplePlaceholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.StaticPlaceholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
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
import link.locutus.discord.db.entities.UserWrapper;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.ThrowingBiFunction;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.binding.BindingHelper.emumSet;

public class PlaceholdersMap {
    private final Map<Class<?>, Placeholders<?>> placeholders = new ConcurrentHashMap<>();
    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public Set<Class<?>> getTypes() {
        return placeholders.keySet();
    }

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
        this.placeholders.put(TaxBracket.class, createBrackets());

        this.placeholders.put(UserWrapper.class, createUsers());

        // special
        // input = getSelection(store, Transaction2.class, input);
        // deserializeSelection
        this.placeholders.put(Transaction2.class, createTransactions());
        this.placeholders.put(DBTrade.class, createTrades());
        this.placeholders.put(IAttack.class, createAttacks());
        this.placeholders.put(DBWar.class, createWars());
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

    private <T> String getSelection(ValueStore store, Class<T> type, String input) {
        if (input.startsWith("!")) {
            GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), false);
            if (db != null) {
                SelectionAlias<T> selection = db.getSheetManager().getSelectionAlias(input.substring(1), type);
                if (selection != null) {
                    return selection.getSelection();
                }
            }
        }
        return input;
    }

    private Placeholders<Continent> createContinents() {
        return new StaticPlaceholders<Continent>(Continent.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<Continent>>) (store, input) -> {
                    input = getSelection(store, Continent.class, input);
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(Continent.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("continent"), true, (type, str) -> PWBindings.continent(str));
                    }
                    return emumSet(Continent.class, input);
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of continents")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Continent> continents) {
                return _addSelectionAlias(command, db, name, continents, "continents");
            }

            @NoFormat
            @Command(desc = "Add columns to a Continent sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                      @Default TypedFunction<Continent, String> column1,
                                      @Default TypedFunction<Continent, String> column2,
                                      @Default TypedFunction<Continent, String> column3,
                                      @Default TypedFunction<Continent, String> column4,
                                      @Default TypedFunction<Continent, String> column5,
                                      @Default TypedFunction<Continent, String> column6,
                                      @Default TypedFunction<Continent, String> column7,
                                      @Default TypedFunction<Continent, String> column8,
                                      @Default TypedFunction<Continent, String> column9,
                                      @Default TypedFunction<Continent, String> column10,
                                      @Default TypedFunction<Continent, String> column11,
                                      @Default TypedFunction<Continent, String> column12,
                                      @Default TypedFunction<Continent, String> column13,
                                      @Default TypedFunction<Continent, String> column14,
                                      @Default TypedFunction<Continent, String> column15,
                                      @Default TypedFunction<Continent, String> column16,
                                      @Default TypedFunction<Continent, String> column17,
                                      @Default TypedFunction<Continent, String> column18,
                                      @Default TypedFunction<Continent, String> column19,
                                      @Default TypedFunction<Continent, String> column20,
                                      @Default TypedFunction<Continent, String> column21,
                                      @Default TypedFunction<Continent, String> column22,
                                      @Default TypedFunction<Continent, String> column23,
                                      @Default TypedFunction<Continent, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                                column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                                column21, column22, column23, column24);
            }
        };
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
            public String getDescription() {
                return "TODO";
            }

            @Override
            public Set<NationOrAlliance> parseSet(ValueStore store2, String input) {
                input = getSelection(store, NationOrAlliance.class, input);
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
            @NoFormat
            @Command(desc = "Add an alias for a selection of NationOrAlliances")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<NationOrAlliance> nationoralliances) {
                return _addSelectionAlias(command, db, name, nationoralliances, "nationoralliances");
            }

            @NoFormat
            @Command(desc = "Add columns to a NationOrAlliance sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<NationOrAlliance, String> column1,
                                     @Default TypedFunction<NationOrAlliance, String> column2,
                                     @Default TypedFunction<NationOrAlliance, String> column3,
                                     @Default TypedFunction<NationOrAlliance, String> column4,
                                     @Default TypedFunction<NationOrAlliance, String> column5,
                                     @Default TypedFunction<NationOrAlliance, String> column6,
                                     @Default TypedFunction<NationOrAlliance, String> column7,
                                     @Default TypedFunction<NationOrAlliance, String> column8,
                                     @Default TypedFunction<NationOrAlliance, String> column9,
                                     @Default TypedFunction<NationOrAlliance, String> column10,
                                     @Default TypedFunction<NationOrAlliance, String> column11,
                                     @Default TypedFunction<NationOrAlliance, String> column12,
                                     @Default TypedFunction<NationOrAlliance, String> column13,
                                     @Default TypedFunction<NationOrAlliance, String> column14,
                                     @Default TypedFunction<NationOrAlliance, String> column15,
                                     @Default TypedFunction<NationOrAlliance, String> column16,
                                     @Default TypedFunction<NationOrAlliance, String> column17,
                                     @Default TypedFunction<NationOrAlliance, String> column18,
                                     @Default TypedFunction<NationOrAlliance, String> column19,
                                     @Default TypedFunction<NationOrAlliance, String> column20,
                                     @Default TypedFunction<NationOrAlliance, String> column21,
                                     @Default TypedFunction<NationOrAlliance, String> column22,
                                     @Default TypedFunction<NationOrAlliance, String> column23,
                                     @Default TypedFunction<NationOrAlliance, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }

        };
    }

    private Placeholders<GuildDB> createGuildDB() {
        return new SimplePlaceholders<GuildDB>(GuildDB.class,  store, validators, permisser,
                "TODO CM Ref",
                (ThrowingBiFunction<ValueStore, String, Set<GuildDB>>) (store, input) -> {
                    input = getSelection(store, GuildDB.class, input);
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
                }, (ThrowingBiFunction<ValueStore, String, Predicate<GuildDB>>) (store, input) -> {
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
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of guilds")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<GuildDB> guilds) {
                return _addSelectionAlias(command, db, name, guilds, "guilds");
            }

            @NoFormat
            @Command(desc = "Add columns to a Guild sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<GuildDB, String> column1,
                                     @Default TypedFunction<GuildDB, String> column2,
                                     @Default TypedFunction<GuildDB, String> column3,
                                     @Default TypedFunction<GuildDB, String> column4,
                                     @Default TypedFunction<GuildDB, String> column5,
                                     @Default TypedFunction<GuildDB, String> column6,
                                     @Default TypedFunction<GuildDB, String> column7,
                                     @Default TypedFunction<GuildDB, String> column8,
                                     @Default TypedFunction<GuildDB, String> column9,
                                     @Default TypedFunction<GuildDB, String> column10,
                                     @Default TypedFunction<GuildDB, String> column11,
                                     @Default TypedFunction<GuildDB, String> column12,
                                     @Default TypedFunction<GuildDB, String> column13,
                                     @Default TypedFunction<GuildDB, String> column14,
                                     @Default TypedFunction<GuildDB, String> column15,
                                     @Default TypedFunction<GuildDB, String> column16,
                                     @Default TypedFunction<GuildDB, String> column17,
                                     @Default TypedFunction<GuildDB, String> column18,
                                     @Default TypedFunction<GuildDB, String> column19,
                                     @Default TypedFunction<GuildDB, String> column20,
                                     @Default TypedFunction<GuildDB, String> column21,
                                     @Default TypedFunction<GuildDB, String> column22,
                                     @Default TypedFunction<GuildDB, String> column23,
                                     @Default TypedFunction<GuildDB, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<DBBan> createBans() {
        return new SimplePlaceholders<DBBan>(DBBan.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<DBBan>>) (store, input) -> {
                    input = getSelection(store, DBBan.class, input);
                    if (input.equalsIgnoreCase("*")) {
                        return new HashSet<>(Locutus.imp().getNationDB().getBansByNation().values());
                    }
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("bans"), true, (type, str) -> PWBindings.ban(str));
                    }
                    return Set.of(PWBindings.ban(input));
                }, (ThrowingBiFunction<ValueStore, String, Predicate<DBBan>>) (store, input) -> {
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
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of bans")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBBan> bans) {
                return _addSelectionAlias(command, db, name, bans, "bans");
            }

            @NoFormat
            @Command(desc = "Add columns to a Ban sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBBan, String> column1,
                                     @Default TypedFunction<DBBan, String> column2,
                                     @Default TypedFunction<DBBan, String> column3,
                                     @Default TypedFunction<DBBan, String> column4,
                                     @Default TypedFunction<DBBan, String> column5,
                                     @Default TypedFunction<DBBan, String> column6,
                                     @Default TypedFunction<DBBan, String> column7,
                                     @Default TypedFunction<DBBan, String> column8,
                                     @Default TypedFunction<DBBan, String> column9,
                                     @Default TypedFunction<DBBan, String> column10,
                                     @Default TypedFunction<DBBan, String> column11,
                                     @Default TypedFunction<DBBan, String> column12,
                                     @Default TypedFunction<DBBan, String> column13,
                                     @Default TypedFunction<DBBan, String> column14,
                                     @Default TypedFunction<DBBan, String> column15,
                                     @Default TypedFunction<DBBan, String> column16,
                                     @Default TypedFunction<DBBan, String> column17,
                                     @Default TypedFunction<DBBan, String> column18,
                                     @Default TypedFunction<DBBan, String> column19,
                                     @Default TypedFunction<DBBan, String> column20,
                                     @Default TypedFunction<DBBan, String> column21,
                                     @Default TypedFunction<DBBan, String> column22,
                                     @Default TypedFunction<DBBan, String> column23,
                                     @Default TypedFunction<DBBan, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<NationList> createNationList() {
        return new SimplePlaceholders<NationList>(NationList.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<NationList>>) (store, input) -> {
                    input = getSelection(store, NationList.class, input);
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
                }, (ThrowingBiFunction<ValueStore, String, Predicate<NationList>>) (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return f -> true;
                    throw new IllegalArgumentException("NationList predicates other than `*` are unsupported. Please use DBNation instead");
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of nationlists")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<NationList> nationlists) {
                return _addSelectionAlias(command, db, name, nationlists, "nationlists");
            }

            @NoFormat
            @Command(desc = "Add columns to a NationList sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<NationList, String> column1,
                                     @Default TypedFunction<NationList, String> column2,
                                     @Default TypedFunction<NationList, String> column3,
                                     @Default TypedFunction<NationList, String> column4,
                                     @Default TypedFunction<NationList, String> column5,
                                     @Default TypedFunction<NationList, String> column6,
                                     @Default TypedFunction<NationList, String> column7,
                                     @Default TypedFunction<NationList, String> column8,
                                     @Default TypedFunction<NationList, String> column9,
                                     @Default TypedFunction<NationList, String> column10,
                                     @Default TypedFunction<NationList, String> column11,
                                     @Default TypedFunction<NationList, String> column12,
                                     @Default TypedFunction<NationList, String> column13,
                                     @Default TypedFunction<NationList, String> column14,
                                     @Default TypedFunction<NationList, String> column15,
                                     @Default TypedFunction<NationList, String> column16,
                                     @Default TypedFunction<NationList, String> column17,
                                     @Default TypedFunction<NationList, String> column18,
                                     @Default TypedFunction<NationList, String> column19,
                                     @Default TypedFunction<NationList, String> column20,
                                     @Default TypedFunction<NationList, String> column21,
                                     @Default TypedFunction<NationList, String> column22,
                                     @Default TypedFunction<NationList, String> column23,
                                     @Default TypedFunction<NationList, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
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
        User user = DiscordUtil.getUser(input);
        if (user != null) {
            return new LinkedHashSet<>(List.of(new UserWrapper(guild, user)));
        }
        // Role
        Role role = DiscordUtil.getRole(guild, input);
        if (role != null) {
            return guild.getMembersWithRoles(role).stream().map(UserWrapper::new).collect(Collectors.toSet());
        }
        NationOrAlliance natOrAA = PWBindings.nationOrAlliance(input);
        if (natOrAA.isNation()) {
            user = natOrAA.asNation().getUser();
            if (user == null) {
                throw new IllegalArgumentException("Nation " + natOrAA.getMarkdownUrl() + " is not registered. See: " + CM.register.cmd.toSlashMention());
            }
            return new LinkedHashSet<>(List.of(new UserWrapper(guild, user)));
        }
        return natOrAA.asAlliance().getNations().stream().map(f -> {
            Long id = f.getUserId();
            return id != null ? guild.getMemberById(id) : null;
        }).filter(Objects::nonNull).map(UserWrapper::new).collect(Collectors.toSet());
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
                            for (Role role : member.getRoles()) {
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
        Integer nationId = DiscordUtil.parseNationId(input);
        if (nationId != null) {
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
                for (Role role : member.getRoles()) {
                    if (role.getName().equalsIgnoreCase(finalInput)) return true;
                }
            }
            return false;
        };
    }

    private Placeholders<UserWrapper> createUsers() {
        return new SimplePlaceholders<UserWrapper>(UserWrapper.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<UserWrapper>>) (store, input) -> {
                    input = getSelection(store, UserWrapper.class, input);
                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                    Guild guild = db.getGuild();
                    if (SpreadSheet.isSheet(input)) {
                        Set<Member> member = SpreadSheet.parseSheet(input, List.of("user"), true, (type, str) -> DiscordBindings.member(guild, null, str));
                        return member.stream().map(UserWrapper::new).collect(Collectors.toSet());
                    }
                    return parseUserSingle(guild, input);
                }, (ThrowingBiFunction<ValueStore, String, Predicate<UserWrapper>>) (store, input) -> {
                    if (input.equalsIgnoreCase("*")) return f -> true;

                    GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class), true);
                    Guild guild = db.getGuild();

                    if (SpreadSheet.isSheet(input)) {
                        Set<Long> sheet = SpreadSheet.parseSheet(input, List.of("user"), true,
                                (type, str) -> DiscordUtil.parseUserId(guild, str));
                        return f -> sheet.contains(f.getUserId());
                    }
                    return parseUserPredicate(guild, input);
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of users")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<UserWrapper> users) {
                return _addSelectionAlias(command, db, name, users, "users");
            }

            @NoFormat
            @Command(desc = "Add columns to a User sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<UserWrapper, String> column1,
                                     @Default TypedFunction<UserWrapper, String> column2,
                                     @Default TypedFunction<UserWrapper, String> column3,
                                     @Default TypedFunction<UserWrapper, String> column4,
                                     @Default TypedFunction<UserWrapper, String> column5,
                                     @Default TypedFunction<UserWrapper, String> column6,
                                     @Default TypedFunction<UserWrapper, String> column7,
                                     @Default TypedFunction<UserWrapper, String> column8,
                                     @Default TypedFunction<UserWrapper, String> column9,
                                     @Default TypedFunction<UserWrapper, String> column10,
                                     @Default TypedFunction<UserWrapper, String> column11,
                                     @Default TypedFunction<UserWrapper, String> column12,
                                     @Default TypedFunction<UserWrapper, String> column13,
                                     @Default TypedFunction<UserWrapper, String> column14,
                                     @Default TypedFunction<UserWrapper, String> column15,
                                     @Default TypedFunction<UserWrapper, String> column16,
                                     @Default TypedFunction<UserWrapper, String> column17,
                                     @Default TypedFunction<UserWrapper, String> column18,
                                     @Default TypedFunction<UserWrapper, String> column19,
                                     @Default TypedFunction<UserWrapper, String> column20,
                                     @Default TypedFunction<UserWrapper, String> column21,
                                     @Default TypedFunction<UserWrapper, String> column22,
                                     @Default TypedFunction<UserWrapper, String> column23,
                                     @Default TypedFunction<UserWrapper, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };

    }

    private Placeholders<DBCity> createCities() {
        return new SimplePlaceholders<DBCity>(DBCity.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<DBCity>>) (store, input) -> {
                    input = getSelection(store, DBCity.class, input);
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
                }, (ThrowingBiFunction<ValueStore, String, Predicate<DBCity>>) (store, input) -> {
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
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of cities")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBCity> cities) {
                return _addSelectionAlias(command, db, name, cities, "cities");
            }

            @NoFormat
            @Command(desc = "Add columns to a City sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBCity, String> column1,
                                     @Default TypedFunction<DBCity, String> column2,
                                     @Default TypedFunction<DBCity, String> column3,
                                     @Default TypedFunction<DBCity, String> column4,
                                     @Default TypedFunction<DBCity, String> column5,
                                     @Default TypedFunction<DBCity, String> column6,
                                     @Default TypedFunction<DBCity, String> column7,
                                     @Default TypedFunction<DBCity, String> column8,
                                     @Default TypedFunction<DBCity, String> column9,
                                     @Default TypedFunction<DBCity, String> column10,
                                     @Default TypedFunction<DBCity, String> column11,
                                     @Default TypedFunction<DBCity, String> column12,
                                     @Default TypedFunction<DBCity, String> column13,
                                     @Default TypedFunction<DBCity, String> column14,
                                     @Default TypedFunction<DBCity, String> column15,
                                     @Default TypedFunction<DBCity, String> column16,
                                     @Default TypedFunction<DBCity, String> column17,
                                     @Default TypedFunction<DBCity, String> column18,
                                     @Default TypedFunction<DBCity, String> column19,
                                     @Default TypedFunction<DBCity, String> column20,
                                     @Default TypedFunction<DBCity, String> column21,
                                     @Default TypedFunction<DBCity, String> column22,
                                     @Default TypedFunction<DBCity, String> column23,
                                     @Default TypedFunction<DBCity, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
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
        return new SimplePlaceholders<BankDB.TaxDeposit>(BankDB.TaxDeposit.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<BankDB.TaxDeposit>>) (store, input) -> {
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

                }, (ThrowingBiFunction<ValueStore, String, Predicate<BankDB.TaxDeposit>>) (store, input) -> {
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
                }) {
            @NoFormat
            @Command(desc = "Add columns to a Bank TaxDeposit sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column1,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column2,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column3,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column4,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column5,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column6,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column7,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column8,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column9,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column10,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column11,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column12,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column13,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column14,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column15,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column16,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column17,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column18,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column19,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column20,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column21,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column22,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column23,
                                     @Default TypedFunction<BankDB.TaxDeposit, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
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
        return new SimplePlaceholders<IAttack>(IAttack.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<IAttack>>) (store, input) -> {
                    input = getSelection(store, IAttack.class, input);
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
                }, (ThrowingBiFunction<ValueStore, String, Predicate<IAttack>>) (store, input) -> {
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
        ) {
            @NoFormat
            @Command(desc = "Add columns to an Attack sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<IAttack, String> column1,
                                     @Default TypedFunction<IAttack, String> column2,
                                     @Default TypedFunction<IAttack, String> column3,
                                     @Default TypedFunction<IAttack, String> column4,
                                     @Default TypedFunction<IAttack, String> column5,
                                     @Default TypedFunction<IAttack, String> column6,
                                     @Default TypedFunction<IAttack, String> column7,
                                     @Default TypedFunction<IAttack, String> column8,
                                     @Default TypedFunction<IAttack, String> column9,
                                     @Default TypedFunction<IAttack, String> column10,
                                     @Default TypedFunction<IAttack, String> column11,
                                     @Default TypedFunction<IAttack, String> column12,
                                     @Default TypedFunction<IAttack, String> column13,
                                     @Default TypedFunction<IAttack, String> column14,
                                     @Default TypedFunction<IAttack, String> column15,
                                     @Default TypedFunction<IAttack, String> column16,
                                     @Default TypedFunction<IAttack, String> column17,
                                     @Default TypedFunction<IAttack, String> column18,
                                     @Default TypedFunction<IAttack, String> column19,
                                     @Default TypedFunction<IAttack, String> column20,
                                     @Default TypedFunction<IAttack, String> column21,
                                     @Default TypedFunction<IAttack, String> column22,
                                     @Default TypedFunction<IAttack, String> column23,
                                     @Default TypedFunction<IAttack, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    public Placeholders<DBWar> createWars() {
        return new SimplePlaceholders<DBWar>(DBWar.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<DBWar>>) (store, input) -> {
                    input = getSelection(store, DBWar.class, input);
                    if (SpreadSheet.isSheet(input)) {
                        Set<Integer> warIds = new ObjectOpenHashSet<>();
                        SpreadSheet.parseSheet(input, List.of("id", "war_id"), true, (type, str) -> {
                            switch (type) {
                                case 0,1 -> warIds.add(Integer.parseInt(str));
                            }
                            return null;
                        });
                        return Locutus.imp().getWarDb().getWarsById(warIds);
                    }
                    if (MathMan.isInteger(input)) {
                        int id = Integer.parseInt(input);
                        return Locutus.imp().getWarDb().getWarsById(Set.of(id));
                    }
                    if (input.contains("/war/id=")) {
                        int warId = Integer.parseInt(input.substring(input.indexOf('=') + 1));
                        return Locutus.imp().getWarDb().getWarsById(Set.of(warId));
                    }
                    throw new UnsupportedOperationException("Filters must begin with `#`. Please use the attack selector argument to specify participants.");
                }, (ThrowingBiFunction<ValueStore, String, Predicate<DBWar>>) (store, input) -> {
            if (input.equalsIgnoreCase("*")) return f -> true;
            if (SpreadSheet.isSheet(input)) {
                Set<Integer> warIds = new ObjectOpenHashSet<>();
                SpreadSheet.parseSheet(input, List.of("id", "war_id"), true, (type, str) -> {
                    switch (type) {
                        case 0,1 -> warIds.add(Integer.parseInt(str));
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
        }
        ) {
            @NoFormat
            @Command(desc = "Add columns to a War sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBWar, String> column1,
                                     @Default TypedFunction<DBWar, String> column2,
                                     @Default TypedFunction<DBWar, String> column3,
                                     @Default TypedFunction<DBWar, String> column4,
                                     @Default TypedFunction<DBWar, String> column5,
                                     @Default TypedFunction<DBWar, String> column6,
                                     @Default TypedFunction<DBWar, String> column7,
                                     @Default TypedFunction<DBWar, String> column8,
                                     @Default TypedFunction<DBWar, String> column9,
                                     @Default TypedFunction<DBWar, String> column10,
                                     @Default TypedFunction<DBWar, String> column11,
                                     @Default TypedFunction<DBWar, String> column12,
                                     @Default TypedFunction<DBWar, String> column13,
                                     @Default TypedFunction<DBWar, String> column14,
                                     @Default TypedFunction<DBWar, String> column15,
                                     @Default TypedFunction<DBWar, String> column16,
                                     @Default TypedFunction<DBWar, String> column17,
                                     @Default TypedFunction<DBWar, String> column18,
                                     @Default TypedFunction<DBWar, String> column19,
                                     @Default TypedFunction<DBWar, String> column20,
                                     @Default TypedFunction<DBWar, String> column21,
                                     @Default TypedFunction<DBWar, String> column22,
                                     @Default TypedFunction<DBWar, String> column23,
                                     @Default TypedFunction<DBWar, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    public Placeholders<TaxBracket> createBrackets() {
        return new SimplePlaceholders<TaxBracket>(TaxBracket.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<TaxBracket>>) (store2, input) -> {
                    input = getSelection(store, TaxBracket.class, input);
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
                        String finalInput = input;
                        Set<Set<TaxBracket>> result = SpreadSheet.parseSheet(input, List.of("id"), true, (type, str) -> bracketSingle(store2, db, finalInput));
                        Set<TaxBracket> brackets = new ObjectOpenHashSet<>();
                        for (Set<TaxBracket> set : result) {
                            brackets.addAll(set);
                        }
                        return brackets;
                    }
                    return bracketSingle(store2, db, input);
                }, (ThrowingBiFunction<ValueStore, String, Predicate<TaxBracket>>) (store, input) -> {
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
        ) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of tax brackets")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<TaxBracket> taxbrackets) {
                return _addSelectionAlias(command, db, name, taxbrackets, "taxbrackets");
            }

            @NoFormat
            @Command(desc = "Add columns to a TaxBracket sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<TaxBracket, String> column1,
                                     @Default TypedFunction<TaxBracket, String> column2,
                                     @Default TypedFunction<TaxBracket, String> column3,
                                     @Default TypedFunction<TaxBracket, String> column4,
                                     @Default TypedFunction<TaxBracket, String> column5,
                                     @Default TypedFunction<TaxBracket, String> column6,
                                     @Default TypedFunction<TaxBracket, String> column7,
                                     @Default TypedFunction<TaxBracket, String> column8,
                                     @Default TypedFunction<TaxBracket, String> column9,
                                     @Default TypedFunction<TaxBracket, String> column10,
                                     @Default TypedFunction<TaxBracket, String> column11,
                                     @Default TypedFunction<TaxBracket, String> column12,
                                     @Default TypedFunction<TaxBracket, String> column13,
                                     @Default TypedFunction<TaxBracket, String> column14,
                                     @Default TypedFunction<TaxBracket, String> column15,
                                     @Default TypedFunction<TaxBracket, String> column16,
                                     @Default TypedFunction<TaxBracket, String> column17,
                                     @Default TypedFunction<TaxBracket, String> column18,
                                     @Default TypedFunction<TaxBracket, String> column19,
                                     @Default TypedFunction<TaxBracket, String> column20,
                                     @Default TypedFunction<TaxBracket, String> column21,
                                     @Default TypedFunction<TaxBracket, String> column22,
                                     @Default TypedFunction<TaxBracket, String> column23,
                                     @Default TypedFunction<TaxBracket, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<DBTrade> createTrades() {
        return new SimplePlaceholders<DBTrade>(DBTrade.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<DBTrade>>) (store, input) -> {
                    input = getSelection(store, DBTreasure.class, input);
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
                }, (ThrowingBiFunction<ValueStore, String, Predicate<DBTrade>>) (store, input) -> {
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
        ){
            @NoFormat
            @Command(desc = "Add columns to a Trade sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBTrade, String> column1,
                                     @Default TypedFunction<DBTrade, String> column2,
                                     @Default TypedFunction<DBTrade, String> column3,
                                     @Default TypedFunction<DBTrade, String> column4,
                                     @Default TypedFunction<DBTrade, String> column5,
                                     @Default TypedFunction<DBTrade, String> column6,
                                     @Default TypedFunction<DBTrade, String> column7,
                                     @Default TypedFunction<DBTrade, String> column8,
                                     @Default TypedFunction<DBTrade, String> column9,
                                     @Default TypedFunction<DBTrade, String> column10,
                                     @Default TypedFunction<DBTrade, String> column11,
                                     @Default TypedFunction<DBTrade, String> column12,
                                     @Default TypedFunction<DBTrade, String> column13,
                                     @Default TypedFunction<DBTrade, String> column14,
                                     @Default TypedFunction<DBTrade, String> column15,
                                     @Default TypedFunction<DBTrade, String> column16,
                                     @Default TypedFunction<DBTrade, String> column17,
                                     @Default TypedFunction<DBTrade, String> column18,
                                     @Default TypedFunction<DBTrade, String> column19,
                                     @Default TypedFunction<DBTrade, String> column20,
                                     @Default TypedFunction<DBTrade, String> column21,
                                     @Default TypedFunction<DBTrade, String> column22,
                                     @Default TypedFunction<DBTrade, String> column23,
                                     @Default TypedFunction<DBTrade, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<Transaction2> createTransactions() {
        return new SimplePlaceholders<Transaction2>(Transaction2.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<Transaction2>>) (store, input) -> {
                    input = getSelection(store, Transaction2.class, input);
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
                }, (ThrowingBiFunction<ValueStore, String, Predicate<Transaction2>>) (store, input) -> {
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
                }) {

            @Override
            public Set<Transaction2> deserializeSelection(ValueStore store, String input) {
                // index of {
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
                return _addSelectionAlias("all", command, db, name, "sender", "receiver", "banker", "transactionFilter", "startTime", "endTime", "includeOffset");
            }

            @NoFormat
            @Command(desc = "Add columns to a Transaction sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String bankRecordsDeposits(@Me JSONObject command, @Me GuildDB db, String name,
                                              NationOrAllianceOrGuild nationOrAllianceOrGuild, @Default Predicate<Transaction2> transactionFilter, @Default @Timestamp Long startTime, @Default @Timestamp Long endTime, @Switch("o") Boolean includeOffset, @Switch("o") Boolean includeTaxes) {
                return _addSelectionAlias("deposits", command, db, name, "nationOrAllianceOrGuild", "transactionFilter", "startTime", "endTime", "startTime", "endTime", "includeOffset");
            }

            @NoFormat
            @Command(desc = "Add columns to a Transaction sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<Transaction2, String> column1,
                                     @Default TypedFunction<Transaction2, String> column2,
                                     @Default TypedFunction<Transaction2, String> column3,
                                     @Default TypedFunction<Transaction2, String> column4,
                                     @Default TypedFunction<Transaction2, String> column5,
                                     @Default TypedFunction<Transaction2, String> column6,
                                     @Default TypedFunction<Transaction2, String> column7,
                                     @Default TypedFunction<Transaction2, String> column8,
                                     @Default TypedFunction<Transaction2, String> column9,
                                     @Default TypedFunction<Transaction2, String> column10,
                                     @Default TypedFunction<Transaction2, String> column11,
                                     @Default TypedFunction<Transaction2, String> column12,
                                     @Default TypedFunction<Transaction2, String> column13,
                                     @Default TypedFunction<Transaction2, String> column14,
                                     @Default TypedFunction<Transaction2, String> column15,
                                     @Default TypedFunction<Transaction2, String> column16,
                                     @Default TypedFunction<Transaction2, String> column17,
                                     @Default TypedFunction<Transaction2, String> column18,
                                     @Default TypedFunction<Transaction2, String> column19,
                                     @Default TypedFunction<Transaction2, String> column20,
                                     @Default TypedFunction<Transaction2, String> column21,
                                     @Default TypedFunction<Transaction2, String> column22,
                                     @Default TypedFunction<Transaction2, String> column23,
                                     @Default TypedFunction<Transaction2, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<DBBounty> createBounties() {
        return new SimplePlaceholders<DBBounty>(DBBounty.class,  store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<DBBounty>>) (store, input) -> {
                    input = getSelection(store, DBBounty.class, input);
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
                }, (ThrowingBiFunction<ValueStore, String, Predicate<DBBounty>>) (store, input) -> {
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
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of bounties")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBBounty> bounties) {
                return _addSelectionAlias(command, db, name, bounties, "bounties");
            }

            @NoFormat
            @Command(desc = "Add columns to a bounty sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<DBBounty, String> column1,
                                     @Default TypedFunction<DBBounty, String> column2,
                                     @Default TypedFunction<DBBounty, String> column3,
                                     @Default TypedFunction<DBBounty, String> column4,
                                     @Default TypedFunction<DBBounty, String> column5,
                                     @Default TypedFunction<DBBounty, String> column6,
                                     @Default TypedFunction<DBBounty, String> column7,
                                     @Default TypedFunction<DBBounty, String> column8,
                                     @Default TypedFunction<DBBounty, String> column9,
                                     @Default TypedFunction<DBBounty, String> column10,
                                     @Default TypedFunction<DBBounty, String> column11,
                                     @Default TypedFunction<DBBounty, String> column12,
                                     @Default TypedFunction<DBBounty, String> column13,
                                     @Default TypedFunction<DBBounty, String> column14,
                                     @Default TypedFunction<DBBounty, String> column15,
                                     @Default TypedFunction<DBBounty, String> column16,
                                     @Default TypedFunction<DBBounty, String> column17,
                                     @Default TypedFunction<DBBounty, String> column18,
                                     @Default TypedFunction<DBBounty, String> column19,
                                     @Default TypedFunction<DBBounty, String> column20,
                                     @Default TypedFunction<DBBounty, String> column21,
                                     @Default TypedFunction<DBBounty, String> column22,
                                     @Default TypedFunction<DBBounty, String> column23,
                                     @Default TypedFunction<DBBounty, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<Treaty> createTreaty() {
        return new SimplePlaceholders<Treaty>(Treaty.class,  store, validators, permisser,
        "TODO CM REF",
        (ThrowingBiFunction<ValueStore, String, Set<Treaty>>) (store, input) -> {
            input = getSelection(store, Treaty.class, input);
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
        }, (ThrowingBiFunction<ValueStore, String, Predicate<Treaty>>) (store, input) -> {
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
        }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of treaties")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Treaty> treaties) {
                return _addSelectionAlias(command, db, name, treaties, "treaties");
            }

            @NoFormat
            @Command(desc = "Add columns to a Treaty sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<Treaty, String> column1,
                                     @Default TypedFunction<Treaty, String> column2,
                                     @Default TypedFunction<Treaty, String> column3,
                                     @Default TypedFunction<Treaty, String> column4,
                                     @Default TypedFunction<Treaty, String> column5,
                                     @Default TypedFunction<Treaty, String> column6,
                                     @Default TypedFunction<Treaty, String> column7,
                                     @Default TypedFunction<Treaty, String> column8,
                                     @Default TypedFunction<Treaty, String> column9,
                                     @Default TypedFunction<Treaty, String> column10,
                                     @Default TypedFunction<Treaty, String> column11,
                                     @Default TypedFunction<Treaty, String> column12,
                                     @Default TypedFunction<Treaty, String> column13,
                                     @Default TypedFunction<Treaty, String> column14,
                                     @Default TypedFunction<Treaty, String> column15,
                                     @Default TypedFunction<Treaty, String> column16,
                                     @Default TypedFunction<Treaty, String> column17,
                                     @Default TypedFunction<Treaty, String> column18,
                                     @Default TypedFunction<Treaty, String> column19,
                                     @Default TypedFunction<Treaty, String> column20,
                                     @Default TypedFunction<Treaty, String> column21,
                                     @Default TypedFunction<Treaty, String> column22,
                                     @Default TypedFunction<Treaty, String> column23,
                                     @Default TypedFunction<Treaty, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<Project> createProjects() {
        return new StaticPlaceholders<Project>(Project.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<Project>>) (store, input) -> {
                    input = getSelection(store, Project.class, input);
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
            @NoFormat
            @Command(desc = "Add an alias for a selection of Projects")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Project> projects) {
                return _addSelectionAlias(command, db, name, projects, "projects");
            }

            @NoFormat
            @Command(desc = "Add columns to a Project sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<Project, String> column1,
                                     @Default TypedFunction<Project, String> column2,
                                     @Default TypedFunction<Project, String> column3,
                                     @Default TypedFunction<Project, String> column4,
                                     @Default TypedFunction<Project, String> column5,
                                     @Default TypedFunction<Project, String> column6,
                                     @Default TypedFunction<Project, String> column7,
                                     @Default TypedFunction<Project, String> column8,
                                     @Default TypedFunction<Project, String> column9,
                                     @Default TypedFunction<Project, String> column10,
                                     @Default TypedFunction<Project, String> column11,
                                     @Default TypedFunction<Project, String> column12,
                                     @Default TypedFunction<Project, String> column13,
                                     @Default TypedFunction<Project, String> column14,
                                     @Default TypedFunction<Project, String> column15,
                                     @Default TypedFunction<Project, String> column16,
                                     @Default TypedFunction<Project, String> column17,
                                     @Default TypedFunction<Project, String> column18,
                                     @Default TypedFunction<Project, String> column19,
                                     @Default TypedFunction<Project, String> column20,
                                     @Default TypedFunction<Project, String> column21,
                                     @Default TypedFunction<Project, String> column22,
                                     @Default TypedFunction<Project, String> column23,
                                     @Default TypedFunction<Project, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<ResourceType> createResourceType() {
        return new StaticPlaceholders<ResourceType>(ResourceType.class, store, validators, permisser,
        "TODO CM REF",
        (ThrowingBiFunction<ValueStore, String, Set<ResourceType>>) (store, input) -> {
            input = getSelection(store, ResourceType.class, input);
            if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(ResourceType.values));
            if (SpreadSheet.isSheet(input)) {
                return SpreadSheet.parseSheet(input, List.of("resource"), true, (type, str) -> PWBindings.resource(str));
            }
            return Set.of(PWBindings.resource(input));
        }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of resources")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<ResourceType> resources) {
                return _addSelectionAlias(command, db, name, resources, "resources");
            }

            @NoFormat
            @Command(desc = "Add columns to a Resource sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<ResourceType, String> column1,
                                     @Default TypedFunction<ResourceType, String> column2,
                                     @Default TypedFunction<ResourceType, String> column3,
                                     @Default TypedFunction<ResourceType, String> column4,
                                     @Default TypedFunction<ResourceType, String> column5,
                                     @Default TypedFunction<ResourceType, String> column6,
                                     @Default TypedFunction<ResourceType, String> column7,
                                     @Default TypedFunction<ResourceType, String> column8,
                                     @Default TypedFunction<ResourceType, String> column9,
                                     @Default TypedFunction<ResourceType, String> column10,
                                     @Default TypedFunction<ResourceType, String> column11,
                                     @Default TypedFunction<ResourceType, String> column12,
                                     @Default TypedFunction<ResourceType, String> column13,
                                     @Default TypedFunction<ResourceType, String> column14,
                                     @Default TypedFunction<ResourceType, String> column15,
                                     @Default TypedFunction<ResourceType, String> column16,
                                     @Default TypedFunction<ResourceType, String> column17,
                                     @Default TypedFunction<ResourceType, String> column18,
                                     @Default TypedFunction<ResourceType, String> column19,
                                     @Default TypedFunction<ResourceType, String> column20,
                                     @Default TypedFunction<ResourceType, String> column21,
                                     @Default TypedFunction<ResourceType, String> column22,
                                     @Default TypedFunction<ResourceType, String> column23,
                                     @Default TypedFunction<ResourceType, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<AttackType> createAttackTypes() {
        return new StaticPlaceholders<AttackType>(AttackType.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<AttackType>>) (store, input) -> {
                    input = getSelection(store, AttackType.class, input);
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(AttackType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("attack_type"), true, (type, str) -> PWBindings.attackType(str));
                    }
                    return Set.of(PWBindings.attackType(input));
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of attack types")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<AttackType> attack_types) {
                return _addSelectionAlias(command, db, name, attack_types, "attack_types");
            }

            @NoFormat
            @Command(desc = "Add columns to a AttackType sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<AttackType, String> column1,
                                     @Default TypedFunction<AttackType, String> column2,
                                     @Default TypedFunction<AttackType, String> column3,
                                     @Default TypedFunction<AttackType, String> column4,
                                     @Default TypedFunction<AttackType, String> column5,
                                     @Default TypedFunction<AttackType, String> column6,
                                     @Default TypedFunction<AttackType, String> column7,
                                     @Default TypedFunction<AttackType, String> column8,
                                     @Default TypedFunction<AttackType, String> column9,
                                     @Default TypedFunction<AttackType, String> column10,
                                     @Default TypedFunction<AttackType, String> column11,
                                     @Default TypedFunction<AttackType, String> column12,
                                     @Default TypedFunction<AttackType, String> column13,
                                     @Default TypedFunction<AttackType, String> column14,
                                     @Default TypedFunction<AttackType, String> column15,
                                     @Default TypedFunction<AttackType, String> column16,
                                     @Default TypedFunction<AttackType, String> column17,
                                     @Default TypedFunction<AttackType, String> column18,
                                     @Default TypedFunction<AttackType, String> column19,
                                     @Default TypedFunction<AttackType, String> column20,
                                     @Default TypedFunction<AttackType, String> column21,
                                     @Default TypedFunction<AttackType, String> column22,
                                     @Default TypedFunction<AttackType, String> column23,
                                     @Default TypedFunction<AttackType, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<MilitaryUnit> createMilitaryUnit() {
        return new StaticPlaceholders<MilitaryUnit>(MilitaryUnit.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<MilitaryUnit>>) (store, input) -> {
                    input = getSelection(store, MilitaryUnit.class, input);
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(MilitaryUnit.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("unit"), true, (type, str) -> PWBindings.unit(str));
                    }
                    return Set.of(PWBindings.unit(input));
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of Military Units")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<MilitaryUnit> military_units) {
                return _addSelectionAlias(command, db, name, military_units, "military_units");
            }

            @NoFormat
            @Command(desc = "Add columns to a Military Unit sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<MilitaryUnit, String> column1,
                                     @Default TypedFunction<MilitaryUnit, String> column2,
                                     @Default TypedFunction<MilitaryUnit, String> column3,
                                     @Default TypedFunction<MilitaryUnit, String> column4,
                                     @Default TypedFunction<MilitaryUnit, String> column5,
                                     @Default TypedFunction<MilitaryUnit, String> column6,
                                     @Default TypedFunction<MilitaryUnit, String> column7,
                                     @Default TypedFunction<MilitaryUnit, String> column8,
                                     @Default TypedFunction<MilitaryUnit, String> column9,
                                     @Default TypedFunction<MilitaryUnit, String> column10,
                                     @Default TypedFunction<MilitaryUnit, String> column11,
                                     @Default TypedFunction<MilitaryUnit, String> column12,
                                     @Default TypedFunction<MilitaryUnit, String> column13,
                                     @Default TypedFunction<MilitaryUnit, String> column14,
                                     @Default TypedFunction<MilitaryUnit, String> column15,
                                     @Default TypedFunction<MilitaryUnit, String> column16,
                                     @Default TypedFunction<MilitaryUnit, String> column17,
                                     @Default TypedFunction<MilitaryUnit, String> column18,
                                     @Default TypedFunction<MilitaryUnit, String> column19,
                                     @Default TypedFunction<MilitaryUnit, String> column20,
                                     @Default TypedFunction<MilitaryUnit, String> column21,
                                     @Default TypedFunction<MilitaryUnit, String> column22,
                                     @Default TypedFunction<MilitaryUnit, String> column23,
                                     @Default TypedFunction<MilitaryUnit, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<TreatyType> createTreatyType() {
        return new StaticPlaceholders<TreatyType>(TreatyType.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<TreatyType>>) (store, input) -> {
                    input = getSelection(store, TreatyType.class, input);
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(TreatyType.values));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("treaty_type"), true, (type, str) -> PWBindings.TreatyType(str));
                    }
                    return Set.of(PWBindings.TreatyType(input));
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of Treaty Types")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<TreatyType> treaty_types) {
                return _addSelectionAlias(command, db, name, treaty_types, "treaty_types");
            }

            @NoFormat
            @Command(desc = "Add columns to a TreatyType sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<TreatyType, String> column1,
                                     @Default TypedFunction<TreatyType, String> column2,
                                     @Default TypedFunction<TreatyType, String> column3,
                                     @Default TypedFunction<TreatyType, String> column4,
                                     @Default TypedFunction<TreatyType, String> column5,
                                     @Default TypedFunction<TreatyType, String> column6,
                                     @Default TypedFunction<TreatyType, String> column7,
                                     @Default TypedFunction<TreatyType, String> column8,
                                     @Default TypedFunction<TreatyType, String> column9,
                                     @Default TypedFunction<TreatyType, String> column10,
                                     @Default TypedFunction<TreatyType, String> column11,
                                     @Default TypedFunction<TreatyType, String> column12,
                                     @Default TypedFunction<TreatyType, String> column13,
                                     @Default TypedFunction<TreatyType, String> column14,
                                     @Default TypedFunction<TreatyType, String> column15,
                                     @Default TypedFunction<TreatyType, String> column16,
                                     @Default TypedFunction<TreatyType, String> column17,
                                     @Default TypedFunction<TreatyType, String> column18,
                                     @Default TypedFunction<TreatyType, String> column19,
                                     @Default TypedFunction<TreatyType, String> column20,
                                     @Default TypedFunction<TreatyType, String> column21,
                                     @Default TypedFunction<TreatyType, String> column22,
                                     @Default TypedFunction<TreatyType, String> column23,
                                     @Default TypedFunction<TreatyType, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<IACheckup.AuditType> createAuditType() {
        return new StaticPlaceholders<IACheckup.AuditType>(IACheckup.AuditType.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<IACheckup.AuditType>>) (store, input) -> {
                    input = getSelection(store, IACheckup.AuditType.class, input);
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(IACheckup.AuditType.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("audit"), true, (type, str) -> PWBindings.auditType(str));
                    }
                    return Set.of(PWBindings.auditType(input));
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of Audit Types")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<IACheckup.AuditType> audit_types) {
                return _addSelectionAlias(command, db, name, audit_types, "audit_types");
            }

            @NoFormat
            @Command(desc = "Add columns to a Audit Type sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<IACheckup.AuditType, String> column1,
                                     @Default TypedFunction<IACheckup.AuditType, String> column2,
                                     @Default TypedFunction<IACheckup.AuditType, String> column3,
                                     @Default TypedFunction<IACheckup.AuditType, String> column4,
                                     @Default TypedFunction<IACheckup.AuditType, String> column5,
                                     @Default TypedFunction<IACheckup.AuditType, String> column6,
                                     @Default TypedFunction<IACheckup.AuditType, String> column7,
                                     @Default TypedFunction<IACheckup.AuditType, String> column8,
                                     @Default TypedFunction<IACheckup.AuditType, String> column9,
                                     @Default TypedFunction<IACheckup.AuditType, String> column10,
                                     @Default TypedFunction<IACheckup.AuditType, String> column11,
                                     @Default TypedFunction<IACheckup.AuditType, String> column12,
                                     @Default TypedFunction<IACheckup.AuditType, String> column13,
                                     @Default TypedFunction<IACheckup.AuditType, String> column14,
                                     @Default TypedFunction<IACheckup.AuditType, String> column15,
                                     @Default TypedFunction<IACheckup.AuditType, String> column16,
                                     @Default TypedFunction<IACheckup.AuditType, String> column17,
                                     @Default TypedFunction<IACheckup.AuditType, String> column18,
                                     @Default TypedFunction<IACheckup.AuditType, String> column19,
                                     @Default TypedFunction<IACheckup.AuditType, String> column20,
                                     @Default TypedFunction<IACheckup.AuditType, String> column21,
                                     @Default TypedFunction<IACheckup.AuditType, String> column22,
                                     @Default TypedFunction<IACheckup.AuditType, String> column23,
                                     @Default TypedFunction<IACheckup.AuditType, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<NationColor> createNationColor() {
        return new StaticPlaceholders<NationColor>(NationColor.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<NationColor>>) (store, input) -> {
                    input = getSelection(store, NationColor.class, input);
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(NationColor.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("color"), true, (type, str) -> PWBindings.NationColor(str));
                    }
                    return Set.of(PWBindings.NationColor(input));
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of Nation Colors")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<NationColor> colors) {
                return _addSelectionAlias(command, db, name, colors, "colors");
            }

            @NoFormat
            @Command(desc = "Add columns to a Nation Color sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<NationColor, String> column1,
                                     @Default TypedFunction<NationColor, String> column2,
                                     @Default TypedFunction<NationColor, String> column3,
                                     @Default TypedFunction<NationColor, String> column4,
                                     @Default TypedFunction<NationColor, String> column5,
                                     @Default TypedFunction<NationColor, String> column6,
                                     @Default TypedFunction<NationColor, String> column7,
                                     @Default TypedFunction<NationColor, String> column8,
                                     @Default TypedFunction<NationColor, String> column9,
                                     @Default TypedFunction<NationColor, String> column10,
                                     @Default TypedFunction<NationColor, String> column11,
                                     @Default TypedFunction<NationColor, String> column12,
                                     @Default TypedFunction<NationColor, String> column13,
                                     @Default TypedFunction<NationColor, String> column14,
                                     @Default TypedFunction<NationColor, String> column15,
                                     @Default TypedFunction<NationColor, String> column16,
                                     @Default TypedFunction<NationColor, String> column17,
                                     @Default TypedFunction<NationColor, String> column18,
                                     @Default TypedFunction<NationColor, String> column19,
                                     @Default TypedFunction<NationColor, String> column20,
                                     @Default TypedFunction<NationColor, String> column21,
                                     @Default TypedFunction<NationColor, String> column22,
                                     @Default TypedFunction<NationColor, String> column23,
                                     @Default TypedFunction<NationColor, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }

    private Placeholders<Building> createBuilding() {
        return new StaticPlaceholders<Building>(Building.class, store, validators, permisser,
                "TODO CM REF",
                (ThrowingBiFunction<ValueStore, String, Set<Building>>) (store, input) -> {
                    input = getSelection(store, Building.class, input);
                    if (input.equalsIgnoreCase("*")) return new HashSet<>(Arrays.asList(Buildings.values()));
                    if (SpreadSheet.isSheet(input)) {
                        return SpreadSheet.parseSheet(input, List.of("attack_type"), true, (type, str) -> PWBindings.getBuilding(str));
                    }
                    return Set.of(PWBindings.getBuilding(input));
                }) {
            @NoFormat
            @Command(desc = "Add an alias for a selection of Buildings")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<Building> Buildings) {
                return _addSelectionAlias(command, db, name, Buildings, "Buildings");
            }

            @NoFormat
            @Command(desc = "Add columns to a Building sheet")
            @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
            public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                                     @Default TypedFunction<Building, String> column1,
                                     @Default TypedFunction<Building, String> column2,
                                     @Default TypedFunction<Building, String> column3,
                                     @Default TypedFunction<Building, String> column4,
                                     @Default TypedFunction<Building, String> column5,
                                     @Default TypedFunction<Building, String> column6,
                                     @Default TypedFunction<Building, String> column7,
                                     @Default TypedFunction<Building, String> column8,
                                     @Default TypedFunction<Building, String> column9,
                                     @Default TypedFunction<Building, String> column10,
                                     @Default TypedFunction<Building, String> column11,
                                     @Default TypedFunction<Building, String> column12,
                                     @Default TypedFunction<Building, String> column13,
                                     @Default TypedFunction<Building, String> column14,
                                     @Default TypedFunction<Building, String> column15,
                                     @Default TypedFunction<Building, String> column16,
                                     @Default TypedFunction<Building, String> column17,
                                     @Default TypedFunction<Building, String> column18,
                                     @Default TypedFunction<Building, String> column19,
                                     @Default TypedFunction<Building, String> column20,
                                     @Default TypedFunction<Building, String> column21,
                                     @Default TypedFunction<Building, String> column22,
                                     @Default TypedFunction<Building, String> column23,
                                     @Default TypedFunction<Building, String> column24) throws GeneralSecurityException, IOException {
                return Placeholders._addColumns(this, command,db, io, author, sheet,
                        column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                        column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                        column21, column22, column23, column24);
            }
        };
    }



}
