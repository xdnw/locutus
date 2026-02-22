package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;
import java.util.Map;

public class WebVirtualConflict {
    public String id;
    public String name;
    public String category;
    public long date;
    public long end;
    public String wiki;
    public String cb;
    public String status;
    public ConflictAlliances alliances;
    public Map<String, List<List<Object>>> posts;

    public WebVirtualConflict() {
    }

    public WebVirtualConflict(String id, String name, String category, long date, long end, String wiki, String cb,
            String status, ConflictAlliances alliances, Map<String, List<List<Object>>> posts) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.date = date;
        this.end = end;
        this.wiki = wiki;
        this.cb = cb;
        this.status = status;
        this.alliances = alliances;
        this.posts = posts;
    }
}
