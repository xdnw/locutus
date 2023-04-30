package link.locutus.discord.commands.manager.v2.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public abstract class CopilotAuthentication implements ICopilotAuthentication {
    private final CopilotConfiguration _configuration;
    private final IDataStore _dataStore;
    private final HttpClientWrapper _httpClient;

    /// <summary>
    /// Creates a new instance of the Copilot Authentication flow.
    /// </summary>
    /// <param name="configuration">Configuration for the API.</param>
    /// <param name="dataStore">Storage where to store tokens.</param>
    /// <param name="httpClient">HttpClientWrapper to perform web requests.</param>
    public CopilotAuthentication(CopilotConfiguration configuration, IDataStore dataStore, HttpClientWrapper httpClient)
    {
        _configuration = configuration;
        _dataStore = dataStore;
        _httpClient = httpClient;
    }
    
    @Override
    public abstract void OnEnterDeviceCode(CopilotDeviceAuthenticationData data);

    @Override
    public CompletableFuture<String> GetAccessTokenAsync() throws JsonProcessingException, ExecutionException, InterruptedException {
        return _dataStore.GetAsync().thenApply(new Function<String, String>() {
            @Override
            public String apply(String rawToken) {
                try {
                    CopilotAuthenticationData authenticationData = new CopilotAuthenticationData();

                    if (rawToken != null && !rawToken.isEmpty()) {
                        authenticationData = CopilotApi.JSON_MAPPER.readValue(rawToken, CopilotAuthenticationData.class);
                    }

                    if (authenticationData != null && authenticationData.AccessToken != null && System.currentTimeMillis() < authenticationData.AccessTokenValidTo.getTime()) {
                        return authenticationData.AccessToken;
                    }

                    _httpClient.AddOrReplaceHeader("User-Agent", _configuration.UserAgent);
                    _httpClient.AddOrReplaceHeader("Accept", "application/json");

                    authenticationData.GithubToken = GetSessionToken(authenticationData).get();
                    _httpClient.AddOrReplaceHeader("Authorization", "token " + authenticationData.GithubToken);
                    var response = _httpClient.GetAsync("https://api.github.com/copilot_internal/v2/token").get();
                    var jsonResult = response.body();
                    JsonNode jsonObject = CopilotApi.JSON_MAPPER.readTree(jsonResult);
                    authenticationData.AccessToken = jsonObject.get("token").asText();

                    long expires_at = jsonObject.get("expires_at").asLong();

                    authenticationData.AccessTokenValidTo = new Date(expires_at);
                    _dataStore.SaveAsync(CopilotApi.JSON_MAPPER.writeValueAsString(authenticationData));

                    return authenticationData.AccessToken;
                } catch (ExecutionException | InterruptedException | URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /// <summary>
    /// Gets a session authenticationData, which identifies the current device session with Github.
    /// </summary>
    /// <returns>Valid Session Token.</returns>
    private CompletableFuture<String> GetSessionToken(CopilotAuthenticationData authenticationData)
    {
        if (authenticationData.GithubToken != null)
        {
            return CompletableFuture.completedFuture(authenticationData.GithubToken);
        }

        return GetDeviceToken().thenApply(new Function<String[], String>() {
            @Override
            public String apply(String[] tokens) {
                var deviceToken = tokens[0];
                var userCode = tokens[1];

                CopilotDeviceAuthenticationData authData = new CopilotDeviceAuthenticationData();
                authData.Url = "https://github.com/login/device";
                authData.UserCode = userCode;
                CopilotAuthentication.this.OnEnterDeviceCode(authData);

                // Wait until the user has entered the device authenticationData in Github.
                while (true)
                {
                    try {
                        Thread.sleep(5000);
                        String url = "https://github.com/login/oauth/access_token?client_id=" + _configuration.GithubAppId + "&device_code=" + deviceToken + "&grant_type=" + _configuration.GithubGrantType;

                        var response = _httpClient.PostAsync(url, "").get();
                        var jsonResult = response.body();
                        JsonNode jsonObject = CopilotApi.JSON_MAPPER.readTree(jsonResult);

                        if (!jsonObject.has("error")) {
                            authenticationData.GithubToken = jsonObject.get("access_token").asText();
                            return authenticationData.GithubToken;
                        }
                    } catch (InterruptedException | ExecutionException | IOException | URISyntaxException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    /// <summary>
    /// Gets a new device code identifying this device.
    /// </summary>
    /// <returns>Device Token.</returns>
    private CompletableFuture<String[]> GetDeviceToken() {
        String url = "https://github.com/login/device/code?client_id=" + _configuration.GithubAppId + "&scope=read:user";
        try {
            return _httpClient.PostAsync(url, "").thenApply(new Function<HttpResponse<String>, String[]>() {
                @Override
                public String[] apply(HttpResponse<String> response) {
                    System.out.println("Response " + response.headers() + "\n  - status:" + response.statusCode());
                    var jsonResult = response.body();

                    System.out.println("Result " + jsonResult);

                    JsonNode jsonObject = null;
                    try {
                        jsonObject = CopilotApi.JSON_MAPPER.readTree(jsonResult);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    var deviceToken = jsonObject.get("device_code").asText();
                    var userCode = jsonObject.get("user_code").asText();
                    return new String[] {deviceToken, userCode};
                }
            });
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        System.out.println("Closed http client");
    }
}
