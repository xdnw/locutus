package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class WebTable {
    public @Nullable final List<String> errors;
    public final List<List<Object>> cells;
    private @Nullable final List<String> renderers;

    public WebTable(List<List<Object>> cells, List<String> errors, List<String> renderers) {
        this.cells = cells;
        this.errors = errors;
        this.renderers = renderers;
    }
}
