package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class WebAudits extends WebSuccess {
    public List<WebAudit> values;
    public WebAudits() {
        super(true, null);
        this.values = new ObjectArrayList<>();
    }
}
