package link.locutus.wiki.pages;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.RssConvertMode;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.wiki.BotWikiGen;

public class WikiConversionPage extends BotWikiGen {
    public WikiConversionPage(CommandManager2 manager) {
        super(manager, "resource_conversion");
    }

    @Override
    public String generateMarkdown() {
        return build(
                "Resource conversion supports both on-demand and automatic conversion of raw or negative resource balances into cash.",
                "- Manual conversion by ECON roles via the " + CM.deposits.convert.cmd.toString() + " command.\n" +
                "- Automatic conversion at deposit/withdrawal time using the `#cash` note.",
                "Rates default to 100% of weekly market average and can be scoped per-nation, tax bracket, role, or custom filter.",
                "## 1. Manual resource conversion",
                "Admins and ECON can manually convert existing balances on demand:",
                commandMarkdownSpoiler(CM.deposits.convert.cmd),
                "Example:",
                "- " + CM.deposits.convert.cmd.nations("AA:1234,#position>1")
                        .mode(RssConvertMode.ALL.name())
                        .from_resources("*")
                        .to_resource(ResourceType.MONEY.name())
                        .conversionFactor("0.8")
                        .includeGrants("false")
                        .toString(),
                "## 2. Enable member resource conversion",
                "Allow members to deposit or withdraw using `#cash` (e.g. via the `convertCash` argument in transfer commands) .",
                "- Note: If a " + Roles.RESOURCE_CONVERSION.name() + " role is set via " + CM.role.setAlias.cmd.toString() + ", only members with that role can use this feature.",
                commandMarkdownSpoiler(CM.settings_bank_conversion.RESOURCE_CONVERSION.cmd),

                "## 3. Allow negative resource withdrawals",
                "Allow nations to withdraw resources into the negative as long as the overall market value of their deposits is positive",
                commandMarkdownSpoiler(CM.settings_bank_conversion.ALLOW_NEGATIVE_RESOURCES.cmd),

                "## 4. Set conversion rates",
                "Define a percent of weekly market value to use for each resource (default 100 = 100%):",
                "Use `*` to apply to all allowed nations or specify a filter: " + linkPage("nation_placeholders") + " e.g. `#cities>20` or `MyDiscordRole`",
                commandMarkdownSpoiler(
                        CM.settings_bank_conversion.ADD_RSS_CONVERSION_RATE
                                .cmd
                                .filter("*")
                                .prices("{food=60, coal=90, oil=90, uranium=90}")
                ),
                "To view the current rates: " + CM.settings.info.cmd.key(GuildKey.RSS_CONVERSION_RATES.name()).toString(),

                "## 5. Force conversion on new deposits",
                "Automatically convert all new deposits regardless of `#cash` note:",
                commandMarkdownSpoiler(CM.settings_bank_conversion.FORCE_RSS_CONVERSION.cmd),
                "Note: This will only apply to nations eligable for resource conversion (such as if you have a role set)"
        );
    }
}
