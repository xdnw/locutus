package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.wiki.CommandWikiPages;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiCommandsPage extends BotWikiGen {
    public WikiCommandsPage(CommandManager2 manager) {
        super(manager, "commands");
    }

    @Override
    public String getDescription() {
        return "List and description of all commands.";
    }

    @Override
    public String generateMarkdown() {
        return CommandWikiPages.printCommands(getManager().getCommands(), getManager().getStore(), getManager().getPermisser());
    }
}
