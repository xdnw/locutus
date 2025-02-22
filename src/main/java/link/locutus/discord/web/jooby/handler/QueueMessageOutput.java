package link.locutus.discord.web.jooby.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import link.locutus.discord.web.jooby.WebRoot;

import java.io.IOException;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class QueueMessageOutput implements IMessageOutput {
    public final ObjectArrayList<Map<String, Object>> queue = new ObjectArrayList<>();

    @Override
    public void sendEvent(Map<String, Object> obj) {
        queue.add(obj);
    }

    @Override
    public void sendEvent(byte[] data) {
        throw new UnsupportedOperationException();
    }

    public ObjectArrayList<Map<String, Object>> getQueue() {
        return queue;
    }
}
