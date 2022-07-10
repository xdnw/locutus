package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.SimpleNationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllianceDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.bindings.Operation;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.*;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PWBindings extends BindingHelper {
    @Binding(value = "City build json or url", examples = {"city/id=371923", "{city-json}", "city/id=1{json-modifiers}"})
    public CityBuild city(@Me DBNation nation, String input) {
        int index = input.indexOf('{');
        String json;
        if (index == -1) {
            json = null;
        } else {
            json = input.substring(index);
            input = input.substring(0, index);
        }
        CityBuild build = null;
        if (input.contains("city/id=")) {
            int cityId = Integer.parseInt(input.split("=")[1]);
            DBCity city = Locutus.imp().getNationDB().getCitiesV3ByCityId(cityId);
            if (city == null) throw new IllegalArgumentException("No city found in cache for " + cityId);
            build = city.toJavaCity(nation).toCityBuild();
        }
        if (json != null) {
            CityBuild build2 = CityBuild.of(json, true);
            json = build2.toString().replace("}", "") + "," + build.toString().replace("{", "");
            build = CityBuild.of(json, true);
        }
        return build;
    }

    @Binding(value = "City ranges", examples = {"c1-10", "c11+"})
    public CityRanges CityRanges(String input) {
        return CityRanges.parse(input);
    }

    @Binding(value = "nation id, name or url", examples = {"Borg", "<@664156861033086987>", "Danzek", "189573", "https://politicsandwar.com/nation/id=189573"})
    public DBNation nation(String input) {
        return DiscordUtil.parseNation(input);
    }

    @Binding()
    public NationOrAlliance nationOrAlliance(String input) {
        String lower = input.toLowerCase();
        if (lower.startsWith("aa:")) {
            return alliance(input.split(":", 2)[1]);
        }
        if (lower.contains("alliance/id=")) {
            return alliance(input);
        }
        DBNation nation = DiscordUtil.parseNation(input);
        if (nation == null) {
            return alliance(input);
        }
        return nation;
    }

    @Binding(examples = "{nation}")
    public NationPlaceholder placeholder(ArgumentStack stack, String input) {
        CommandManager2 v2 = Locutus.imp().getCommandManager().getV2();
        NationPlaceholders placeholders = v2.getNationPlaceholders();
        ParametricCallable ph = placeholders.get(input);
        ph.validatePermissions(stack.getStore(), stack.getPermissionHandler());
        Map.Entry<Type, Function<DBNation, Object>> entry = placeholders.getPlaceholderFunction(stack.getStore(), input);
        return new SimpleNationPlaceholder(ph.getPrimaryCommandId(), entry.getKey(), entry.getValue());
    }

    @Binding(examples = {"25/25"})
    public TaxRate taxRate(String input) {
        if (!input.contains("/")) throw new IllegalArgumentException("Tax rate must be in the form: 0/0");
        String[] split = input.split("/");
        int moneyRate = Integer.parseInt(split[0]);
        int rssRate = Integer.parseInt(split[1]);
        return new TaxRate(moneyRate, rssRate);
    }

    @Binding(examples = {"Borg", "alliance/id=7452", "647252780817448972"})
    public NationOrAllianceOrGuild nationOrAllianceOrGuild(String input) {
        try {
            return nationOrAlliance(input);
        } catch (IllegalArgumentException ignore) {
            if (input.startsWith("guild:")) {
                input = input.substring(6);
                if (!MathMan.isInteger(input)) {
                    for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
                        if (db.getName().equalsIgnoreCase(input) || db.getGuild().getName().equalsIgnoreCase(input)) {
                            return db;
                        }
                    }
                }
            }
            if (MathMan.isInteger(input)) {
                long id = Long.parseLong(input);
                if (id > Integer.MAX_VALUE) {
                    GuildDB db = Locutus.imp().getGuildDB(id);
                    if (db == null) throw new IllegalArgumentException("Not connected to guild: " + id);
                    return db;
                }
            }
            for (GuildDB value : Locutus.imp().getGuildDatabases().values()) {
                if (value.getName().equalsIgnoreCase(input)) {
                    return value;
                }
            }
            throw ignore;
        }
    }

    @Binding(examples = {"'Error 404'", "7413", "https://politicsandwar.com/alliance/id=7413"})
    public DBAlliance alliance(String input) {
        Integer aaId = PnwUtil.parseAllianceId(input);
        if (aaId == null) throw new IllegalArgumentException("Invalid alliance: " + input);
        return DBAlliance.getOrCreate(aaId);
    }

    @Binding(value = "Spy operation")
    public Set<SpyCount.Operation> opTypes(String input) {
        Set<SpyCount.Operation> allowedOpTypes = new HashSet<>();
        for (String type : input.split(",")) {
            if (type.equalsIgnoreCase("*")) {
                allowedOpTypes.addAll(Arrays.asList(SpyCount.Operation.values()));
                allowedOpTypes.remove(SpyCount.Operation.INTEL);
            } else {
                SpyCount.Operation op = StringMan.parseUpper(SpyCount.Operation.class, type);
                allowedOpTypes.add(op);
            }
        }
        return allowedOpTypes;
    }


    @Binding
    public Set<AllianceMetric> metrics(String input) {
        Set<AllianceMetric> metrics = new HashSet<>();
        for (String type : input.split(",")) {
            AllianceMetric arg = StringMan.parseUpper(AllianceMetric.class, type);
            metrics.add(arg);
        }
        return metrics;
    }

    @Binding(examples = "borg,AA:Cataclysm,#position>1")
    public Set<DBNation> nations(@Me Guild guild, String input) {
        Set<DBNation> nations = DiscordUtil.parseNations(guild, input);
        if (nations == null) throw new IllegalArgumentException("Invalid nations: " + input);
        return nations;
    }

    @Binding(examples = "borg,AA:Cataclysm,#position>1")
    public NationList nationList(@Me Guild guild, String input) {
        return new SimpleNationList(nations(guild, input)).setFilter(input);
    }

    @Binding(examples = "score,soldiers")
    public Set<NationAttributeDouble> nationMetricDoubles(ArgumentStack stack, String input) {
        Set<NationAttributeDouble> metrics = new LinkedHashSet<>();
        for (String arg : StringMan.split(input, ',')) {
            metrics.add(nationMetricDouble(stack, arg));
        }
        return metrics;
    }

    @Binding(examples = "borg,AA:Cataclysm")
    public Set<NationOrAlliance> nationOrAlliance(@Me Guild guild, String input) {
        Set<NationOrAlliance> result = new LinkedHashSet<>();

        for (String group : input.split("\\|+")) {
            List<String> remainder = new ArrayList<>();
            if (!group.contains("#")) {
                List<String> args = StringMan.split(group, ',');

                GuildDB db = Locutus.imp().getGuildDB(guild);
                for (String arg : args) {
                    try {
                        DBAlliance aa = alliance(arg);
                        if (aa.exists()) {
                            result.add(aa);
                            continue;
                        }
                    } catch (IllegalArgumentException ignore) {
                        ignore.printStackTrace();
                    }
                    if (db != null) {
                        if (arg.charAt(0) == '~') arg = arg.substring(1);
                        Set<Integer> coalition = db.getCoalition(arg);
                        if (!coalition.isEmpty()) {
                            result.addAll(coalition.stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet()));
                            continue;
                        }
                    }
                    remainder.add(arg);
                }
            } else {
                remainder.add(group);
            }
            result.addAll(nations(guild, StringMan.join(remainder, ",")));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Invalid nations or alliances: " + input);
        return result;
    }

    @Binding(examples = "borg,AA:Cataclysm,647252780817448972")
    public Set<NationOrAllianceOrGuild> nationOrAllianceOrGuild(@Me Guild guild, String input) {
        List<String> args = StringMan.split(input, ',');
        Set<NationOrAllianceOrGuild> result = new LinkedHashSet<>();
        List<String> remainder = new ArrayList<>();
        outer:
        for (String arg : args) {
            if (arg.startsWith("guild:")) {
                arg = arg.substring(6);
                if (!MathMan.isInteger(arg)) {
                    for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
                        if (db.getName().equalsIgnoreCase(arg) || db.getGuild().getName().equalsIgnoreCase(arg)) {
                            result.add(db);
                            continue outer;
                        }
                    }
                    throw new IllegalArgumentException("Unknown guild: " + arg);
                }
            }
            if (MathMan.isInteger(arg)) {
                long id = Long.parseLong(arg);
                if (id > Integer.MAX_VALUE) {
                    GuildDB db = Locutus.imp().getGuildDB(id);
                    if (db == null) throw new IllegalArgumentException("Unknown guild: " + id);
                    result.add(db);
                    continue;
                }
            }

            DBAlliance aa = alliance(arg);
            try {
                if (aa.exists()) {
                    result.add(aa);
                    continue;
                }
            } catch (IllegalArgumentException ignore) {}
            GuildDB db = Locutus.imp().getGuildDB(guild);
            if (db != null) {
                if (arg.charAt(0) == '~') arg = arg.substring(1);
                Set<Integer> coalition = db.getCoalition(arg);
                if (!coalition.isEmpty()) {
                    result.addAll(coalition.stream().map(f -> DBAlliance.getOrCreate(f)).collect(Collectors.toSet()));
                    continue;
                }
            }
            remainder.add(arg);
        }
        result.addAll(nations(guild, StringMan.join(remainder, ",")));
        if (result.isEmpty()) throw new IllegalArgumentException("Invalid nations or alliances: " + input);
        return result;
    }

    @Binding(examples = "Cataclysm,790")
    public Set<DBAlliance> alliances(@Me Guild guild, String input) {
        Set<Integer> aaIds = DiscordUtil.parseAlliances(guild, input);
        if (aaIds == null) throw new IllegalArgumentException("Invalid alliances: " + input);
        Set<DBAlliance> alliances = new HashSet<>();
        for (Integer aaId : aaIds) {
            alliances.add(DBAlliance.getOrCreate(aaId));
        }
        return alliances;
    }


    @Binding(examples = "ACTIVE,EXPIRED")
    public Set<WarStatus> WarStatuses(String input) {
        Set<WarStatus> result = new HashSet<>();
        for (String s : input.split(",")) {
            result.add(WarStatus.parse(s));
        }
        return result;
    }

    @Binding(examples = {"aluminum", "money", "*", "manu", "raws", "!food"})
    public static List<ResourceType> rssTypes(String input) {
        Set<ResourceType> types = new LinkedHashSet<>();
        for (String arg : input.split(",")) {
            boolean remove = arg.startsWith("!");
            if (remove) arg = arg.substring(1);
            List<ResourceType> toAddOrRemove;
            if (arg.equalsIgnoreCase("*")) {
                toAddOrRemove = (Arrays.asList(ResourceType.values()));
            } else if (arg.equalsIgnoreCase("manu") || arg.equalsIgnoreCase("manufactured")) {
                toAddOrRemove = Arrays.asList(
                        ResourceType.GASOLINE,
                        ResourceType.MUNITIONS,
                        ResourceType.STEEL,
                        ResourceType.ALUMINUM);
            } else if (arg.equalsIgnoreCase("raws") || arg.equalsIgnoreCase("raw")) {
                toAddOrRemove = Arrays.asList(ResourceType.COAL,
                        ResourceType.OIL,
                        ResourceType.URANIUM,
                        ResourceType.LEAD,
                        ResourceType.IRON,
                        ResourceType.BAUXITE);
            } else {
                toAddOrRemove = Collections.singletonList(ResourceType.parse(arg));
            }
            if (remove) types.removeAll(toAddOrRemove);
            else types.addAll(toAddOrRemove);
        }
        return new ArrayList<>(types);
    }

    @AllianceDepositLimit
    @Binding(examples = "{money=1.2,food=6}")
    public Map<ResourceType, Double> resourcesAA(String resources) {
        return resources(resources);
    }

    @NationDepositLimit
    @Binding(examples = "{money=1.2,food=6}")
    public Map<ResourceType, Double> resourcesNation(String resources) {
        return resources(resources);
    }

    @Binding(examples = "{money=1.2,food=6}")
    public Map<ResourceType, Double> resources(String resources) {
        Map<ResourceType, Double> map = PnwUtil.parseResources(resources);
        if (map == null) throw new IllegalArgumentException("Invalid resources: " + resources);
        return map;
    }

    @Binding(examples = "{soldiers=12,tanks=56}")
    public Map<MilitaryUnit, Long> units(String input) {
        Map<MilitaryUnit, Long> map = PnwUtil.parseUnits(input);
        if (map == null) throw new IllegalArgumentException("Invalid units: " + input + ". Valid types: " + StringMan.getString(MilitaryUnit.values()) + ". In the form: `{SOLDIERS=1234,TANKS=5678}`");
        return map;
    }

    @Binding(examples = {"money", "aluminum"})
    public ResourceType resource(String resource) {
        return ResourceType.parse(resource);
    }

    @Binding
    public DepositType DepositType(String input) {
        return StringMan.parseUpper(DepositType.class, input);
    }

    @Binding
    public WarType warType(String warType) {
        return WarType.parse(warType);
    }

    @Binding
    @Me
    public DBNation nation(@Me User user) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) throw new IllegalArgumentException("Please use " + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "verify");
        return nation;
    }

    @Binding
    @Me
    public OffshoreInstance offshore(@Me GuildDB db) {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) throw new IllegalArgumentException("No offshore is set");
        return offshore;
    }

    @Binding
    @Me
    public DBAlliance alliance(@Me DBNation nation) {
        return DBAlliance.getOrCreate(nation.getAlliance_id());
    }

    @Binding
    @Me
    public Map<ResourceType, Double> deposits(@Me GuildDB db, @Me DBNation nation) throws IOException {
        return PnwUtil.resourcesToMap(deposits2(db, nation));
    }

    @Binding
    @Me
    public double[] deposits2(@Me GuildDB db, @Me DBNation nation) throws IOException {
        return nation.getNetDeposits(db);
    }

    @Binding
    @Me
    public Rank rank(@Me DBNation nation) {
        return Rank.byId(nation.getPosition());
    }

    @Binding
    public WarStatus status(String input) {
        return WarStatus.parse(input);
    }

    @Binding
    public WarDB warDB() {
        return Locutus.imp().getWarDb();
    }

    @Binding
    public NationDB nationDB() {
        return Locutus.imp().getNationDB();
    }

    @Binding
    public BankDB bankDB() {
        return Locutus.imp().getBankDB();
    }

    @Binding
    public StockDB stockDB() {
        return Locutus.imp().getStockDB();
    }
    @Binding
    public BaseballDB baseballDB() {
        if (Settings.INSTANCE.TASKS.BASEBALL_SECONDS <= 0) throw new IllegalStateException("Baseball is not enabled");
        return Locutus.imp().getBaseballDB();
    }

    @Binding
    public ForumDB forumDB() {
        return Locutus.imp().getForumDb();
    }

    @Binding
    public DiscordDB discordDB() {
        return Locutus.imp().getDiscordDB();
    }

    @Binding
    @Me
    public GuildDB guildDB(@Me Guild guild) {
        return Locutus.imp().getGuildDB(guild);
    }

    @Binding
    @Me
    public GuildHandler handler(@Me GuildDB db) {
        return db.getHandler();
    }

    @Binding
    public ExecutorService executor() {
        return Locutus.imp().getExecutor();
    }

    @Binding
    public TradeManager tradeManager() {
        return Locutus.imp().getTradeManager();
    }

    @Binding
    public TradeDB tradeDB() {
        return Locutus.imp().getTradeManager().getTradeDb();
    }

    @Binding
    public IACategory iaCat(@Me GuildDB db) {
        IACategory iaCat = db.getIACategory();
        if (iaCat == null) throw new IllegalArgumentException("No IA category exists (please see: <TODO document>)");
        return iaCat;
    }

    @Binding
    @Me
    public Auth auth(@Me DBNation nation) {
        return nation.getAuth(null);
    }

    @Binding
    public Rank rank(String rank) {
        return emum(Rank.class, rank);
    }

    @Binding
    public DBAlliancePosition position(@Me GuildDB db, String name) {
        return DBAlliancePosition.parse(name, db.getAlliance_id(), true);
    }

    @Binding
    public AlliancePermission alliancePermission(String name) {
        return emum(AlliancePermission.class, name);
    }

    @Binding
    public GuildDB.Key key(String input) {
        return emum(GuildDB.Key.class, input);
    }

    @Binding
    public UnsortedCommands.ClearRolesEnum clearRolesEnum(String input) {
        return emum(UnsortedCommands.ClearRolesEnum.class, input);
    }

    @Binding(examples = {"@role", "672238503119028224", "roleName"})
    public Roles role(String role) {
        return emum(Roles.class, role);
    }

    @Binding
    public MilitaryUnit unit(String unit) {
        return emum(MilitaryUnit.class, unit);
    }

    @Binding
    public Operation op(String input) {
        return emum(Operation.class, input);
    }

    @Binding
    public SpyCount.Safety safety(String input) {
        return emum(SpyCount.Safety.class, input);
    }

    @Binding
    public Coalition coalition(String input) {
        return emum(Coalition.class, input);
    }

    @Binding
    @GuildCoalition
    public String guildCoalition(@Me GuildDB db, String input) {
        input = input.toLowerCase();
        Set<String> coalitions = new HashSet<>(db.getCoalitions().keySet());
        for (Coalition value : Coalition.values()) coalitions.add(value.name().toLowerCase());
        if (!coalitions.contains(input)) throw new IllegalArgumentException("No coalition found matching: `" + input + "`. Options: " + StringMan.getString(coalitions));
        return input;
    }

    @Me
    @Binding
    public WarCategory.WarRoom warRoom(@Me WarCategory warCat, @Me MessageChannel channel) {
        WarCategory.WarRoom warroom = warCat.getWarRoom(((GuildMessageChannel) channel));
        if (warroom == null) throw new IllegalArgumentException("The command was not run in a war room");
        return warroom;
    }
    @Me
    @Binding
    public WarCategory warChannelBinding(@Me GuildDB db) {
        WarCategory warChannel = db.getWarChannel(true);
        if (warChannel == null) throw new IllegalArgumentException("War channels are not enabled. `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore ENABLE_WAR_ROOMS true`");
        return warChannel;
    }

    @Binding
    public Project project(String input) {
        Project project = Projects.get(input);
        if (project == null) throw new IllegalArgumentException("Invalid project: `"  + input + "`. Options: " + StringMan.getString(Projects.values));
        return project;
    }

    @Binding
    public AllianceMetric AllianceMetric(String input) {
        return StringMan.parseUpper(AllianceMetric.class, input);
    }

    @Binding
    public NationMeta.BeigeAlertMode BeigeAlertMode(String input) {
        return StringMan.parseUpper(NationMeta.BeigeAlertMode.class, input);
    }

    @Binding
    public NationMeta.BeigeAlertRequiredStatus BeigeAlertRequiredStatus(String input) {
        return StringMan.parseUpper(NationMeta.BeigeAlertRequiredStatus.class, input);
    }

    @Binding
    public NationAttributeDouble nationMetricDouble(ArgumentStack stack, String input) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        NationAttributeDouble metric = placeholders.getMetricDouble(stack.getStore(), input);
        if (metric == null) {
            String optionsStr = StringMan.getString(placeholders.getMetricsDouble(stack.getStore()).stream().map(f -> f.getName()).collect(Collectors.toList()));
            throw new IllegalArgumentException("Invalid metric: `" + input + "`. Options: " + optionsStr);
        }
        return metric;
    }

    @Binding
    public TreatyType TreatyType(String input) {
        return TreatyType.parse(input);
    }

    @Binding
    public TaxBracket bracket(@Me GuildDB db, String input) {
        if (input.matches("[0-9]+/[0-9]+")) {
            String[] split = input.split("/");
            int moneyRate = Integer.parseInt(split[0]);
            int rssRate = Integer.parseInt(split[1]);

            Auth auth = db.getAuth(AlliancePermission.TAX_BRACKETS);
            Map<Integer, TaxBracket> brackets = auth.getTaxBrackets();
            for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                if (bracket.moneyRate == moneyRate && bracket.rssRate == rssRate) {
                    return bracket;
                }
            }
            throw new IllegalArgumentException("No bracket found for `" + input + "`. Are you sure that tax rate exists ingame?");
        }
        if (!input.contains("tax_id=")) {
            throw new IllegalArgumentException("Invalid tax url `" + input + "`");
        }
        String[] split = input.split("=");
        int taxId = Integer.parseInt(split[split.length - 1]);
        Auth auth = db.getAuth(AlliancePermission.TAX_BRACKETS);
        Map<Integer, TaxBracket> brackets = auth.getTaxBrackets();
        TaxBracket bracket = brackets.get(taxId);
        if (bracket != null) return bracket;
        throw new IllegalArgumentException("Bracket " + taxId + " not found for alliance: " + db.getAlliance_id());
    }

//    @Binding(examples = "'Error 404' 'Arrgh' 45d")
//    @Me
//    public WarParser wars(@Me Guild guild, String coalition1, String coalition2, @Timediff long timediff) {
//        return WarParser.of(coalition1, coalition1, timediff);
//        return nation.get();
//    }
}