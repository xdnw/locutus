package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.user.Roles;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class WikiDepositsPage extends BotWikiGen {
    public WikiDepositsPage(CommandManager2 manager) {
        super(manager, "deposits");
    }

    @Override
    public String generateMarkdown() {
        return build(
        """
                Member balances are determined by what they deposit into the bank, as well as funds they receive from the alliance.
                Offshores/Training alliances etc. 
                """,
                "# Viewing balances",
                """
                The deposits check command can be used to view the account balance of nations, tax brackets as well as the alliance or guild offshore account
                - Nation e.g. `Borg` - Net funds a nation has deposited/received with the alliance bank
                - Tax bracket e.g. `tax_id=1234` - Net funds a from tax records
                - Alliance e.g. `AA:Rose` - Net funds the alliance has deposited/received with the offshore
                - Guild ID e.g. `647252780817448972` - Net funds the `#guild` has deposited/received with the offshore
                
                Nation and Tax Bracket deposits can be adjusted by econ or admin, however alliance/guild accounts can only be set in the offshore server""",
                // /deposits check
                commandMarkdownSpoiler(CM.deposits.check.cmd),
                // /deposits sheet
                commandMarkdownSpoiler(CM.deposits.sheet.cmd),
                "See also:" + linkPage("bank_flows"),
                // ## Changing display mode
                "## Changing how balances are displayed",
                "Use the argument in the deposits command, or set the following",
                commandMarkdownSpoiler(CM.settings_bank_info.DISPLAY_CONDENSED_DEPOSITS.cmd),
                commandMarkdownSpoiler(CM.settings_bank_info.DISPLAY_ITEMIZED_DEPOSITS.cmd),
                "## Listing records",
                commandMarkdownSpoiler(CM.bank.records.cmd),
                commandMarkdownSpoiler(CM.tax.deposits.cmd),
                commandMarkdownSpoiler(CM.tax.records.cmd),
                "# Transfer Notes",
                "Notes can be used in the transfer commands, as well as when sending in-game",
                "Multiple notes can be used at a time",
                "Notes with values go in the form `#expire=60d`",
                "## Primary Notes",
                "- " + Arrays.stream(DepositType.values()).filter(f -> f.getParent() == null).map(f -> "`#" + f.toString().toLowerCase(Locale.ROOT) + "`: " + f.getDescription()).collect(Collectors.joining("\n- ")),
                "## Modifier Notes",
                "These notes are used in addition to a primary note, and usually have a value",
                "- " + Arrays.stream(DepositType.values()).filter(f -> f.getParent() != null).map(f -> "`#" + f.getParent().toString().toLowerCase(Locale.ROOT) +" #" + f.toString().toLowerCase(Locale.ROOT) + "`: " + f.getDescription()).collect(Collectors.joining("\n- ")),
                // ensure expire is listed and explained
                "## Tracking alliances",
                "__This applies for guilds with a registed alliance__",
                commandMarkdownSpoiler(CM.settings_default.registerAlliance.cmd),
                "By default, any alliance alliance registered to the guild is tracked for member deposits.",
                "Removing an alliance from being tracked will remove all those transfers from a member's balance",
                "The following coalitions are also tracked:\n" +
                "- " + Coalition.TRACK_DEPOSITS.name() + "\n" +
                "- " + Coalition.OFFSHORE.name(),
                "See: " + linkPage("coalitions"),
                "# Tax accounts & Including taxes in deposits",
                "See: " + linkPage("tax_automation"),
                "# Manually adding/subtracing balances",
                "Use negative amounts to subtract",
                "Nation or tax brackets can be adjusted for a server",
                "Offshore balances for alliances or guilds are managed in the offshore's server",
                commandMarkdownSpoiler(CM.deposits.add.cmd),
                "## Bulk add balance",
                "Specify multiple nations for the command above, or use add balance sheet",
                commandMarkdownSpoiler(CM.deposits.addSheet.cmd),
                "# Shifting balance notes",
                "For example, moving `#loan` to `#deposit`",
                commandMarkdownSpoiler(CM.deposits.shift.cmd),
                "# Converting negative deposits",
                commandMarkdownSpoiler(CM.deposits.convertNegative.cmd),
                "# Resetting deposits",
                "Set the arguments for the categories you do not wish to reset to false",
                CM.deposits.reset.cmd.create("", "true", "true", "true", "true", "true", null).toString(),
                commandMarkdownSpoiler(CM.deposits.reset.cmd),
                "# Allow members to withdraw or offshore",
                "See: " + linkPage("banking"),
                "# Resource conversion",
                "Allow members to withdraw any resource as long as their balance has a positive market value",
                commandMarkdownSpoiler(CM.settings_bank_access.RESOURCE_CONVERSION.cmd),
                "# Alerts",
                commandMarkdownSpoiler(CM.settings_bank_info.ADDBALANCE_ALERT_CHANNEL.cmd),
                CM.role.setAlias.cmd.create(Roles.ECON_WITHDRAW_ALERTS.name(), "@Econ Withdraw Alerts", null, null).toString(),
                commandMarkdownSpoiler(CM.settings_bank_info.WITHDRAW_ALERT_CHANNEL.cmd),
                CM.role.setAlias.cmd.create(Roles.ECON_DEPOSIT_ALERTS.name(), "@Econ Deposit Alerts", null, null).toString(),
                commandMarkdownSpoiler(CM.settings_bank_info.DEPOSIT_ALERT_CHANNEL.cmd),
                commandMarkdownSpoiler(CM.settings_bank_info.BANK_ALERT_CHANNEL.cmd),
                "# Transfer command arguments",
                commandMarkdown(CM.transfer.resources.cmd)
        );
    }
}
