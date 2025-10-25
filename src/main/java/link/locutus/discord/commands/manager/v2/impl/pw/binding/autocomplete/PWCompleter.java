package link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete;

import com.google.common.base.Predicates;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord._test.PredicateDslCompleter;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.Report;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.entities.grant.GrantTemplateManager;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.OrbisMetric;
import link.locutus.discord.db.entities.newsletter.Newsletter;
import link.locutus.discord.db.entities.newsletter.NewsletterManager;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.pnw.*;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AutoAuditType;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.Operation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.task.ia.AuditType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PWCompleter extends BindingHelper {

    @Autocomplete
    @Binding(types={Set.class, DBNation.class}, multiple = true)
    public List<String> nationCompleter(ValueStore store, String input) {
        System.out.println("Get nation completer for input: " + input);
        Parser<?> testExist = store.get(Key.of(DBNation.class, Autocomplete.class));
        if (testExist == null) {
            throw new IllegalStateException("No parser for DBNation with Autocomplete found");
        }

        // Keep wrapped version for completion processing
        Placeholders<DBNation, Object> ph = PlaceholdersMap.get().get(DBNation.class);
        String wrappedInput = ph.wrapHash(input);

        PredicateDslCompleter<DBNation> predicate = new PredicateDslCompleter<>(store, DBNation.class);
        PredicateDslCompleter.CompletionResult result = predicate.apply(wrappedInput, wrappedInput.length());

        System.out.println("Found " + result.getItems().size() + " items for input: " + wrappedInput);

        // Build full replacement strings
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        for (PredicateDslCompleter.CompletionItem item : result.getItems()) {
            int from = Math.max(0, Math.min(item.getReplaceFrom(), wrappedInput.length()));
            int to = Math.max(from, Math.min(item.getReplaceTo(), wrappedInput.length()));
            String insert = item.getInsertText();

            String completed = wrappedInput.substring(0, from) + insert + wrappedInput.substring(to);
            suggestions.add(completed);

            if (suggestions.size() >= OptionData.MAX_CHOICES) break;
        }

        return new ArrayList<>(suggestions);
    }

    @Autocomplete
    @PlaceholderType
    @Binding(types={Class.class, WildcardType.class}, multiple = true)
    public List<String> PlaceholderType(String input) {
        PlaceholdersMap phMap = Locutus.cmd().getV2().getPlaceholders();
        List<String> options = phMap.getTypes().stream().map(PlaceholdersMap::getClassName).collect(Collectors.toList());
        return StringMan.getClosest(input, options, true);
    }
    @Autocomplete
    @Binding(types={NationMeta.class})
    public List<String> NationMeta(String input) {
        return StringMan.completeEnum(input, NationMeta.class);
    }

    @Autocomplete
    @Binding(types={MMRBuyMode.class})
    public List<String> MMRBuyMode(String input) {
        return StringMan.completeEnum(input, MMRBuyMode.class);
    }

    @Autocomplete
    @Binding(types={WarCostByDayMode.class})
    public List<String> WarCostByDayMode(String input) {
        return StringMan.completeEnum(input, WarCostByDayMode.class);
    }

    @Autocomplete
    @Binding(types={Font.class})
    public List<String> Font(String input) {
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        return StringMan.getClosest(input, Arrays.asList(fonts), true);
    }
    @Autocomplete
    @Binding(types={Set.class, TreatyType.class}, multiple = true)
    public List<Map.Entry<String, String>> TreatyType(String input) {
        return StringMan.autocompleteCommaEnum(TreatyType.class, input, OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={Set.class, NationColor.class}, multiple = true)
    public List<Map.Entry<String, String>> NationColor(String input) {
        return StringMan.autocompleteCommaEnum(NationColor.class, input, OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={Set.class, Conflict.class}, multiple = true)
    public List<Map.Entry<String, String>> Conflict(ConflictManager manager, String input) {
        List<Conflict> options = new ArrayList<>(manager.getConflictMap().values());
        return StringMan.autocompleteComma(input, options,
                f -> PWBindings.conflict(manager, f),
                Conflict::getName,
                f -> f.getId() + "",
                OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={Set.class, SuccessType.class}, multiple = true)
    public List<Map.Entry<String, String>> SuccessType(String input) {
        return StringMan.autocompleteCommaEnum(SuccessType.class, input, OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={Set.class, ResourceType.class}, multiple = true)
    public List<Map.Entry<String, String>> ResourceType(String input) {
        return StringMan.autocompleteCommaEnum(ResourceType.class, input, OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={Set.class, GuildDB.class}, multiple = true)
    public List<Map.Entry<String, String>> GuildDB(@Me User user, String input) {
        List<GuildDB> options = user.getMutualGuilds().stream().map(Locutus.imp()::getGuildDB).toList();
        Map<String, GuildDB> byMap = new HashMap<>();
        for (GuildDB db : options) {
            byMap.put(db.getGuild().getName().toLowerCase(), db);
        }
        return StringMan.autocompleteComma(input, options,
                f -> f.isEmpty() ? null : byMap.get(f.split("/")[0].toLowerCase()),
                GuildDB::getName,
                f -> f.getGuild().getId(),
                OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={Set.class, GuildSetting.class, WildcardType.class}, multiple = true)
    public List<String> GuildSetting(String input) {
        List<String> options = Arrays.stream(GuildKey.values()).map(GuildSetting::name).toList();
        return StringMan.autocompleteComma(input, options, OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={Set.class, AllianceMetric.class}, multiple = true)
    public List<Map.Entry<String, String>> AllianceMetrics(String input) {
        return StringMan.autocompleteCommaEnum(AllianceMetric.class, input, OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={Set.class, DomesticPolicy.class}, multiple = true)
    public List<Map.Entry<String, String>> DomesticPolicy(String input) {
        return StringMan.autocompleteCommaEnum(DomesticPolicy.class, input, OptionData.MAX_CHOICES);
    }
    @Autocomplete
    @Binding(types={CommandCallable.class})
    public List<String> command(String input) {
        List<ParametricCallable> options = new ArrayList<>(Locutus.imp().getCommandManager().getV2().getCommands().getParametricCallables(Predicates.alwaysTrue()));
        List<String> optionsStr = options.stream().map(CommandCallable::getFullPath).toList();
        return StringMan.getClosest(input, optionsStr, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={ICommand.class, WildcardType.class}, multiple = true)
    public List<String> commandEndpoint(String input) {
        return command(input);
    }

    @Autocomplete
    @Binding(types={Research.class})
    public List<String> Research(String input) {
        return StringMan.completeEnum(input, Research.class);
    }

    @Autocomplete
    @Binding(types={Coalition.class})
    public List<String> Coalition(String input) {
        return StringMan.completeEnum(input, Coalition.class);
    }

    @Autocomplete
    @Binding(types={WarCostMode.class})
    public List<String> WarCostMode(String input) {
        return StringMan.completeEnum(input, WarCostMode.class);
    }

    @Autocomplete
    @Binding(types={RssConvertMode.class})
    public List<String> RssConvertMode(String input) {
        return StringMan.completeEnum(input, RssConvertMode.class);
    }

    @Autocomplete
    @Binding(types={WarCostStat.class})
    public List<String> WarCostStat(String input) {
        return StringMan.completeEnum(input, WarCostStat.class);
    }

    @Autocomplete
    @Binding(types={DepositTypeInfo.class})
    public List<String> DepositTypeInfo(String input) {
        return StringMan.completeEnum(input, DepositType.class);
    }

    @Autocomplete
    @Binding(types={AlliancePermission.class})
    public List<String> AlliancePermission(String input) {
        return StringMan.completeEnum(input, AlliancePermission.class);
    }

    @Autocomplete
    @Binding(types={AGrantTemplate.class})
    public List<Map.Entry<String, String>> AGrantTemplate(GrantTemplateManager manager, @Me GuildDB db, String input) {
        List<AGrantTemplate> options = new ArrayList<>(manager.getTemplates());
        if (options == null || options.isEmpty()) return null;
        options = StringMan.getClosest(input, options, f -> f.getType() + "/" + f.getName(), OptionData.MAX_CHOICES, true);
        return options.stream().map(new Function<AGrantTemplate, Map.Entry<String, String>>() {
            @Override
            public Map.Entry<String, String> apply(AGrantTemplate f) {
                return KeyValue.of(f.getType() + "/" + f.getName(), f.getName());
            }
        }).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={DBAlliancePosition.class})
    public List<Map.Entry<String, String>> DBAlliancePosition(@Me GuildDB db, String input) {
        AllianceList alliances = db.getAllianceList();
        if (alliances == null || alliances.isEmpty()) return null;
        List<DBAlliancePosition> options = new ArrayList<>(alliances.getPositions());
        options.add(DBAlliancePosition.REMOVE);
        options.add(DBAlliancePosition.APPLICANT);

        options = StringMan.getClosest(input, options, DBAlliancePosition::getName, OptionData.MAX_CHOICES, true);
        return options.stream().map(new Function<DBAlliancePosition, Map.Entry<String, String>>() {
            @Override
            public Map.Entry<String, String> apply(DBAlliancePosition f) {
                return KeyValue.of(f.getQualifiedName(), f.getInputName());
            }
        }).collect(Collectors.toList());
    }


    @Autocomplete
    @GuildCoalition
    @Binding(types={String.class})
    public List<String> GuildCoalition(@Me GuildDB db, String input) {
        List<String> options = new ArrayList<>();
        for (Coalition coalition : Coalition.values()) {
            options.add(coalition.name());
        }
        for (String coalition : db.getCoalitionNames()) {
            if (Coalition.getOrNull(coalition) != null) continue;
            options.add(coalition);
        }
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @ReportPerms
    @Binding(types={Report.class})
    public List<Map.Entry<String, String>> reports(ReportManager manager, @Me DBNation me, @Me User author, @Me GuildDB db, String input) {
        return reports(manager, me, author, db, input, true, OptionData.MAX_CHOICES);
    }

    @Autocomplete
    @ReportPerms
    @Binding(types={GrantRequest.class})
    public List<Map.Entry<String, String>> grantRequests(@Me GuildDB db, String input) {
        List<GrantRequest> requests;
        if (MathMan.isInteger(input)) {
            requests = db.getGrantRequestsByIdPrefix(input);
        } else {
            requests = db.getGrantRequests();
        }
        List<GrantRequest> options = StringMan.getClosest(input, requests, GrantRequest::toLineString, OptionData.MAX_CHOICES, true, false);
        return options.stream().map(f -> KeyValue.of(f.toLineString(), f.getId() + "")).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types = {Newsletter.class})
    public List<Map.Entry<String, String>> newsletters(NewsletterManager manager, String input) {
        List<Newsletter> options = new ArrayList<>(manager.getNewsletters().values());
        options = StringMan.getClosest(input, options, Newsletter::getName, OptionData.MAX_CHOICES, true, false);
        return options.stream().map(f -> KeyValue.of(f.getName(), f.getId() + "")).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={Report.class})
    public List<Map.Entry<String, String>> reportsAll(ReportManager manager, @Me DBNation me, @Me User author, @Me GuildDB db, String input) {
        return reports(manager, me, author, db, input, false, OptionData.MAX_CHOICES);
    }

    public static List<Map.Entry<String, String>> reports(ReportManager manager, @Me DBNation me, @Me User author, @Me GuildDB db, String input, boolean checkPerms, int maxChoices) {
        List<Report> options = manager.loadReports(null);
        if (checkPerms) {
            options.removeIf(f -> !f.hasPermission(me, author, db));
        }

        options = StringMan.getClosest(input, options, f -> "#" + f.reportId + " " + f.getTitle(), maxChoices, true, false);

        return options.stream().map(f -> KeyValue.of("#" + f.reportId + " " + f.getTitle(), f.reportId + "")).collect(Collectors.toList());
    }

    @Autocomplete
    @GuildLoan
    @Binding(types={DBLoan.class})
    public List<Map.Entry<String, String>> loan(LoanManager manager, @Me DBNation me, @Me User author, @Me GuildDB db, String input) {
        List<DBLoan> options = manager.getLoansByGuildDB(db);
        options = StringMan.getClosest(input, options, f -> f.getLineString(true, false), OptionData.MAX_CHOICES, true, false);
        return options.stream().map(f -> KeyValue.of(f.getLineString(true, false), f.loanId + "")).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={DBNation.class})
    public List<Map.Entry<String, String>> DBNation(String input, @Me Guild guild) {
        if (input.isEmpty()) return null;
        if (input.charAt(0) == '@') {
            return completeUser(guild, input, true);
        }

        List<DBNation> options = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        options = StringMan.getClosest(input, options, DBNation::getName, OptionData.MAX_CHOICES, true, true);
        if (options.size() == 1) {
            DBNation nation = options.get(0);
            Locutus.imp().getNationDB().markNationDirty(nation.getNation_id());
        }

        return options.stream().map(f -> KeyValue.of(f.getName(), f.getQualifiedId())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={DBAlliance.class})
    public List<Map.Entry<String, String>> DBAlliance(String input) {
        if (input.isEmpty()) return null;

        List<DBAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getAlliances());
        options = StringMan.getClosest(input, options, DBAlliance::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(f -> KeyValue.of(f.getName(), f.getTypePrefix() + ":" + f.getId())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={NationOrAlliance.class})
    public List<Map.Entry<String, String>> NationOrAlliance(String input, @Me Guild guild) {
        if (input.isEmpty()) return null;
        if (input.charAt(0) == '@') {
            return completeUser(guild, input, true);
        }
        List<NationOrAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        options.addAll(Locutus.imp().getNationDB().getAlliances());

        options = StringMan.getClosest(input, options, NationOrAlliance::getName, OptionData.MAX_CHOICES, true, true);
        if (options.size() == 1) {
            NationOrAlliance nation = options.get(0);
            if (nation.isNation()) {
                Locutus.imp().getNationDB().markNationDirty(nation.getId());
            }
        }

        return options.stream().map(f -> KeyValue.of(f.getName(), f.getTypePrefix() + ":" + f.getId())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={GuildOrAlliance.class})
    public List<Map.Entry<String, String>> GuildOrAlliance(String input, @Me User user, @Me Guild guild) {
        if (input.isEmpty()) return null;
        List<GuildOrAlliance> options = new ArrayList<>();
        options.addAll(Locutus.imp().getNationDB().getAlliances());
        if (user != null) {
            for (Guild other : user.getMutualGuilds()) {
                GuildDB db = Locutus.imp().getGuildDB(other);
                if (db != null) {
                    options.add(db);
                }
            }
        }
        options = StringMan.getClosest(input, options, GuildOrAlliance::getName, OptionData.MAX_CHOICES, true, true);
        return options.stream().map(f -> KeyValue.of((f.isGuild() ? "guild:" : "") + f.getName(), f.getTypePrefix() + ":" + f.getIdLong())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={NationOrAllianceOrGuild.class})
    public List<Map.Entry<String, String>> NationOrAllianceOrGuild(String input, @Me User user, @Me Guild guild) {
        if (input.isEmpty()) return null;
        if (input.charAt(0) == '@') {
            return completeUser(guild, input, true);
        }
        List<NationOrAllianceOrGuild> options = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        options.addAll(Locutus.imp().getNationDB().getAlliances());
        if (user != null) {
            for (Guild other : user.getMutualGuilds()) {
                GuildDB db = Locutus.imp().getGuildDB(other);
                if (db != null) {
                    options.add(db);
                }
            }
        }
        options = StringMan.getClosest(input, options, NationOrAllianceOrGuild::getName, OptionData.MAX_CHOICES, true, true);
        if (options.size() == 1) {
            NationOrAllianceOrGuild nation = options.get(0);
            if (nation.isNation()) {
                Locutus.imp().getNationDB().markNationDirty(nation.getId());
            }
        }
        return options.stream().map(f -> KeyValue.of((f.isGuild() ? "guild:" : "") + f.getName(), f.getTypePrefix() + ":" + f.getIdLong())).collect(Collectors.toList());
    }

    private List<Map.Entry<String, String>> completeUser(@Me Guild guild, String input, boolean removeTag) {
        if (removeTag) input = input.substring(1);
        List<Member> options = guild.getMembers();
        Function<Member, String> getName = new Function<Member, String>() {
            @Override
            public String apply(Member member) {
                String nick = member.getNickname();
                String user = DiscordUtil.getFullUsername(member.getUser());
                if (nick != null && !nick.equalsIgnoreCase(user)) {
                    return nick + " " + user;
                }
                return user;
            }
        };
        options = StringMan.getClosest(input, options, getName, OptionData.MAX_CHOICES, true, false);
        return options.stream().map(f -> KeyValue.of(f.getEffectiveName(), f.getAsMention())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={NationOrAllianceOrGuildOrTaxid.class})
    public List<Map.Entry<String, String>> NationOrAllianceOrGuildOrTaxid(String input, @Me GuildDB db, @Me User user) {
        if (input.isEmpty()) return null;
        if (input.charAt(0) == '@') {
            return completeUser(db.getGuild(), input, true);
        }
        AllianceList aaList = db.getAllianceList();

        List<NationOrAllianceOrGuildOrTaxid> options = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        options.addAll(Locutus.imp().getNationDB().getAlliances());
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                GuildDB mutual = Locutus.imp().getGuildDB(guild);
                if (mutual != null) {
                    options.add(mutual);
                }
            }
        }
        if (aaList != null) {
            for (Map.Entry<Integer, TaxBracket> entry : aaList.getTaxBrackets(Long.MAX_VALUE).entrySet()) {
                TaxBracket bracket = entry.getValue();
                if (bracket.getName().isEmpty()) {
                    bracket.setName("tax_id:" + bracket.taxId);
                }
                options.add(bracket);
            }
        }

        options = StringMan.getClosest(input, options, NationOrAllianceOrGuildOrTaxid::getName, OptionData.MAX_CHOICES, true, true);
        if (options.size() == 1) {
            NationOrAllianceOrGuildOrTaxid nation = options.get(0);
            if (nation.isNation()) {
                Locutus.imp().getNationDB().markNationDirty(nation.getId());
            }
        }
        return options.stream().map(f -> KeyValue.of((f.isGuild() ? "guild:" : "") + f.getName(), f.getTypePrefix() + ":" + f.getIdLong())).collect(Collectors.toList());
    }

    private final static List<String> MMR_MATCHER_CHAR_5 = new ArrayList<>();
    private final static List<String> MMR_MATCHER_CHAR_3 = new ArrayList<>();
    static {
        for (int i = 0; i <= 5; i++) {
            MMR_MATCHER_CHAR_5.add(String.valueOf(i));
        }
        for (int i = 0; i <= 3; i++) {
            MMR_MATCHER_CHAR_3.add(String.valueOf(i));
        }
        MMR_MATCHER_CHAR_5.add("X");
        MMR_MATCHER_CHAR_3.add("X");
    }

    @Autocomplete
    @Binding(types={MMRMatcher.class, MMRDouble.class})
    public List<String> MMRMatcher(String input) {
        List<String> append = null;
        switch (input.length()) {
            case 0:
                return MMR_MATCHER_CHAR_5;
            case 1:
            case 2:
                append = MMR_MATCHER_CHAR_5;
                break;
            case 3:
                append = MMR_MATCHER_CHAR_3;
                break;
        }
        if (append != null) {
            return append.stream().map(f -> input + f).collect(Collectors.toList());
        }
        return null;
    }

    @Autocomplete
    @Binding(types={CityRanges.class})
    public List<String> CityRanges(String input) {
        if (input.isEmpty() || input.endsWith(",")) return List.of(input + "c");
        char lastChar = input.charAt(input.length() - 1);

        boolean allowDigit = false;
        boolean allowDash = false;
        boolean allowComma = false;

        String[] split = input.split("-");
        // last
        String token = split[split.length - 1];

        if (Character.isDigit(lastChar)) {
            // if only 1 preceeding digit, allow digit
            if (token.length() == 1 || !Character.isDigit(token.charAt(token.length() - 2))) {
                allowDigit = true;
            }
            // if token does not contain '-' allow dash
            if (!token.contains("-")) {
                allowDash = true;
            }
            // if token does contain '-' allow comma
            if (token.contains("-")) {
                allowComma = true;
            }
        } else if (lastChar == '-') {
            allowDigit = true;
        } else if (lastChar == ',') {
            return List.of("c");
        } else {
            allowDigit = true;
        }
        List<String> options = new LinkedList<>();
        if (allowDigit) {
            for (int i = 0; i < 10; i++) {
                options.add(input + i);
            }
        }
        if (allowDash) {
            options.add(input + "-");
        }
        if (allowComma) {
            options.add(input + ",");
        }
        return options;
    }


    @Autocomplete
    @Binding(types={AllianceMetric.class})
    public List<String> AllianceMetric(String input) {
        return StringMan.completeEnum(input, AllianceMetric.class);
    }

    @Autocomplete
    @Binding(types={AutoAuditType.class})
    public List<String> AutoAuditType(String input) {
        return StringMan.completeEnum(input, AutoAuditType.class);
    }

    @Autocomplete
    @Binding(types={Project.class})
    public List<String> Project(String input) {
        List<Project> options = Arrays.asList(Projects.values);;
        options = StringMan.getClosest(input, options, Project::name, OptionData.MAX_CHOICES, true);
        return options.stream().map(Project::name).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={GuildSetting.class, WildcardType.class}, multiple = true)
    public List<String> setting(String input) {
        List<String> options = Arrays.asList(GuildKey.values()).stream().map(GuildSetting::name).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={Parser.class})
    public List<String> Parser(ValueStore store, String input) {
        Map<Key, Parser> parsers = store.getParsers();
        List<String> options = parsers.entrySet().stream().filter(f -> f.getValue().isConsumer(store)).map(f -> f.getKey().toSimpleString()).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={TaxBracket.class})
    public List<String> TaxBracket(@Me GuildDB db, String input) {
        Map<Integer, TaxBracket> brackets = db.getAllianceList().getTaxBrackets(Long.MAX_VALUE);
        if (brackets.isEmpty()) return null;

        List<String> options = brackets.values().stream().map(f -> f.taxId + "").collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={ClearRolesEnum.class})
    public List<String> ClearRolesEnum(String input) {
        return StringMan.completeEnum(input, ClearRolesEnum.class);
    }

    @Autocomplete
    @Binding(types={FlowType.class})
    public List<String> FlowType(String input) {
        return StringMan.completeEnum(input, FlowType.class);
    }

    @Autocomplete
    @Binding(types={Status.class})
    public List<String> LoanStatus(String input) {
        return StringMan.completeEnum(input, Status.class);
    }

    @Autocomplete
    @Binding(types = {GuildDB.class})
    public List<Map.Entry<String, String>> GuildDBOne(@Me User user, String input) {
        if (user == null) return null;
        List<GuildDB> options = user.getMutualGuilds().stream()
                .map(Locutus.imp()::getGuildDB)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        options = StringMan.getClosest(input, options, GuildDB::getName, OptionData.MAX_CHOICES, true, false);
        return options.stream()
                .map(db -> KeyValue.of(db.getName(), db.getGuild().getId()))
                .collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types = {Guild.class})
    public List<Map.Entry<String, String>> Guild(@Me User user, String input) {
        if (user == null) return null;
        List<Guild> options = new ObjectArrayList<>(user.getMutualGuilds());
        options = StringMan.getClosest(input, options, Guild::getName, OptionData.MAX_CHOICES, true, false);
        return options.stream()
                .map(g -> KeyValue.of(g.getName(), g.getId()))
                .collect(Collectors.toList());
    }

    public PWCompleter()
    {
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, Category.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));
                    if (guild == null) return null;
                    return StringMan.autocompleteComma(input.toString(),
                            guild.getCategories(),
                            guild::getCategoryById,
                            Channel::getName,
                            ISnowflake::getId,
                            OptionData.MAX_CHOICES);
                }));
            });
        }

        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, DBAlliance.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<DBAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getAlliances());
                    String inputStr = input.toString();
                    return StringMan.autocompleteComma(inputStr, options, f -> DBAlliance.parse(f, false), DBAlliance::getName, f -> f.getId() + "", OptionData.MAX_CHOICES);
                }));
            });
        }

        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, Project.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<Project> options = Arrays.asList(Projects.values);
                    String inputStr = input.toString();
                    return StringMan.autocompleteComma(inputStr, options, Projects::get, Project::name, Project::name, OptionData.MAX_CHOICES);
                }));
            });
        }

        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, Building.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<Building> options = Arrays.asList(Buildings.values());
                    String inputStr = input.toString();
                    return StringMan.autocompleteComma(inputStr, options, Buildings::get, Building::name, Building::name, OptionData.MAX_CHOICES);
                }));
            });
        }

        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, Roles.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(Roles.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }

        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, Status.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(Status.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }

        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, BeigeReason.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(BeigeReason.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, AutoAuditType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(AutoAuditType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, AuditType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(AuditType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, Operation.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(Operation.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, AuditType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(AuditType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, Continent.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(Continent.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, WarStatus.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(WarStatus.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, WarType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(WarType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, AttackType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(AttackType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, MilitaryUnit.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(MilitaryUnit.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, OrbisMetric.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(OrbisMetric.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(List.class, ResourceType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<ResourceType> options = new ArrayList<>(ResourceType.valuesList);
                    return StringMan.autocompleteComma(input.toString(), options, ResourceType::valueOf, ResourceType::getName, ResourceType::getName, OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Map.class, MilitaryUnit.class, Long.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<String> options = Arrays.asList(MilitaryUnit.values).stream().map(Enum::name).collect(Collectors.toList());
                    return StringMan.completeMap(options, null, input.toString());
                }));
            });
        }
        {
            Type type = TypeToken.getParameterized(Map.class, ResourceType.class, Double.class).getType();
            Consumer<ValueStore<?>> binding = store -> {
                Key<Object> key = Key.of(type, Autocomplete.class);
                FunctionConsumerParser parser = new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<String> options = ResourceType.valuesList.stream().map(Enum::name).collect(Collectors.toList());
                    return StringMan.completeMap(options, null, input.toString());
                });
                store.addParser(key, parser);
                store.addParser(Key.of(type, Autocomplete.class, AllianceDepositLimit.class), parser);
                store.addParser(Key.of(type, Autocomplete.class, NationDepositLimit.class), parser);
            };
            addBinding(binding);
        }
    }
}