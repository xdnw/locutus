package link.locutus.discord.event.nation;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.event.guild.GuildScopeEvent;

import java.util.Map;

public class NationBlockadedEvent extends GuildScopeEvent {
    private final int blockader;
    private final int blockaded;
    private final Map<Integer, Integer> blockaders;

    public NationBlockadedEvent(int blockaded, int blockader, Map<Integer, Integer> blockaders) {
        this.blockaded = blockaded;
        this.blockader = blockader;
        this.blockaders = blockaders;
    }

    public Map<Integer, Integer> getBlockaders() {
        return blockaders;
    }

    public int getBlockaded() {
        return blockaded;
    }

    public int getBlockader() {
        return blockader;
    }

    public DBNation getBlockadedNation() {
        return DBNation.getById(blockaded);
    }

    public DBNation getBlockaderNation() {
        return DBNation.getById(blockader);
    }

    @Override
    protected void postToGuilds() {
        DBNation nation = getBlockadedNation();
        if (nation != null && nation.getAlliance_id() != 0 && nation.getPosition() > Rank.APPLICANT.id) {
            post(Locutus.imp().getGuildDBByAA(nation.getAlliance_id()));
        }
    }
}
