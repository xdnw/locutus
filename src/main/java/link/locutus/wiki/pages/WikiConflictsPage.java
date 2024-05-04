package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;

public class WikiConflictsPage extends BotWikiGen {
    public WikiConflictsPage(CommandManager2 manager) {
        super(manager, "conflict_webpage");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                Live at: <https://wars.locutus.link/>
                
                
                Browse featured Alliance Vs Alliance conflicts and statistics
                For fine grained war stats, please use other bot commands
                """,
                "# Listing conflicts",
                commandMarkdownSpoiler(CM.conflict.list.cmd),
                "# Viewing conflict attributes on discord",
                "TODO",
                "# Site Administration",
                "!! ONLY AVAILABLE TO SITE MODERATORS !!",
                "Note: Conflicts created within the bot are NOT automatically pushed to the site, see below for instructions",
                "## Importing conflicts",
                "TODO: Instructions for wiki/ctowned importing",
                commandMarkdownSpoiler(CM.conflict.clone.cmd),
                "## Manually Creating/Deleting a conflict",
                "TODO: Instructions for manually creating/deleting a conflict",
                commandMarkdownSpoiler(CM.conflict.create.cmd),
                commandMarkdownSpoiler(CM.conflict.delete.cmd),
                "## Configuring a conflict",
                "### Start/End date",
                commandMarkdownSpoiler(CM.conflict.start.cmd),
                commandMarkdownSpoiler(CM.conflict.end.cmd),
                "### Participants",
                "TODO participant START/END",
                commandMarkdownSpoiler(CM.conflict.alliance.add.cmd),
                commandMarkdownSpoiler(CM.conflict.alliance.remove.cmd),
                "### Attributes",
                commandMarkdownSpoiler(CM.conflict.rename.cmd),
                "TODO: Wiki link",
                "## Pushing stats to webpage",
                "This does NOT create any new conflicts within the bot, only updating the site with the conflicts that already exist",
                "Note: To recalculate conflict stats before updating the site, set `reloadWars: True`",
                "If `includeGraphs` is false, the graph pages will not be updated",
                "### Updating ALL conflicts",
                CM.conflict.sync.cmd.create("*", "true", null).toString(),
                "### Updating a specific conflict",
                "The conflict ID is in the conflict URL",
                CM.conflict.sync.cmd.create("1234", "true", null).toString()
        );
    }
}
