package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.db.entities.DBNation;

import java.util.List;

public class WebTargets {
    public List<WebTarget> targets;
    public boolean include_strength;
    public WebTarget self;

    public WebTargets(DBNation self) {
        this.targets = new ObjectArrayList<>();
        this.self = new WebTarget(self, 0, 0, 1);
    }
}
