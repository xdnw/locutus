package link.locutus.discord.commands.manager.v2.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface ICopilotApi extends Closeable {
    /// <summary>
    /// Gets the returned tokens from the Copilot AI programmer.
    /// Uses meaningful default parameters along with the given prompt. For a customization, use <see cref="GetCompletionsAsync"/> instead.
    /// </summary>
    /// <param name="prompt"></param>
    /// <returns>A list of strings containing the text values of the returned tokens.</returns>
    CompletableFuture<List<String>> GetStringCompletionsAsync(String prompt) throws ExecutionException, InterruptedException, JsonProcessingException;

    /// <summary>
    /// Gets the returned tokens from the Copilot AI programmer.
    /// Uses the Copilot Parameter data to customize the result.
    /// </summary>
    /// <param name="parameters"><see cref="CopilotParameters"/> parameters</param>
    /// <returns>A list of returned copilot tokens.</returns>
    CompletableFuture<List<CopilotResult>> GetCompletionsAsync(CopilotParameters parameters) throws JsonProcessingException, ExecutionException, InterruptedException;

    /// <summary>
    /// Gets the returned result from the CopilotAI programmer.
    /// Takes a raw string which is sent per http request and returns the raw result. Useful for testing unknown parameters or results.
    /// e.g.
    /// </summary>
    /// <param name="rawContent">Raw parameters.</param>
    /// <returns>Raw return result.</returns>
    CompletableFuture<String> GetRawCompletionsAsync(String rawContent) throws ExecutionException, JsonProcessingException, InterruptedException;
}
