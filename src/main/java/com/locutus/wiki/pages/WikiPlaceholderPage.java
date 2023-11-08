package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import com.locutus.wiki.CommandWikiPages;
import com.locutus.wiki.WikiGenHandler;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;

import java.util.Locale;

public class WikiPlaceholderPage extends BotWikiGen {
    private final PlaceholdersMap placeholderMap;
    private final Class type;
    private final Placeholders placeholders;

    public WikiPlaceholderPage(CommandManager2 manager, PlaceholdersMap placeholderMap, Class type) {
        super(manager, (type.getSimpleName().replaceAll("DB", "") + "_placeholders").toLowerCase(Locale.ROOT));
        System.out.println(type.getSimpleName().replaceAll("DB", ""));
        this.placeholderMap = placeholderMap;
        this.type = type;
        this.placeholders = this.placeholderMap.get(type);
    }

    @Override
    public String generateMarkdown() {
        return CommandWikiPages.printPlaceholders(placeholders, getManager().getStore());
    }
}
