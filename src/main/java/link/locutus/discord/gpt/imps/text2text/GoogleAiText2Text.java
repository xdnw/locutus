package link.locutus.discord.gpt.imps.text2text;

import com.google.genai.Client;
import com.google.genai.types.*;
import link.locutus.discord.gpt.GPTUtil;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class GoogleAiText2Text implements IText2Text {

    private final Client client;
    private final String modelName;

    private Integer tokenLimit;
    private boolean isGeminiModel = false;

    public GoogleAiText2Text(Client client, String modelName) {
        this.client = client;
        this.modelName = modelName;
        this.isGeminiModel = modelName.toLowerCase().contains("gemini");
    }

    // Optional init similar to embedding class to fetch token limits
    public void init() {
        try {
            Model model = client.models.get(modelName, null);
            this.tokenLimit = model.inputTokenLimit().orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init model: " + modelName, e);
        }
    }

    @Override
    public String getId() {
        return "google_" + modelName;
    }

    @Override
    public String generate(String text, IntConsumer tokenUsage) {
        try {
            GenerateContentConfig.Builder cfg = GenerateContentConfig.builder();

            GenerateContentResponse response = client.models.generateContent(modelName, text, cfg.build());

            String out = response.text();
            if (out != null) return out;

            StringBuilder sb = new StringBuilder();
            response.candidates().ifPresent(cands -> {
                for (var c : cands) {
                    c.content().flatMap(Content::parts).ifPresent(parts -> {
                        for (Part p : parts) {
                            p.text().ifPresent(sb::append);
                        }
                    });
                }
            });
            Supplier<Integer> backupTokenCount = () -> getSize(text + "\n" + sb.toString());
            if (tokenUsage != null) {
                int tokens = response.usageMetadata()
                        .map(usage -> usage.totalTokenCount().orElseGet(backupTokenCount))
                        .orElseGet(backupTokenCount);
                tokenUsage.accept(tokens);
            }

            if (sb.length() > 0) return sb.toString();
            throw new RuntimeException("No text generated for input.");
        } catch (Exception e) {
            throw new RuntimeException("Error generating content", e);
        }
    }

    // ITokenizer-style helpers (token count and cap)
    public int getSize(String text) {
        if (isGeminiModel) {
            return GPTUtil.countSentencePieceTokens(text);
        }
        try {
            return client.models.countTokens(modelName, text, null).totalTokens().orElseThrow();
        } catch (Exception e) {
            throw new RuntimeException("Error counting tokens", e);
        }
    }

    public int getSizeCap() {
        if (tokenLimit == null) {
            // Lazy init if not initialized explicitly
            init();
        }
        return tokenLimit != null ? tokenLimit : 0;
    }

    public void close() {
        // no-op; client is managed externally
    }

    private static Float parseFloat(String s, Float def) {
        try { return s == null ? def : Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private static Integer parseInt(String s, Integer def) {
        try { return s == null ? def : Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}