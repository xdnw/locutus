package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiInterviewPage extends WikiGen {
    public WikiInterviewPage(CommandManager2 manager) {
        super(manager, "interviews");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       copypasta interview

       Applicant role
        - Make a self role
        - (how to do that via locutus)
        - OR ME6 example


       Related roles
       INTERNAL_AFFAIRS_STAFF
       APPLICANT
       INTERVIEWER
       MENTOR
       GRADUATED

       Interview category (name, how many to have etc.)
        - Categories

/interview adRanking
/interview create
/interview iacat
/interview iachannels
/interview incentiveRanking
/interview interviewMessage
/interview listMentors
/interview mentee
/interview mentor
/interview mymentees
/interview recruitmentRankings
/interview setReferrer
/interview sortInterviews
/interview syncInterviews
/interview unassignMentee


           public static GuildSetting<MessageChannel> INTERVIEW_INFO_SPAM = new GuildChannelSetting(GuildSettingCategory.INTERVIEW) {
    public static GuildSetting<MessageChannel> INTERVIEW_PENDING_ALERTS = new GuildChannelSetting(GuildSettingCategory.INTERVIEW) {
    public static GuildSetting<Category> ARCHIVE_CATEGORY = new GuildCategorySetting(GuildSettingCategory.INTERVIEW) {

        public static GuildSetting<Map<ResourceType, Double>> REWARD_REFERRAL = new GuildResourceSetting(GuildSettingCategory.REWARD) {
    public static GuildSetting<Map<ResourceType, Double>> REWARD_MENTOR = new GuildResourceSetting(GuildSettingCategory.REWARD) {
        */
    }
}
