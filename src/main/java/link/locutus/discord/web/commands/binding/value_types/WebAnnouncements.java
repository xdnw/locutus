package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public class WebAnnouncements extends WebSuccess {
    public List<WebAnnouncement> values;

    public WebAnnouncements() {
        super(true, null);
        this.values = new ObjectArrayList<>();
    }
}
