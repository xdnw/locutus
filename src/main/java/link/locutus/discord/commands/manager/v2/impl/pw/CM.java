package link.locutus.discord.commands.manager.v2.impl.pw;

import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;

public class CM {
    public static class offshore{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="markAsOffshore")
        public static class markAsOffshore extends CommandRef {
            public static final markAsOffshore cmd = new markAsOffshore();
            public markAsOffshore create(String offshore, String parent) {
                return createArgs("offshore", offshore, "parent", parent);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="addOffshore")
        public static class add extends CommandRef {
            public static final add cmd = new add();
            public add create(String offshoreAlliance, String force) {
                return createArgs("offshoreAlliance", offshoreAlliance, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="findOffshores")
        public static class find extends CommandRef {
            public static final find cmd = new find();
            public find create(String cutoff, String enemiesList, String alliesList) {
                return createArgs("cutoff", cutoff, "enemiesList", enemiesList, "alliesList", alliesList);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="listOffshores")
        public static class listAllInOrbis extends CommandRef {
            public static final listAllInOrbis cmd = new listAllInOrbis();
            public listAllInOrbis create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="unlockTransfers")
        public static class unlockTransfers extends CommandRef {
            public static final unlockTransfers cmd = new unlockTransfers();
            public unlockTransfers create(String alliance) {
                return createArgs("alliance", alliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="findOffshore")
        public static class findForCoalition extends CommandRef {
            public static final findForCoalition cmd = new findForCoalition();
            public findForCoalition create(String alliance, String cutoffMs) {
                return createArgs("alliance", alliance, "cutoffMs", cutoffMs);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="offshore")
        public static class send extends CommandRef {
            public static final send cmd = new send();
            public send create(String to, String warchest, String account) {
                return createArgs("to", to, "warchest", warchest, "account", account);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="compareOffshoreStockpile")
        public static class compareStockpile extends CommandRef {
            public static final compareStockpile cmd = new compareStockpile();
            public compareStockpile create() {
                return createArgs();
            }
        }
    }
    @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="register")
    public static class register extends CommandRef {
        public static final register cmd = new register();
        public register create(String nation) {
            return createArgs("nation", nation);
        }
    }
    public static class web{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grants")
        public static class grants extends CommandRef {
            public static final grants cmd = new grants();
            public grants create(String nation) {
                return createArgs("nation", nation);
            }
        }
    }
    public static class bank{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="unlockTransfers")
        public static class unlockTransfers extends CommandRef {
            public static final unlockTransfers cmd = new unlockTransfers();
            public unlockTransfers create(String alliance) {
                return createArgs("alliance", alliance);
            }
        }
        public static class stats{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="inflows")
            public static class inflows extends CommandRef {
                public static final inflows cmd = new inflows();
                public inflows create(String nationOrAlliances, String cutoffMs, String hideInflows, String hideOutflows) {
                    return createArgs("nationOrAlliances", nationOrAlliances, "cutoffMs", cutoffMs, "hideInflows", hideInflows, "hideOutflows", hideOutflows);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="weeklyInterest")
            public static class weeklyInterest extends CommandRef {
                public static final weeklyInterest cmd = new weeklyInterest();
                public weeklyInterest create(String amount, String pct, String weeks) {
                    return createArgs("amount", amount, "pct", pct, "weeks", weeks);
                }
            }
        }
        public static class limits{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setTransferLimit")
            public static class setTransferLimit extends CommandRef {
                public static final setTransferLimit cmd = new setTransferLimit();
                public setTransferLimit create(String nations, String limit) {
                    return createArgs("nations", nations, "limit", limit);
                }
            }
        }
        public static class escrow{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="escrowDisburse")
            public static class disburse extends CommandRef {
                public static final disburse cmd = new disburse();
                public disburse create(String receiver, String days, String expireAfter) {
                    return createArgs("receiver", receiver, "days", days, "expireAfter", expireAfter);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="approveEscrowed")
            public static class approve extends CommandRef {
                public static final approve cmd = new approve();
                public approve create(String receiver, String deposits, String escrowed) {
                    return createArgs("receiver", receiver, "deposits", deposits, "escrowed", escrowed);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="escrow")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String receiver, String resources, String expireAfter, String topUp) {
                    return createArgs("receiver", receiver, "resources", resources, "expireAfter", expireAfter, "topUp", topUp);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transactions")
        public static class records extends CommandRef {
            public static final records cmd = new records();
            public records create(String nationOrAllianceOrGuild, String timeframe, String useTaxBase, String useOffset, String sheet, String onlyOffshoreTransfers) {
                return createArgs("nationOrAllianceOrGuild", nationOrAllianceOrGuild, "timeframe", timeframe, "useTaxBase", useTaxBase, "useOffset", useOffset, "sheet", sheet, "onlyOffshoreTransfers", onlyOffshoreTransfers);
            }
        }
    }
    public static class fun{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="joke")
        public static class joke extends CommandRef {
            public static final joke cmd = new joke();
            public joke create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="say")
        public static class say extends CommandRef {
            public static final say cmd = new say();
            public say create(String msg) {
                return createArgs("msg", msg);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="borgmas")
        public static class borgmas extends CommandRef {
            public static final borgmas cmd = new borgmas();
            public borgmas create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="borg")
        public static class borg extends CommandRef {
            public static final borg cmd = new borg();
            public borg create(String msg) {
                return createArgs("msg", msg);
            }
        }
    }
    public static class baseball{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballEarningsRanking")
        public static class baseballEarningsRanking extends CommandRef {
            public static final baseballEarningsRanking cmd = new baseballEarningsRanking();
            public baseballEarningsRanking create(String date, String uploadFile, String byAlliance) {
                return createArgs("date", date, "uploadFile", uploadFile, "byAlliance", byAlliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballChallengeRanking")
        public static class baseballChallengeRanking extends CommandRef {
            public static final baseballChallengeRanking cmd = new baseballChallengeRanking();
            public baseballChallengeRanking create(String uploadFile, String byAlliance) {
                return createArgs("uploadFile", uploadFile, "byAlliance", byAlliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseBallChallengeInflow")
        public static class baseBallChallengeInflow extends CommandRef {
            public static final baseBallChallengeInflow cmd = new baseBallChallengeInflow();
            public baseBallChallengeInflow create(String nationId, String dateSince, String uploadFile) {
                return createArgs("nationId", nationId, "dateSince", dateSince, "uploadFile", uploadFile);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballChallengeEarningsRanking")
        public static class baseballChallengeEarningsRanking extends CommandRef {
            public static final baseballChallengeEarningsRanking cmd = new baseballChallengeEarningsRanking();
            public baseballChallengeEarningsRanking create(String uploadFile, String byAlliance) {
                return createArgs("uploadFile", uploadFile, "byAlliance", byAlliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballRanking")
        public static class baseballRanking extends CommandRef {
            public static final baseballRanking cmd = new baseballRanking();
            public baseballRanking create(String date, String uploadFile, String byAlliance) {
                return createArgs("date", date, "uploadFile", uploadFile, "byAlliance", byAlliance);
            }
        }
    }
    public static class nation{
        public static class sheet{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="NationSheet")
            public static class NationSheet extends CommandRef {
                public static final NationSheet cmd = new NationSheet();
                public NationSheet create(String nations, String columns, String updateSpies, String sheet, String updateTimer) {
                    return createArgs("nations", nations, "columns", columns, "updateSpies", updateSpies, "sheet", sheet, "updateTimer", updateTimer);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="revenue")
        public static class revenue extends CommandRef {
            public static final revenue cmd = new revenue();
            public revenue create(String nations, String includeUntaxable, String excludeNationBonus) {
                return createArgs("nations", nations, "includeUntaxable", includeUntaxable, "excludeNationBonus", excludeNationBonus);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="stockpile")
        public static class stockpile extends CommandRef {
            public static final stockpile cmd = new stockpile();
            public stockpile create(String nationOrAlliance) {
                return createArgs("nationOrAlliance", nationOrAlliance);
            }
        }
        public static class list{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="rebuy")
            public static class rebuy extends CommandRef {
                public static final rebuy cmd = new rebuy();
                public rebuy create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="multi")
            public static class multi extends CommandRef {
                public static final multi cmd = new multi();
                public multi create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="inactive")
            public static class inactive extends CommandRef {
                public static final inactive cmd = new inactive();
                public inactive create(String nations, String days, String includeApplicants, String includeVacationMode, String page) {
                    return createArgs("nations", nations, "days", days, "includeApplicants", includeApplicants, "includeVacationMode", includeVacationMode, "page", page);
                }
            }
        }
        public static class report{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="create")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String type, String target, String message, String imageEvidenceUrl, String user, String forumPost, String newsReport, String sheet) {
                    return createArgs("type", type, "target", target, "message", message, "imageEvidenceUrl", imageEvidenceUrl, "user", user, "forumPost", forumPost, "newsReport", newsReport, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="list")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String nation, String user) {
                    return createArgs("nation", nation, "user", user);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="unitHistory")
        public static class unitHistory extends CommandRef {
            public static final unitHistory cmd = new unitHistory();
            public unitHistory create(String nation, String unit, String page) {
                return createArgs("nation", nation, "unit", unit, "page", page);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="reroll")
        public static class reroll extends CommandRef {
            public static final reroll cmd = new reroll();
            public reroll create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="TurnTimer")
        public static class TurnTimer extends CommandRef {
            public static final TurnTimer cmd = new TurnTimer();
            public TurnTimer create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="moneyTrades")
        public static class moneyTrades extends CommandRef {
            public static final moneyTrades cmd = new moneyTrades();
            public moneyTrades create(String nation, String time, String forceUpdate, String addBalance) {
                return createArgs("nation", nation, "time", time, "forceUpdate", forceUpdate, "addBalance", addBalance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="loot")
        public static class loot extends CommandRef {
            public static final loot cmd = new loot();
            public loot create(String nationOrAlliance) {
                return createArgs("nationOrAlliance", nationOrAlliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="wars")
        public static class wars extends CommandRef {
            public static final wars cmd = new wars();
            public wars create(String nation) {
                return createArgs("nation", nation);
            }
        }
        public static class stats{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByNation")
            public static class warStatusRankingByNation extends CommandRef {
                public static final warStatusRankingByNation cmd = new warStatusRankingByNation();
                public warStatusRankingByNation create(String attackers, String defenders, String time) {
                    return createArgs("attackers", attackers, "defenders", defenders, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="inflows")
            public static class inflows extends CommandRef {
                public static final inflows cmd = new inflows();
                public inflows create(String nationOrAlliances, String cutoffMs, String hideInflows, String hideOutflows) {
                    return createArgs("nationOrAlliances", nationOrAlliances, "cutoffMs", cutoffMs, "hideInflows", hideInflows, "hideOutflows", hideOutflows);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="nationRanking")
            public static class nationRanking extends CommandRef {
                public static final nationRanking cmd = new nationRanking();
                public nationRanking create(String nations, String attribute, String groupByAlliance, String reverseOrder, String total) {
                    return createArgs("nations", nations, "attribute", attribute, "groupByAlliance", groupByAlliance, "reverseOrder", reverseOrder, "total", total);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="spies")
        public static class spies extends CommandRef {
            public static final spies cmd = new spies();
            public spies create(String nation, String spiesUsed, String requiredSafety) {
                return createArgs("nation", nation, "spiesUsed", spiesUsed, "requiredSafety", requiredSafety);
            }
        }
        public static class set{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setBracket")
            public static class taxbracket extends CommandRef {
                public static final taxbracket cmd = new taxbracket();
                public taxbracket create(String nations, String bracket, String internalRate) {
                    return createArgs("nations", nations, "bracket", bracket, "internalRate", internalRate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setNationTaxBrackets")
            public static class taxbracketAuto extends CommandRef {
                public static final taxbracketAuto cmd = new taxbracketAuto();
                public taxbracketAuto create(String nations, String ping) {
                    return createArgs("nations", nations, "ping", ping);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setInternalTaxRate")
            public static class taxinternal extends CommandRef {
                public static final taxinternal cmd = new taxinternal();
                public taxinternal create(String nations, String taxRate) {
                    return createArgs("nations", nations, "taxRate", taxRate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setNationInternalTaxRates")
            public static class taxinternalAuto extends CommandRef {
                public static final taxinternalAuto cmd = new taxinternalAuto();
                public taxinternalAuto create(String nations, String ping) {
                    return createArgs("nations", nations, "ping", ping);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="setLoot")
            public static class loot extends CommandRef {
                public static final loot cmd = new loot();
                public loot create(String nation, String resources, String type, String fraction) {
                    return createArgs("nation", nation, "resources", resources, "type", type, "fraction", fraction);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setRank")
            public static class rank extends CommandRef {
                public static final rank cmd = new rank();
                public rank create(String nation, String position, String force, String doNotUpdateDiscord) {
                    return createArgs("nation", nation, "position", position, "force", force, "doNotUpdateDiscord", doNotUpdateDiscord);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="leftAA")
        public static class departures extends CommandRef {
            public static final departures cmd = new departures();
            public departures create(String nationOrAlliance, String time, String filter, String ignoreInactives, String ignoreVM, String ignoreMembers, String listIds) {
                return createArgs("nationOrAlliance", nationOrAlliance, "time", time, "filter", filter, "ignoreInactives", ignoreInactives, "ignoreVM", ignoreVM, "ignoreMembers", ignoreMembers, "listIds", listIds);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="who")
        public static class who extends CommandRef {
            public static final who cmd = new who();
            public who create(String nations, String sortBy, String list, String listAlliances, String listRawUserIds, String listMentions, String listInfo, String listChannels, String page) {
                return createArgs("nations", nations, "sortBy", sortBy, "list", list, "listAlliances", listAlliances, "listRawUserIds", listRawUserIds, "listMentions", listMentions, "listInfo", listInfo, "listChannels", listChannels, "page", page);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="beigeTurns")
        public static class beigeTurns extends CommandRef {
            public static final beigeTurns cmd = new beigeTurns();
            public beigeTurns create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="score")
        public static class score extends CommandRef {
            public static final score cmd = new score();
            public score create(String nation, String cities, String soldiers, String tanks, String aircraft, String boats, String missiles, String nukes, String projects, String avg_infra, String infraTotal, String builtMMR) {
                return createArgs("nation", nation, "cities", cities, "soldiers", soldiers, "tanks", tanks, "aircraft", aircraft, "boats", boats, "missiles", missiles, "nukes", nukes, "projects", projects, "avg_infra", avg_infra, "infraTotal", infraTotal, "builtMMR", builtMMR);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="canIBeige")
        public static class canIBeige extends CommandRef {
            public static final canIBeige cmd = new canIBeige();
            public canIBeige create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectSlots")
        public static class slots extends CommandRef {
            public static final slots cmd = new slots();
            public slots create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="revenueSheet")
        public static class revenueSheet extends CommandRef {
            public static final revenueSheet cmd = new revenueSheet();
            public revenueSheet create(String nations, String sheet) {
                return createArgs("nations", nations, "sheet", sheet);
            }
        }
    }
    public static class unit{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="unitHistory")
        public static class history extends CommandRef {
            public static final history cmd = new history();
            public history create(String nation, String unit, String page) {
                return createArgs("nation", nation, "unit", unit, "page", page);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="unitCost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();
            public cost create(String units, String wartime) {
                return createArgs("units", units, "wartime", wartime);
            }
        }
    }
    public static class role{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addRole")
        public static class add extends CommandRef {
            public static final add cmd = new add();
            public add create(String member, String addRole) {
                return createArgs("member", member, "addRole", addRole);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="unregisterRole")
        public static class unregister extends CommandRef {
            public static final unregister cmd = new unregister();
            public unregister create(String locutusRole, String alliance) {
                return createArgs("locutusRole", locutusRole, "alliance", alliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="autoroleall")
        public static class autoassign extends CommandRef {
            public static final autoassign cmd = new autoassign();
            public autoassign create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="aliasRole")
        public static class setAlias extends CommandRef {
            public static final setAlias cmd = new setAlias();
            public setAlias create(String locutusRole, String discordRole, String alliance, String removeRole) {
                return createArgs("locutusRole", locutusRole, "discordRole", discordRole, "alliance", alliance, "removeRole", removeRole);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="removeRole")
        public static class remove extends CommandRef {
            public static final remove cmd = new remove();
            public remove create(String member, String addRole) {
                return createArgs("member", member, "addRole", addRole);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="optOut")
        public static class optOut extends CommandRef {
            public static final optOut cmd = new optOut();
            public optOut create(String value) {
                return createArgs("value", value);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="autorole")
        public static class autorole extends CommandRef {
            public static final autorole cmd = new autorole();
            public autorole create(String member) {
                return createArgs("member", member);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="removeAssignableRole")
        public static class removeAssignableRole extends CommandRef {
            public static final removeAssignableRole cmd = new removeAssignableRole();
            public removeAssignableRole create(String govRole, String assignableRoles) {
                return createArgs("govRole", govRole, "assignableRoles", assignableRoles);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="hasRole")
        public static class hasRole extends CommandRef {
            public static final hasRole cmd = new hasRole();
            public hasRole create(String user, String role) {
                return createArgs("user", user, "role", role);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addRoleToAllMembers")
        public static class addRoleToAllMembers extends CommandRef {
            public static final addRoleToAllMembers cmd = new addRoleToAllMembers();
            public addRoleToAllMembers create(String role) {
                return createArgs("role", role);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="clearAllianceRoles")
        public static class clearAllianceRoles extends CommandRef {
            public static final clearAllianceRoles cmd = new clearAllianceRoles();
            public clearAllianceRoles create(String type) {
                return createArgs("type", type);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="clearNicks")
        public static class clearNicks extends CommandRef {
            public static final clearNicks cmd = new clearNicks();
            public clearNicks create(String undo) {
                return createArgs("undo", undo);
            }
        }
    }
    public static class test{
        @AutoRegister(clazz=link.locutus.discord.web.test.TestCommands.class,method="dummy")
        public static class dummy extends CommandRef {
            public static final dummy cmd = new dummy();
            public dummy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.web.test.TestCommands.class,method="embedTest")
        public static class embedTest extends CommandRef {
            public static final embedTest cmd = new embedTest();
            public embedTest create() {
                return createArgs();
            }
        }
    }
    public static class alliance{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="stockpile")
        public static class stockpile extends CommandRef {
            public static final stockpile cmd = new stockpile();
            public stockpile create(String nationOrAlliance) {
                return createArgs("nationOrAlliance", nationOrAlliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="markAsOffshore")
        public static class markAsOffshore extends CommandRef {
            public static final markAsOffshore cmd = new markAsOffshore();
            public markAsOffshore create(String offshore, String parent) {
                return createArgs("offshore", offshore, "parent", parent);
            }
        }
        public static class treaty{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="approveTreaty")
            public static class approve extends CommandRef {
                public static final approve cmd = new approve();
                public approve create(String senders) {
                    return createArgs("senders", senders);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="sendTreaty")
            public static class send extends CommandRef {
                public static final send cmd = new send();
                public send create(String alliance, String type, String days, String message) {
                    return createArgs("alliance", alliance, "type", type, "days", days, "message", message);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="cancelTreaty")
            public static class cancel extends CommandRef {
                public static final cancel cmd = new cancel();
                public cancel create(String senders) {
                    return createArgs("senders", senders);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="treaties")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String alliances, String listExpired) {
                    return createArgs("alliances", alliances, "listExpired", listExpired);
                }
            }
        }
        public static class stats{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceRanking")
            public static class ranking extends CommandRef {
                public static final ranking cmd = new ranking();
                public ranking create(String alliances, String metric, String reverseOrder, String uploadFile) {
                    return createArgs("alliances", alliances, "metric", metric, "reverseOrder", reverseOrder, "uploadFile", uploadFile);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
            public static class counterStats extends CommandRef {
                public static final counterStats cmd = new counterStats();
                public counterStats create(String alliance) {
                    return createArgs("alliance", alliance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsByTurn")
            public static class metricsByTurn extends CommandRef {
                public static final metricsByTurn cmd = new metricsByTurn();
                public metricsByTurn create(String metric, String coalition, String time) {
                    return createArgs("metric", metric, "coalition", coalition, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsAB")
            public static class allianceMetricsAB extends CommandRef {
                public static final allianceMetricsAB cmd = new allianceMetricsAB();
                public allianceMetricsAB create(String metric, String coalition1, String coalition2, String time) {
                    return createArgs("metric", metric, "coalition1", coalition1, "coalition2", coalition2, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsCompareByTurn")
            public static class allianceMetricsCompareByTurn extends CommandRef {
                public static final allianceMetricsCompareByTurn cmd = new allianceMetricsCompareByTurn();
                public allianceMetricsCompareByTurn create(String metric, String alliances, String time) {
                    return createArgs("metric", metric, "alliances", alliances, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceNationsSheet")
            public static class allianceNationsSheet extends CommandRef {
                public static final allianceNationsSheet cmd = new allianceNationsSheet();
                public allianceNationsSheet create(String nations, String columns, String sheet, String useTotal, String includeInactives, String includeApplicants) {
                    return createArgs("nations", nations, "columns", columns, "sheet", sheet, "useTotal", useTotal, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceRankingTime")
            public static class rankingTime extends CommandRef {
                public static final rankingTime cmd = new rankingTime();
                public rankingTime create(String alliances, String metric, String timeStart, String timeEnd, String reverseOrder, String uploadFile) {
                    return createArgs("alliances", alliances, "metric", metric, "timeStart", timeStart, "timeEnd", timeEnd, "reverseOrder", reverseOrder, "uploadFile", uploadFile);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="editAlliance")
        public static class edit extends CommandRef {
            public static final edit cmd = new edit();
            public edit create(String alliance, String attribute, String value) {
                return createArgs("alliance", alliance, "attribute", attribute, "value", value);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="allianceCost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();
            public cost create(String nations, String update) {
                return createArgs("nations", nations, "update", update);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="revenue")
        public static class revenue extends CommandRef {
            public static final revenue cmd = new revenue();
            public revenue create(String nations, String includeUntaxable, String excludeNationBonus) {
                return createArgs("nations", nations, "includeUntaxable", includeUntaxable, "excludeNationBonus", excludeNationBonus);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="listAllianceMembers")
        public static class listAllianceMembers extends CommandRef {
            public static final listAllianceMembers cmd = new listAllianceMembers();
            public listAllianceMembers create(String page) {
                return createArgs("page", page);
            }
        }
        public static class sheets{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="stockpileSheet")
            public static class stockpileSheet extends CommandRef {
                public static final stockpileSheet cmd = new stockpileSheet();
                public stockpileSheet create(String nationFilter, String normalize, String onlyShowExcess, String forceUpdate) {
                    return createArgs("nationFilter", nationFilter, "normalize", normalize, "onlyShowExcess", onlyShowExcess, "forceUpdate", forceUpdate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warchestSheet")
            public static class warchestSheet extends CommandRef {
                public static final warchestSheet cmd = new warchestSheet();
                public warchestSheet create(String nations, String perCityWarchest, String allianceBankWarchest, String includeGrants, String doNotNormalizeDeposits, String ignoreDeposits, String ignoreStockpileInExcess, String includeRevenueDays, String forceUpdate) {
                    return createArgs("nations", nations, "perCityWarchest", perCityWarchest, "allianceBankWarchest", allianceBankWarchest, "includeGrants", includeGrants, "doNotNormalizeDeposits", doNotNormalizeDeposits, "ignoreDeposits", ignoreDeposits, "ignoreStockpileInExcess", ignoreStockpileInExcess, "includeRevenueDays", includeRevenueDays, "forceUpdate", forceUpdate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="AllianceSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
                public sheet create(String nations, String columns, String sheet) {
                    return createArgs("nations", nations, "columns", columns, "sheet", sheet);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="leftAA")
        public static class departures extends CommandRef {
            public static final departures cmd = new departures();
            public departures create(String nationOrAlliance, String time, String filter, String ignoreInactives, String ignoreVM, String ignoreMembers, String listIds) {
                return createArgs("nationOrAlliance", nationOrAlliance, "time", time, "filter", filter, "ignoreInactives", ignoreInactives, "ignoreVM", ignoreVM, "ignoreMembers", ignoreMembers, "listIds", listIds);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="invite")
    public static class invite extends CommandRef {
        public static final invite cmd = new invite();
        public invite create() {
            return createArgs();
        }
    }
    public static class mail{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="testRecruitMessage")
        public static class recruit extends CommandRef {
            public static final recruit cmd = new recruit();
            public recruit create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="mailTargets")
        public static class targets extends CommandRef {
            public static final targets cmd = new targets();
            public targets create(String warsheet, String spysheet, String allowedNations, String header, String sendFromLocalAccount, String force, String dm) {
                return createArgs("warsheet", warsheet, "spysheet", spysheet, "allowedNations", allowedNations, "header", header, "sendFromLocalAccount", sendFromLocalAccount, "force", force, "dm", dm);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mail")
        public static class send extends CommandRef {
            public static final send cmd = new send();
            public send create(String nations, String subject, String message, String confirm, String notLocal, String apiKey) {
                return createArgs("nations", nations, "subject", subject, "message", message, "confirm", confirm, "notLocal", notLocal, "apiKey", apiKey);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mailCommandOutput")
        public static class command extends CommandRef {
            public static final command cmd = new command();
            public command create(String nations, String subject, String command, String body, String sheet) {
                return createArgs("nations", nations, "subject", subject, "command", command, "body", body, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mailSheet")
        public static class sheet extends CommandRef {
            public static final sheet cmd = new sheet();
            public sheet create(String sheet, String confirm) {
                return createArgs("sheet", sheet, "confirm", confirm);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="reply")
        public static class reply extends CommandRef {
            public static final reply cmd = new reply();
            public reply create(String receiver, String url, String message, String sender) {
                return createArgs("receiver", receiver, "url", url, "message", message, "sender", sender);
            }
        }
    }
    public static class land{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="LandCost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();
            public cost create(String currentLand, String maxLand, String rapidExpansion, String ala, String aec, String gsa, String cities) {
                return createArgs("currentLand", currentLand, "maxLand", maxLand, "rapidExpansion", rapidExpansion, "ala", ala, "aec", aec, "gsa", gsa, "cities", cities);
            }
        }
    }
    public static class audit{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="hasNotBoughtSpies")
        public static class hasNotBoughtSpies extends CommandRef {
            public static final hasNotBoughtSpies cmd = new hasNotBoughtSpies();
            public hasNotBoughtSpies create(String nations) {
                return createArgs("nations", nations);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="checkCities")
        public static class run extends CommandRef {
            public static final run cmd = new run();
            public run create(String nationList, String audits, String pingUser, String mailResults, String postInInterviewChannels, String skipUpdate) {
                return createArgs("nationList", nationList, "audits", audits, "pingUser", pingUser, "mailResults", mailResults, "postInInterviewChannels", postInInterviewChannels, "skipUpdate", skipUpdate);
            }
        }
    }
    public static class coalition{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="listCoalition")
        public static class list extends CommandRef {
            public static final list cmd = new list();
            public list create(String filter, String listIds, String ignoreDeleted) {
                return createArgs("filter", filter, "listIds", listIds, "ignoreDeleted", ignoreDeleted);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="deleteCoalition")
        public static class delete extends CommandRef {
            public static final delete cmd = new delete();
            public delete create(String coalitionStr) {
                return createArgs("coalitionStr", coalitionStr);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="addCoalition")
        public static class add extends CommandRef {
            public static final add cmd = new add();
            public add create(String alliances, String coalitionStr) {
                return createArgs("alliances", alliances, "coalitionStr", coalitionStr);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="createCoalition")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String alliances, String coalitionStr) {
                return createArgs("alliances", alliances, "coalitionStr", coalitionStr);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="generateSphere")
        public static class generate extends CommandRef {
            public static final generate cmd = new generate();
            public generate create(String coalition, String rootAlliance, String topX) {
                return createArgs("coalition", coalition, "rootAlliance", rootAlliance, "topX", topX);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="removeCoalition")
        public static class remove extends CommandRef {
            public static final remove cmd = new remove();
            public remove create(String alliances, String coalitionStr) {
                return createArgs("alliances", alliances, "coalitionStr", coalitionStr);
            }
        }
    }
    public static class trade{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeProfit")
        public static class profit extends CommandRef {
            public static final profit cmd = new profit();
            public profit create(String nations, String time) {
                return createArgs("nations", nations, "time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="findTrader")
        public static class findTrader extends CommandRef {
            public static final findTrader cmd = new findTrader();
            public findTrader create(String type, String isBuy, String cutoff, String groupByAlliance, String includeMoneyTrades) {
                return createArgs("type", type, "isBuy", isBuy, "cutoff", cutoff, "groupByAlliance", groupByAlliance, "includeMoneyTrades", includeMoneyTrades);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="convertedTotal")
        public static class value extends CommandRef {
            public static final value cmd = new value();
            public value create(String resources, String normalize, String useBuyPrice, String useSellPrice, String convertType) {
                return createArgs("resources", resources, "normalize", normalize, "useBuyPrice", useBuyPrice, "useSellPrice", useSellPrice, "convertType", convertType);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="GlobalTradeAverage")
        public static class average extends CommandRef {
            public static final average cmd = new average();
            public average create(String time) {
                return createArgs("time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="moneyTrades")
        public static class moneyTrades extends CommandRef {
            public static final moneyTrades cmd = new moneyTrades();
            public moneyTrades create(String nation, String time, String forceUpdate, String addBalance) {
                return createArgs("nation", nation, "time", time, "forceUpdate", forceUpdate, "addBalance", addBalance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradetotalbyday")
        public static class totalByDay extends CommandRef {
            public static final totalByDay cmd = new totalByDay();
            public totalByDay create(String days) {
                return createArgs("days", days);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradepricebyday")
        public static class priceByDay extends CommandRef {
            public static final priceByDay cmd = new priceByDay();
            public priceByDay create(String resources, String days) {
                return createArgs("resources", resources, "days", days);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradePrice")
        public static class price extends CommandRef {
            public static final price cmd = new price();
            public price create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="trending")
        public static class trending extends CommandRef {
            public static final trending cmd = new trending();
            public trending create(String time) {
                return createArgs("time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeMargin")
        public static class margin extends CommandRef {
            public static final margin cmd = new margin();
            public margin create(String usePercent) {
                return createArgs("usePercent", usePercent);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="trademarginbyday")
        public static class marginByDay extends CommandRef {
            public static final marginByDay cmd = new marginByDay();
            public marginByDay create(String days, String percent) {
                return createArgs("days", days, "percent", percent);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="findProducer")
        public static class findProducer extends CommandRef {
            public static final findProducer cmd = new findProducer();
            public findProducer create(String resources, String nationList, String ignoreMilitaryUpkeep, String ignoreTradeBonus, String ignoreNationBonus, String includeNegative, String listByNation, String listAverage, String uploadFile, String includeInactive) {
                return createArgs("resources", resources, "nationList", nationList, "ignoreMilitaryUpkeep", ignoreMilitaryUpkeep, "ignoreTradeBonus", ignoreTradeBonus, "ignoreNationBonus", ignoreNationBonus, "includeNegative", includeNegative, "listByNation", listByNation, "listAverage", listAverage, "uploadFile", uploadFile, "includeInactive", includeInactive);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="acceptTrades")
        public static class accept extends CommandRef {
            public static final accept cmd = new accept();
            public accept create(String receiver, String force) {
                return createArgs("receiver", receiver, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="compareStockpileValueByDay")
        public static class compareStockpileValueByDay extends CommandRef {
            public static final compareStockpileValueByDay cmd = new compareStockpileValueByDay();
            public compareStockpileValueByDay create(String stockpile1, String stockpile2, String days) {
                return createArgs("stockpile1", stockpile1, "stockpile2", stockpile2, "days", days);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeRanking")
        public static class ranking extends CommandRef {
            public static final ranking cmd = new ranking();
            public ranking create(String nations, String time, String groupByAlliance, String uploadFile) {
                return createArgs("nations", nations, "time", time, "groupByAlliance", groupByAlliance, "uploadFile", uploadFile);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradevolumebyday")
        public static class volumebyday extends CommandRef {
            public static final volumebyday cmd = new volumebyday();
            public volumebyday create(String days) {
                return createArgs("days", days);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="GlobalTradeVolume")
        public static class volume extends CommandRef {
            public static final volume cmd = new volume();
            public volume create() {
                return createArgs();
            }
        }
        public static class offer{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="updateOffer")
            public static class update extends CommandRef {
                public static final update cmd = new update();
                public update create(String offerId, String quantity, String minPPU, String maxPPU, String negotiable, String expire, String exchangeFor, String exchangePPU, String force) {
                    return createArgs("offerId", offerId, "quantity", quantity, "minPPU", minPPU, "maxPPU", maxPPU, "negotiable", negotiable, "expire", expire, "exchangeFor", exchangeFor, "exchangePPU", exchangePPU, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="buyList")
            public static class buy_list extends CommandRef {
                public static final buy_list cmd = new buy_list();
                public buy_list create(String youBuy, String youProvide, String allowedTraders, String sortByLowestMinPrice, String sortByLowestMaxPrice) {
                    return createArgs("youBuy", youBuy, "youProvide", youProvide, "allowedTraders", allowedTraders, "sortByLowestMinPrice", sortByLowestMinPrice, "sortByLowestMaxPrice", sortByLowestMaxPrice);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="myOffers")
            public static class my_offers extends CommandRef {
                public static final my_offers cmd = new my_offers();
                public my_offers create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="deleteOffer")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
                public delete create(String deleteResource, String buyOrSell, String deleteId) {
                    return createArgs("deleteResource", deleteResource, "buyOrSell", buyOrSell, "deleteId", deleteId);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="sellOffer")
            public static class sell extends CommandRef {
                public static final sell cmd = new sell();
                public sell create(String resource, String quantity, String minPPU, String maxPPU, String negotiable, String expire, String exchangeFor, String exchangePPU, String force) {
                    return createArgs("resource", resource, "quantity", quantity, "minPPU", minPPU, "maxPPU", maxPPU, "negotiable", negotiable, "expire", expire, "exchangeFor", exchangeFor, "exchangePPU", exchangePPU, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="buyOffer")
            public static class buy extends CommandRef {
                public static final buy cmd = new buy();
                public buy create(String resource, String quantity, String minPPU, String maxPPU, String negotiable, String expire, String exchangeFor, String exchangePPU, String force) {
                    return createArgs("resource", resource, "quantity", quantity, "minPPU", minPPU, "maxPPU", maxPPU, "negotiable", negotiable, "expire", expire, "exchangeFor", exchangeFor, "exchangePPU", exchangePPU, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="offerInfo")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create(String offerId) {
                    return createArgs("offerId", offerId);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="sellList")
            public static class sell_list extends CommandRef {
                public static final sell_list cmd = new sell_list();
                public sell_list create(String youSell, String youReceive, String allowedTraders, String sortByLowestMinPrice, String sortByLowestMaxPrice) {
                    return createArgs("youSell", youSell, "youReceive", youReceive, "allowedTraders", allowedTraders, "sortByLowestMinPrice", sortByLowestMinPrice, "sortByLowestMaxPrice", sortByLowestMaxPrice);
                }
            }
        }
    }
    public static class sheets_ia{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="NationSheet")
        public static class NationSheet extends CommandRef {
            public static final NationSheet cmd = new NationSheet();
            public NationSheet create(String nations, String columns, String updateSpies, String sheet, String updateTimer) {
                return createArgs("nations", nations, "columns", columns, "updateSpies", updateSpies, "sheet", sheet, "updateTimer", updateTimer);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ActivitySheetFromId")
        public static class ActivitySheetFromId extends CommandRef {
            public static final ActivitySheetFromId cmd = new ActivitySheetFromId();
            public ActivitySheetFromId create(String nationId, String trackTime, String sheet) {
                return createArgs("nationId", nationId, "trackTime", trackTime, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ActivitySheet")
        public static class ActivitySheet extends CommandRef {
            public static final ActivitySheet cmd = new ActivitySheet();
            public ActivitySheet create(String nations, String trackTime, String sheet) {
                return createArgs("nations", nations, "trackTime", trackTime, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="AllianceSheet")
        public static class AllianceSheet extends CommandRef {
            public static final AllianceSheet cmd = new AllianceSheet();
            public AllianceSheet create(String nations, String columns, String sheet) {
                return createArgs("nations", nations, "columns", columns, "sheet", sheet);
            }
        }
    }
    public static class channel{
        public static class close{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="close")
            public static class current extends CommandRef {
                public static final current cmd = new current();
                public current create(String forceDelete) {
                    return createArgs("forceDelete", forceDelete);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="closeInactiveChannels")
            public static class inactive extends CommandRef {
                public static final inactive cmd = new inactive();
                public inactive create(String category, String age, String force) {
                    return createArgs("category", category, "age", age, "force", force);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="allChannelMembers")
        public static class members extends CommandRef {
            public static final members cmd = new members();
            public members create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelCategory")
        public static class setCategory extends CommandRef {
            public static final setCategory cmd = new setCategory();
            public setCategory create(String category) {
                return createArgs("category", category);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="channelMembers")
        public static class channelMembers extends CommandRef {
            public static final channelMembers cmd = new channelMembers();
            public channelMembers create(String channel) {
                return createArgs("channel", channel);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="channelCount")
        public static class count extends CommandRef {
            public static final count cmd = new count();
            public count create() {
                return createArgs();
            }
        }
        public static class move{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelDown")
            public static class Down extends CommandRef {
                public static final Down cmd = new Down();
                public Down create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelUp")
            public static class Up extends CommandRef {
                public static final Up cmd = new Up();
                public Up create() {
                    return createArgs();
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="memberChannels")
        public static class memberChannels extends CommandRef {
            public static final memberChannels cmd = new memberChannels();
            public memberChannels create(String member) {
                return createArgs("member", member);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelPermissions")
        public static class permissions extends CommandRef {
            public static final permissions cmd = new permissions();
            public permissions create(String channel, String nations, String permission, String negate, String removeOthers, String listChanges, String pingAddedUsers) {
                return createArgs("channel", channel, "nations", nations, "permission", permission, "negate", negate, "removeOthers", removeOthers, "listChanges", listChanges, "pingAddedUsers", pingAddedUsers);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="open")
        public static class open extends CommandRef {
            public static final open cmd = new open();
            public open create(String category) {
                return createArgs("category", category);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channel")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String channelName, String category, String copypasta, String addIA, String addMilcom, String addFa, String addEa, String pingRoles, String pingAuthor) {
                return createArgs("channelName", channelName, "category", category, "copypasta", copypasta, "addIA", addIA, "addMilcom", addMilcom, "addFa", addFa, "addEa", addEa, "pingRoles", pingRoles, "pingAuthor", pingAuthor);
            }
        }
        public static class delete{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="debugPurgeChannels")
            public static class inactive extends CommandRef {
                public static final inactive cmd = new inactive();
                public inactive create(String category, String cutoff) {
                    return createArgs("category", category, "cutoff", cutoff);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="deleteChannel")
            public static class current extends CommandRef {
                public static final current cmd = new current();
                public current create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="deleteAllInaccessibleChannels")
            public static class inaccessible extends CommandRef {
                public static final inaccessible cmd = new inaccessible();
                public inaccessible create(String force) {
                    return createArgs("force", force);
                }
            }
        }
    }
    public static class admin{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="loginTimes")
        public static class list_login_times extends CommandRef {
            public static final list_login_times cmd = new list_login_times();
            public list_login_times create(String nations, String cutoff, String sheet) {
                return createArgs("nations", nations, "cutoff", cutoff, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listGuildPerms")
        public static class listGuildPerms extends CommandRef {
            public static final listGuildPerms cmd = new listGuildPerms();
            public listGuildPerms create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="msgInfo")
        public static class msgInfo extends CommandRef {
            public static final msgInfo cmd = new msgInfo();
            public msgInfo create(String message, String useIds) {
                return createArgs("message", message, "useIds", useIds);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="dm")
        public static class dm extends CommandRef {
            public static final dm cmd = new dm();
            public dm create(String nation, String message) {
                return createArgs("nation", nation, "message", message);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listGuildOwners")
        public static class listGuildOwners extends CommandRef {
            public static final listGuildOwners cmd = new listGuildOwners();
            public listGuildOwners create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="testAlert")
        public static class testAlert extends CommandRef {
            public static final testAlert cmd = new testAlert();
            public testAlert create(String channel) {
                return createArgs("channel", channel);
            }
        }
        public static class sync{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncReferrals")
            public static class syncReferrals extends CommandRef {
                public static final syncReferrals cmd = new syncReferrals();
                public syncReferrals create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncMetrics")
            public static class syncMetrics extends CommandRef {
                public static final syncMetrics cmd = new syncMetrics();
                public syncMetrics create(String topX) {
                    return createArgs("topX", topX);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncDiscordWithLocutus")
            public static class discord extends CommandRef {
                public static final discord cmd = new discord();
                public discord create(String url) {
                    return createArgs("url", url);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncTreasures")
            public static class treasures extends CommandRef {
                public static final treasures cmd = new treasures();
                public treasures create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncNations")
            public static class syncNations extends CommandRef {
                public static final syncNations cmd = new syncNations();
                public syncNations create(String nations) {
                    return createArgs("nations", nations);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBanks")
            public static class syncBanks extends CommandRef {
                public static final syncBanks cmd = new syncBanks();
                public syncBanks create(String alliance, String timestamp) {
                    return createArgs("alliance", alliance, "timestamp", timestamp);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncInfraLand")
            public static class syncInfraLand extends CommandRef {
                public static final syncInfraLand cmd = new syncInfraLand();
                public syncInfraLand create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBlockades")
            public static class syncBlockades extends CommandRef {
                public static final syncBlockades cmd = new syncBlockades();
                public syncBlockades create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="syncInterviews")
            public static class syncinterviews extends CommandRef {
                public static final syncinterviews cmd = new syncinterviews();
                public syncinterviews create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncCities")
            public static class syncCities extends CommandRef {
                public static final syncCities cmd = new syncCities();
                public syncCities create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncForum")
            public static class syncforum extends CommandRef {
                public static final syncforum cmd = new syncforum();
                public syncforum create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncLootFromAttacks")
            public static class syncLootFromAttacks extends CommandRef {
                public static final syncLootFromAttacks cmd = new syncLootFromAttacks();
                public syncLootFromAttacks create() {
                    return createArgs();
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="removeInvalidOffshoring")
        public static class removeinvalidoffshoring extends CommandRef {
            public static final removeinvalidoffshoring cmd = new removeinvalidoffshoring();
            public removeinvalidoffshoring create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="importEmojis")
        public static class importEmoji extends CommandRef {
            public static final importEmoji cmd = new importEmoji();
            public importEmoji create(String guild) {
                return createArgs("guild", guild);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="importGuildKeys")
        public static class importGuildKeys extends CommandRef {
            public static final importGuildKeys cmd = new importGuildKeys();
            public importGuildKeys create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="displayGuildPerms")
        public static class displayGuildPerms extends CommandRef {
            public static final displayGuildPerms cmd = new displayGuildPerms();
            public displayGuildPerms create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="apiUsageStats")
        public static class apiUsageStats extends CommandRef {
            public static final apiUsageStats cmd = new apiUsageStats();
            public apiUsageStats create(String cached) {
                return createArgs("cached", cached);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="rootApiUsageStats")
        public static class rootApiUsageStats extends CommandRef {
            public static final rootApiUsageStats cmd = new rootApiUsageStats();
            public rootApiUsageStats create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listExpiredOffshores")
        public static class listExpiredOffshores extends CommandRef {
            public static final listExpiredOffshores cmd = new listExpiredOffshores();
            public listExpiredOffshores create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="leaveServer")
        public static class leaveServer extends CommandRef {
            public static final leaveServer cmd = new leaveServer();
            public leaveServer create(String guildId) {
                return createArgs("guildId", guildId);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="stop")
        public static class stop extends CommandRef {
            public static final stop cmd = new stop();
            public stop create(String save) {
                return createArgs("save", save);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="validateAPIKeys")
        public static class validateAPIKeys extends CommandRef {
            public static final validateAPIKeys cmd = new validateAPIKeys();
            public validateAPIKeys create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listAuthenticated")
        public static class listAuthenticated extends CommandRef {
            public static final listAuthenticated cmd = new listAuthenticated();
            public listAuthenticated create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listExpiredGuilds")
        public static class listExpiredGuilds extends CommandRef {
            public static final listExpiredGuilds cmd = new listExpiredGuilds();
            public listExpiredGuilds create(String checkMessages) {
                return createArgs("checkMessages", checkMessages);
            }
        }
    }
    public static class transfer{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transferBulk")
        public static class bulk extends CommandRef {
            public static final bulk cmd = new bulk();
            public bulk create(String sheet, String depositType, String depositsAccount, String useAllianceBank, String useOffshoreAccount, String taxAccount, String existingTaxAccount, String expire, String convertToMoney, String bypassChecks, String force, String key) {
                return createArgs("sheet", sheet, "depositType", depositType, "depositsAccount", depositsAccount, "useAllianceBank", useAllianceBank, "useOffshoreAccount", useOffshoreAccount, "taxAccount", taxAccount, "existingTaxAccount", existingTaxAccount, "expire", expire, "convertToMoney", convertToMoney, "bypassChecks", bypassChecks, "force", force, "key", key);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="warchest")
        public static class warchest extends CommandRef {
            public static final warchest cmd = new warchest();
            public warchest create(String nations, String resourcesPerCity, String note, String skipStockpile, String depositsAccount, String useAllianceBank, String useOffshoreAccount, String taxAccount, String expire, String convertToMoney, String bypassChecks, String force) {
                return createArgs("nations", nations, "resourcesPerCity", resourcesPerCity, "note", note, "skipStockpile", skipStockpile, "depositsAccount", depositsAccount, "useAllianceBank", useAllianceBank, "useOffshoreAccount", useOffshoreAccount, "taxAccount", taxAccount, "expire", expire, "convertToMoney", convertToMoney, "bypassChecks", bypassChecks, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="withdraw")
        public static class self extends CommandRef {
            public static final self cmd = new self();
            public self create(String transfer, String depositType, String depositsAccount, String useAllianceBank, String useOffshoreAccount, String taxAccount, String onlyMissingFunds, String expire, String token, String convertCash, String bypassChecks, String force) {
                return createArgs("transfer", transfer, "depositType", depositType, "depositsAccount", depositsAccount, "useAllianceBank", useAllianceBank, "useOffshoreAccount", useOffshoreAccount, "taxAccount", taxAccount, "onlyMissingFunds", onlyMissingFunds, "expire", expire, "token", token, "convertCash", convertCash, "bypassChecks", bypassChecks, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="disburse")
        public static class raws extends CommandRef {
            public static final raws cmd = new raws();
            public raws create(String nationList, String daysDefault, String depositType, String noDailyCash, String noCash, String depositsAccount, String useAllianceBank, String useOffshoreAccount, String taxAccount, String expire, String convertToMoney, String bypassChecks, String force) {
                return createArgs("nationList", nationList, "daysDefault", daysDefault, "depositType", depositType, "noDailyCash", noDailyCash, "noCash", noCash, "depositsAccount", depositsAccount, "useAllianceBank", useAllianceBank, "useOffshoreAccount", useOffshoreAccount, "taxAccount", taxAccount, "expire", expire, "convertToMoney", convertToMoney, "bypassChecks", bypassChecks, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="offshore")
        public static class offshore extends CommandRef {
            public static final offshore cmd = new offshore();
            public offshore create(String to, String warchest, String account) {
                return createArgs("to", to, "warchest", warchest, "account", account);
            }
        }
        public static class internal{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="send")
            public static class nation extends CommandRef {
                public static final nation cmd = new nation();
                public nation create(String amount, String receiver, String receiverGuild, String receiverAlliance, String senderAlliance, String confirm) {
                    return createArgs("amount", amount, "receiver", receiver, "receiverGuild", receiverGuild, "receiverAlliance", receiverAlliance, "senderAlliance", senderAlliance, "confirm", confirm);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="sendAA")
            public static class alliance extends CommandRef {
                public static final alliance cmd = new alliance();
                public alliance create(String amount, String receiver, String receiverGuild, String receiverAlliance, String senderAlliance, String senderNation, String confirm) {
                    return createArgs("amount", amount, "receiver", receiver, "receiverGuild", receiverGuild, "receiverAlliance", receiverAlliance, "senderAlliance", senderAlliance, "senderNation", senderNation, "confirm", confirm);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transfer")
        public static class resources extends CommandRef {
            public static final resources cmd = new resources();
            public resources create(String receiver, String transfer, String depositType, String nationAccount, String senderAlliance, String allianceAccount, String taxAccount, String existingTaxAccount, String onlyMissingFunds, String expire, String token, String convertCash, String bypassChecks, String force) {
                return createArgs("receiver", receiver, "transfer", transfer, "depositType", depositType, "nationAccount", nationAccount, "senderAlliance", senderAlliance, "allianceAccount", allianceAccount, "taxAccount", taxAccount, "existingTaxAccount", existingTaxAccount, "onlyMissingFunds", onlyMissingFunds, "expire", expire, "token", token, "convertCash", convertCash, "bypassChecks", bypassChecks, "force", force);
            }
        }
    }
    public static class build{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="add")
        public static class add extends CommandRef {
            public static final add cmd = new add();
            public add create(String category, String ranges, String build) {
                return createArgs("category", category, "ranges", ranges, "build", build);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="listall")
        public static class listall extends CommandRef {
            public static final listall cmd = new listall();
            public listall create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="get")
        public static class get extends CommandRef {
            public static final get cmd = new get();
            public get create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="delete")
        public static class delete extends CommandRef {
            public static final delete cmd = new delete();
            public delete create(String category, String minCities) {
                return createArgs("category", category, "minCities", minCities);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="assign")
        public static class assign extends CommandRef {
            public static final assign cmd = new assign();
            public assign create(String category, String nation, String cities) {
                return createArgs("category", category, "nation", nation, "cities", cities);
            }
        }
    }
    public static class infra{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="InfraCost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();
            public cost create(String currentInfra, String maxInfra, String urbanization, String cce, String aec, String gsa, String cities) {
                return createArgs("currentInfra", currentInfra, "maxInfra", maxInfra, "urbanization", urbanization, "cce", cce, "aec", aec, "gsa", gsa, "cities", cities);
            }
        }
    }
    public static class stats_war{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warCostRanking")
        public static class warCostRanking extends CommandRef {
            public static final warCostRanking cmd = new warCostRanking();
            public warCostRanking create(String timeStart, String timeEnd, String coalition1, String coalition2, String excludeInfra, String excludeConsumption, String excludeLoot, String excludeUnits, String total, String netProfit, String damage, String netTotal, String groupByAlliance, String scalePerCity, String unitKill, String unitLoss, String attackType, String allowedWarTypes, String allowedWarStatuses, String allowedAttacks, String resource, String uploadFile) {
                return createArgs("timeStart", timeStart, "timeEnd", timeEnd, "coalition1", coalition1, "coalition2", coalition2, "excludeInfra", excludeInfra, "excludeConsumption", excludeConsumption, "excludeLoot", excludeLoot, "excludeUnits", excludeUnits, "total", total, "netProfit", netProfit, "damage", damage, "netTotal", netTotal, "groupByAlliance", groupByAlliance, "scalePerCity", scalePerCity, "unitKill", unitKill, "unitLoss", unitLoss, "attackType", attackType, "allowedWarTypes", allowedWarTypes, "allowedWarStatuses", allowedWarStatuses, "allowedAttacks", allowedAttacks, "resource", resource, "uploadFile", uploadFile);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warsCost")
        public static class warsCost extends CommandRef {
            public static final warsCost cmd = new warsCost();
            public warsCost create(String coalition1, String coalition2, String timeStart, String timeEnd, String ignoreUnits, String ignoreInfra, String ignoreConsumption, String ignoreLoot, String listWarIds, String showWarTypes, String allowedWarTypes, String allowedWarStatus, String allowedAttackTypes) {
                return createArgs("coalition1", coalition1, "coalition2", coalition2, "timeStart", timeStart, "timeEnd", timeEnd, "ignoreUnits", ignoreUnits, "ignoreInfra", ignoreInfra, "ignoreConsumption", ignoreConsumption, "ignoreLoot", ignoreLoot, "listWarIds", listWarIds, "showWarTypes", showWarTypes, "allowedWarTypes", allowedWarTypes, "allowedWarStatus", allowedWarStatus, "allowedAttackTypes", allowedAttackTypes);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByAA")
        public static class warStatusRankingByAA extends CommandRef {
            public static final warStatusRankingByAA cmd = new warStatusRankingByAA();
            public warStatusRankingByAA create(String attackers, String defenders, String time) {
                return createArgs("attackers", attackers, "defenders", defenders, "time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="warRanking")
        public static class warRanking extends CommandRef {
            public static final warRanking cmd = new warRanking();
            public warRanking create(String time, String attackers, String defenders, String onlyOffensives, String onlyDefensives, String normalizePerMember, String ignore2dInactives, String rankByNation, String warType, String statuses) {
                return createArgs("time", time, "attackers", attackers, "defenders", defenders, "onlyOffensives", onlyOffensives, "onlyDefensives", onlyDefensives, "normalizePerMember", normalizePerMember, "ignore2dInactives", ignore2dInactives, "rankByNation", rankByNation, "warType", warType, "statuses", statuses);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="myloot")
        public static class myloot extends CommandRef {
            public static final myloot cmd = new myloot();
            public myloot create(String coalition2, String timeStart, String timeEnd, String ignoreUnits, String ignoreInfra, String ignoreConsumption, String ignoreLoot, String listWarIds, String showWarTypes, String allowedWarTypes, String allowedWarStatus, String allowedAttackTypes) {
                return createArgs("coalition2", coalition2, "timeStart", timeStart, "timeEnd", timeEnd, "ignoreUnits", ignoreUnits, "ignoreInfra", ignoreInfra, "ignoreConsumption", ignoreConsumption, "ignoreLoot", ignoreLoot, "listWarIds", listWarIds, "showWarTypes", showWarTypes, "allowedWarTypes", allowedWarTypes, "allowedWarStatus", allowedWarStatus, "allowedAttackTypes", allowedAttackTypes);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warCost")
        public static class warCost extends CommandRef {
            public static final warCost cmd = new warCost();
            public warCost create(String war, String ignoreUnits, String ignoreInfra, String ignoreConsumption, String ignoreLoot) {
                return createArgs("war", war, "ignoreUnits", ignoreUnits, "ignoreInfra", ignoreInfra, "ignoreConsumption", ignoreConsumption, "ignoreLoot", ignoreLoot);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByNation")
        public static class warStatusRankingByNation extends CommandRef {
            public static final warStatusRankingByNation cmd = new warStatusRankingByNation();
            public warStatusRankingByNation create(String attackers, String defenders, String time) {
                return createArgs("attackers", attackers, "defenders", defenders, "time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
        public static class counterStats extends CommandRef {
            public static final counterStats cmd = new counterStats();
            public counterStats create(String alliance) {
                return createArgs("alliance", alliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warAttacksByDay")
        public static class warattacksbyday extends CommandRef {
            public static final warattacksbyday cmd = new warattacksbyday();
            public warattacksbyday create(String nations, String cutoff, String allowedTypes) {
                return createArgs("nations", nations, "cutoff", cutoff, "allowedTypes", allowedTypes);
            }
        }
    }
    public static class simulate{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="airSim")
        public static class air extends CommandRef {
            public static final air cmd = new air();
            public air create(String attAircraft, String defAircraft) {
                return createArgs("attAircraft", attAircraft, "defAircraft", defAircraft);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="navalSim")
        public static class naval extends CommandRef {
            public static final naval cmd = new naval();
            public naval create(String attShips, String defShips) {
                return createArgs("attShips", attShips, "defShips", defShips);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="groundSim")
        public static class ground extends CommandRef {
            public static final ground cmd = new ground();
            public ground create(String attSoldiersUnarmed, String attSoldiers, String attTanks, String defSoldiersUnarmed, String defSoldiers, String defTanks) {
                return createArgs("attSoldiersUnarmed", attSoldiersUnarmed, "attSoldiers", attSoldiers, "attTanks", attTanks, "defSoldiersUnarmed", defSoldiersUnarmed, "defSoldiers", defSoldiers, "defTanks", defTanks);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="quickestBeige")
        public static class fastBeige extends CommandRef {
            public static final fastBeige cmd = new fastBeige();
            public fastBeige create(String resistance, String noGround, String noShip, String noAir, String noMissile, String noNuke) {
                return createArgs("resistance", resistance, "noGround", noGround, "noShip", noShip, "noAir", noAir, "noMissile", noMissile, "noNuke", noNuke);
            }
        }
    }
    public static class sheets_econ{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getNationsInternalTransfers")
        public static class getNationsInternalTransfers extends CommandRef {
            public static final getNationsInternalTransfers cmd = new getNationsInternalTransfers();
            public getNationsInternalTransfers create(String nations, String timeframe, String sheet) {
                return createArgs("nations", nations, "timeframe", timeframe, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="stockpileSheet")
        public static class stockpileSheet extends CommandRef {
            public static final stockpileSheet cmd = new stockpileSheet();
            public stockpileSheet create(String nationFilter, String normalize, String onlyShowExcess, String forceUpdate) {
                return createArgs("nationFilter", nationFilter, "normalize", normalize, "onlyShowExcess", onlyShowExcess, "forceUpdate", forceUpdate);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warReimburseByNationCsv")
        public static class warReimburseByNationCsv extends CommandRef {
            public static final warReimburseByNationCsv cmd = new warReimburseByNationCsv();
            public warReimburseByNationCsv create(String allies, String enemies, String cutoff, String removeWarsWithNoDefenderActions) {
                return createArgs("allies", allies, "enemies", enemies, "cutoff", cutoff, "removeWarsWithNoDefenderActions", removeWarsWithNoDefenderActions);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="projectCostCsv")
        public static class projectCostCsv extends CommandRef {
            public static final projectCostCsv cmd = new projectCostCsv();
            public projectCostCsv create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getIngameNationTransfers")
        public static class getIngameNationTransfers extends CommandRef {
            public static final getIngameNationTransfers cmd = new getIngameNationTransfers();
            public getIngameNationTransfers create(String senders, String receivers, String timeframe, String sheet) {
                return createArgs("senders", senders, "receivers", receivers, "timeframe", timeframe, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warchestSheet")
        public static class warchestSheet extends CommandRef {
            public static final warchestSheet cmd = new warchestSheet();
            public warchestSheet create(String nations, String perCityWarchest, String allianceBankWarchest, String includeGrants, String doNotNormalizeDeposits, String ignoreDeposits, String ignoreStockpileInExcess, String includeRevenueDays, String forceUpdate) {
                return createArgs("nations", nations, "perCityWarchest", perCityWarchest, "allianceBankWarchest", allianceBankWarchest, "includeGrants", includeGrants, "doNotNormalizeDeposits", doNotNormalizeDeposits, "ignoreDeposits", ignoreDeposits, "ignoreStockpileInExcess", ignoreStockpileInExcess, "includeRevenueDays", includeRevenueDays, "forceUpdate", forceUpdate);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxBracketSheet")
        public static class taxBracketSheet extends CommandRef {
            public static final taxBracketSheet cmd = new taxBracketSheet();
            public taxBracketSheet create(String force, String includeApplicants) {
                return createArgs("force", force, "includeApplicants", includeApplicants);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="ProjectSheet")
        public static class ProjectSheet extends CommandRef {
            public static final ProjectSheet cmd = new ProjectSheet();
            public ProjectSheet create(String nations, String sheet) {
                return createArgs("nations", nations, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="IngameNationTransfersByReceiver")
        public static class IngameNationTransfersByReceiver extends CommandRef {
            public static final IngameNationTransfersByReceiver cmd = new IngameNationTransfersByReceiver();
            public IngameNationTransfersByReceiver create(String receivers, String timeframe, String sheet) {
                return createArgs("receivers", receivers, "timeframe", timeframe, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxRecords")
        public static class taxRecords extends CommandRef {
            public static final taxRecords cmd = new taxRecords();
            public taxRecords create(String nation, String startDate, String endDate, String sheet) {
                return createArgs("nation", nation, "startDate", startDate, "endDate", endDate, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="IngameNationTransfersBySender")
        public static class IngameNationTransfersBySender extends CommandRef {
            public static final IngameNationTransfersBySender cmd = new IngameNationTransfersBySender();
            public IngameNationTransfersBySender create(String senders, String timeframe, String sheet) {
                return createArgs("senders", senders, "timeframe", timeframe, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="revenueSheet")
        public static class revenueSheet extends CommandRef {
            public static final revenueSheet cmd = new revenueSheet();
            public revenueSheet create(String nations, String sheet) {
                return createArgs("nations", nations, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getIngameTransactions")
        public static class getIngameTransactions extends CommandRef {
            public static final getIngameTransactions cmd = new getIngameTransactions();
            public getIngameTransactions create(String sender, String receiver, String banker, String timeframe, String sheet) {
                return createArgs("sender", sender, "receiver", receiver, "banker", banker, "timeframe", timeframe, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="taxRevenueSheet")
        public static class taxRevenue extends CommandRef {
            public static final taxRevenue cmd = new taxRevenue();
            public taxRevenue create(String nations, String sheet, String forceUpdate, String includeUntaxable) {
                return createArgs("nations", nations, "sheet", sheet, "forceUpdate", forceUpdate, "includeUntaxable", includeUntaxable);
            }
        }
    }
    public static class stats_tier{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="mmrTierGraph")
        public static class mmrTierGraph extends CommandRef {
            public static final mmrTierGraph cmd = new mmrTierGraph();
            public mmrTierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants, String sheet, String buildings) {
                return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "sheet", sheet, "buildings", buildings);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="cityTierGraph")
        public static class cityTierGraph extends CommandRef {
            public static final cityTierGraph cmd = new cityTierGraph();
            public cityTierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants) {
                return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="scoreTierGraph")
        public static class scoreTierGraph extends CommandRef {
            public static final scoreTierGraph cmd = new scoreTierGraph();
            public scoreTierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants) {
                return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="attributeTierGraph")
        public static class attributeTierGraph extends CommandRef {
            public static final attributeTierGraph cmd = new attributeTierGraph();
            public attributeTierGraph create(String metric, String coalition1, String coalition2, String includeInactives, String includeApplicants, String total) {
                return createArgs("metric", metric, "coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "total", total);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="spyTierGraph")
        public static class spyTierGraph extends CommandRef {
            public static final spyTierGraph cmd = new spyTierGraph();
            public spyTierGraph create(String coalition1, String coalition2, String total) {
                return createArgs("coalition1", coalition1, "coalition2", coalition2, "total", total);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="strengthTierGraph")
        public static class strengthTierGraph extends CommandRef {
            public static final strengthTierGraph cmd = new strengthTierGraph();
            public strengthTierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants, String col1MMR, String col2MMR, String col1Infra, String col2Infra) {
                return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "col1MMR", col1MMR, "col2MMR", col2MMR, "col1Infra", col1Infra, "col2Infra", col2Infra);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="copyPasta")
    public static class copyPasta extends CommandRef {
        public static final copyPasta cmd = new copyPasta();
        public copyPasta create(String key, String message, String requiredRolesAny) {
            return createArgs("key", key, "message", message, "requiredRolesAny", requiredRolesAny);
        }
    }
    public static class alerts{
        public static class beige{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="beigeAlertOptOut")
            public static class beigeAlertOptOut extends CommandRef {
                public static final beigeAlertOptOut cmd = new beigeAlertOptOut();
                public beigeAlertOptOut create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="setBeigeAlertScoreLeeway")
            public static class setBeigeAlertScoreLeeway extends CommandRef {
                public static final setBeigeAlertScoreLeeway cmd = new setBeigeAlertScoreLeeway();
                public setBeigeAlertScoreLeeway create(String scoreLeeway) {
                    return createArgs("scoreLeeway", scoreLeeway);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeReminders")
            public static class beigeReminders extends CommandRef {
                public static final beigeReminders cmd = new beigeReminders();
                public beigeReminders create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertRequiredStatus")
            public static class beigeAlertRequiredStatus extends CommandRef {
                public static final beigeAlertRequiredStatus cmd = new beigeAlertRequiredStatus();
                public beigeAlertRequiredStatus create(String status) {
                    return createArgs("status", status);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="removeBeigeReminder")
            public static class removeBeigeReminder extends CommandRef {
                public static final removeBeigeReminder cmd = new removeBeigeReminder();
                public removeBeigeReminder create(String nationsToRemove) {
                    return createArgs("nationsToRemove", nationsToRemove);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertMode")
            public static class beigeAlertMode extends CommandRef {
                public static final beigeAlertMode cmd = new beigeAlertMode();
                public beigeAlertMode create(String mode) {
                    return createArgs("mode", mode);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertRequiredLoot")
            public static class beigeAlertRequiredLoot extends CommandRef {
                public static final beigeAlertRequiredLoot cmd = new beigeAlertRequiredLoot();
                public beigeAlertRequiredLoot create(String requiredLoot) {
                    return createArgs("requiredLoot", requiredLoot);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeReminder")
            public static class beigeAlert extends CommandRef {
                public static final beigeAlert cmd = new beigeAlert();
                public beigeAlert create(String targets, String requiredLoot, String allowOutOfScore) {
                    return createArgs("targets", targets, "requiredLoot", requiredLoot, "allowOutOfScore", allowOutOfScore);
                }
            }
        }
        public static class trade{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertAbsolute")
            public static class price extends CommandRef {
                public static final price cmd = new price();
                public price create(String resource, String buyOrSell, String aboveOrBelow, String ppu, String duration) {
                    return createArgs("resource", resource, "buyOrSell", buyOrSell, "aboveOrBelow", aboveOrBelow, "ppu", ppu, "duration", duration);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertDisparity")
            public static class margin extends CommandRef {
                public static final margin cmd = new margin();
                public margin create(String resources, String aboveOrBelow, String ppu, String duration) {
                    return createArgs("resources", resources, "aboveOrBelow", aboveOrBelow, "ppu", ppu, "duration", duration);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertUndercut")
            public static class undercut extends CommandRef {
                public static final undercut cmd = new undercut();
                public undercut create(String resources, String buyOrSell, String duration) {
                    return createArgs("resources", resources, "buyOrSell", buyOrSell, "duration", duration);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertNoOffer")
            public static class no_offers extends CommandRef {
                public static final no_offers cmd = new no_offers();
                public no_offers create(String resources, String duration) {
                    return createArgs("resources", resources, "duration", duration);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertMistrade")
            public static class mistrade extends CommandRef {
                public static final mistrade cmd = new mistrade();
                public mistrade create(String resources, String aboveOrBelow, String ppu, String duration) {
                    return createArgs("resources", resources, "aboveOrBelow", aboveOrBelow, "ppu", ppu, "duration", duration);
                }
            }
        }
    }
    @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="unregister")
    public static class unregister extends CommandRef {
        public static final unregister cmd = new unregister();
        public unregister create(String nation, String force) {
            return createArgs("nation", nation, "force", force);
        }
    }
    public static class stats_other{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="radiationByTurn")
        public static class radiationByTurn extends CommandRef {
            public static final radiationByTurn cmd = new radiationByTurn();
            public radiationByTurn create(String continents, String time) {
                return createArgs("continents", continents, "time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="inflows")
        public static class inflows extends CommandRef {
            public static final inflows cmd = new inflows();
            public inflows create(String nationOrAlliances, String cutoffMs, String hideInflows, String hideOutflows) {
                return createArgs("nationOrAlliances", nationOrAlliances, "cutoffMs", cutoffMs, "hideInflows", hideInflows, "hideOutflows", hideOutflows);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsAB")
        public static class allianceMetricsAB extends CommandRef {
            public static final allianceMetricsAB cmd = new allianceMetricsAB();
            public allianceMetricsAB create(String metric, String coalition1, String coalition2, String time) {
                return createArgs("metric", metric, "coalition1", coalition1, "coalition2", coalition2, "time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="findProducer")
        public static class findProducer extends CommandRef {
            public static final findProducer cmd = new findProducer();
            public findProducer create(String resources, String nationList, String ignoreMilitaryUpkeep, String ignoreTradeBonus, String ignoreNationBonus, String includeNegative, String listByNation, String listAverage, String uploadFile, String includeInactive) {
                return createArgs("resources", resources, "nationList", nationList, "ignoreMilitaryUpkeep", ignoreMilitaryUpkeep, "ignoreTradeBonus", ignoreTradeBonus, "ignoreNationBonus", ignoreNationBonus, "includeNegative", includeNegative, "listByNation", listByNation, "listAverage", listAverage, "uploadFile", uploadFile, "includeInactive", includeInactive);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsCompareByTurn")
        public static class allianceMetricsCompareByTurn extends CommandRef {
            public static final allianceMetricsCompareByTurn cmd = new allianceMetricsCompareByTurn();
            public allianceMetricsCompareByTurn create(String metric, String alliances, String time) {
                return createArgs("metric", metric, "alliances", alliances, "time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="recruitmentRankings")
        public static class recruitmentRankings extends CommandRef {
            public static final recruitmentRankings cmd = new recruitmentRankings();
            public recruitmentRankings create(String cutoff, String topX, String uploadFile) {
                return createArgs("cutoff", cutoff, "topX", topX, "uploadFile", uploadFile);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradepricebyday")
        public static class tradepricebyday extends CommandRef {
            public static final tradepricebyday cmd = new tradepricebyday();
            public tradepricebyday create(String resources, String days) {
                return createArgs("resources", resources, "days", days);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="nationRanking")
        public static class nationRanking extends CommandRef {
            public static final nationRanking cmd = new nationRanking();
            public nationRanking create(String nations, String attribute, String groupByAlliance, String reverseOrder, String total) {
                return createArgs("nations", nations, "attribute", attribute, "groupByAlliance", groupByAlliance, "reverseOrder", reverseOrder, "total", total);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceNationsSheet")
        public static class allianceNationsSheet extends CommandRef {
            public static final allianceNationsSheet cmd = new allianceNationsSheet();
            public allianceNationsSheet create(String nations, String columns, String sheet, String useTotal, String includeInactives, String includeApplicants) {
                return createArgs("nations", nations, "columns", columns, "sheet", sheet, "useTotal", useTotal, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
            }
        }
    }
    public static class self{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addAssignableRole")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String govRole, String assignableRoles) {
                return createArgs("govRole", govRole, "assignableRoles", assignableRoles);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="mask")
        public static class mask extends CommandRef {
            public static final mask cmd = new mask();
            public mask create(String members, String role, String value, String reason) {
                return createArgs("members", members, "role", role, "value", value, "reason", reason);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="listAssignableRoles")
        public static class list extends CommandRef {
            public static final list cmd = new list();
            public list create() {
                return createArgs();
            }
        }
    }
    public static class city{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="cityRevenue")
        public static class revenue extends CommandRef {
            public static final revenue cmd = new revenue();
            public revenue create(String city, String nation, String excludeNationBonus) {
                return createArgs("city", city, "nation", nation, "excludeNationBonus", excludeNationBonus);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="CityCost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();
            public cost create(String currentCity, String maxCity, String manifestDestiny, String urbanPlanning, String advancedUrbanPlanning, String metropolitanPlanning, String governmentSupportAgency) {
                return createArgs("currentCity", currentCity, "maxCity", maxCity, "manifestDestiny", manifestDestiny, "urbanPlanning", urbanPlanning, "advancedUrbanPlanning", advancedUrbanPlanning, "metropolitanPlanning", metropolitanPlanning, "governmentSupportAgency", governmentSupportAgency);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="optimalBuild")
        public static class optimalBuild extends CommandRef {
            public static final optimalBuild cmd = new optimalBuild();
            public optimalBuild create(String build, String days, String buildMMR, String age, String infra, String baseReducedInfra, String land, String diseaseCap, String crimeCap, String minPopulation, String radiation, String taxRate, String useRawsForManu, String writePlaintext, String nationalProjects, String moneyPositive, String geographicContinent) {
                return createArgs("build", build, "days", days, "buildMMR", buildMMR, "age", age, "infra", infra, "baseReducedInfra", baseReducedInfra, "land", land, "diseaseCap", diseaseCap, "crimeCap", crimeCap, "minPopulation", minPopulation, "radiation", radiation, "taxRate", taxRate, "useRawsForManu", useRawsForManu, "writePlaintext", writePlaintext, "nationalProjects", nationalProjects, "moneyPositive", moneyPositive, "geographicContinent", geographicContinent);
            }
        }
    }
    public static class treaty{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="sendTreaty")
        public static class send extends CommandRef {
            public static final send cmd = new send();
            public send create(String alliance, String type, String days, String message) {
                return createArgs("alliance", alliance, "type", type, "days", days, "message", message);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="approveTreaty")
        public static class approve extends CommandRef {
            public static final approve cmd = new approve();
            public approve create(String senders) {
                return createArgs("senders", senders);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="cancelTreaty")
        public static class cancel extends CommandRef {
            public static final cancel cmd = new cancel();
            public cancel create(String senders) {
                return createArgs("senders", senders);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="treaties")
        public static class list extends CommandRef {
            public static final list cmd = new list();
            public list create(String alliances, String listExpired) {
                return createArgs("alliances", alliances, "listExpired", listExpired);
            }
        }
    }
    public static class credentials{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="addApiKey")
        public static class addApiKey extends CommandRef {
            public static final addApiKey cmd = new addApiKey();
            public addApiKey create(String apiKey, String verifiedBotKey) {
                return createArgs("apiKey", apiKey, "verifiedBotKey", verifiedBotKey);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="login")
        public static class login extends CommandRef {
            public static final login cmd = new login();
            public login create(String username, String password) {
                return createArgs("username", username, "password", password);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="logout")
        public static class logout extends CommandRef {
            public static final logout cmd = new logout();
            public logout create() {
                return createArgs();
            }
        }
    }
    public static class interview{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="sortInterviews")
        public static class sortInterviews extends CommandRef {
            public static final sortInterviews cmd = new sortInterviews();
            public sortInterviews create(String sortCategoried) {
                return createArgs("sortCategoried", sortCategoried);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mentor")
        public static class mentor extends CommandRef {
            public static final mentor cmd = new mentor();
            public mentor create(String mentor, String mentee, String force) {
                return createArgs("mentor", mentor, "mentee", mentee, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="myMentees")
        public static class mymentees extends CommandRef {
            public static final mymentees cmd = new mymentees();
            public mymentees create(String mentees, String timediff) {
                return createArgs("mentees", mentees, "timediff", timediff);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="unassignMentee")
        public static class unassignMentee extends CommandRef {
            public static final unassignMentee cmd = new unassignMentee();
            public unassignMentee create(String mentee) {
                return createArgs("mentee", mentee);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="iaCat")
        public static class iacat extends CommandRef {
            public static final iacat cmd = new iacat();
            public iacat create(String category) {
                return createArgs("category", category);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setReferrer")
        public static class setReferrer extends CommandRef {
            public static final setReferrer cmd = new setReferrer();
            public setReferrer create(String user) {
                return createArgs("user", user);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="syncInterviews")
        public static class syncInterviews extends CommandRef {
            public static final syncInterviews cmd = new syncInterviews();
            public syncInterviews create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="adRanking")
        public static class adRanking extends CommandRef {
            public static final adRanking cmd = new adRanking();
            public adRanking create(String uploadFile) {
                return createArgs("uploadFile", uploadFile);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="incentiveRanking")
        public static class incentiveRanking extends CommandRef {
            public static final incentiveRanking cmd = new incentiveRanking();
            public incentiveRanking create(String timestamp) {
                return createArgs("timestamp", timestamp);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="recruitmentRankings")
        public static class recruitmentRankings extends CommandRef {
            public static final recruitmentRankings cmd = new recruitmentRankings();
            public recruitmentRankings create(String cutoff, String topX, String uploadFile) {
                return createArgs("cutoff", cutoff, "topX", topX, "uploadFile", uploadFile);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="listMentors")
        public static class listMentors extends CommandRef {
            public static final listMentors cmd = new listMentors();
            public listMentors create(String mentors, String mentees, String timediff, String includeAudit, String ignoreUnallocatedMembers, String listIdleMentors) {
                return createArgs("mentors", mentors, "mentees", mentees, "timediff", timediff, "includeAudit", includeAudit, "ignoreUnallocatedMembers", ignoreUnallocatedMembers, "listIdleMentors", listIdleMentors);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="interview")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String user) {
                return createArgs("user", user);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="interviewMessage")
        public static class interviewMessage extends CommandRef {
            public static final interviewMessage cmd = new interviewMessage();
            public interviewMessage create(String nations, String message, String ping) {
                return createArgs("nations", nations, "message", message, "ping", ping);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mentee")
        public static class mentee extends CommandRef {
            public static final mentee cmd = new mentee();
            public mentee create(String mentee, String force) {
                return createArgs("mentee", mentee, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="iachannels")
        public static class iachannels extends CommandRef {
            public static final iachannels cmd = new iachannels();
            public iachannels create(String filter, String time) {
                return createArgs("filter", filter, "time", time);
            }
        }
    }
    @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="keyStore")
    public static class settings extends CommandRef {
        public static final settings cmd = new settings();
        public settings create(String key, String value, String hideSheetList, String showAll) {
            return createArgs("key", key, "value", value, "hideSheetList", hideSheetList, "showAll", showAll);
        }
    }
    @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="who")
    public static class who extends CommandRef {
        public static final who cmd = new who();
        public who create(String nations, String sortBy, String list, String listAlliances, String listRawUserIds, String listMentions, String listInfo, String listChannels, String page) {
            return createArgs("nations", nations, "sortBy", sortBy, "list", list, "listAlliances", listAlliances, "listRawUserIds", listRawUserIds, "listMentions", listMentions, "listInfo", listInfo, "listChannels", listChannels, "page", page);
        }
    }
    public static class sheets_milcom{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="MMRSheet")
        public static class MMRSheet extends CommandRef {
            public static final MMRSheet cmd = new MMRSheet();
            public MMRSheet create(String nations, String sheet, String forceUpdate, String showCities) {
                return createArgs("nations", nations, "sheet", sheet, "forceUpdate", forceUpdate, "showCities", showCities);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostSheet")
        public static class WarCostSheet extends CommandRef {
            public static final WarCostSheet cmd = new WarCostSheet();
            public WarCostSheet create(String attackers, String defenders, String time, String excludeConsumption, String excludeInfra, String excludeLoot, String excludeUnitCost, String normalizePerCity, String sheet) {
                return createArgs("attackers", attackers, "defenders", defenders, "time", time, "excludeConsumption", excludeConsumption, "excludeInfra", excludeInfra, "excludeLoot", excludeLoot, "excludeUnitCost", excludeUnitCost, "normalizePerCity", normalizePerCity, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="lootValueSheet")
        public static class lootValueSheet extends CommandRef {
            public static final lootValueSheet cmd = new lootValueSheet();
            public lootValueSheet create(String attackers, String sheet) {
                return createArgs("attackers", attackers, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warchestSheet")
        public static class warchestSheet extends CommandRef {
            public static final warchestSheet cmd = new warchestSheet();
            public warchestSheet create(String nations, String perCityWarchest, String allianceBankWarchest, String includeGrants, String doNotNormalizeDeposits, String ignoreDeposits, String ignoreStockpileInExcess, String includeRevenueDays, String forceUpdate) {
                return createArgs("nations", nations, "perCityWarchest", perCityWarchest, "allianceBankWarchest", allianceBankWarchest, "includeGrants", includeGrants, "doNotNormalizeDeposits", doNotNormalizeDeposits, "ignoreDeposits", ignoreDeposits, "ignoreStockpileInExcess", ignoreStockpileInExcess, "includeRevenueDays", includeRevenueDays, "forceUpdate", forceUpdate);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="listSpyTargets")
        public static class listSpyTargets extends CommandRef {
            public static final listSpyTargets cmd = new listSpyTargets();
            public listSpyTargets create(String spySheet, String attackers, String defenders, String headerRow, String output, String groupByAttacker) {
                return createArgs("spySheet", spySheet, "attackers", attackers, "defenders", defenders, "headerRow", headerRow, "output", output, "groupByAttacker", groupByAttacker);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="SpySheet")
        public static class SpySheet extends CommandRef {
            public static final SpySheet cmd = new SpySheet();
            public SpySheet create(String attackers, String defenders, String allowedTypes, String forceUpdate, String checkEspionageSlots, String prioritizeKills, String sheet, String maxDef, String prioritizeAlliances) {
                return createArgs("attackers", attackers, "defenders", defenders, "allowedTypes", allowedTypes, "forceUpdate", forceUpdate, "checkEspionageSlots", checkEspionageSlots, "prioritizeKills", prioritizeKills, "sheet", sheet, "maxDef", maxDef, "prioritizeAlliances", prioritizeAlliances);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostByAllianceSheet")
        public static class WarCostByAllianceSheet extends CommandRef {
            public static final WarCostByAllianceSheet cmd = new WarCostByAllianceSheet();
            public WarCostByAllianceSheet create(String nationSet, String time, String includeInactives, String includeApplicants) {
                return createArgs("nationSet", nationSet, "time", time, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="DeserterSheet")
        public static class DeserterSheet extends CommandRef {
            public static final DeserterSheet cmd = new DeserterSheet();
            public DeserterSheet create(String alliances, String cuttOff, String filter, String ignoreInactive, String ignoreVM, String ignoreMembers) {
                return createArgs("alliances", alliances, "cuttOff", cuttOff, "filter", filter, "ignoreInactive", ignoreInactive, "ignoreVM", ignoreVM, "ignoreMembers", ignoreMembers);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="validateSpyBlitzSheet")
        public static class validateSpyBlitzSheet extends CommandRef {
            public static final validateSpyBlitzSheet cmd = new validateSpyBlitzSheet();
            public validateSpyBlitzSheet create(String sheet, String dayChange, String filter) {
                return createArgs("sheet", sheet, "dayChange", dayChange, "filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warSheet")
        public static class warSheet extends CommandRef {
            public static final warSheet cmd = new warSheet();
            public warSheet create(String allies, String enemies, String cutoff, String includeConcludedWars, String sheetId) {
                return createArgs("allies", allies, "enemies", enemies, "cutoff", cutoff, "includeConcludedWars", includeConcludedWars, "sheetId", sheetId);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertTKRSpySheet")
        public static class convertTKRSpySheet extends CommandRef {
            public static final convertTKRSpySheet cmd = new convertTKRSpySheet();
            public convertTKRSpySheet create(String input, String output, String groupByAttacker, String forceUpdate) {
                return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "forceUpdate", forceUpdate);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertHidudeSpySheet")
        public static class convertHidudeSpySheet extends CommandRef {
            public static final convertHidudeSpySheet cmd = new convertHidudeSpySheet();
            public convertHidudeSpySheet create(String input, String output, String groupByAttacker, String forceUpdate) {
                return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "forceUpdate", forceUpdate);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostByResourceSheet")
        public static class WarCostByResourceSheet extends CommandRef {
            public static final WarCostByResourceSheet cmd = new WarCostByResourceSheet();
            public WarCostByResourceSheet create(String attackers, String defenders, String time, String excludeConsumption, String excludeInfra, String excludeLoot, String excludeUnitCost, String includeGray, String includeDefensives, String normalizePerCity, String normalizePerWar, String sheet) {
                return createArgs("attackers", attackers, "defenders", defenders, "time", time, "excludeConsumption", excludeConsumption, "excludeInfra", excludeInfra, "excludeLoot", excludeLoot, "excludeUnitCost", excludeUnitCost, "includeGray", includeGray, "includeDefensives", includeDefensives, "normalizePerCity", normalizePerCity, "normalizePerWar", normalizePerWar, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="combatantSheet")
        public static class combatantSheet extends CommandRef {
            public static final combatantSheet cmd = new combatantSheet();
            public combatantSheet create(String alliances) {
                return createArgs("alliances", alliances);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="IntelOpSheet")
        public static class IntelOpSheet extends CommandRef {
            public static final IntelOpSheet cmd = new IntelOpSheet();
            public IntelOpSheet create(String time, String attackers, String dnrTopX, String ignoreWithLootHistory, String ignoreDNR, String sheet) {
                return createArgs("time", time, "attackers", attackers, "dnrTopX", dnrTopX, "ignoreWithLootHistory", ignoreWithLootHistory, "ignoreDNR", ignoreDNR, "sheet", sheet);
            }
        }
    }
    public static class spy{
        public static class sheet{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="IntelOpSheet")
            public static class intel extends CommandRef {
                public static final intel cmd = new intel();
                public intel create(String time, String attackers, String dnrTopX, String ignoreWithLootHistory, String ignoreDNR, String sheet) {
                    return createArgs("time", time, "attackers", attackers, "dnrTopX", dnrTopX, "ignoreWithLootHistory", ignoreWithLootHistory, "ignoreDNR", ignoreDNR, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="validateSpyBlitzSheet")
            public static class validate extends CommandRef {
                public static final validate cmd = new validate();
                public validate create(String sheet, String dayChange, String filter) {
                    return createArgs("sheet", sheet, "dayChange", dayChange, "filter", filter);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="listSpyTargets")
            public static class copyForAlliance extends CommandRef {
                public static final copyForAlliance cmd = new copyForAlliance();
                public copyForAlliance create(String spySheet, String attackers, String defenders, String headerRow, String output, String groupByAttacker) {
                    return createArgs("spySheet", spySheet, "attackers", attackers, "defenders", defenders, "headerRow", headerRow, "output", output, "groupByAttacker", groupByAttacker);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertTKRSpySheet")
            public static class convertTKR extends CommandRef {
                public static final convertTKR cmd = new convertTKR();
                public convertTKR create(String input, String output, String groupByAttacker, String forceUpdate) {
                    return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "forceUpdate", forceUpdate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="SpySheet")
            public static class generate extends CommandRef {
                public static final generate cmd = new generate();
                public generate create(String attackers, String defenders, String allowedTypes, String forceUpdate, String checkEspionageSlots, String prioritizeKills, String sheet, String maxDef, String prioritizeAlliances) {
                    return createArgs("attackers", attackers, "defenders", defenders, "allowedTypes", allowedTypes, "forceUpdate", forceUpdate, "checkEspionageSlots", checkEspionageSlots, "prioritizeKills", prioritizeKills, "sheet", sheet, "maxDef", maxDef, "prioritizeAlliances", prioritizeAlliances);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertHidudeSpySheet")
            public static class convertHidude extends CommandRef {
                public static final convertHidude cmd = new convertHidude();
                public convertHidude create(String input, String output, String groupByAttacker, String forceUpdate) {
                    return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "forceUpdate", forceUpdate);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="Counterspy")
        public static class counter extends CommandRef {
            public static final counter cmd = new counter();
            public counter create(String enemy, String operations, String counterWith, String minSuccess) {
                return createArgs("enemy", enemy, "operations", operations, "counterWith", counterWith, "minSuccess", minSuccess);
            }
        }
        public static class find{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="findSpyOp")
            public static class fromNotification extends CommandRef {
                public static final fromNotification cmd = new fromNotification();
                public fromNotification create(String times, String defenderSpies, String defender) {
                    return createArgs("times", times, "defenderSpies", defenderSpies, "defender", defender);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="Spyops")
            public static class target extends CommandRef {
                public static final target cmd = new target();
                public target create(String targets, String operations, String requiredSuccess, String directMesssage, String prioritizeKills, String attacker) {
                    return createArgs("targets", targets, "operations", operations, "requiredSuccess", requiredSuccess, "directMesssage", directMesssage, "prioritizeKills", prioritizeKills, "attacker", attacker);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="intel")
            public static class intel extends CommandRef {
                public static final intel cmd = new intel();
                public intel create(String dnrTopX, String useDNR, String attacker, String score) {
                    return createArgs("dnrTopX", dnrTopX, "useDNR", useDNR, "attacker", attacker, "score", score);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="spyTierGraph")
        public static class tierGraph extends CommandRef {
            public static final tierGraph cmd = new tierGraph();
            public tierGraph create(String coalition1, String coalition2, String total) {
                return createArgs("coalition1", coalition1, "coalition2", coalition2, "total", total);
            }
        }
    }
    public static class embed{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="embedInfo")
        public static class info extends CommandRef {
            public static final info cmd = new info();
            public info create(String message) {
                return createArgs("message", message);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="card")
        public static class commands extends CommandRef {
            public static final commands cmd = new commands();
            public commands create(String title, String body, String commands) {
                return createArgs("title", title, "body", body, "commands", commands);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="updateEmbed")
        public static class update extends CommandRef {
            public static final update cmd = new update();
            public update create(String requiredRole, String color, String title, String desc) {
                return createArgs("requiredRole", requiredRole, "color", color, "title", title, "desc", desc);
            }
        }
    }
    public static class announcement{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="announce")
        public static class create extends CommandRef {
            public static final create cmd = new create();
            public create create(String nationList, String subject, String announcement, String replacements, String requiredVariation, String requiredDepth, String seed, String sendMail, String sendDM, String force) {
                return createArgs("nationList", nationList, "subject", subject, "announcement", announcement, "replacements", replacements, "requiredVariation", requiredVariation, "requiredDepth", requiredDepth, "seed", seed, "sendMail", sendMail, "sendDM", sendDM, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="archiveAnnouncement")
        public static class archive extends CommandRef {
            public static final archive cmd = new archive();
            public archive create(String announcementId, String archive) {
                return createArgs("announcementId", announcementId, "archive", archive);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="readAnnouncement")
        public static class read extends CommandRef {
            public static final read cmd = new read();
            public read create(String ann_id, String markRead) {
                return createArgs("ann_id", ann_id, "markRead", markRead);
            }
        }
    }
    public static class color{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="calculateColorRevenue")
        public static class revenue extends CommandRef {
            public static final revenue cmd = new revenue();
            public revenue create(String forceAqua, String forceBlack, String forceBlue, String forceBrown, String forceGreen, String forceLime, String forceMaroon, String forceOlive, String forceOrange, String forcePink, String forcePurple, String forceRed, String forceWhite, String forceYellow, String forceGrayOrBeige) {
                return createArgs("forceAqua", forceAqua, "forceBlack", forceBlack, "forceBlue", forceBlue, "forceBrown", forceBrown, "forceGreen", forceGreen, "forceLime", forceLime, "forceMaroon", forceMaroon, "forceOlive", forceOlive, "forceOrange", forceOrange, "forcePink", forcePink, "forcePurple", forcePurple, "forceRed", forceRed, "forceWhite", forceWhite, "forceYellow", forceYellow, "forceGrayOrBeige", forceGrayOrBeige);
            }
        }
    }
    public static class project{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="projectCostCsv")
        public static class costsheet extends CommandRef {
            public static final costsheet cmd = new costsheet();
            public costsheet create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectCost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();
            public cost create(String project, String technologicalAdvancement, String governmentSupportAgency) {
                return createArgs("project", project, "technologicalAdvancement", technologicalAdvancement, "governmentSupportAgency", governmentSupportAgency);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectSlots")
        public static class slots extends CommandRef {
            public static final slots cmd = new slots();
            public slots create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="ProjectSheet")
        public static class sheet extends CommandRef {
            public static final sheet cmd = new sheet();
            public sheet create(String nations, String sheet) {
                return createArgs("nations", nations, "sheet", sheet);
            }
        }
    }
    public static class continent{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="nap")
        public static class nap extends CommandRef {
            public static final nap cmd = new nap();
            public nap create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="radiation")
        public static class radiation extends CommandRef {
            public static final radiation cmd = new radiation();
            public radiation create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="continent")
        public static class info extends CommandRef {
            public static final info cmd = new info();
            public info create() {
                return createArgs();
            }
        }
    }
    @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="me")
    public static class me extends CommandRef {
        public static final me cmd = new me();
        public me create() {
            return createArgs();
        }
    }
    @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="embassy")
    public static class embassy extends CommandRef {
        public static final embassy cmd = new embassy();
        public embassy create(String nation) {
            return createArgs("nation", nation);
        }
    }
    public static class deposits{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="addBalance")
        public static class add extends CommandRef {
            public static final add cmd = new add();
            public add create(String accounts, String amount, String note, String force) {
                return createArgs("accounts", accounts, "amount", amount, "note", note, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="shiftDeposits")
        public static class shift extends CommandRef {
            public static final shift cmd = new shift();
            public shift create(String nation, String from, String to, String timediff) {
                return createArgs("nation", nation, "from", from, "to", to, "timediff", timediff);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="depositSheet")
        public static class sheet extends CommandRef {
            public static final sheet cmd = new sheet();
            public sheet create(String nations, String offshores, String ignoreTaxBase, String ignoreOffsets, String noTaxes, String noLoans, String noGrants, String noDeposits, String includePastDepositors, String force) {
                return createArgs("nations", nations, "offshores", offshores, "ignoreTaxBase", ignoreTaxBase, "ignoreOffsets", ignoreOffsets, "noTaxes", noTaxes, "noLoans", noLoans, "noGrants", noGrants, "noDeposits", noDeposits, "includePastDepositors", includePastDepositors, "force", force);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="interest")
        public static class interest extends CommandRef {
            public static final interest cmd = new interest();
            public interest create(String nations, String interestPositivePercent, String interestNegativePercent) {
                return createArgs("nations", nations, "interestPositivePercent", interestPositivePercent, "interestNegativePercent", interestNegativePercent);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="resetDeposits")
        public static class reset extends CommandRef {
            public static final reset cmd = new reset();
            public reset create(String nation, String ignoreGrants, String ignoreLoans, String ignoreTaxes, String ignoreBankDeposits) {
                return createArgs("nation", nation, "ignoreGrants", ignoreGrants, "ignoreLoans", ignoreLoans, "ignoreTaxes", ignoreTaxes, "ignoreBankDeposits", ignoreBankDeposits);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="deposits")
        public static class check extends CommandRef {
            public static final check cmd = new check();
            public check create(String nationOrAllianceOrGuild, String offshores, String timeCutoff, String includeBaseTaxes, String ignoreInternalOffsets, String showTaxesSeparately, String replyInDMs, String includeExpired, String includeIgnored) {
                return createArgs("nationOrAllianceOrGuild", nationOrAllianceOrGuild, "offshores", offshores, "timeCutoff", timeCutoff, "includeBaseTaxes", includeBaseTaxes, "ignoreInternalOffsets", ignoreInternalOffsets, "showTaxesSeparately", showTaxesSeparately, "replyInDMs", replyInDMs, "includeExpired", includeExpired, "includeIgnored", includeIgnored);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="addBalanceSheet")
        public static class addSheet extends CommandRef {
            public static final addSheet cmd = new addSheet();
            public addSheet create(String sheet, String note, String force, String negative) {
                return createArgs("sheet", sheet, "note", note, "force", force, "negative", negative);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="convertNegativeDeposits")
        public static class convertNegative extends CommandRef {
            public static final convertNegative cmd = new convertNegative();
            public convertNegative create(String nations, String negativeResources, String convertTo, String includeGrants, String depositType, String conversionFactor, String sheet, String note) {
                return createArgs("nations", nations, "negativeResources", negativeResources, "convertTo", convertTo, "includeGrants", includeGrants, "depositType", depositType, "conversionFactor", conversionFactor, "sheet", sheet, "note", note);
            }
        }
    }
    public static class tax{
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setNationTaxBrackets")
        public static class setNationBracketAuto extends CommandRef {
            public static final setNationBracketAuto cmd = new setNationBracketAuto();
            public setNationBracketAuto create(String nations, String ping) {
                return createArgs("nations", nations, "ping", ping);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxRecords")
        public static class records extends CommandRef {
            public static final records cmd = new records();
            public records create(String nation, String startDate, String endDate, String sheet) {
                return createArgs("nation", nation, "startDate", startDate, "endDate", endDate, "sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxBracketSheet")
        public static class bracketsheet extends CommandRef {
            public static final bracketsheet cmd = new bracketsheet();
            public bracketsheet create(String force, String includeApplicants) {
                return createArgs("force", force, "includeApplicants", includeApplicants);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="listRequiredTaxRates")
        public static class listBracketAuto extends CommandRef {
            public static final listBracketAuto cmd = new listBracketAuto();
            public listBracketAuto create(String sheet) {
                return createArgs("sheet", sheet);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxDeposits")
        public static class deposists extends CommandRef {
            public static final deposists cmd = new deposists();
            public deposists create(String nations, String baseTaxRate, String startDate, String endDate, String sheet) {
                return createArgs("nations", nations, "baseTaxRate", baseTaxRate, "startDate", startDate, "endDate", endDate, "sheet", sheet);
            }
        }
    }
    public static class war{
        public static class blockade{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unblockadeMe")
            public static class request extends CommandRef {
                public static final request cmd = new request();
                public request create(String diff, String note, String force) {
                    return createArgs("diff", diff, "note", note, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="cancelUnblockadeRequest")
            public static class cancelRequest extends CommandRef {
                public static final cancelRequest cmd = new cancelRequest();
                public cancelRequest create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unblockade")
            public static class find extends CommandRef {
                public static final find cmd = new find();
                public find create(String allies, String targets, String myShips, String numResults) {
                    return createArgs("allies", allies, "targets", targets, "myShips", myShips, "numResults", numResults);
                }
            }
        }
        public static class counter{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="counterWar")
            public static class url extends CommandRef {
                public static final url cmd = new url();
                public url create(String war, String counterWith, String allowAttackersWithMaxOffensives, String filterWeak, String onlyActive, String requireDiscord, String ping, String allowSameAlliance) {
                    return createArgs("war", war, "counterWith", counterWith, "allowAttackersWithMaxOffensives", allowAttackersWithMaxOffensives, "filterWeak", filterWeak, "onlyActive", onlyActive, "requireDiscord", requireDiscord, "ping", ping, "allowSameAlliance", allowSameAlliance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
            public static class stats extends CommandRef {
                public static final stats cmd = new stats();
                public stats create(String alliance) {
                    return createArgs("alliance", alliance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="autocounter")
            public static class auto extends CommandRef {
                public static final auto cmd = new auto();
                public auto create(String enemy, String attackers, String max, String pingMembers, String skipAddMembers, String sendMail) {
                    return createArgs("enemy", enemy, "attackers", attackers, "max", max, "pingMembers", pingMembers, "skipAddMembers", skipAddMembers, "sendMail", sendMail);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="counter")
            public static class nation extends CommandRef {
                public static final nation cmd = new nation();
                public nation create(String target, String counterWith, String allowAttackersWithMaxOffensives, String filterWeak, String onlyActive, String requireDiscord, String ping, String allowSameAlliance) {
                    return createArgs("target", target, "counterWith", counterWith, "allowAttackersWithMaxOffensives", allowAttackersWithMaxOffensives, "filterWeak", filterWeak, "onlyActive", onlyActive, "requireDiscord", requireDiscord, "ping", ping, "allowSameAlliance", allowSameAlliance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="counterSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
                public sheet create(String enemyFilter, String allies, String excludeApplicants, String excludeInactives, String includeAllEnemies, String sheetUrl) {
                    return createArgs("enemyFilter", enemyFilter, "allies", allies, "excludeApplicants", excludeApplicants, "excludeInactives", excludeInactives, "includeAllEnemies", includeAllEnemies, "sheetUrl", sheetUrl);
                }
            }
        }
        public static class room{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warpin")
            public static class pin extends CommandRef {
                public static final pin cmd = new pin();
                public pin create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warcat")
            public static class setCategory extends CommandRef {
                public static final setCategory cmd = new setCategory();
                public setCategory create(String category) {
                    return createArgs("category", category);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="sortWarRooms")
            public static class sort extends CommandRef {
                public static final sort cmd = new sort();
                public sort create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warroom")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String enemy, String attackers, String max, String force, String excludeWeakAttackers, String requireDiscord, String allowAttackersWithMaxOffensives, String pingMembers, String skipAddMembers, String sendMail) {
                    return createArgs("enemy", enemy, "attackers", attackers, "max", max, "force", force, "excludeWeakAttackers", excludeWeakAttackers, "requireDiscord", requireDiscord, "allowAttackersWithMaxOffensives", allowAttackersWithMaxOffensives, "pingMembers", pingMembers, "skipAddMembers", skipAddMembers, "sendMail", sendMail);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warcard")
        public static class card extends CommandRef {
            public static final card cmd = new card();
            public card create(String warId) {
                return createArgs("warId", warId);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="canIBeige")
        public static class canIBeige extends CommandRef {
            public static final canIBeige cmd = new canIBeige();
            public canIBeige create(String nation) {
                return createArgs("nation", nation);
            }
        }
        public static class find{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unblockade")
            public static class unblockade extends CommandRef {
                public static final unblockade cmd = new unblockade();
                public unblockade create(String allies, String targets, String myShips, String numResults) {
                    return createArgs("allies", allies, "targets", targets, "myShips", myShips, "numResults", numResults);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="BlitzPractice")
            public static class blitztargets extends CommandRef {
                public static final blitztargets cmd = new blitztargets();
                public blitztargets create(String topX, String page) {
                    return createArgs("topX", topX, "page", page);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="war")
            public static class enemy extends CommandRef {
                public static final enemy cmd = new enemy();
                public enemy create(String targets, String numResults, String attackerScore, String includeInactives, String includeApplicants, String onlyPriority, String onlyWeak, String onlyLessCities, String resultsInDm, String includeStrong) {
                    return createArgs("targets", targets, "numResults", numResults, "attackerScore", attackerScore, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "onlyPriority", onlyPriority, "onlyWeak", onlyWeak, "onlyLessCities", onlyLessCities, "resultsInDm", resultsInDm, "includeStrong", includeStrong);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="raidNone")
            public static class none extends CommandRef {
                public static final none cmd = new none();
                public none create(String nations, String numResults, String score) {
                    return createArgs("nations", nations, "numResults", numResults, "score", score);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="damage")
            public static class damage extends CommandRef {
                public static final damage cmd = new damage();
                public damage create(String nations, String includeApps, String includeInactives, String filterWeak, String noNavy, String targetMeanInfra, String targetCityMax, String includeBeige, String resultsInDm, String warRange, String relativeNavalStrength) {
                    return createArgs("nations", nations, "includeApps", includeApps, "includeInactives", includeInactives, "filterWeak", filterWeak, "noNavy", noNavy, "targetMeanInfra", targetMeanInfra, "targetCityMax", targetCityMax, "includeBeige", includeBeige, "resultsInDm", resultsInDm, "warRange", warRange, "relativeNavalStrength", relativeNavalStrength);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unprotected")
            public static class unprotected extends CommandRef {
                public static final unprotected cmd = new unprotected();
                public unprotected create(String targets, String numResults, String ignoreDNR, String includeAllies, String nationsToBlitzWith, String maxRelativeTargetStrength, String maxRelativeCounterStrength, String withinAllAttackersRange, String force) {
                    return createArgs("targets", targets, "numResults", numResults, "ignoreDNR", ignoreDNR, "includeAllies", includeAllies, "nationsToBlitzWith", nationsToBlitzWith, "maxRelativeTargetStrength", maxRelativeTargetStrength, "maxRelativeCounterStrength", maxRelativeCounterStrength, "withinAllAttackersRange", withinAllAttackersRange, "force", force);
                }
            }
        }
        public static class sheet{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="blitzSheet")
            public static class blitzSheet extends CommandRef {
                public static final blitzSheet cmd = new blitzSheet();
                public blitzSheet create(String attNations, String defNations, String maxOff, String sameAAPriority, String sameActivityPriority, String turn, String attActivity, String defActivity, String processActiveWars, String onlyEasyTargets, String maxCityRatio, String maxGroundRatio, String maxAirRatio, String sheet) {
                    return createArgs("attNations", attNations, "defNations", defNations, "maxOff", maxOff, "sameAAPriority", sameAAPriority, "sameActivityPriority", sameActivityPriority, "turn", turn, "attActivity", attActivity, "defActivity", defActivity, "processActiveWars", processActiveWars, "onlyEasyTargets", onlyEasyTargets, "maxCityRatio", maxCityRatio, "maxGroundRatio", maxGroundRatio, "maxAirRatio", maxAirRatio, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostByResourceSheet")
            public static class costByResource extends CommandRef {
                public static final costByResource cmd = new costByResource();
                public costByResource create(String attackers, String defenders, String time, String excludeConsumption, String excludeInfra, String excludeLoot, String excludeUnitCost, String includeGray, String includeDefensives, String normalizePerCity, String normalizePerWar, String sheet) {
                    return createArgs("attackers", attackers, "defenders", defenders, "time", time, "excludeConsumption", excludeConsumption, "excludeInfra", excludeInfra, "excludeLoot", excludeLoot, "excludeUnitCost", excludeUnitCost, "includeGray", includeGray, "includeDefensives", includeDefensives, "normalizePerCity", normalizePerCity, "normalizePerWar", normalizePerWar, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warSheet")
            public static class warSheet extends CommandRef {
                public static final warSheet cmd = new warSheet();
                public warSheet create(String allies, String enemies, String cutoff, String includeConcludedWars, String sheetId) {
                    return createArgs("allies", allies, "enemies", enemies, "cutoff", cutoff, "includeConcludedWars", includeConcludedWars, "sheetId", sheetId);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warReimburseByNationCsv")
            public static class ReimburseByNation extends CommandRef {
                public static final ReimburseByNation cmd = new ReimburseByNation();
                public ReimburseByNation create(String allies, String enemies, String cutoff, String removeWarsWithNoDefenderActions) {
                    return createArgs("allies", allies, "enemies", enemies, "cutoff", cutoff, "removeWarsWithNoDefenderActions", removeWarsWithNoDefenderActions);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostSheet")
            public static class costSheet extends CommandRef {
                public static final costSheet cmd = new costSheet();
                public costSheet create(String attackers, String defenders, String time, String excludeConsumption, String excludeInfra, String excludeLoot, String excludeUnitCost, String normalizePerCity, String sheet) {
                    return createArgs("attackers", attackers, "defenders", defenders, "time", time, "excludeConsumption", excludeConsumption, "excludeInfra", excludeInfra, "excludeLoot", excludeLoot, "excludeUnitCost", excludeUnitCost, "normalizePerCity", normalizePerCity, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ValidateBlitzSheet")
            public static class validate extends CommandRef {
                public static final validate cmd = new validate();
                public validate create(String sheet, String maxWars, String nationsFilter, String headerRow) {
                    return createArgs("sheet", sheet, "maxWars", maxWars, "nationsFilter", nationsFilter, "headerRow", headerRow);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="dnr")
        public static class dnr extends CommandRef {
            public static final dnr cmd = new dnr();
            public dnr create(String nation) {
                return createArgs("nation", nation);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="wars")
        public static class info extends CommandRef {
            public static final info cmd = new info();
            public info create(String nation) {
                return createArgs("nation", nation);
            }
        }
    }

}