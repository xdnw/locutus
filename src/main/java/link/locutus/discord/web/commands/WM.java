package link.locutus.discord.web.commands;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class WM {
        public static class api{
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="AlliancesDataByDay")
            public static class AlliancesDataByDay extends CommandRef {
                public static final AlliancesDataByDay cmd = new AlliancesDataByDay();
            public AlliancesDataByDay metric(String value) {
                return set("metric", value);
            }

            public AlliancesDataByDay start(String value) {
                return set("start", value);
            }

            public AlliancesDataByDay end(String value) {
                return set("end", value);
            }

            public AlliancesDataByDay mode(String value) {
                return set("mode", value);
            }

            public AlliancesDataByDay alliances(String value) {
                return set("alliances", value);
            }

            public AlliancesDataByDay filter(String value) {
                return set("filter", value);
            }

            public AlliancesDataByDay includeApps(String value) {
                return set("includeApps", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="NthBeigeLootByScoreRange")
            public static class NthBeigeLootByScoreRange extends CommandRef {
                public static final NthBeigeLootByScoreRange cmd = new NthBeigeLootByScoreRange();
            public NthBeigeLootByScoreRange nations(String value) {
                return set("nations", value);
            }

            public NthBeigeLootByScoreRange n(String value) {
                return set("n", value);
            }

            public NthBeigeLootByScoreRange snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RoleEndpoints.class,method="add_alliance_role")
            public static class add_alliance_role extends CommandRef {
                public static final add_alliance_role cmd = new add_alliance_role();
            public add_alliance_role alliance(String value) {
                return set("alliance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RoleEndpoints.class,method="add_city_role")
            public static class add_city_role extends CommandRef {
                public static final add_city_role cmd = new add_city_role();
            public add_city_role range(String value) {
                return set("range", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RoleEndpoints.class,method="add_tax_role")
            public static class add_tax_role extends CommandRef {
                public static final add_tax_role cmd = new add_tax_role();
            public add_tax_role rate(String value) {
                return set("rate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="allianceAttributeRanking")
            public static class allianceAttributeRanking extends CommandRef {
                public static final allianceAttributeRanking cmd = new allianceAttributeRanking();
            public allianceAttributeRanking attribute(String value) {
                return set("attribute", value);
            }

            public allianceAttributeRanking alliances(String value) {
                return set("alliances", value);
            }

            public allianceAttributeRanking reverseOrder(String value) {
                return set("reverseOrder", value);
            }

            public allianceAttributeRanking highlight(String value) {
                return set("highlight", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="allianceLootRanking")
            public static class allianceLootRanking extends CommandRef {
                public static final allianceLootRanking cmd = new allianceLootRanking();
            public allianceLootRanking time(String value) {
                return set("time", value);
            }

            public allianceLootRanking showTotal(String value) {
                return set("showTotal", value);
            }

            public allianceLootRanking minScore(String value) {
                return set("minScore", value);
            }

            public allianceLootRanking maxScore(String value) {
                return set("maxScore", value);
            }

            public allianceLootRanking highlight(String value) {
                return set("highlight", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="allianceMetricAB")
            public static class allianceMetricAB extends CommandRef {
                public static final allianceMetricAB cmd = new allianceMetricAB();
            public allianceMetricAB metric(String value) {
                return set("metric", value);
            }

            public allianceMetricAB coalition1(String value) {
                return set("coalition1", value);
            }

            public allianceMetricAB coalition2(String value) {
                return set("coalition2", value);
            }

            public allianceMetricAB start(String value) {
                return set("start", value);
            }

            public allianceMetricAB end(String value) {
                return set("end", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="allianceMetricByTurn")
            public static class allianceMetricByTurn extends CommandRef {
                public static final allianceMetricByTurn cmd = new allianceMetricByTurn();
            public allianceMetricByTurn metric(String value) {
                return set("metric", value);
            }

            public allianceMetricByTurn coalition(String value) {
                return set("coalition", value);
            }

            public allianceMetricByTurn start(String value) {
                return set("start", value);
            }

            public allianceMetricByTurn end(String value) {
                return set("end", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="allianceMetricDeltaRanking")
            public static class allianceMetricDeltaRanking extends CommandRef {
                public static final allianceMetricDeltaRanking cmd = new allianceMetricDeltaRanking();
            public allianceMetricDeltaRanking alliances(String value) {
                return set("alliances", value);
            }

            public allianceMetricDeltaRanking metric(String value) {
                return set("metric", value);
            }

            public allianceMetricDeltaRanking timeStart(String value) {
                return set("timeStart", value);
            }

            public allianceMetricDeltaRanking timeEnd(String value) {
                return set("timeEnd", value);
            }

            public allianceMetricDeltaRanking reverseOrder(String value) {
                return set("reverseOrder", value);
            }

            public allianceMetricDeltaRanking highlight(String value) {
                return set("highlight", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="allianceMetricRanking")
            public static class allianceMetricRanking extends CommandRef {
                public static final allianceMetricRanking cmd = new allianceMetricRanking();
            public allianceMetricRanking metric(String value) {
                return set("metric", value);
            }

            public allianceMetricRanking alliances(String value) {
                return set("alliances", value);
            }

            public allianceMetricRanking reverseOrder(String value) {
                return set("reverseOrder", value);
            }

            public allianceMetricRanking highlight(String value) {
                return set("highlight", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="allianceStats")
            public static class allianceStats extends CommandRef {
                public static final allianceStats cmd = new allianceStats();
            public allianceStats metrics(String value) {
                return set("metrics", value);
            }

            public allianceStats start(String value) {
                return set("start", value);
            }

            public allianceStats end(String value) {
                return set("end", value);
            }

            public allianceStats coalition(String value) {
                return set("coalition", value);
            }

            }
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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="attackTypeRanking")
            public static class attackTypeRanking extends CommandRef {
                public static final attackTypeRanking cmd = new attackTypeRanking();
            public attackTypeRanking time(String value) {
                return set("time", value);
            }

            public attackTypeRanking type(String value) {
                return set("type", value);
            }

            public attackTypeRanking alliances(String value) {
                return set("alliances", value);
            }

            public attackTypeRanking only_top_x(String value) {
                return set("only_top_x", value);
            }

            public attackTypeRanking percent(String value) {
                return set("percent", value);
            }

            public attackTypeRanking only_off_wars(String value) {
                return set("only_off_wars", value);
            }

            public attackTypeRanking only_def_wars(String value) {
                return set("only_def_wars", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="autorole")
            public static class autorole extends CommandRef {
                public static final autorole cmd = new autorole();
            public autorole member(String value) {
                return set("member", value);
            }

            public autorole force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="autoroleall")
            public static class autoroleall extends CommandRef {
                public static final autoroleall cmd = new autoroleall();
            public autoroleall force(String value) {
                return set("force", value);
            }

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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.WarEndpoints.class,method="bounty")
            public static class bounty extends CommandRef {
                public static final bounty cmd = new bounty();
            public bounty nation(String value) {
                return set("nation", value);
            }

            public bounty onlyWeaker(String value) {
                return set("onlyWeaker", value);
            }

            public bounty ignoreDNR(String value) {
                return set("ignoreDNR", value);
            }

            public bounty bountyTypes(String value) {
                return set("bountyTypes", value);
            }

            public bounty numResults(String value) {
                return set("numResults", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="cityTierGraph")
            public static class cityTierGraph extends CommandRef {
                public static final cityTierGraph cmd = new cityTierGraph();
            public cityTierGraph coalition1(String value) {
                return set("coalition1", value);
            }

            public cityTierGraph coalition2(String value) {
                return set("coalition2", value);
            }

            public cityTierGraph includeInactives(String value) {
                return set("includeInactives", value);
            }

            public cityTierGraph barGraph(String value) {
                return set("barGraph", value);
            }

            public cityTierGraph includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public cityTierGraph snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="command")
            public static class command extends CommandRef {
                public static final command cmd = new command();
            public command data(String value) {
                return set("data", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="compareStats")
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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="compareStockpileValueByDay")
            public static class compareStockpileValueByDay extends CommandRef {
                public static final compareStockpileValueByDay cmd = new compareStockpileValueByDay();
            public compareStockpileValueByDay stockpile1(String value) {
                return set("stockpile1", value);
            }

            public compareStockpileValueByDay stockpile2(String value) {
                return set("stockpile2", value);
            }

            public compareStockpileValueByDay numDays(String value) {
                return set("numDays", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="compareTierDeltaGraph")
            public static class compareTierDeltaGraph extends CommandRef {
                public static final compareTierDeltaGraph cmd = new compareTierDeltaGraph();
            public compareTierDeltaGraph coalition1(String value) {
                return set("coalition1", value);
            }

            public compareTierDeltaGraph coalition2(String value) {
                return set("coalition2", value);
            }

            public compareTierDeltaGraph coalition3(String value) {
                return set("coalition3", value);
            }

            public compareTierDeltaGraph coalition4(String value) {
                return set("coalition4", value);
            }

            public compareTierDeltaGraph coalition5(String value) {
                return set("coalition5", value);
            }

            public compareTierDeltaGraph coalition6(String value) {
                return set("coalition6", value);
            }

            public compareTierDeltaGraph coalition7(String value) {
                return set("coalition7", value);
            }

            public compareTierDeltaGraph coalition8(String value) {
                return set("coalition8", value);
            }

            public compareTierDeltaGraph coalition9(String value) {
                return set("coalition9", value);
            }

            public compareTierDeltaGraph coalition10(String value) {
                return set("coalition10", value);
            }

            public compareTierDeltaGraph stat(String value) {
                return set("stat", value);
            }

            public compareTierDeltaGraph start(String value) {
                return set("start", value);
            }

            public compareTierDeltaGraph end(String value) {
                return set("end", value);
            }

            public compareTierDeltaGraph mode(String value) {
                return set("mode", value);
            }

            public compareTierDeltaGraph bucket_size(String value) {
                return set("bucket_size", value);
            }

            public compareTierDeltaGraph include_apps(String value) {
                return set("include_apps", value);
            }

            public compareTierDeltaGraph include_vm(String value) {
                return set("include_vm", value);
            }

            public compareTierDeltaGraph filter(String value) {
                return set("filter", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="compareTierStats")
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

            public compareTierStats includeApps(String value) {
                return set("includeApps", value);
            }

            public compareTierStats includeVm(String value) {
                return set("includeVm", value);
            }

            public compareTierStats includeInactive(String value) {
                return set("includeInactive", value);
            }

            public compareTierStats snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.ConflictEndpoints.class,method="conflictAlliances")
            public static class conflictAlliances extends CommandRef {
                public static final conflictAlliances cmd = new conflictAlliances();
            public conflictAlliances conflicts(String value) {
                return set("conflicts", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.ConflictEndpoints.class,method="conflictPosts")
            public static class conflictPosts extends CommandRef {
                public static final conflictPosts cmd = new conflictPosts();
            public conflictPosts conflicts(String value) {
                return set("conflicts", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TreatyEndpoints.class,method="current_treaties")
            public static class current_treaties extends CommandRef {
                public static final current_treaties cmd = new current_treaties();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.WarEndpoints.class,method="damage")
            public static class damage extends CommandRef {
                public static final damage cmd = new damage();
            public damage nation(String value) {
                return set("nation", value);
            }

            public damage nations(String value) {
                return set("nations", value);
            }

            public damage includeApps(String value) {
                return set("includeApps", value);
            }

            public damage includeInactives(String value) {
                return set("includeInactives", value);
            }

            public damage filterWeak(String value) {
                return set("filterWeak", value);
            }

            public damage noNavy(String value) {
                return set("noNavy", value);
            }

            public damage targetMeanInfra(String value) {
                return set("targetMeanInfra", value);
            }

            public damage targetCityMax(String value) {
                return set("targetCityMax", value);
            }

            public damage targetBeigeMax(String value) {
                return set("targetBeigeMax", value);
            }

            public damage includeBeige(String value) {
                return set("includeBeige", value);
            }

            public damage relativeNavalStrength(String value) {
                return set("relativeNavalStrength", value);
            }

            public damage warRange(String value) {
                return set("warRange", value);
            }

            public damage numResults(String value) {
                return set("numResults", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.CoalitionGraphEndpoints.class,method="globalStats")
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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.CoalitionGraphEndpoints.class,method="globalTierStats")
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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="input_options")
            public static class input_options extends CommandRef {
                public static final input_options cmd = new input_options();
            public input_options type(String value) {
                return set("type", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RoleEndpoints.class,method="list_autorole_roles")
            public static class list_autorole_roles extends CommandRef {
                public static final list_autorole_roles cmd = new list_autorole_roles();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.CoalitionEndpoints.class,method="list_coalitions")
            public static class list_coalitions extends CommandRef {
                public static final list_coalitions cmd = new list_coalitions();
            public list_coalitions filter(String value) {
                return set("filter", value);
            }

            public list_coalitions ignoreDeleted(String value) {
                return set("ignoreDeleted", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RoleEndpoints.class,method="list_role_aliases")
            public static class list_role_aliases extends CommandRef {
                public static final list_role_aliases cmd = new list_role_aliases();
            public list_role_aliases roles_filter(String value) {
                return set("roles_filter", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.AdminEndpoints.class,method="locutus_task")
            public static class locutus_task extends CommandRef {
                public static final locutus_task cmd = new locutus_task();
            public locutus_task id(String value) {
                return set("id", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.AdminEndpoints.class,method="locutus_tasks")
            public static class locutus_tasks extends CommandRef {
                public static final locutus_tasks cmd = new locutus_tasks();

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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="metricByGroup")
            public static class metricByGroup extends CommandRef {
                public static final metricByGroup cmd = new metricByGroup();
            public metricByGroup metrics(String value) {
                return set("metrics", value);
            }

            public metricByGroup nations(String value) {
                return set("nations", value);
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

            public metricByGroup snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="metric_compare_by_turn")
            public static class metric_compare_by_turn extends CommandRef {
                public static final metric_compare_by_turn cmd = new metric_compare_by_turn();
            public metric_compare_by_turn metric(String value) {
                return set("metric", value);
            }

            public metric_compare_by_turn alliances(String value) {
                return set("alliances", value);
            }

            public metric_compare_by_turn start(String value) {
                return set("start", value);
            }

            public metric_compare_by_turn end(String value) {
                return set("end", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="militarizationTime")
            public static class militarizationTime extends CommandRef {
                public static final militarizationTime cmd = new militarizationTime();
            public militarizationTime alliance(String value) {
                return set("alliance", value);
            }

            public militarizationTime start_time(String value) {
                return set("start_time", value);
            }

            public militarizationTime end_time(String value) {
                return set("end_time", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.MultiEndpoints.class,method="multi_buster")
            public static class multi_buster extends CommandRef {
                public static final multi_buster cmd = new multi_buster();
            public multi_buster nation(String value) {
                return set("nation", value);
            }

            public multi_buster forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.MultiEndpoints.class,method="multi_v2")
            public static class multi_v2 extends CommandRef {
                public static final multi_v2 cmd = new multi_v2();
            public multi_v2 nation(String value) {
                return set("nation", value);
            }

            public multi_v2 forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="my_audits")
            public static class my_audits extends CommandRef {
                public static final my_audits cmd = new my_audits();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="my_wars")
            public static class my_wars extends CommandRef {
                public static final my_wars cmd = new my_wars();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="nationAttributeRanking")
            public static class nationAttributeRanking extends CommandRef {
                public static final nationAttributeRanking cmd = new nationAttributeRanking();
            public nationAttributeRanking nations(String value) {
                return set("nations", value);
            }

            public nationAttributeRanking attribute(String value) {
                return set("attribute", value);
            }

            public nationAttributeRanking groupByAlliance(String value) {
                return set("groupByAlliance", value);
            }

            public nationAttributeRanking reverseOrder(String value) {
                return set("reverseOrder", value);
            }

            public nationAttributeRanking snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public nationAttributeRanking total(String value) {
                return set("total", value);
            }

            public nationAttributeRanking title(String value) {
                return set("title", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="orbisStatByDay")
            public static class orbisStatByDay extends CommandRef {
                public static final orbisStatByDay cmd = new orbisStatByDay();
            public orbisStatByDay metrics(String value) {
                return set("metrics", value);
            }

            public orbisStatByDay start(String value) {
                return set("start", value);
            }

            public orbisStatByDay end(String value) {
                return set("end", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="permission")
            public static class permission extends CommandRef {
                public static final permission cmd = new permission();
            public permission command(String value) {
                return set("command", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="producerRanking")
            public static class producerRanking extends CommandRef {
                public static final producerRanking cmd = new producerRanking();
            public producerRanking resources(String value) {
                return set("resources", value);
            }

            public producerRanking nationList(String value) {
                return set("nationList", value);
            }

            public producerRanking ignoreMilitaryUpkeep(String value) {
                return set("ignoreMilitaryUpkeep", value);
            }

            public producerRanking ignoreTradeBonus(String value) {
                return set("ignoreTradeBonus", value);
            }

            public producerRanking ignoreNationBonus(String value) {
                return set("ignoreNationBonus", value);
            }

            public producerRanking includeNegative(String value) {
                return set("includeNegative", value);
            }

            public producerRanking includeInactive(String value) {
                return set("includeInactive", value);
            }

            public producerRanking listByNation(String value) {
                return set("listByNation", value);
            }

            public producerRanking listAverage(String value) {
                return set("listAverage", value);
            }

            public producerRanking snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public producerRanking highlight(String value) {
                return set("highlight", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="query")
            public static class query extends CommandRef {
                public static final query cmd = new query();
            public query queries(String value) {
                return set("queries", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="radiationByTurn")
            public static class radiationByTurn extends CommandRef {
                public static final radiationByTurn cmd = new radiationByTurn();
            public radiationByTurn continents(String value) {
                return set("continents", value);
            }

            public radiationByTurn start(String value) {
                return set("start", value);
            }

            public radiationByTurn end(String value) {
                return set("end", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.WarEndpoints.class,method="raid")
            public static class raid extends CommandRef {
                public static final raid cmd = new raid();
            public raid nation(String value) {
                return set("nation", value);
            }

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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="recruitmentRanking")
            public static class recruitmentRanking extends CommandRef {
                public static final recruitmentRanking cmd = new recruitmentRanking();
            public recruitmentRanking cutoff(String value) {
                return set("cutoff", value);
            }

            public recruitmentRanking topX(String value) {
                return set("topX", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="register")
            public static class register extends CommandRef {
                public static final register cmd = new register();
            public register confirm(String value) {
                return set("confirm", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.ConflictEndpoints.class,method="removeVirtualConflict")
            public static class removeVirtualConflict extends CommandRef {
                public static final removeVirtualConflict cmd = new removeVirtualConflict();
            public removeVirtualConflict conflict(String value) {
                return set("conflict", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RoleEndpoints.class,method="remove_alliance_role")
            public static class remove_alliance_role extends CommandRef {
                public static final remove_alliance_role cmd = new remove_alliance_role();
            public remove_alliance_role alliance(String value) {
                return set("alliance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RoleEndpoints.class,method="remove_city_role")
            public static class remove_city_role extends CommandRef {
                public static final remove_city_role cmd = new remove_city_role();
            public remove_city_role range(String value) {
                return set("range", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RoleEndpoints.class,method="remove_tax_role")
            public static class remove_tax_role extends CommandRef {
                public static final remove_tax_role cmd = new remove_tax_role();
            public remove_tax_role rate(String value) {
                return set("rate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="scoreTierGraph")
            public static class scoreTierGraph extends CommandRef {
                public static final scoreTierGraph cmd = new scoreTierGraph();
            public scoreTierGraph coalition1(String value) {
                return set("coalition1", value);
            }

            public scoreTierGraph coalition2(String value) {
                return set("coalition2", value);
            }

            public scoreTierGraph includeInactives(String value) {
                return set("includeInactives", value);
            }

            public scoreTierGraph includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public scoreTierGraph snapshotDate(String value) {
                return set("snapshotDate", value);
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

            public set_token guild_id(String value) {
                return set("guild_id", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.AdminEndpoints.class,method="settings_validation_cheapness")
            public static class settings_validation_cheapness extends CommandRef {
                public static final settings_validation_cheapness cmd = new settings_validation_cheapness();
            public settings_validation_cheapness settings(String value) {
                return set("settings", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.AdminEndpoints.class,method="settings_validation_errors")
            public static class settings_validation_errors extends CommandRef {
                public static final settings_validation_errors cmd = new settings_validation_errors();
            public settings_validation_errors settings(String value) {
                return set("settings", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="spyTierGraph")
            public static class spyTierGraph extends CommandRef {
                public static final spyTierGraph cmd = new spyTierGraph();
            public spyTierGraph coalition1(String value) {
                return set("coalition1", value);
            }

            public spyTierGraph coalition2(String value) {
                return set("coalition2", value);
            }

            public spyTierGraph includeInactives(String value) {
                return set("includeInactives", value);
            }

            public spyTierGraph includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public spyTierGraph total(String value) {
                return set("total", value);
            }

            public spyTierGraph barGraph(String value) {
                return set("barGraph", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="strengthTierGraph")
            public static class strengthTierGraph extends CommandRef {
                public static final strengthTierGraph cmd = new strengthTierGraph();
            public strengthTierGraph coalition1(String value) {
                return set("coalition1", value);
            }

            public strengthTierGraph coalition2(String value) {
                return set("coalition2", value);
            }

            public strengthTierGraph includeInactives(String value) {
                return set("includeInactives", value);
            }

            public strengthTierGraph includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public strengthTierGraph col1MMR(String value) {
                return set("col1MMR", value);
            }

            public strengthTierGraph col2MMR(String value) {
                return set("col2MMR", value);
            }

            public strengthTierGraph col1Infra(String value) {
                return set("col1Infra", value);
            }

            public strengthTierGraph col2Infra(String value) {
                return set("col2Infra", value);
            }

            public strengthTierGraph snapshotDate(String value) {
                return set("snapshotDate", value);
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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TaxEndpoints.class,method="tax_expense")
            public static class tax_expense extends CommandRef {
                public static final tax_expense cmd = new tax_expense();
            public tax_expense start(String value) {
                return set("start", value);
            }

            public tax_expense end(String value) {
                return set("end", value);
            }

            public tax_expense nationList(String value) {
                return set("nationList", value);
            }

            public tax_expense dontRequireGrant(String value) {
                return set("dontRequireGrant", value);
            }

            public tax_expense dontRequireTagged(String value) {
                return set("dontRequireTagged", value);
            }

            public tax_expense dontRequireExpiry(String value) {
                return set("dontRequireExpiry", value);
            }

            public tax_expense includeDeposits(String value) {
                return set("includeDeposits", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TaxEndpoints.class,method="tax_expense_bracket_rows")
            public static class tax_expense_bracket_rows extends CommandRef {
                public static final tax_expense_bracket_rows cmd = new tax_expense_bracket_rows();
            public tax_expense_bracket_rows datasetId(String value) {
                return set("datasetId", value);
            }

            public tax_expense_bracket_rows taxId(String value) {
                return set("taxId", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TaxEndpoints.class,method="tax_expense_by_time")
            public static class tax_expense_by_time extends CommandRef {
                public static final tax_expense_by_time cmd = new tax_expense_by_time();
            public tax_expense_by_time datasetId(String value) {
                return set("datasetId", value);
            }

            public tax_expense_by_time start(String value) {
                return set("start", value);
            }

            public tax_expense_by_time end(String value) {
                return set("end", value);
            }

            public tax_expense_by_time nationFilter(String value) {
                return set("nationFilter", value);
            }

            public tax_expense_by_time dontRequireGrant(String value) {
                return set("dontRequireGrant", value);
            }

            public tax_expense_by_time dontRequireTagged(String value) {
                return set("dontRequireTagged", value);
            }

            public tax_expense_by_time dontRequireExpiry(String value) {
                return set("dontRequireExpiry", value);
            }

            public tax_expense_by_time includeDeposits(String value) {
                return set("includeDeposits", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TaxEndpoints.class,method="tax_expense_by_time_bracket")
            public static class tax_expense_by_time_bracket extends CommandRef {
                public static final tax_expense_by_time_bracket cmd = new tax_expense_by_time_bracket();
            public tax_expense_by_time_bracket datasetId(String value) {
                return set("datasetId", value);
            }

            public tax_expense_by_time_bracket taxId(String value) {
                return set("taxId", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TaxEndpoints.class,method="tax_expense_by_time_resources")
            public static class tax_expense_by_time_resources extends CommandRef {
                public static final tax_expense_by_time_resources cmd = new tax_expense_by_time_resources();
            public tax_expense_by_time_resources datasetId(String value) {
                return set("datasetId", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TaxEndpoints.class,method="tax_expense_nation")
            public static class tax_expense_nation extends CommandRef {
                public static final tax_expense_nation cmd = new tax_expense_nation();
            public tax_expense_nation datasetId(String value) {
                return set("datasetId", value);
            }

            public tax_expense_nation taxId(String value) {
                return set("taxId", value);
            }

            public tax_expense_nation nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="tradeMarginByDay")
            public static class tradeMarginByDay extends CommandRef {
                public static final tradeMarginByDay cmd = new tradeMarginByDay();
            public tradeMarginByDay resources(String value) {
                return set("resources", value);
            }

            public tradeMarginByDay start(String value) {
                return set("start", value);
            }

            public tradeMarginByDay end(String value) {
                return set("end", value);
            }

            public tradeMarginByDay percent(String value) {
                return set("percent", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="tradePriceByDay")
            public static class tradePriceByDay extends CommandRef {
                public static final tradePriceByDay cmd = new tradePriceByDay();
            public tradePriceByDay resources(String value) {
                return set("resources", value);
            }

            public tradePriceByDay numDays(String value) {
                return set("numDays", value);
            }

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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="tradeTotalByDay")
            public static class tradeTotalByDay extends CommandRef {
                public static final tradeTotalByDay cmd = new tradeTotalByDay();
            public tradeTotalByDay resource(String value) {
                return set("resource", value);
            }

            public tradeTotalByDay start(String value) {
                return set("start", value);
            }

            public tradeTotalByDay end(String value) {
                return set("end", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="tradeVolumeByDay")
            public static class tradeVolumeByDay extends CommandRef {
                public static final tradeVolumeByDay cmd = new tradeVolumeByDay();
            public tradeVolumeByDay resource(String value) {
                return set("resource", value);
            }

            public tradeVolumeByDay start(String value) {
                return set("start", value);
            }

            public tradeVolumeByDay end(String value) {
                return set("end", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.WarEndpoints.class,method="treasure")
            public static class treasure extends CommandRef {
                public static final treasure cmd = new treasure();
            public treasure nation(String value) {
                return set("nation", value);
            }

            public treasure onlyWeaker(String value) {
                return set("onlyWeaker", value);
            }

            public treasure ignoreDNR(String value) {
                return set("ignoreDNR", value);
            }

            public treasure numResults(String value) {
                return set("numResults", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.TreatyEndpoints.class,method="treaty_changes")
            public static class treaty_changes extends CommandRef {
                public static final treaty_changes cmd = new treaty_changes();
            public treaty_changes start(String value) {
                return set("start", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.WarEndpoints.class,method="unprotected")
            public static class unprotected extends CommandRef {
                public static final unprotected cmd = new unprotected();
            public unprotected nation(String value) {
                return set("nation", value);
            }

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
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.EndpointPages.class,method="unset_guild")
            public static class unset_guild extends CommandRef {
                public static final unset_guild cmd = new unset_guild();

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.IAEndpoints.class,method="view_announcement")
            public static class view_announcement extends CommandRef {
                public static final view_announcement cmd = new view_announcement();
            public view_announcement ann_id(String value) {
                return set("ann_id", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.ConflictEndpoints.class,method="virtualConflictInfo")
            public static class virtualConflictInfo extends CommandRef {
                public static final virtualConflictInfo cmd = new virtualConflictInfo();
            public virtualConflictInfo conflict(String value) {
                return set("conflict", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.ConflictEndpoints.class,method="virtualConflicts")
            public static class virtualConflicts extends CommandRef {
                public static final virtualConflicts cmd = new virtualConflicts();
            public virtualConflicts all(String value) {
                return set("all", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.WarEndpoints.class,method="war")
            public static class war extends CommandRef {
                public static final war cmd = new war();
            public war nation(String value) {
                return set("nation", value);
            }

            public war nations(String value) {
                return set("nations", value);
            }

            public war numResults(String value) {
                return set("numResults", value);
            }

            public war attackerScore(String value) {
                return set("attackerScore", value);
            }

            public war includeInactives(String value) {
                return set("includeInactives", value);
            }

            public war includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public war onlyPriority(String value) {
                return set("onlyPriority", value);
            }

            public war onlyWeak(String value) {
                return set("onlyWeak", value);
            }

            public war onlyEasy(String value) {
                return set("onlyEasy", value);
            }

            public war onlyLessCities(String value) {
                return set("onlyLessCities", value);
            }

            public war includeStrong(String value) {
                return set("includeStrong", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="warAttacksByDay")
            public static class warAttacksByDay extends CommandRef {
                public static final warAttacksByDay cmd = new warAttacksByDay();
            public warAttacksByDay nations(String value) {
                return set("nations", value);
            }

            public warAttacksByDay cutoff(String value) {
                return set("cutoff", value);
            }

            public warAttacksByDay allowedTypes(String value) {
                return set("allowedTypes", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="warCostRanking")
            public static class warCostRanking extends CommandRef {
                public static final warCostRanking cmd = new warCostRanking();
            public warCostRanking timeStart(String value) {
                return set("timeStart", value);
            }

            public warCostRanking timeEnd(String value) {
                return set("timeEnd", value);
            }

            public warCostRanking coalition1(String value) {
                return set("coalition1", value);
            }

            public warCostRanking coalition2(String value) {
                return set("coalition2", value);
            }

            public warCostRanking onlyRankCoalition1(String value) {
                return set("onlyRankCoalition1", value);
            }

            public warCostRanking type(String value) {
                return set("type", value);
            }

            public warCostRanking stat(String value) {
                return set("stat", value);
            }

            public warCostRanking excludeInfra(String value) {
                return set("excludeInfra", value);
            }

            public warCostRanking excludeConsumption(String value) {
                return set("excludeConsumption", value);
            }

            public warCostRanking excludeLoot(String value) {
                return set("excludeLoot", value);
            }

            public warCostRanking excludeBuildings(String value) {
                return set("excludeBuildings", value);
            }

            public warCostRanking excludeUnits(String value) {
                return set("excludeUnits", value);
            }

            public warCostRanking groupByAlliance(String value) {
                return set("groupByAlliance", value);
            }

            public warCostRanking scalePerWar(String value) {
                return set("scalePerWar", value);
            }

            public warCostRanking scalePerCity(String value) {
                return set("scalePerCity", value);
            }

            public warCostRanking allowedWarTypes(String value) {
                return set("allowedWarTypes", value);
            }

            public warCostRanking allowedWarStatuses(String value) {
                return set("allowedWarStatuses", value);
            }

            public warCostRanking allowedAttacks(String value) {
                return set("allowedAttacks", value);
            }

            public warCostRanking onlyOffensiveWars(String value) {
                return set("onlyOffensiveWars", value);
            }

            public warCostRanking onlyDefensiveWars(String value) {
                return set("onlyDefensiveWars", value);
            }

            public warCostRanking highlight(String value) {
                return set("highlight", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="warCostsByDay")
            public static class warCostsByDay extends CommandRef {
                public static final warCostsByDay cmd = new warCostsByDay();
            public warCostsByDay coalition1(String value) {
                return set("coalition1", value);
            }

            public warCostsByDay coalition2(String value) {
                return set("coalition2", value);
            }

            public warCostsByDay type(String value) {
                return set("type", value);
            }

            public warCostsByDay time_start(String value) {
                return set("time_start", value);
            }

            public warCostsByDay time_end(String value) {
                return set("time_end", value);
            }

            public warCostsByDay running_total(String value) {
                return set("running_total", value);
            }

            public warCostsByDay allowedWarStatus(String value) {
                return set("allowedWarStatus", value);
            }

            public warCostsByDay allowedWarTypes(String value) {
                return set("allowedWarTypes", value);
            }

            public warCostsByDay allowedAttackTypes(String value) {
                return set("allowedAttackTypes", value);
            }

            public warCostsByDay allowedVictoryTypes(String value) {
                return set("allowedVictoryTypes", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="warRanking")
            public static class warRanking extends CommandRef {
                public static final warRanking cmd = new warRanking();
            public warRanking attackers(String value) {
                return set("attackers", value);
            }

            public warRanking defenders(String value) {
                return set("defenders", value);
            }

            public warRanking time(String value) {
                return set("time", value);
            }

            public warRanking onlyOffensives(String value) {
                return set("onlyOffensives", value);
            }

            public warRanking onlyDefensives(String value) {
                return set("onlyDefensives", value);
            }

            public warRanking only_rank_attackers(String value) {
                return set("only_rank_attackers", value);
            }

            public warRanking normalizePerMember(String value) {
                return set("normalizePerMember", value);
            }

            public warRanking ignore2dInactives(String value) {
                return set("ignore2dInactives", value);
            }

            public warRanking rankByNation(String value) {
                return set("rankByNation", value);
            }

            public warRanking warType(String value) {
                return set("warType", value);
            }

            public warRanking statuses(String value) {
                return set("statuses", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="warStatusRankingByAlliance")
            public static class warStatusRankingByAlliance extends CommandRef {
                public static final warStatusRankingByAlliance cmd = new warStatusRankingByAlliance();
            public warStatusRankingByAlliance attackers(String value) {
                return set("attackers", value);
            }

            public warStatusRankingByAlliance defenders(String value) {
                return set("defenders", value);
            }

            public warStatusRankingByAlliance time(String value) {
                return set("time", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.RankingEndpoints.class,method="warStatusRankingByNation")
            public static class warStatusRankingByNation extends CommandRef {
                public static final warStatusRankingByNation cmd = new warStatusRankingByNation();
            public warStatusRankingByNation attackers(String value) {
                return set("attackers", value);
            }

            public warStatusRankingByNation defenders(String value) {
                return set("defenders", value);
            }

            public warStatusRankingByNation time(String value) {
                return set("time", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.api.GraphEndpoints.class,method="warsCostRankingByDay")
            public static class warsCostRankingByDay extends CommandRef {
                public static final warsCostRankingByDay cmd = new warsCostRankingByDay();
            public warsCostRankingByDay type(String value) {
                return set("type", value);
            }

            public warsCostRankingByDay mode(String value) {
                return set("mode", value);
            }

            public warsCostRankingByDay time_start(String value) {
                return set("time_start", value);
            }

            public warsCostRankingByDay time_end(String value) {
                return set("time_end", value);
            }

            public warsCostRankingByDay coalition1(String value) {
                return set("coalition1", value);
            }

            public warsCostRankingByDay coalition2(String value) {
                return set("coalition2", value);
            }

            public warsCostRankingByDay coalition3(String value) {
                return set("coalition3", value);
            }

            public warsCostRankingByDay coalition4(String value) {
                return set("coalition4", value);
            }

            public warsCostRankingByDay coalition5(String value) {
                return set("coalition5", value);
            }

            public warsCostRankingByDay coalition6(String value) {
                return set("coalition6", value);
            }

            public warsCostRankingByDay coalition7(String value) {
                return set("coalition7", value);
            }

            public warsCostRankingByDay coalition8(String value) {
                return set("coalition8", value);
            }

            public warsCostRankingByDay coalition9(String value) {
                return set("coalition9", value);
            }

            public warsCostRankingByDay coalition10(String value) {
                return set("coalition10", value);
            }

            public warsCostRankingByDay running_total(String value) {
                return set("running_total", value);
            }

            public warsCostRankingByDay allowedWarStatus(String value) {
                return set("allowedWarStatus", value);
            }

            public warsCostRankingByDay allowedWarTypes(String value) {
                return set("allowedWarTypes", value);
            }

            public warsCostRankingByDay allowedAttackTypes(String value) {
                return set("allowedAttackTypes", value);
            }

            public warsCostRankingByDay allowedVictoryTypes(String value) {
                return set("allowedVictoryTypes", value);
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
