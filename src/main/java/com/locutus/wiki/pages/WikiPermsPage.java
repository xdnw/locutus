package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import com.locutus.wiki.CommandWikiPages;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;

public class WikiPermsPage extends BotWikiGen {
    public WikiPermsPage(CommandManager2 manager) {
        super(manager, "Permissions");
    }

    @Override
    public String getDescription() {
        return """
                Some commands and placeholders may require certain permissions to use.
                This page lists the types of permissions used. 
                See each respective command or placeholder page for more details. """;
    }

    @Override
    public String generateMarkdown() {
        return getDescription() + "\n\n---\n\n" + CommandWikiPages.printPermissions(getManager().getPermisser());

    }
}
