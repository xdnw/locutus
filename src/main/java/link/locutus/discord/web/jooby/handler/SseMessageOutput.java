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
    private final AsyncContext async;
    private final ServletOutputStream out;

    private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean closeRequested = new AtomicBoolean(false);
    private final Context ctx;
    private volatile boolean closed = false;

    public SseMessageOutput(Context ctx) throws IOException {
        this.ctx = ctx;
        this.async = ctx.req().getAsyncContext();
        this.out = async.getResponse().getOutputStream();
        Logg.info("SseMessageOutput constructed for request=" + ctx.req().getRequestURI());
    }

    public Context ctx() {
        return ctx;
    }

    public void sseMessage(String message, boolean success) {
        Map<String, Object> data = Map.of("content", message, "success", success);
        Logg.info("sseMessage -> content='" + message + "' success=" + success);
        sendEvent(data);
    }

    @Override
    public void sendEvent(byte[] data) {
        System.out.println("sse sendEvent called with bytes=" + (data == null ? "[null]" : new String(data)));
        if (closed || closeRequested.get()) {
            Logg.info("sendEvent called but already closed or closing (closed=" + closed + ", closeRequested=" + closeRequested.get() + ")");
            return;
        }
        try {
            queue.add(data);
            Logg.info("sendEvent queued bytes=" + (data == null ? 0 : data.length) + " queueSizeMaybeApprox=");
        } catch (Exception e) {
            Logg.error("Failed to queue event: " + e.getMessage());
        }
        drain();
    }

    public void close() {
        Logg.info("close requested");
        closeRequested.set(true);
        drain(); // will complete once queue is empty
    }

    private void drain() {
        if (closed) {
            Logg.info("drain() called but already closed");
            return;
        }
        if (!draining.compareAndSet(false, true)) {
            Logg.info("drain() already in progress by another thread");
            return;
        }

        Logg.info("drain() starting async writer");
        async.start(() -> {
            try {
                byte[] msg;
                while ((msg = queue.poll()) != null) {
                    int len = msg.length;
                    out.write((len >>> 24) & 0xFF);
                    out.write((len >>> 16) & 0xFF);
                    out.write((len >>>  8) & 0xFF);
                    out.write((len       ) & 0xFF);
                    out.write(msg);
                    Logg.info("drain wrote message bytes=" + len);
                }
                out.flush();
                out.flush();
                Logg.info("drain flushed output stream");
            } catch (IOException | IllegalStateException e) {
                closed = true;
                Logg.error("Exception while draining SSE output: " + e.getMessage());
            } finally {
                draining.set(false);

                if (!closed && !queue.isEmpty()) {
                    Logg.info("drain finished but queue not empty, scheduling another drain");
                    drain();
                    return;
                }

                if (!closed && closeRequested.get()) {
                    closed = true;
                    try {
                        Logg.info("drain completing async context");
                        async.complete();
                    } catch (IllegalStateException ignore) {
                        Logg.error("IllegalStateException when completing async context");
                    }
                }
            }
        });
    }

    public void markClosed() {
        Logg.info("markClosed called");
        closed = true;
        closeRequested.set(true);
        queue.clear();
    }
}