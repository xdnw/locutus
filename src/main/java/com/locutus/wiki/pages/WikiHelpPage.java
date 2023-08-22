package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import com.locutus.wiki.WikiGenHandler;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;

import java.util.List;

public class WikiHelpPage extends WikiGen {
    private final List<WikiGen> pages;

    public WikiHelpPage(ValueStore store, List<WikiGen>pages) {
        super(store, "Overview");
        this.pages = pages;
    }

    @Override
    public String generateMarkdown() {
        return build(
                "# Using the help commands",
                """
                        """,
            "# Overview of this Wiki",
                """
                        """
        );
    }
}
