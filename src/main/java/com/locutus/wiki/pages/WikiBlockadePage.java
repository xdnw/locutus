package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;

public class WikiBlockadePage extends WikiGen {
    public WikiBlockadePage(CommandManager2 manager) {
        super(manager, "blockade tools");
    }

    @Override
    public String generateMarkdown() {
        return build(
        "# Overview",
                "Post a card to a channel, and optionally ping members or gov when a member is blockaded or unblockaded",
                "# Blockade Roles",
                "Link the blockade alert roles to a discord role (e.g. your member role)",
                CM.role.setAlias.cmd.create(Roles.UNBLOCKADED_ALERT.name(), "", null, null).toString(),
                CM.role.setAlias.cmd.create(Roles.BLOCKADED_ALERT.name(), "", null, null).toString(),
                CM.role.setAlias.cmd.create(Roles.UNBLOCKADED_GOV_ROLE_ALERT.name(), "", null, null).toString(),
                "## Set the blockade alert channels",
                commandMarkdown(CM.settings_war_alerts.BLOCKADED_ALERTS.cmd),
                commandMarkdown(CM.settings_war_alerts.UNBLOCKADED_ALERTS.cmd),
                "# Member unblockade requests",
                commandMarkdown(CM.settings_war_alerts.UNBLOCKADE_REQUESTS.cmd),
                commandMarkdown(CM.war.blockade.request.cmd),
                commandMarkdown(CM.war.blockade.cancelRequest.cmd),
                commandMarkdown(CM.embed.template.unblockade_requests.cmd),
                "# Target finder enemies blockading allies",
                commandMarkdown(CM.war.find.unblockade.cmd),
                "# See also",
                MarkupUtil.markdownUrl("Locutus/Wiki/Escrow", "../escrow")
        );
    }
}
