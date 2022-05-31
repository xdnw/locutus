package com.boydti.discord.web.jooby.handler;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import java.io.IOException;
import java.util.function.Consumer;

public class SseHandler2 implements Handler {
    private final Consumer<SseClient2> clientConsumer;

    public SseHandler2(Consumer<SseClient2> clientConsumer) {
        this.clientConsumer = clientConsumer;
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.res.setStatus(200);
        ctx.res.setCharacterEncoding("UTF-8");
        ctx.contentType("text/event-stream");
        ctx.header(Header.CONNECTION, "close");
        ctx.header(Header.CACHE_CONTROL, "no-cache");
//                ctx.res.flushBuffer();

        if (!ctx.req.isAsyncStarted()) {
            ctx.req.startAsync(ctx.req, ctx.res);
        }
        AsyncContext async = ctx.req.getAsyncContext();
        async.setTimeout(0);

        ctx.res.flushBuffer();

        clientConsumer.accept(new SseClient2(ctx));

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
