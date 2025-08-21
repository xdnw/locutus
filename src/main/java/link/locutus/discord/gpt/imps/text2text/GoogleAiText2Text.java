package link.locutus.discord.gpt.imps.text2text;

import com.google.genai.Client;
import com.google.genai.types.*;

import java.util.HashMap;
import java.util.Map;

public class GoogleAiText2Text implements IText2Text {

    private final Client client;
    private final String modelName;

    private Model model;
    private Integer tokenLimit;

    public GoogleAiText2Text(Client client, String modelName) {
        this.client = client;
        this.modelName = modelName;
    }

    // Optional init similar to embedding class to fetch token limits
    public void init() {
        try {
            this.model = client.models.get(modelName, null);
            this.tokenLimit = model.inputTokenLimit().orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init model: " + modelName, e);
        }
    }

    @Override
    public String getId() {
        return "google-gemini:" + modelName;
    }

    @Override
    public Map<String, String> getOptions() {
        // Defaults that can be overridden in generate(options, text)
        Map<String, String> opts = new HashMap<>();
        opts.put("temperature", "0.7");
        opts.put("maxOutputTokens", "1024");
        opts.put("topP", "0.95");
        opts.put("topK", "40");
        opts.put("system", "");
        return opts;
    }

    @Override
    public String generate(Map<String, String> options, String text) {
        init(); // Ensure model is initialized
        try {
            Map<String, String> opts = new HashMap<>(getOptions());
            if (options != null) opts.putAll(options);

            Float temperature = parseFloat(opts.get("temperature"), null);
            Integer maxOutputTokens = parseInt(opts.get("maxOutputTokens"), null);
            Float topP = parseFloat(opts.get("topP"), null);
            Float topK = parseFloat(opts.get("topK"), null);
            String system = opts.getOrDefault("system", null);

            GenerateContentConfig.Builder cfg = GenerateContentConfig.builder();
            if (temperature != null) cfg = cfg.temperature(temperature);
            if (maxOutputTokens != null) cfg = cfg.maxOutputTokens(maxOutputTokens);
            if (topP != null) cfg = cfg.topP(topP);
            if (topK != null) cfg = cfg.topK(topK);

            if (system != null && !system.isEmpty()) {
                Content sys = Content.builder()
                        .role("system")
                        .parts(Part.fromText(system))
                        .build();
                cfg = cfg.systemInstruction(sys);
            }

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
            if (sb.length() > 0) return sb.toString();

            throw new RuntimeException("No text generated for input.");
        } catch (Exception e) {
            throw new RuntimeException("Error generating content", e);
        }
    }

    // ITokenizer-style helpers (token count and cap)
    public int getSize(String text) {
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