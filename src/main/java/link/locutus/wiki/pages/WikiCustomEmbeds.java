package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiCustomEmbeds extends BotWikiGen {
    public WikiCustomEmbeds(CommandManager2 manager) {
        super(manager, "embeds");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        - Create or update embeds with custom title, description, and buttons
                        - Buttons can run commands, either directly, or open prompts for input from the user
                        - Buttons can submit arguments with set values or use placeholders based on the nation using it
                        - Run one or more commands in other channels via a button
                        - Set the behavior to remove the embed, allow single use, or show the result only to the user""",
                linkPage("nation_placeholders"),
                "# Embed templates",
                """
                Create an embed with preset title, description and command buttons.
                See:""",
                commandMarkdownSpoiler(CM.embed.template.guerilla.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.spy_sheets.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.ally_enemy_sheets.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.spy_enemy.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.unblockade_requests.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.war_winning.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.member_econ.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.war_contested_range.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.deposits.cmd, true),
                commandMarkdownSpoiler(CM.embed.template.raid.cmd, true),
                "# View the commands for an embed",
                "To show the commands needed to create or copy an embed",
                commandMarkdownSpoiler(CM.embed.info.cmd),
                "# Create an embed with title and description",
                CM.embed.create.cmd.title("Your Title").description("Your Description").toString(),
                "# Update the title and description",
                "Copy the message url for an embed",
                CM.embed.title.cmd.discMessage("").title("New Title").toString(),
                CM.embed.description.cmd.discMessage("").description("New Description").toString(),
                "# Remove a button from an embed",
                "Copy the message url for an embed, and use the name of the button",
                CM.embed.remove.button.cmd.message("").labels("button name").toString(),
                "# Add a button to an embed",
                commandMarkdownSpoiler(CM.embed.add.command.cmd),
                "Example: " + CM.embed.add.command.cmd.message("<url>").label("Blah").behavior(CommandBehavior.EPHEMERAL.name()).command(CM.who.cmd.getPath()).arguments("nationoralliances").toString(),
                "# Add a modal (prompt button) to an embed",
                commandMarkdownSpoiler(CM.embed.add.modal.cmd),
                "Example with default:",
                CM.embed.add.modal.cmd.message("<url>").label("Who").behavior(CommandBehavior.EPHEMERAL.name()).command(CM.who.cmd.getPath()).arguments("nationoralliances").defaults("list: True").toString(),
                "Example with placeholder, and default",
                CM.embed.add.modal.cmd.message("<url>").label("Who Cities").behavior(CommandBehavior.EPHEMERAL.name()).command(CM.who.cmd.getPath()).arguments("num_cities,position").defaults("nationoralliances: Rose,#cities>{num_cities},#position={position=1}").toString(),
                "# Multiple commands in one button",
                "Use `\\n` for newlines between commands",
                commandMarkdownSpoiler(CM.embed.add.raw.cmd)
        );
    }

}
