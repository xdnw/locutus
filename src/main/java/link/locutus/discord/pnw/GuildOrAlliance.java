package link.locutus.discord.pnw;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MarkupUtil;

import java.util.Map;

public interface GuildOrAlliance extends NationOrAllianceOrGuild {
}
