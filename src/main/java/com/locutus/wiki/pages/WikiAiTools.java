package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;

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
