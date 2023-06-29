package link.locutus.discord.gpt.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CopilotApi implements ICopilotApi{
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final ICopilotAuthentication _copilotAuthentication;
    private final CopilotConfiguration _configuration;
    private final HttpClientWrapper _httpClient;

    /// <summary>
    /// Creates a new instance of the copilot api with dependencies.
    /// </summary>
    /// <param name="configuration"><see cref="CopilotConfiguration"/> configuration.</param>
    /// <param name="authentication">Handles the authentication flow.</param>
    /// <param name="httpClient">HttpClientWrapper for performing web requests.</param>
    public CopilotApi(CopilotConfiguration configuration, ICopilotAuthentication authentication,
                      HttpClientWrapper httpClient)
    {
        _configuration = configuration;
        _copilotAuthentication = authentication;
        _httpClient = httpClient;
    }
    
    @Override
    public CompletableFuture<List<String>> GetStringCompletionsAsync(String prompt) throws ExecutionException, InterruptedException, JsonProcessingException {
        var copilotParameters = new CopilotParameters();
        copilotParameters.Prompt = prompt;
        return GetCompletionsAsync(copilotParameters).thenApply(f -> f.stream().map(g -> g.choices[0].Text).toList());
    }

    @Override
    public CompletableFuture<List<CopilotResult>> GetCompletionsAsync(CopilotParameters parameters) throws JsonProcessingException, ExecutionException, InterruptedException {

        _httpClient.AddOrReplaceHeader("OpenAI-Intent", parameters.OpenAiIntent);

        String parametersJson = CopilotApi.JSON_MAPPER.writeValueAsString(parameters);
        return GetRawCompletionsAsync(parametersJson).thenApply(new Function<String, List<CopilotResult>>() {
            @Override
            public List<CopilotResult> apply(String rawResult) {
                try {
                    List<String> lines = Arrays.asList(rawResult.split("\n")).stream().filter(f -> !f.isEmpty()).collect(Collectors.toList());
                    lines.remove(lines.size() - 1);
                    List<CopilotResult> results = new ArrayList<CopilotResult>();

                    System.out.println("Lines " + rawResult);
                    System.out.println("Num lines: " + lines.size());
                    for (String line : lines) {
                        System.out.println("Line " + line);

                        var content = line.replace("data: ", "");
                        CopilotResult resultObject = CopilotApi.JSON_MAPPER.readValue(content, CopilotResult.class);
                        results.add(resultObject);
                    }

                    return results;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public CompletableFuture<String> GetRawCompletionsAsync(String rawContent) throws ExecutionException, JsonProcessingException, InterruptedException {
        return _copilotAuthentication.GetAccessTokenAsync().thenApply(new Function<String, String>() {
            @Override
            public String apply(String accessToken) {
                _httpClient.AddOrReplaceHeader("User-Agent", _configuration.UserAgent);
                _httpClient.AddOrReplaceHeader("Accept", "application/json");
                _httpClient.AddOrReplaceHeader("Authorization", "Bearer " + accessToken);
                try {
                    HttpResponse<String> response = _httpClient.PostAsync(
                            "https://copilot-proxy.githubusercontent.com/v1/engines/copilot-codex/completions",
                            rawContent).get();
                    return response.body();
                } catch (URISyntaxException | ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public void close() throws IOException {
        _copilotAuthentication.close();
    }
}
