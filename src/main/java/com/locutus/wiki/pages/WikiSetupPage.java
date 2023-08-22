package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.util.MarkupUtil;
import net.dv8tion.jda.api.utils.MarkdownUtil;

public class WikiSetupPage extends WikiGen {
    public WikiSetupPage(ValueStore store) {
        super(store, "Initial Setup");
    }

    @Override
    public String getDescription() {
        return """
                Introduction
                Inviting the bot to your discord server
                Support Server
                Registering your nation
                Creating and registering roles
                Registering your alliance""";
    }

    @Override
    public String generateMarkdown() {
        return build(
                "# Introduction",
                """
                Locutus is a bot on the discord server which assists with war calculations and alliance affairs for the game Politics and War. To get started, add the bot to your server.""\",
                                    
                Support Server: [<kbd> <br> Discord Support Server <br> </kbd>][Link]
                                
                [Link]: https://discord.gg/cUuskPDrB7 'Discord Support Server'
                                    
                Created by Borg (nation: <https://politicsandwar.com/nation/id=189573> discord: `xdnw` )""",
                "# Inviting the bot to your discord server",
                """
                Locutus (Main)
                https://discord.com/api/oauth2/authorize?client_id=672237266940198960&permissions=395606879321&scope=bot
                """,
                "# Registering your nation",
                """
                Use the register command to link your in-game nation to your discord.
                This allows you to use nation related commands.""",
                MarkupUtil.spoiler(CM.register.cmd.toSlashCommand(true), commandMarkdownSpoiler(CM.register.cmd)),
                "Example:",
                CM.register.cmd.create("https://politicsandwar.com/nation/id=189573").toSlashCommand(true),
                "You can verify by using:",
                CM.me.cmd.toSlashCommand(true),
                "# Creating and registering roles",
                """
                        """,
                "# Registering your alliance",
                """
                        """,
                "# Setting your API Key",
                """
                        """
        );
    }
}
