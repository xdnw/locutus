package link.locutus.discord.web.jooby.handler;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.Header;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Consumer;

public class SseHandler2 implements Handler {
    private final Consumer<SseMessageOutput> clientConsumer;

    public SseHandler2(Consumer<SseMessageOutput> clientConsumer) {
        this.clientConsumer = clientConsumer;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.res().setStatus(200);
        ctx.res().setCharacterEncoding("UTF-8");
        ctx.contentType("text/event-stream");
        ctx.header(Header.CONNECTION, "close");
        ctx.header(Header.CACHE_CONTROL, "no-cache");
//                ctx.res.flushBuffer();

        if (!ctx.req().isAsyncStarted()) {
            ctx.req().startAsync(ctx.req(), ctx.res());
        }
        AsyncContext async = ctx.req().getAsyncContext();
        async.setTimeout(0);

        ctx.res().flushBuffer();

        clientConsumer.accept(new SseMessageOutput(ctx));

        async.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) throws IOException {
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                event.getAsyncContext().complete();
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
                event.getAsyncContext().complete();
            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {

            }
        });
    }
}
