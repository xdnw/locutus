package link.locutus.discord.web.jooby.handler;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.Header;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import link.locutus.discord.Logg;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Consumer;

public final class SseHandler2 implements Handler {
    private final Consumer<SseMessageOutput> clientConsumer;

    public SseHandler2(Consumer<SseMessageOutput> clientConsumer) {
        this.clientConsumer = clientConsumer;
    }

    @Override
    public void handle(@NotNull Context ctx) {
        ctx.status(200);
        ctx.contentType("application/x-msgpack");
        ctx.header(Header.CACHE_CONTROL, "no-cache");
        ctx.header("X-Accel-Buffering", "no"); // useful behind proxies

        ctx.async(() -> {
            ctx.req().getAsyncContext().setTimeout(0);

            SseMessageOutput sse = new SseMessageOutput(ctx);

            try {
                clientConsumer.accept(sse);
            } finally {
                sse.close(); // just marks closed; let Javalin complete by returning
            }
        });
    }
}