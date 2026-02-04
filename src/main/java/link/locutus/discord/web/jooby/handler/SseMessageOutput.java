package link.locutus.discord.web.jooby.handler;

import io.javalin.http.Context;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import link.locutus.discord.Logg;

public final class SseMessageOutput implements IMessageOutput {
    private final ServletOutputStream out;
    private volatile boolean closed = false;
    private final Context ctx;

    public SseMessageOutput(Context ctx) {
        this.ctx = ctx;
        try {
            this.out = ctx.res().getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Context ctx() { return ctx; }

    public void sseMessage(String message, boolean success) {
        Map<String, Object> data = Map.of("content", message, "success", success);
        Logg.info("sseMessage -> content='" + message + "' success=" + success);
        sendEvent(data);
    }

    @Override
    public synchronized void sendEvent(byte[] payload) {
        if (closed) return;

        try {
            int len = payload.length;
            // 4-byte big-endian length prefix
            out.write((len >>> 24) & 0xFF);
            out.write((len >>> 16) & 0xFF);
            out.write((len >>>  8) & 0xFF);
            out.write((len       ) & 0xFF);

            out.write(payload);
            out.flush();
        } catch (IOException | IllegalStateException e) {
            // client disconnected or stream no longer writable
            closed = true;
        }
    }

    public synchronized void close() {
        closed = true;
        try { out.flush(); } catch (IOException ignore) {}
    }

    public void markClosed() { closed = true; } // optional
}