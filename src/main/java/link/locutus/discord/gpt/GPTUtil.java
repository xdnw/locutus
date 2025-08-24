package link.locutus.discord.gpt;

import ai.djl.sentencepiece.SpTokenizer;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.models.moderations.Moderation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.gpt.imps.token.TokenizerDownloader;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.MathMan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPTUtil {
//    public static float[] average(List<float[]> input, List<Double> weighting) {
//        // see https://github.com/openai/openai-cookbook/blob/main/examples/Embedding_long_inputs.ipynb
//    }

    private static class RegistryHolder {
        static final EncodingRegistry INSTANCE = Encodings.newDefaultEncodingRegistry();
    }

    public static EncodingRegistry getRegistry() {
        return RegistryHolder.INSTANCE;
    }

    private static class SentencePieceHolder {
        static final SpTokenizer TOKENIZER;

        static {
            try {
                TOKENIZER = TokenizerDownloader.downloadAndLoad(TokenizerDownloader.SourceType.GITHUB, "google/gemma_pytorch/main/tokenizer", "tokenizer.model");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static int countSentencePieceTokens(String text) {
        int count = SentencePieceHolder.TOKENIZER.tokenize(text).size();
        // Add extra margin for safety, since newer models use slightly more tokens than SP counts
        return (int) ((count * 1.005) + 1);
    }

    public static int detectContextWindow(Path modelDir) {
        // Try common HF config locations
        Path[] candidates = new Path[] {
                modelDir.resolve("config.json"),
                modelDir.resolve("model").resolve("config.json")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    Integer v = findInt(json, "\"max_position_embeddings\"\\s*:\\s*(\\d+)");
                    if (v == null) v = findInt(json, "\"n_positions\"\\s*:\\s*(\\d+)");
                    if (v == null) v = findInt(json, "\"max_sequence_length\"\\s*:\\s*(\\d+)");
                    if (v != null) return v;
                } catch (IOException ignore) {
                }
            }
        }
        System.err.println("Warning: Unable to determine context window size from model config. " +
                "Using default value of 512 tokens. This may lead to unexpected behavior if the model supports a different size.");
        // Sensible default if the config doesn't declare it (typical BERT/Roberta: 512)
        return 512;
    }

    private static Integer findInt(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    public static void checkThrowModeration2(List<Moderation> moderations, String text) {
        for (Moderation result : moderations) {
            if (result.flagged()) {
                String message = "Your submission has been flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    public static void checkThrowModeration(String text) {
        if (text == null || text.isEmpty() || MathMan.isInteger(text)) return;
        PWGPTHandler gpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
        if (gpt != null) {
            GptHandler handler = gpt.getHandler();
            List<ModerationResult> result = handler.getModerator().moderate(text);
            GPTUtil.checkThrowModeration(result, "<redacted>");
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
        Encoding enc = ENCODER_CACHE.computeIfAbsent(type, getRegistry()::getEncodingForModel);
        return enc.countTokens(input);
    }

    public static List<String> getChunks(String input, ModelType type, int tokenSizeCap) {
        Encoding enc = ENCODER_CACHE.computeIfAbsent(type, getRegistry()::getEncodingForModel);
        return getChunks(input, tokenSizeCap, enc::countTokens);
    }

    public static String getNextChunk(String text, int tokenSizeCap, Function<String, Integer> getSize) {
        if (text == null || text.isEmpty()) return "";
        if (tokenSizeCap == Integer.MAX_VALUE) return text;

        if (getSize.apply(text) <= tokenSizeCap) {
            return text; // Whole text fits.
        }

        int bestCut = -1;
        StringBuilder sb = new StringBuilder();

        // --- Pass 1: prefer newline boundaries ---
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (c == '\n') {
                if (getSize.apply(sb.toString()) > tokenSizeCap) break;
                bestCut = sb.length();
            }
        }

        if (bestCut > 0) {
            return text.substring(0, bestCut);
        }

        // --- Pass 2: fallback to whitespace boundaries ---
        sb.setLength(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (Character.isWhitespace(c)) {
                if (getSize.apply(sb.toString()) > tokenSizeCap) break;
                bestCut = sb.length();
            }
        }

        if (bestCut > 0) {
            return text.substring(0, bestCut);
        }

        // --- Pass 3: hard cut (binary search) ---
        int low = 0, high = text.length();
        while (low < high) {
            int mid = (low + high) / 2;
            String candidate = text.substring(0, mid);
            if (getSize.apply(candidate) <= tokenSizeCap) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        return text.substring(0, Math.max(0, low - 1));
    }

    public static Client createGoogleClient(String baseUrl, String apiKey) {
        HttpOptions.Builder googeHttpOpt = HttpOptions.builder();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            googeHttpOpt.baseUrl(baseUrl);
        }
        googeHttpOpt.timeout(120);
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(googeHttpOpt.build())
                .build();
    }

    public static List<String> getChunks(String input, int tokenSizeCap, Function<String, Integer> getSize) {
        if (tokenSizeCap == Integer.MAX_VALUE) {
            return List.of(input);
        }
        int fullSize = getSize.apply(input);
        List<String> result = new ObjectArrayList<>();
        if (fullSize <= tokenSizeCap) {
            result.add(input);
            return result;
        }

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
