package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiDepositsPage extends WikiGen {
    public WikiDepositsPage(CommandManager2 manager) {
        super(manager, "deposits");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        Member balances are determined by what they deposit into the bank, as well as funds they receive from the alliance.
                        
                        Offshores/Training alliances etc. 
                        """
        );
       /*
       Notes
       Expire

       That's a nation's deposits account.
[7:31 PM]borg: Taxes wont show there unless you set all or a portion of taxes to go into their personal deposits
/tax records and /tax deposits
[7:30 PM]borg--2081783266881942131-------: You can also view the taxes for a bracket using /deposits check (with the tax url, or #tax_id=1234, where 1234 is the tax id)
And /deposits add to adjust the balance of hte tax bracket (see also taxaccount argument for the /transfer commands - to withdraw from the bracket when you do a transfer)

       Grant command

       /deposits shift
       /deposits convertNegative

       Itemized deposits
       /settings_bank_info DISPLAY_ITEMIZED_DEPOSITS

       Listing transfers
       /bank records
       /tax deposits

       Taxes into deposits -> Link to tax automation page

       Tracking other alliances
       Tracking offshores

       Addbalance

       Bulk add balance

       Withdraw
       NationAccount argument
       Transfer commands

       Checking deposits / deposits sheet
       Importing deposits sheet

       Resetting deposits

       Settings
           public static GuildSetting<Boolean> RESOURCE_CONVERSION = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {

           public static GuildSetting<MessageChannel> ADDBALANCE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
           public static GuildSetting<MessageChannel> WITHDRAW_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
           public static GuildSetting<MessageChannel> BANK_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
           public static GuildSetting<Boolean> DISPLAY_ITEMIZED_DEPOSITS = new GuildBooleanSetting(GuildSettingCategory.BANK_INFO) {
           public static GuildSetting<MessageChannel> DEPOSIT_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {

       Member auto withdrawing
           public static GuildSetting<Boolean> MEMBER_CAN_OFFSHORE = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW_WARTIME = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW_IGNORES_GRANTS = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Long> BANKER_WITHDRAW_LIMIT = new GuildLongSetting(GuildSettingCategory.BANK_ACCESS) {
    public static GuildSetting<Long> BANKER_WITHDRAW_LIMIT_INTERVAL = new GuildLongSetting(GuildSettingCategory.BANK_ACCESS) {

    BANK_ALERT_CHANNEL
        */
    }
}
