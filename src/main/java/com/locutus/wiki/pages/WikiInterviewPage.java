package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.account.question.Interview;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildCategorySetting;
import link.locutus.discord.db.guild.GuildChannelSetting;
import link.locutus.discord.db.guild.GuildResourceSetting;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;

import java.util.Map;

public class WikiInterviewPage extends BotWikiGen {
    public WikiInterviewPage(CommandManager2 manager) {
        super(manager, "interviews");
    }

    @Override
    public String generateMarkdown() {
        return build(
//                Applicant role
//                """
//                        - Interview alerts and tickets
//                        - Sorted interview categories
//                        - Interview creation prompts
//                        - Mentor/Mentee management and auditing
//                        - Referral, interviewing and mentoring rewards
//                        """,
//                        - Make a self role
//                        - (how to do that via locutus)
//        - OR ME6 example
//
//        copypasta interview
//
//
//
//
//        Related roles
//        INTERNAL_AFFAIRS_STAFF
//                APPLICANT
//        INTERVIEWER
//                MENTOR
//        GRADUATED
//
//        Interview category (name, how many to have etc.)
//        - Categories
//
//                /interview adRanking
//                /interview create
//                /interview iacat
//                /interview iachannels
//                /interview incentiveRanking
//                /interview interviewMessage
//                /interview listMentors
//                /interview mentee
//                /interview mentor
//                /interview mymentees
//                /interview recruitmentRankings
//                /interview setReferrer
//                /interview sortInterviews
//                /interview syncInterviews
//                /interview unassignMentee
//
//
//        public static GuildSetting<MessageChannel> INTERVIEW_INFO_SPAM = new GuildChannelSetting(GuildSettingCategory.INTERVIEW) {
//            public static GuildSetting<MessageChannel> INTERVIEW_PENDING_ALERTS = new GuildChannelSetting(GuildSettingCategory.INTERVIEW) {
//                public static GuildSetting<Category> ARCHIVE_CATEGORY = new GuildCategorySetting(GuildSettingCategory.INTERVIEW) {
//
//                    public static GuildSetting<Map<ResourceType, Double>> REWARD_REFERRAL = new GuildResourceSetting(GuildSettingCategory.REWARD) {
//                        public static GuildSetting<Map<ResourceType, Double>> REWARD_MENTOR = new GuildResourceSetting(GuildSettingCategory.REWARD) {
//                        }
        );
    }

}
