package link.locutus.discord.web.commands.binding.value_types;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.db.entities.DBNation;

import java.util.List;

public class WebSpyTargets {
    public List<WebSpyTarget> targets;
    public WebTarget self;
    public String message;

    public WebSpyTargets(DBNation self) {
        this.targets = new ObjectArrayList<>();
        this.self = new WebTarget(self, 0, 0, 1);
    }
}