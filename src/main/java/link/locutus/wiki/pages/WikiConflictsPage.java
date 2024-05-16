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
                "# Listing featured",
                commandMarkdownSpoiler(CM.conflict.list.cmd),
                "# Viewing conflict attributes on discord",
                commandMarkdownSpoiler(CM.conflict.info.cmd),
                "# Creating a personal conflict (non featured)",
                commandMarkdownSpoiler(CM.conflict.create_temp.cmd),
                "# Website Administration",
                "!! ONLY AVAILABLE TO SITE MODERATORS !!",
                "Note: Conflicts created within the bot may NOT be automatically pushed to the site. See the sync command below",
                "Your browser may cache site data. To disable caching in chrome:\n\n" +
                "1. Press `F12` or `Ctrl + Shift + i`. Press `Command + Option+i` if you’re using MacOS.\n" +
                "2. When the console appears, click on the `Network` tab and then tick the `Disable Cache` checkbox",
                "## Importing conflicts / Website sync",
                "Use `*` for arguments accepting `Set<Conflict>` to specify ALL featured conflicts",
                commandMarkdownSpoiler(CM.conflict.sync.wiki_page.cmd),
                commandMarkdownSpoiler(CM.conflict.sync.wiki_all.cmd),
                commandMarkdownSpoiler(CM.conflict.sync.ctowned.cmd),
                commandMarkdownSpoiler(CM.conflict.sync.multiple_sources.cmd),
                commandMarkdownSpoiler(CM.conflict.sync.alliance_names.cmd),
                commandMarkdownSpoiler(CM.conflict.sync.recalculate_tables.cmd),
                commandMarkdownSpoiler(CM.conflict.sync.recalculate_graphs.cmd),
                "## Manually Creating/Deleting a conflict",
                commandMarkdownSpoiler(CM.conflict.create.cmd),
                commandMarkdownSpoiler(CM.conflict.delete.cmd),
                "## Configuring a conflict",
                "### Start/End date",
                "You may need to run " + CM.conflict.sync.website.cmd.create("", "true", "true", "true") + " after changing the start/end date",
                commandMarkdownSpoiler(CM.conflict.edit.start.cmd),
                commandMarkdownSpoiler(CM.conflict.edit.end.cmd),
                "### Add/Remove Alliance from a conflict",
                "You may need to run " + CM.conflict.sync.website.cmd.create("", "true", "true", "true") + " after changing the belligerents",
                commandMarkdownSpoiler(CM.conflict.alliance.add.cmd),
                commandMarkdownSpoiler(CM.conflict.alliance.remove.cmd),
                "#### Individual alliance start/end dates",
                "Use `-1` for the start/end date to match the conflict start/end date",
                "You may need to run " + CM.conflict.sync.website.cmd.create("", "true", "true", "true") + " after changing the start/end date",
                CM.conflict.edit.start.cmd.create("<conflict>", "<time>", "<alliance>").toString(),
                CM.conflict.edit.end.cmd.create("<conflict>", "<time>", "<alliance>").toString(),
                "### Attributes",
                commandMarkdownSpoiler(CM.conflict.edit.rename.cmd),
                commandMarkdownSpoiler(CM.conflict.edit.wiki.cmd),
                commandMarkdownSpoiler(CM.conflict.edit.status.cmd),
                commandMarkdownSpoiler(CM.conflict.edit.casus_belli.cmd),
                commandMarkdownSpoiler(CM.conflict.edit.category.cmd),
                "## Pushing stats to webpage",
                "This does NOT create any new conflicts within the bot, only updating the site with the conflicts that already exist",
                "Note: To recalculate conflict stats before updating the site, set `reloadWars: True` with the command below",
                "If `includeGraphs` is false, the graph pages will not be updated",
                "### Updating ALL conflicts",
                CM.conflict.sync.website.cmd.create("*", "true", null, null).toString(),
                "### Updating a specific conflict",
                "The conflict ID is in the conflict URL",
                "Set `reinitialize_wars` or `reinitialize_graphs` to fully recalculate table and graph data before uploading",
                CM.conflict.sync.website.cmd.create("1234", "true", null, null).toString(),
                "# Filtering Featured Conflicts",
                "Conflicts have an id (in the url), as well as an creator (discord guild)",
                "You add `guilds` or `conflict ids` that are shown on the main webpage when your discord guild is selected on the website",
                commandMarkdownSpoiler(CM.conflict.featured.add_rule.cmd),
                commandMarkdownSpoiler(CM.conflict.featured.remove_rule.cmd),
                commandMarkdownSpoiler(CM.conflict.featured.list_rules.cmd),
                "# Purging unused conflicts from the website",
                "When a conflict is deleted via command, it will be removed from the conflict list page, but remains accessible via direct url",
                "These conflicts as well as user generated conflicts can be purged in bulk",
                commandMarkdownSpoiler(CM.conflict.purge.featured.cmd),
                commandMarkdownSpoiler(CM.conflict.purge.user_generated.cmd)
        );
    }
}