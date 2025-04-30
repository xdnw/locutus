package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class WebGraph {
    public @Nullable TimeFormat time_format;
    public @Nullable TableNumberFormat number_format;
    public @Nullable GraphType type;
    public @Nullable long origin;

    public String title;
    public String x;
    public String y;
    public String[] labels;

    public List<List<?>> data;
}
