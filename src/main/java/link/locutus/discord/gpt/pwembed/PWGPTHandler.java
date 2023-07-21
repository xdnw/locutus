package link.locutus.discord.gpt.pwembed;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.theokanning.openai.embedding.EmbeddingResult;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.ModerationResult;
import link.locutus.discord.gpt.imps.EmbeddingAdapter;
import link.locutus.discord.gpt.imps.EmbeddingInfo;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.imps.IEmbeddingAdapter;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.TriConsumer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PWGPTHandler {

    private final GptHandler handler;
    private final BiMap<EmbeddingType, EmbeddingSource> sourceMap = HashBiMap.create();
    private final Map<EmbeddingSource, IEmbeddingAdapter<?>> adapterMap2 = new HashMap<>();
    private final CommandManager2 cmdManager;

    public PWGPTHandler(CommandManager2 manager) throws SQLException, ClassNotFoundException {
        this.cmdManager = manager;
        this.handler = new GptHandler();
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

//        registerArgumentBindings("Command Argument");
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
        return handler.getEmbeddings().getSources(guildId -> (allowRoot && guildId == 0) || guildId == guild.getIdLong(), src -> true);
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
    }

    private void registerNationMetricBindings() {
        EmbeddingSource source = sourceMap.get(EmbeddingType.Nation_Statistic);
        Set<NationAttribute> metrics = new HashSet<>(cmdManager.getNationPlaceholders().getMetrics(cmdManager.getStore()));
        NationAttributeAdapter adapter = new NationAttributeAdapter(source, metrics);
        adapter.createEmbeddings(handler);
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
}
