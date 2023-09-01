package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;

public class WikiCustomEmbeds extends WikiGen {
    public WikiCustomEmbeds(CommandManager2 manager) {
        super(manager, "embeds");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        Create or update embeds with custom title, description, and buttons
                        Buttons can run commands, either directly, or open prompts for input from the user
                        Buttons can submit arguments with set values or use placeholders based on the nation using it
                        Run one or more commands in other channels via a button
                        Set the behavior to remove the embed, allow single use, or show the result only to the user""",
                "# Embed templates",
                """
                Create an embed with preset title, description and command buttons.
                See:""",
                commandMarkdownSpoiler(CM.embed.template.guerilla.cmd),
                commandMarkdownSpoiler(CM.embed.template.spy_sheets.cmd),
                commandMarkdownSpoiler(CM.embed.template.ally_enemy_sheets.cmd),
                commandMarkdownSpoiler(CM.embed.template.spy_enemy.cmd),
                commandMarkdownSpoiler(CM.embed.template.unblockade_requests.cmd),
                commandMarkdownSpoiler(CM.embed.template.war_winning.cmd),
                commandMarkdownSpoiler(CM.embed.template.member_econ.cmd),
                commandMarkdownSpoiler(CM.embed.template.war_contested_range.cmd),
                commandMarkdownSpoiler(CM.embed.template.deposits.cmd),
                commandMarkdownSpoiler(CM.embed.template.raid.cmd),
                "# View the commands for a template",
                "To show the commands needed to create or copy a template",
                commandMarkdown(CM.embed.info.cmd),
                "# Create an embed with title and description",
                CM.embed.create.cmd.create("Your Title", "Your Description").toString(),
                "# Update the title and description",
                "Copy the message url for an embed",
                CM.embed.title.cmd.create("", "New Title").toString(),
                CM.embed.description.cmd.create("", "New Description").toString(),
                "# Remove a button from an embed",
                "Copy the message url for an embed, and use the name of the button",
                CM.embed.remove.button.cmd.create("", "button name").toString(),
                "# Add a button to an embed",
                commandMarkdown(CM.embed.add.command.cmd),
                "# Add a modal (prompt button) to an embed",
                commandMarkdown(CM.embed.add.modal.cmd),
                "# Multiple commands in one button
                "Use `\\n` for newlines between commands",
                commandMarkdown(CM.embed.add.raw.cmd)
        );
    }
}
