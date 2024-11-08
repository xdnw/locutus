package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class WebBulkQuery {
    public List<WebSuccess> results;

    public WebBulkQuery(List<WebSuccess> results) {
        this.results = results;
    }
}
