package link.locutus.discord.web.jooby.handler;

import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DummyMessageOutput implements IMessageOutput {
    private Map<Long, String> outputs = new LinkedHashMap<>();

    public DummyMessageOutput() {
    }

    @Override
    public void sendEvent(String msg) {
        if (msg == null || msg.isEmpty()) return;
        long id = ThreadLocalRandom.current().nextLong();
        outputs.put(id, MarkupUtil.markdownToHTML(msg));
    }

    @Override
    public void sendEvent(JsonObject obj) {
        JsonElement content = obj.get("content");
        if (content != null) {
            String contentStr = content.toString();
            if (contentStr.isEmpty()) return;
            contentStr = MarkupUtil.markdownToHTML(contentStr);
            long id = ThreadLocalRandom.current().nextLong();
            JsonElement idObj = obj.get("id");
            if (idObj != null) id = Long.parseLong(idObj.toString());
            outputs.put(id, contentStr);
        } else {
            JsonElement action = obj.get("action");
            JsonElement value = obj.get("value");
            if (action != null && action.getAsString().equalsIgnoreCase("deleteByIds")) {
                for (JsonElement idObj : value.getAsJsonArray()) {
                    outputs.remove(Long.parseLong(idObj.getAsString()));
                }
            }
        }
    }

    public String getOutput() {
        return StringMan.join(outputs.values(), "<br>");
    }
}
