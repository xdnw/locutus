package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Map;

public class WebViewCommand {
    public long uid;
    public ObjectArrayList<Map<String, Object>> data;

    public WebViewCommand(long uid, ObjectArrayList<Map<String, Object>> data) {
        this.uid = uid;
        this.data = data;
    }
}
