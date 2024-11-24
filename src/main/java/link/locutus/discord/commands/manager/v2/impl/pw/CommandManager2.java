package link.locutus.discord.commands.manager.v2.impl.pw;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.commands.manager.v2.binding.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.bindings.SelectorInfo;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.GPTBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NewsletterBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.*;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.config.yaml.file.YamlConfiguration;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.WebCommands;
import link.locutus.discord.web.jooby.JteUtil;
import link.locutus.discord.web.test.TestCommands;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static link.locutus.discord.util.StringMan.isQuote;

public class CommandManager2 {
    private final CommandGroup commands;
    private final ValueStore<Object> store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final PlaceholdersMap placeholders;
    private PWGPTHandler pwgptHandler;

    public Map<String, Object> toJson(ValueStore htmlOptionsStore, PermissionHandler permHandler) {
        Map<String, Object> cmdJson = commands.toJson(permHandler, false);

        Map<String, Map<String, Object>> keysData = new LinkedHashMap<>();
        Set<String> checkedOptions = new HashSet<>();
        Map<String, Object> optionsData = new LinkedHashMap<>();

        Set<Parser> parsers = new LinkedHashSet<>();
        for (ParametricCallable callable : commands.getParametricCallables(f -> true)) {
            for (ParameterData param : callable.getUserParameters()) {
                Parser<?> parser = param.getBinding();
                Binding binding = parser.getKey().getBinding();
                if (binding != null && binding.webType().isEmpty()) {
                    parsers.add(parser);
                }
            }

        }
        for (Parser parser : parsers) {
            Key key = parser.getKey();
            Map<String, Object> typeJson = parser.toJson();
            keysData.put(key.toSimpleString(), typeJson);
            Key optionsKey = key.append(HtmlOptions.class);
            Parser optionParser = htmlOptionsStore.get(optionsKey);
            if (optionParser != null) {
                WebOption option = (WebOption) optionParser.apply(store, null);
                optionsData.computeIfAbsent(option.getName(), k -> option.toJson());
                continue;
            }
            List<Class> components = WebOption.getComponentClasses(key.getType());
            if (components.isEmpty()) {
                System.out.println("No components for " + key.toSimpleString());
                continue;
            }
            for (Class t : components) {
                String name = t.getSimpleName();
                if (!checkedOptions.add(name)) continue;
                if (t.isEnum()) {
                    WebOption option = WebOption.fromEnum(t);
                    optionsData.computeIfAbsent(option.getName(), k -> option.toJson());
                    continue;
                }
                optionsKey = Key.of(t, HtmlOptions.class);
                optionParser = htmlOptionsStore.get(optionsKey);
                if (optionParser != null) {
                    WebOption option = (WebOption) optionParser.apply(htmlOptionsStore, null);
                    if (!option.getName().equalsIgnoreCase(name)) {
                        optionsData.put(name, option.getName());
                    } else {
                        optionsData.computeIfAbsent(option.getName(), k -> option.toJson());
                    }
                } else {
                    System.out.println("No options for " + name);
                }
            }
        }

        Map<String, Object> phRoot = new LinkedHashMap<>();
        for (Class<?> t : placeholders.getTypes()) {
            Placeholders<?> ph = placeholders.get(t);
            Map<String, Object> json = new LinkedHashMap<>();

            Map<String, Object> bindings = ph.getCommands().toJson(permHandler, true);
            json.put("commands", bindings);

            Set<SelectorInfo> selectors = ph.getSelectorInfo();
            List<String[]> arr = selectors.stream().map(f -> new String[]{f.format(), f.example(), f.desc()}).toList();
            json.put("selectors", arr);
            Set<String> columns = ph.getSheetColumns();
            if (!columns.isEmpty()) {
                json.put("columns", columns);
            }
            phRoot.put(t.getSimpleName(), json);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commands", cmdJson);
        result.put("placeholders", phRoot);
        result.put("keys", keysData);
        result.put("options", optionsData);
        return result;
    }

    public CommandManager2() {
        this.store = new SimpleValueStore<>();
        new PrimitiveBindings().register(store);
        new DiscordBindings().register(store);
        PWBindings pwBindings = new PWBindings();
        pwBindings.register(store);
        new GPTBindings().register(store);
        new SheetBindings().register(store);
//        new StockBinding().register(store);
        new NewsletterBindings().register(store);

        this.validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);

        this.permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);

        this.placeholders = new PlaceholdersMap(store, validators, permisser);
        // Register bindings
        for (Class<?> type : placeholders.getTypes()) {
            Placeholders<?> ph = placeholders.get(type);
            ph.register(store);
        }
        // Initialize commands (staged after bindings as there might be cross dependency)
        for (Class<?> type : placeholders.getTypes()) {
            placeholders.get(type).init();
        }

        this.commands = CommandGroup.createRoot(store, validators);

        if (!Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY.isEmpty()) {
            try {
                pwgptHandler = new PWGPTHandler(this);
            } catch (SQLException | ClassNotFoundException | ModelNotFoundException | MalformedModelException |
                     IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public PWGPTHandler getPwgptHandler() {
        return pwgptHandler;
    }

    public static Map<String, String> parseArguments(Set<String> params, String input, boolean checkUnbound) {
        Map<String, String> lowerCase = new HashMap<>();
        for (String param : params) {
            lowerCase.put(param.toLowerCase(Locale.ROOT), param);
        }

        Map<String, String> result = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("(?i)(^| |,)(" + String.join("|", lowerCase.keySet()) + "):[ ]{0,1}[^ ]");
        Matcher matcher = pattern.matcher(input);

        Pattern fuzzyArg = !checkUnbound ? null : Pattern.compile("(?i)[ ,]([a-zA-Z]+):[ ]{0,1}[^ ]");

        Map<String, Integer> argStart = new LinkedHashMap<>();
        Map<String, Integer> argEnd = new LinkedHashMap<>();
        String lastArg = null;
        while (matcher.find()) {
            String argName = matcher.group(2).toLowerCase(Locale.ROOT);
            int index = matcher.end(2) + 1;
            Integer existing = argStart.put(argName, index);
            if (existing != null)
                throw new IllegalArgumentException("Duplicate argument `" + argName + "` in `" + input + "`");

            if (lastArg != null) {
                argEnd.put(lastArg, matcher.start(2) - 1);
            }
            lastArg = argName;
        }
        if (lastArg != null) {
            argEnd.put(lastArg, input.length());
        }

        for (Map.Entry<String, Integer> entry : argStart.entrySet()) {
            String id = entry.getKey();
            int start = entry.getValue();
            int end = argEnd.get(id);
            String value = input.substring(start, end).trim();
            boolean hasQuote = false;
            if (value.length() > 1 && isQuote(value.charAt(0)) && isQuote(value.charAt(value.length() - 1))) {
                value = value.substring(1, value.length() - 1);
                hasQuote = true;
            }

            if (fuzzyArg != null && !hasQuote) {
                Matcher valueMatcher = fuzzyArg.matcher(value);
                if (valueMatcher.find()) {
                    String fuzzyArgName = valueMatcher.group(1);
                    throw new IllegalArgumentException("Invalid argument: `" + fuzzyArgName + "` for `" + input + "` options: " + (params) + "\n" +
                            "Please use quotes if you did not intend to specify an argument: `" + value + "`");
                }
            }
            result.put(lowerCase.get(id), value);
        }

        if (argStart.isEmpty()) {
            throw new IllegalArgumentException("No arguments found` for `" + input + "` options: " + (params));
        }

        return result;
    }

    public CommandManager2 registerDefaults() {
//        this.commands.registerMethod(new TestCommands(), List.of("test"), "test", "test");
        getCommands().registerMethod(new WebCommands(), List.of(), "web", "web");
        getCommands().registerMethod(new WebCommands(), List.of("mail"), "mailLogin", "web_login");
        getCommands().registerMethod(new IACommands(), List.of("channel", "sort"), "sortChannelsSheetRules", "category_rule_sheet");

        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "reloadConfig", "config");
        getCommands().registerMethod(new GrantCommands(), List.of("grant"), "costBulk", "cost");
        getCommands().registerMethod(new StatCommands(), List.of("alliance", "stats"), "militarizationTime", "militarization_time");

        getCommands().registerMethod(new WarCommands(), List.of("sheets_ia"), "ActivitySheetDate", "activity_date");
        getCommands().registerMethod(new WarCommands(), List.of("sheets_ia"), "WarDecSheetDate", "declares_date");
        getCommands().registerMethod(new WarCommands(), List.of("sheets_ia"), "DepositSheetDate", "deposits_date");

        getCommands().registerMethod(new AdminCommands(), List.of("admin", "settings"), "unsetNews", "subscribe");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "settings"), "unsetKeys", "unset");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "settings"), "infoBulk", "info_servers");

        getCommands().registerMethod(new WarCommands(), List.of("alerts", "beige"), "testBeigeAlertAuto", "test_auto");
        getCommands().registerMethod(new UtilityCommands(), List.of("nation", "history"), "vmHistory", "vm");
        getCommands().registerMethod(new UtilityCommands(), List.of("nation", "history"), "grayStreak", "gray_streak");
        getCommands().registerMethod(new UtilityCommands(), List.of("tax"), "setBracketBulk", "set_from_sheet");
        getCommands().registerMethod(new StatCommands(), List.of("stats_other", "global_metrics"), "orbisStatByDay", "by_time");

        getCommands().registerMethod(new IACommands(), List.of("channel", "sort"), "sortChannelsSheet", "sheet");
        getCommands().registerMethod(new IACommands(), List.of("channel", "sort"), "sortChannelsName", "category_filter");

        getCommands().registerMethod(new StatCommands(), List.of("alliance", "stats"), "militaryRanking", "militarization");
        getCommands().registerMethod(new StatCommands(), List.of("alliance", "stats"), "listMerges", "merges");
        getCommands().registerMethod(new StatCommands(), List.of("stats_war", "attack_breakdown"), "attackBreakdownSheet", "sheet");
        getCommands().registerMethod(new IACommands(), List.of("interview", "questions"), "viewInterview", "view");
        getCommands().registerMethod(new IACommands(), List.of("interview", "questions"), "setInterview", "set");
        getCommands().registerMethod(new IACommands(), List.of("interview", "channel"), "renameInterviewChannels", "auto_rename");

        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncWars", "wars");
        getCommands().registerMethod(new WarCommands(), List.of("war", "room"), "warRoomList", "list");
        getCommands().registerMethod(new WarCommands(), List.of("war", "room"), "deletePlanningChannel", "delete_planning");
        getCommands().registerMethod(new WarCommands(), List.of("war", "room"), "deleteForEnemies", "delete_for_enemies");
        getCommands().registerMethod(new UtilityCommands(), List.of("land"), "landROI", "roi");
        getCommands().registerMethod(new UtilityCommands(), List.of("infra"), "infraROI", "roi");

        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "featured"), "featureConflicts", "add_rule");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "featured"), "removeFeature", "remove_rule");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "featured"), "listFeaturedRuleset", "list_rules");

        getCommands().registerMethod(new ConflictCommands(), List.of("conflict"), "info", "info");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict"), "listConflicts", "list");
        getCommands().registerMethod(new VirtualConflictCommands(), List.of("conflict"), "createTemporary", "create_temp");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict"), "deleteConflict", "delete");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict"), "addConflict", "create");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "purge"), "purgeFeatured", "featured");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "purge"), "purgeTemporaryConflicts", "user_generated");

        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "addAnnouncement", "add_forum_post");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "setConflictEnd", "end");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "setConflictStart", "start");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "setConflictName", "rename");

        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "setWiki", "wiki");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "setWiki", "wiki");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "setStatus", "status");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "setCB", "casus_belli");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "setCategory", "category");

        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "syncConflictData", "website");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "importConflictData", "multiple_sources");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "importCtowned", "ctowned");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "importWikiPage", "wiki_page");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "importWikiAll", "wiki_all");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "recalculateGraphs", "recalculate_graphs");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "recalculateTables", "recalculate_tables");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "importAllianceNames", "alliance_names");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "importExternal", "db_file");

        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "alliance"), "removeCoalition", "remove");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "alliance"), "addCoalition", "add");


        getCommands().registerMethod(new AllianceMetricCommands(), List.of("admin", "sync"), "saveMetrics", "saveMetrics");
        getCommands().registerMethod(new AllianceMetricCommands(), List.of("stats_tier"), "metricByGroup", "metric_by_group");
        getCommands().registerMethod(new AllianceMetricCommands(), List.of("stats_other", "data_csv"), "AlliancesDataByDay", "AlliancesDataByDay");

        getCommands().registerMethod(new PlayerSettingCommands(), List.of("alerts", "bank"), "bankAlertRequiredValue", "min_value");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncWarrooms", "warrooms");
        getCommands().registerMethod(new AdminCommands(), List.of("fun"), "resetCityNames", "reset_borgs_cities");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "queue"), "conditionalMessageSettings", "custom_messages");

        getCommands().registerMethod(new UtilityCommands(), List.of("announcement"), "addWatermark", "watermark");
        getCommands().registerMethod(new WarCommands(), List.of("war", "sheet"), "raidSheet", "raid");

        GrantCommands grants = new GrantCommands();
        getCommands().registerMethod(grants, List.of("grant"), "grantCity", "city");
        getCommands().registerMethod(grants, List.of("grant"), "grantProject", "project");
        getCommands().registerMethod(grants, List.of("grant"), "grantInfra", "infra");
        getCommands().registerMethod(grants, List.of("grant"), "grantLand", "land");
        getCommands().registerMethod(grants, List.of("grant"), "grantUnit", "unit");
        getCommands().registerMethod(grants, List.of("grant"), "grantMMR", "mmr");
        getCommands().registerMethod(grants, List.of("grant"), "grantConsumption", "consumption");
        getCommands().registerMethod(grants, List.of("grant"), "grantBuild", "build");
        getCommands().registerMethod(grants, List.of("grant"), "grantWarchest", "warchest");

        NewsletterCommands newsletter = new NewsletterCommands();
        getCommands().registerMethod(newsletter, List.of("newsletter"), "create", "create");
        getCommands().registerMethod(newsletter, List.of("newsletter", "channel"), "channelAdd", "add");
        getCommands().registerMethod(newsletter, List.of("newsletter", "channel"), "channelRemove", "remove");
        getCommands().registerMethod(newsletter, List.of("newsletter"), "info", "info");
        getCommands().registerMethod(newsletter, List.of("newsletter"), "autosend", "auto");
        getCommands().registerMethod(newsletter, List.of("newsletter"), "send", "send");
        getCommands().registerMethod(newsletter, List.of("newsletter"), "list", "list");
        getCommands().registerMethod(newsletter, List.of("newsletter"), "subscribe", "subscribe");
        getCommands().registerMethod(newsletter, List.of("newsletter"), "unsubscribe", "unsubscribe");
        getCommands().registerMethod(newsletter, List.of("newsletter"), "delete", "delete");

        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "auto", "auto");
        this.commands.registerMethod(new CustomSheetCommands(), List.of("settings_sheet"), "setSheetKey", "set");
        this.commands.registerMethod(new CustomSheetCommands(), List.of("settings_sheet"), "listSheetKeys", "list");
        ////listSheetTemplates
        //sheet_template list
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "renameTemplate", "rename");
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "listSheetTemplates", "list");
        ////listSelectionAliases
        //selection_alias list
        this.commands.registerMethod(new CustomSheetCommands(), List.of("selection_alias"), "listSelectionAliases", "list");
        ////listCustomSheets
        //sheet_custom list
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "listCustomSheets", "list");
        ////deleteSelectionAlias
        //selection_alias remove
        this.commands.registerMethod(new CustomSheetCommands(), List.of("selection_alias"), "renameSelection", "rename");
        this.commands.registerMethod(new CustomSheetCommands(), List.of("selection_alias"), "deleteSelectionAlias", "remove");
        ////viewTemplate
        //sheet_template view
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "viewTemplate", "view");
        ////deleteTemplate
        //sheet_template remove
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "deleteTemplate", "remove");
        ////deleteColumns
        //sheet_template remove_column
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "deleteColumns", "remove_column");
        ////addTab
        //sheet_custom add
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "addTab", "add_tab");
        ////updateSheet
        //sheet_custom update
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "updateSheet", "update");
        ////deleteTab
        //sheet_custom remove_tab
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "deleteTab", "remove_tab");
        ////info
        //sheet_custom view
        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "info", "view");


        this.commands.registerMethod(new EmbedCommands(), List.of("announcement"), "announceDocument", "document");
        this.commands.registerMethod(new AdminCommands(), List.of("role"), "maskSheet", "mask_sheet");

        this.commands.registerMethod(new UnsortedCommands(), List.of("audit"), "auditSheet", "sheet");
        this.commands.registerMethod(new DiscordCommands(), List.of("deposits"), "viewFlow", "flows");
        this.commands.registerMethod(new DiscordCommands(), List.of("deposits"), "shiftFlow", "shiftFlow");

        this.commands.registerCommandsWithMapping(CM.class);

        this.commands.registerMethod(new UtilityCommands(), List.of("treaty"), "nap", "gw_nap");
        this.commands.registerMethod(new UtilityCommands(), List.of("building"), "buildingCost", "cost");
        this.commands.registerMethod(new AdminCommands(), List.of("admin"), "setV2", "set_v2");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncBans", "bans");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "savePojos", "pojos");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "list"), "hasSameNetworkAsBan", "multis");

        this.commands.registerMethod(new GPTCommands(), List.of("chat", "dataset"), "embeddingSelect", "select");
        this.commands.registerMethod(new GPTCommands(), List.of("help"), "find_placeholder", "find_nation_placeholder");
        this.commands.registerMethod(new BankCommands(), List.of("escrow"), "escrowSheetCmd", "view_sheet");

        this.commands.registerMethod(new IACommands(), List.of("nation", "list"), "viewBans", "bans");
        this.commands.registerMethod(new IACommands(), List.of("mail"), "readMail", "read");
        this.commands.registerMethod(new IACommands(), List.of("mail"), "searchMail", "search");

        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "importLinkedBans", "multi_bans");

        this.commands.registerMethod(new EmbedCommands(), List.of("embed", "template"), "depositsPanel", "deposits");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed", "template"), "econPanel", "econ_gov");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed", "template"), "iaPanel", "ia_gov");

        this.commands.registerMethod(new EmbedCommands(), List.of("embed"), "create", "create");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed"), "title", "title");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed"), "description", "description");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed", "remove"), "removeButton", "button");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed", "add"), "addButton", "command");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed", "add"), "addModal", "modal");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed", "add"), "addButtonRaw", "raw");
        this.commands.registerMethod(new EmbedCommands(), List.of("embed", "rename"), "renameButton", "button");

        // Exception in thread "main" java.lang.IllegalStateException: Missing methods for IACommands:
        // - /interviewSheet
        this.commands.registerMethod(new IACommands(), List.of("interview"), "interviewSheet", "sheet");
        //
        //See example in CommandManager2#registerDefaultsMissing methods for UnsortedCommands:
        // - prolificOffshores
        this.commands.registerMethod(new UnsortedCommands(), List.of("offshore", "list"), "prolificOffshores", "prolific");
        this.commands.registerMethod(new UtilityCommands(), List.of("offshore", "list"), "listOffshores", "all");
        this.commands.registerMethod(new UtilityCommands(), List.of("offshore", "find"), "findOffshore", "for_coalition");
        this.commands.registerMethod(new UtilityCommands(), List.of("offshore", "find"), "findOffshores", "for_enemies");
        //
        //See example in CommandManager2#registerDefaultsMissing methods for TradeCommands:
        // - unsubTrade
        // - tradeSubs
        this.commands.registerMethod(new TradeCommands(), List.of("alerts", "trade"), "unsubTrade", "unsubscribe");
        this.commands.registerMethod(new TradeCommands(), List.of("alerts", "trade"), "tradeSubs", "list");
        //
        //See example in CommandManager2#registerDefaultsMissing methods for AdminCommands:
        // - syncBounties
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncBounties", "bounties");
        // - purgeWarRooms
        this.commands.registerMethod(new AdminCommands(), List.of("war", "room"), "purgeWarRooms", "purge");
        // - syncForumProfiles
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncForumProfiles", "forum_profiles");
        // - syncTreaties
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncTreaties", "treaties");
        // - syncAttacks
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncAttacks", "attacks");
        // - syncOffshore
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncOffshore", "offshore");
        // - runMultiple
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "command"), "runMultiple", "multiple");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "command"), "runForNations", "format_for_nations");
        // - sudoNations
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sudo"), "sudoNations", "nations");
        // - sudo
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sudo"), "sudo", "user");
        // - nationMeta
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "debug"), "nationMeta", "nation_meta");
        // - tradeId
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "debug"), "tradeId", "trade_id");
        // - syncTrade
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncTrade", "trade");
        // - syncUid
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync2"), "syncUid", "uid");
        // - syncMail
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync2"), "syncMail", "mail");
        // - syncTaxes
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync2"), "syncTaxes", "taxes");
        // - guildInfo
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "debug"), "guildInfo", "guild");
        //
        //See example in CommandManager2#registerDefaultsMissing methods for FACommands:
        // - generateCoalitionSheet
        this.commands.registerMethod(new FACommands(), List.of("coalition"), "generateCoalitionSheet", "sheet");
        //
        //See example in CommandManager2#registerDefaultsMissing methods for PlayerSettingCommands:
        // - bankAlertList
        // - bankAlertUnsubscribe
        // - bankAlert
        this.commands.registerMethod(new PlayerSettingCommands(), List.of("alerts", "bounty"), "bountyAlertOptOut", "opt_out");
        this.commands.registerMethod(new PlayerSettingCommands(), List.of("alerts", "bank"), "bankAlertList", "list");
        this.commands.registerMethod(new PlayerSettingCommands(), List.of("alerts", "bank"), "bankAlertUnsubscribe", "unsubscribe");
        this.commands.registerMethod(new PlayerSettingCommands(), List.of("alerts", "bank"), "bankAlert", "subscribe");
        //
        //See example in CommandManager2#registerDefaultsMissing methods for StatCommands:
        // - allianceByLoot
        // - warCostsByDay
        // - warsCostRankingByDay
        // - attackTypeRanking
        // - attackTypeBreakdownAB
        this.commands.registerMethod(new StatCommands(), List.of("alliance", "stats"), "allianceAttributeRanking", "attribute_ranking");
        this.commands.registerMethod(new StatCommands(), List.of("alliance", "stats"), "allianceByLoot", "loot_ranking");
        this.commands.registerMethod(new StatCommands(), List.of("stats_war", "by_day"), "warCostsByDay", "warcost_versus");
        this.commands.registerMethod(new StatCommands(), List.of("stats_war", "by_day"), "warsCostRankingByDay", "warcost_global");
        this.commands.registerMethod(new StatCommands(), List.of("stats_war"), "attackTypeRanking", "attack_ranking");
        this.commands.registerMethod(new StatCommands(), List.of("stats_war", "attack_breakdown"), "attackTypeBreakdownAB", "versus");

        for (GuildSetting setting : GuildKey.values()) {
            List<String> path = List.of("settings_" + setting.getCategory().name().toLowerCase(Locale.ROOT));

            Class<? extends GuildSetting> settingClass = setting.getClass();
            Method[] methods = settingClass.getMethods();
            Map<String, String> methodNameToCommandName = new HashMap<>();
            for (Method method : methods) {
                if (method.getDeclaringClass() != settingClass) continue;
                if (method.getAnnotation(Command.class) != null) {
                    Command command = method.getAnnotation(Command.class);

                    String[] aliases = command.aliases();
                    String commandName = aliases.length == 0 ? method.getName() : aliases[0];
                    methodNameToCommandName.put(method.getName(), commandName);
                }
            }

            for (Map.Entry<String, String> entry : methodNameToCommandName.entrySet()) {
                String methodName = entry.getKey();
                String commandName = entry.getValue();
                this.commands.registerMethod(setting, path, methodName, commandName);
            }
        }

        {
            // report commands
            ReportCommands reportCommands = new ReportCommands();
            this.commands.registerMethod(reportCommands, List.of("report", "sheet"), "reportSheet", "generate");
            this.commands.registerMethod(reportCommands, List.of("report", "upload"), "importLegacyBlacklist", "legacy_reports");

            this.commands.registerMethod(reportCommands, List.of("report", "loan"), "addLoan", "add");
            this.commands.registerMethod(reportCommands, List.of("report", "loan"), "updateLoan", "update");
            this.commands.registerMethod(reportCommands, List.of("report", "loan"), "deleteLoan", "remove");
            this.commands.registerMethod(reportCommands, List.of("report", "loan"), "purgeLoans", "purge");
            this.commands.registerMethod(reportCommands, List.of("report", "loan"), "markAllLoansAsUpdated", "update_all");
            this.commands.registerMethod(reportCommands, List.of("report", "loan"), "getLoanSheet", "sheet");
            this.commands.registerMethod(reportCommands, List.of("report", "loan"), "importLoans", "upload");

            this.commands.registerMethod(reportCommands, List.of("report"), "createReport", "add");
            this.commands.registerMethod(reportCommands, List.of("report"), "removeReport", "remove");
            this.commands.registerMethod(reportCommands, List.of("report"), "approveReport", "approve");
            this.commands.registerMethod(reportCommands, List.of("report", "comment"), "comment", "add");
            this.commands.registerMethod(reportCommands, List.of("report"), "purgeReports", "purge");
            this.commands.registerMethod(reportCommands, List.of("report"), "ban", "ban");
            this.commands.registerMethod(reportCommands, List.of("report"), "unban", "unban");
            this.commands.registerMethod(reportCommands, List.of("report"), "searchReports", "search");
            this.commands.registerMethod(reportCommands, List.of("report"), "showReport", "show");
            this.commands.registerMethod(reportCommands, List.of("report"), "riskFactors", "analyze");

            this.commands.registerMethod(reportCommands, List.of("report", "comment"), "removeComment", "delete");
            this.commands.registerMethod(reportCommands, List.of("report", "comment"), "purgeComments", "purge");
        }

        HelpCommands help = new HelpCommands();

        this.commands.registerMethod(help, List.of("help"), "command", "command");
        this.commands.registerMethod(help, List.of("help"), "nation_placeholder", "nation_placeholder");
        this.commands.registerMethod(new GPTCommands(), List.of("help"), "find_argument", "find_argument");
        this.commands.registerMethod(help, List.of("help"), "argument", "argument");


        if (pwgptHandler != null) {
//            this.commands.registerMethod(help, List.of("help"), "find_command", "find_command");
            this.commands.registerMethod(help, List.of("help"), "find_setting", "find_setting");

            this.commands.registerMethod(help, List.of("help"), "moderation_check", "moderation_check");

            GPTCommands gptCommands = new GPTCommands();

            this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "list_documents", "list");
            this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "view_document", "view");
            this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "delete_document", "delete");
            this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "save_embeddings", "import_sheet");
            this.commands.registerMethod(gptCommands, List.of("chat", "providers"), "listChatProviders", "list");
            this.commands.registerMethod(gptCommands, List.of("chat", "providers"), "setChatProviders", "set");
            this.commands.registerMethod(gptCommands, List.of("chat", "providers"), "chatProviderConfigure", "configure");
            this.commands.registerMethod(gptCommands, List.of("chat", "providers"), "chatResume", "resume");
            this.commands.registerMethod(gptCommands, List.of("chat", "providers"), "chatPause", "pause");
            this.commands.registerMethod(gptCommands, List.of("channel", "rename"), "emojifyChannels", "bulk");

            this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "showConverting", "list");
            this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "generate_factsheet", "add_document");
            this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "pauseConversion", "pause");
            this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "resumeConversion", "resume");
            this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "deleteConversion", "delete");

            this.commands.registerMethod(gptCommands, List.of("chat"), "unban", "unban");

            this.commands.registerMethod(gptCommands, List.of("help"), "find_command2", "find_command");

            pwgptHandler.registerDefaults();
        }


        List<String> missing = new ArrayList<>();
        for (Class<?> type : placeholders.getTypes()) {
            Placeholders<?> ph = placeholders.get(type);

            Method methodAlias = null;
            Method methodColumns = null;
            Class<? extends Placeholders> phClass = ph.getClass();
            for (Method method : phClass.getMethods()) {
                if (method.getDeclaringClass() != phClass) continue;
                if (method.getName().equals("addSelectionAlias")) {
                    methodAlias = method;
                } else if (method.getName().equals("addColumns")) {
                    methodColumns = method;
                }
            }
            if (methodAlias == null) {
                missing.add("Missing method `addSelectionAlias` for " + ph.getType().getSimpleName());
                continue;
            }
            if (methodColumns == null) {
                missing.add("Missing method `addColumns` for " + ph.getType().getSimpleName());
                continue;
            }
            String typeName = PlaceholdersMap.getClassName(ph.getType());
            this.commands.registerMethod(ph, List.of("selection_alias", "add"), methodAlias.getName(), typeName);
            this.commands.registerMethod(ph, List.of("sheet_template", "add"), methodColumns.getName(), typeName);
//            for (Method method : ph.getClass().getDeclaredMethods()) {
//                Command cmd = method.getAnnotation(Command.class);
//                if (cmd != null) {
//                    String name = cmd.aliases().length != 0 ? cmd.aliases()[0] : method.getName();
//                    this.commands.registerMethod(ph, List.of("sheets_ia", "custom"), method.getName(), name);
//                }
//            }
        }
        if (!missing.isEmpty()) {
            System.out.println("Missing methods for placeholders:\n- " + String.join("\n- ", missing));
        }

        commands.checkUnregisteredMethods(true);

        return this;
    }

    public YamlConfiguration loadDefaultMapping() {
        File file = new File("config" + File.separator + "commands.yaml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        } else return null;
    }

    public ValueStore<Object> getStore() {
        return store;
    }

    public PermissionHandler getPermisser() {
        return permisser;
    }

    public ValidatorStore getValidators() {
        return validators;
    }

    public NationPlaceholders getNationPlaceholders() {
        return (NationPlaceholders) this.placeholders.get(DBNation.class);
    }

    public AlliancePlaceholders getAlliancePlaceholders() {
        return (AlliancePlaceholders) this.placeholders.get(DBAlliance.class);
    }

    public PlaceholdersMap getPlaceholders() {
        return placeholders;
    }

    public CommandGroup getCommands() {
        return commands;
    }

    public CommandCallable getCallable(List<String> args) {
        return commands.getCallable(args);
    }

    public Map.Entry<CommandCallable, String> getCallableAndPath(List<String> args) {
        CommandCallable root = commands;
        List<String> path = new ArrayList<>();

        Queue<String> stack = new ArrayDeque<>(args);
        while (!stack.isEmpty()) {
            String arg = stack.poll();
            path.add(arg);
            if (root instanceof CommandGroup) {
                root = ((CommandGroup) root).get(arg);
            } else {
                throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommand: " + arg);
            }
        }
        return new AbstractMap.SimpleEntry<>(root, StringMan.join(path, " "));
    }

    public void run(Guild guild, IMessageIO io, User author, String command, boolean async, boolean returnNotFound) {
        String fullCmdStr = DiscordUtil.trimContent(command).trim();
        if (!fullCmdStr.isEmpty() && Locutus.cmd().isModernPrefix(fullCmdStr.charAt(0))) {
            fullCmdStr = fullCmdStr.substring(1);
        }
        Message message = null;
        MessageChannel channel = null;
        if (io instanceof DiscordChannelIO dio) {
            message = dio.getUserMessage();
            channel = dio.getChannel();
        }
        run(guild, channel, author, message, io, fullCmdStr, async, returnNotFound);
    }

    public LocalValueStore createLocals(@Nullable LocalValueStore<Object> existingLocals, @Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, @Nullable Map<String, String> fullCmdStr) {
        if (guild != null) {
            String denyReason = Settings.INSTANCE.MODERATION.BANNED_GUILDS.get(guild.getIdLong());
            if (denyReason != null) {
                throw new IllegalArgumentException("Access-Denied[Guild=" + guild.getIdLong() + "]: " + denyReason);
            }
            long ownerId = guild.getOwnerIdLong();
            if (user == null || user.getIdLong() != ownerId) {
                String userDenyReason = Settings.INSTANCE.MODERATION.BANNED_USERS.get(ownerId);
                if (userDenyReason != null) {
                    String userName = DiscordUtil.getUserName(ownerId);
                    if (!userName.startsWith("<@")) userName += "/" + ownerId;
                    throw new IllegalArgumentException("Access-Denied[User=" + userName + "]: " + userDenyReason);
                }
                DBNation nation = DiscordUtil.getNation(ownerId);
                if (nation != null) {
                    String nationDenyReason = Settings.INSTANCE.MODERATION.BANNED_NATIONS.get(nation.getId());
                    if (nationDenyReason != null) {
                        throw new IllegalArgumentException("Access-Denied[Nation=" + nation.getId() + "]: " + nationDenyReason);
                    }
                }
            }
        }

        LocalValueStore<Object> locals = existingLocals == null ? new LocalValueStore<>(store) : existingLocals;

        locals.addProvider(Key.of(PermissionHandler.class), permisser);
        locals.addProvider(Key.of(ValidatorStore.class), validators);

        if (user != null) {
            String userDenyReason = Settings.INSTANCE.MODERATION.BANNED_USERS.get(user.getIdLong());
            if (userDenyReason != null) {
                throw new IllegalArgumentException("Access-Denied: " + userDenyReason);
            }
            DBNation nation = DiscordUtil.getNation(user);
            if (nation != null) {
                String nationDenyReason = Settings.INSTANCE.MODERATION.BANNED_NATIONS.get(nation.getId());
                if (nationDenyReason != null) {
                    throw new IllegalArgumentException("Access-Denied: " + nationDenyReason);
                }
                String allianceDenyReason = Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.get(nation.getAlliance_id());
                if (allianceDenyReason != null) {
                    throw new IllegalArgumentException("Access-Denied: " + allianceDenyReason);
                }
            }
            locals.addProvider(Key.of(User.class, Me.class), user);
        }

        locals.addProvider(Key.of(IMessageIO.class, Me.class), io);
        if (fullCmdStr != null) {
            locals.addProvider(Key.of(JSONObject.class, Me.class), new JSONObject(fullCmdStr));
        }
        if (channel != null) locals.addProvider(Key.of(MessageChannel.class, Me.class), channel);
        if (message != null) locals.addProvider(Key.of(Message.class, Me.class), message);
        if (guild != null) {
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) locals.addProvider(Key.of(Member.class, Me.class), member);
            }
            locals.addProvider(Key.of(Guild.class, Me.class), guild);
            GuildDB db = Locutus.imp().getGuildDB(guild);
            if (db != null) {
                for (int id : db.getAllianceIds(true)) {
                    String allianceDenyReason = Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.get(id);
                    if (allianceDenyReason != null) {
                        throw new IllegalArgumentException("Access-Denied[Alliance=" + id + "]: " + allianceDenyReason);
                    }
                }
            }
            locals.addProvider(Key.of(GuildDB.class, Me.class), db);
        }
        return locals;
    }

    public void run(@Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, String fullCmdStr, boolean async, boolean returnNotFound) {
        LocalValueStore existingLocals = createLocals(null, guild, channel, user, message, io, null);
        run(existingLocals, io, fullCmdStr, async, returnNotFound);
    }

    public void run(LocalValueStore<Object> existingLocals, IMessageIO io, String fullCmdStr, boolean async, boolean returnNotFound) {
        Runnable task = () -> {
            try {
                if (fullCmdStr.startsWith("{")) {
                    JSONObject json = new JSONObject(fullCmdStr);
                    Map<String, Object> arguments = json.toMap();
                    Map<String, String> stringArguments = new HashMap<>();
                    for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                        stringArguments.put(entry.getKey(), entry.getValue().toString());
                    }

                    String pathStr = arguments.remove("").toString();
                    run(existingLocals, io, pathStr, stringArguments, async);
                    return;
                }
                if (fullCmdStr.isEmpty()) {
                    if (returnNotFound) {
                        io.send("You did not enter a command");
                        return;
                    }
                    return;
                }
                StringBuilder remaining = new StringBuilder();
                CommandCallable callable = commands.getCallable(fullCmdStr, remaining);
                if (callable instanceof CommandGroup group) {
                    if (returnNotFound) {
                        String prefix = group.getFullPath();
                        prefix = "/" + prefix + (prefix.isEmpty() ? "" : " ");
                        if (!remaining.isEmpty()) {
                            String[] lastCommandIdSplit = remaining.toString().split(" ");
                            String lastCommandId = lastCommandIdSplit[0];
                            List<String> validIds = new ArrayList<>(group.primarySubCommandIds());
                            List<String> closest = StringMan.getClosest(lastCommandId, validIds, false);
                            if (closest.size() > 5) closest = closest.subList(0, 5);

                            io.send("No subcommand found for `" + lastCommandId + "`\n" +
                                    "Did you mean:\n- `" + prefix + StringMan.join(closest, "`\n- `" + prefix) +
                                    "`\n\nSee also: " + CM.help.find_command.cmd.toSlashMention());
                        } else {
                            Set<String> options = group.primarySubCommandIds();
                            io.send("No subcommand found for `" + prefix.trim() + "`. Options:\n" +
                                    "`" + prefix + StringMan.join(options, "`\n`" + prefix) + "`");
                        }
                    }
                    return;
                }

                if (!remaining.isEmpty() && callable instanceof ParametricCallable pc) {
                    try {
                        Set<String> params = pc.getUserParameterMap().keySet();
                        Map<String, String> parsed = parseArguments(params, remaining.toString(), false);
                        String pathStr = callable.getFullPath();
                        run(existingLocals, io, pathStr, parsed, async);
                        return;
                    } catch (IllegalArgumentException ignore) {
                        ignore.printStackTrace();
                    }
                }

                List<String> args = remaining.isEmpty() ? new ArrayList<>() : StringMan.split(remaining.toString(), ' ');

                LocalValueStore locals = createLocals(existingLocals, null, null, null, null, io, null);

                if (callable instanceof ParametricCallable parametric) {
                    ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
                    handleCall(io, () -> {
                        try {
                            Map<ParameterData, Map.Entry<String, Object>> map = parametric.parseArgumentsToMap(stack);
                            Object[] parsed = parametric.argumentMapToArray(map);
                            return parametric.call(null, locals, parsed);
                        } catch (RuntimeException e) {
                            Throwable e2 = e;
                            while (e2.getCause() != null && e2.getCause() != e2) e2 = e2.getCause();
                            e2.printStackTrace();
                            throw new CommandUsageException(callable, e2.getMessage());
                        }
                    });
                } else if (callable instanceof CommandGroup group) {
                    handleCall(io, group, locals);
                } else throw new IllegalArgumentException("Invalid command class " + callable.getClass());
            } catch (Throwable e) {
                e.printStackTrace();
            }
        };
        if (async) Locutus.imp().getExecutor().submit(task);
        else task.run();
    }

    public void run(@Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, String path, Map<String, String> arguments, boolean async) {
        LocalValueStore existingLocals = createLocals(null, guild, channel, user, message, io, null);
        run(existingLocals, io, path, arguments, async);
    }

    public void run(LocalValueStore<Object> existingLocals, IMessageIO io, String path, Map<String, String> arguments, boolean async) {
        Runnable task = () -> {
            try {
                CommandCallable callable = commands.get(Arrays.asList(path.split(" ")));
                if (callable == null) {
                    System.out.println("No cmd found for " + path);
                    io.create().append("No command found for " + path).send();
                    return;
                }

                Map<String, String> argsAndCmd = new HashMap<>(arguments);
                argsAndCmd.put("", path);
                Map<String, String> finalArguments = new LinkedHashMap<>(arguments);
                finalArguments.remove("");

                LocalValueStore<Object> finalLocals = createLocals(existingLocals, null, null, null, null, io, argsAndCmd);
                if (callable instanceof ParametricCallable parametric) {
                    handleCall(io, () -> {
                        try {
                            if (parametric.getAnnotations().stream().noneMatch(a -> a instanceof NoFormat)) {
                                DBNation me = finalLocals.getProvided(Key.of(DBNation.class, Me.class), false);
                                if (me != null) {
                                    for (Map.Entry<String, String> entry : finalArguments.entrySet()) {
                                        String key = entry.getKey();
                                        String value = entry.getValue();
                                        if (value.contains("{") && value.contains("}")) {
                                            value = getNationPlaceholders().format2(finalLocals, value, me, false);
                                            entry.setValue(value);
                                        }
                                    }
                                }
                            }
                            Object[] parsed = parametric.parseArgumentMap(finalArguments, finalLocals, validators, permisser);
                            return parametric.call(null, finalLocals, parsed);
                        } catch (RuntimeException e) {
                            Throwable e2 = e;
                            while (e2.getCause() != null && e2.getCause() != e2) e2 = e2.getCause();
                            e2.printStackTrace();
                            e.printStackTrace();
                            throw new CommandUsageException(callable, e2.getMessage());
                        }
                    });
                } else if (callable instanceof CommandGroup group) {
                    handleCall(io, group, finalLocals);
                } else {
                    System.out.println("Invalid command class " + callable.getClass());
                    throw new IllegalArgumentException("Invalid command class " + callable.getClass());
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        };
        if (async) Locutus.imp().getExecutor().submit(task);
        else task.run();
    }

    private void handleCall(IMessageIO io, Supplier<Object> call) {
        try {
            try {
                Object result = call.get();
                if (result != null) {
                    io.create().append(result.toString()).send();
                }
            } catch (CommandUsageException e) {
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                root.printStackTrace();

                StringBuilder body = new StringBuilder();

                if (e.getMessage() != null && (e.getMessage().contains("`") || e.getMessage().contains("<#") || e.getMessage().contains("</") || e.getMessage().contains("<@"))) {
                    body.append("## Error:\n");
                    body.append(">>> " + e.getMessage() + "\n");
                } else {
                    body.append("```ansi\n" + StringMan.ConsoleColors.RESET + StringMan.ConsoleColors.WHITE_BOLD + StringMan.ConsoleColors.RED_BACKGROUND);
                    body.append(e.getMessage());
                    body.append("```\n");
                }

                body.append("## Usage:\n");
                CommandCallable command = e.getCommand();
                String title = "Error Running: /" + command.getFullPath(" ");
                if (command instanceof ICommand icmd) {
                    body.append(icmd.toBasicMarkdown(store, permisser, "/", false, true, true));
                } else {
                    String help = command.help(store);
                    String desc = command.desc(store);
                    if (help != null) {
                        body.append("`/").append(help).append("`");
                    }
                    if (desc != null && !desc.isEmpty()) {
                        body.append("\n").append(desc);
                    }
                }

                io.create().embed(title, body.toString()).send();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                io.create().append(e.getMessage()).send();
            } catch (Throwable e) {
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();

                root.printStackTrace();
                io.create().append("Error: " + root.getMessage()).send();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void handleCall(IMessageIO io, CommandGroup group, LocalValueStore store) {
        handleCall(io, () -> group.call(new ArgumentStack(new ArrayList<>(), store, validators, permisser)));
    }

    public Map<String, String> validateSlashCommand(String input, boolean strict) {
        String original = input;
        CommandGroup root = commands;
        while (true) {
            int index = input.indexOf(' ');
            if (index < 0)
                throw new IllegalArgumentException("No parametric command found for " + original + " only found root: " + root.getFullPath());
            String arg0 = input.substring(0, index);
            input = input.substring(index + 1).trim();

            CommandCallable next = root.get(arg0);
            if (next instanceof ParametricCallable parametric) {
                if (!input.isEmpty()) {
                    return parseArguments(parametric.getUserParameterMap().keySet(), input, strict);
                }
                return new HashMap<>();
            } else if (next instanceof CommandGroup group) {
                root = group;
            } else if (next == null) {
                throw new IllegalArgumentException("No parametric command found for " + original + " (" + arg0 + ")");
            } else {
                throw new UnsupportedOperationException("Invalid command class " + next.getClass());
            }
        }
    }
}
