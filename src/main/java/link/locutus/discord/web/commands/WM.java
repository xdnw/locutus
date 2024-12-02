package link.locutus.discord.web.commands;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class WM {
        public static class api{
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="announcement_titles")
            public static class announcement_titles extends CommandRef {
                public static final announcement_titles cmd = new announcement_titles();
            public announcement_titles read(String value) {
                return set("read", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="announcements")
            public static class announcements extends CommandRef {
                public static final announcements cmd = new announcements();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="balance")
            public static class balance extends CommandRef {
                public static final balance cmd = new balance();
            public balance nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="bank_access")
            public static class bank_access extends CommandRef {
                public static final bank_access cmd = new bank_access();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="input_options")
            public static class input_options extends CommandRef {
                public static final input_options cmd = new input_options();
            public input_options type(String value) {
                return set("type", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="login_mail")
            public static class login_mail extends CommandRef {
                public static final login_mail cmd = new login_mail();
            public login_mail nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="logout")
            public static class logout extends CommandRef {
                public static final logout cmd = new logout();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="mark_all_read")
            public static class mark_all_read extends CommandRef {
                public static final mark_all_read cmd = new mark_all_read();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="my_audits")
            public static class my_audits extends CommandRef {
                public static final my_audits cmd = new my_audits();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="my_wars")
            public static class my_wars extends CommandRef {
                public static final my_wars cmd = new my_wars();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="query")
            public static class query extends CommandRef {
                public static final query cmd = new query();
            public query queries(String value) {
                return set("queries", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="raid")
            public static class raid extends CommandRef {
                public static final raid cmd = new raid();
            public raid nations(String value) {
                return set("nations", value);
            }

            public raid weak_ground(String value) {
                return set("weak_ground", value);
            }

            public raid vm_turns(String value) {
                return set("vm_turns", value);
            }

            public raid beige_turns(String value) {
                return set("beige_turns", value);
            }

            public raid ignore_dnr(String value) {
                return set("ignore_dnr", value);
            }

            public raid time_inactive(String value) {
                return set("time_inactive", value);
            }

            public raid min_loot(String value) {
                return set("min_loot", value);
            }

            public raid num_results(String value) {
                return set("num_results", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="read_announcement")
            public static class read_announcement extends CommandRef {
                public static final read_announcement cmd = new read_announcement();
            public read_announcement ann_id(String value) {
                return set("ann_id", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="records")
            public static class records extends CommandRef {
                public static final records cmd = new records();
            public records nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="register")
            public static class register extends CommandRef {
                public static final register cmd = new register();
            public register confirm(String value) {
                return set("confirm", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="session")
            public static class session extends CommandRef {
                public static final session cmd = new session();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="set_guild")
            public static class set_guild extends CommandRef {
                public static final set_guild cmd = new set_guild();
            public set_guild guild(String value) {
                return set("guild", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="set_oauth_code")
            public static class set_oauth_code extends CommandRef {
                public static final set_oauth_code cmd = new set_oauth_code();
            public set_oauth_code code(String value) {
                return set("code", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="set_token")
            public static class set_token extends CommandRef {
                public static final set_token cmd = new set_token();
            public set_token token(String value) {
                return set("token", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.StatEndpoints.class,method="table")
            public static class table extends CommandRef {
                public static final table cmd = new table();
            public table type(String value) {
                return set("type", value);
            }

            public table selection_str(String value) {
                return set("selection_str", value);
            }

            public table columns(String value) {
                return set("columns", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="test")
            public static class test extends CommandRef {
                public static final test cmd = new test();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TradeEndpoints.class,method="tradePriceByDayJson")
            public static class tradePriceByDayJson extends CommandRef {
                public static final tradePriceByDayJson cmd = new tradePriceByDayJson();
            public tradePriceByDayJson resources(String value) {
                return set("resources", value);
            }

            public tradePriceByDayJson days(String value) {
                return set("days", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="unprotected")
            public static class unprotected extends CommandRef {
                public static final unprotected cmd = new unprotected();
            public unprotected nations(String value) {
                return set("nations", value);
            }

            public unprotected includeAllies(String value) {
                return set("includeAllies", value);
            }

            public unprotected ignoreODP(String value) {
                return set("ignoreODP", value);
            }

            public unprotected ignore_dnr(String value) {
                return set("ignore_dnr", value);
            }

            public unprotected maxRelativeTargetStrength(String value) {
                return set("maxRelativeTargetStrength", value);
            }

            public unprotected maxRelativeCounterStrength(String value) {
                return set("maxRelativeCounterStrength", value);
            }

            public unprotected num_results(String value) {
                return set("num_results", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="unread_announcement")
            public static class unread_announcement extends CommandRef {
                public static final unread_announcement cmd = new unread_announcement();
            public unread_announcement ann_id(String value) {
                return set("ann_id", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="unread_count")
            public static class unread_count extends CommandRef {
                public static final unread_count cmd = new unread_count();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="unregister")
            public static class unregister extends CommandRef {
                public static final unregister cmd = new unregister();
            public unregister confirm(String value) {
                return set("confirm", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="view_announcement")
            public static class view_announcement extends CommandRef {
                public static final view_announcement cmd = new view_announcement();
            public view_announcement ann_id(String value) {
                return set("ann_id", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="withdraw")
            public static class withdraw extends CommandRef {
                public static final withdraw cmd = new withdraw();
            public withdraw receiver(String value) {
                return set("receiver", value);
            }

            public withdraw amount(String value) {
                return set("amount", value);
            }

            public withdraw note(String value) {
                return set("note", value);
            }

            }
        }
        public static class page{
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.StatPages.class,method="aaStats")
            public static class aaStats extends CommandRef {
                public static final aaStats cmd = new aaStats();
            public aaStats metrics(String value) {
                return set("metrics", value);
            }

            public aaStats start(String value) {
                return set("start", value);
            }

            public aaStats end(String value) {
                return set("end", value);
            }

            public aaStats coalition(String value) {
                return set("coalition", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.alliance.AlliancePages.class,method="allianceLeaves")
            public static class allianceLeaves extends CommandRef {
                public static final allianceLeaves cmd = new allianceLeaves();
            public allianceLeaves allianceId(String value) {
                return set("allianceId", value);
            }

            public allianceLeaves includeInactive(String value) {
                return set("includeInactive", value);
            }

            public allianceLeaves includeVM(String value) {
                return set("includeVM", value);
            }

            public allianceLeaves include(String value) {
                return set("include", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.alliance.AlliancePages.class,method="announcementVariations")
            public static class announcementVariations extends CommandRef {
                public static final announcementVariations cmd = new announcementVariations();
            public announcementVariations announcementId(String value) {
                return set("announcementId", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.alliance.AlliancePages.class,method="announcements")
            public static class announcements extends CommandRef {
                public static final announcements cmd = new announcements();
            public announcements showArchived(String value) {
                return set("showArchived", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.BankPages.class,method="bankIndex")
            public static class bankIndex extends CommandRef {
                public static final bankIndex cmd = new bankIndex();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.GrantPages.class,method="cityGrants")
            public static class cityGrants extends CommandRef {
                public static final cityGrants cmd = new cityGrants();
            public cityGrants nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.StatPages.class,method="compareStats")
            public static class compareStats extends CommandRef {
                public static final compareStats cmd = new compareStats();
            public compareStats metric(String value) {
                return set("metric", value);
            }

            public compareStats start(String value) {
                return set("start", value);
            }

            public compareStats end(String value) {
                return set("end", value);
            }

            public compareStats coalition1(String value) {
                return set("coalition1", value);
            }

            public compareStats coalition2(String value) {
                return set("coalition2", value);
            }

            public compareStats coalition3(String value) {
                return set("coalition3", value);
            }

            public compareStats coalition4(String value) {
                return set("coalition4", value);
            }

            public compareStats coalition5(String value) {
                return set("coalition5", value);
            }

            public compareStats coalition6(String value) {
                return set("coalition6", value);
            }

            public compareStats coalition7(String value) {
                return set("coalition7", value);
            }

            public compareStats coalition8(String value) {
                return set("coalition8", value);
            }

            public compareStats coalition9(String value) {
                return set("coalition9", value);
            }

            public compareStats coalition10(String value) {
                return set("coalition10", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.StatPages.class,method="compareTierStats")
            public static class compareTierStats extends CommandRef {
                public static final compareTierStats cmd = new compareTierStats();
            public compareTierStats metric(String value) {
                return set("metric", value);
            }

            public compareTierStats groupBy(String value) {
                return set("groupBy", value);
            }

            public compareTierStats coalition1(String value) {
                return set("coalition1", value);
            }

            public compareTierStats coalition2(String value) {
                return set("coalition2", value);
            }

            public compareTierStats coalition3(String value) {
                return set("coalition3", value);
            }

            public compareTierStats coalition4(String value) {
                return set("coalition4", value);
            }

            public compareTierStats coalition5(String value) {
                return set("coalition5", value);
            }

            public compareTierStats coalition6(String value) {
                return set("coalition6", value);
            }

            public compareTierStats coalition7(String value) {
                return set("coalition7", value);
            }

            public compareTierStats coalition8(String value) {
                return set("coalition8", value);
            }

            public compareTierStats coalition9(String value) {
                return set("coalition9", value);
            }

            public compareTierStats coalition10(String value) {
                return set("coalition10", value);
            }

            public compareTierStats total(String value) {
                return set("total", value);
            }

            public compareTierStats barGraph(String value) {
                return set("barGraph", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.WarPages.class,method="counter")
            public static class counter extends CommandRef {
                public static final counter cmd = new counter();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.StatPages.class,method="globalStats")
            public static class globalStats extends CommandRef {
                public static final globalStats cmd = new globalStats();
            public globalStats metrics(String value) {
                return set("metrics", value);
            }

            public globalStats start(String value) {
                return set("start", value);
            }

            public globalStats end(String value) {
                return set("end", value);
            }

            public globalStats topX(String value) {
                return set("topX", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.StatPages.class,method="globalTierStats")
            public static class globalTierStats extends CommandRef {
                public static final globalTierStats cmd = new globalTierStats();
            public globalTierStats metrics(String value) {
                return set("metrics", value);
            }

            public globalTierStats topX(String value) {
                return set("topX", value);
            }

            public globalTierStats groupBy(String value) {
                return set("groupBy", value);
            }

            public globalTierStats total(String value) {
                return set("total", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="guildMemberIndex")
            public static class guildMemberIndex extends CommandRef {
                public static final guildMemberIndex cmd = new guildMemberIndex();
            public guildMemberIndex nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="guildindex")
            public static class guildindex extends CommandRef {
                public static final guildindex cmd = new guildindex();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="guildselect")
            public static class guildselect extends CommandRef {
                public static final guildselect cmd = new guildselect();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IAPages.class,method="iaChannels")
            public static class iaChannels extends CommandRef {
                public static final iaChannels cmd = new iaChannels();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="index")
            public static class index extends CommandRef {
                public static final index cmd = new index();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.GrantPages.class,method="infraGrants")
            public static class infraGrants extends CommandRef {
                public static final infraGrants cmd = new infraGrants();
            public infraGrants nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.GrantPages.class,method="landGrants")
            public static class landGrants extends CommandRef {
                public static final landGrants cmd = new landGrants();
            public landGrants nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="login")
            public static class login extends CommandRef {
                public static final login cmd = new login();
            public login token(String value) {
                return set("token", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="login_mail")
            public static class login_mail extends CommandRef {
                public static final login_mail cmd = new login_mail();
            public login_mail nationId(String value) {
                return set("nationId", value);
            }

            public login_mail allianceId(String value) {
                return set("allianceId", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="logout")
            public static class logout extends CommandRef {
                public static final logout cmd = new logout();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.alliance.AlliancePages.class,method="manageAnnouncements")
            public static class manageAnnouncements extends CommandRef {
                public static final manageAnnouncements cmd = new manageAnnouncements();
            public manageAnnouncements showArchived(String value) {
                return set("showArchived", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IAPages.class,method="memberAuditIndex")
            public static class memberAuditIndex extends CommandRef {
                public static final memberAuditIndex cmd = new memberAuditIndex();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.BankPages.class,method="memberDeposits")
            public static class memberDeposits extends CommandRef {
                public static final memberDeposits cmd = new memberDeposits();
            public memberDeposits force(String value) {
                return set("force", value);
            }

            public memberDeposits noTaxBase(String value) {
                return set("noTaxBase", value);
            }

            public memberDeposits ignoreOffset(String value) {
                return set("ignoreOffset", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IAPages.class,method="mentorIndex")
            public static class mentorIndex extends CommandRef {
                public static final mentorIndex cmd = new mentorIndex();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.StatPages.class,method="metricByGroup")
            public static class metricByGroup extends CommandRef {
                public static final metricByGroup cmd = new metricByGroup();
            public metricByGroup metrics(String value) {
                return set("metrics", value);
            }

            public metricByGroup coalition(String value) {
                return set("coalition", value);
            }

            public metricByGroup groupBy(String value) {
                return set("groupBy", value);
            }

            public metricByGroup includeInactives(String value) {
                return set("includeInactives", value);
            }

            public metricByGroup includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public metricByGroup total(String value) {
                return set("total", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.GrantPages.class,method="projectGrants")
            public static class projectGrants extends CommandRef {
                public static final projectGrants cmd = new projectGrants();
            public projectGrants nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.StatPages.class,method="radiationStats")
            public static class radiationStats extends CommandRef {
                public static final radiationStats cmd = new radiationStats();
            public radiationStats continents(String value) {
                return set("continents", value);
            }

            public radiationStats start(String value) {
                return set("start", value);
            }

            public radiationStats end(String value) {
                return set("end", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="register")
            public static class register extends CommandRef {
                public static final register cmd = new register();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="search")
            public static class search extends CommandRef {
                public static final search cmd = new search();
            public search term(String value) {
                return set("term", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="setguild")
            public static class setguild extends CommandRef {
                public static final setguild cmd = new setguild();
            public setguild guild(String value) {
                return set("guild", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.EconPages.class,method="taxExpensesByTime")
            public static class taxExpensesByTime extends CommandRef {
                public static final taxExpensesByTime cmd = new taxExpensesByTime();
            public taxExpensesByTime start(String value) {
                return set("start", value);
            }

            public taxExpensesByTime end(String value) {
                return set("end", value);
            }

            public taxExpensesByTime nationFilter(String value) {
                return set("nationFilter", value);
            }

            public taxExpensesByTime movingAverageTurns(String value) {
                return set("movingAverageTurns", value);
            }

            public taxExpensesByTime cumulative(String value) {
                return set("cumulative", value);
            }

            public taxExpensesByTime dontRequireTagged(String value) {
                return set("dontRequireTagged", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.EconPages.class,method="taxExpensesIndex")
            public static class taxExpensesIndex extends CommandRef {
                public static final taxExpensesIndex cmd = new taxExpensesIndex();
            public taxExpensesIndex start(String value) {
                return set("start", value);
            }

            public taxExpensesIndex end(String value) {
                return set("end", value);
            }

            public taxExpensesIndex nationList(String value) {
                return set("nationList", value);
            }

            public taxExpensesIndex dontRequireGrant(String value) {
                return set("dontRequireGrant", value);
            }

            public taxExpensesIndex dontRequireTagged(String value) {
                return set("dontRequireTagged", value);
            }

            public taxExpensesIndex dontRequireExpiry(String value) {
                return set("dontRequireExpiry", value);
            }

            public taxExpensesIndex includeDeposits(String value) {
                return set("includeDeposits", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.TradePages.class,method="tradePrice")
            public static class tradePrice extends CommandRef {
                public static final tradePrice cmd = new tradePrice();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.TradePages.class,method="tradePriceByDay")
            public static class tradePriceByDay extends CommandRef {
                public static final tradePriceByDay cmd = new tradePriceByDay();
            public tradePriceByDay resources(String value) {
                return set("resources", value);
            }

            public tradePriceByDay days(String value) {
                return set("days", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.page.IndexPages.class,method="unregister")
            public static class unregister extends CommandRef {
                public static final unregister cmd = new unregister();

            }
        }

}
