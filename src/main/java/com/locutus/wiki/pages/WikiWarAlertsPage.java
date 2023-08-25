package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WikiWarAlertsPage extends WikiGen {
    public WikiWarAlertsPage(CommandManager2 manager) {
        super(manager, "war_alerts");
    }

    @Override
    public String generateMarkdown() {
        List<GuildSetting> warStatusChannelSettings = new ArrayList<>(Arrays.asList(
            GuildKey.DEFENSE_WAR_CHANNEL,
            GuildKey.OFFENSIVE_WAR_CHANNEL,
            GuildKey.ESPIONAGE_ALERT_CHANNEL,
            GuildKey.WAR_PEACE_ALERTS,
            GuildKey.LOST_WAR_CHANNEL,
            GuildKey.WON_WAR_CHANNEL,
            GuildKey.BLOCKADED_ALERTS,
            GuildKey.UNBLOCKADED_ALERTS
        ));
        List<GuildSetting> warStatusOtherSettings = new ArrayList<>(Arrays.asList(
                GuildKey.MENTION_MILCOM_FILTER,
                GuildKey.SHOW_ALLY_DEFENSIVE_WARS,
                GuildKey.WAR_ALERT_FOR_OFFSHORES,
                GuildKey.SHOW_ALLY_OFFENSIVE_WARS,
                GuildKey.HIDE_APPLICANT_WARS
        ));

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

        // Enemy alert roles / opt out / opt out command
        // /alerts enemy optout

        // Online alerts
        // /alerts login

        // Beige alerts
        ///alerts beige beigeAlert
        ///alerts beige beigeAlertMode
        ///alerts beige beigeAlertOptOut
        ///alerts beige beigeAlertRequiredLoot
        ///alerts beige beigeAlertRequiredStatus
        ///alerts beige beigeReminders
        ///alerts beige removeBeigeReminder
        ///alerts beige setBeigeAlertScoreLeeway

        // ALLIANCE_ID
        // API KEY

        return build(
//                "# War & Espionage Alerts Overview",
//                """
//                There are alert channels for war status updates, espionage, target availability and militarization
//                """
        );
        // for (GuildSetting key : GuildKey.values()) {
        //
        //        }



        // Militarization alerts
        // ACTIVITY_ALERTS
        // /settings_orbis_alerts ORBIS_OFFICER_MMR_CHANGE_ALERTS
        // /settings_orbis_alerts AA_GROUND_TOP_X
        ///settings_orbis_alerts AA_GROUND_UNIT_ALERTS
//        /settings_beige_alerts ENEMY_MMR_CHANGE_ALERTS


        // Enemy alerts



        // Beige alerts





        //    public static GuildSetting<MessageChannel> DEFENSE_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        //    public static GuildSetting<MessageChannel> OFFENSIVE_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {

        //     public static GuildSetting<MessageChannel> ESPIONAGE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {

        //    public static GuildSetting<MessageChannel> WAR_PEACE_ALERTS = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {

        //    public static GuildSetting<MessageChannel> LOST_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        //    public static GuildSetting<MessageChannel> WON_WAR_CHANNEL = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {

        //    public static GuildSetting<MessageChannel> BLOCKADED_ALERTS = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {
        //    public static GuildSetting<MessageChannel> UNBLOCKADED_ALERTS = new GuildChannelSetting(GuildSettingCategory.WAR_ALERTS) {

        //    public static GuildSetting<NationFilter> MENTION_MILCOM_FILTER = new GuildNationFilterSetting(GuildSettingCategory.WAR_ALERTS) {

        //    public static GuildSetting<Boolean> SHOW_ALLY_DEFENSIVE_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        //    public static GuildSetting<Boolean> WAR_ALERT_FOR_OFFSHORES = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        //    public static GuildSetting<Boolean> SHOW_ALLY_OFFENSIVE_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {
        //    public static GuildSetting<Boolean> HIDE_APPLICANT_WARS = new GuildBooleanSetting(GuildSettingCategory.WAR_ALERTS) {


        // Set reminders
    }
}
