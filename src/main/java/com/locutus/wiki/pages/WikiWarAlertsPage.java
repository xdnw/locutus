package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;

public class WikiWarAlertsPage extends WikiGen {
    public WikiWarAlertsPage(CommandManager2 manager) {
        super(manager, "war_alerts");
    }

    @Override
    public String generateMarkdown() {
        // Cards are generated when members have offensive/defensive wars.
        // Members/milcom are pinged for defensive wars against members. FA is pinged for DNR violations.
        for (GuildSetting key : GuildKey.values()) {

        }

        // Militarization alerts
        // /settings_orbis_alerts ORBIS_OFFICER_MMR_CHANGE_ALERTS
        // /settings_orbis_alerts AA_GROUND_TOP_X
        ///settings_orbis_alerts AA_GROUND_UNIT_ALERTS
        // Set the role


        // Off/def
        //War won/loss
        // peace alerts
        // espionage

        // Enemy alerts

        // Online alerts

        // Beige alerts
        ///alerts beige beigeAlert
        ///alerts beige beigeAlertMode
        ///alerts beige beigeAlertOptOut
        ///alerts beige beigeAlertRequiredLoot
        ///alerts beige beigeAlertRequiredStatus
        ///alerts beige beigeReminders
        ///alerts beige removeBeigeReminder
        ///alerts beige setBeigeAlertScoreLeeway

        // /alerts enemy optout
        // /alerts login

        // ALLIANCE_ID
        // API KEY

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
