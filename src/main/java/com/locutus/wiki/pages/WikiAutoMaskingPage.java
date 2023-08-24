package com.locutus.wiki.pages;

import com.locutus.wiki.CommandWikiPages;
import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiAutoMaskingPage extends WikiGen {
    public WikiAutoMaskingPage(CommandManager2 manager) {
        super(manager, "auto masking");
    }

    @Override
    public String generateMarkdown() {
        /*
        Alliance city roles (inclusive): Add a role called c3 or a range c5-10
        Tax roles (must have api key set)
        AutoNick enums
        AutoRole enums

        MASKEDALLIANCES coalition

/role autoassign
/role autorole
/role clearAllianceRoles
/role clearNicks

/role addRoleToAllMembers


// self roles
/role add
/role remove
/role removeAssignableRole

/self create
/self list
/self mask

// settings
Channel settings
    public static GuildSetting<GuildDB.AutoNickOption> AUTONICK = new GuildEnumSetting<GuildDB.AutoNickOption>(GuildSettingCategory.ROLE, GuildDB.AutoNickOption.class) {
    public static GuildSetting<GuildDB.AutoRoleOption> AUTOROLE_ALLIANCES = new GuildEnumSetting<GuildDB.AutoRoleOption>(GuildSettingCategory.ROLE, GuildDB.AutoRoleOption.class) {
    public static GuildSetting<Rank> AUTOROLE_ALLIANCE_RANK = new GuildEnumSetting<Rank>(GuildSettingCategory.ROLE, Rank.class) {
    public static GuildSetting<Integer> AUTOROLE_TOP_X = new GuildIntegerSetting(GuildSettingCategory.ROLE) {
    public static GuildSetting<Boolean> AUTOROLE_ALLY_GOV = new GuildBooleanSetting(GuildSettingCategory.ROLE) {
    public static GuildSetting<Set<Roles>> AUTOROLE_ALLY_ROLES = new GuildEnumSetSetting<Roles>(GuildSettingCategory.ROLE, Roles.class) {
    ASSIGNABLE_ROLES

    /settings_role ASSIGNABLE_ROLES
/settings_role AUTONICK
/settings_role AUTOROLE_ALLIANCES
/settings_role AUTOROLE_ALLIANCE_RANK
/settings_role AUTOROLE_ALLY_GOV
/settings_role AUTOROLE_ALLY_ROLES
/settings_role AUTOROLE_TOP_X
/settings_role addAssignableRole
         */
    }
}
