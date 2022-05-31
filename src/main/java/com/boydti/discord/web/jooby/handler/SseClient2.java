package com.boydti.discord.web.jooby.handler;

import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.PnwUtil;
import io.javalin.http.Context;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SseClient2 implements IMessageOutput {
    private static final byte SEPARATOR = '\n';

    private static final byte[] ID = "id:".getBytes(UTF_8);

    private static final byte[] EVENT = "event:".getBytes(UTF_8);

    private static final byte[] RETRY = "retry:".getBytes(UTF_8);

    private static final byte[] DATA = "data:".getBytes(UTF_8);

    public final Context ctx;

    public SseClient2(Context ctx) {
        this.ctx = ctx;
    }

    public void sendEvent(String event, String message) {
        sendEvent   (null, null, event, message);
    }

    @Override
    public void sendEvent(String message) {
        sendEvent(null, null, "message", message);
    }

    public void sendEvent(Object id, Object retry, String event, String message) {
        AsyncContext async = ctx.req.getAsyncContext();
        try {
            ServletOutputStream output = async.getResponse().getOutputStream();
            byte[] bytes = toBytes(id, retry, event, message);
            output.write(bytes);
            async.getResponse().flushBuffer();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static byte[] toBytes(Object id, Object retry, String event, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(bytes.length);
        if (id != null) {
            buffer.write(ID);
            buffer.write(id.toString().getBytes(UTF_8));
            buffer.write(SEPARATOR);
        }
        if (event != null) {
            buffer.write(EVENT);
            buffer.write(event.getBytes(UTF_8));
            buffer.write(SEPARATOR);
        }
        if (retry != null) {
            buffer.write(RETRY);
            buffer.write(retry.toString().getBytes(UTF_8));
            buffer.write(SEPARATOR);
        }
        buffer.write(DATA);
        int offset = 0;
        for (int i = 0; i < bytes.length; i++) {
            byte ch = bytes[i];
            if (ch == '\n') {
                buffer.write(Arrays.copyOfRange(bytes, offset, offset + i));
                buffer.write(SEPARATOR);
                if (i + 1 < bytes.length) {
                    buffer.write(DATA);
                }
                offset = i + 1;
            }
        }
        if (offset == 0) {
            buffer.write(bytes);
        } else if (offset < bytes.length) {
            buffer.write(Arrays.copyOfRange(bytes, offset, bytes.length));
        }
        buffer.write(SEPARATOR);
        buffer.write(SEPARATOR);

        return buffer.toByteArray();
    }
}
