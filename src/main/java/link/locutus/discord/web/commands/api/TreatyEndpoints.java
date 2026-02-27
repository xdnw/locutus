package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBTreatyChange;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.WebCurrentTreaties;
import link.locutus.discord.web.commands.binding.value_types.WebCurrentTreaties.WebCurrentTreaty;
import link.locutus.discord.web.commands.binding.value_types.WebTreatyChanges;
import link.locutus.discord.web.commands.binding.value_types.WebTreatyChanges.WebTreatyChange;

import java.util.List;
import java.util.Set;

public class TreatyEndpoints {

    @Command(desc = "Get treaty changes (signed, extended, cancelled, expired) since a given timestamp", viewable = true)
    @ReturnType(WebTreatyChanges.class)
    public WebTreatyChanges treaty_changes(@Timestamp long start) {
        NationDB db = Locutus.imp().getNationDB();
        List<DBTreatyChange> changes = db.getTreatyChangesSince(start);
        List<WebTreatyChange> result = new ObjectArrayList<>(changes.size());
        for (DBTreatyChange change : changes) {
            result.add(new WebTreatyChange(
                    change.getTimestamp(),
                    change.getAction().name().toLowerCase(),
                    change.getTreatyType().getName(),
                    change.getFromAllianceId(),
                    change.getToAllianceId(),
                    change.getTurnsRemaining()
            ));
        }
        return new WebTreatyChanges(result);
    }

    @Command(desc = "Get all current active treaties", viewable = true)
    @ReturnType(WebCurrentTreaties.class)
    public WebCurrentTreaties current_treaties() {
        NationDB db = Locutus.imp().getNationDB();
        Set<Treaty> treaties = db.getTreaties();
        List<WebCurrentTreaty> result = new ObjectArrayList<>(treaties.size());
        for (Treaty treaty : treaties) {
            int turnsRemaining = treaty.isPermanent() ? -1 : treaty.getTurnsRemaining();
            result.add(new WebCurrentTreaty(
                    treaty.getType().getName(),
                    treaty.getFromId(),
                    treaty.getToId(),
                    turnsRemaining
            ));
        }
        return new WebCurrentTreaties(result);
    }
}
