package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class WebTable {
    public List<List<Object>> cells;

    public WebTable(List<List<Object>> cells) {
        this.cells = cells;
    }
}
