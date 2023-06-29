package link.locutus.discord.gpt.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface ICopilotAuthentication extends Closeable {
    /// <summary>
    /// Gets called when the authentication flow requires user input.
    /// </summary>
    void OnEnterDeviceCode(CopilotDeviceAuthenticationData data);

    /// <summary>
    /// Gets the access token, which can be used to authenticate http requests to the copilot http api.
    /// </summary>
    /// <returns>Access Token.</returns>
    CompletableFuture<String> GetAccessTokenAsync() throws JsonProcessingException, ExecutionException, InterruptedException;
}
