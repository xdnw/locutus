package com.boydti.discord.db.entities;

import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;

import java.util.Set;
import java.util.function.Predicate;

public class NationFilterString implements Predicate<DBNation> {
    private final String filter;
    private final Guild guild;

    public NationFilterString(String filter, Guild guild) {
        this.filter =filter;
        this.guild = guild;
    }

    public String getFilter() {
        return filter;
    }

    @Override
    public boolean test(DBNation nation) {
        String localFilter = "#nation_id=" + nation.getNation_id() + "," + filter;
        Set<DBNation> nations = DiscordUtil.parseNations(guild, localFilter);
        return nations != null && nations.contains(nation);
    }
}
