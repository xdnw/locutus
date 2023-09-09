package link.locutus.discord.gpt;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationRequest;
import link.locutus.discord.util.StringMan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class GPTUtil {

//    public static float[] average(List<float[]> input, List<Double> weighting) {
//        // see https://github.com/openai/openai-cookbook/blob/main/examples/Embedding_long_inputs.ipynb
//    }

    private static EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    public String getSummaryPrompt(List<String> previousSummary , String text, int maxSummarySize, Function<String, Integer> sizeFunction) {
        String prompt = """
        # Goal
        You will be provided both `context` and `text` from a user guide for the game Politics And War.\s
        Take the information in `text` and organize it into dot points of standalone factual knowledge (start each line with `- `).\s
        Use the `context` solely to understand `text` but do not create facts solely from it.
        Do not make anything up.\s
        Preserve syntax, formulas and precision.
                        
        # Context:
        {context}
                        
        # Text:
        {text}
                        
        # Fact summary:""";
        int size = 0;
        List<String> lines = new ArrayList<>();
        for (int i = previousSummary.size() - 1; i >= 0; i--) {
            String line = previousSummary.get(i);
            size += sizeFunction.apply(line);
            if (size > maxSummarySize) break;
            lines.add(line);
        }
        // reverse lines
        Collections.reverse(lines);
        String context = StringMan.join(lines, "\n");
        String promptFilled = prompt.replace("{context}", context).replace("{text}", text);

        return prompt;
    }

    public static void checkThrowModeration2(List<Moderation> moderations, String text) {
        for (Moderation result : moderations) {
            if (result.isFlagged()) {
                String message = "Your submission has been flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    public static void checkThrowModeration(List<ModerationResult> moderations, String text) {
        for (ModerationResult result : moderations) {
            if (result.isFlagged()) {
                String message = "Your submission was flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static final ConcurrentHashMap<ModelType, Encoding> ENCODER_CACHE = new ConcurrentHashMap<>();

    public static int getTokens(String input, ModelType type) {
        Encoding enc = ENCODER_CACHE.computeIfAbsent(type, REGISTRY::getEncodingForModel);
        return enc.countTokens(input);
    }

    public static List<String> getChunks(String input, ModelType type, int tokenSizeCap) {
        Encoding enc = ENCODER_CACHE.computeIfAbsent(type, REGISTRY::getEncodingForModel);
        return getChunks(input, tokenSizeCap, enc::countTokens);
    }

    public static List<String> getChunks(String input, int tokenSizeCap, Function<String, Integer> getSize) {
        List<String> result = new ArrayList<>();

        String[] lines = input.split("[\r\n]+|\\.\\s");

        // get the tokens count for each line
        List<Integer> tokensCount = new ArrayList<>();
        for (String line : lines) {
            int size = getSize.apply(line);
            if (size > tokenSizeCap) {
                throw new IllegalArgumentException("Line exceeds token limit of " + tokenSizeCap);
            }
            tokensCount.add(size);
        }

        // iterate over lines in chunks of 6000 tokens
        int currentChunkSize = 0;
        StringBuilder currentChunk = new StringBuilder();
        for (String line : lines) {
            int lineTokens = tokensCount.get(0);
            if (currentChunkSize + lineTokens > tokenSizeCap) {
                // process current chunk
                result.add(currentChunk.toString());
                // start new chunk
                currentChunk = new StringBuilder();
                currentChunkSize = 0;
            }
            currentChunk.append(line).append("\n");
            currentChunkSize += lineTokens;
            tokensCount.remove(0);
        }
        // process last chunk
        result.add(currentChunk.toString());

        return result;
    }
}
