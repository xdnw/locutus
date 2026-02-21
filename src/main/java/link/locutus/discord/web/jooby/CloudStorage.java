package link.locutus.discord.web.jooby;

import java.io.IOException;
import java.util.List;

public interface CloudStorage extends AutoCloseable {
    /**
     * Uploads an object to the cloud storage.
     * 
     * @param key    the key (path) where the object will be stored
     * @param data   the byte array of the object to be stored
     * @param maxAge the maximum age TTL (in seconds) for caching purposes
     */
    void putObject(String key, byte[] data, long maxAge);

    byte[] getObject(String key) throws IOException;

    /**
     * Generates a link to access the object stored in the cloud storage for use
     * within a static web site (e.g. embed image)
     * 
     * @param key
     * @return
     */
    String getLink(String key);

    void deleteObject(String key);

    List<CloudItem> getObjects();

    /**
     * Lists objects in the storage, optionally constrained by prefix.
     * Passing null or blank behaves the same as {@link #getObjects()}.
     */
    default List<CloudItem> getObjects(String prefix) {
        return getObjects();
    }

    void close();
}
