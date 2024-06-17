package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;

public class WikiBlockadePage extends BotWikiGen {
    public WikiBlockadePage(CommandManager2 manager) {
        super(manager, "blockade_tools");
    }

    @Override
    public String generateMarkdown() {
        return build(
                "Post a card to a channel, and optionally ping members or gov when a member is blockaded or unblockaded",
                "# Set the blockade alert channels",
                commandMarkdownSpoiler(CM.settings_war_alerts.BLOCKADED_ALERTS.cmd),
                commandMarkdownSpoiler(CM.settings_war_alerts.UNBLOCKADED_ALERTS.cmd),
                "# Blockade Roles",
                "Link the blockade alert roles to a discord role (e.g. your member role)",
                Roles.UNBLOCKADED_ALERT.getDesc() + ": " +
                CM.role.setAlias.cmd.locutusRole(Roles.UNBLOCKADED_ALERT.name()).discordRole("").toString(),
                Roles.BLOCKADED_ALERT.getDesc() + ": " +
                CM.role.setAlias.cmd.locutusRole(Roles.BLOCKADED_ALERT.name()).discordRole("").toString(),
                Roles.UNBLOCKADED_GOV_ROLE_ALERT.getDesc() + ": " +
                CM.role.setAlias.cmd.locutusRole(Roles.UNBLOCKADED_GOV_ROLE_ALERT.name()).discordRole("").toString(),
                Roles.ESCROW_GOV_ALERT.getDesc() + ": " +
                CM.role.setAlias.cmd.locutusRole(Roles.ESCROW_GOV_ALERT.name()).discordRole("").toString(),
                "# Member unblockade requests",
                commandMarkdownSpoiler(CM.settings_war_alerts.UNBLOCKADE_REQUESTS.cmd),
                commandMarkdownSpoiler(CM.war.blockade.request.cmd),
                commandMarkdownSpoiler(CM.war.blockade.cancelRequest.cmd),
                commandMarkdownSpoiler(CM.embed.template.unblockade_requests.cmd),
                "# Target finder enemies blockading allies",
                commandMarkdownSpoiler(CM.war.find.unblockade.cmd),
                "# See also",
                MarkupUtil.markdownUrl("Locutus/Wiki/Escrow", "../wiki/escrow")
        );
    }
}
