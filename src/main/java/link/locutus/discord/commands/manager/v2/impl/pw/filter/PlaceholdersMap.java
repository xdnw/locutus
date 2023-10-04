package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBBan;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.db.entities.NationFilterString;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
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
//        this.placeholders.put(DBCity.class, createCities());
        // -TaxBracket
//        this.placeholders.put(TaxBracket.class, createBrackets());
//        //-GuildKey
//        this.placeholders.put(GuildSetting.class, createGuildSetting());
//        //- Tax records
//        // - * (within aa)
//        this.placeholders.put(BankDB.TaxDeposit.class, createTaxDeposit());
//
//        this.placeholders.put(DBAttack.class, createAttacks());
//
//        this.placeholders.put(DBTrade.class, createTrades());
//
//        this.placeholders.put(Transaction2.class, createTransactions());
//
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
                    return Collections.singleton(guild);
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
                    return Collections.singleton(PWBindings.ban(input));
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

                    if (SpreadSheet.isSheet(input)) {
                        Set<String> inputs = SpreadSheet.parseSheet(input, List.of("nations"), true, (type, str) -> str);
                        Set<NationList> lists = new HashSet<>();
                        for (String str : inputs) {
                            Set<DBNation> nations = PWBindings.nations(null, guild, str);
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
                    throw new IllegalArgumentException("NationList predicates are unsupported. Please use DBNation instead");
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
                        return Collections.singleton(PWBindings.bounty(input));
                    }
                    Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
                    Set<DBNation> nations = PWBindings.nations(null, guild, input);
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
            return Collections.singleton(PWBindings.resource(input));
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
                    return Collections.singleton(PWBindings.attackType(input));
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
                    return Collections.singleton(PWBindings.unit(input));
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
                    return Collections.singleton(PWBindings.TreatyType(input));
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
                    return Collections.singleton(PWBindings.auditType(input));
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
                    return Collections.singleton(PWBindings.NationColor(input));
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
                    return Collections.singleton(PWBindings.getBuilding(input));
                });
    }



}
