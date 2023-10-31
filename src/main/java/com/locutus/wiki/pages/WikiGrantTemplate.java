package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.grant.TemplateTypes;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;

import java.util.Arrays;

public class WikiGrantTemplate extends BotWikiGen {
    public WikiGrantTemplate(CommandManager2 manager) {
        super(manager, "grant_templates");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        Create grant templates for specified changes to a nation.
                        Set which nations can receive a grant for a template, using any nation filter
                        Default restrictions to prevent repeated or unnecessary grants
                        Configurable roles needed to grant a template to others, or themselves
                        Absolute and time based global and per template grant allowances.
                        Configurable grant expiry and tax account""",
                "# Template Types",
                MarkupUtil.list(TemplateTypes.values),
                "# Creating a template",
                // listing
                // deleting
                // info
                // disable

                "# Requirements"
                //Default settings
                // default requirements
                // Specific requirements

                // Creating a template

                // Add @Arg

                // Template permissions
        );
    }
}
