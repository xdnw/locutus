package com.locutus.wiki.pages;

import com.locutus.wiki.CommandWikiPages;
import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

import java.lang.reflect.InvocationTargetException;

public class WikiCommandsPage extends WikiGen {
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
