package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.user.Roles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WikiWarAlertsPage extends BotWikiGen {
    public WikiWarAlertsPage(CommandManager2 manager) {
        super(manager, "war_alerts");
    }

    @Override
    public String generateMarkdown() {

        List<GuildSetting> militarizationSettings = new ArrayList<>(Arrays.asList(
                GuildKey.ACTIVITY_ALERTS,
                GuildKey.ORBIS_OFFICER_MMR_CHANGE_ALERTS,
                GuildKey.AA_GROUND_TOP_X,
                GuildKey.AA_GROUND_UNIT_ALERTS,
                GuildKey.ENEMY_MMR_CHANGE_ALERTS
        ));
        // militarization alert role

        List<GuildSetting> enemyAlerts = new ArrayList<>(Arrays.asList(

        ));

        return build(
    """
            Create and configure alerts for:
            - War declarations
            - War status updates
            - Espionage
            - Target availability
            - Militarization
            - Activity""",
            "# Prerequisites",
            "Register your alliance",
            CM.settings_default.registerAlliance.cmd.create("").toString(),
            "# War Declarations",
            "## Defensive Wars",
                commandMarkdownSpoiler(CM.settings_war_alerts.DEFENSE_WAR_CHANNEL.cmd, true),
            "### Configure defensive wars",
            "Set the milcom role to ping",
            CM.role.setAlias.cmd.locutusRole(Roles.MILCOM.name()).discordRole("@milcom").toString(),
            hr(),
                commandMarkdownSpoiler(CM.settings_war_alerts.MENTION_MILCOM_FILTER.cmd, true),
                commandMarkdownSpoiler(CM.settings_war_alerts.SHOW_ALLY_DEFENSIVE_WARS.cmd, true),
                commandMarkdownSpoiler(CM.settings_war_alerts.WAR_ALERT_FOR_OFFSHORES.cmd, true),
                commandMarkdownSpoiler(CM.settings_war_alerts.HIDE_APPLICANT_WARS.cmd, true),
            "## Offensive wars",
                commandMarkdownSpoiler(CM.settings_war_alerts.OFFENSIVE_WAR_CHANNEL.cmd, true),
            "### Configure offensive wars",
            "Ping the foreign affairs role when there is a " + linkPage("do_not_raid") + " violation",
            CM.role.setAlias.cmd.locutusRole(Roles.FOREIGN_AFFAIRS.name()).discordRole("@foreign_affairs").toString(),
            hr(),
                commandMarkdownSpoiler(CM.settings_war_alerts.SHOW_ALLY_OFFENSIVE_WARS.cmd, true),
            "# War Status Updates",
            "## Peace",
                commandMarkdownSpoiler(CM.settings_war_alerts.WAR_PEACE_ALERTS.cmd, true),
            "## Win/Lose",
                commandMarkdownSpoiler(CM.settings_war_alerts.LOST_WAR_CHANNEL.cmd, true),
                commandMarkdownSpoiler(CM.settings_war_alerts.WON_WAR_CHANNEL.cmd, true),
            "## Blockade",
            "See: " + linkPage("blockade_tools"),
            "# Espionage",
                commandMarkdownSpoiler(CM.settings_default.registerApiKey.cmd, true),
                commandMarkdownSpoiler(CM.settings_war_alerts.ESPIONAGE_ALERT_CHANNEL.cmd, true),
            "# Target alerts",
            "## Enemy Leaving Beige Alerts",
                commandMarkdownSpoiler(CM.settings_beige_alerts.ENEMY_ALERT_CHANNEL.cmd, true),
                commandMarkdownSpoiler(CM.settings_beige_alerts.ENEMY_ALERT_CHANNEL_MODE.cmd, true),
                commandMarkdownSpoiler(CM.settings_beige_alerts.ENEMY_ALERT_FILTER.cmd, true),
            "#### Roles",
            CM.role.setAlias.cmd.locutusRole(Roles.BEIGE_ALERT.name()).discordRole("@member").toString(),
            "#### Opt out of enemy alerts:",
            CM.alerts.enemy.optout.cmd.toString(),
            "## Raiding Beige Alerts (raid targets)",
            CM.role.setAlias.cmd.locutusRole(Roles.BEIGE_ALERT.name()).discordRole("@member").toString(),
            commandMarkdownSpoiler(CM.settings_beige_alerts.BEIGE_ALERT_CHANNEL.cmd, true),
            ///alerts beige beigeAlertMode
            commandMarkdownSpoiler(CM.alerts.beige.beigeAlertOptOut.cmd, true),
            commandMarkdownSpoiler(CM.alerts.beige.test_auto.cmd, true),
            commandMarkdownSpoiler(CM.alerts.beige.beigeAlertMode.cmd, true),
            commandMarkdownSpoiler(CM.alerts.beige.beigeAlert.cmd, true),
            commandMarkdownSpoiler(CM.alerts.beige.beigeAlertRequiredLoot.cmd, true),
            commandMarkdownSpoiler(CM.alerts.beige.beigeAlertRequiredStatus.cmd, true),
            commandMarkdownSpoiler(CM.alerts.beige.beigeReminders.cmd, true),
            commandMarkdownSpoiler(CM.alerts.beige.removeBeigeReminder.cmd, true),
            commandMarkdownSpoiler(CM.alerts.beige.setBeigeAlertScoreLeeway.cmd, true),
            "### Treasure  alerts",
            commandMarkdownSpoiler(CM.settings_bounty.TREASURE_ALERT_CHANNEL.cmd, true),
            "#### Roles",
            CM.role.setAlias.cmd.locutusRole(Roles.TREASURE_ALERT.name()).discordRole("@treasure-alert").toString(),
            CM.role.setAlias.cmd.locutusRole(Roles.TREASURE_ALERT_OPT_OUT.name()).discordRole("@no-alerts").toString(),
            "### Bounty alerts",
                commandMarkdownSpoiler(CM.settings_bounty.BOUNTY_ALERT_CHANNEL.cmd, true),
            "#### Roles",
            CM.role.setAlias.cmd.locutusRole(Roles.BOUNTY_ALERT.name()).discordRole("@bounty-alert").toString(),
            CM.role.setAlias.cmd.locutusRole(Roles.BOUNTY_ALERT_OPT_OUT.name()).discordRole("@no-alerts").toString(),
            "# Militarization",
            commandMarkdownSpoiler(CM.settings_orbis_alerts.ACTIVITY_ALERTS.cmd, true),
            commandMarkdownSpoiler(CM.settings_orbis_alerts.ORBIS_OFFICER_MMR_CHANGE_ALERTS.cmd, true),
            commandMarkdownSpoiler(CM.settings_orbis_alerts.AA_GROUND_UNIT_ALERTS.cmd, true),
            commandMarkdownSpoiler(CM.settings_orbis_alerts.AA_GROUND_TOP_X.cmd, true),
            commandMarkdownSpoiler(CM.settings_war_alerts.ENEMY_MMR_CHANGE_ALERTS.cmd, true),
            commandMarkdownSpoiler(CM.settings_orbis_alerts.ACTIVITY_ALERTS.cmd, true),
            "# Login Alerts",
                commandMarkdownSpoiler(CM.alerts.login.cmd, true)

      );
    }
}
