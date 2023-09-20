package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.apiv1.enums.EscrowMode;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;

public class WikiEscrowPage extends BotWikiGen {
    public WikiEscrowPage(CommandManager2 manager) {
        super(manager, "escrow");
    }

    @Override
    public String generateMarkdown() {
        return build(
        """
                Escrowed funds are not transferred in-game, but are added to a nation's escrow account.
                
                When escrowing via a transfer command, this can deduct from member balance based on the note used e.g. `#deposit`.
                
                Escrowing does not deduct from the alliance/guild's offshore when no funds are sent in-game.
                
                Overview:
                - Escrowed funds can be withdrawn by the receiver when their blockade ends
                - Escrow can be set to expire
                - Bulk set, add, reset or view escrow for multiple nations
                - Modify escrow based on deposits, cities, stockpile, or current military units
                """,
                "# Viewing escrow",
                commandMarkdownSpoiler(CM.deposits.check.cmd),
                commandMarkdownSpoiler(CM.escrow.view_sheet.cmd),
                commandMarkdownSpoiler(CM.deposits.sheet.cmd),
                "# Using escrow with transfer commands",
                "Control whether a transfer is escrowed when",
                "## Escrow modes",
                "- " + StringMan.join(EscrowMode.values(), "\n- "),
                "## Commands",
                "Use the `escrowMode` argument for the transfer commands, e.g.",
                CM.transfer.self.cmd.create("food=10", null, null, null, null, null, null, null, null, null, null, EscrowMode.WHEN_BLOCKADED.name(), null, null).toString(),
                "### Slash commands:",
                commandMarkdownSpoiler(CM.transfer.bulk.cmd),
                commandMarkdownSpoiler(CM.transfer.resources.cmd),
                commandMarkdownSpoiler(CM.transfer.raws.cmd),
                commandMarkdownSpoiler(CM.transfer.self.cmd),
                commandMarkdownSpoiler(CM.transfer.warchest.cmd),
                "### Legacy commands:",
                "Add an argument such as `escrow:WHEN_BLOCKADED` to the legacy `!` commands",
                "# Withdraw escrow",
                commandMarkdownSpoiler(CM.escrow.withdraw.cmd),
                "# Add escrow",
                """
                Add amounts to nation's existing escrow balance
                - Use negative to remove escrow amounts
                - See the arguments for handling cities/stockpile/military units""",
                commandMarkdownSpoiler(CM.escrow.add.cmd),
                "## Set escrow",
                """
                Overwrite existing and set nations escrow balance
                - See the arguments for handling cities/stockpile/military units""",
                commandMarkdownSpoiler(CM.escrow.set.cmd),
                "# Reset escrow",
                CM.deposits.reset.cmd.create("", "true", "true", "true", "true", "false", null).toString(),
                "# Add or set escrow using a sheet",
                commandMarkdownSpoiler(CM.escrow.set_sheet.cmd),
                "# Escrow alerts",
                "Setup blockade alerts: " + linkPage("blockade_tools"),
                CM.role.setAlias.cmd.create(Roles.ESCROW_GOV_ALERT.name(), "@escrowGovRole", null, null).toString()
        );
    }
}
