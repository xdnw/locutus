package link.locutus.discord.web.jooby.handler;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Map;

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
