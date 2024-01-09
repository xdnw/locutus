package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.ICommandGroup;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.SimpleNationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.bindings.Operation;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.*;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.entities.grant.GrantTemplateManager;
import link.locutus.discord.db.entities.grant.TemplateTypes;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.newsletter.Newsletter;
import link.locutus.discord.db.entities.newsletter.NewsletterManager;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.mail.MailReceivedEvent;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AutoAuditType;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.util.task.mail.Mail;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PWBindings extends BindingHelper {

    @Binding(value = "A treaty between two alliances\n" +
            "Link two alliances, separated by a colon")
    public static Treaty treaty(String input) {
        String[] split = input.split("[:><]");
        if (split.length != 2) throw new IllegalArgumentException("Invalid input: `" + input + "` - must be two alliances separated by a comma");
        DBAlliance aa1 = alliance(split[0].trim());
        DBAlliance aa2 = alliance(split[1].trim());
        Treaty treaty = aa1.getTreaties().get(aa2.getId());
        if (treaty == null) {
            throw new IllegalArgumentException("No treaty found between " + aa1.getName() + " and " + aa2.getName());
        }
        return treaty;
    }

    @Binding(value = "A named game treasure")
    public static DBTreasure treasure(String input) {
        Map<String, DBTreasure> treasures = Locutus.imp().getNationDB().getTreasuresByName();
        DBTreasure treasure = treasures.get(input.toLowerCase(Locale.ROOT));
        if (treasure == null) {
            throw new IllegalArgumentException("No treasure found with name: `" + input + "`. Options " + StringMan.getString(treasures.keySet()));
        }
        return treasure;
    }

    public static DBBounty bounty(String input) {
        int id = PrimitiveBindings.Integer(input);
        DBBounty bounty = Locutus.imp().getWarDb().getBountyById(id);
        if (bounty == null) {
            throw new IllegalArgumentException("No bounty found with id: `" + id + "`");
        }
        return bounty;
    }

    @Binding(value = "The name of a nation attribute\n" +
            "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>", examples = {"color", "war_policy", "continent"})
    @NationAttributeCallable
    public ParametricCallable nationAttribute(NationPlaceholders placeholders, ValueStore store, String input) {
        List<ParametricCallable> options = placeholders.getParametricCallables();
        ParametricCallable metric = placeholders.get(input);
        if (metric == null) {
            throw new IllegalArgumentException("Invalid attribute: `" + input + "`. See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>");
        }
        return metric;
    }

    @Binding(value = "A discord slash command reference for the bot")
    public ICommand slashCommand(String input) {
        List<String> split = StringMan.split(input, ' ');
        CommandCallable command = Locutus.imp().getCommandManager().getV2().getCallable(split);
        if (command == null) throw new IllegalArgumentException("No command found for `" + input + "`");
        if (command instanceof ICommandGroup group) {
            throw new IllegalArgumentException("Command `" + input + "` is a group, not an endpoint. Please specify a sub command");
        }
        if (!(command instanceof ICommand)) throw new IllegalArgumentException("Command `" + input + "` is not a command endpoint");
        return (ICommand) command;
    }

    @Binding(value = """
            A map of city ranges to a list of beige reasons for defeating an enemy in war
            Priority is first to last (so put defaults at the bottom)""",
            examples = """
            c1-9:*
            c10+:INACTIVE,VACATION_MODE,APPLICANT""")
    public Map<CityRanges, Set<BeigeReason>> beigeReasonMap(@Me GuildDB db, String input) {
        input = input.replace("=", ":");

        Map<CityRanges, Set<BeigeReason>> result = new LinkedHashMap<>();
        String[] split = input.trim().split("\\r?\\n");
        if (split.length == 1) split = StringMan.split(input.trim(), ' ').toArray(new String[0]);
        for (String s : split) {
            String[] pair = s.split(":");
            if (pair.length != 2) throw new IllegalArgumentException("Invalid `CITY_RANGE:BEIGE_REASON` pair: `" + s + "`");
            CityRanges range = CityRanges.parse(pair[0]);
            List<BeigeReason> list = StringMan.parseEnumList(BeigeReason.class, pair[1]);
            result.put(range, new HashSet<>(list));
        }
        return result;
    }

    @Binding(value = "A comma separated list of beige reasons for defeating an enemy in war")
    public Set<BeigeReason> BeigeReasons(String input) {
        return emumSet(BeigeReason.class, input);
    }

    @Binding(value = "A comma separated list of attack success types")
    public Set<SuccessType> SuccessTypes(String input) {
        return emumSet(SuccessType.class, input);
    }

    @Binding(value = "A comma separated list of deposit types")
    public Set<DepositType> DepositTypes(String input) {
        return emumSet(DepositType.class, input);
    }

    @Binding(value = "A comma separated list of the status of a nation's loan")
    public Set<DBLoan.Status> LoanStatuses(String input) {
        return emumSet(DBLoan.Status.class, input);
    }

    @Binding(value = "The status of a nation's loan")
    public DBLoan.Status LoanStatus(String input) {
        return emum(DBLoan.Status.class, input);
    }

    @Binding(value = "The success type of an attack")
    public SuccessType SuccessType(String input) {
        return emum(SuccessType.class, input);
    }

    @Binding
    public static DBBan ban(String input) {
        if (MathMan.isInteger(input)) {
            DBBan ban = Locutus.imp().getNationDB().getBanById(Integer.parseInt(input));
            if (ban == null) {
                throw new IllegalArgumentException("No ban found for id `" + input + "`");
            }
            return ban;
        }
        DBNation nation = nation(null, input);
        List<DBBan> bans = Locutus.imp().getNationDB().getBansForNation(nation.getId());
        if (bans.isEmpty()) {
            throw new IllegalArgumentException("No bans found for nation `" + nation.getName() + "`");
        }
        return bans.get(0);
    }

    @Binding
    @GuildLoan
    public DBLoan loan(LoanManager manager, String input) {
        int id = PrimitiveBindings.Integer(input);
        DBLoan loan = manager.getLoanById(id);
        if (loan == null) {
            throw new IllegalArgumentException("No loan found for id `" + id + "`");
        }
        return loan;
    }

    @Binding(value = "A reason beiging and defeating an enemy in war")
    public BeigeReason BeigeReason(String input) {
        return emum(BeigeReason.class, input);
    }

    @Binding(value = "A category for a grant template")
    public TemplateTypes GrantTemplate(String input) {
        return emum(TemplateTypes.class, input);
    }

    @Binding(value = "The name of a created grant template")
    public AGrantTemplate AGrantTemplate(GrantTemplateManager manager, @Me GuildDB db, String input) {
        if (input.contains("/")) {
            input = input.substring(input.lastIndexOf('/') + 1);
        }
        String finalInput = input;
        Set<AGrantTemplate> found = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalInput));
        if (found.isEmpty()) throw new IllegalArgumentException("No grant template found for `" + input + "` see: " + CM.grant_template.list.cmd.toSlashMention());
        if (found.size() > 1) throw new IllegalArgumentException("Multiple grant templates found for `" + input + "`");
        return found.iterator().next();
    }

    @Binding(value = "An alert mode for the ENEMY_ALERT_CHANNEL when enemies leave beige")
    public EnemyAlertChannelMode EnemyAlertChannelMode(String input) {
        return emum(EnemyAlertChannelMode.class, input);
    }

    @Binding(value = "A city building type")
    public static Building getBuilding(String input) {
        Building building = Buildings.get(input);
        if (building == null) {
            throw new IllegalArgumentException("No building found for `" + input + "`\n" +
                    "Options: `" + Arrays.stream(Buildings.values()).map(Building::nameUpperUnd).collect(Collectors.joining("`, `")) + "`");
        }
        return building;
    }

    @Binding("An string matching for a nation's military buildings (MMR)\n" +
            "In the form `505X` where `X` is any military building")
    public MMRMatcher mmrMatcher(String input) {
        return new MMRMatcher(input);
    }

    @Binding(value = """
            A map of nation filters to MMR
            Use X for any military building
            All nation filters are supported (e.g. roles)
            Priority is first to last (so put defaults at the bottom)""",
            examples = """
            #cities<10:505X
            #cities>=10:0250""")
    public Map<NationFilter, MMRMatcher> mmrMatcherMap(@Me GuildDB db, String input, @Default @Me User author, @Default @Me DBNation nation) {
        Map<NationFilter, MMRMatcher> filterToMMR = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            String[] split = line.split("[:]");
            if (split.length != 2) continue;

            String filterStr = split[0].trim();

            boolean containsNation = false;
            for (String arg : filterStr.split(",")) {
                if (!arg.startsWith("#")) containsNation = true;
                if (arg.contains("tax_id=")) containsNation = true;
                if (arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:")) containsNation = true;
            }
            if (!containsNation) filterStr += ",*";
            NationFilterString filter = new NationFilterString(filterStr, db.getGuild(), author, nation);
            MMRMatcher mmr = new MMRMatcher(split[1]);
            filterToMMR.put(filter, mmr);
        }

        return filterToMMR;
    }

    @Binding(value = "Auto assign roles based on conditions\n" +
            "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>\n" +
            "Accepts a list of filters to a role.\n" +
            "In the form:\n" +
            "```\n" +
            "#cities<10:@someRole\n" +
            "#cities>=10:@otherRole\n" +
            "```\n" +
            "Use `*` as the filter to match all nations.\n" +
            "Only alliance members can be given role")
    public Map<NationFilter, Role> conditionalRole(@Me GuildDB db, String input, @Default @Me User author, @Default @Me DBNation nation) {
        System.out.println("Parse conditional roles " + input);
        Map<NationFilter, Role> filterToRole = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            int index = line.lastIndexOf(":");
            if (index == -1) {
                continue;
            }
            String part1 = line.substring(0, index);
            String part2 = line.substring(index + 1);
            String filterStr = part1.trim();
            boolean containsNation = false;
            NationFilterString filter = new NationFilterString(filterStr, db.getGuild(), author, nation);
            Role role = DiscordBindings.role(db.getGuild(), part2);
            filterToRole.put(filter, role);
        }
        return filterToRole;
    }

    @Binding(value = """
            A map of nation filters to tax rates
            All nation filters are supported (e.g. roles)
            Priority is first to last (so put defaults at the bottom)""",
            examples = """
            #cities<10:100/100
            #cities>=10:25/25""")
    public Map<NationFilter, TaxRate> taxRateMap(@Default @Me User author, @Default @Me DBNation nation, @Me GuildDB db, String input) {
        Map<NationFilter, TaxRate> filterToTaxRate = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            String[] split = line.split("[:]");
            if (split.length != 2) continue;

            String filterStr = split[0].trim();

            boolean containsNation = false;
            for (String arg : filterStr.split(",")) {
                if (!arg.startsWith("#")) containsNation = true;
                if (arg.contains("tax_id=")) containsNation = true;
                if (arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:")) containsNation = true;
            }
            if (!containsNation) filterStr += ",*";
            NationFilterString filter = new NationFilterString(filterStr, db.getGuild(), author, nation);
            TaxRate rate = new TaxRate(split[1]);
            filterToTaxRate.put(filter, rate);
        }
        if (filterToTaxRate.isEmpty()) throw new IllegalArgumentException("No valid nation filters provided");

        return filterToTaxRate;
    }

    @Binding(value = """
            A map of nation filters to tax ids
            All nation filters are supported (e.g. roles)
            Priority is first to last (so put defaults at the bottom)""",
    examples = """
            #cities<10:1
            #cities>=10:2""")
    public Map<NationFilter, Integer> taxIdMap(@Default @Me User author, @Default @Me DBNation nation, @Me GuildDB db, String input) {
        Map<NationFilter, Integer> filterToBracket = new LinkedHashMap<>();
        for (String line : input.split("[\n|;]")) {
            String[] split = line.split("[:]");
            if (split.length != 2) continue;

            String filterStr = split[0].trim();
            NationFilterString filter = new NationFilterString(filterStr, db.getGuild(), author, nation);
            int bracket = Integer.parseInt(split[1]);
            filterToBracket.put(filter, bracket);
        }
        if (filterToBracket.isEmpty()) throw new IllegalArgumentException("No valid nation filters provided");

        return filterToBracket;
    }

    @Binding(value = "City build json or url", examples = {"city/id=371923", "{city-json}", "city/id=1{json-modifiers}"})
    public CityBuild city(@Default @Me DBNation nation, @TextArea String input) {
        // {city X Nation}
        int index = input.indexOf('{');
        Integer cityId = null;
        String json;
        if (index == -1) {
            json = null;
        } else {
            if (input.startsWith("{city")) {
                // in the form {city 1234}
                index = input.indexOf('}');
                if (index == -1) throw new IllegalArgumentException("No closing bracket found");
                // parse number 1234
                int cityIndex = input.contains(" ") ? Integer.parseInt(input.substring(6, index)) - 1 : 0;
                Set<Map.Entry<Integer, JavaCity>> cities = nation.getCityMap(true, false).entrySet();
                int i = 0;
                for (Map.Entry<Integer, JavaCity> entry : cities) {
                    if (++i == cityIndex) {
                        CityBuild build = entry.getValue().toCityBuild();
                        build.setCity_id(entry.getKey());
                        return build;
                    }
                }
                throw new IllegalArgumentException("City not found: " + index + " for natiion " + nation.getName());
            }
            json = input.substring(index);
            input = input.substring(0, index);
        }
        CityBuild build = null;
        if (input.contains("city/id=")) {
            cityId = Integer.parseInt(input.split("=")[1]);
            DBCity cityEntry = Locutus.imp().getNationDB().getCitiesV3ByCityId(cityId);
            if (cityEntry == null) throw new IllegalArgumentException("No city found in cache for " + cityId);
            int nationId = cityEntry.getNationId();
            DBNation nation2 = DBNation.getById(nationId);
            if (nation2 != null) nation = nation2;
            build = cityEntry.toJavaCity(nation == null ? f -> false : nation::hasProject).toCityBuild();
        }
        if (json != null && !json.isBlank()) {
            for (Building building : Buildings.values()) {
                json = json.replace(building.name(), building.nameSnakeCase());
            }
            CityBuild build2 = CityBuild.of(json, true);
            if (build != null) {
                json = build2.toString().replace("}", "") + "," + build.toString().replace("{", "");
                build = CityBuild.of(json, true);
            } else {
                build = build2;
            }
        }
        if (build != null && cityId != null) {
            build.setCity_id(cityId);
        }
        return build;
    }

    @Binding(value = "City url", examples = {"city/id=371923"})
    public static DBCity cityUrl(String input) {
        int cityId;
        if (input.contains("city/id=")) {
            cityId = Integer.parseInt(input.split("=")[1]);
        } else if (MathMan.isInteger(input)) {
            cityId = Integer.parseInt(input);
        } else {
            throw new IllegalArgumentException("Not a valid city url: `" + input + "`");
        }
        DBCity cityEntry = Locutus.imp().getNationDB().getCitiesV3ByCityId(cityId);
        if (cityEntry == null) throw new IllegalArgumentException("No city found in cache for " + cityId);
        return cityEntry;
    }

    @Binding(examples = ("#grant #city=1"), value = "A DepositType optionally with a value and a city tag")
    public static DepositType.DepositTypeInfo DepositTypeInfo(String input) {
        DepositType type = null;
        long value = 0;
        long city = 0;
        boolean ignore = false;
        for (String arg : input.split(" ")) {
            if (arg.equalsIgnoreCase("#ignore")) {
                ignore = true;
                continue;
            }
            if (arg.startsWith("#")) arg = arg.substring(1);
            String[] split = arg.split("[=|:]");
            String key = split[0];
            DepositType tmp = StringMan.parseUpper(DepositType.class, key.toUpperCase(Locale.ROOT));
            if (type == null || (type != tmp)) {
                type = tmp;
            } else {
                throw new IllegalArgumentException("Invalid deposit type (duplicate): `" + input + "`");
            }
            if (split.length == 2) {
                long num = Long.valueOf(split[1]);
                if (tmp == DepositType.CITY) {
                    city = num;
                } else {
                    value = num;
                }
            } else if (split.length != 1) {
                throw new IllegalArgumentException("Invalid deposit type (value): `" + input + "`");
            }
        }
        if (type == null) {
            if (ignore) {
                type = DepositType.IGNORE;
            } else {
                throw new IllegalArgumentException("Invalid deposit type (empty): `" + input + "`");
            }
        }
        if (type == DepositType.CITY) {
            value = city;
            city = 0;
        }
        return new DepositType.DepositTypeInfo(type, value, city, ignore);
    }

    @Binding(value = "A range of city counts (inclusive)", examples = {"c1-10", "c11+"})
    public CityRanges CityRanges(String input) {
        return CityRanges.parse(input);
    }

    @Binding(value = "A war id or url", examples = {"https://politicsandwar.com/nation/war/timeline/war=1234"})
    public DBWar war(String arg0) {
        if (arg0.contains("/war=")) {
            arg0 = arg0.split("=")[1];
        }
        if (!MathMan.isInteger(arg0)) {
            throw new IllegalArgumentException("Not a valid war number: `" + arg0 + "`");
        }
        int warId = Integer.parseInt(arg0);
        DBWar war = Locutus.imp().getWarDb().getWar(warId);
        if (war == null) throw new IllegalArgumentException("No war founds for id: `" + warId + "`");
        return war;
    }

    @Binding(value = "nation id, name or url", examples = {"Borg", "<@664156861033086987>", "Danzek", "189573", "https://politicsandwar.com/nation/id=189573"})
    public static DBNation nation(@Default @Me User selfUser, String input) {
        DBNation nation = DiscordUtil.parseNation(input);
        if (nation == null) {
            if (selfUser != null && (input.equalsIgnoreCase("%user%") || input.equalsIgnoreCase("{usermention}"))) {
                nation = DiscordUtil.getNation(selfUser);
            }
            if (nation == null) {
                String error = "No such nation: `" + input + "`";
                if (input.contains(",")) {
                    throw new IllegalArgumentException(error + " (Multiple nations are not accepted for this argument)");
                }
                throw new IllegalArgumentException(error);
            }
        }
        return nation;
    }

    @Binding(value = "4 whole numbers representing barracks,factory,hangar,drydock", examples = {"5553", "0/2/5/0"})
    public MMRInt mmrInt(String input) {
        return MMRInt.fromString(input);
    }

    @Binding(value = "4 decimal numbers representing barracks, factory, hangar, drydock", examples = {"0.0/2.0/5.0/0.0", "5553"})
    public MMRDouble mmrDouble(String input) {
        return MMRDouble.fromString(input);
    }

    public static NationOrAlliance nationOrAlliance(String input) {
        return nationOrAlliance(null, input);
    }

    @Binding(value = "A nation or alliance name, url or id. Prefix with `AA:` or `nation:` to avoid ambiguity if there exists both by the same name or id", examples = {"Borg", "https://politicsandwar.com/alliance/id=1234", "aa:1234"})
    public static NationOrAlliance nationOrAlliance(ParameterData data, String input) {
        return nationOrAlliance(data, input, false);
    }

    public static NationOrAlliance nationOrAlliance(ParameterData data, String input, boolean forceAllowDeleted) {
        String lower = input.toLowerCase();
        if (lower.startsWith("aa:")) {
            return alliance(data, input.split(":", 2)[1]);
        }
        if (lower.contains("alliance/id=")) {
            return alliance(data, input);
        }
        DBNation nation = DiscordUtil.parseNation(input, forceAllowDeleted || (data != null && data.getAnnotation(AllowDeleted.class) != null));
        if (nation == null) {
            return alliance(data, input);
        }
        return nation;
    }

    @Binding
    public NationPlaceholders placeholders() {
        return Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
    }

    @Binding
    public AlliancePlaceholders aa_placeholders() {
        return Locutus.imp().getCommandManager().getV2().getAlliancePlaceholders();
    }

    @Binding(examples = "{nation}", value = "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
    public static NationPlaceholder placeholder(ValueStore store, PermissionHandler permisser, String input) {
        CommandManager2 v2 = Locutus.imp().getCommandManager().getV2();
        NationPlaceholders placeholders = v2.getNationPlaceholders();
        ParametricCallable ph = placeholders.get(input);
        ph.validatePermissions(store, permisser);
        TypedFunction<DBNation, ?> entry = placeholders.formatRecursively(store, input, null, 0, true);
        return new SimpleNationPlaceholder(ph.getPrimaryCommandId(), entry.getType(), entry);
    }

    @Binding(examples = {"25/25"}, value = "A tax rate in the form of `money/rss`")
    public TaxRate taxRate(String input) {
        if (!input.contains("/")) throw new IllegalArgumentException("Tax rate must be in the form: 0/0");
        String[] split = input.split("/");
        int moneyRate = Integer.parseInt(split[0]);
        int rssRate = Integer.parseInt(split[1]);
        return new TaxRate(moneyRate, rssRate);
    }

    public static NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuildOrTaxId(String input) {
        return nationOrAllianceOrGuildOrTaxId(null, input);
    }

    @Binding(examples = {"Borg", "alliance/id=7452", "647252780817448972", "tax_id=1234"}, value = "A nation or alliance name, url or id, or a guild id, or a tax id or url")
    public static NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuildOrTaxId(ParameterData data, String input) {
        return nationOrAllianceOrGuildOrTaxId(data, input, true);
    }

    public static NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuildOrTaxId(String input, boolean includeTaxId) {
        return nationOrAllianceOrGuildOrTaxId(null, input, includeTaxId);
    }
    public static NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuildOrTaxId(ParameterData data, String input, boolean includeTaxId) {
        try {
            return nationOrAlliance(data, input);
        } catch (IllegalArgumentException ignore) {
            if (includeTaxId && !input.startsWith("#") && input.contains("tax_id")) {
                int taxId = PnwUtil.parseTaxId(input);
                return new TaxBracket(taxId, -1, "", 0, 0, 0L);
            }
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
                    if (db == null) {
                        if (data != null && data.getAnnotation(AllowDeleted.class) != null) {
                            throw new IllegalArgumentException("Not connected to guild: " + id + " (deleted guilds are not currently supported)");
                        }
                        throw new IllegalArgumentException("Not connected to guild: " + id);
                    }
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

    public static NationOrAllianceOrGuild nationOrAllianceOrGuild(String input) {
        return nationOrAllianceOrGuild(null, input);
    }

    @Binding(examples = {"Borg", "alliance/id=7452", "647252780817448972"}, value = "A nation or alliance name, url or id, or a guild id")
    public static NationOrAllianceOrGuild nationOrAllianceOrGuild(ParameterData data, String input) {
        return (NationOrAllianceOrGuild) nationOrAllianceOrGuildOrTaxId(data, input, false);
    }

    public static DBAlliance alliance(String input) {
        return alliance(null, input);
    }

    @Binding(examples = {"'Error 404'", "7413", "https://politicsandwar.com/alliance/id=7413"}, value = "An alliance name id or url")
    public static DBAlliance alliance(ParameterData data, String input) {
        Integer aaId = PnwUtil.parseAllianceId(input);
        if (aaId == null) throw new IllegalArgumentException("Invalid alliance: " + input);
        return DBAlliance.getOrCreate(aaId);
    }

    @Binding(value = "A comma separated list of audit types")
    public Set<IACheckup.AuditType> auditTypes(ValueStore store, String input) {
        Set<IACheckup.AuditType> audits = Locutus.cmd().getV2().getPlaceholders().get(IACheckup.AuditType.class).parseSet(store, input);
        if (audits == null || audits.isEmpty()) {
            throw new IllegalArgumentException("No audit types found in: " + input + ". Options: " + StringMan.getString(IACheckup.AuditType.values()));
        }
        return audits;
    }

    @Binding(value = "An audit type")
    public static IACheckup.AuditType auditType(String input) {
        return emum(IACheckup.AuditType.class, input);
    }

    @Binding(value = "An in-game  color bloc")
    public static NationColor NationColor(String input) {
        return emum(NationColor.class, input);
    }

    @Binding(value = "A comma separated list of auto audit types")
    public Set<AutoAuditType> autoAuditType(String input) {
        return emumSet(AutoAuditType.class, input);
    }

    @Binding(value = "A comma separated list of continents, or `*`")
    public static Set<Continent> continentTypes(ValueStore store, String input) {
        Set<Continent> result = Locutus.cmd().getV2().getPlaceholders().get(Continent.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("No projects found in: " + input + ". Options: " + StringMan.getString(Continent.values));
        }
        return result;
    }

    @Binding(value = "A comma separated list of spy operation types")
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


    @Binding(value = "A comma separated list of alliance metrics")
    public Set<AllianceMetric> metrics(String input) {
        Set<AllianceMetric> metrics = new HashSet<>();
        for (String type : input.split(",")) {
            AllianceMetric arg = StringMan.parseUpper(AllianceMetric.class, type);
            metrics.add(arg);
        }
        return metrics;
    }

    @Binding(value = "A comma separated list of nation projects")
    public static Set<Project> projects(ValueStore store, String input) {
        Set<Project> result = Locutus.cmd().getV2().getPlaceholders().get(Project.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("No projects found in: " + input + ". Options: " + StringMan.getString(Projects.values));
        }
        return result;
    }

    @Binding(value = "A comma separated list of building types")
    public Set<Building> buildings(ValueStore store, String input) {
        Set<Building> result = Locutus.cmd().getV2().getPlaceholders().get(Building.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("No projects found in: " + input + ". Options: " + StringMan.getString(Buildings.values()));
        }
        return result;
    }

    @Binding(examples = "borg,AA:Cataclysm,#position>1", value = "A comma separated list of nations, alliances and filters")
    public static Set<DBNation> nations(ParameterData data, @Default @Me Guild guild, String input, @Default @Me User author, @Default @Me DBNation me) {
        return nations(data, guild, input, false, author, me);
    }

    public static Set<DBNation> nations(ParameterData data, @Default @Me Guild guild, String input, boolean forceAllowDeleted, @Default @Me User user, @Default @Me DBNation nation) {
        Set<DBNation> nations = DiscordUtil.parseNations(guild, user, nation, input, true, forceAllowDeleted || (data != null && data.getAnnotation(AllowDeleted.class) != null));
        if (nations.isEmpty() && (data == null || data.getAnnotation(AllowEmpty.class) == null)) {
            System.out.println("Data " + data);
            throw new IllegalArgumentException("No nations found matching: `" + input + "`");
        }
        return nations;
    }

    @Binding(examples = "borg,AA:Cataclysm,#position>1", value = "A comma separated list of nations, alliances and filters")
    public NationList nationList(ParameterData data, @Default @Me Guild guild, String input, @Default @Me User author, @Default @Me DBNation me) {
        return new SimpleNationList(nations(data, guild, input, author, me)).setFilter(input);
    }

    @Binding(examples = "#position>1,#cities<=5", value = "A comma separated list of filters (can include nations and alliances)")
    public NationFilter nationFilter(@Default @Me User author, @Default @Me DBNation nation, @Default @Me Guild guild, String input) {
        return new NationFilterString(input, guild, author, nation);
    }

    @Binding(examples = "score,soldiers", value = "A comma separated list of numeric nation attributes")
    public Set<NationAttributeDouble> nationMetricDoubles(ValueStore store, String input) {
        Set<NationAttributeDouble> metrics = new LinkedHashSet<>();
        for (String arg : StringMan.split(input, ',')) {
            metrics.add(nationMetricDouble(store, arg));
        }
        return metrics;
    }

    @Binding(examples = "warpolicy,color", value = "A comma separated list of nation attributes")
    public Set<NationAttribute> nationMetrics(ValueStore store, String input) {
        Set<NationAttribute> metrics = new LinkedHashSet<>();
        for (String arg : StringMan.split(input, ',')) {
            metrics.add(nationMetric(store, arg));
        }
        return metrics;
    }

    @Binding(examples = "borg,AA:Cataclysm", value = "A comma separated list of nations and alliances")
    public static Set<NationOrAlliance> nationOrAlliance(ParameterData data, @Default @Me Guild guild, String input, @Default @Me User author, @Default @Me DBNation me) {
        Set<NationOrAlliance> result = nationOrAlliance(data, guild, input, false, author, me);
        boolean allowDeleted = data != null && data.getAnnotation(AllowDeleted.class) != null;
        if (!allowDeleted) {
            result.removeIf(n -> !n.isValid());
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No nations or alliances found matching: `" + input + "`");
        }
        return result;
    }

    public static Set<NationOrAlliance> nationOrAlliance(ParameterData data, @Default @Me Guild guild, String input, boolean forceAllowDeleted, @Default @Me User author, @Default @Me DBNation me) {
        Placeholders<NationOrAlliance> placeholders = Locutus.cmd().getV2().getPlaceholders().get(NationOrAlliance.class);
        return placeholders.parseSet(guild, author, me, input);
//        Set<NationOrAlliance> result = new LinkedHashSet<>();
//        for (String group : input.split("\\|+")) {
//            List<String> remainder = new ArrayList<>();
//            if (!group.contains("#")) {
//                List<String> args = StringMan.split(group, ',');
//
//                GuildDB db = Locutus.imp().getGuildDB(guild);
//                for (String arg : args) {
//                    try {
//                        DBAlliance aa = alliance(data, arg);
//                        if (forceAllowDeleted || (data != null && data.getAnnotation(AllowDeleted.class) != null) || aa.exists()) {
//                            result.add(aa);
//                            continue;
//                        }
//                    } catch (IllegalArgumentException ignore) {
//                    }
//                    if (db != null) {
//                        if (arg.charAt(0) == '~') arg = arg.substring(1);
//                        Set<Integer> coalition = db.getCoalition(arg);
//                        if (!coalition.isEmpty()) {
//                            result.addAll(coalition.stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet()));
//                            continue;
//                        }
//                    }
//                    remainder.add(arg);
//                }
//            } else {
//                remainder.add(group);
//            }
//            if (!remainder.isEmpty()) {
//                result.addAll(nations(data, guild, StringMan.join(remainder, ","), forceAllowDeleted, author, me));
//            }
//        }
//        if (result.isEmpty()) throw new IllegalArgumentException("Invalid nations or alliances: `" + input + "`");
//        return result;
    }

    @Binding(examples = "borg,AA:Cataclysm,647252780817448972", value = "A comma separated list of nations, alliances and guild ids")
    public Set<NationOrAllianceOrGuild> nationOrAllianceOrGuild(ParameterData data, @Default @Me Guild guild, String input, @Default @Me User author, @Default @Me DBNation me) {
        return (Set) nationOrAllianceOrGuildOrTaxId(data, guild, input, false, author, me);
    }

    @Binding(examples = "borg,AA:Cataclysm,647252780817448972", value = "A comma separated list of nations, alliances, guild ids and tax ids or urls")
    public Set<NationOrAllianceOrGuildOrTaxid> nationOrAllianceOrGuildOrTaxId(ParameterData data, @Default @Me Guild guild, String input, @Default @Me User author, @Default @Me DBNation me) {
        return nationOrAllianceOrGuildOrTaxId(data, guild, input, true, author, me);
    }

    public static Set<NationOrAllianceOrGuildOrTaxid> nationOrAllianceOrGuildOrTaxId(ParameterData data, @Default @Me Guild guild, String input, boolean includeTaxId, @Default @Me User author, @Default @Me DBNation me) {
        List<String> args = StringMan.split(input, ',');
        Set<NationOrAllianceOrGuildOrTaxid> result = new LinkedHashSet<>();
        List<String> remainder = new ArrayList<>();
        outer:
        for (String arg : args) {
            arg = arg.trim();
            if (includeTaxId && !arg.startsWith("#") && arg.contains("tax_id")) {
                int taxId = PnwUtil.parseTaxId(arg);
                TaxBracket bracket = new TaxBracket(taxId, -1, "", 0, 0, 0L);
                result.add(bracket);
                continue;
            }
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

            try {
                DBAlliance aa = alliance(data, arg);
                if (aa.exists()) {
                    result.add(aa);
                    continue;
                }
            } catch (IllegalArgumentException ignore) {}
            GuildDB db = guild == null ? null : Locutus.imp().getGuildDB(guild);
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
        if (!remainder.isEmpty()) {
            result.addAll(nations(data, guild, StringMan.join(remainder, ","), author, me));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Invalid nations or alliances: " + input);
        return result;
    }

    public static Set<DBAlliance> alliances(@Default @Me Guild guild, String input, @Default @Me User author, @Default @Me DBNation me) {
        Set<DBAlliance> alliances = Locutus.cmd().getV2().getAlliancePlaceholders().parseSet(guild, author, me, input);
        if (alliances.isEmpty()) throw new IllegalArgumentException("No alliances found for: `" + input + "`");
        return alliances;
    }

    @Binding(examples = "Cataclysm,790", value = "A comma separated list of alliances")
    public Set<DBAlliance> alliances(AlliancePlaceholders placeholders, ValueStore store, String input) {
        return placeholders.parseSet(store, input);
    }
//
//    @Binding(examples = "Cataclysm,790", value = "A comma separated list of alliances")
//    public static Set<DBAlliance> alliances(ValueStore store, @Default @Me Guild guild, String input, AlliancePlaceholders placeholders) {
//
//        // make function parsing non hash
//
//        for (String arg : StringMan.split(input, ',')) {
//            arg = arg.trim();
//            if (arg.isEmpty()) {
//                throw new IllegalArgumentException("Empty argument. Did you use an extra comma? (input: `" + input + "`)");
//            }
//            char char0 = arg.charAt(0);
//            if (char0 == '#') {
//                Predicate<DBAlliance> filter = placeholders.getFilter(store, arg.substring(1));
//            }
//        }
//    }

    @Binding(examples = "ACTIVE,EXPIRED", value = "A comma separated list of war statuses")
    public Set<WarStatus> WarStatuses(String input) {
        Set<WarStatus> result = new HashSet<>();
        for (String s : input.split(",")) {
            result.add(WarStatus.parse(s));
        }
        return result;
    }

    @Binding(examples = "ATTRITION,RAID", value = "A comma separated list of war declaration types")
    public Set<WarType> WarType(String input) {
        return emumSet(WarType.class, input);
    }

    @Binding(examples = "GROUND,VICTORY", value = "A comma separated list of attack types")
    public static Set<AttackType> AttackTypes(ValueStore store, String input) {
        Set<AttackType> result = Locutus.cmd().getV2().getPlaceholders().get(AttackType.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("No projects found in: " + input + ". Options: " + StringMan.getString(AttackType.values));
        }
        return result;
    }


    @Binding(examples = "SOLDIER,TANK,AIRCRAFT,SHIP,MISSILE,NUKE", value = "A comma separated list of military units")
    public Set<MilitaryUnit> MilitaryUnits(ValueStore store, String input) {
        Set<MilitaryUnit> result = Locutus.cmd().getV2().getPlaceholders().get(MilitaryUnit.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("No projects found in: " + input + ". Options: " + StringMan.getString(MilitaryUnit.values));
        }
        return result;
    }

    @Binding(examples = {"aluminum", "money", "`*`", "manu", "raws", "!food"}, value = "A comma separated list of resource types")
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
    @Binding(examples = {"{money=1.2,food=6}", "food 5,money 3", "5f 3$ 10.5c", "$53"}, value = "A comma separated list of resources and their amounts, which will be restricted by an alliance's account balance")
    public Map<ResourceType, Double> resourcesAA(String resources) {
        return resources(resources);
    }

    @NationDepositLimit
    @Binding(examples = {"{money=1.2,food=6}", "food 5,money 3", "5f 3$ 10.5c", "$53"}, value = "A comma separated list of resources and their amounts, which will be restricted by an nations's account balance")
    public Map<ResourceType, Double> resourcesNation(String resources) {
        return resources(resources);
    }

    @Binding(examples = {"{money=1.2,food=6}", "food 5,money 3", "5f 3$ 10.5c", "$53", "{food=1}*1.5"}, value = "A comma separated list of resources and their amounts")
    public Map<ResourceType, Double> resources(String resources) {
        Map<ResourceType, Double> map = PnwUtil.parseResources(resources);
        if (map == null) throw new IllegalArgumentException("Invalid resources: " + resources);
        return map;
    }

    @Binding(examples = {"{soldiers=12,tanks=56}"}, value = "A comma separated list of units and their amounts")
    public Map<MilitaryUnit, Long> units(String input) {
        Map<MilitaryUnit, Long> map = PnwUtil.parseUnits(input);
        if (map == null) throw new IllegalArgumentException("Invalid units: " + input + ". Valid types: " + StringMan.getString(MilitaryUnit.values()) + ". In the form: `{SOLDIERS=1234,TANKS=5678}`");
        return map;
    }

    @Binding(examples = {"money", "aluminum"}, value = "The name of a resource")
    public static ResourceType resource(String resource) {
        return ResourceType.parse(resource);
    }

    @Binding(value = "A note to use for a bank transfer")
    public static DepositType DepositType(String input) {
        if (input.startsWith("#")) input = input.substring(1);
        return StringMan.parseUpper(DepositType.class, input.toUpperCase(Locale.ROOT));
    }

    @Binding(value = "The mode for escrowing funds for a transfer, such as when a receiver is blockaded")
    public static EscrowMode EscrowMode(String input) {
        return emum(EscrowMode.class, input);
    }

    @Binding(value = "A war declaration type")
    public WarType warType(String warType) {
        return WarType.parse(warType);
    }

    @Binding
    @Me
    public DBNation nationProvided(@Default @Me User user) {
        if (user == null) {
            throw new IllegalStateException("No user provided in command locals");
        }
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) throw new IllegalArgumentException("Please use " + CM.register.cmd.toSlashMention() + "");
        return nation;
    }

    @Binding
    public MailReceivedEvent MailReceivedEvent() {
        throw new IllegalStateException("No mail event provided in command locals");
    }

    @Binding
    public Mail Mail() {
        throw new IllegalStateException("No mail provided in command locals");
    }

    @Binding
    @Me
    public IMessageIO io() {
        throw new IllegalArgumentException("No channel io binding found");
    }

    @Binding
    @Me
    public OffshoreInstance offshore(@Me GuildDB db) {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) throw new IllegalArgumentException("No offshore is set. See: " + CM.offshore.add.cmd.toSlashMention());
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
        return nation.getNetDeposits(db, false);
    }

    @Binding
    @Me
    public Rank rank(@Me DBNation nation) {
        return Rank.byId(nation.getPosition());
    }

    @Binding(value = "A war status")
    public WarStatus status(String input) {
        return WarStatus.parse(input);
    }

    @Binding(value = "An attack type")
    public static AttackType attackType(String input) {
        return emum(AttackType.class, input);
    }

    @Binding(value = "Mode for automatically giving discord roles")
    public GuildDB.AutoRoleOption roleOption(String input) {
        return emum(GuildDB.AutoRoleOption.class, input);
    }

    @Binding(value = "Mode for automatically giving discord nicknames")
    public GuildDB.AutoNickOption nickOption(String input) {
        return emum(GuildDB.AutoNickOption.class, input);
    }

    @Binding(value = "A war policy")
    public WarPolicy warPolicy(String input) {
        return emum(WarPolicy.class, input);
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
    public ReportManager ReportManager() {
        return Locutus.imp().getNationDB().getReportManager();
    }

    @Binding
    public GrantTemplateManager grantTemplateManager(@Me GuildDB db) {
        return db.getGrantTemplateManager();
    }

    @Binding
    public LoanManager loanManager() {
        return Locutus.imp().getNationDB().getLoanManager();
    }

    @Binding
    @Me
    public GuildDB guildDB(@Me Guild guild) {
        return Locutus.imp().getGuildDB(guild);
    }

    @Binding(examples = "647252780817448972", value = "A discord guild id. See: <https://en.wikipedia.org/wiki/Template:Discord_server#Getting_Guild_ID>")
    public static GuildDB guild(long guildId) {
        GuildDB guild = Locutus.imp().getGuildDB(guildId);
        if (guild == null) throw new IllegalArgumentException("No guild found for: " + guildId);
        return guild;
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
    public link.locutus.discord.db.TradeDB tradeDB() {
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
        return nation.getAuth(true);
    }

    @Binding(value = "The reason for a nation's loot being known")
    public NationLootType lootType(String input) {
        return emum(NationLootType.class, input);
    }

    @Binding
    public ReportManager.ReportType reportType(String input) {
        return emum(ReportManager.ReportType.class, input);
    }

    @Binding(value = "One of the default in-game position levels")
    public Rank rank(String rank) {
        return emum(Rank.class, rank);
    }

    @Binding(value = "An in-game position")
    public static DBAlliancePosition position(@Me GuildDB db, @Default @Me DBNation nation, String name) {
        AllianceList alliances = db.getAllianceList();
        if (alliances == null || alliances.isEmpty()) throw new IllegalArgumentException("No alliances are set. See: " + CM.settings.info.cmd.toSlashMention() + " with key " + GuildKey.ALLIANCE_ID.name());

        String[] split = name.split(":", 2);
        Integer aaId = split.length == 2 ? PnwUtil.parseAllianceId(split[0]) : null;
        String positionName = split[split.length - 1];

        if (aaId != null && !alliances.contains(aaId)) throw new IllegalArgumentException("Alliance " + aaId + " is not in the list of alliances registered to this guild: " + StringMan.getString(alliances.getIds()));
        Set<Integer> aaIds = new LinkedHashSet<>();
        if (aaId != null) aaIds.add(aaId);
        else {
            if (nation != null && alliances.contains(nation.getAlliance_id())) aaIds.add(nation.getAlliance_id());
            aaIds.addAll(alliances.getIds());
        }
        DBAlliancePosition result = null;
        for (int allianceId : aaIds) {
            result = DBAlliancePosition.parse(positionName, allianceId, true);
        }
        if (result == null) {
            throw new IllegalArgumentException("Unknown position: `" + name +
                    "`. Options: " + StringMan.getString(alliances.getPositions().stream().map(DBAlliancePosition::getQualifiedName).collect(Collectors.toList()))
                    + " / Special: remove/applicant");
        }
        return result;
    }

    @Binding(value = "In-game permission in an alliance")
    public AlliancePermission alliancePermission(String name) {
        return emum(AlliancePermission.class, name);
    }

    @Binding(value = "A comma separated list of In-game permission in an alliance")
    public Set<AlliancePermission> alliancePermissions(String input) {
        return emumSet(AlliancePermission.class, input);
    }

    @Binding(value = "Locutus guild settings")
    public static GuildSetting key(String input) {
        input = input.replaceAll("_", " ").toLowerCase();
        GuildSetting[] constants = GuildKey.values();
        for (GuildSetting constant : constants) {
            String name = constant.name().replaceAll("_", " ").toLowerCase();
            if (name.equals(input)) return constant;
        }
        List<String> options = Arrays.asList(constants).stream().map(GuildSetting::name).collect(Collectors.toList());
        throw new IllegalArgumentException("Invalid category: `" + input + "`. Options: " + StringMan.getString(options));
    }

    @Binding(value = "Types of users to clear roles of")
    public UnsortedCommands.ClearRolesEnum clearRolesEnum(String input) {
        return emum(UnsortedCommands.ClearRolesEnum.class, input);
    }

    @Binding(value = "Bank transaction flow type (internal, withdrawal, depost)")
    public static FlowType FlowType(String input) {
        return emum(FlowType.class, input);
    }

    @Binding(examples = {"@role", "672238503119028224", "roleName"}, value = "A discord role name, mention or id")
    public Roles role(String role) {
        return emum(Roles.class, role);
    }

    @Binding(value = "Military unit name")
    public static MilitaryUnit unit(String unit) {
        return emum(MilitaryUnit.class, unit);
    }

    @Binding(value = "Continent name")
    public static Continent continent(String input) {
        return emum(Continent.class, input);
    }

    @Binding(value = "Math comparison operation")
    public Operation op(String input) {
        return emum(Operation.class, input);
    }

    @Binding(value = "Spy safety level")
    public SpyCount.Safety safety(String input) {
        return emum(SpyCount.Safety.class, input);
    }

    @Binding(value = "One of the default Locutus coalition names")
    public Coalition coalition(String input) {
        return emum(Coalition.class, input);
    }

    @Binding(value = "A name for a default or custom Locutus coalition")
    @GuildCoalition
    public String guildCoalition(@Me GuildDB db, String input) {
        input = input.toLowerCase();
        Set<String> coalitions = db.getCoalitionNames();
        for (Coalition value : Coalition.values()) coalitions.add(value.name().toLowerCase());
        if (!coalitions.contains(input)) throw new IllegalArgumentException(
                "No coalition found matching: `" + input +
                        "`. Options: " + StringMan.getString(coalitions) + "\n" +
                        "Create it via " + CM.coalition.create.cmd.toSlashMention()
        );
        return input;
    }

    @Binding
    public AllianceList allianceList(ParameterData param, @Default @Me User user, @Me GuildDB db) {
        AllianceList list = db.getAllianceList();
        if (list == null) {
            throw new IllegalArgumentException("This guild has no registered alliance. See " + CM.settings.info.cmd.toSlashMention() + " with key " + GuildKey.ALLIANCE_ID.name());
        }
        RolePermission perms = param.getAnnotation(RolePermission.class);
        if (perms != null) {
            if (user != null) {
                Set<Integer> allowedIds = new HashSet<>();
                for (int aaId : list.getIds()) {
                    try {
                        PermissionBinding.checkRole(db.getGuild(), perms, user, aaId);
                        allowedIds.add(aaId);
                    } catch (IllegalArgumentException ignore) {
                    }
                }
                if (allowedIds.isEmpty()) {
                    throw new IllegalArgumentException("You are lacking role permissions for the alliance ids: " + StringMan.getString(list.getIds()));
                }
                return new AllianceList(allowedIds);
            }
            throw new IllegalArgumentException("Not registered");
        } else {
            throw new IllegalArgumentException("TODO: disable this error once i verify it works (see console for debug info)");
        }
    }

    @Me
    @Binding
    public WarCategory.WarRoom warRoom(@Me WarCategory warCat, @Me TextChannel channel) {
        WarCategory.WarRoom warroom = warCat.getWarRoom(channel);
        if (warroom == null) throw new IllegalArgumentException("The command was not run in a war room");
        return warroom;
    }
    @Me
    @Binding
    public WarCategory warChannelBinding(@Me GuildDB db) {
        WarCategory warChannel = db.getWarChannel(true);
        if (warChannel == null) throw new IllegalArgumentException("War channels are not enabled. " + GuildKey.ENABLE_WAR_ROOMS.getCommandObj(db, true) + "");
        return warChannel;
    }

    @Binding(value = "A project name. Replace spaces with `_`. See: <https://politicsandwar.com/nation/projects/>", examples = "ACTIVITY_CENTER")
    public static Project project(String input) {
        Project project = Projects.get(input);
        if (project == null) throw new IllegalArgumentException("Invalid project: `"  + input + "`. Options: " + StringMan.getString(Projects.values));
        return project;
    }

    @Binding(value = "A locutus metric for alliances")
    public AllianceMetric AllianceMetric(String input) {
        return StringMan.parseUpper(AllianceMetric.class, input);
    }

    @Binding(value = "A mode for receiving alerts when a nation leaves beige")
    public NationMeta.BeigeAlertMode BeigeAlertMode(String input) {
        return StringMan.parseUpper(NationMeta.BeigeAlertMode.class, input);
    }

    @Binding(value = "A discord status for receiving alerts when a nation leaves beige")
    public NationMeta.BeigeAlertRequiredStatus BeigeAlertRequiredStatus(String input) {
        return StringMan.parseUpper(NationMeta.BeigeAlertRequiredStatus.class, input);
    }

    @Binding(value = "A completed nation attribute that accepts no arguments and returns a number\n" +
            "To get the attribute for an attribute with arguments, you must provide a value in brackets\n" +
            "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>", examples = {"score", "ships", "land", "getCitiesSince(5d)"})
    public NationAttributeDouble nationMetricDouble(ValueStore store, String input) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        NationAttributeDouble metric = placeholders.getMetricDouble(store, input);
        if (metric == null) {
            String optionsStr = StringMan.getString(placeholders.getMetricsDouble(store).stream().map(NationAttribute::getName).collect(Collectors.toList()));
            throw new IllegalArgumentException("Invalid metric: `" + input + "`. Options: " + optionsStr);
        }
        return metric;
    }

    @Binding(value = "A completed nation attribute that accepts no arguments, returns an object, typically a string, number, boolean or enum\n" +
            "To get the attribute for an attribute with arguments, you must provide a value in brackets\n" +
            "See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>", examples = {"color", "war_policy", "continent", "city(1)"})
    public NationAttribute nationMetric(ValueStore store, String input) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        NationAttribute metric = placeholders.getMetric(store, input, false);
        if (metric == null) {
            String optionsStr = StringMan.getString(placeholders.getMetrics(store).stream().map(NationAttribute::getName).collect(Collectors.toList()));
            throw new IllegalArgumentException("Invalid metric: `" + input + "`. Options: " + optionsStr);
        }
        return metric;
    }

    @Binding(value = "An in-game treaty type")
    public static TreatyType TreatyType(String input) {
        return TreatyType.parse(input);
    }

    @Binding(value = "A tax id or url", examples = {"tax_id=1234", "https://politicsandwar.com/index.php?id=15&tax_id=1234"})
    public static TaxBracket bracket(@Default @Me GuildDB db, String input) {
        Integer taxId;
        if (MathMan.isInteger(input)) {
            taxId = Integer.parseInt(input);
        } else if (input.toLowerCase(Locale.ROOT).contains("tax_id")) {
            taxId = PnwUtil.parseTaxId(input);
        } else {
            taxId = null;
        }
        if (db == null) {
            if (taxId == null) throw new IllegalArgumentException("Invalid tax id: `" + input + "`");
            DBNation nation = Locutus.imp().getNationDB().getFirstNationMatching(f -> f.getTax_id() == taxId);
            if (nation == null) {
                throw new IllegalArgumentException("No nation found with tax id: `" + taxId + "`");
            } else {
                return new TaxBracket(taxId, nation.getAlliance_id(), "", -1, -1, 0L);
            }
        }
        AllianceList allianceList = db.getAllianceList();
        if (allianceList == null) {
            throw new IllegalArgumentException("No alliance registered. See: " + GuildKey.ALLIANCE_ID.getCommandMention());
        }
        Map<Integer, TaxBracket> brackets = allianceList.getTaxBrackets(true);
        if (input.matches("[0-9]+/[0-9]+")) {
            String[] split = input.split("/");
            int moneyRate = Integer.parseInt(split[0]);
            int rssRate = Integer.parseInt(split[1]);

            for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                if (bracket.moneyRate == moneyRate && bracket.rssRate == rssRate) {
                    return bracket;
                }
            }
            throw new IllegalArgumentException("No bracket found for `" + input + "`. Are you sure that tax rate exists ingame?");
        }
        TaxBracket bracket = brackets.get(taxId);
        if (bracket != null) return bracket;
        throw new IllegalArgumentException("Bracket " + taxId + " not found for alliance: " + StringMan.getString(db.getAllianceIds()));
    }

    @Binding
    @ReportPerms
    public ReportManager.Report getReport(ReportManager manager, int id) {
        return getReportAll(manager, id);
    }

    @Binding
    public ReportManager.Report getReportAll(ReportManager manager, int id) {
        ReportManager.Report report = manager.getReport(id);
        if (report == null) {
            throw new IllegalArgumentException("No report found with id: `" + id + "`");
        }
        return report;
    }

    @Binding
    public NewsletterManager manager(@Me GuildDB db) {
        NewsletterManager manager = db.getNewsletterManager();
        if (manager == null) {
            throw new IllegalArgumentException("Your guild is not whitelisted to use newsletters.");
        }
        return manager;
    }

    @Binding
    @ReportPerms
    public Newsletter getNewsletter(NewsletterManager manager, String nameOrId) {
        if (MathMan.isInteger(nameOrId)) {
            int id = Integer.parseInt(nameOrId);
            Newsletter newsletter = manager.getNewsletters().get(id);
            if (newsletter != null) return newsletter;
        }

        for (Newsletter value : manager.getNewsletters().values()) {
            if (value.getName().equalsIgnoreCase(nameOrId)) return value;
        }

        List<String> options = manager.getNewsletters().values().stream().map(Newsletter::getName).collect(Collectors.toList());
        throw new IllegalArgumentException("No newsletter found with name or id: `" + nameOrId + "`\n" +
                "Options: " + StringMan.getString(options));
    }

//    @Binding(examples = "'Error 404' 'Arrgh' 45d")
//    @Me
//    public WarParser wars(@Me Guild guild, String coalition1, String coalition2, @Timediff long timediff) {
//        return WarParser.of(coalition1, coalition1, timediff);
//        return nation.get();
//    }

    // public DoubleArray parse(Map<ResourceType, Double> input)
    // public Map<ResourceType, Double> parse(DoubleArray input)

    @Binding
    public Predicate<DBWar> warFilter(ValueStore store, String input) {
        Placeholders<DBWar> placeholders = Locutus.cmd().getV2().getPlaceholders().get(DBWar.class);
        return placeholders.parseFilter(store, input);
    }

    @Binding
    public Predicate<IAttack> attackFilter(ValueStore store, String input) {
        Placeholders<IAttack> placeholders = Locutus.cmd().getV2().getPlaceholders().get(IAttack.class);
        return placeholders.parseFilter(store, input);
    }

    @Binding
    public MessageTrigger trigger(String trigger) {
        return emum(MessageTrigger.class, trigger);
    }

    @Binding
    public AllianceMetricMode AllianceMetricMode(String mode) {
        return emum(AllianceMetricMode.class, mode);
    }

}