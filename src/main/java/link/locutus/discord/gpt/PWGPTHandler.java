package link.locutus.discord.gpt;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettings;
import link.locutus.discord.util.math.ArrayUtil;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PWGPTHandler {

    private final GptHandler handler;
    private final Map<EmbeddingType, Set<PWEmbedding>> embeddingTypeSetMap;
    private final CommandManager2 cmdManager;

    /*
    Pre processors
     - Categorize as knowledge, request, command
     */

    /*
    Post processors
     */

    public PWGPTHandler(CommandManager2 manager) throws SQLException, ClassNotFoundException {
        this.cmdManager = manager;
        this.handler = new GptHandler();
        this.embeddingTypeSetMap = new ConcurrentHashMap<>();
//        registerArgumentBindings("Command Argument");
//        registerFormulaBindings("Formula");
//        registerAcronymBindings("Acronym");
//        registerPageSectionBindings("Wiki Page");
//        registerTutorialBindings("Tutorial");
    }

    public GptHandler getHandler() {
        return handler;
    }

    public void registerDefaults() {
        registerCommandEmbeddings();
        registerSettingEmbeddings();
        registerNationMetricBindings();
    }

    private void registerCommandEmbeddings() {
        Set<Method> existing = new HashSet<>();
        for (ParametricCallable callable : cmdManager.getCommands().getParametricCallables(f -> true)) {
            if (callable.simpleDesc().isEmpty()) continue;
            if (existing.contains(callable.getMethod())) continue;
            existing.add(callable.getMethod());
            registerEmbedding(new CommandEmbedding(callable), true, "");
        }
    }

    private void registerSettingEmbeddings() {
        for (GuildSetting key : GuildSettings.Key.values()) {
            if (key.help().isEmpty()) continue;
            registerEmbedding(new SettingEmbedding(key), true, "");
        }
    }

    private void registerNationMetricBindings() {
        List<NationAttribute> metrics = cmdManager.getNationPlaceholders().getMetrics(cmdManager.getStore());
        for (NationAttribute metric : metrics) {
            registerEmbedding(new NationMetricEmbedding(metric), true, "");
        }
    }

    private void registerEmbedding(PWEmbedding embedding, boolean forceDownload, String prefix) {
        embeddingTypeSetMap.computeIfAbsent(embedding.getType(), k -> new HashSet<>()).add(embedding);
        if (embedding.getId() != null && !forceDownload) {
            double[] existing = handler.getExistingEmbedding(embedding.getType().ordinal(), embedding.getId());
            if (existing != null) {
                return;
            }
        }
        String full = prefix + embedding.getType().name() + " " + embedding.getId() + ": " + embedding.getContent();
        handler.getEmbedding(embedding.getType().ordinal(), embedding.getId(), full, embedding.shouldSaveConent());
    }

    public List<Map.Entry<PWEmbedding, Double>> getClosest(ValueStore store, String input, int top, Set<EmbeddingType> allowedTypes) {
        double[] compareTo = handler.getEmbedding(input);

        List<Map.Entry<PWEmbedding, Double>> closest = new ArrayList<>();

        System.out.println(embeddingTypeSetMap.size() + " types " + embeddingTypeSetMap.keySet());

        for (Map.Entry<EmbeddingType, Set<PWEmbedding>> entry : embeddingTypeSetMap.entrySet()) {
            if (allowedTypes != null && !allowedTypes.contains(entry.getKey())) {
                System.out.println("Skipping " + entry.getKey() + " " + entry.getValue().size());
                continue;
            }

            System.out.println("Checking " + entry.getKey() + " " + entry.getValue().size());

            for (PWEmbedding other : entry.getValue()) {
                if (!other.hasPermission(store, cmdManager)) {
                    continue;
                }
                System.out.println("has permission " + other.getId());

                double[] cmdEmbed = handler.getExistingEmbedding(other.getType().ordinal(), other.getId());
                if (cmdEmbed == null) {
                    System.out.println("No embedding for " + other.getId());
                    continue;
                }
                System.out.println("has embedding " + other.getId());
                double diff = ArrayUtil.cosineSimilarity(compareTo, cmdEmbed);
                closest.add(Map.entry(other, diff));
            }
        }
        closest.sort((o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));
        return closest.subList(0, Math.min(top, closest.size()));
    }
}
