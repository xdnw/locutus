package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildBooleanSetting;
import link.locutus.discord.db.guild.GuildChannelSetting;
import link.locutus.discord.db.guild.GuildLongSetting;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;

import java.util.Arrays;
import java.util.stream.Collectors;

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
                        """,

                "# Viewing deposits",
                // /deposits check
                // /deposits sheet

                // ## Changing display mode
                // Use the command argument, or set the following:
//                condensed deposits
//                        /settings_bank_info DISPLAY_ITEMIZED_DEPOSITS

                // ## Viewing tax bracket accounts
                // link to tax automation page

                "# Transfer Notes",
                "Notes can be used in the transfer commands, as well as when sending in-game",
                "Multiple notes can be used at a time",
                "Notes with values go in the form `#expire=60d`",
                "- " + Arrays.stream(DepositType.values()).map(f -> "`" + f.toString() + "`: " + f.getDescription()).collect(Collectors.joining("\n- ")),
                // ensure expire is listed and explained


                // Tracking alliances
                // only relevant for alliances
                // coalition track bank
                // allianceids
                // offshores

                // taxes

                // Adding to balances
                // Use negative amounts to subtract
                // ## Bulk add balance
                // Specify multiple nations for the command above, or use add balance sheet
//                /deposits shift
//                /deposits convertNegative

                // resetting deposits
                // - Set the arguments for the categories you do not wish to reset to false

                // Allow members to withdraw
                // copy bank page to here
                // NON_AA_MEMBERS_CAN_BANK
                // resource conversion
                // MEMBER_CAN_OFFSHORE

//        public static GuildSetting<Boolean> MEMBER_CAN_OFFSHORE = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
//            public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
//                public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW_WARTIME = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
//                    public static GuildSetting<Boolean> MEMBER_CAN_WITHDRAW_IGNORES_GRANTS = new GuildBooleanSetting(GuildSettingCategory.BANK_ACCESS) {
//                        public static GuildSetting<Long> BANKER_WITHDRAW_LIMIT = new GuildLongSetting(GuildSettingCategory.BANK_ACCESS) {
//                            public static GuildSetting<Long> BANKER_WITHDRAW_LIMIT_INTERVAL = new GuildLongSetting(GuildSettingCategory.BANK_ACCESS) {

                // Explanation of transfer command
                // explain each argument

                // Tax accounts & Including taxes in deposits -> Link to tax automation page

                // Listing transfers
                //        /bank records
                //        /tax deposits
                //       /tax records

                // Alerts
//        public static GuildSetting<MessageChannel> ADDBALANCE_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
//            public static GuildSetting<MessageChannel> WITHDRAW_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
//                public static GuildSetting<MessageChannel> BANK_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
//                    public static GuildSetting<MessageChannel> DEPOSIT_ALERT_CHANNEL = new GuildChannelSetting(GuildSettingCategory.BANK_INFO) {
//                                BANK_ALERT_CHANNEL
        );
    }
}
