package link.locutus.discord.gpt;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationRequest;

import java.util.ArrayList;
import java.util.List;

public class GPTUtil {

//    public static float[] average(List<float[]> input, List<Double> weighting) {
//        // see https://github.com/openai/openai-cookbook/blob/main/examples/Embedding_long_inputs.ipynb
//    }

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
                String message = "Your submission has been flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    public static int getTokens(String input, ModelType type) {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        Encoding enc = registry.getEncodingForModel(type);
        return enc.encode(input).size();
    }
    public static List<String> getChunks(String input, ModelType type, int tokenSizeCap) {
        List<String> result = new ArrayList<>();

        String[] lines = input.split("[\r\n]+|\\.\\s");

        // get tokenizer
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        Encoding enc = registry.getEncodingForModel(type);

        // get the tokens count for each line
        List<Integer> tokensCount = new ArrayList<>();
        for (String line : lines) {
            int size = enc.encode(line).size();
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
