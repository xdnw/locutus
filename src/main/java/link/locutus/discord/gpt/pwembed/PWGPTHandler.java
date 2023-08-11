package link.locutus.discord.gpt.pwembed;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.knuddels.jtokkit.api.ModelType;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.gpt.imps.EmbeddingInfo;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.imps.GPTText2Text;
import link.locutus.discord.gpt.imps.IEmbeddingAdapter;
import link.locutus.discord.gpt.imps.IText2Text;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public class PWGPTHandler {

    private final GptHandler handler;
    private final BiMap<EmbeddingType, EmbeddingSource> sourceMap = HashBiMap.create();
    private final Map<EmbeddingSource, IEmbeddingAdapter<?>> adapterMap2 = new HashMap<>();
    private final CommandManager2 cmdManager;
    private final Logger logger;

    private Map<ProviderType, GPTProvider> globalProviders = new ConcurrentHashMap<>();
    private Map<Long, Map<ProviderType, GPTProvider>> guildProviders = new ConcurrentHashMap<>();

    public PWGPTHandler(CommandManager2 manager) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
        this.cmdManager = manager;
        this.handler = new GptHandler();

        ConfigurationBuilder<BuiltConfiguration> builder
                = ConfigurationBuilderFactory.newConfigurationBuilder();

        AppenderComponentBuilder rollingFile = builder.newAppender("rolling", "RollingFile");
        rollingFile.addAttribute("fileName", "rolling.log");
        rollingFile.addAttribute("filePattern", "rolling-%d{MM-dd-yy}.log.gz");

        ComponentBuilder triggeringPolicies = builder.newComponent("Policies")
                .addComponent(builder.newComponent("CronTriggeringPolicy")
                        .addAttribute("schedule", "0 0 0 * * ?"))
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy")
                        .addAttribute("size", "100M"));

        rollingFile.addComponent(triggeringPolicies);

        LoggerComponentBuilder logger = builder.newLogger("gpt", Level.DEBUG);
        logger.add(builder.newAppenderRef("log"));
        logger.addAttribute("additivity", false);

        Configurator.initialize(builder.build());

        this.logger = LoggerFactory.getLogger("gpt");
    }

    public Map<String, Map<String, String>> getConfiguration(DBNation nation) {
        Gson gson = new Gson();
        ByteBuffer gptOptBuf = nation.getMeta(NationMeta.GPT_OPTIONS);
        Map<String, Map<String, String>> gptOptions = new HashMap<>();
        if (gptOptBuf != null) {
            String json = new String(gptOptBuf.array(), StandardCharsets.UTF_8);
            gptOptions = gson.fromJson(json, new TypeToken<Map<String, Map<String, String>>>(){}.getType());
        }
        return gptOptions;
    }

    public void setOptions(DBNation nation, Map<String, Map<String, String>> options) {
        Gson gson = new Gson();
        String json = gson.toJson(options);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        nation.setMeta(NationMeta.GPT_OPTIONS, data);
    }

    public Map<String, Map<String, String>> setAndValidateOptions(DBNation nation, GPTProvider provider, Map<String, String> options) {
        Map<String, String> allowed = provider.getOptions();
        // ensure all options are in allowed (as keys)
        for (String key : options.keySet()) {
            if (!allowed.containsKey(key)) {
                throw new IllegalArgumentException("Option `" + key + "` is not allowed for provider `" + provider.getId() + "`. Example options:\n```json\n" + allowed + "\n```");
            }
        }
        Map<String, Map<String, String>> config = getConfiguration(nation);
        config.put(provider.getId(), options);
        setOptions(nation, config);
        return config;
    }

    public Map<String, String> getOptions(DBNation nation, GPTProvider provider) {
        Map<String, Map<String, String>> allOptions = getConfiguration(nation);
        return allOptions.getOrDefault(provider.getId(), new HashMap<>());
    }

    public Set<GPTProvider> getProviders(GuildDB db) {
        Set<GPTProvider> providers = new LinkedHashSet<>();
        // add guild
        Map<ProviderType, GPTProvider> result = guildProviders.computeIfAbsent(db.getIdLong(), k -> new ConcurrentHashMap<>());
        synchronized (result) {
            String openAiKey = GuildKey.OPENAI_KEY.getOrNull(db);
            ModelType expectedModel = GuildKey.OPENAI_MODEL.getOrNull(db);
            if (expectedModel == null) expectedModel = ModelType.GPT_3_5_TURBO;

            int[] limits = GuildKey.GPT_USAGE_LIMITS.getOrNull(db);

            GPTProvider existing = result.get(ProviderType.OPENAI);
            if (openAiKey == null || (existing != null && ((GPTText2Text) existing.getText2Text()).getModel() != expectedModel)) {
                GPTProvider removed = result.remove(ProviderType.OPENAI);
                if (removed != null) {
                    try {
                        removed.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                ModelType finalExpectedModel = expectedModel;
                GPTProvider provider = result.computeIfAbsent(ProviderType.OPENAI, k -> {
                    IText2Text newProvider = handler.createOpenAiText2Text(openAiKey, finalExpectedModel);
                    return new SimpleGPTProvider(ProviderType.OPENAI, newProvider, handler.getModerator(), true, logger);
                });
                if (limits != null) provider.setUsageLimits(limits[0], limits[1], limits[2], limits[3]);
                providers.add(provider);
            }

            Boolean enableCopilot = GuildKey.ENABLE_GITHUB_COPILOT.getOrNull(db);
            if (enableCopilot != Boolean.TRUE) {
                GPTProvider removed = result.remove(ProviderType.COPILOT);
                if (removed != null) {
                    try {
                        removed.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                GPTProvider provider = result.computeIfAbsent(ProviderType.COPILOT, k -> {
                    String path = "tokens" + File.separator + db.getIdLong();
                    IText2Text newProvider = handler.createCopilotText2Text(path, authData -> {
                        PrivateChannel channel = RateLimitUtil.complete(db.getGuild().getOwner().getUser().openPrivateChannel());
                        String message = "To use copilot for chat completions, please authenticate the application via:\n" +
                                authData.Url + "\n" +
                                "and enter the code: " + authData.UserCode + "\n\n" +
                                "This feature was enabled on a guild you own: " + db.getGuild() + "\n" +
                                "To disable, see: " + CM.settings.delete.cmd.toSlashMention() + " with key: `" + GuildKey.ENABLE_GITHUB_COPILOT.name() + "`";
                        RateLimitUtil.queue(channel.sendMessage(message));
                    });
                    return new SimpleGPTProvider(ProviderType.COPILOT, newProvider, handler.getModerator(), true, logger);
                });
                if (limits != null) provider.setUsageLimits(limits[0], limits[1], limits[2], limits[3]);
                providers.add(provider);
            }
        }

        // add global
        providers.addAll(globalProviders.values());

        return providers;
    }

    public GptHandler getHandler() {
        return handler;
    }

    public void registerSources() {
        for (EmbeddingType type : EmbeddingType.values()) {
            EmbeddingSource source = handler.getEmbeddings().getOrCreateSource(type.name(), 0);
            sourceMap.put(type, source);
        }
    }

    public EmbeddingSource getSource(EmbeddingType type) {
        return sourceMap.get(type);
    }

    public void registerDefaults() {
        registerSources();

        registerCommandEmbeddings();
        registerSettingEmbeddings();
        registerNationMetricBindings();
        registerArgumentBindings();
//        registerFormulaBindings("Formula");
//        registerAcronymBindings("Acronym");
//        registerPageSectionBindings("Wiki Page");
//        registerTutorialBindings("Tutorial");

        if (Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.ENABLED) {
            SimpleGPTProvider provider = new SimpleGPTProvider(
                    ProviderType.COPILOT,
                    handler.createCopilotText2Text("tokens", authData -> {
                        throw new IllegalArgumentException("(For bot owner): Open URL <" + authData.Url + "> to enter the device code: `" + authData.UserCode + "`");
                    }),
                    handler.getModerator(),
                    true,
                    logger);
            provider.setTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.USER_TURN_LIMIT);
            provider.setDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.USER_DAY_LIMIT);
            provider.setGuildTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.GUILD_TURN_LIMIT);
            provider.setGuildDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.COPILOT.GUILD_DAY_LIMIT);

            globalProviders.put(ProviderType.COPILOT, provider);
        }

        if (Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY != null) {
            SimpleGPTProvider provider = new SimpleGPTProvider(
                    ProviderType.OPENAI,
                    handler.createOpenAiText2Text(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY, ModelType.GPT_3_5_TURBO),
                    handler.getModerator(),
                    true,
                    logger);

            provider.setTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.USER_TURN_LIMIT);
            provider.setDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.USER_DAY_LIMIT);
            provider.setGuildTurnLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.GUILD_TURN_LIMIT);
            provider.setGuildDayLimit(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.GUILD_DAY_LIMIT);

            globalProviders.put(ProviderType.OPENAI, provider);
        }

        if (handler.getProcessText2Text() != null) {
            SimpleGPTProvider provider = new SimpleGPTProvider(
                    ProviderType.PROCESS,
                    handler.getProcessText2Text(),
                    handler.getModerator(),
                    false,
                    logger);
            globalProviders.put(ProviderType.PROCESS, provider);
        }
    }

//    public String generateSolution(ValueStore store, GuildDB db, User user, String userInput) {
//        // check moderation
//        List<ModerationResult> modResult = handler.getModerator().moderate(userInput);
//        GPTUtil.checkThrowModeration(modResult, userInput);
//
//        // get prompt
//        String prompt = """
//               You are `Locutus` a discord bot for providing documentation to a player who is the leader of a nation in the game Politics And War.
//               Locutus wiki: <https://github.com/xdnw/locutus/wiki/Commands>
//               Use the information to reply with comprehensive documentation including appropriate syntax.
//
//               Player conversation:
//               ```
//               {user_input}
//               ```
//
//               Top results from searching the game database (might not be relevant):
//               {search_results}""";
//
//        // 2000
//        int promptLength = prompt.replace("{user_input}", "").replace("{search_results}", "").length();
//        int userInputLength = userInput.length();
//
//        int max = 2000 - 31;
//        int remaining = max - promptLength - userInputLength;
//
//        if (store == null) {
//            store = new LocalValueStore(Locutus.imp().getCommandManager().getV2().getStore());
//            // set db and user
//            store.addProvider(Key.of(GuildDB.class, Me.class), db);
//            store.addProvider(Key.of(User.class, Me.class), user);
//        }
//
//        // get the closest results
//        List<String> embeddings = new ArrayList<>();
//        Set<EmbeddingType> allowedTypes = new HashSet<>(Arrays.asList(EmbeddingType.values()));
//        List<EmbeddingResult> closest = this.getClosest(store, userInput, 50, allowedTypes);
//        for (EmbeddingResult result : closest) {
//            EmbeddingType type = sourceMap.inverse().get(result.source);
//            IEmbeddingAdapter<?> adapter = adapterMap.get(type);
//            Object obj = adapter.getObject(result.hash);
//            String text = adapter.getDescription(obj);
//
//            String text = embedding.getType() + "." + embedding.getId() + "=" + embedding.getFull();
//            text = text.replace("\n\n", "\n");
//            if (text.length() + 1 > remaining) continue;
//            embeddings.add(text);
//            remaining -= text.length() + 1;
//        }
//
//        String formatted = prompt.replace("{user_input}", userInput).replace("{search_results}", String.join("\n", embeddings));
//
//        System.out.println("Prompt\n\n" + formatted + "\n");
//
//        try {
//            String result = this.handler.getText2text().generate(formatted);
//            return result;
//        } catch (Throwable e) {
//            e.printStackTrace();
//            return "Error (see console)";
//        }
//    }

    private void registerCommandEmbeddings() {
        EmbeddingSource source = sourceMap.get(EmbeddingType.Command);
        Set<Method> methods = new HashSet<>();
        Set<ParametricCallable> registerCommands = new HashSet<>();
        for (ParametricCallable callable : cmdManager.getCommands().getParametricCallables(f -> true)) {
            if (callable.simpleDesc().isEmpty()) continue;
            if (methods.contains(callable.getMethod())) continue;
            methods.add(callable.getMethod());
            registerCommands.add(callable);
        }
        CommandEmbeddingAdapter adapter = new CommandEmbeddingAdapter(source, registerCommands);
        adapter.createEmbeddings(handler);

        adapterMap2.put(source, adapter);
    }

    public Set<EmbeddingSource> getSources(Guild guild, boolean allowRoot) {
        return handler.getEmbeddings().getSources(guildId -> (allowRoot && (guildId == 0 || guildId == Settings.INSTANCE.ROOT_SERVER || guildId == Settings.INSTANCE.ROOT_COALITION_SERVER || guildId == Settings.INSTANCE.FORUM_FEED_SERVER)) || guildId == guild.getIdLong(), src -> true);
    }

    private void registerSettingEmbeddings() {
        EmbeddingSource source = sourceMap.get(EmbeddingType.Configuration);
        Set<GuildSetting> settings = new HashSet<>();
        for (GuildSetting setting : GuildKey.values()) {
            if (setting.help().isEmpty()) continue;
            settings.add(setting);
        }

        SettingEmbeddingAdapter adapter = new SettingEmbeddingAdapter(source, settings);
        adapter.createEmbeddings(handler);

        adapterMap2.put(source, adapter);
    }

    private void registerNationMetricBindings() {
        EmbeddingSource source = sourceMap.get(EmbeddingType.Nation_Statistic);
        Set<ParametricCallable> metrics = new HashSet<>(cmdManager.getNationPlaceholders().getParametricCallables());
        NationAttributeAdapter adapter = new NationAttributeAdapter(source, metrics);
        adapter.createEmbeddings(handler);

        adapterMap2.put(source, adapter);
    }

    private void registerArgumentBindings() {
        EmbeddingSource source = sourceMap.get(EmbeddingType.Argument);
        ValueStore store = this.cmdManager.getStore();
        Map<Key, Parser> parsers = store.getParsers();

        Map<Key, Parser> consumeParsers = new HashMap<>();
        for (Map.Entry<Key, Parser> entry : parsers.entrySet()) {
            Parser parser = entry.getValue();
            if (!parser.isConsumer(store)) continue;
            consumeParsers.put(entry.getKey(), parser);
        }

        ArgumentEmbeddingAdapter adapter = new ArgumentEmbeddingAdapter(source, consumeParsers);
        adapter.createEmbeddings(handler);

        adapterMap2.put(source, adapter);
    }

    public List<ParametricCallable> getClosestCommands(ValueStore store, ParametricCallable command, int top) {
        CommandEmbeddingAdapter adapter = (CommandEmbeddingAdapter) adapterMap2.get(sourceMap.get(EmbeddingType.Command));
        String text = adapter.getDescription(command);
        return getClosestCommands(store, text, top);
    }

    public List<ParametricCallable> getClosestNationAttributes(ValueStore store, ParametricCallable cmd, int top) {
        NationAttributeAdapter adapter = (NationAttributeAdapter) adapterMap2.get(sourceMap.get(EmbeddingType.Nation_Statistic));
        String text = adapter.getDescription(cmd);
        return getClosestNationAttributes(store, text, top);
    }

    public List<ParametricCallable> getClosestCommands(ValueStore store, String input, int top) {
        EmbeddingSource commandSource = sourceMap.get(EmbeddingType.Command);
        List<EmbeddingInfo> closest = getClosest(store, input, top, Set.of(commandSource));
        List<ParametricCallable> commands = new ArrayList<>();
        CommandEmbeddingAdapter adapter = (CommandEmbeddingAdapter) adapterMap2.get(commandSource);
        for (EmbeddingInfo info : closest) {
            ParametricCallable callable = adapter.getObject(info.hash);
            commands.add(callable);
        }
        return commands;
    }

    public List<ParametricCallable> getClosestNationAttributes(ValueStore store, String input, int top) {
        EmbeddingSource typeSource = sourceMap.get(EmbeddingType.Nation_Statistic);
        List<EmbeddingInfo> closest = getClosest(store, input, top, Set.of(typeSource));
        List<ParametricCallable> list = new ArrayList<>();
        NationAttributeAdapter adapter = (NationAttributeAdapter) adapterMap2.get(typeSource);
        for (EmbeddingInfo info : closest) {
            ParametricCallable obj = adapter.getObject(info.hash);
            list.add(obj);
        }
        return list;
    }

    public List<Parser> getClosestArguments(ValueStore store, String input, int top) {
        EmbeddingSource commandSource = sourceMap.get(EmbeddingType.Argument);
        List<EmbeddingInfo> closest = getClosest(store, input, top, Set.of(commandSource));
        List<Parser> commands = new ArrayList<>();
        ArgumentEmbeddingAdapter adapter = (ArgumentEmbeddingAdapter) adapterMap2.get(commandSource);
        for (EmbeddingInfo info : closest) {
            Parser callable = adapter.getObject(info.hash);
            commands.add(callable);
        }
        return commands;
    }

    public List<GuildSetting> getClosestSettings(ValueStore store, String input, int top) {
        EmbeddingSource settingSource = sourceMap.get(EmbeddingType.Configuration);
        List<EmbeddingInfo> closest = getClosest(store, input, top, Set.of(settingSource));
        List<GuildSetting> settings = new ArrayList<>();
        SettingEmbeddingAdapter adapter = (SettingEmbeddingAdapter) adapterMap2.get(settingSource);
        for (EmbeddingInfo info : closest) {
            GuildSetting setting = adapter.getObject(info.hash);
            settings.add(setting);
        }
        return settings;
    }

    public IEmbeddingAdapter getAdapter(EmbeddingType type) {
        EmbeddingSource source = sourceMap.get(type);
        if (source == null) return null;
        return adapterMap2.get(source);
    }

    public IEmbeddingAdapter getAdapter(EmbeddingSource source) {
        return adapterMap2.get(source);
    }

    public List<EmbeddingInfo> getClosest(ValueStore store, String input, int top, Set<EmbeddingSource> allowedSources) {
        EmbeddingType userInput = EmbeddingType.User_Input;
        EmbeddingSource userInputSrc = sourceMap.get(userInput);

        List<EmbeddingInfo> result = handler.getEmbeddings().getClosest(userInputSrc, input, top, allowedSources, new BiPredicate<EmbeddingSource, Long>() {
            @Override
            public boolean test(EmbeddingSource embeddingSource, Long hash) {
                IEmbeddingAdapter<?> adapter = adapterMap2.get(embeddingSource);
                if (adapter == null) return true;
                if (adapter instanceof PWAdapter pwAdapter) {
                    Object obj = pwAdapter.getObject(hash);
                    if (!pwAdapter.hasPermission(obj, store, cmdManager)) {
                        return false;
                    }
                }
                return true;
            }
        }, handler::checkModeration);
        return result;
    }

    public List<String> convertDocument(String markdown, String documentDescription) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Set<ProviderType> getProviderTypes(DBNation nation) {
        ByteBuffer existingBuf = nation.getMeta(NationMeta.GPT_PROVIDER);
        Set<ProviderType> existing = new HashSet<>();
        long mask = existingBuf == null ? 0 : existingBuf.getLong();
        if (mask == 0) mask = -1;
        for (ProviderType type : ProviderType.values()) {
            if ((mask & (1L << type.ordinal())) != 0) {
                existing.add(type);
            }
        }
        return existing;
    }

    public void setProviderTypes(DBNation nation, Set<ProviderType> types) {
        long mask = 0;
        for (ProviderType type : types) {
            mask |= (1L << type.ordinal());
        }
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
        nation.setMeta(NationMeta.GPT_PROVIDER, mask);
    }

    public GPTProvider getDefaultProvider(GuildDB db, User user, DBNation nation) {
        Set<ProviderType> types = getProviderTypes(nation);
        Set<GPTProvider> providers = getProviders(db);
        // remove if not type
        if (!types.isEmpty()) {
            providers.removeIf(provider -> !types.contains(provider.getType()));
        }
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("No providers available. See: " + CM.chat.providers.list.cmd.toSlashMention() + " and " + CM.chat.providers.set.cmd.toSlashMention());
        }
        List<String> noPermsMessages = new ArrayList<>();
        for (GPTProvider provider : providers) {
            try {
                if (provider.hasPermission(db, user, true)) {
                    return provider;
                }
            } catch (IllegalArgumentException ignore) {
                noPermsMessages.add(provider.getId() + ": " + ignore.getMessage());
            }
        }
        throw new IllegalArgumentException("No providers available. Errors:\n- " + String.join("\n- ", noPermsMessages) + "\n\nSee " +  CM.chat.providers.list.cmd.toSlashMention() + " and " + CM.chat.providers.set.cmd.toSlashMention());
    }
}
