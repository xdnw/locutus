package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class WebTable {
    public @Nullable final List<WebTableError> errors;
    public final List<List<Object>> cells;
    public @Nullable final List<String> renderers;

    public WebTable(List<List<Object>> cells, List<WebTableError> errors, List<String> renderers) {
        this.cells = cells;
        this.errors = errors;
        this.renderers = renderers;
    }
}