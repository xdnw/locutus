package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.DBNation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WebTargets {
    public List<WebTarget> targets;
    public boolean include_strength;
    public WebTarget self;

    public WebTargets(DBNation self) {
        this.targets = new ArrayList<>();
        this.self = new WebTarget(self, 0, 0, 1);
    }
}
