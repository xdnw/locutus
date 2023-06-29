package link.locutus.discord.gpt.copilot;

import java.util.concurrent.CompletableFuture;

public interface IDataStore {
    /// <summary>
    /// Retrieves the previously stored JSON string from this data store.
    /// </summary>
    /// <returns>JSON string or null if not found.</returns>
    CompletableFuture<String> GetAsync();

    /// <summary>
    /// Stores the given <paramref name="data"/> into this data store.
    /// </summary>
    /// <param name="data">Not null JSON string.</param>
    /// <returns><see cref="Task"/></returns>
    CompletableFuture SaveAsync(String data);
}
