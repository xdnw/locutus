package link.locutus.discord.gpt.test;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.gpt.imps.embedding.EmbeddingInfo;
import link.locutus.discord.gpt.imps.embedding.EmbeddingType;
import link.locutus.discord.gpt.imps.embedding.IEmbeddingAdapter;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.math.ArrayUtil;

import javax.security.auth.login.LoginException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

public class GPTTest {

    public static void main(String[] args) throws SQLException, LoginException, InterruptedException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.WEB.PORT = 0;
        Settings.INSTANCE.WEB.BACKEND_DOMAIN = "http://localhost";
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.TASKS.UNLOAD_WARS_AFTER_TURNS = 0;
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;

        Locutus locutus = Locutus.create();
        locutus.start();

        String input = "A custom sheet for nation info";

        PWGPTHandler pwGpt = locutus.getCommandManager().getV2().getPwgptHandler();
        pwGpt.registerDefaults();
        GptHandler gptHandler = pwGpt.getHandler();
        IEmbeddingDatabase embeddings = gptHandler.getEmbeddings();
        EmbeddingType userInput = EmbeddingType.User_Input;
        EmbeddingSource userInputSrc = pwGpt.getSource(userInput);

        System.out.println("Source " + userInputSrc);
        if (userInputSrc == null) {
            System.exit(0);
        }

        EmbeddingSource commandSource = pwGpt.getSource(EmbeddingType.Command);
        IEmbeddingAdapter<ParametricCallable> cmdAdapter = pwGpt.getAdapter(commandSource);
        Set<ParametricCallable> callables = locutus.getCommandManager().getV2().getCommands().getParametricCallables(f -> f.getPrimaryCommandId().equalsIgnoreCase("nationsheet"));
        ParametricCallable nationSheet = callables.iterator().next();
        long hash = cmdAdapter.getHash(nationSheet);

        System.out.println("--- nation sheet ---");
        System.out.println("Hash: " + hash);
        System.out.println("Text: " + embeddings.getText(hash));
        float[] vector1 = embeddings.getEmbedding(userInputSrc, input, null);
        float[] vector2 = embeddings.getEmbedding(userInputSrc, embeddings.getText(hash), null);

        System.out.println("Similarity " + ArrayUtil.cosineSimilarity(vector1, vector2));

        Set<EmbeddingSource> sources = pwGpt.getSources(null, true);
        sources.remove(userInputSrc);

        List<EmbeddingInfo> closest = embeddings.getClosest(userInputSrc, input, 100, sources, new BiPredicate<EmbeddingSource, Long>() {
            @Override
            public boolean test(EmbeddingSource embeddingSource, Long hash) {
                return true;
            }
        }, gptHandler::checkModeration);

        for (EmbeddingInfo info : closest) {
            String text = embeddings.getText(info.hash);
            System.out.println(info.source.source_name + " | " + text + " | " + info.distance);
        }
        System.exit(0);
    }
}
