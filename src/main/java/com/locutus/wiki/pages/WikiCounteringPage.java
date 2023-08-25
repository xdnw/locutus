package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiCounteringPage extends WikiGen {
    public WikiCounteringPage(CommandManager2 manager) {
        super(manager, "countering");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       TODO: Locutus countering guide
        https://docs.google.com/document/d/1eJfgNRk6L72G6N3MT01xjfn0CzQtYibwnTg9ARFknRg/edit

       /spy counter

       /war counter auto

       /war counter nation

       /war counter sheet

       /war counter url
        */
    }
}
