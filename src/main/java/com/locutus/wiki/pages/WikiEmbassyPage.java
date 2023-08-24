package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiEmbassyPage extends WikiGen {
    public WikiEmbassyPage(CommandManager2 manager) {
        super(manager, "embassies");
    }

    @Override
    public String generateMarkdown() {
       /*
       Locutus can automatically create embassy channels for alliances (upon using the !embassy command). These channels are created in a specified category. To enable this:
	    !KeyStore EMBASSY_CATEGORY <embassy-category>

	    Also whatever the embassy rank required is

	    TODO see the auto masking page on how to setup alliance roles
        */
    }
}
