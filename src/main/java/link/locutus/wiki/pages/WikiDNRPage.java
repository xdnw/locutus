package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;

public class WikiDNRPage extends BotWikiGen {
    public WikiDNRPage(CommandManager2 manager) {
        super(manager, "do_not_raid");
    }

    @Override
    public String generateMarkdown() {
        return build(
            "Mark an alliance's members or applicants as Do Not Raid (DNR). " +
                    "Ping `" + Roles.FOREIGN_AFFAIRS.name() + "` for violations." +
                    "Prevent DNR nations appearing in war and raid find commands, as well as target alerts.\n" +
                    "Set the DNR by alliance rank or activity",
                "# Violation Alerts",
                "## Member violations",
                "Set the `" + Roles.FOREIGN_AFFAIRS.name() + "` role to be pinged for violations",
                CM.role.setAlias.cmd.create(Roles.FOREIGN_AFFAIRS.name(), "@foreign_affairs", null, null).toString(),
                "Members will be pinged in offensive war channel and sent an ingame mail if they violate the dnr",
                "See: " + this.linkPage("war_alerts"),
                "# Ignore another alliance's violations",
                Coalition.IGNORE_FA.getDescription(),
                CM.coalition.create.cmd.create("AA:Camelot", Coalition.IGNORE_FA.name()).toString(),
                "# Ping milcom for an alliances violations",
                Coalition.COUNTER.getDescription(),
                CM.coalition.create.cmd.create("AA:Guardian", Coalition.COUNTER.name()).toString(),
                "# Instruct FA to attempt peace",
                Coalition.FA_FIRST.getDescription(),
                CM.coalition.create.cmd.create("AA:Arrgh", Coalition.FA_FIRST.name()).toString(),
                "# Set DNR to top alliances",
                commandMarkdownSpoiler(CM.settings_foreign_affairs.DO_NOT_RAID_TOP_X.cmd),
                "# Check the DNR",
                CM.coalition.list.cmd.create("dnr", null, null).toString(),
                commandMarkdownSpoiler(CM.war.dnr.cmd),
                "# DNR an alliance and its applicants",
                CM.coalition.create.cmd.create("AA:Rose", Coalition.DNR.name()).toString(),
                "# Remove an alliance from the DNR coalition",
                CM.coalition.remove.cmd.create("AA:Eclipse", Coalition.DNR.name()).toString(),
                "If they are not added, but are DNR by `" + GuildKey.DO_NOT_RAID_TOP_X.name() + "`:",
                CM.coalition.create.cmd.create("AA:Rose", Coalition.CAN_RAID.name()).toString(),
                "Exclude inactives (1w):",
                CM.coalition.create.cmd.create("AA:Rose", Coalition.CAN_RAID_INACTIVE.name()).toString(),
                "Note: Adding to `" + Coalition.ENEMIES.name() + "` will also exclude them from the DNR",
                "# DNR only members",
                "Use the coalition `" + Coalition.DNR_MEMBER.name() + "` to only add members to the DNR",
                CM.coalition.create.cmd.create("AA:The Knights Radiant", Coalition.DNR_MEMBER.name()).toString(),
                "# DNR only inactive",
                "Add a time difference to the coalition name, like so: `DNR_1w` or `DNR_MEMBER_30d`",
                CM.coalition.create.cmd.create("AA:Aurora", "DNR_1w").toString(),
                "# See also",
                "You can also add DNR information to the war declaration message in-game on your alliance management page"
        );
    }
}
