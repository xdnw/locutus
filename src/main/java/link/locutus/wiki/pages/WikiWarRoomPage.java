package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.util.MarkupUtil;

public class WikiWarRoomPage extends BotWikiGen {
    public WikiWarRoomPage(CommandManager2 manager) {
        super(manager, "war_rooms");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        - War channels are for coordinating wars against a target or attacking enemy. 
                        - One room is generated automatically by the bot per enemy (not per war).
                        - Sort into categories based on enemy city count (`warcat-c1-10`)
                        - Create rooms manually for a counter, or from a blitz sheet 
                        - Messages mentioning the bot are shared with war rooms in allies' servers.
                        - War rooms close automatically when the war ends""",
                "# Known limitations",
                "War rooms are only for registered alliances",
                "Users must be registered with the bot: " + CM.register.cmd.create("").toString(),
                "Guilds are limited to 500 channels",
                "War rooms are only created if both attacker and defender are active members",
                "# Enabling war rooms",
                "Please ensure the can manage channels and create categories. ",
                "If you would like to restrict bot permissions, you may manually create the warroom categories, and give the bot permission to manage only those categories.",
                CM.settings_war_room.ENABLE_WAR_ROOMS.cmd.create("true").toString(),
                "# Using a war server",
                "Have war rooms in another server you are admin in (e.g. a milcom or coalition server)",
                "Multiple servers can share the same war server",
                "1. Disable war rooms in your main server: " + CM.settings_war_room.ENABLE_WAR_ROOMS.cmd.create("false").toString(),
                "2. Copy the guild id of the war server (" + MarkupUtil.markdownUrl("How To Obtain Guild Id", "https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID-") + ")",
                "3. In your main server, set the war server:",
                commandMarkdownSpoiler(CM.settings_war_room.WAR_SERVER.cmd),
                "4. Enable war rooms in the war server: " + CM.settings_war_room.ENABLE_WAR_ROOMS.cmd.create("true").toString(),
                "Set the `" + Coalition.ALLIES.name() + "` coalition in your war server: " + linkPage("coalitions"),
                "# Creating a war room",
                "It is recommended to create a test war room to ensure it is setup properly",
                commandMarkdownSpoiler(CM.war.room.create.cmd),
                "e.g.",
                CM.war.room.create.cmd.create("Borg", "@User", null, null, null, null, null, "True", null, "True").toString(),
                "See: " + linkPage("counters") + " for finding, or auto assigning counters",
                "# Category sorting",
                "All categories must start with `warcat` and then optionally have a city range (e.g. `warcat-c1-10`)",
                "Overlapping ranges are not supported",
                "Categories are assigned based on *enemy* city count",
                CM.war.room.sort.cmd.toString(),
                "To manually set the category:",
                commandMarkdownSpoiler(CM.war.room.setCategory.cmd),
                "# Generating from blitz sheet",
                "You must have a blitz sheet with columns `nation` (the target) and the attackers `att1`, `att2`, `att3`",
                "See: " + linkPage("blitzes"),
                commandMarkdownSpoiler(CM.war.room.from_sheet.cmd),
                "# War room pins",
                "War rooms have an updating pin of the current war info",
                "Use the war pin command to update the current one",
                commandMarkdownSpoiler(CM.war.room.pin.cmd),
                "Or use the war info command",
                commandMarkdownSpoiler(CM.war.info.cmd),
                "# Manually closing war rooms",
                "War rooms are automatically closed if all attackers, or the enemy is inactive, or if those wars are concluded",
                "Manually close a rooom by deleting it, or using the command",
                commandMarkdownSpoiler(CM.channel.close.current.cmd),
                "Note: The channel close command can only close war rooms and interviews",
                "# Creating a sheet of war rooms",
                CM.war.counter.sheet.cmd.create(null, null, null, null, "True").toString(),
                commandMarkdownSpoiler(CM.war.counter.sheet.cmd),
                "# Force update/delete war rooms",
                commandMarkdownSpoiler(CM.admin.sync.warrooms.cmd),
                commandMarkdownSpoiler(CM.war.room.delete_planning.cmd),
                commandMarkdownSpoiler(CM.war.room.delete_for_enemies.cmd)
        );
    }
}
