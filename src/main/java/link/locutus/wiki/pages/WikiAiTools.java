package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiAiTools extends BotWikiGen {
    public WikiAiTools(CommandManager2 manager) {
        super(manager, "ai_tools");
    }

    @Override
    public String generateMarkdown() {
        return build(
"# Locutus Chatbot," +
        "Work in progress",
        "# Emojify your discord",
            commandMarkdownSpoiler(CM.channel.rename.bulk.cmd)
        );
    }
}
