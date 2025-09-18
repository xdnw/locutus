package link.locutus.discord.gpt.pw;

import com.google.common.base.Predicates;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.ISourceManager;
import link.locutus.discord.gpt.imps.embedding.EmbeddingInfo;
import link.locutus.discord.gpt.imps.embedding.EmbeddingType;
import link.locutus.discord.gpt.imps.embedding.IEmbeddingAdapter;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PWGPTHandler {

    private final GptHandler handler;
    private final BiMap<EmbeddingType, EmbeddingSource> baseSources = HashBiMap.create();
    private final BiMap<Class<?>, EmbeddingSource> classSources = HashBiMap.create();


    private final Map<EmbeddingSource, IEmbeddingAdapter<?>> adapterMap2 = new ConcurrentHashMap<>();

    private final CommandManager2 cmdManager;
    private final PlayerGPTConfig PlayerGPTConfig;

    private final LimitManager limitManager;
    private final DocumentConverter converter;

    private final GptDatabase database;
    private final WikiManager wikiManager;

    public PWGPTHandler(CommandManager2 manager) throws Exception {
        this.database = new GptDatabase();
        this.cmdManager = manager;
        this.handler = new GptHandler(database);
        this.limitManager = new LimitManager(handler);
        this.PlayerGPTConfig = new PlayerGPTConfig();
        this.converter = new DocumentConverter(limitManager, handler);
        this.wikiManager = new WikiManager(database, handler.getSourceManager(), handler);
    }

    public WikiManager getWikiManager() {
        return wikiManager;
    }

    public DocumentConverter getConverter() {
        return converter;
    }

    public PlayerGPTConfig getPlayerGPTConfig() {
        return PlayerGPTConfig;
    }

    public LimitManager getLimitManager() {
        return limitManager;
    }

    public GptHandler getHandler() {
        return handler;
    }

    public void registerSources(PlaceholdersMap placeholdersMap) {
        for (EmbeddingType type : EmbeddingType.values()) {
            EmbeddingSource source = handler.getSourceManager().getOrCreateSource(type.name(), 0);
            System.out.println("REMOVE:|| Register source " + type + " | " + source);
            baseSources.put(type, source);
        }
        for (Class<?> type : placeholdersMap.getTypes()) {
            String name = PlaceholdersMap.getClassName(type);
            EmbeddingSource source = handler.getSourceManager().getOrCreateSource(name, 0);
            System.out.println("REMOVE:|| Register source " + type + " | " + source);
            classSources.put(type, source);
        }
    }

    public EmbeddingSource getSource(EmbeddingType type) {
        return baseSources.get(type);
    }

    public void registerDefaults(PlaceholdersMap placeholdersMap) {
        registerSources(placeholdersMap);

        registerCommandEmbeddings();
        registerSettingEmbeddings();
        registerArgumentBindings();
        registerPlaceholderBindings(placeholdersMap);
//        try {
//            registerWikiPagesLegacy();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        registerFormulaBindings("Formula");
//        registerAcronymBindings("Acronym");
//        registerPageSectionBindings("Wiki Page");
//        registerTutorialBindings("Tutorial");
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
        EmbeddingSource source = baseSources.get(EmbeddingType.Command);
        Set<Method> methods = new HashSet<>();
        Set<ParametricCallable> registerCommands = new ObjectLinkedOpenHashSet<>();
        for (ParametricCallable callable : cmdManager.getCommands().getParametricCallables(Predicates.alwaysTrue())) {
            if (callable.simpleDesc().isEmpty()) continue;
            if (methods.contains(callable.getMethod())) continue;
            methods.add(callable.getMethod());
            registerCommands.add(callable);
        }
        CommandEmbeddingAdapter adapter = new CommandEmbeddingAdapter(source, registerCommands);
        adapter.createEmbeddings(handler, true);

        adapterMap2.put(source, adapter);
    }

    public Set<EmbeddingSource> getSources(Guild guild, boolean allowRoot) {
        return handler.getSourceManager().getSources(guildId -> (allowRoot && (guildId == 0 || guildId == Settings.INSTANCE.ROOT_SERVER || guildId == Settings.INSTANCE.ROOT_COALITION_SERVER || guildId == Settings.INSTANCE.FORUM_FEED_SERVER)) || guildId == guild.getIdLong(), src -> true);
    }

    private void registerSettingEmbeddings() {
        EmbeddingSource source = baseSources.get(EmbeddingType.Configuration);
        Set<GuildSetting> settings = new HashSet<>();
        for (GuildSetting setting : GuildKey.values()) {
            if (setting.help().isEmpty()) continue;
            settings.add(setting);
        }
        SettingEmbeddingAdapter adapter = new SettingEmbeddingAdapter(source, settings);
        adapter.createEmbeddings(handler, true);

        adapterMap2.put(source, adapter);
    }

    private void registerPlaceholderBindings(PlaceholdersMap placeholdersMap) {
        for (Class<?> type : placeholdersMap.getTypes()) {
            EmbeddingSource source = classSources.get(type);
            Placeholders<?, Object> ph = placeholdersMap.get(type);
            Set<ParametricCallable> metrics = new ObjectOpenHashSet<>(ph.getParametricCallables());
            PlaceholderAdapter adapter = new PlaceholderAdapter(source, type, metrics);
            adapter.createEmbeddings(handler, true);
            adapterMap2.put(source, adapter);
        }
    }

    private void registerArgumentBindings() {
        EmbeddingSource source = baseSources.get(EmbeddingType.Argument);
        ValueStore<Object> store = this.cmdManager.getStore();
        Map<Key, Parser> parsers = store.getParsers();

        Map<Key, Parser> consumeParsers = new HashMap<>();
        for (Map.Entry<Key, Parser> entry : parsers.entrySet()) {
            Parser parser = entry.getValue();
            if (!parser.isConsumer(store)) continue;
            consumeParsers.put(entry.getKey(), parser);
        }

        ArgumentEmbeddingAdapter adapter = new ArgumentEmbeddingAdapter(source, consumeParsers);
        adapter.createEmbeddings(handler, true);

        adapterMap2.put(source, adapter);
    }

    public <T> List<T> getClosest(EmbeddingSource source, ValueStore<?> store, T input, int top) {
        return getClosest(source, store, f -> f.getDescription(input), top, false);
    }

    public <T> List<T> getClosest(Class<?> classSource, ValueStore<?> store, T input, int top) {
        EmbeddingSource source = classSources.get(classSource);
        if (source == null) throw new IllegalArgumentException("No source found for class " + classSource);
        return getClosest(source, store, f -> f.getDescription(input), top, false);
    }

    public <T> List<T> getClosest(EmbeddingSource source, ValueStore<?> store, String input, int top, boolean moderate) {
        return getClosest(source, store, _ -> input, top, moderate);
    }

    public <T> List<T> getClosest(EmbeddingSource source, ValueStore<?> store, Function<IEmbeddingAdapter<T>, String> func, int top, boolean moderate) {
        IEmbeddingAdapter<T> adapter = (IEmbeddingAdapter<T>) this.adapterMap2.get(source);
        if (adapter == null) throw new IllegalArgumentException("No adapter found for source " + source);
        String text = func.apply(adapter);
        List<EmbeddingInfo> closest = getClosest(store, text, top, Set.of(source), moderate);
        List<T> adapted = new ObjectArrayList<>(closest.size());
        for (EmbeddingInfo info : closest) {
            T entity = adapter.getObject(info.hash);
            adapted.add(entity);
        }
        return adapted;
    }

    public <T> List<T> getClosest(EmbeddingType type, ValueStore<?> store, String input, int top, boolean moderate) {
        EmbeddingSource source = baseSources.get(type);
        if (source == null) throw new IllegalArgumentException("No source found for type " + type);
        return getClosest(source, store, input, top, moderate);
    }

    public <T> List<T> getClosest(EmbeddingType type, ValueStore<?> store, T input, int top) {
        EmbeddingSource source = baseSources.get(type);
        if (source == null) throw new IllegalArgumentException("No source found for type " + type);
        return getClosest(source, store, input, top);
    }

    public EmbeddingSource getClassSource(Class<?> type) {
        return classSources.get(type);
    }


    public IEmbeddingAdapter getAdapter(EmbeddingType type) {
        EmbeddingSource source = baseSources.get(type);
        if (source == null) return null;
        return adapterMap2.get(source);
    }

    public IEmbeddingAdapter getAdapter(EmbeddingSource source) {
        return adapterMap2.get(source);
    }

    public List<EmbeddingInfo> getClosest(ValueStore store, String input, int top, Set<EmbeddingSource> allowedSources, boolean moderate) {
        EmbeddingType userInput = EmbeddingType.User_Input;
        EmbeddingSource userInputSrc = baseSources.get(userInput);

        List<EmbeddingInfo> result = handler.getSourceManager().getClosest(userInputSrc, input, top, allowedSources, new BiPredicate<EmbeddingSource, Long>() {
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
        }, moderate ? handler::checkModeration : null);
        return result;
    }

    public Set<EmbeddingSource> setSources(DBNation nation,
                                           Guild guild,
                                           Set<EmbeddingType> excludeTypes,
                                           Set<String> includeWikiCategories,
                                           Set<String> excludeWikiCategories,
                                           Set<EmbeddingSource> excludeSources,
                                           Set<EmbeddingSource> addSources) {
        if (excludeSources != null && addSources != null) {
            throw new IllegalArgumentException("Cannot exclude and add sources at the same time. Please use only one of the two arguments.");
        }
        Map<String, List<String>> configurationMap = new HashMap<>();
        if (excludeTypes != null && !excludeTypes.isEmpty()) {
            configurationMap.put("types", excludeTypes.stream().map(Enum::name).collect(Collectors.toList()));
        }
        if (includeWikiCategories != null && !includeWikiCategories.isEmpty()) {
            configurationMap.put("allow_wiki", new ArrayList<>(includeWikiCategories));
        }
        if (excludeWikiCategories != null && !excludeWikiCategories.isEmpty()) {
            configurationMap.put("deny_wiki", new ArrayList<>(excludeWikiCategories));
        }
        if (excludeSources != null && !excludeSources.isEmpty()) {
            configurationMap.put("deny_sources", excludeSources.stream().map(f -> f.source_id + "").collect(Collectors.toList()));
        }
        if (addSources != null && !addSources.isEmpty()) {
            configurationMap.put("allow_sources", addSources.stream().map(f -> f.source_id + "").collect(Collectors.toList()));
        }

        // to json
        String json = WebUtil.GSON.toJson(configurationMap);
        nation.setMeta(NationMeta.GPT_SOURCES, json);

        Set<Integer> excludeSourceIds = excludeSources == null ? null : excludeSources.stream().map(f -> f.source_id).collect(Collectors.toSet());
        Set<Integer> addSourceIds = addSources == null ? null : addSources.stream().map(f -> f.source_id).collect(Collectors.toSet());
        return getSources(guild, true, excludeTypes, includeWikiCategories, excludeWikiCategories, excludeSourceIds, addSourceIds);
    }

    public EmbeddingType getSourceType(EmbeddingSource source) {
        if (wikiManager.getWikiPageBySourceId(source.source_id) != null) {
            return EmbeddingType.Game_Wiki_Page;
        }
        return baseSources.inverse().get(source);
    }

    public Set<EmbeddingSource> getSelectedSources(Guild guild, DBNation nation, boolean allowRoot) {
        ByteBuffer jsonBuffer = nation.getMeta(NationMeta.GPT_SOURCES);
        if (jsonBuffer == null) {
            return getSources(guild, allowRoot);
        }
        Map<String, List<String>> configuration = WebUtil.GSON.fromJson(new String(jsonBuffer.array()), new TypeToken<Map<String, List<String>>>(){}.getType());
        Set<EmbeddingType> excludeTypes = configuration.containsKey("types") ? configuration.get("types").stream().map(EmbeddingType::valueOf).collect(Collectors.toSet()) : null;
        Set<String> includeWikiCategories = configuration.containsKey("allow_wiki") ? new HashSet<>(configuration.get("allow_wiki")) : null;
        Set<String> excludeWikiCategories = configuration.containsKey("deny_wiki") ? new HashSet<>(configuration.get("deny_wiki")) : null;
        Set<Integer> excludeSources = configuration.containsKey("deny_sources") ? configuration.get("deny_sources").stream().map(Integer::parseInt).collect(Collectors.toSet()) : null;
        Set<Integer> addSources = configuration.containsKey("allow_sources") ? configuration.get("allow_sources").stream().map(Integer::parseInt).collect(Collectors.toSet()) : null;

        return getSources(guild, allowRoot, excludeTypes, includeWikiCategories, excludeWikiCategories, excludeSources, addSources);
    }

    public Set<EmbeddingSource> getSources(Guild guild, boolean allowRoot,
                                           Set<EmbeddingType> excludeTypes,
                                           Set<String> includeWikiCategories,
                                           Set<String> excludeWikiCategories,
                                           Set<Integer> excludeSources,
                                           Set<Integer> addSources) {
        Set<EmbeddingSource> sources = new HashSet<>();
        for (EmbeddingSource source : getSources(guild, allowRoot)) {
            EmbeddingType type = getSourceType(source);
            if (excludeTypes != null && excludeTypes.contains(type)) {
                continue;
            }
            if (excludeSources != null && excludeSources.contains(source.source_id)) {
                continue;
            }
            if (addSources != null && !addSources.contains(source.source_id)) {
                continue;
            }
            sources.add(source);
        }

        return sources;
    }

    public ISourceManager getSourceManager() {
        return getHandler().getSourceManager();
    }
}
