package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class WebTable {
    public final List<String> errors;
    public final List<List<Object>> cells;

    public WebTable(List<List<Object>> cells, List<String> errors) {
        this.cells = cells;
        this.errors = errors;
    }
}
