package link.locutus.discord.web.jooby;

import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.IOException;
import java.util.List;

public interface ICloudStorage extends AutoCloseable {
    void putObject(String key, byte[] data, long maxAge);
    byte[] getObject(String key) throws IOException;
    String getLink(String key);
    void deleteObject(String key);
    List<CloudItem> getObjects();
}
