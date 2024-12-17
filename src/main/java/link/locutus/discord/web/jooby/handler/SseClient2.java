package link.locutus.discord.web.jooby.handler;

import io.javalin.http.Context;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SseClient2 implements IMessageOutput {
    public final Context ctx;

    public SseClient2(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void sendEvent(String message) {
        sendEvent(message.getBytes(UTF_8));
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

//    public static byte[] toBytes(Object id, Object retry, String event, byte[] bytes) throws IOException {
//        ByteArrayOutputStream buffer = new ByteArrayOutputStream(bytes.length);
//        if (id != null) {
//            buffer.write(ID);
//            buffer.write(id.toString().getBytes(UTF_8));
//            buffer.write(SEPARATOR);
//        }
//        if (event != null) {
//            buffer.write(EVENT);
//            buffer.write(event.getBytes(UTF_8));
//            buffer.write(SEPARATOR);
//        }
//        if (retry != null) {
//            buffer.write(RETRY);
//            buffer.write(retry.toString().getBytes(UTF_8));
//            buffer.write(SEPARATOR);
//        }
//        buffer.write(DATA);
//        int offset = 0;
//        for (int i = 0; i < bytes.length; i++) {
//            byte ch = bytes[i];
//            if (ch == '\n') {
//                buffer.write(Arrays.copyOfRange(bytes, offset, offset + i));
//                buffer.write(SEPARATOR);
//                if (i + 1 < bytes.length) {
//                    buffer.write(DATA);
//                }
//                offset = i + 1;
//            }
//        }
//        if (offset == 0) {
//            buffer.write(bytes);
//        } else if (offset < bytes.length) {
//            buffer.write(Arrays.copyOfRange(bytes, offset, bytes.length));
//        }
//        buffer.write(SEPARATOR);
//        buffer.write(SEPARATOR);
//
//        return buffer.toByteArray();
//    }
}
