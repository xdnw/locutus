package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.wiki.CommandWikiPages;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiAlliancePlaceholdersPage extends BotWikiGen {
    public WikiAlliancePlaceholdersPage(CommandManager2 manager) {
        super(manager, "alliance_placeholders");
    }

    @Override
    public String getDescription() {
        return "List and description of all alliance placeholders.";
    }

    @Override
    public String generateMarkdown() {
        return CommandWikiPages.printPlaceholders(getManager().getAlliancePlaceholders(), getManager().getStore());
    }
}
