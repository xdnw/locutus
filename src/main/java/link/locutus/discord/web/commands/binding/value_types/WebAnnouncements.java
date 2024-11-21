package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.List;

public class WebAnnouncements {
    public List<WebAnnouncement> values;

    public WebAnnouncements() {
        this.values = new ObjectArrayList<>();
    }
}
