package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class WebAudits {
    public List<WebAudit> values;
    public WebAudits() {
        this.values = new ObjectArrayList<>();
    }
}