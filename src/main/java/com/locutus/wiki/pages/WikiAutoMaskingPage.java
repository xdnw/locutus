package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;

public class WikiAutoMaskingPage extends BotWikiGen {
    public WikiAutoMaskingPage(CommandManager2 manager) {
        super(manager, "auto masking");
    }

    @Override
    public String generateMarkdown() {
        return build(
        """
                Set user nicknames.
                
                Automatically add and remove discord roles:
                - Applicant and member roles
                - Set city range roles (e.g. `c1-10`, `c11+`)
                - Tax bracket roles (e.g. `25/25`)
                - Specific or top alliance name roles
                - Sync roles (e.g. for a coalition or milcom server)""",
                "# Prerequisites",
                "Configure your role aliases, as seen on the " + linkPage("setup") + " page.",
                "# Nicknames",
                commandMarkdownSpoiler(CM.settings_role.AUTONICK.cmd),
                commandMarkdownSpoiler(CM.role.clearNicks.cmd),
                "# Verified roles",
                "Give a role to all nations registered with the bot using " + CM.register.cmd.toString(),
                CM.role.setAlias.cmd.create(Roles.REGISTERED.name(), "@registered", null, null).toString(),
                "# Manually running the autorole task",
                "### For all users",
                CM.role.autoassign.cmd.toString(),
                "### For a single user",
                CM.role.autorole.cmd.create("@user", null).toString(),
                "# Alliance Name Roles",
                "Options\n- " + StringMan.join(GuildDB.AutoRoleOption.values(), "\n- "),
                CM.settings_role.AUTOROLE_ALLIANCES.cmd.create("ALL").toString(),
                commandMarkdownSpoiler(CM.settings_role.AUTOROLE_ALLIANCE_RANK.cmd),
                "### Give alliance name roles to the top alliances (or set it to 0)",
                commandMarkdownSpoiler(CM.settings_role.AUTOROLE_TOP_X.cmd),
                "### Add specific alliances",
                CM.coalition.add.cmd.create("", Coalition.MASKEDALLIANCES.name()).toString(),
                "## Clear alliance roles",
                "Options\n- " + StringMan.join(UnsortedCommands.ClearRolesEnum.values(), "\n- "),
                CM.role.clearAllianceRoles.cmd.create("").toString(),
                "# Applicant / Member roles",
                "Register your alliance:",
                CM.settings_default.registerAlliance.cmd.create("").toString(),
                "Create the roles on discord:",
                CM.role.setAlias.cmd.create(Roles.APPLICANT.name(), "@applicant", null, null).toString(),
                CM.role.setAlias.cmd.create(Roles.MEMBER.name(), "@member", null, null).toString(),
                "Enable auto role for member and applicant:",
                CM.settings_role.AUTOROLE_MEMBER_APPS.cmd.create("true").toString(),
                "# City Range roles",
                """
                Create a discord role.
                All city name roles start with `c`, and are inclusive:
                - To create range, use a dash: `c1-10`
                - For a single city count, use a number: `c11`
                - For a range to infinity, use a plus: `c12+`
                
                Overlapping ranges are not supported. Run the auto role command to assign.""",
                "# Tax roles",
                "Ensure you have provided your api key",
                CM.settings_default.registerApiKey.cmd.create("").toString(),
                """
                To create and assign a tax role:
                - Create a discord role named as the tax rate, i.e. `25/25`.
                - Run the auto role command.
                
                No other formats are currently supported.""",
                "# Conditional roles",
                GuildKey.CONDITIONAL_ROLES.help(),
                commandMarkdownSpoiler(CM.settings_role.CONDITIONAL_ROLES.cmd),
                "# Sync roles",
                "Give the aliased locutus roles to all members based on the roles they have in their respective alliance server",
                "This cannot be enabled on an alliance server, and is intended for coalitions",
                "Set the allies coalition:",
                CM.coalition.add.cmd.create("", Coalition.ALLIES.name()).toString(),
                CM.coalition.list.cmd.toString(),
                "Enable ally gov roles:",
                CM.settings_role.AUTOROLE_ALLY_GOV.cmd.create("true").toString(),
                "Specify the roles",
                commandMarkdownSpoiler(CM.settings_role.AUTOROLE_ALLY_ROLES.cmd),
                "# See also",
                linkPage("self_roles")
        );
    }
}
