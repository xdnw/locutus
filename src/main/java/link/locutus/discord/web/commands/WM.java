package link.locutus.discord.web.commands;

import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import rocker.index;

public class WM {
    @AutoRegister(clazz=link.locutus.discord.web.commands.EconPages.class,method="taxExpensesByTime")
    public static class taxExpensesByTime extends CommandRef {
        public static final taxExpensesByTime cmd = new taxExpensesByTime();
        public taxExpensesByTime create(String start, String end, String nationFilter, String movingAverageTurns, String cumulative, String dontRequireTagged) {
            return createArgs("start", start, "end", end, "nationFilter", nationFilter, "movingAverageTurns", movingAverageTurns, "cumulative", cumulative, "dontRequireTagged", dontRequireTagged);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.NationListPages.class,method="filter")
    public static class filter extends CommandRef {
        public static final filter cmd = new filter();
        public filter create(String filter, String parentId) {
            return createArgs("filter", filter, "parentId", parentId);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.TradePages.class,method="tradePriceByDay")
    public static class tradePriceByDay extends CommandRef {
        public static final tradePriceByDay cmd = new tradePriceByDay();
        public tradePriceByDay create(String resources, String days) {
            return createArgs("resources", resources, "days", days);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.TradePages.class,method="tradePrice")
    public static class tradePrice extends CommandRef {
        public static final tradePrice cmd = new tradePrice();
        public tradePrice create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IndexPages.class,method="guildindex")
    public static class guildindex extends CommandRef {
        public static final guildindex cmd = new guildindex();
        public guildindex create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.StatPages.class,method="globalTierStats")
    public static class globalTierStats extends CommandRef {
        public static final globalTierStats cmd = new globalTierStats();
        public globalTierStats create(String metrics, String topX, String groupBy, String total) {
            return createArgs("metrics", metrics, "topX", topX, "groupBy", groupBy, "total", total);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.NationListPages.class,method="filters")
    public static class filters extends CommandRef {
        public static final filters cmd = new filters();
        public filters create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.StatPages.class,method="globalStats")
    public static class globalStats extends CommandRef {
        public static final globalStats cmd = new globalStats();
        public globalStats create(String metrics, String start, String end, String topX) {
            return createArgs("metrics", metrics, "start", start, "end", end, "topX", topX);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.WarPages.class,method="enemies")
    public static class enemies extends CommandRef {
        public static final enemies cmd = new enemies();
        public enemies create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.alliance.AlliancePages.class,method="announcements")
    public static class announcements extends CommandRef {
        public static final announcements cmd = new announcements();
        public announcements create(String showArchived) {
            return createArgs("showArchived", showArchived);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IndexPages.class,method="logout")
    public static class logout extends CommandRef {
        public static final logout cmd = new logout();
        public logout create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.StatPages.class,method="radiationStats")
    public static class radiationStats extends CommandRef {
        public static final radiationStats cmd = new radiationStats();
        public radiationStats create(String continents, String start, String end) {
            return createArgs("continents", continents, "start", start, "end", end);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.WarPages.class,method="defensive")
    public static class defensive extends CommandRef {
        public static final defensive cmd = new defensive();
        public defensive create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.NationListPages.class,method="filtersJson")
    public static class filtersJson extends CommandRef {
        public static final filtersJson cmd = new filtersJson();
        public filtersJson create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.jooby.PageHandler.class,method="command")
    public static class command extends CommandRef {
        public static final command cmd = new command();
        public command create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.TestPages.class,method="testIndex")
    public static class testIndex extends CommandRef {
        public static final testIndex cmd = new testIndex();
        public testIndex create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IndexPages.class,method="allianceIndex")
    public static class allianceIndex extends CommandRef {
        public static final allianceIndex cmd = new allianceIndex();
        public allianceIndex create(String allianceId) {
            return createArgs("allianceId", allianceId);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IAPages.class,method="mentorIndex")
    public static class mentorIndex extends CommandRef {
        public static final mentorIndex cmd = new mentorIndex();
        public mentorIndex create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.WarPages.class,method="offensive")
    public static class offensive extends CommandRef {
        public static final offensive cmd = new offensive();
        public offensive create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.StatPages.class,method="aaStats")
    public static class aaStats extends CommandRef {
        public static final aaStats cmd = new aaStats();
        public aaStats create(String metrics, String start, String end, String coalition) {
            return createArgs("metrics", metrics, "start", start, "end", end, "coalition", coalition);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IndexPages.class,method="guildMemberIndex")
    public static class guildMemberIndex extends CommandRef {
        public static final guildMemberIndex cmd = new guildMemberIndex();
        public guildMemberIndex create(String nation) {
            return createArgs("nation", nation);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.StatPages.class,method="compareTierStats")
    public static class compareTierStats extends CommandRef {
        public static final compareTierStats cmd = new compareTierStats();
        public compareTierStats create(String metric, String groupBy, String coalition1, String coalition2, String coalition3, String coalition4, String coalition5, String coalition6, String coalition7, String coalition8, String coalition9, String coalition10, String total, String barGraph) {
            return createArgs("metric", metric, "groupBy", groupBy, "coalition1", coalition1, "coalition2", coalition2, "coalition3", coalition3, "coalition4", coalition4, "coalition5", coalition5, "coalition6", coalition6, "coalition7", coalition7, "coalition8", coalition8, "coalition9", coalition9, "coalition10", coalition10, "total", total, "barGraph", barGraph);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.GrantPages.class,method="landGrants")
    public static class landGrants extends CommandRef {
        public static final landGrants cmd = new landGrants();
        public landGrants create(String nation) {
            return createArgs("nation", nation);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.alliance.AlliancePages.class,method="announcementVariations")
    public static class announcementVariations extends CommandRef {
        public static final announcementVariations cmd = new announcementVariations();
        public announcementVariations create(String announcementId) {
            return createArgs("announcementId", announcementId);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.alliance.AlliancePages.class,method="allianceLeaves")
    public static class allianceLeaves extends CommandRef {
        public static final allianceLeaves cmd = new allianceLeaves();
        public allianceLeaves create(String allianceId, String includeInactive, String includeVM, String include) {
            return createArgs("allianceId", allianceId, "includeInactive", includeInactive, "includeVM", includeVM, "include", include);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.GrantPages.class,method="infraGrants")
    public static class infraGrants extends CommandRef {
        public static final infraGrants cmd = new infraGrants();
        public infraGrants create(String nation) {
            return createArgs("nation", nation);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IAPages.class,method="memberAuditIndex")
    public static class memberAuditIndex extends CommandRef {
        public static final memberAuditIndex cmd = new memberAuditIndex();
        public memberAuditIndex create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.StatPages.class,method="compareStats")
    public static class compareStats extends CommandRef {
        public static final compareStats cmd = new compareStats();
        public compareStats create(String metric, String start, String end, String coalition1, String coalition2, String coalition3, String coalition4, String coalition5, String coalition6, String coalition7, String coalition8, String coalition9, String coalition10) {
            return createArgs("metric", metric, "start", start, "end", end, "coalition1", coalition1, "coalition2", coalition2, "coalition3", coalition3, "coalition4", coalition4, "coalition5", coalition5, "coalition6", coalition6, "coalition7", coalition7, "coalition8", coalition8, "coalition9", coalition9, "coalition10", coalition10);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.EconPages.class,method="taxExpensesIndex")
    public static class taxExpensesIndex extends CommandRef {
        public static final taxExpensesIndex cmd = new taxExpensesIndex();
        public taxExpensesIndex create(String start, String end, String nationList, String dontRequireGrant, String dontRequireTagged, String dontRequireExpiry, String includeDeposits) {
            return createArgs("start", start, "end", end, "nationList", nationList, "dontRequireGrant", dontRequireGrant, "dontRequireTagged", dontRequireTagged, "dontRequireExpiry", dontRequireExpiry, "includeDeposits", includeDeposits);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.TradePages.class,method="tradePriceByDayJson")
    public static class tradePriceByDayJson extends CommandRef {
        public static final tradePriceByDayJson cmd = new tradePriceByDayJson();
        public tradePriceByDayJson create(String resources, String days) {
            return createArgs("resources", resources, "days", days);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.WarPages.class,method="counter")
    public static class counter extends CommandRef {
        public static final counter cmd = new counter();
        public counter create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.GrantPages.class,method="projectGrants")
    public static class projectGrants extends CommandRef {
        public static final projectGrants cmd = new projectGrants();
        public projectGrants create(String nation) {
            return createArgs("nation", nation);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.StatPages.class,method="warCost")
    public static class warCost extends CommandRef {
        public static final warCost cmd = new warCost();
        public warCost create(String coalition1, String coalition2, String timeStart, String timeEnd) {
            return createArgs("coalition1", coalition1, "coalition2", coalition2, "timeStart", timeStart, "timeEnd", timeEnd);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.TestPages.class,method="testPost")
    public static class testPost extends CommandRef {
        public static final testPost cmd = new testPost();
        public testPost create(String argument) {
            return createArgs("argument", argument);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IAPages.class,method="iaChannels")
    public static class iaChannels extends CommandRef {
        public static final iaChannels cmd = new iaChannels();
        public iaChannels create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.GrantPages.class,method="cityGrants")
    public static class cityGrants extends CommandRef {
        public static final cityGrants cmd = new cityGrants();
        public cityGrants create(String nation) {
            return createArgs("nation", nation);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.BankPages.class,method="memberDeposits")
    public static class memberDeposits extends CommandRef {
        public static final memberDeposits cmd = new memberDeposits();
        public memberDeposits create(String force, String noTaxBase, String ignoreOffset) {
            return createArgs("force", force, "noTaxBase", noTaxBase, "ignoreOffset", ignoreOffset);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.StatPages.class,method="metricByGroup")
    public static class metricByGroup extends CommandRef {
        public static final metricByGroup cmd = new metricByGroup();
        public metricByGroup create(String metrics, String coalition, String groupBy, String includeInactives, String includeApplicants, String total) {
            return createArgs("metrics", metrics, "coalition", coalition, "groupBy", groupBy, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "total", total);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.BankPages.class,method="bankIndex")
    public static class bankIndex extends CommandRef {
        public static final bankIndex cmd = new bankIndex();
        public bankIndex create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IndexPages.class,method="guildSelect")
    public static class guildSelect extends CommandRef {
        public static final guildSelect cmd = new guildSelect();
        public guildSelect create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.IndexPages.class,method="search")
    public static class search extends CommandRef {
        public static final search cmd = new search();
        public search create(String term) {
            return createArgs("term", term);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.web.commands.alliance.AlliancePages.class,method="manageAnnouncements")
    public static class manageAnnouncements extends CommandRef {
        public static final manageAnnouncements cmd = new manageAnnouncements();
        public manageAnnouncements create(String showArchived) {
            return createArgs("showArchived", showArchived);
        }
    }
}
