package link.locutus.discord.gpt.imps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.knuddels.jtokkit.api.ModelType;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.copilot.CopilotApi;
import link.locutus.discord.gpt.copilot.CopilotAuthentication;
import link.locutus.discord.gpt.copilot.CopilotConfiguration;
import link.locutus.discord.gpt.copilot.CopilotDeviceAuthenticationData;
import link.locutus.discord.gpt.copilot.CopilotParameters;
import link.locutus.discord.gpt.copilot.CopilotResult;
import link.locutus.discord.gpt.copilot.FileDataStore;
import link.locutus.discord.gpt.copilot.HttpClientWrapper;
import link.locutus.discord.util.StringMan;

import java.io.File;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

public class CopilotText2Text implements IText2Text {
    private final CopilotApi copilotApi;
    private final String tokensPath;
    private final CopilotOptions defaultOptions = new CopilotOptions();

    public CopilotText2Text(String tokensPath, Consumer<CopilotDeviceAuthenticationData> authCallback) {
        this.tokensPath = tokensPath;
        HttpClient httpClient = HttpClient.newBuilder()
//                .authenticator()
                .connectTimeout(Duration.ofSeconds(120))
                .build();
        HttpClientWrapper wrapper = new HttpClientWrapper(httpClient);

        var copilotConfiguration = new CopilotConfiguration();
        // Should be where your application stores files. You can also implement CopilotDev.NET.Api.Contract.IDataStore instead to store it in your own storage (e.g. database).
        var dataStore = new FileDataStore(tokensPath + File.separator + "tokens.json");
        var copilotAuthentication = new CopilotAuthentication(copilotConfiguration, dataStore, wrapper) {
            @Override
            public void OnEnterDeviceCode(CopilotDeviceAuthenticationData data) {
                authCallback.accept(data);
            }
        };
        this.copilotApi = new CopilotApi(copilotConfiguration, copilotAuthentication, wrapper);
    }
    @Override
    public String getId() {
        return "Copilot-" + tokensPath;
    }

    @Override
    public Map<String, String> getOptions() {
        return Map.of(
                "temperature", "0.7",
                "stop_sequences", "\n\n",
                "top_p", "1",
                "max_tokens", "2000"
        );
    }

    private static class CopilotOptions {
        public float temperature = 0.7F;
        public String[] stopChars = new String[]{"\n\n"};
        public float top_p = 1F;
        public Integer maxTokens = 2000;

        public CopilotOptions setOptions(CopilotText2Text parent, Map<String, String> options) {
            // reset options
            temperature = 0.7F;
            stopChars = new String[]{"\n\n"};
            top_p = 1F;
            maxTokens = 2000;

            if (options != null) {
                for (Map.Entry<String, String> entry : options.entrySet()) {
                    switch (entry.getKey().toLowerCase()) {
                        case "temperature":
                            temperature = Float.parseFloat(entry.getValue());
                            checkArgument(temperature >= 0 && temperature <= 2, "Temperature must be between 0 and 2");
                            break;
                        case "stop_sequences":
                            stopChars = entry.getValue().replace("\\n", "\n").split(",");
                            break;
                        case "top_p":
                            top_p = Float.parseFloat(entry.getValue());
                            break;
                        case "max_tokens":
                            maxTokens = Integer.parseInt(entry.getValue());
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown option: " + entry.getKey() + ". Valid options are: " + StringMan.getString(parent.getOptions()));
                    }
                }
            }
            return this;
        }
    }

    @Override
    public String generate(Map<String, String> options, String text) {
        CopilotOptions optObj = options == null || options.isEmpty() ? defaultOptions : new CopilotOptions().setOptions(this, options);
        CopilotParameters parameters = new CopilotParameters();

        parameters.MaxTokens = optObj.maxTokens;
        parameters.Temperature = optObj.temperature;
        parameters.Stop = optObj.stopChars;
        parameters.TopP = optObj.top_p;

        List<CopilotResult> completions2 = null;
        try {
            completions2 = copilotApi.GetCompletionsAsync(parameters).get();
        } catch (InterruptedException | ExecutionException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return String.join("", completions2.stream().map(f -> f.choices[0].Text).toList());
    }

    @Override
    public int getSize(String text) {
        return GPTUtil.getTokens(text, ModelType.GPT_3_5_TURBO);
    }

    @Override
    public int getSizeCap() {
        return 8094;
    }
}
