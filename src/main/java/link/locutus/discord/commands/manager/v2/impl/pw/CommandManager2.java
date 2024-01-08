package link.locutus.discord.commands.manager.v2.impl.pw;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
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
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ReflectionUtil;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static link.locutus.discord.util.StringMan.isQuote;

public class CommandManager2 {
    private final CommandGroup commands;
    private final ValueStore<Object> store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final PlaceholdersMap placeholders;
    private PWGPTHandler pwgptHandler;

    public CommandManager2() {
        this.store = new SimpleValueStore<>();
        new PrimitiveBindings().register(store);
        new DiscordBindings().register(store);
        new PWBindings().register(store);
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
        getCommands().registerMethod(new AllianceMetricCommands(), List.of("admin", "sync"), "saveMetrics", "saveMetrics");
        getCommands().registerMethod(new AllianceMetricCommands(), List.of("stats_tier", "metricByGroup"), "metricByGroup", "metricByGroup");
        getCommands().registerMethod(new AllianceMetricCommands(), List.of("stats_other", "data_csv"), "AlliancesDataByDay", "AlliancesDataByDay");

        getCommands().registerMethod(new PlayerSettingCommands(), List.of("alerts", "bank"), "bankAlertRequiredValue", "min_value");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncWarrooms", "warrooms");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "queue"), "conditionalMessageSettings", "custom_messages");

        getCommands().registerMethod(new UtilityCommands(), List.of("announcement"), "addWatermark", "watermark");
        getCommands().registerMethod(new WarCommands(), List.of("war", "sheet"), "raidSheet", "raid");

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
        this.commands.registerMethod(new TestCommands(), List.of("deposits"), "viewFlow", "flows");
        this.commands.registerMethod(new TestCommands(), List.of("deposits"), "shiftFlow", "shiftFlow");

        this.commands.registerCommandsWithMapping(CM.class, false, false);

        this.commands.registerMethod(new UtilityCommands(), List.of("building"), "buildingCost", "cost");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncBans", "bans");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "savePojos", "pojos");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "list"), "hasSameNetworkAsBan", "multis");

        this.commands.registerMethod(new GPTCommands(), List.of("help"), "find_placeholder", "find_nation_placeholder");
        this.commands.registerMethod(new BankCommands(), List.of("escrow"), "escrowSheetCmd", "view_sheet");

        this.commands.registerMethod(new IACommands(), List.of("nation", "list"), "viewBans", "bans");

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

        for (GuildSetting setting : GuildKey.values()) {
            List<String> path = List.of("settings_" + setting.getCategory().name().toLowerCase(Locale.ROOT));

            Method[] methods = setting.getClass().getDeclaredMethods();
            Map<String, String> methodNameToCommandName = new HashMap<>();
            for (Method method : methods) {
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
            this.commands.registerMethod(help, List.of("help"), "query", "query");

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
            for (Method method : ph.getClass().getDeclaredMethods()) {
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
            System.out.println("Registering " + typeName);
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
        System.out.println("remove:|| full " + fullCmdStr);
        Message message = null;
        MessageChannel channel = null;
        if (io instanceof DiscordChannelIO dio) {
            message = dio.getUserMessage();
            channel = dio.getChannel();
        }
        run(guild, channel, author, message, io, fullCmdStr, async, returnNotFound);
    }

    private LocalValueStore createLocals(@Nullable LocalValueStore<Object> existingLocals, @Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, @Nullable Map<String, String> fullCmdStr) {
        if (guild != null && Settings.INSTANCE.MODERATION.BANNED_GUILDS.contains(guild.getIdLong()))
            throw new IllegalArgumentException("Unsupported");

        LocalValueStore<Object> locals = existingLocals == null ? new LocalValueStore<>(store) : existingLocals;

        locals.addProvider(Key.of(PermissionHandler.class), permisser);
        locals.addProvider(Key.of(ValidatorStore.class), validators);

        if (user != null) {
            if (Settings.INSTANCE.MODERATION.BANNED_USERS.contains(user.getIdLong()))
                throw new IllegalArgumentException("Unsupported");
            DBNation nation = DiscordUtil.getNation(user);
            if (nation != null) {
                if (Settings.INSTANCE.MODERATION.BANNED_NATIONS.contains(nation.getId())
                        || Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(nation.getAlliance_id())) {
                    throw new IllegalArgumentException("Unsupported");
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
                    if (Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(id))
                        throw new IllegalArgumentException("Unsupported");
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
                if (callable instanceof CommandGroup group && !remaining.isEmpty()) {
                    if (returnNotFound) {
                        String commandId = fullCmdStr.replace(remaining.toString(), "");
                        if (commandId.isEmpty()) {
                            commandId = fullCmdStr.split(" ")[0];
                        }
                        // last string in split by space
                        String[] lastCommandIdSplit = commandId.split(" ");
                        String lastCommandId = lastCommandIdSplit[lastCommandIdSplit.length - 1];
                        List<String> validIds = new ArrayList<>(group.primarySubCommandIds());
                        List<String> closest = StringMan.getClosest(lastCommandId, validIds, false);
                        if (closest.size() > 5) closest = closest.subList(0, 5);

                        io.send("No command found for `" + commandId + "`\n" +
                                "Did you mean:\n- " + group.getFullPath() + StringMan.join(closest, "\n- " + group.getFullPath()) +
                                "\n\nSee also: " + CM.help.find_command.cmd.toSlashMention());
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
                System.out.println("Had usage exception");
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

    private void handleCall(IMessageIO io, CommandGroup group, ValueStore store) {
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
