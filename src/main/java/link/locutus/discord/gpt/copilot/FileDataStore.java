package link.locutus.discord.gpt.copilot;

import link.locutus.discord.Logg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public class FileDataStore implements IDataStore {
    private final File _file;

    /// <summary>
    /// Creates a new <see cref="FileDataStore"/> with the file being the given <paramref name="filePath"/>.
    /// </summary>
    /// <param name="filePath">Path to the file where the token should be stored. Needs to be in an existing directory.</param>
    public FileDataStore(String filePath)
    {
        _file = new File(filePath);
        if (!_file.exists())
        {
            try {
                _file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /// <summary>
    /// Retrieves the previously stored JSON string from this data store.
    /// </summary>
    /// <returns>JSON string or null if not found.</returns>
    public CompletableFuture<String> GetAsync()
    {
        if (!_file.exists())
        {
            Logg.text("File does not exist " + _file.getAbsolutePath());
            return null;
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.readString(_file.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /// <summary>
    /// Stores the given <paramref name="data"/> into this data store.
    /// </summary>
    /// <param name="data">Not null JSON string.</param>
    /// <returns><see cref="Task"/></returns>
    public CompletableFuture<Object> SaveAsync(String data)
    {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.writeString(_file.toPath(), data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
