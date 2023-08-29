package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;

public class WikiBankPage extends WikiGen {
    public WikiBankPage(CommandManager2 manager) {
        super(manager, "banking");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                The bot can enable withdrawals from the local alliance bank, or a bot managed offshore.
                It is recommended to use an offshore as an alliance bank will be looted from wars.
                Enable banking on discord servers (such as for a player corporation) without an alliance by using an offshore.
                
                You can use the public offshore or setup your own.
                Automatic withdrawals for member deposits can be enabled.""",
                "See also: " + MarkupUtil.markdownUrl("Deposits", "../wiki/deposits"),
                "# Prerequisites",
                "Setting up member roles: " + MarkupUtil.markdownUrl("Registering Roles", "../wiki/initial_setup#creating-and-registering-roles"),
                "If you are an alliance:",
                commandMarkdown(CM.settings_default.registerAlliance.cmd),
                "If you want to run your own offshore, or use the local bank:",
                commandMarkdown(CM.settings_default.registerApiKey.cmd),
                "# Using the public offshore",
                """
                    Alliance or corporate accounts will hold the funds that you send to the offshore alliance in-game.
                    
                    Withdrawals will send from the offshore alliance to the receiver and deduct from your offshore account.
                    
                    This bot can track nation deposits with your alliance or corporation, but you are responsible for managing their balances in your guild.
                    If member withdrawals are enabled, they will only be able to withdraw if you have sufficient funds in your offshore account. 
                    
                    The bot owner runs the public offshore. For Locutus that is `Borg`.
                    Use the who command to see the current offshore alliance""",
                    CM.who.cmd.create("Borg", null, null, null, null, null, null, null, null).toString(),
                "Set that alliance as your offshore:",
                commandMarkdown(CM.offshore.add.cmd),
                "**Alternatively** you can use the coalition command in your alliance server to set `" + Coalition.OFFSHORE.name() + "` to the desired offshore alliance, and ensure `" + Coalition.OFFSHORING.name() + "` is empty",
                CM.coalition.create.cmd.create("", Coalition.OFFSHORE.name()).toString(),
                CM.coalition.delete.cmd.create(Coalition.OFFSHORING.name()).toString(),
                "And ask an admin in the offshore alliance to add your alliance to the `" + Coalition.OFFSHORING.name() + "` coalition",
                MarkupUtil.spoiler("Legal/Disclaimer", """
                        Offshoring and bot services are non political, and should be assumed to continue operating regardless of any attacks on Borg.
                                                
                        Borg is not an arbitration body. In-game drama or actions are not a concern if they do not break any game rules.
                                                
                        If there is evidence of breaking game rules, relevant funds will be frozen until it can be resolved by game moderators.
                                                
                        It is assumed whoever owns the discord has full authority to withdraw funds.
                                                
                        Anyone given econ roles on discord can withdraw to whatever limits have been set via the bot.
                                                
                        If the owner has been inactive without notice for over one month (both on discord and in-game), no one can access funds, nor is there a succession plan - funds can be transferred to stakeholders if there is reasonable claim. Reasonable claim can be considered:
                        - Alliance leadership (in-game)
                        - Discord admin perms
                        - Deposits (sheet, bank log, Locutus etc.) (Alliance leader / admin takes precedence over deposits)
                                                
                        If there is a dispute with the above process, it must be resolved by the people disputing it (not Borg)."""),
                "# Offshoring funds",
                "## For my alliance",
                """
                **Option 1:** Send Via Same Alliance
                - Send from your alliance to the offshore alliance. No note is required
                
                **Option 2:** Send Via Other Alliance 
                - Send from any alliance to the offshore alliance with the note `#alliance=1234` 
                (where `1234` is your alliance id)
                
                **Option 3:** Via Trade
                (See below. Same instructions as corporate account)
                """,
                "## For my corporation",
                """
                    **Option 1:** Via Alliance Bank
                    - Send the funds to the offshore alliance with the note `#guild=1234` 
                    (where `1234` is your guild id)
                    [How-To: Obtaining Guild Id](https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID-)
                    
                    **Option 2:** Via Trade 
                    - Create a __private__ trade of either a `$0` ppu sell offer, or a food buy offer __over__ `$100,000` ppu. 
                    - Send the trade with a nation with bank access to an alliance using the offshore, who has provided credentials to the bot. 
                      (i.e. the bot owner `Borg`)
                    - Use the command e.g. `/trade accept` in the discord server you wish to deposit into (i.e. your alliance or corporate server)
                    Set the banker as the receiver (e.g. `Borg`)""",
                    commandMarkdownSpoiler(CM.trade.accept.cmd),
                "# For my nation",
                """
                You can deposit with an alliance or corporation that has enabled withdrawals.
                Alternatively join the Locutus Corporation server and open a ticket to deposit there:
                - <https://discord.gg/cUuskPDrB7>""",
                "# Checking alliance/guild balance",
                """
                        Use the command with either your alliance or guild id.
                        The command must be run in the guild for that account.
                        - [How-To: Obtaining Guild Id](https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID-)""",
                CM.deposits.check.cmd.create("", null, null, null, null, null, null, null, null, null).toString(),
                "# Using the local alliance",
                """
                        It is recommended to use an offshore instead, to avoid loot losses.
                        To enable banking using the local alliance, use your alliance url in:""",
                        CM.offshore.add.cmd.create("", null).toString(),
                "**Alternatively** you can use the coalition command to set both `" + Coalition.OFFSHORE.name() + "` and `" + Coalition.OFFSHORING.name() + "` to your alliance",
                CM.coalition.add.cmd.create("", Coalition.OFFSHORE.name()).toString(),
                CM.coalition.add.cmd.create("", Coalition.OFFSHORING.name()).toString(),
                "And no other values are set:",
                CM.coalition.list.cmd.toString(),
                "# Running your own offshore",
                """
                Go to the discord guild for your offshore, or create a new one if it does not exist.
                Ensure no other alliance is registered for that guild:""",
                CM.settings.delete.cmd.create(GuildKey.ALLIANCE_ID.name()).toString(),
                "### Register the alliance and api key",
                """
                Set the alliance to the alliance for your offshore.""",
                CM.settings_default.registerAlliance.cmd.create("").toString(),
                """
                 Set the api key to someone with bank access in the offshore alliance.
                 - For the api key, enable whitelisted access from the account page: <https://politicsandwar.com/account/>""",
                CM.settings_default.registerApiKey.cmd.create("").toString(),
                "### Set it as an offshore",
                "In the guild for your offshore, use the alliance id of the offshore:",
                CM.offshore.add.cmd.create("", null).toString(),
                "**Alternatively**, you can use the coalition command to set both `" + Coalition.OFFSHORE.name() + "` and `" + Coalition.OFFSHORING.name() + "` to your offshore alliance",
                CM.coalition.add.cmd.create("", Coalition.OFFSHORE.name()).toString(),
                CM.coalition.add.cmd.create("", Coalition.OFFSHORING.name()).toString(),
                "And ensure ONLY past offshores (not connected to any other guild) are set as those coalitions",
                CM.coalition.list.cmd.toString(),
                "### Set the offshore in your alliance or corporation",
                "Go to the guild you have setup for your alliance or corporation, and use the offshore add command with the alliance id of your offshore:",
                CM.offshore.add.cmd.create("", null).toString(),
                "**Alternatively**:\n" +
                "1. In your alliance/corporation server, use the coalition command to set `" + Coalition.OFFSHORE.name() + "` and ensure `" + Coalition.OFFSHORING.name() + "` is not set",
                CM.coalition.add.cmd.create("", Coalition.OFFSHORE.name()).toString() + "\n" +
                CM.coalition.delete.cmd.create(Coalition.OFFSHORING.name()).toString(),
                "2. In your offshore server, add your alliance id (if an alliance) or guild id (if corporation) to the `" + Coalition.OFFSHORING.name() + "` coalition",
                CM.coalition.add.cmd.create("", Coalition.OFFSHORING.name()).toString(),
                "### Check deposits of alliances or guilds",
                "In the offshore server, as above, use the deposits command with the alliance or guild id you wish to check",
                CM.deposits.check.cmd.create("", null, null, null, null, null, null, null, null, null).toString(),
                "### Adjust the balance of any alliance or corporation",
                """
                    Deposits are automatically tracked, but you may need to adjust balances if funds are sent from another source, using the wrong note, or to set initial amounts.
                    Use the alliance or guild id as the account, and then the amounts you wish to add.    
                        """,
                CM.deposits.add.cmd.create("", "", "#deposit", null).toString(),
                "### Creating a new offshore (e.g. during war)",
                "In the offshore server, add the new offshore using the alliance id of the new offshore:",
                CM.offshore.add.cmd.create("", null).toString(),
                """
                    Guilds using the previous offshore should be updated.
                    You can manually add an offshore by going to the alliance or corporation discord which has an account and adding the alliance id of the offshore to the `offshore` coalition.""",
                CM.coalition.add.cmd.create("id of offshore", Coalition.OFFSHORE.name()).toString(),
                "# Removing an offshore",
                "If you wish to stop using an offshore. In the alliance or corporation discord:",
                CM.coalition.remove.cmd.create(null, Coalition.OFFSHORE.name()).toString(),
                CM.coalition.remove.cmd.create(null, Coalition.OFFSHORING.name()).toString(),
                "Note: If a new offshore is created, use offshore add to add it. You should keep deleted alliances for deposit tracking purposes",
                "# Enabling member withdrawals",
                "### Set your member role:",
                CM.role.setAlias.cmd.create(Roles.MEMBER.name(), "@someRole", null, null).toString(),
                "### To allow members to use the offshore command or embed (if enabled)",
                CM.settings_bank_access.MEMBER_CAN_WITHDRAW.cmd.create("true").toString(),
                "### To enable a role to withdraw their own funds.\n(where someRole is the role needed to withdraw your own funds - you can set this to the member role if you’d like)",
                CM.role.setAlias.cmd.create(Roles.ECON_WITHDRAW_SELF.name(), "@someRole", null, null).toString(),
                "### To enable that role to withdraw:",
                commandMarkdown(CM.settings_bank_access.MEMBER_CAN_WITHDRAW.cmd),
                commandMarkdown(CM.settings_bank_access.addResourceChannel.cmd),
                "### Allow members to offshore",
                commandMarkdown(CM.settings_bank_access.MEMBER_CAN_OFFSHORE.cmd),
                commandMarkdown(CM.transfer.offshore.cmd),
                "### Optional Settings:",
                commandMarkdown(CM.settings_bank_access.MEMBER_CAN_WITHDRAW_WARTIME.cmd),
                "- last one, if their withdrawal limit ignores their grants when calculating total deposits\n",
                commandMarkdown(CM.settings_bank_access.WITHDRAW_IGNORES_GRANTS.cmd),
                "# Transfer commands",
                commandMarkdownSpoiler(CM.transfer.self.cmd),
                commandMarkdownSpoiler(CM.transfer.resources.cmd),
                commandMarkdownSpoiler(CM.transfer.bulk.cmd),
                commandMarkdownSpoiler(CM.transfer.raws.cmd),
                commandMarkdownSpoiler(CM.transfer.warchest.cmd),
                "# Transfer limits",
                "Set how much a banker can withdraw by default each interval (default 1 day)",
                commandMarkdown(CM.settings_bank_access.BANKER_WITHDRAW_LIMIT.cmd),
                "Override the default and set how much a nation can withdraw each interval",
                commandMarkdown(CM.bank.limits.setTransferLimit.cmd),
                "Note: It is recommended to set the global `" + GuildKey.BANKER_WITHDRAW_LIMIT.name() + "` to a low value so that users who have no individual limit set do not have unwarranted access to funds\n" +
                "Set the interval the limit applies to (e.g. 1h)",
                commandMarkdown(CM.settings_bank_access.BANKER_WITHDRAW_LIMIT_INTERVAL.cmd),
                "# Offshore browser addon",
                """
                Userscript browser addon to more easily select / offshore the funds that you need.
                                
                1\\. Install TamperMonkey/GreaseMonkey add-in into your browser <https://www.tampermonkey.net/>
                                
                2\\. Click on the addon in your toolbar and select "Create new script"
                                
                ![](https://github.com/xdnw/locutus/blob/master/src/main/resources/img/create_script.png?raw=true)
                                
                More info: <https://hibbard.eu/tampermonkey-tutorial/>
                                
                3\\. Copy the script from <https://gist.github.com/xdnw/59dfa64f2b7c22340647485d384a992b>
                                
                and paste it in the Tampermonkey website and click File and Save.
                                
                How to use the extension
                                
                1.  Click the "Set Warchest" button to set the alliance warchest (i.e. funds that you don't want to offshore)
                                
                ![](https://github.com/xdnw/locutus/blob/master/src/main/resources/img/aawarchest.png?raw=true)
                                
                1.  Enter the alliance to offshore to (and change Nation to Alliance)
                                
                2.  Click the select all button
                                
                3.  Click withdraw
                                
                ![](https://github.com/xdnw/locutus/blob/master/src/main/resources/img/selectall.png?raw=true)
                        """

        );
    }
}
