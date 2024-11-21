package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class WebBulkQuery {
    public List<Object> results;

    public WebBulkQuery(List<Object> results) {
        this.results = results;
    }
}
