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

public class SseHandler2 implements Handler {
    private final Consumer<SseMessageOutput> clientConsumer;

    public SseHandler2(Consumer<SseMessageOutput> clientConsumer) {
        this.clientConsumer = clientConsumer;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.res().setStatus(200);
        ctx.contentType("application/x-msgpack");
        ctx.header(Header.CACHE_CONTROL, "no-cache");
//                ctx.res.flushBuffer();

        if (!ctx.req().isAsyncStarted()) {
            ctx.req().startAsync(ctx.req(), ctx.res());
        }
        AsyncContext async = ctx.req().getAsyncContext();
        async.setTimeout(0);

        ctx.res().flushBuffer();

        SseMessageOutput sse = new SseMessageOutput(ctx);

        Logg.info("SseHandler2: AsyncContext started for request=" + ctx.req().getRequestURI());

        async.addListener(new AsyncListener() {
            @Override public void onComplete(AsyncEvent e) {
                Logg.info("SseHandler2: AsyncContext completed for request=" + ctx.req().getRequestURI());
                sse.markClosed();
            }
            @Override public void onError(AsyncEvent e)   {
                Logg.info("SseHandler2: AsyncContext error for request=" + ctx.req().getRequestURI() + " error=" + e.getThrowable());
                sse.markClosed();
            }
            @Override public void onTimeout(AsyncEvent e) {
                Logg.info("SseHandler2: AsyncContext timeout for request=" + ctx.req().getRequestURI());
                e.getAsyncContext().complete();
            }
            @Override public void onStartAsync(AsyncEvent e) {
                Logg.info("SseHandler2: AsyncContext started async for request=" + ctx.req().getRequestURI());
            }
        });

        async.start(() -> {
            Logg.info("SseHandler2: Running clientConsumer for request=" + ctx.req().getRequestURI());
            clientConsumer.accept(sse);
        }); // run PageHandler on async thread
    }
}
