package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;

public class WikiEmbassyPage extends WikiGen {
    public WikiEmbassyPage(CommandManager2 manager) {
        super(manager, "embassies");
    }

    @Override
    public String generateMarkdown() {
        return build(
                "# Creating embassy category",
                GuildKey.EMBASSY_CATEGORY.help(),
                commandMarkdown(CM.embassy.cmd),
                "# Set the rank required for opening embassies:",
                GuildKey.AUTOROLE_ALLIANCE_RANK.help(),
                commandMarkdown(CM.settings_role.AUTOROLE_ALLIANCE_RANK.cmd),
                "# Open an embassy",
                "Ensure you are registered to the bot:",
                commandMarkdownSpoiler(CM.register.cmd),
                "To open an embassy for yourself:",
                CM.embassy.cmd.toSlashCommand(true),
                "Or for another player:",
                CM.embassy.cmd.create("@Borg").toSlashCommand(true)
        );
    }
}
