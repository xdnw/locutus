package link.locutus.discord.commands.manager.v2.impl.pw;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.SelectorInfo;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.CommandMessagePriority;
import link.locutus.discord.commands.manager.v2.command.CommandTextParser;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.WebOption;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWAppBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.ResearchCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeServices;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeStoreBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.config.yaml.file.YamlConfiguration;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.menu.AppMenu;
import link.locutus.discord.db.entities.menu.MenuState;
import link.locutus.discord.db.entities.metric.GrowthAsset;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;
import link.locutus.discord.db.guild.GuildSettingSubgroup;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CommandManager2 {
    private final CommandGroup commands;
    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final PlaceholdersMap placeholders;
    private final CommandRuntimeServices runtimeServices;
    private PWGPTHandler pwgptHandler;

    public Map<String, Object> toJson(ValueStore htmlOptionsStore, PermissionHandler permHandler) {
        Map<String, Object> cmdJson = commands.toJson(permHandler, false);

        Map<String, Map<String, Object>> keysData = new Object2ObjectLinkedOpenHashMap<>();
        Set<String> checkedOptions = new ObjectLinkedOpenHashSet<>();
        Map<String, Object> optionsData = new Object2ObjectLinkedOpenHashMap<>();

        Set<Parser<?>> parsers = new ObjectLinkedOpenHashSet<>();
        Consumer<CommandGroup> addParsers = group -> {
            for (ParametricCallable<?> callable : group.getParametricCallables(Predicates.alwaysTrue())) {
                for (ParameterData param : callable.getUserParameters()) {
                    Parser<?> parser = param.getBinding();
                    Class<?>[] webType = parser.getWebType();
                    if (webType == null || webType.length == 0) {
                        parsers.add(parser);
                    }
                }
            }
        };
        addParsers.accept(commands);
        WebRoot web = WebRoot.getInstance();
        if (web != null) {
            addParsers.accept(web.getPageHandler().getCommands());
        }
        { // Manually add parsers
            List<Class<?>> manual = List.of(GuildSettingCategory.class, GuildSettingSubgroup.class, AlliancePermission.class, GrowthAsset.class, OnlineStatus.class);
            for (Class<?> t : manual) {
                Parser<?> parser = store.get(Key.of(t));
                if (parser != null) {
                    parsers.add(parser);
                } else {
                    Logg.info("No parser for " + t.getSimpleName());
                }
            }
        }

        for (Parser<?> parser : parsers) {
            Key<?> key = parser.getKey();
            Map<String, Object> typeJson = parser.toJson();
            keysData.put(key.toSimpleString(), typeJson);
            Parser<?> optionParser = htmlOptionsStore.get(key);
            if (optionParser != null) {
                WebOption option = (WebOption) optionParser.apply(store, null);
                optionsData.computeIfAbsent(option.getName(), k -> option.toJson());
                continue;
            }
            List<Class> components = WebOption.getComponentClasses(key.getType());
            if (components.isEmpty()) {
                Logg.info("Web: No components for " + key.toSimpleString());
                continue;
            }
            for (Class t : components) {
                String name = t.getSimpleName();
                if (!checkedOptions.add(name))
                    continue;
                if (t.isEnum()) {
                    WebOption option = WebOption.fromEnum(t);
                    optionsData.computeIfAbsent(option.getName(), k -> option.toJson());
                    continue;
                }
                Key<Object> optionsKey = Key.of(t);
                optionParser = htmlOptionsStore.get(optionsKey);
                if (optionParser != null) {
                    WebOption option = (WebOption) optionParser.apply(htmlOptionsStore, null);
                    if (!option.getName().equalsIgnoreCase(name)) {
                        optionsData.put(name, option.getName());
                    } else {
                        optionsData.computeIfAbsent(option.getName(), k -> option.toJson());
                    }
                } else {
                    Logg.info("No options for " + name + " | " + key.toSimpleString());
                }
            }
        }

        Map<String, Object> phRoot = new LinkedHashMap<>();
        List<Class<?>> phTypesSorted = placeholders.getTypes().stream()
                .sorted(Comparator.comparing(PlaceholdersMap::getClassName)).toList();
        for (Class<?> t : phTypesSorted) {
            Placeholders<?, ?> ph = placeholders.get(t);
            Map<String, Object> json = new LinkedHashMap<>();

            Map<String, Object> bindings = ph.getCommands().toJson(permHandler, true);
            json.put("commands", bindings);

            ParametricCallable create = ph.getCreateModifier();
            if (create != null) {
                json.put("create", create.toJson(permHandler, false));
            }

            Set<SelectorInfo> selectors = ph.getSelectorInfo();
            List<String[]> arr = selectors.stream().map(f -> new String[] { f.format(), f.example(), f.desc() })
                    .toList();
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
        this(LocutusCommandRuntimeServiceFactory.create(Locutus.imp()));
    }

    public CommandManager2(CommandRuntimeServices runtimeServices) {
        this.store = PWBindings.createDefaultStore();
        new PWAppBindings().register(this.store);
        this.validators = PWBindings.createDefaultValidators();
        this.permisser = PWBindings.createDefaultPermisser();
        this.commands = CommandGroup.createRoot(store, validators);
        this.runtimeServices = runtimeServices.withCommandRuntime(
                this.commands::getCallable,
                this::validateSlashCommand,
                () -> this.commands.getParametricCallables(Predicates.alwaysTrue()));
        this.placeholders = new PlaceholdersMap(store, validators, permisser, this.runtimeServices).initAppCommands();

        CommandRuntimeStoreBindings.register(this.store, this.runtimeServices);
        this.store.addLazyProvider(Key.of(CommandManager2.class), () -> this);
        this.store.addProvider(Key.of(PermissionHandler.class), this.permisser);
        this.store.addProvider(Key.of(ValidatorStore.class), this.validators);

        if (Settings.INSTANCE.ENABLED_COMPONENTS.ARTIFICIAL_INTELLIGENCE) {
            try {
                pwgptHandler = new PWGPTHandler(this);
                this.store.addProvider(Key.of(PWGPTHandler.class), pwgptHandler);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public PWGPTHandler getGptHandler() {
        return pwgptHandler;
    }

    public static Map<String, String> parseArguments(Set<String> params, String input, boolean checkUnbound) {
        return CommandTextParser.parseArguments(params, input, checkUnbound);
    }

    public CommandManager2 registerDefaults() {
        this.commands.registerCommandsWithMapping(CM.class);

        this.commands.registerMethod(new FACommands(), List.of("coalitions"), "renameCoalition", "rename");

        this.commands.registerMethod(new ConflictCommands(), List.of("conflict", "sync"), "importCloudData",
                "cloud_providers");

        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync2"), "clearAllApiKeys",
                "clearAllApiKeys");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync2"), "clearInvalidAccounts",
                "clearInvalidAccounts");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync2"), "fixCashConversion",
                "fixCashConversion");
        this.commands.registerMethod(new AdminCommands(), List.of("sheets_econ"), "conversionRates",
                "conversion_rates");

        this.commands.registerMethod(new TradeCommands(), List.of("trade", "create"), "createSell", "sell");
        this.commands.registerMethod(new TradeCommands(), List.of("trade", "create"), "createBuy", "buy");
        this.commands.registerMethod(new TradeCommands(), List.of("trade", "create"), "undercutSell", "undercut_sell");
        this.commands.registerMethod(new TradeCommands(), List.of("trade", "create"), "undercutBuy", "undercut_buy");

        getCommands().registerMethod(new GrantCommands(), List.of("grant", "request"), "grantRequest", "create");
        getCommands().registerMethod(new GrantCommands(), List.of("grant", "request"), "grantRequestCancel", "cancel");
        getCommands().registerMethod(new GrantCommands(), List.of("grant", "request"), "grantRequestApprove",
                "approve");

        getCommands().registerMethod(new IACommands(), List.of("build"), "matchBuildSheet", "matches_sheet");
        getCommands().registerMethod(new UtilityCommands(), List.of("project"), "projectROI", "roi");
        getCommands().registerMethod(new GrantCommands(), List.of("grant_template", "create"), "templateCreateResearch",
                "research");
        getCommands().registerMethod(new GrantCommands(), List.of("grant"), "grantResearch", "research");

        getCommands().registerMethod(new WarCommands(), List.of("war"), "warRange", "range");

        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "syncCityRefund", "city_refund");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "reloadConfig", "config");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "cullInactiveGuilds",
                "cull_inactive_guilds");

        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "alliance"), "addAllForNation",
                "add_all_for_nation");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "addManualWars",
                "add_none_war");
        getCommands().registerMethod(new ConflictCommands(), List.of("conflict", "edit"), "removeAnnouncement",
                "remove_forum_post");
        getCommands().registerMethod(new SettingCommands(), List.of("bank"), "importTransactions", "import_transfers");
        getCommands().registerMethod(new AppMenuCommands(), List.of("menu"), "info", "info");
        getCommands().registerMethod(new AppMenuCommands(), List.of("menu"), "list", "list");
        getCommands().registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "fromFile", "from_file");
        getCommands().registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "autoTab", "auto_tab");

        getCommands().registerMethod(new StatCommands(), List.of("alliance", "stats"), "compareStats",
                "coalition_metric_by_turn");
        getCommands().registerMethod(new StatCommands(), List.of("alliance", "stats"), "allianceStats",
                "metrics_by_turn");
        getCommands().registerMethod(new StatCommands(), List.of("alliance", "stats"), "compareTierStats",
                "tier_by_coalition");

        getCommands().registerMethod(new StatCommands(), List.of("stats", "other"), "compareStats",
                "coalition_metric_by_turn");
        getCommands().registerMethod(new StatCommands(), List.of("stats", "other"), "allianceStats",
                "aa_metrics_by_turn");
        getCommands().registerMethod(new StatCommands(), List.of("stats", "tier"), "compareTierStats",
                "tier_by_coalition");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "bot"), "setProfile", "profile");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "bot"), "setBotName", "rename");

        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "runMilitarizationAlerts",
                "militarization_alerts");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "checkActiveConflicts",
                "active_conflicts");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "dumpWiki", "export_wiki");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "saveWebPojos", "web_pojos");

        getCommands().registerMethod(new AdminCommands(), List.of("admin", "debug"), "apiUsageStats", "api_usage");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "bot"), "removeInvalidOffshoring",
                "remove_deleted_offshores");
        getCommands().registerMethod(new DiscordCommands(), List.of("admin", "bot"), "importEmojis", "import_emojis");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "bot"), "importGuildKeys",
                "import_settings");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "list"), "listAuthenticated",
                "authenticated");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "list"), "listExpiredGuilds",
                "expired_guilds");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "list"), "listExpiredOffshores",
                "expired_offshores");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "list"), "listGuildOwners", "guild_owners");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "list"), "loginTimes", "login_times");
        getCommands().registerMethod(new IACommands(), List.of("admin", "debug"), "msgInfo", "msg_info");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "bot"), "stop", "stop");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "command"), "sudoNations", "sudo_nations");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "command"), "sudo", "sudo");

        getCommands().registerMethod(new AdminCommands(), List.of("research"), "updateResearch", "sync");
        getCommands().registerMethod(new ResearchCommands(), List.of("research"), "researchCost", "cost");
        getCommands().registerMethod(new ResearchCommands(), List.of("research"), "getResearch", "view_nation");
        getCommands().registerMethod(new ResearchCommands(), List.of("research"), "researchSheet", "sheet");
        getCommands().registerMethod(new ResearchCommands(), List.of("research"), "researchCityTable", "table");

        getCommands().registerMethod(new AdminCommands(), List.of("admin", "debug"), "newOffshore", "new_offshore");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "bot"), "upsertCommands", "update_commands");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "syncCityAvg", "city_avg");
        getCommands().registerMethod(new AdminCommands(), List.of("admin", "sync2"), "syncAlliances", "alliances");
        getCommands().registerMethod(new UnsortedCommands(), List.of("alliance", "stats"), "compareAlliancePositions",
                "compare_past_positions");
        getCommands().registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "importSheetJsonColumns",
                "import_json");

        for (GuildSetting setting : GuildKey.values()) {
            List<String> path = List.of("settings_" + setting.getCategory().name().toLowerCase(Locale.ROOT));

            Class<? extends GuildSetting> settingClass = setting.getClass();
            Method[] methods = settingClass.getMethods();
            Map<String, String> methodNameToCommandName = new HashMap<>();
            for (Method method : methods) {
                if (method.getDeclaringClass() != settingClass)
                    continue;
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

        HelpCommands help = new HelpCommands();
        if (pwgptHandler != null) {
            // this.commands.registerMethod(help, List.of("help"), "find_command",
            // "find_command");
            this.commands.registerMethod(help, List.of("help"), "find_setting", "find_setting");

            this.commands.registerMethod(help, List.of("help"), "moderation_check", "moderation_check");

            GPTCommands gptCommands = new GPTCommands();
            // this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "embeddingSelect", "select");
            // this.commands.registerMethod(gptCommands, List.of("help"), "find_placeholder", "find_nation_placeholder");
            // this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "list_documents", "list");
            // this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "view_document", "view");
            // this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "delete_document", "delete");
            // this.commands.registerMethod(gptCommands, List.of("chat", "dataset"), "save_embeddings", "import_sheet");
            // this.commands.registerMethod(gptCommands, List.of("chat", "providers"), "chatResume", "resume");
            // this.commands.registerMethod(gptCommands, List.of("chat", "providers"), "chatPause", "pause");
            // this.commands.registerMethod(gptCommands, List.of("channel", "rename"), "emojifyChannels", "bulk");
            // this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "showConverting", "list");
            // this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "generate_factsheet",
            //         "add_document");
            // this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "pauseConversion", "pause");
            // this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "resumeConversion", "resume");
            // this.commands.registerMethod(gptCommands, List.of("chat", "conversion"), "deleteConversion", "delete");
            // this.commands.registerMethod(gptCommands, List.of("chat"), "unban", "unban");

            this.commands.registerMethod(gptCommands, List.of("help"), "find_command2", "find_command");

            try {
                pwgptHandler.registerDefaults(placeholders);
            } catch (Throwable e) {
                e.printStackTrace();
                Logg.text("Cannot register AI commands: " + e.getMessage());
            }
        }

        List<String> missing = new ArrayList<>();
        for (Class<?> type : placeholders.getTypes()) {
            Placeholders<?, ?> ph = placeholders.get(type);

            Method methodAlias = null;
            Method methodColumns = null;
            Class<? extends Placeholders> phClass = ph.getClass();
            for (Method method : phClass.getMethods()) {
                if (method.getDeclaringClass() != phClass)
                    continue;
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
        }
        if (!missing.isEmpty()) {
            Logg.info("Missing methods for placeholders:\n- " + String.join("\n- ", missing));
        }

        commands.checkUnregisteredMethods(false);

        return this;
    }

    public YamlConfiguration loadDefaultMapping() {
        File file = new File("config" + File.separator + "commands.yaml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        } else
            return null;
    }

    public ValueStore getStore() {
        return store;
    }

    public PermissionHandler getPermisser() {
        return permisser;
    }

    public CommandRuntimeServices getCommandRuntimeServices() {
        return runtimeServices;
    }

    public ValidatorStore getValidators() {
        return validators;
    }

    public NationPlaceholders getNationPlaceholders() {
        return (NationPlaceholders) (Placeholders) this.placeholders.get(DBNation.class);
    }

    public AlliancePlaceholders getAlliancePlaceholders() {
        return (AlliancePlaceholders) (Placeholders) this.placeholders.get(DBAlliance.class);
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

    public LocalValueStore createLocals(@Nullable LocalValueStore existingLocals, @Nullable Guild guild,
            @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io,
            @Nullable Map<String, String> fullCmdStr) {
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
                    if (!userName.startsWith("<@"))
                        userName += "/" + ownerId;
                    throw new IllegalArgumentException("Access-Denied[User=" + userName + "]: " + userDenyReason);
                }
                DBNation nation = DiscordUtil.getNation(ownerId);
                if (nation != null) {
                    String nationDenyReason = Settings.INSTANCE.MODERATION.BANNED_NATIONS.get(nation.getId());
                    if (nationDenyReason != null) {
                        throw new IllegalArgumentException(
                                "Access-Denied[Nation=" + nation.getId() + "]: " + nationDenyReason);
                    }
                }
            }
        }

        LocalValueStore locals = existingLocals == null ? new LocalValueStore(store) : existingLocals;

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
        if (channel != null)
            locals.addProvider(Key.of(MessageChannel.class, Me.class), channel);
        if (message != null)
            locals.addProvider(Key.of(Message.class, Me.class), message);
        if (guild != null) {
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null)
                    locals.addProvider(Key.of(Member.class, Me.class), member);
            }
            locals.addProvider(Key.of(Guild.class, Me.class), guild);
            GuildDB db = runtimeServices.getGuildDb(guild);
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

    public LocalValueStore createExecutionContext(@Nullable Guild guild, IMessageIO io,
            @Nullable User author, @Nullable DBNation me) {
        DBNation nation = me != null ? me : (author == null ? null : DBNation.getByUser(author));
        User user = author != null ? author : (nation == null ? null : nation.getUser());

        LocalValueStore locals = createLocals(null, guild, null, user, null, io, null);
        if (nation != null) {
            locals.addProvider(Key.of(DBNation.class, Me.class), nation);
        }
        return locals;
    }

    public void run(@Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user,
            @Nullable Message message, IMessageIO io, String fullCmdStr, boolean async, boolean returnNotFound) {
        LocalValueStore existingLocals = createLocals(null, guild, channel, user, message, io, null);
        run(existingLocals, io, fullCmdStr, async, returnNotFound);
    }

    public void run(LocalValueStore existingLocals, IMessageIO io, String fullCmdStr, boolean async,
            boolean returnNotFound) {
        Runnable task = () -> {
            try {
                if (fullCmdStr.startsWith("{")) {
                    try {
                        JSONObject json = new JSONObject(fullCmdStr);
                        Map<String, Object> arguments = json.toMap();
                        Map<String, String> stringArguments = new HashMap<>();
                        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                            stringArguments.put(entry.getKey(), entry.getValue().toString());
                        }
                        String pathStr = arguments.remove("").toString();
                        run(existingLocals, io, pathStr, stringArguments, async);
                        return;
                    } catch (JSONException e) {
                        sendResult(io, "Invalid JSON command input: " + e.getMessage());
                        return;
                    }
                }
                if (fullCmdStr.isEmpty()) {
                    if (returnNotFound) {
                        sendResult(io, "You did not enter a command");
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
                            if (closest.size() > 5)
                                closest = closest.subList(0, 5);

                            sendResult(io, "No subcommand found for `" + lastCommandId + "`\n" +
                                    "Did you mean:\n- `" + prefix + StringMan.join(closest, "`\n- `" + prefix) +
                                    "`\n\nSee also: " + CM.help.find_command.cmd.toSlashMention());
                        } else {
                            Set<String> options = group.primarySubCommandIds();
                            sendResult(io, "No subcommand found for `" + prefix.trim() + "`. Options:\n" +
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

                List<String> args = remaining.isEmpty() ? new ArrayList<>()
                        : StringMan.split(remaining.toString(), ' ');

                LocalValueStore locals = createLocals(existingLocals, null, null, null, null, io, null);

                if (callable instanceof ParametricCallable parametric) {
                    ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
                    handleCall(io, () -> {
                        try {
                            Map<ParameterData, Map.Entry<String, Object>> map = parametric.parseArgumentsToMap(stack);
                            Object[] parsed = parametric.argumentMapToArray(map);
                            User user = (User) locals.getProvided(Key.of(User.class, Me.class), false);
                            Logg.info("User `" + user + "`: " + fullCmdStr + " in guild " + io.getGuildOrNull());
                            return parametric.call(null, locals, parsed);
                        } catch (RuntimeException e) {
                            Throwable e2 = e;
                            while (e2.getCause() != null && e2.getCause() != e2)
                                e2 = e2.getCause();
                            e2.printStackTrace();
                            throw new CommandUsageException(callable, e2.getMessage());
                        }
                    });
                } else if (callable instanceof CommandGroup group) {
                    handleCall(io, group, locals);
                } else
                    throw new IllegalArgumentException("Invalid command class " + callable.getClass());
            } catch (Throwable e) {
                e.printStackTrace();
            }
        };
        if (async)
            Locutus.imp().getExecutor().submit(task);
        else
            task.run();
    }

    public void run(@Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user,
            @Nullable Message message, IMessageIO io, String path, Map<String, String> arguments, boolean async) {
        LocalValueStore existingLocals = createLocals(null, guild, channel, user, message, io, null);
        run(existingLocals, io, path, arguments, async);
    }

    public void run(LocalValueStore existingLocals, IMessageIO io, String path, Map<String, String> arguments,
            boolean async) {
        Runnable task = () -> {
            try {
                CommandCallable callable = commands.get(Arrays.asList(path.split(" ")));
                if (callable == null) {
                    Logg.info("User attempted invalid command: " + path);
                    io.create().append("No command found for " + path).send(CommandMessagePriority.RESULT);
                    return;
                }

                Map<String, String> argsAndCmd = new HashMap<>(arguments);
                argsAndCmd.put("", path);
                Map<String, String> finalArguments = new LinkedHashMap<>(arguments);
                finalArguments.remove("");

                LocalValueStore finalLocals = createLocals(existingLocals, null, null, null, null, io,
                        argsAndCmd);
                if (callable instanceof ParametricCallable parametric) {

                    User user = finalLocals.getProvided(Key.of(User.class, Me.class), false);
                    if (handleMenu(io, user, io.getGuildOrNull(), argsAndCmd)) {
                        return;
                    }

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
                            long startParse = System.currentTimeMillis();
                            Object[] parsed = parametric.parseArgumentMap(finalArguments, finalLocals, validators,
                                    permisser);
                            long parseDiff = System.currentTimeMillis() - startParse;
                            String timeStr = (parseDiff >= 1000 ? "(took: " + (parseDiff / 1000.0) + "s)" : "");
                            Logg.info("User `" + user + "` execute command " + parametric.getFullPath() + " with args "
                                    + finalArguments + timeStr + " in guild " + io.getGuildOrNull());
                            return parametric.call(null, finalLocals, parsed);
                        } catch (RuntimeException e) {
                            Throwable e2 = e;
                            while (e2.getCause() != null && e2.getCause() != e2)
                                e2 = e2.getCause();
                            e2.printStackTrace();
                            e.printStackTrace();
                            throw new CommandUsageException(callable, e2.getMessage());
                        }
                    });
                } else if (callable instanceof CommandGroup group) {
                    handleCall(io, group, finalLocals);
                } else {
                    Logg.error("Invalid command class " + callable.getClass());
                    throw new IllegalArgumentException("Invalid command class " + callable.getClass());
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        };
        if (async)
            Locutus.imp().getExecutor().submit(task);
        else
            task.run();
    }

    private void sendResult(IMessageIO io, String message) {
        io.send(message, CommandMessagePriority.RESULT);
    }

    private boolean handleMenu(IMessageIO io, User user, Guild guild, Map<String, String> argsAndCmd) {
        if (guild == null || user == null)
            return false;
        AppMenu menu = AppMenuCommands.USER_MENU_STATE.get(user.getIdLong());
        if (menu == null || menu.state != MenuState.ADD_BUTTON)
            return false;
        try {
            long channelId = io.getIdLong();
            if (channelId == 0 || channelId != menu.lastUsedChannel) {
                sendResult(io, "Aborted command. You had a menu open in a different channel. The menu is no longer in ADD BUTTON mode. Please enter BUTTON ADD mode and try again.");
                return true;
            }
            if (!Roles.ADMIN.has(user, guild)) {
                io.create().append("Aborted command. You do not have permission to add buttons to menus.").send(CommandMessagePriority.RESULT);
                return true;
            }
            handleCall(io, () -> {
                GuildDB db = runtimeServices.getGuildDb(guild);
                String cmd = WebUtil.GSON.toJson(argsAndCmd);
                AppMenuCommands.addMenuButton(io, null, db, user, menu, menu.lastPressedButton, cmd, true);
                return null;
            });
        } finally {
            AppMenuCommands.USER_MENU_STATE.remove(user.getIdLong());
        }
        return true;
    }

    private void handleCall(IMessageIO io, Supplier<Object> call) {
        try {
            try {
                Object result = call.get();
                if (result != null) {
                    io.create().append(result.toString()).send(CommandMessagePriority.RESULT);
                }
            } catch (CommandUsageException e) {
                Throwable root = e;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                root.printStackTrace();

                StringBuilder body = new StringBuilder();

                if (e.getMessage() != null && (e.getMessage().contains("`") || e.getMessage().contains("<#")
                        || e.getMessage().contains("</") || e.getMessage().contains("<@"))) {
                    body.append("## Error:\n");
                    body.append(">>> " + e.getMessage() + "\n");
                } else {
                    body.append("```ansi\n" + StringMan.ConsoleColors.RESET + StringMan.ConsoleColors.WHITE_BOLD
                            + StringMan.ConsoleColors.RED_BACKGROUND);
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

                io.create().embed(title, body.toString()).send(CommandMessagePriority.RESULT);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                io.create().append(e.getMessage()).send(CommandMessagePriority.RESULT);
            } catch (Throwable e) {
                Throwable root = e;
                while (root.getCause() != null)
                    root = root.getCause();

                root.printStackTrace();
                io.create().append("Error: " + root.getMessage()).send(CommandMessagePriority.RESULT);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void handleCall(IMessageIO io, CommandGroup group, LocalValueStore store) {
        handleCall(io, () -> group.call(new ArgumentStack(new ArrayList<>(), store, validators, permisser)));
    }

    public Map<String, String> validateSlashCommand(String input, boolean strict) {
        return commands.validateSlashCommand(input, strict);
    }

    public void handleLanguage(Guild guild, IMessageIO channel, User msgUser, String content) {
        // if (guild == null) return;
        // PWGPTHandler gpt = getPwgptHandler();
        // if (gpt == null) return;
        // LimitManager limitManager = gpt.getLimitManager();
        //
        // GuildDB db = Locutus.imp().getGuildDB(guild);
        //
        // GptLimitTracker limitTracker = limitManager.getLimitTracker(db);
        // result = limitTracker.submit(db, msgUser, nation, content);
    }
}
