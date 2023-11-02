package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.entities.grant.BuildTemplate;
import link.locutus.discord.db.entities.grant.CityTemplate;
import link.locutus.discord.db.entities.grant.InfraTemplate;
import link.locutus.discord.db.entities.grant.LandTemplate;
import link.locutus.discord.db.entities.grant.ProjectTemplate;
import link.locutus.discord.db.entities.grant.RawsTemplate;
import link.locutus.discord.db.entities.grant.TemplateTypes;
import link.locutus.discord.db.entities.grant.WarchestTemplate;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.offshore.Grant;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WikiGrantTemplate extends BotWikiGen {
    public WikiGrantTemplate(CommandManager2 manager) {
        super(manager, "grant_templates");
    }

    private String reqSpoilter(String title, List<Grant.Requirement> reqs) {
        return MarkupUtil.spoiler(title, MarkupUtil.markdownToHTML("- " +
                reqs.stream().map(f -> {
                    String msg = f.getMessage();
                    if (f.canOverride()) {
                        msg = "**[Optional]** " + msg;
                    }
                    return msg;
                }).collect(Collectors.joining("\n- ")))) +
                "\n\n";
    }

    @Override
    public String generateMarkdown() {
        StringBuilder requirements = new StringBuilder();
        requirements.append(reqSpoilter("Default Requirements",
                AGrantTemplate.getRequirements(null, null, null)));
        for (TemplateTypes type : TemplateTypes.values) {
            requirements.append(reqSpoilter(type.name() + " Requirements",
                    type.getRequirements()));
        }

        return build(
        """
                - Create grant templates for specified changes to a nation.
                - Set which nations can receive a grant for a template, using any nation filter
                - Default restrictions to prevent repeated or unnecessary grants
                - Configurable roles needed to grant a template to others, or themselves
                - Absolute and time based global and per template grant allowances.
                - Configurable grant expiry and tax account""",
                "# Template Types",
                "Note: warchest grants not implemented yet",
                MarkupUtil.list((Object[]) TemplateTypes.values),
                "# Creating a template",
                """
                Created templates are disabled by default
                Use nation filters to specify who can receive a template:
                - <https://github.com/xdnw/locutus/wiki/nation_placeholders>
                
                Specify a role to choose who can grant this template
                """,
                commandMarkdownSpoiler(CM.grant_template.create.city.cmd),
                commandMarkdownSpoiler(CM.grant_template.create.build.cmd),
                commandMarkdownSpoiler(CM.grant_template.create.infra.cmd),
                commandMarkdownSpoiler(CM.grant_template.create.land.cmd),
                commandMarkdownSpoiler(CM.grant_template.create.project.cmd),
                commandMarkdownSpoiler(CM.grant_template.create.raws.cmd),
                commandMarkdownSpoiler(CM.grant_template.create.warchest.cmd),
                "# Enabling or disabling a template",
                "Templates are disabled by default",
                commandMarkdownSpoiler(CM.grant_template.enable.cmd),
                commandMarkdownSpoiler(CM.grant_template.disable.cmd),
                "# Listing your template",
                commandMarkdownSpoiler(CM.grant_template.list.cmd),
                "# Viewing, copying and editing a template",
                commandMarkdownSpoiler(CM.grant_template.info.cmd),
                "## Copying or Editing a template",
                """
                Use the `show_command: True` argument to get the creation command\n
                Modify the command to edit, use a new template name to create a copy.
                """,
                CM.grant_template.info.cmd.create("YOUR_TEMPLATE", null, null, "True").toString(),
                "# Deleting a template",
                commandMarkdownSpoiler(CM.grant_template.delete.cmd),
                "# Requirements",
                """
                The following is a list of requirement messages for each grant type.
                - Default requirements apply to all grant templates
                - `[Optional]` Requirements act as warnings, and can be sent after confirmation""",
                requirements.toString()
        );
    }
}
