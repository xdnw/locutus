package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;

public class WikiEmbassyPage extends BotWikiGen {
    public WikiEmbassyPage(CommandManager2 manager) {
        super(manager, "embassies");
    }

    @Override
    public String generateMarkdown() {
        return build(
                "# Creating embassy category",
                GuildKey.EMBASSY_CATEGORY.help(),
                commandMarkdownSpoiler(CM.embassy.cmd),
                "# Set the rank required for opening embassies:",
                commandMarkdownSpoiler(CM.settings_role.AUTOROLE_ALLIANCE_RANK.cmd),
                "# Open an embassy",
                "Ensure you are registered to the bot:",
                CM.register.cmd.toSlashCommand(true),
                "To open an embassy for yourself:",
                CM.embassy.cmd.toSlashCommand(true),
                "Or for another player:",
                CM.embassy.cmd.nation("@Borg").toSlashCommand(true)
        );
    }
}
