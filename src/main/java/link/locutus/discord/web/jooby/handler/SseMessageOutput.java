package link.locutus.discord.web.jooby.handler;

import io.javalin.http.Context;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;

import java.io.IOException;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SseMessageOutput implements IMessageOutput {
    public final Context ctx;

    public SseMessageOutput(Context ctx) {
        this.ctx = ctx;
    }

    public void sseMessage(String message, boolean success) {
        Map<String, Object> data = Map.of("content", message, "success", success);
        sendEvent(data);
    }

    @Override
    public void sendEvent(byte[] data) {
        AsyncContext async = ctx.req().getAsyncContext();
        try {
            ServletOutputStream output = async.getResponse().getOutputStream();
            output.write(data);
            async.getResponse().flushBuffer();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
