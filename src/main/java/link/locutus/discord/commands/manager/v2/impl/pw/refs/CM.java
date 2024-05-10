package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM {
        public static class admin{
            public static class alliance{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="runMilitarizationAlerts")
                public static class military_alerts extends CommandRef {
                    public static final military_alerts cmd = new military_alerts();
                    public military_alerts create() {
                        return createArgs();
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="apiUsageStats")
            public static class apiUsageStats extends CommandRef {
                public static final apiUsageStats cmd = new apiUsageStats();
                public apiUsageStats create(String cached) {
                    return createArgs("cached", cached);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="removeInvalidOffshoring")
            public static class clear_deleted_coalition_entries extends CommandRef {
                public static final clear_deleted_coalition_entries cmd = new clear_deleted_coalition_entries();
                public clear_deleted_coalition_entries create(String coalition) {
                    return createArgs("coalition", coalition);
                }
            }
            public static class conflicts{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="checkActiveConflicts")
                public static class check extends CommandRef {
                    public static final check cmd = new check();
                    public check create() {
                        return createArgs();
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="displayGuildPerms")
            public static class displayGuildPerms extends CommandRef {
                public static final displayGuildPerms cmd = new displayGuildPerms();
                public displayGuildPerms create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="dm")
            public static class dm extends CommandRef {
                public static final dm cmd = new dm();
                public dm create(String nation, String message) {
                    return createArgs("nation", nation, "message", message);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="leaveServer")
            public static class leaveServer extends CommandRef {
                public static final leaveServer cmd = new leaveServer();
                public leaveServer create(String guildId) {
                    return createArgs("guildId", guildId);
                }
            }
            public static class list{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="hasSameNetworkAsBan")
                public static class multis extends CommandRef {
                    public static final multis cmd = new multis();
                    public multis create(String nations, String listExpired, String forceUpdate) {
                        return createArgs("nations", nations, "listExpired", listExpired, "forceUpdate", forceUpdate);
                    }
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listExpiredOffshores")
            public static class listExpiredOffshores extends CommandRef {
                public static final listExpiredOffshores cmd = new listExpiredOffshores();
                public listExpiredOffshores create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listGuildOwners")
            public static class listGuildOwners extends CommandRef {
                public static final listGuildOwners cmd = new listGuildOwners();
                public listGuildOwners create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listGuildPerms")
            public static class listGuildPerms extends CommandRef {
                public static final listGuildPerms cmd = new listGuildPerms();
                public listGuildPerms create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="loginTimes")
            public static class list_login_times extends CommandRef {
                public static final list_login_times cmd = new list_login_times();
                public list_login_times create(String nations, String cutoff, String sheet) {
                    return createArgs("nations", nations, "cutoff", cutoff, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="msgInfo")
            public static class msgInfo extends CommandRef {
                public static final msgInfo cmd = new msgInfo();
                public msgInfo create(String message, String useIds) {
                    return createArgs("message", message, "useIds", useIds);
                }
            }
            public static class queue{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="conditionalMessageSettings")
                public static class custom_messages extends CommandRef {
                    public static final custom_messages cmd = new custom_messages();
                    public custom_messages create(String setMeta, String sendMessages, String run) {
                        return createArgs("setMeta", setMeta, "sendMessages", sendMessages, "run", run);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="showFileQueue")
                public static class file extends CommandRef {
                    public static final file cmd = new file();
                    public file create(String timestamp, String numResults) {
                        return createArgs("timestamp", timestamp, "numResults", numResults);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="stop")
            public static class stop extends CommandRef {
                public static final stop cmd = new stop();
                public stop create(String save) {
                    return createArgs("save", save);
                }
            }
            public static class sync{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBans")
                public static class bans extends CommandRef {
                    public static final bans cmd = new bans();
                    public bans create(String discordBans) {
                        return createArgs("discordBans", discordBans);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncCitiesTest")
                public static class cities extends CommandRef {
                    public static final cities cmd = new cities();
                    public cities create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncDiscordWithLocutus")
                public static class discord extends CommandRef {
                    public static final discord cmd = new discord();
                    public discord create(String url) {
                        return createArgs("url", url);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="importLinkedBans")
                public static class multi_bans extends CommandRef {
                    public static final multi_bans cmd = new multi_bans();
                    public multi_bans create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="savePojos")
                public static class pojos extends CommandRef {
                    public static final pojos cmd = new pojos();
                    public pojos create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AllianceMetricCommands.class,method="saveMetrics")
                public static class saveMetrics extends CommandRef {
                    public static final saveMetrics cmd = new saveMetrics();
                    public saveMetrics create(String metrics, String start, String end, String overwrite, String saveAllTurns) {
                        return createArgs("metrics", metrics, "start", start, "end", end, "overwrite", overwrite, "saveAllTurns", saveAllTurns);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBanks")
                public static class syncBanks extends CommandRef {
                    public static final syncBanks cmd = new syncBanks();
                    public syncBanks create(String alliance, String timestamp) {
                        return createArgs("alliance", alliance, "timestamp", timestamp);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBlockades")
                public static class syncBlockades extends CommandRef {
                    public static final syncBlockades cmd = new syncBlockades();
                    public syncBlockades create() {
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncInfraLand")
                public static class syncInfraLand extends CommandRef {
                    public static final syncInfraLand cmd = new syncInfraLand();
                    public syncInfraLand create() {
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncMetrics")
                public static class syncMetrics extends CommandRef {
                    public static final syncMetrics cmd = new syncMetrics();
                    public syncMetrics create(String topX) {
                        return createArgs("topX", topX);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncNations")
                public static class syncNations extends CommandRef {
                    public static final syncNations cmd = new syncNations();
                    public syncNations create(String nations, String dirtyNations) {
                        return createArgs("nations", nations, "dirtyNations", dirtyNations);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncReferrals")
                public static class syncReferrals extends CommandRef {
                    public static final syncReferrals cmd = new syncReferrals();
                    public syncReferrals create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncForum")
                public static class syncforum extends CommandRef {
                    public static final syncforum cmd = new syncforum();
                    public syncforum create(String sectionId, String sectionName) {
                        return createArgs("sectionId", sectionId, "sectionName", sectionName);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="syncInterviews")
                public static class syncinterviews extends CommandRef {
                    public static final syncinterviews cmd = new syncinterviews();
                    public syncinterviews create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncTreasures")
                public static class treasures extends CommandRef {
                    public static final treasures cmd = new treasures();
                    public treasures create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncWarrooms")
                public static class warrooms extends CommandRef {
                    public static final warrooms cmd = new warrooms();
                    public warrooms create(String force) {
                        return createArgs("force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncWars")
                public static class wars extends CommandRef {
                    public static final wars cmd = new wars();
                    public wars create(String updateCityCounts) {
                        return createArgs("updateCityCounts", updateCityCounts);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="validateAPIKeys")
            public static class validateAPIKeys extends CommandRef {
                public static final validateAPIKeys cmd = new validateAPIKeys();
                public validateAPIKeys create() {
                    return createArgs();
                }
            }
            public static class wiki{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="dumpWiki")
                public static class save extends CommandRef {
                    public static final save cmd = new save();
                    public save create(String pathRelative) {
                        return createArgs("pathRelative", pathRelative);
                    }
                }
            }
        }
        public static class alerts{
            public static class audit{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="auditAlertOptOut")
                public static class optout extends CommandRef {
                    public static final optout cmd = new optout();
                    public optout create() {
                        return createArgs();
                    }
                }
            }
            public static class bank{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="bankAlertRequiredValue")
                public static class min_value extends CommandRef {
                    public static final min_value cmd = new min_value();
                    public min_value create(String requiredValue) {
                        return createArgs("requiredValue", requiredValue);
                    }
                }
            }
            public static class beige{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeReminder")
                public static class beigeAlert extends CommandRef {
                    public static final beigeAlert cmd = new beigeAlert();
                    public beigeAlert create(String targets, String requiredLoot, String allowOutOfScore) {
                        return createArgs("targets", targets, "requiredLoot", requiredLoot, "allowOutOfScore", allowOutOfScore);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertMode")
                public static class beigeAlertMode extends CommandRef {
                    public static final beigeAlertMode cmd = new beigeAlertMode();
                    public beigeAlertMode create(String mode) {
                        return createArgs("mode", mode);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="beigeAlertOptOut")
                public static class beigeAlertOptOut extends CommandRef {
                    public static final beigeAlertOptOut cmd = new beigeAlertOptOut();
                    public beigeAlertOptOut create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertRequiredLoot")
                public static class beigeAlertRequiredLoot extends CommandRef {
                    public static final beigeAlertRequiredLoot cmd = new beigeAlertRequiredLoot();
                    public beigeAlertRequiredLoot create(String requiredLoot) {
                        return createArgs("requiredLoot", requiredLoot);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertRequiredStatus")
                public static class beigeAlertRequiredStatus extends CommandRef {
                    public static final beigeAlertRequiredStatus cmd = new beigeAlertRequiredStatus();
                    public beigeAlertRequiredStatus create(String status) {
                        return createArgs("status", status);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeReminders")
                public static class beigeReminders extends CommandRef {
                    public static final beigeReminders cmd = new beigeReminders();
                    public beigeReminders create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="removeBeigeReminder")
                public static class removeBeigeReminder extends CommandRef {
                    public static final removeBeigeReminder cmd = new removeBeigeReminder();
                    public removeBeigeReminder create(String nationsToRemove) {
                        return createArgs("nationsToRemove", nationsToRemove);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="setBeigeAlertScoreLeeway")
                public static class setBeigeAlertScoreLeeway extends CommandRef {
                    public static final setBeigeAlertScoreLeeway cmd = new setBeigeAlertScoreLeeway();
                    public setBeigeAlertScoreLeeway create(String scoreLeeway) {
                        return createArgs("scoreLeeway", scoreLeeway);
                    }
                }
            }
            public static class enemy{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="enemyAlertOptOut")
                public static class optout extends CommandRef {
                    public static final optout cmd = new optout();
                    public optout create() {
                        return createArgs();
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="loginNotifier")
            public static class login extends CommandRef {
                public static final login cmd = new login();
                public login create(String target, String doNotRequireWar) {
                    return createArgs("target", target, "doNotRequireWar", doNotRequireWar);
                }
            }
            public static class trade{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertDisparity")
                public static class margin extends CommandRef {
                    public static final margin cmd = new margin();
                    public margin create(String resources, String aboveOrBelow, String ppu, String duration) {
                        return createArgs("resources", resources, "aboveOrBelow", aboveOrBelow, "ppu", ppu, "duration", duration);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertMistrade")
                public static class mistrade extends CommandRef {
                    public static final mistrade cmd = new mistrade();
                    public mistrade create(String resources, String aboveOrBelow, String ppu, String duration) {
                        return createArgs("resources", resources, "aboveOrBelow", aboveOrBelow, "ppu", ppu, "duration", duration);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertNoOffer")
                public static class no_offers extends CommandRef {
                    public static final no_offers cmd = new no_offers();
                    public no_offers create(String resources, String duration) {
                        return createArgs("resources", resources, "duration", duration);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertAbsolute")
                public static class price extends CommandRef {
                    public static final price cmd = new price();
                    public price create(String resource, String buyOrSell, String aboveOrBelow, String ppu, String duration) {
                        return createArgs("resource", resource, "buyOrSell", buyOrSell, "aboveOrBelow", aboveOrBelow, "ppu", ppu, "duration", duration);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertUndercut")
                public static class undercut extends CommandRef {
                    public static final undercut cmd = new undercut();
                    public undercut create(String resources, String buyOrSell, String duration) {
                        return createArgs("resources", resources, "buyOrSell", buyOrSell, "duration", duration);
                    }
                }
            }
        }
        public static class alliance{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="allianceCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
                public cost create(String nations, String update, String snapshotDate) {
                    return createArgs("nations", nations, "update", update, "snapshotDate", snapshotDate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="leftAA")
            public static class departures extends CommandRef {
                public static final departures cmd = new departures();
                public departures create(String nationOrAlliance, String time, String filter, String ignoreInactives, String ignoreVM, String ignoreMembers, String listIds, String sheet) {
                    return createArgs("nationOrAlliance", nationOrAlliance, "time", time, "filter", filter, "ignoreInactives", ignoreInactives, "ignoreVM", ignoreVM, "ignoreMembers", ignoreMembers, "listIds", listIds, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="editAlliance")
            public static class edit extends CommandRef {
                public static final edit cmd = new edit();
                public edit create(String alliance, String attribute, String value) {
                    return createArgs("alliance", alliance, "attribute", attribute, "value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="listAllianceMembers")
            public static class listAllianceMembers extends CommandRef {
                public static final listAllianceMembers cmd = new listAllianceMembers();
                public listAllianceMembers create(String page) {
                    return createArgs("page", page);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="markAsOffshore")
            public static class markAsOffshore extends CommandRef {
                public static final markAsOffshore cmd = new markAsOffshore();
                public markAsOffshore create(String offshore, String parent) {
                    return createArgs("offshore", offshore, "parent", parent);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="revenue")
            public static class revenue extends CommandRef {
                public static final revenue cmd = new revenue();
                public revenue create(String nations, String includeUntaxable, String excludeNationBonus, String rads, String forceAtWar, String forceAtPeace, String includeWarCosts, String snapshotDate) {
                    return createArgs("nations", nations, "includeUntaxable", includeUntaxable, "excludeNationBonus", excludeNationBonus, "rads", rads, "forceAtWar", forceAtWar, "forceAtPeace", forceAtPeace, "includeWarCosts", includeWarCosts, "snapshotDate", snapshotDate);
                }
            }
            public static class sheets{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="AllianceSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                    public sheet create(String nations, String columns, String sheet) {
                        return createArgs("nations", nations, "columns", columns, "sheet", sheet);
                    }
                }
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
                    public warchestSheet create(String nations, String perCityWarchest, String includeGrants, String doNotNormalizeDeposits, String ignoreDeposits, String ignoreStockpileInExcess, String includeRevenueDays, String forceUpdate) {
                        return createArgs("nations", nations, "perCityWarchest", perCityWarchest, "includeGrants", includeGrants, "doNotNormalizeDeposits", doNotNormalizeDeposits, "ignoreDeposits", ignoreDeposits, "ignoreStockpileInExcess", ignoreStockpileInExcess, "includeRevenueDays", includeRevenueDays, "forceUpdate", forceUpdate);
                    }
                }
            }
            public static class stats{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsAB")
                public static class allianceMetricsAB extends CommandRef {
                    public static final allianceMetricsAB cmd = new allianceMetricsAB();
                    public allianceMetricsAB create(String metric, String coalition1, String coalition2, String time, String attachJson, String attachCsv) {
                        return createArgs("metric", metric, "coalition1", coalition1, "coalition2", coalition2, "time", time, "attachJson", attachJson, "attachCsv", attachCsv);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsCompareByTurn")
                public static class allianceMetricsCompareByTurn extends CommandRef {
                    public static final allianceMetricsCompareByTurn cmd = new allianceMetricsCompareByTurn();
                    public allianceMetricsCompareByTurn create(String metric, String alliances, String time, String attachJson, String attachCsv) {
                        return createArgs("metric", metric, "alliances", alliances, "time", time, "attachJson", attachJson, "attachCsv", attachCsv);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceNationsSheet")
                public static class allianceNationsSheet extends CommandRef {
                    public static final allianceNationsSheet cmd = new allianceNationsSheet();
                    public allianceNationsSheet create(String nations, String columns, String sheet, String useTotal, String includeInactives, String includeApplicants) {
                        return createArgs("nations", nations, "columns", columns, "sheet", sheet, "useTotal", useTotal, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
                public static class counterStats extends CommandRef {
                    public static final counterStats cmd = new counterStats();
                    public counterStats create(String alliance) {
                        return createArgs("alliance", alliance);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="listMerges")
                public static class merges extends CommandRef {
                    public static final merges cmd = new merges();
                    public merges create(String sheet, String threshold, String dayWindow, String minMembers) {
                        return createArgs("sheet", sheet, "threshold", threshold, "dayWindow", dayWindow, "minMembers", minMembers);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsByTurn")
                public static class metricsByTurn extends CommandRef {
                    public static final metricsByTurn cmd = new metricsByTurn();
                    public metricsByTurn create(String metric, String coalition, String time, String attachJson, String attachCsv) {
                        return createArgs("metric", metric, "coalition", coalition, "time", time, "attachJson", attachJson, "attachCsv", attachCsv);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="militaryRanking")
                public static class militarization extends CommandRef {
                    public static final militarization cmd = new militarization();
                    public militarization create(String nations2, String top_n_alliances, String sheet, String removeUntaxable, String removeInactive, String includeApplicants, String snapshotDate) {
                        return createArgs("nations2", nations2, "top_n_alliances", top_n_alliances, "sheet", sheet, "removeUntaxable", removeUntaxable, "removeInactive", removeInactive, "includeApplicants", includeApplicants, "snapshotDate", snapshotDate);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceRanking")
                public static class ranking extends CommandRef {
                    public static final ranking cmd = new ranking();
                    public ranking create(String alliances, String metric, String reverseOrder, String uploadFile) {
                        return createArgs("alliances", alliances, "metric", metric, "reverseOrder", reverseOrder, "uploadFile", uploadFile);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="stockpile")
            public static class stockpile extends CommandRef {
                public static final stockpile cmd = new stockpile();
                public stockpile create(String nationOrAlliance) {
                    return createArgs("nationOrAlliance", nationOrAlliance);
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
                    public list create(String alliances, String treatyFilter) {
                        return createArgs("alliances", alliances, "treatyFilter", treatyFilter);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="sendTreaty")
                public static class send extends CommandRef {
                    public static final send cmd = new send();
                    public send create(String receiver, String type, String days, String message) {
                        return createArgs("receiver", receiver, "type", type, "days", days, "message", message);
                    }
                }
            }
        }
        public static class announcement{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="archiveAnnouncement")
            public static class archive extends CommandRef {
                public static final archive cmd = new archive();
                public archive create(String announcementId, String archive) {
                    return createArgs("announcementId", announcementId, "archive", archive);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="announce")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String sendTo, String subject, String announcement, String replacements, String channel, String bottomText, String requiredVariation, String requiredDepth, String seed, String sendMail, String sendDM, String force) {
                    return createArgs("sendTo", sendTo, "subject", subject, "announcement", announcement, "replacements", replacements, "channel", channel, "bottomText", bottomText, "requiredVariation", requiredVariation, "requiredDepth", requiredDepth, "seed", seed, "sendMail", sendMail, "sendDM", sendDM, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="announceDocument")
            public static class document extends CommandRef {
                public static final document cmd = new document();
                public document create(String original, String sendTo, String replacements) {
                    return createArgs("original", original, "sendTo", sendTo, "replacements", replacements);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="find_announcement")
            public static class find extends CommandRef {
                public static final find cmd = new find();
                public find create(String announcementId, String message) {
                    return createArgs("announcementId", announcementId, "message", message);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="find_invite")
            public static class find_invite extends CommandRef {
                public static final find_invite cmd = new find_invite();
                public find_invite create(String invite) {
                    return createArgs("invite", invite);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="sendInvite")
            public static class invite extends CommandRef {
                public static final invite cmd = new invite();
                public invite create(String message, String inviteTo, String sendTo, String expire, String maxUsesEach, String sendDM, String sendMail, String allowCreation, String force) {
                    return createArgs("message", message, "inviteTo", inviteTo, "sendTo", sendTo, "expire", expire, "maxUsesEach", maxUsesEach, "sendDM", sendDM, "sendMail", sendMail, "allowCreation", allowCreation, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.web.test.TestCommands.class,method="ocr")
            public static class ocr extends CommandRef {
                public static final ocr cmd = new ocr();
                public ocr create(String discordImageUrl) {
                    return createArgs("discordImageUrl", discordImageUrl);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="readAnnouncement")
            public static class read extends CommandRef {
                public static final read cmd = new read();
                public read create(String ann_id, String markRead) {
                    return createArgs("ann_id", ann_id, "markRead", markRead);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="viewAnnouncement")
            public static class view extends CommandRef {
                public static final view cmd = new view();
                public view create(String ann_id, String document, String nation) {
                    return createArgs("ann_id", ann_id, "document", document, "nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="addWatermark")
            public static class watermark extends CommandRef {
                public static final watermark cmd = new watermark();
                public watermark create(String imageUrl, String watermarkText, String color, String opacity, String font, String repeat) {
                    return createArgs("imageUrl", imageUrl, "watermarkText", watermarkText, "color", color, "opacity", opacity, "font", font, "repeat", repeat);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="auditSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
                public sheet create(String nations, String includeAudits, String excludeAudits, String forceUpdate, String verbose, String sheet) {
                    return createArgs("nations", nations, "includeAudits", includeAudits, "excludeAudits", excludeAudits, "forceUpdate", forceUpdate, "verbose", verbose, "sheet", sheet);
                }
            }
        }
        public static class bank{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="depositResources")
            public static class deposit extends CommandRef {
                public static final deposit cmd = new deposit();
                public deposit create(String nations, String sheetAmounts, String amount, String rawsDays, String rawsNoDailyCash, String rawsNoCash, String keepWarchestFactor, String keepPerCity, String keepTotal, String unitResources, String note, String customMessage, String mailResults, String dm, String useApi, String force) {
                    return createArgs("nations", nations, "sheetAmounts", sheetAmounts, "amount", amount, "rawsDays", rawsDays, "rawsNoDailyCash", rawsNoDailyCash, "rawsNoCash", rawsNoCash, "keepWarchestFactor", keepWarchestFactor, "keepPerCity", keepPerCity, "keepTotal", keepTotal, "unitResources", unitResources, "note", note, "customMessage", customMessage, "mailResults", mailResults, "dm", dm, "useApi", useApi, "force", force);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transactions")
            public static class records extends CommandRef {
                public static final records cmd = new records();
                public records create(String nationOrAllianceOrGuild, String timeframe, String useTaxBase, String useOffset, String sheet, String onlyOffshoreTransfers) {
                    return createArgs("nationOrAllianceOrGuild", nationOrAllianceOrGuild, "timeframe", timeframe, "useTaxBase", useTaxBase, "useOffset", useOffset, "sheet", sheet, "onlyOffshoreTransfers", onlyOffshoreTransfers);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="unlockTransfers")
            public static class unlockTransfers extends CommandRef {
                public static final unlockTransfers cmd = new unlockTransfers();
                public unlockTransfers create(String nationOrAllianceOrGuild, String unlockAll) {
                    return createArgs("nationOrAllianceOrGuild", nationOrAllianceOrGuild, "unlockAll", unlockAll);
                }
            }
        }
        public static class baseball{
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballChallengeRanking")
            public static class baseballChallengeRanking extends CommandRef {
                public static final baseballChallengeRanking cmd = new baseballChallengeRanking();
                public baseballChallengeRanking create(String uploadFile, String byAlliance) {
                    return createArgs("uploadFile", uploadFile, "byAlliance", byAlliance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballEarningsRanking")
            public static class baseballEarningsRanking extends CommandRef {
                public static final baseballEarningsRanking cmd = new baseballEarningsRanking();
                public baseballEarningsRanking create(String date, String uploadFile, String byAlliance) {
                    return createArgs("date", date, "uploadFile", uploadFile, "byAlliance", byAlliance);
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
        public static class build{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="add")
            public static class add extends CommandRef {
                public static final add cmd = new add();
                public add create(String category, String ranges, String build) {
                    return createArgs("category", category, "ranges", ranges, "build", build);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="assign")
            public static class assign extends CommandRef {
                public static final assign cmd = new assign();
                public assign create(String category, String nation, String cities) {
                    return createArgs("category", category, "nation", nation, "cities", cities);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="delete")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
                public delete create(String category, String minCities) {
                    return createArgs("category", category, "minCities", minCities);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="get")
            public static class get extends CommandRef {
                public static final get cmd = new get();
                public get create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="listall")
            public static class listall extends CommandRef {
                public static final listall cmd = new listall();
                public listall create() {
                    return createArgs();
                }
            }
        }
        public static class building{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="buildingCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
                public cost create(String build) {
                    return createArgs("build", build);
                }
            }
        }
        public static class channel{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="channelMembers")
            public static class channelMembers extends CommandRef {
                public static final channelMembers cmd = new channelMembers();
                public channelMembers create(String channel) {
                    return createArgs("channel", channel);
                }
            }
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="channelCount")
            public static class count extends CommandRef {
                public static final count cmd = new count();
                public count create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channel")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String channelName, String category, String copypasta, String addInternalAffairsRole, String addMilcom, String addForeignAffairs, String addEcon, String pingRoles, String pingAuthor) {
                    return createArgs("channelName", channelName, "category", category, "copypasta", copypasta, "addInternalAffairsRole", addInternalAffairsRole, "addMilcom", addMilcom, "addForeignAffairs", addForeignAffairs, "addEcon", addEcon, "pingRoles", pingRoles, "pingAuthor", pingAuthor);
                }
            }
            public static class delete{
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="debugPurgeChannels")
                public static class inactive extends CommandRef {
                    public static final inactive cmd = new inactive();
                    public inactive create(String category, String cutoff) {
                        return createArgs("category", category, "cutoff", cutoff);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="allChannelMembers")
            public static class members extends CommandRef {
                public static final members cmd = new members();
                public members create() {
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="open")
            public static class open extends CommandRef {
                public static final open cmd = new open();
                public open create(String category) {
                    return createArgs("category", category);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelPermissions")
            public static class permissions extends CommandRef {
                public static final permissions cmd = new permissions();
                public permissions create(String channel, String nations, String permission, String negate, String removeOthers, String listChanges, String pingAddedUsers) {
                    return createArgs("channel", channel, "nations", nations, "permission", permission, "negate", negate, "removeOthers", removeOthers, "listChanges", listChanges, "pingAddedUsers", pingAddedUsers);
                }
            }
            public static class rename{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="emojifyChannels")
                public static class bulk extends CommandRef {
                    public static final bulk cmd = new bulk();
                    public bulk create(String sheet, String excludeCategories, String includeCategories, String force, String popCultureQuotes) {
                        return createArgs("sheet", sheet, "excludeCategories", excludeCategories, "includeCategories", includeCategories, "force", force, "popCultureQuotes", popCultureQuotes);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelCategory")
            public static class setCategory extends CommandRef {
                public static final setCategory cmd = new setCategory();
                public setCategory create(String category) {
                    return createArgs("category", category);
                }
            }
            public static class sort{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="sortChannelsName")
                public static class category_filter extends CommandRef {
                    public static final category_filter cmd = new category_filter();
                    public category_filter create(String from, String categoryPrefix, String filter, String warn_on_filter_fail, String force) {
                        return createArgs("from", from, "categoryPrefix", categoryPrefix, "filter", filter, "warn_on_filter_fail", warn_on_filter_fail, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="sortChannelsSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                    public sheet create(String from, String sheet, String filter, String warn_on_filter_fail, String force) {
                        return createArgs("from", from, "sheet", sheet, "filter", filter, "warn_on_filter_fail", warn_on_filter_fail, "force", force);
                    }
                }
            }
        }
        public static class chat{
            public static class conversion{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="generate_factsheet")
                public static class add_document extends CommandRef {
                    public static final add_document cmd = new add_document();
                    public add_document create(String googleDocumentUrl, String document_name, String force) {
                        return createArgs("googleDocumentUrl", googleDocumentUrl, "document_name", document_name, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="deleteConversion")
                public static class delete extends CommandRef {
                    public static final delete cmd = new delete();
                    public delete create(String source) {
                        return createArgs("source", source);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="showConverting")
                public static class list extends CommandRef {
                    public static final list cmd = new list();
                    public list create(String showRoot, String showOtherGuilds) {
                        return createArgs("showRoot", showRoot, "showOtherGuilds", showOtherGuilds);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="pauseConversion")
                public static class pause extends CommandRef {
                    public static final pause cmd = new pause();
                    public pause create(String source) {
                        return createArgs("source", source);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="resumeConversion")
                public static class resume extends CommandRef {
                    public static final resume cmd = new resume();
                    public resume create(String source) {
                        return createArgs("source", source);
                    }
                }
            }
            public static class dataset{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="delete_document")
                public static class delete extends CommandRef {
                    public static final delete cmd = new delete();
                    public delete create(String source, String force) {
                        return createArgs("source", source, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="save_embeddings")
                public static class import_sheet extends CommandRef {
                    public static final import_sheet cmd = new import_sheet();
                    public import_sheet create(String sheet, String document_name, String force) {
                        return createArgs("sheet", sheet, "document_name", document_name, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="list_documents")
                public static class list extends CommandRef {
                    public static final list cmd = new list();
                    public list create(String listRoot) {
                        return createArgs("listRoot", listRoot);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="embeddingSelect")
                public static class select extends CommandRef {
                    public static final select cmd = new select();
                    public select create(String excludeTypes, String includeWikiCategories, String excludeWikiCategories, String excludeSources, String addSources) {
                        return createArgs("excludeTypes", excludeTypes, "includeWikiCategories", includeWikiCategories, "excludeWikiCategories", excludeWikiCategories, "excludeSources", excludeSources, "addSources", addSources);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="view_document")
                public static class view extends CommandRef {
                    public static final view cmd = new view();
                    public view create(String source, String getAnswers, String sheet) {
                        return createArgs("source", source, "getAnswers", getAnswers, "sheet", sheet);
                    }
                }
            }
            public static class providers{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="chatProviderConfigure")
                public static class configure extends CommandRef {
                    public static final configure cmd = new configure();
                    public configure create(String provider, String options) {
                        return createArgs("provider", provider, "options", options);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="listChatProviders")
                public static class list extends CommandRef {
                    public static final list cmd = new list();
                    public list create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="chatPause")
                public static class pause extends CommandRef {
                    public static final pause cmd = new pause();
                    public pause create(String provider) {
                        return createArgs("provider", provider);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="chatResume")
                public static class resume extends CommandRef {
                    public static final resume cmd = new resume();
                    public resume create(String provider) {
                        return createArgs("provider", provider);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="setChatProviders")
                public static class set extends CommandRef {
                    public static final set cmd = new set();
                    public set create(String providerTypes) {
                        return createArgs("providerTypes", providerTypes);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="unban")
            public static class unban extends CommandRef {
                public static final unban cmd = new unban();
                public unban create(String nation, String force) {
                    return createArgs("nation", nation, "force", force);
                }
            }
        }
        public static class city{
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
                public optimalBuild create(String build, String days, String buildMMR, String age, String infra, String baseReducedInfra, String land, String radiation, String diseaseCap, String crimeCap, String minPopulation, String useRawsForManu, String moneyPositive, String nationalProjects, String geographicContinent, String taxRate, String writePlaintext) {
                    return createArgs("build", build, "days", days, "buildMMR", buildMMR, "age", age, "infra", infra, "baseReducedInfra", baseReducedInfra, "land", land, "radiation", radiation, "diseaseCap", diseaseCap, "crimeCap", crimeCap, "minPopulation", minPopulation, "useRawsForManu", useRawsForManu, "moneyPositive", moneyPositive, "nationalProjects", nationalProjects, "geographicContinent", geographicContinent, "taxRate", taxRate, "writePlaintext", writePlaintext);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="cityRevenue")
            public static class revenue extends CommandRef {
                public static final revenue cmd = new revenue();
                public revenue create(String city, String nation, String excludeNationBonus, String land, String age) {
                    return createArgs("city", city, "nation", nation, "excludeNationBonus", excludeNationBonus, "land", land, "age", age);
                }
            }
        }
        public static class coalition{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="addCoalition")
            public static class add extends CommandRef {
                public static final add cmd = new add();
                public add create(String alliances, String coalitionName) {
                    return createArgs("alliances", alliances, "coalitionName", coalitionName);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="createCoalition")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String alliances, String coalitionName) {
                    return createArgs("alliances", alliances, "coalitionName", coalitionName);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="deleteCoalition")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
                public delete create(String coalitionName) {
                    return createArgs("coalitionName", coalitionName);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="generateSphere")
            public static class generate extends CommandRef {
                public static final generate cmd = new generate();
                public generate create(String coalition, String rootAlliance, String topX) {
                    return createArgs("coalition", coalition, "rootAlliance", rootAlliance, "topX", topX);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="listCoalition")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String filter, String listIds, String ignoreDeleted) {
                    return createArgs("filter", filter, "listIds", listIds, "ignoreDeleted", ignoreDeleted);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="removeCoalition")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
                public remove create(String alliances, String coalitionName) {
                    return createArgs("alliances", alliances, "coalitionName", coalitionName);
                }
            }
        }
        public static class color{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="calculateColorRevenue")
            public static class revenue extends CommandRef {
                public static final revenue cmd = new revenue();
                public revenue create(String set_aqua, String set_black, String set_blue, String set_brown, String set_green, String set_lime, String set_maroon, String set_olive, String set_orange, String set_pink, String set_purple, String set_red, String set_white, String set_yellow, String set_gray_or_beige) {
                    return createArgs("set_aqua", set_aqua, "set_black", set_black, "set_blue", set_blue, "set_brown", set_brown, "set_green", set_green, "set_lime", set_lime, "set_maroon", set_maroon, "set_olive", set_olive, "set_orange", set_orange, "set_pink", set_pink, "set_purple", set_purple, "set_red", set_red, "set_white", set_white, "set_yellow", set_yellow, "set_gray_or_beige", set_gray_or_beige);
                }
            }
        }
        public static class conflict{
            public static class alliance{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="addCoalition")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                    public add create(String conflict, String alliances, String isCoalition1, String isCoalition2) {
                        return createArgs("conflict", conflict, "alliances", alliances, "isCoalition1", isCoalition1, "isCoalition2", isCoalition2);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="removeCoalition")
                public static class remove extends CommandRef {
                    public static final remove cmd = new remove();
                    public remove create(String conflict, String alliances) {
                        return createArgs("conflict", conflict, "alliances", alliances);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="addConflict")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String category, String coalition1, String coalition2, String conflictName, String start) {
                    return createArgs("category", category, "coalition1", coalition1, "coalition2", coalition2, "conflictName", conflictName, "start", start);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.VirtualConflictCommands.class,method="createTemporary")
            public static class create_temp extends CommandRef {
                public static final create_temp cmd = new create_temp();
                public create_temp create(String col1, String col2, String start, String end, String includeGraphs) {
                    return createArgs("col1", col1, "col2", col2, "start", start, "end", end, "includeGraphs", includeGraphs);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="deleteConflict")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
                public delete create(String conflict, String confirm) {
                    return createArgs("conflict", conflict, "confirm", confirm);
                }
            }
            public static class edit{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setCB")
                public static class casus_belli extends CommandRef {
                    public static final casus_belli cmd = new casus_belli();
                    public casus_belli create(String conflict, String casus_belli) {
                        return createArgs("conflict", conflict, "casus_belli", casus_belli);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setCategory")
                public static class category extends CommandRef {
                    public static final category cmd = new category();
                    public category create(String conflict, String category) {
                        return createArgs("conflict", conflict, "category", category);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setConflictEnd")
                public static class end extends CommandRef {
                    public static final end cmd = new end();
                    public end create(String conflict, String time, String alliance) {
                        return createArgs("conflict", conflict, "time", time, "alliance", alliance);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setConflictName")
                public static class rename extends CommandRef {
                    public static final rename cmd = new rename();
                    public rename create(String conflict, String name, String isCoalition1, String isCoalition2) {
                        return createArgs("conflict", conflict, "name", name, "isCoalition1", isCoalition1, "isCoalition2", isCoalition2);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setConflictStart")
                public static class start extends CommandRef {
                    public static final start cmd = new start();
                    public start create(String conflict, String time, String alliance) {
                        return createArgs("conflict", conflict, "time", time, "alliance", alliance);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setStatus")
                public static class status extends CommandRef {
                    public static final status cmd = new status();
                    public status create(String conflict, String status) {
                        return createArgs("conflict", conflict, "status", status);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setWiki")
                public static class wiki extends CommandRef {
                    public static final wiki cmd = new wiki();
                    public wiki create(String conflict, String url) {
                        return createArgs("conflict", conflict, "url", url);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="info")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create(String conflict, String showParticipants) {
                    return createArgs("conflict", conflict, "showParticipants", showParticipants);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="listConflicts")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String includeInactive) {
                    return createArgs("includeInactive", includeInactive);
                }
            }
            public static class sync{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importAllianceNames")
                public static class alliance_names extends CommandRef {
                    public static final alliance_names cmd = new alliance_names();
                    public alliance_names create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importCtowned")
                public static class ctowned extends CommandRef {
                    public static final ctowned cmd = new ctowned();
                    public ctowned create(String useCache) {
                        return createArgs("useCache", useCache);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importConflictData")
                public static class multiple_sources extends CommandRef {
                    public static final multiple_sources cmd = new multiple_sources();
                    public multiple_sources create(String ctowned, String graphData, String allianceNames, String wiki, String all) {
                        return createArgs("ctowned", ctowned, "graphData", graphData, "allianceNames", allianceNames, "wiki", wiki, "all", all);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="recalculateGraphData")
                public static class recalculate_graphs extends CommandRef {
                    public static final recalculate_graphs cmd = new recalculate_graphs();
                    public recalculate_graphs create(String conflicts) {
                        return createArgs("conflicts", conflicts);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="syncConflictData")
                public static class website extends CommandRef {
                    public static final website cmd = new website();
                    public website create(String conflicts, String includeGraphs, String reloadWars) {
                        return createArgs("conflicts", conflicts, "includeGraphs", includeGraphs, "reloadWars", reloadWars);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importWikiAll")
                public static class wiki_all extends CommandRef {
                    public static final wiki_all cmd = new wiki_all();
                    public wiki_all create(String useCache) {
                        return createArgs("useCache", useCache);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importWikiPage")
                public static class wiki_page extends CommandRef {
                    public static final wiki_page cmd = new wiki_page();
                    public wiki_page create(String name, String url, String useCache) {
                        return createArgs("name", name, "url", url, "useCache", useCache);
                    }
                }
            }
        }
        public static class continent{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="continent")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create() {
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
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="copyPasta")
        public static class copyPasta extends CommandRef {
            public static final copyPasta cmd = new copyPasta();
            public copyPasta create(String key, String message, String requiredRolesAny, String formatNation) {
                return createArgs("key", key, "message", message, "requiredRolesAny", requiredRolesAny, "formatNation", formatNation);
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
        public static class deposits{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="addBalance")
            public static class add extends CommandRef {
                public static final add cmd = new add();
                public add create(String accounts, String amount, String note, String force) {
                    return createArgs("accounts", accounts, "amount", amount, "note", note, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="addBalanceSheet")
            public static class addSheet extends CommandRef {
                public static final addSheet cmd = new addSheet();
                public addSheet create(String sheet, String note, String force, String negative) {
                    return createArgs("sheet", sheet, "note", note, "force", force, "negative", negative);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="deposits")
            public static class check extends CommandRef {
                public static final check cmd = new check();
                public check create(String nationOrAllianceOrGuild, String offshores, String timeCutoff, String includeBaseTaxes, String ignoreInternalOffsets, String showCategories, String replyInDMs, String includeExpired, String includeIgnored, String allowCheckDeleted, String hideEscrowed) {
                    return createArgs("nationOrAllianceOrGuild", nationOrAllianceOrGuild, "offshores", offshores, "timeCutoff", timeCutoff, "includeBaseTaxes", includeBaseTaxes, "ignoreInternalOffsets", ignoreInternalOffsets, "showCategories", showCategories, "replyInDMs", replyInDMs, "includeExpired", includeExpired, "includeIgnored", includeIgnored, "allowCheckDeleted", allowCheckDeleted, "hideEscrowed", hideEscrowed);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="convertNegativeDeposits")
            public static class convertNegative extends CommandRef {
                public static final convertNegative cmd = new convertNegative();
                public convertNegative create(String nations, String negativeResources, String convertTo, String includeGrants, String depositType, String conversionFactor, String note, String sheet) {
                    return createArgs("nations", nations, "negativeResources", negativeResources, "convertTo", convertTo, "includeGrants", includeGrants, "depositType", depositType, "conversionFactor", conversionFactor, "note", note, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.web.test.TestCommands.class,method="viewFlow")
            public static class flows extends CommandRef {
                public static final flows cmd = new flows();
                public flows create(String nation, String note) {
                    return createArgs("nation", nation, "note", note);
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
                public reset create(String nations, String ignoreGrants, String ignoreLoans, String ignoreTaxes, String ignoreBankDeposits, String ignoreEscrow, String force) {
                    return createArgs("nations", nations, "ignoreGrants", ignoreGrants, "ignoreLoans", ignoreLoans, "ignoreTaxes", ignoreTaxes, "ignoreBankDeposits", ignoreBankDeposits, "ignoreEscrow", ignoreEscrow, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="depositSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
                public sheet create(String nations, String offshores, String ignoreTaxBase, String ignoreOffsets, String noTaxes, String noLoans, String noGrants, String noDeposits, String includePastDepositors, String noEscrowSheet, String useFlowNote, String force) {
                    return createArgs("nations", nations, "offshores", offshores, "ignoreTaxBase", ignoreTaxBase, "ignoreOffsets", ignoreOffsets, "noTaxes", noTaxes, "noLoans", noLoans, "noGrants", noGrants, "noDeposits", noDeposits, "includePastDepositors", includePastDepositors, "noEscrowSheet", noEscrowSheet, "useFlowNote", useFlowNote, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="shiftDeposits")
            public static class shift extends CommandRef {
                public static final shift cmd = new shift();
                public shift create(String nation, String from, String to, String expireTime, String decayTime) {
                    return createArgs("nation", nation, "from", from, "to", to, "expireTime", expireTime, "decayTime", decayTime);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.web.test.TestCommands.class,method="shiftFlow")
            public static class shiftFlow extends CommandRef {
                public static final shiftFlow cmd = new shiftFlow();
                public shiftFlow create(String nation, String noteFrom, String flowType, String amount, String noteTo, String alliance, String force) {
                    return createArgs("nation", nation, "noteFrom", noteFrom, "flowType", flowType, "amount", amount, "noteTo", noteTo, "alliance", alliance, "force", force);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="embassy")
        public static class embassy extends CommandRef {
            public static final embassy cmd = new embassy();
            public embassy create(String nation) {
                return createArgs("nation", nation);
            }
        }
        public static class embed{
            public static class add{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="addButton")
                public static class command extends CommandRef {
                    public static final command cmd = new command();
                    public command create(String message, String label, String behavior, String command, String arguments, String channel, String force) {
                        return createArgs("message", message, "label", label, "behavior", behavior, "command", command, "arguments", arguments, "channel", channel, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="addModal")
                public static class modal extends CommandRef {
                    public static final modal cmd = new modal();
                    public modal create(String message, String label, String behavior, String command, String arguments, String defaults, String channel) {
                        return createArgs("message", message, "label", label, "behavior", behavior, "command", command, "arguments", arguments, "defaults", defaults, "channel", channel);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="addButtonRaw")
                public static class raw extends CommandRef {
                    public static final raw cmd = new raw();
                    public raw create(String message, String label, String behavior, String command, String channel, String force) {
                        return createArgs("message", message, "label", label, "behavior", behavior, "command", command, "channel", channel, "force", force);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="card")
            public static class commands extends CommandRef {
                public static final commands cmd = new commands();
                public commands create(String title, String body, String commands) {
                    return createArgs("title", title, "body", body, "commands", commands);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="create")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String title, String description) {
                    return createArgs("title", title, "description", description);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="description")
            public static class description extends CommandRef {
                public static final description cmd = new description();
                public description create(String discMessage, String description) {
                    return createArgs("discMessage", discMessage, "description", description);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="embedInfo")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create(String embedMessage, String copyToMessage) {
                    return createArgs("embedMessage", embedMessage, "copyToMessage", copyToMessage);
                }
            }
            public static class remove{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="removeButton")
                public static class button extends CommandRef {
                    public static final button cmd = new button();
                    public button create(String message, String labels) {
                        return createArgs("message", message, "labels", labels);
                    }
                }
            }
            public static class template{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="allyEnemySheets")
                public static class ally_enemy_sheets extends CommandRef {
                    public static final ally_enemy_sheets cmd = new ally_enemy_sheets();
                    public ally_enemy_sheets create(String outputChannel, String allEnemiesSheet, String priorityEnemiesSheet, String allAlliesSheet, String underutilizedAlliesSheet) {
                        return createArgs("outputChannel", outputChannel, "allEnemiesSheet", allEnemiesSheet, "priorityEnemiesSheet", priorityEnemiesSheet, "allAlliesSheet", allAlliesSheet, "underutilizedAlliesSheet", underutilizedAlliesSheet);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="depositsPanel")
                public static class deposits extends CommandRef {
                    public static final deposits cmd = new deposits();
                    public deposits create(String bankerNation, String outputChannel) {
                        return createArgs("bankerNation", bankerNation, "outputChannel", outputChannel);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="econPanel")
                public static class econ_gov extends CommandRef {
                    public static final econ_gov cmd = new econ_gov();
                    public econ_gov create(String outputChannel, String useFlowNote, String includePastDepositors) {
                        return createArgs("outputChannel", outputChannel, "useFlowNote", useFlowNote, "includePastDepositors", includePastDepositors);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="warGuerilla")
                public static class guerilla extends CommandRef {
                    public static final guerilla cmd = new guerilla();
                    public guerilla create(String outputChannel) {
                        return createArgs("outputChannel", outputChannel);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="iaPanel")
                public static class ia_gov extends CommandRef {
                    public static final ia_gov cmd = new ia_gov();
                    public ia_gov create(String outputChannel) {
                        return createArgs("outputChannel", outputChannel);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="memberEconPanel")
                public static class member_econ extends CommandRef {
                    public static final member_econ cmd = new member_econ();
                    public member_econ create(String outputChannel, String showDepositsInDms) {
                        return createArgs("outputChannel", outputChannel, "showDepositsInDms", showDepositsInDms);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="raid")
                public static class raid extends CommandRef {
                    public static final raid cmd = new raid();
                    public raid create(String outputChannel) {
                        return createArgs("outputChannel", outputChannel);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="spyEnemy")
                public static class spy_enemy extends CommandRef {
                    public static final spy_enemy cmd = new spy_enemy();
                    public spy_enemy create(String coalition, String outputChannel) {
                        return createArgs("coalition", coalition, "outputChannel", outputChannel);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="spySheets")
                public static class spy_sheets extends CommandRef {
                    public static final spy_sheets cmd = new spy_sheets();
                    public spy_sheets create(String allies, String outputChannel, String spySheet) {
                        return createArgs("allies", allies, "outputChannel", outputChannel, "spySheet", spySheet);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="unblockadeRequests")
                public static class unblockade_requests extends CommandRef {
                    public static final unblockade_requests cmd = new unblockade_requests();
                    public unblockade_requests create(String outputChannel) {
                        return createArgs("outputChannel", outputChannel);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="warContestedRange")
                public static class war_contested_range extends CommandRef {
                    public static final war_contested_range cmd = new war_contested_range();
                    public war_contested_range create(String greaterOrLess, String score, String outputChannel, String resultsInDm) {
                        return createArgs("greaterOrLess", greaterOrLess, "score", score, "outputChannel", outputChannel, "resultsInDm", resultsInDm);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="warWinning")
                public static class war_winning extends CommandRef {
                    public static final war_winning cmd = new war_winning();
                    public war_winning create(String outputChannel, String resultsInDm) {
                        return createArgs("outputChannel", outputChannel, "resultsInDm", resultsInDm);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="title")
            public static class title extends CommandRef {
                public static final title cmd = new title();
                public title create(String discMessage, String title) {
                    return createArgs("discMessage", discMessage, "title", title);
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
        public static class escrow{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="addEscrow")
            public static class add extends CommandRef {
                public static final add cmd = new add();
                public add create(String nations, String amountBase, String amountPerCity, String amountExtra, String subtractStockpile, String subtractNationsUnits, String subtractDeposits, String expireAfter, String force) {
                    return createArgs("nations", nations, "amountBase", amountBase, "amountPerCity", amountPerCity, "amountExtra", amountExtra, "subtractStockpile", subtractStockpile, "subtractNationsUnits", subtractNationsUnits, "subtractDeposits", subtractDeposits, "expireAfter", expireAfter, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setEscrow")
            public static class set extends CommandRef {
                public static final set cmd = new set();
                public set create(String nations, String amountBase, String amountPerCity, String amountExtra, String subtractStockpile, String subtractNationsUnits, String subtractDeposits, String expireAfter, String force) {
                    return createArgs("nations", nations, "amountBase", amountBase, "amountPerCity", amountPerCity, "amountExtra", amountExtra, "subtractStockpile", subtractStockpile, "subtractNationsUnits", subtractNationsUnits, "subtractDeposits", subtractDeposits, "expireAfter", expireAfter, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setEscrowSheet")
            public static class set_sheet extends CommandRef {
                public static final set_sheet cmd = new set_sheet();
                public set_sheet create(String sheet, String expireAfter, String force) {
                    return createArgs("sheet", sheet, "expireAfter", expireAfter, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="escrowSheetCmd")
            public static class view_sheet extends CommandRef {
                public static final view_sheet cmd = new view_sheet();
                public view_sheet create(String nations, String includePastDepositors, String sheet) {
                    return createArgs("nations", nations, "includePastDepositors", includePastDepositors, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="withdrawEscrowed")
            public static class withdraw extends CommandRef {
                public static final withdraw cmd = new withdraw();
                public withdraw create(String receiver, String amount, String force) {
                    return createArgs("receiver", receiver, "amount", amount, "force", force);
                }
            }
        }
        public static class fun{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="borg")
            public static class borg extends CommandRef {
                public static final borg cmd = new borg();
                public borg create(String msg) {
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="joke")
            public static class joke extends CommandRef {
                public static final joke cmd = new joke();
                public joke create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="resetCityNames")
            public static class reset_borgs_cities extends CommandRef {
                public static final reset_borgs_cities cmd = new reset_borgs_cities();
                public reset_borgs_cities create(String name) {
                    return createArgs("name", name);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="say")
            public static class say extends CommandRef {
                public static final say cmd = new say();
                public say create(String msg) {
                    return createArgs("msg", msg);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="stealBorgsCity")
            public static class stealborgscity extends CommandRef {
                public static final stealborgscity cmd = new stealborgscity();
                public stealborgscity create() {
                    return createArgs();
                }
            }
        }
        public static class grant_template{
            public static class create{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateBuild")
                public static class build extends CommandRef {
                    public static final build cmd = new build();
                    public build create(String name, String allowedRecipients, String build, String mmr, String only_new_cities, String allow_after_days, String allow_after_offensive, String allow_after_infra, String allow_all, String allow_after_land_or_project, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String expireTime, String decayTime, String allowIgnore, String repeatable, String force) {
                        return createArgs("name", name, "allowedRecipients", allowedRecipients, "build", build, "mmr", mmr, "only_new_cities", only_new_cities, "allow_after_days", allow_after_days, "allow_after_offensive", allow_after_offensive, "allow_after_infra", allow_after_infra, "allow_all", allow_all, "allow_after_land_or_project", allow_after_land_or_project, "econRole", econRole, "selfRole", selfRole, "bracket", bracket, "useReceiverBracket", useReceiverBracket, "maxTotal", maxTotal, "maxDay", maxDay, "maxGranterDay", maxGranterDay, "maxGranterTotal", maxGranterTotal, "expireTime", expireTime, "decayTime", decayTime, "allowIgnore", allowIgnore, "repeatable", repeatable, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateCity")
                public static class city extends CommandRef {
                    public static final city cmd = new city();
                    public city create(String name, String allowedRecipients, String minCity, String maxCity, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String expireTime, String decayTime, String allowIgnore, String force) {
                        return createArgs("name", name, "allowedRecipients", allowedRecipients, "minCity", minCity, "maxCity", maxCity, "econRole", econRole, "selfRole", selfRole, "bracket", bracket, "useReceiverBracket", useReceiverBracket, "maxTotal", maxTotal, "maxDay", maxDay, "maxGranterDay", maxGranterDay, "maxGranterTotal", maxGranterTotal, "expireTime", expireTime, "decayTime", decayTime, "allowIgnore", allowIgnore, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateInfra")
                public static class infra extends CommandRef {
                    public static final infra cmd = new infra();
                    public infra create(String name, String allowedRecipients, String level, String onlyNewCities, String requireNOffensives, String allowRebuild, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String expireTime, String decayTime, String allowIgnore, String repeatable, String force) {
                        return createArgs("name", name, "allowedRecipients", allowedRecipients, "level", level, "onlyNewCities", onlyNewCities, "requireNOffensives", requireNOffensives, "allowRebuild", allowRebuild, "econRole", econRole, "selfRole", selfRole, "bracket", bracket, "useReceiverBracket", useReceiverBracket, "maxTotal", maxTotal, "maxDay", maxDay, "maxGranterDay", maxGranterDay, "maxGranterTotal", maxGranterTotal, "expireTime", expireTime, "decayTime", decayTime, "allowIgnore", allowIgnore, "repeatable", repeatable, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateLand")
                public static class land extends CommandRef {
                    public static final land cmd = new land();
                    public land create(String name, String allowedRecipients, String level, String onlyNewCities, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String expireTime, String decayTime, String allowIgnore, String repeatable, String force) {
                        return createArgs("name", name, "allowedRecipients", allowedRecipients, "level", level, "onlyNewCities", onlyNewCities, "econRole", econRole, "selfRole", selfRole, "bracket", bracket, "useReceiverBracket", useReceiverBracket, "maxTotal", maxTotal, "maxDay", maxDay, "maxGranterDay", maxGranterDay, "maxGranterTotal", maxGranterTotal, "expireTime", expireTime, "decayTime", decayTime, "allowIgnore", allowIgnore, "repeatable", repeatable, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateProject")
                public static class project extends CommandRef {
                    public static final project cmd = new project();
                    public project create(String name, String allowedRecipients, String project, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String expireTime, String decayTime, String allowIgnore, String force) {
                        return createArgs("name", name, "allowedRecipients", allowedRecipients, "project", project, "econRole", econRole, "selfRole", selfRole, "bracket", bracket, "useReceiverBracket", useReceiverBracket, "maxTotal", maxTotal, "maxDay", maxDay, "maxGranterDay", maxGranterDay, "maxGranterTotal", maxGranterTotal, "expireTime", expireTime, "decayTime", decayTime, "allowIgnore", allowIgnore, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateRaws")
                public static class raws extends CommandRef {
                    public static final raws cmd = new raws();
                    public raws create(String name, String allowedRecipients, String days, String overdrawPercent, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String expireTime, String decayTime, String allowIgnore, String nonRepeatable, String force) {
                        return createArgs("name", name, "allowedRecipients", allowedRecipients, "days", days, "overdrawPercent", overdrawPercent, "econRole", econRole, "selfRole", selfRole, "bracket", bracket, "useReceiverBracket", useReceiverBracket, "maxTotal", maxTotal, "maxDay", maxDay, "maxGranterDay", maxGranterDay, "maxGranterTotal", maxGranterTotal, "expireTime", expireTime, "decayTime", decayTime, "allowIgnore", allowIgnore, "nonRepeatable", nonRepeatable, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateWarchest")
                public static class warchest extends CommandRef {
                    public static final warchest cmd = new warchest();
                    public warchest create(String name, String allowedRecipients, String allowancePerCity, String trackDays, String subtractExpenditure, String overdrawPercent, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String expireTime, String decayTime, String allowIgnore, String nonRepeatable, String force) {
                        return createArgs("name", name, "allowedRecipients", allowedRecipients, "allowancePerCity", allowancePerCity, "trackDays", trackDays, "subtractExpenditure", subtractExpenditure, "overdrawPercent", overdrawPercent, "econRole", econRole, "selfRole", selfRole, "bracket", bracket, "useReceiverBracket", useReceiverBracket, "maxTotal", maxTotal, "maxDay", maxDay, "maxGranterDay", maxGranterDay, "maxGranterTotal", maxGranterTotal, "expireTime", expireTime, "decayTime", decayTime, "allowIgnore", allowIgnore, "nonRepeatable", nonRepeatable, "force", force);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateDelete")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
                public delete create(String template, String force) {
                    return createArgs("template", template, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateDisable")
            public static class disable extends CommandRef {
                public static final disable cmd = new disable();
                public disable create(String template, String force) {
                    return createArgs("template", template, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateEnabled")
            public static class enable extends CommandRef {
                public static final enable cmd = new enable();
                public enable create(String template) {
                    return createArgs("template", template);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateInfo")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create(String template, String receiver, String value, String show_command) {
                    return createArgs("template", template, "receiver", receiver, "value", value, "show_command", show_command);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateList")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String category, String listDisabled) {
                    return createArgs("category", category, "listDisabled", listDisabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateSend")
            public static class send extends CommandRef {
                public static final send cmd = new send();
                public send create(String template, String receiver, String expire, String decay, String ignore, String customValue, String escrowMode, String force) {
                    return createArgs("template", template, "receiver", receiver, "expire", expire, "decay", decay, "ignore", ignore, "customValue", customValue, "escrowMode", escrowMode, "force", force);
                }
            }
        }
        public static class help{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="argument")
            public static class argument extends CommandRef {
                public static final argument cmd = new argument();
                public argument create(String argument, String skipOptionalArgs) {
                    return createArgs("argument", argument, "skipOptionalArgs", skipOptionalArgs);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="command")
            public static class command extends CommandRef {
                public static final command cmd = new command();
                public command create(String command) {
                    return createArgs("command", command);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="find_argument")
            public static class find_argument extends CommandRef {
                public static final find_argument cmd = new find_argument();
                public find_argument create(String search, String instructions, String useGPT, String numResults) {
                    return createArgs("search", search, "instructions", instructions, "useGPT", useGPT, "numResults", numResults);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="find_command2")
            public static class find_command extends CommandRef {
                public static final find_command cmd = new find_command();
                public find_command create(String search, String instructions, String useGPT, String numResults) {
                    return createArgs("search", search, "instructions", instructions, "useGPT", useGPT, "numResults", numResults);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="find_placeholder")
            public static class find_nation_placeholder extends CommandRef {
                public static final find_nation_placeholder cmd = new find_nation_placeholder();
                public find_nation_placeholder create(String search, String instructions, String useGPT, String numResults) {
                    return createArgs("search", search, "instructions", instructions, "useGPT", useGPT, "numResults", numResults);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="find_setting")
            public static class find_setting extends CommandRef {
                public static final find_setting cmd = new find_setting();
                public find_setting create(String query, String num_results) {
                    return createArgs("query", query, "num_results", num_results);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="moderation_check")
            public static class moderation_check extends CommandRef {
                public static final moderation_check cmd = new moderation_check();
                public moderation_check create(String input) {
                    return createArgs("input", input);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="nation_placeholder")
            public static class nation_placeholder extends CommandRef {
                public static final nation_placeholder cmd = new nation_placeholder();
                public nation_placeholder create(String command) {
                    return createArgs("command", command);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="query")
            public static class query extends CommandRef {
                public static final query cmd = new query();
                public query create(String input) {
                    return createArgs("input", input);
                }
            }
        }
        public static class infra{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="InfraCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
                public cost create(String currentInfra, String maxInfra, String urbanization, String center_for_civil_engineering, String advanced_engineering_corps, String government_support_agency, String cities) {
                    return createArgs("currentInfra", currentInfra, "maxInfra", maxInfra, "urbanization", urbanization, "center_for_civil_engineering", center_for_civil_engineering, "advanced_engineering_corps", advanced_engineering_corps, "government_support_agency", government_support_agency, "cities", cities);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="infraROI")
            public static class roi extends CommandRef {
                public static final roi cmd = new roi();
                public roi create(String city, String infraLevel, String continent, String rads, String forceProjects, String openMarkets, String mmr, String land) {
                    return createArgs("city", city, "infraLevel", infraLevel, "continent", continent, "rads", rads, "forceProjects", forceProjects, "openMarkets", openMarkets, "mmr", mmr, "land", land);
                }
            }
        }
        public static class interview{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="adRanking")
            public static class adRanking extends CommandRef {
                public static final adRanking cmd = new adRanking();
                public adRanking create(String uploadFile) {
                    return createArgs("uploadFile", uploadFile);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="interview")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String user) {
                    return createArgs("user", user);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="iaCat")
            public static class iacat extends CommandRef {
                public static final iacat cmd = new iacat();
                public iacat create(String category) {
                    return createArgs("category", category);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="iachannels")
            public static class iachannels extends CommandRef {
                public static final iachannels cmd = new iachannels();
                public iachannels create(String filter, String time) {
                    return createArgs("filter", filter, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="incentiveRanking")
            public static class incentiveRanking extends CommandRef {
                public static final incentiveRanking cmd = new incentiveRanking();
                public incentiveRanking create(String timestamp) {
                    return createArgs("timestamp", timestamp);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="interviewMessage")
            public static class interviewMessage extends CommandRef {
                public static final interviewMessage cmd = new interviewMessage();
                public interviewMessage create(String nations, String message, String pingMentee) {
                    return createArgs("nations", nations, "message", message, "pingMentee", pingMentee);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="listMentors")
            public static class listMentors extends CommandRef {
                public static final listMentors cmd = new listMentors();
                public listMentors create(String mentors, String mentees, String timediff, String includeAudit, String ignoreUnallocatedMembers, String listIdleMentors) {
                    return createArgs("mentors", mentors, "mentees", mentees, "timediff", timediff, "includeAudit", includeAudit, "ignoreUnallocatedMembers", ignoreUnallocatedMembers, "listIdleMentors", listIdleMentors);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mentee")
            public static class mentee extends CommandRef {
                public static final mentee cmd = new mentee();
                public mentee create(String mentee, String force) {
                    return createArgs("mentee", mentee, "force", force);
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
            public static class questions{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setInterview")
                public static class set extends CommandRef {
                    public static final set cmd = new set();
                    public set create(String message) {
                        return createArgs("message", message);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="viewInterview")
                public static class view extends CommandRef {
                    public static final view cmd = new view();
                    public view create() {
                        return createArgs();
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="recruitmentRankings")
            public static class recruitmentRankings extends CommandRef {
                public static final recruitmentRankings cmd = new recruitmentRankings();
                public recruitmentRankings create(String cutoff, String topX, String uploadFile) {
                    return createArgs("cutoff", cutoff, "topX", topX, "uploadFile", uploadFile);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setReferrer")
            public static class setReferrer extends CommandRef {
                public static final setReferrer cmd = new setReferrer();
                public setReferrer create(String user) {
                    return createArgs("user", user);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="sortInterviews")
            public static class sortInterviews extends CommandRef {
                public static final sortInterviews cmd = new sortInterviews();
                public sortInterviews create(String sortCategorized) {
                    return createArgs("sortCategorized", sortCategorized);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="syncInterviews")
            public static class syncInterviews extends CommandRef {
                public static final syncInterviews cmd = new syncInterviews();
                public syncInterviews create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="unassignMentee")
            public static class unassignMentee extends CommandRef {
                public static final unassignMentee cmd = new unassignMentee();
                public unassignMentee create(String mentee) {
                    return createArgs("mentee", mentee);
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
        public static class land{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="LandCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
                public cost create(String currentLand, String maxLand, String rapidExpansion, String arable_land_agency, String advanced_engineering_corps, String government_support_agency, String cities) {
                    return createArgs("currentLand", currentLand, "maxLand", maxLand, "rapidExpansion", rapidExpansion, "arable_land_agency", arable_land_agency, "advanced_engineering_corps", advanced_engineering_corps, "government_support_agency", government_support_agency, "cities", cities);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="landROI")
            public static class roi extends CommandRef {
                public static final roi cmd = new roi();
                public roi create(String city, String landLevel, String continent, String rads, String forceProjects, String openMarkets, String mmr, String infra) {
                    return createArgs("city", city, "landLevel", landLevel, "continent", continent, "rads", rads, "forceProjects", forceProjects, "openMarkets", openMarkets, "mmr", mmr, "infra", infra);
                }
            }
        }
        public static class mail{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mailCommandOutput")
            public static class command extends CommandRef {
                public static final command cmd = new command();
                public command create(String nations, String subject, String command, String body, String sheet, String sendDM, String skipMail) {
                    return createArgs("nations", nations, "subject", subject, "command", command, "body", body, "sheet", sheet, "sendDM", sendDM, "skipMail", skipMail);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="testRecruitMessage")
            public static class recruit extends CommandRef {
                public static final recruit cmd = new recruit();
                public recruit create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="reply")
            public static class reply extends CommandRef {
                public static final reply cmd = new reply();
                public reply create(String receiver, String url, String message, String sender) {
                    return createArgs("receiver", receiver, "url", url, "message", message, "sender", sender);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mail")
            public static class send extends CommandRef {
                public static final send cmd = new send();
                public send create(String nations, String subject, String message, String confirm, String sendFromGuildAccount, String apiKey) {
                    return createArgs("nations", nations, "subject", subject, "message", message, "confirm", confirm, "sendFromGuildAccount", sendFromGuildAccount, "apiKey", apiKey);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mailSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
                public sheet create(String sheet, String force, String dm, String skipMail) {
                    return createArgs("sheet", sheet, "force", force, "dm", dm, "skipMail", skipMail);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="mailTargets")
            public static class targets extends CommandRef {
                public static final targets cmd = new targets();
                public targets create(String blitzSheet, String spySheet, String allowedNations, String allowedEnemies, String header, String sendFromGuildAccount, String apiKey, String hideDefaultBlurb, String force, String useLeader, String dm) {
                    return createArgs("blitzSheet", blitzSheet, "spySheet", spySheet, "allowedNations", allowedNations, "allowedEnemies", allowedEnemies, "header", header, "sendFromGuildAccount", sendFromGuildAccount, "apiKey", apiKey, "hideDefaultBlurb", hideDefaultBlurb, "force", force, "useLeader", useLeader, "dm", dm);
                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="me")
        public static class me extends CommandRef {
            public static final me cmd = new me();
            public me create(String snapshotDate) {
                return createArgs("snapshotDate", snapshotDate);
            }
        }
        public static class modal{
            @AutoRegister(clazz=link.locutus.discord.web.test.TestCommands.class,method="modal")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String command, String arguments, String defaults) {
                    return createArgs("command", command, "arguments", arguments, "defaults", defaults);
                }
            }
        }
        public static class nation{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="TurnTimer")
            public static class TurnTimer extends CommandRef {
                public static final TurnTimer cmd = new TurnTimer();
                public TurnTimer create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="beigeTurns")
            public static class beigeTurns extends CommandRef {
                public static final beigeTurns cmd = new beigeTurns();
                public beigeTurns create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="canIBeige")
            public static class canIBeige extends CommandRef {
                public static final canIBeige cmd = new canIBeige();
                public canIBeige create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="leftAA")
            public static class departures extends CommandRef {
                public static final departures cmd = new departures();
                public departures create(String nationOrAlliance, String time, String filter, String ignoreInactives, String ignoreVM, String ignoreMembers, String listIds, String sheet) {
                    return createArgs("nationOrAlliance", nationOrAlliance, "time", time, "filter", filter, "ignoreInactives", ignoreInactives, "ignoreVM", ignoreVM, "ignoreMembers", ignoreMembers, "listIds", listIds, "sheet", sheet);
                }
            }
            public static class list{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="viewBans")
                public static class bans extends CommandRef {
                    public static final bans cmd = new bans();
                    public bans create(String nationId) {
                        return createArgs("nationId", nationId);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="inactive")
                public static class inactive extends CommandRef {
                    public static final inactive cmd = new inactive();
                    public inactive create(String nations, String days, String includeApplicants, String includeVacationMode, String page) {
                        return createArgs("nations", nations, "days", days, "includeApplicants", includeApplicants, "includeVacationMode", includeVacationMode, "page", page);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="multi")
                public static class multi extends CommandRef {
                    public static final multi cmd = new multi();
                    public multi create(String nation) {
                        return createArgs("nation", nation);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="rebuy")
                public static class rebuy extends CommandRef {
                    public static final rebuy cmd = new rebuy();
                    public rebuy create(String nation) {
                        return createArgs("nation", nation);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="loot")
            public static class loot extends CommandRef {
                public static final loot cmd = new loot();
                public loot create(String nationOrAlliance, String nationScore, String pirate) {
                    return createArgs("nationOrAlliance", nationOrAlliance, "nationScore", nationScore, "pirate", pirate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="moneyTrades")
            public static class moneyTrades extends CommandRef {
                public static final moneyTrades cmd = new moneyTrades();
                public moneyTrades create(String nation, String time, String forceUpdate, String addBalance) {
                    return createArgs("nation", nation, "time", time, "forceUpdate", forceUpdate, "addBalance", addBalance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="reroll")
            public static class reroll extends CommandRef {
                public static final reroll cmd = new reroll();
                public reroll create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="revenue")
            public static class revenue extends CommandRef {
                public static final revenue cmd = new revenue();
                public revenue create(String nations, String includeUntaxable, String excludeNationBonus, String rads, String forceAtWar, String forceAtPeace, String includeWarCosts, String snapshotDate) {
                    return createArgs("nations", nations, "includeUntaxable", includeUntaxable, "excludeNationBonus", excludeNationBonus, "rads", rads, "forceAtWar", forceAtWar, "forceAtPeace", forceAtPeace, "includeWarCosts", includeWarCosts, "snapshotDate", snapshotDate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="revenueSheet")
            public static class revenueSheet extends CommandRef {
                public static final revenueSheet cmd = new revenueSheet();
                public revenueSheet create(String nations, String sheet, String snapshotTime) {
                    return createArgs("nations", nations, "sheet", sheet, "snapshotTime", snapshotTime);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="score")
            public static class score extends CommandRef {
                public static final score cmd = new score();
                public score create(String nation, String cities, String soldiers, String tanks, String aircraft, String ships, String missiles, String nukes, String projects, String avg_infra, String infraTotal, String builtMMR) {
                    return createArgs("nation", nation, "cities", cities, "soldiers", soldiers, "tanks", tanks, "aircraft", aircraft, "ships", ships, "missiles", missiles, "nukes", nukes, "projects", projects, "avg_infra", avg_infra, "infraTotal", infraTotal, "builtMMR", builtMMR);
                }
            }
            public static class set{
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setBracket")
                public static class taxbracket extends CommandRef {
                    public static final taxbracket cmd = new taxbracket();
                    public taxbracket create(String nations, String bracket, String internalTaxRate) {
                        return createArgs("nations", nations, "bracket", bracket, "internalTaxRate", internalTaxRate);
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
            }
            public static class sheet{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="NationSheet")
                public static class NationSheet extends CommandRef {
                    public static final NationSheet cmd = new NationSheet();
                    public NationSheet create(String nations, String columns, String snapshotTime, String updateSpies, String sheet) {
                        return createArgs("nations", nations, "columns", columns, "snapshotTime", snapshotTime, "updateSpies", updateSpies, "sheet", sheet);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectSlots")
            public static class slots extends CommandRef {
                public static final slots cmd = new slots();
                public slots create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="spies")
            public static class spies extends CommandRef {
                public static final spies cmd = new spies();
                public spies create(String nation, String spiesUsed, String requiredSafety) {
                    return createArgs("nation", nation, "spiesUsed", spiesUsed, "requiredSafety", requiredSafety);
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="nationRanking")
                public static class nationRanking extends CommandRef {
                    public static final nationRanking cmd = new nationRanking();
                    public nationRanking create(String nations, String attribute, String groupByAlliance, String reverseOrder, String snapshotDate, String total) {
                        return createArgs("nations", nations, "attribute", attribute, "groupByAlliance", groupByAlliance, "reverseOrder", reverseOrder, "snapshotDate", snapshotDate, "total", total);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByNation")
                public static class warStatusRankingByNation extends CommandRef {
                    public static final warStatusRankingByNation cmd = new warStatusRankingByNation();
                    public warStatusRankingByNation create(String attackers, String defenders, String time) {
                        return createArgs("attackers", attackers, "defenders", defenders, "time", time);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="stockpile")
            public static class stockpile extends CommandRef {
                public static final stockpile cmd = new stockpile();
                public stockpile create(String nationOrAlliance) {
                    return createArgs("nationOrAlliance", nationOrAlliance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="unitHistory")
            public static class unitHistory extends CommandRef {
                public static final unitHistory cmd = new unitHistory();
                public unitHistory create(String nation, String unit, String page) {
                    return createArgs("nation", nation, "unit", unit, "page", page);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="wars")
            public static class wars extends CommandRef {
                public static final wars cmd = new wars();
                public wars create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="who")
            public static class who extends CommandRef {
                public static final who cmd = new who();
                public who create(String nationOrAlliances, String sortBy, String list, String listAlliances, String listRawUserIds, String listMentions, String listInfo, String listChannels, String snapshotDate, String page) {
                    return createArgs("nationOrAlliances", nationOrAlliances, "sortBy", sortBy, "list", list, "listAlliances", listAlliances, "listRawUserIds", listRawUserIds, "listMentions", listMentions, "listInfo", listInfo, "listChannels", listChannels, "snapshotDate", snapshotDate, "page", page);
                }
            }
        }
        public static class newsletter{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="autosend")
            public static class auto extends CommandRef {
                public static final auto cmd = new auto();
                public auto create(String newsletter, String interval, String pingRole) {
                    return createArgs("newsletter", newsletter, "interval", interval, "pingRole", pingRole);
                }
            }
            public static class channel{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="channelAdd")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                    public add create(String newsletter, String channel) {
                        return createArgs("newsletter", newsletter, "channel", channel);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="channelRemove")
                public static class remove extends CommandRef {
                    public static final remove cmd = new remove();
                    public remove create(String newsletter, String channel) {
                        return createArgs("newsletter", newsletter, "channel", channel);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="create")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String name) {
                    return createArgs("name", name);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="delete")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
                public delete create(String newsletter, String force) {
                    return createArgs("newsletter", newsletter, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="info")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create(String newsletter, String listNations) {
                    return createArgs("newsletter", newsletter, "listNations", listNations);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="list")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="send")
            public static class send extends CommandRef {
                public static final send cmd = new send();
                public send create(String newsletter, String sendSince, String document, String endDate) {
                    return createArgs("newsletter", newsletter, "sendSince", sendSince, "document", document, "endDate", endDate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="subscribe")
            public static class subscribe extends CommandRef {
                public static final subscribe cmd = new subscribe();
                public subscribe create(String newsletter, String nations) {
                    return createArgs("newsletter", newsletter, "nations", nations);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="unsubscribe")
            public static class unsubscribe extends CommandRef {
                public static final unsubscribe cmd = new unsubscribe();
                public unsubscribe create(String newsletter, String nations) {
                    return createArgs("newsletter", newsletter, "nations", nations);
                }
            }
        }
        public static class offshore{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="compareOffshoreStockpile")
            public static class accountSheet extends CommandRef {
                public static final accountSheet cmd = new accountSheet();
                public accountSheet create(String sheet) {
                    return createArgs("sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="addOffshore")
            public static class add extends CommandRef {
                public static final add cmd = new add();
                public add create(String offshoreAlliance, String newAccount, String importAccount, String force) {
                    return createArgs("offshoreAlliance", offshoreAlliance, "newAccount", newAccount, "importAccount", importAccount, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="findOffshores")
            public static class find extends CommandRef {
                public static final find cmd = new find();
                public find create(String cutoff, String enemiesList, String alliesList) {
                    return createArgs("cutoff", cutoff, "enemiesList", enemiesList, "alliesList", alliesList);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="findOffshore")
            public static class findForCoalition extends CommandRef {
                public static final findForCoalition cmd = new findForCoalition();
                public findForCoalition create(String alliance, String cutoffMs) {
                    return createArgs("alliance", alliance, "cutoffMs", cutoffMs);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="listOffshores")
            public static class listAllInOrbis extends CommandRef {
                public static final listAllInOrbis cmd = new listAllInOrbis();
                public listAllInOrbis create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="markAsOffshore")
            public static class markAsOffshore extends CommandRef {
                public static final markAsOffshore cmd = new markAsOffshore();
                public markAsOffshore create(String offshore, String parent) {
                    return createArgs("offshore", offshore, "parent", parent);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="offshore")
            public static class send extends CommandRef {
                public static final send cmd = new send();
                public send create(String to, String account, String keepAmount, String sendAmount) {
                    return createArgs("to", to, "account", account, "keepAmount", keepAmount, "sendAmount", sendAmount);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="unlockTransfers")
            public static class unlockTransfers extends CommandRef {
                public static final unlockTransfers cmd = new unlockTransfers();
                public unlockTransfers create(String nationOrAllianceOrGuild, String unlockAll) {
                    return createArgs("nationOrAllianceOrGuild", nationOrAllianceOrGuild, "unlockAll", unlockAll);
                }
            }
        }
        public static class project{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
                public cost create(String projects, String technologicalAdvancement, String governmentSupportAgency, String nations, String sheet, String ignoreProjectSlots, String ignoreRequirements, String ignoreProjectCity) {
                    return createArgs("projects", projects, "technologicalAdvancement", technologicalAdvancement, "governmentSupportAgency", governmentSupportAgency, "nations", nations, "sheet", sheet, "ignoreProjectSlots", ignoreProjectSlots, "ignoreRequirements", ignoreRequirements, "ignoreProjectCity", ignoreProjectCity);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="projectCostCsv")
            public static class costsheet extends CommandRef {
                public static final costsheet cmd = new costsheet();
                public costsheet create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="ProjectSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
                public sheet create(String nations, String sheet, String snapshotTime) {
                    return createArgs("nations", nations, "sheet", sheet, "snapshotTime", snapshotTime);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectSlots")
            public static class slots extends CommandRef {
                public static final slots cmd = new slots();
                public slots create(String nation) {
                    return createArgs("nation", nation);
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
        public static class report{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="createReport")
            public static class add extends CommandRef {
                public static final add cmd = new add();
                public add create(String type, String message, String nation, String discord_user_id, String imageEvidenceUrl, String forum_post, String news_post, String updateReport, String force) {
                    return createArgs("type", type, "message", message, "nation", nation, "discord_user_id", discord_user_id, "imageEvidenceUrl", imageEvidenceUrl, "forum_post", forum_post, "news_post", news_post, "updateReport", updateReport, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="riskFactors")
            public static class analyze extends CommandRef {
                public static final analyze cmd = new analyze();
                public analyze create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="approveReport")
            public static class approve extends CommandRef {
                public static final approve cmd = new approve();
                public approve create(String report, String force) {
                    return createArgs("report", report, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="ban")
            public static class ban extends CommandRef {
                public static final ban cmd = new ban();
                public ban create(String nation, String timestamp, String reason, String force) {
                    return createArgs("nation", nation, "timestamp", timestamp, "reason", reason, "force", force);
                }
            }
            public static class comment{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="comment")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                    public add create(String report, String comment, String force) {
                        return createArgs("report", report, "comment", comment, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="removeComment")
                public static class delete extends CommandRef {
                    public static final delete cmd = new delete();
                    public delete create(String report, String nationCommenting, String force) {
                        return createArgs("report", report, "nationCommenting", nationCommenting, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="purgeComments")
                public static class purge extends CommandRef {
                    public static final purge cmd = new purge();
                    public purge create(String report, String nation_id, String discord_id, String force) {
                        return createArgs("report", report, "nation_id", nation_id, "discord_id", discord_id, "force", force);
                    }
                }
            }
            public static class loan{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="addLoan")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                    public add create(String receiver, String status, String overwriteLoan, String principal, String remaining, String amountPaid, String dueDate, String allianceLending, String force) {
                        return createArgs("receiver", receiver, "status", status, "overwriteLoan", overwriteLoan, "principal", principal, "remaining", remaining, "amountPaid", amountPaid, "dueDate", dueDate, "allianceLending", allianceLending, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="purgeLoans")
                public static class purge extends CommandRef {
                    public static final purge cmd = new purge();
                    public purge create(String guildOrAllianceId, String force) {
                        return createArgs("guildOrAllianceId", guildOrAllianceId, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="deleteLoan")
                public static class remove extends CommandRef {
                    public static final remove cmd = new remove();
                    public remove create(String loan, String force) {
                        return createArgs("loan", loan, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="getLoanSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                    public sheet create(String nations, String sheet, String loanStatus) {
                        return createArgs("nations", nations, "sheet", sheet, "loanStatus", loanStatus);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="updateLoan")
                public static class update extends CommandRef {
                    public static final update cmd = new update();
                    public update create(String loan, String principal, String remaining, String amountPaid, String dueDate, String force) {
                        return createArgs("loan", loan, "principal", principal, "remaining", remaining, "amountPaid", amountPaid, "dueDate", dueDate, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="markAllLoansAsUpdated")
                public static class update_all extends CommandRef {
                    public static final update_all cmd = new update_all();
                    public update_all create(String loanStatus, String force) {
                        return createArgs("loanStatus", loanStatus, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="importLoans")
                public static class upload extends CommandRef {
                    public static final upload cmd = new upload();
                    public upload create(String sheet, String defaultStatus, String overwriteLoans, String overwriteSameNation, String addLoans) {
                        return createArgs("sheet", sheet, "defaultStatus", defaultStatus, "overwriteLoans", overwriteLoans, "overwriteSameNation", overwriteSameNation, "addLoans", addLoans);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="purgeReports")
            public static class purge extends CommandRef {
                public static final purge cmd = new purge();
                public purge create(String nationIdReported, String userIdReported, String reportingNation, String reportingUser, String force) {
                    return createArgs("nationIdReported", nationIdReported, "userIdReported", userIdReported, "reportingNation", reportingNation, "reportingUser", reportingUser, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="removeReport")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
                public remove create(String report, String force) {
                    return createArgs("report", report, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="searchReports")
            public static class search extends CommandRef {
                public static final search cmd = new search();
                public search create(String nationIdReported, String userIdReported, String reportingNation, String reportingUser) {
                    return createArgs("nationIdReported", nationIdReported, "userIdReported", userIdReported, "reportingNation", reportingNation, "reportingUser", reportingUser);
                }
            }
            public static class sheet{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="reportSheet")
                public static class generate extends CommandRef {
                    public static final generate cmd = new generate();
                    public generate create(String sheet) {
                        return createArgs("sheet", sheet);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="showReport")
            public static class show extends CommandRef {
                public static final show cmd = new show();
                public show create(String report) {
                    return createArgs("report", report);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="unban")
            public static class unban extends CommandRef {
                public static final unban cmd = new unban();
                public unban create(String nation, String force) {
                    return createArgs("nation", nation, "force", force);
                }
            }
            public static class upload{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="importLegacyBlacklist")
                public static class legacy_reports extends CommandRef {
                    public static final legacy_reports cmd = new legacy_reports();
                    public legacy_reports create(String sheet) {
                        return createArgs("sheet", sheet);
                    }
                }
            }
        }
        public static class role{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addRoleToAllMembers")
            public static class addRoleToAllMembers extends CommandRef {
                public static final addRoleToAllMembers cmd = new addRoleToAllMembers();
                public addRoleToAllMembers create(String role) {
                    return createArgs("role", role);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="autoroleall")
            public static class autoassign extends CommandRef {
                public static final autoassign cmd = new autoassign();
                public autoassign create(String force) {
                    return createArgs("force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="autorole")
            public static class autorole extends CommandRef {
                public static final autorole cmd = new autorole();
                public autorole create(String member, String force) {
                    return createArgs("member", member, "force", force);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="hasRole")
            public static class hasRole extends CommandRef {
                public static final hasRole cmd = new hasRole();
                public hasRole create(String user, String role) {
                    return createArgs("user", user, "role", role);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="mask")
            public static class mask extends CommandRef {
                public static final mask cmd = new mask();
                public mask create(String members, String role, String value, String toggleMaskFromOthers) {
                    return createArgs("members", members, "role", role, "value", value, "toggleMaskFromOthers", toggleMaskFromOthers);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="maskSheet")
            public static class mask_sheet extends CommandRef {
                public static final mask_sheet cmd = new mask_sheet();
                public mask_sheet create(String sheet, String removeRoles, String removeAll, String listMissing, String force) {
                    return createArgs("sheet", sheet, "removeRoles", removeRoles, "removeAll", removeAll, "listMissing", listMissing, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="optOut")
            public static class optOut extends CommandRef {
                public static final optOut cmd = new optOut();
                public optOut create(String optOut) {
                    return createArgs("optOut", optOut);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="removeAssignableRole")
            public static class removeAssignableRole extends CommandRef {
                public static final removeAssignableRole cmd = new removeAssignableRole();
                public removeAssignableRole create(String requireRole, String assignableRoles) {
                    return createArgs("requireRole", requireRole, "assignableRoles", assignableRoles);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="aliasRole")
            public static class setAlias extends CommandRef {
                public static final setAlias cmd = new setAlias();
                public setAlias create(String locutusRole, String discordRole, String alliance, String removeRole) {
                    return createArgs("locutusRole", locutusRole, "discordRole", discordRole, "alliance", alliance, "removeRole", removeRole);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="unregisterRole")
            public static class unregister extends CommandRef {
                public static final unregister cmd = new unregister();
                public unregister create(String locutusRole, String alliance) {
                    return createArgs("locutusRole", locutusRole, "alliance", alliance);
                }
            }
        }
        public static class selection_alias{
            public static class add{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="ALLIANCES")
                public static class alliance extends CommandRef {
                    public static final alliance cmd = new alliance();
                    public alliance create(String name, String alliances) {
                        return createArgs("name", name, "alliances", alliances);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="ATTACK_TYPES")
                public static class attacktype extends CommandRef {
                    public static final attacktype cmd = new attacktype();
                    public attacktype create(String name, String attack_types) {
                        return createArgs("name", name, "attack_types", attack_types);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="AUDIT_TYPES")
                public static class audittype extends CommandRef {
                    public static final audittype cmd = new audittype();
                    public audittype create(String name, String audit_types) {
                        return createArgs("name", name, "audit_types", audit_types);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="BANS")
                public static class ban extends CommandRef {
                    public static final ban cmd = new ban();
                    public ban create(String name, String bans) {
                        return createArgs("name", name, "bans", bans);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="BOUNTIES")
                public static class bounty extends CommandRef {
                    public static final bounty cmd = new bounty();
                    public bounty create(String name, String bounties) {
                        return createArgs("name", name, "bounties", bounties);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="BUILDINGS")
                public static class building extends CommandRef {
                    public static final building cmd = new building();
                    public building create(String name, String Buildings) {
                        return createArgs("name", name, "Buildings", Buildings);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="CITIES")
                public static class city extends CommandRef {
                    public static final city cmd = new city();
                    public city create(String name, String cities) {
                        return createArgs("name", name, "cities", cities);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="CONTINENTS")
                public static class continent extends CommandRef {
                    public static final continent cmd = new continent();
                    public continent create(String name, String continents) {
                        return createArgs("name", name, "continents", continents);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="GUILDS")
                public static class guild extends CommandRef {
                    public static final guild cmd = new guild();
                    public guild create(String name, String guilds) {
                        return createArgs("name", name, "guilds", guilds);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="SETTINGS")
                public static class guildsetting extends CommandRef {
                    public static final guildsetting cmd = new guildsetting();
                    public guildsetting create(String name, String settings) {
                        return createArgs("name", name, "settings", settings);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="ATTACKS")
                public static class iattack extends CommandRef {
                    public static final iattack cmd = new iattack();
                    public iattack create(String name, String attacks) {
                        return createArgs("name", name, "attacks", attacks);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="MILITARY_UNITS")
                public static class militaryunit extends CommandRef {
                    public static final militaryunit cmd = new militaryunit();
                    public militaryunit create(String name, String military_units) {
                        return createArgs("name", name, "military_units", military_units);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="NATIONS")
                public static class nation extends CommandRef {
                    public static final nation cmd = new nation();
                    public nation create(String name, String nations) {
                        return createArgs("name", name, "nations", nations);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="NATION_COLORS")
                public static class nationcolor extends CommandRef {
                    public static final nationcolor cmd = new nationcolor();
                    public nationcolor create(String name, String colors) {
                        return createArgs("name", name, "colors", colors);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="NATION_LIST")
                public static class nationlist extends CommandRef {
                    public static final nationlist cmd = new nationlist();
                    public nationlist create(String name, String nationlists) {
                        return createArgs("name", name, "nationlists", nationlists);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="NATION_OR_ALLIANCE")
                public static class nationoralliance extends CommandRef {
                    public static final nationoralliance cmd = new nationoralliance();
                    public nationoralliance create(String name, String nationoralliances) {
                        return createArgs("name", name, "nationoralliances", nationoralliances);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="PROJECTS")
                public static class project extends CommandRef {
                    public static final project cmd = new project();
                    public project create(String name, String projects) {
                        return createArgs("name", name, "projects", projects);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="RESOURCE_TYPES")
                public static class resourcetype extends CommandRef {
                    public static final resourcetype cmd = new resourcetype();
                    public resourcetype create(String name, String resources) {
                        return createArgs("name", name, "resources", resources);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TAX_BRACKETS")
                public static class taxbracket extends CommandRef {
                    public static final taxbracket cmd = new taxbracket();
                    public taxbracket create(String name, String taxbrackets) {
                        return createArgs("name", name, "taxbrackets", taxbrackets);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TAX_DEPOSITS")
                public static class taxdeposit extends CommandRef {
                    public static final taxdeposit cmd = new taxdeposit();
                    public taxdeposit create(String name, String taxes) {
                        return createArgs("name", name, "taxes", taxes);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TREASURES")
                public static class treasure extends CommandRef {
                    public static final treasure cmd = new treasure();
                    public treasure create(String name, String treasures) {
                        return createArgs("name", name, "treasures", treasures);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TREATIES")
                public static class treaty extends CommandRef {
                    public static final treaty cmd = new treaty();
                    public treaty create(String name, String treaties) {
                        return createArgs("name", name, "treaties", treaties);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TREATY_TYPES")
                public static class treatytype extends CommandRef {
                    public static final treatytype cmd = new treatytype();
                    public treatytype create(String name, String treaty_types) {
                        return createArgs("name", name, "treaty_types", treaty_types);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="USERS")
                public static class user extends CommandRef {
                    public static final user cmd = new user();
                    public user create(String name, String users) {
                        return createArgs("name", name, "users", users);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="WARS")
                public static class war extends CommandRef {
                    public static final war cmd = new war();
                    public war create(String name, String wars) {
                        return createArgs("name", name, "wars", wars);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="listSelectionAliases")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String type) {
                    return createArgs("type", type);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="deleteSelectionAlias")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
                public remove create(String selection) {
                    return createArgs("selection", selection);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="renameSelection")
            public static class rename extends CommandRef {
                public static final rename cmd = new rename();
                public rename create(String sheet, String name) {
                    return createArgs("sheet", sheet, "name", name);
                }
            }
        }
        public static class self{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addRole")
            public static class add extends CommandRef {
                public static final add cmd = new add();
                public add create(String member, String addRole) {
                    return createArgs("member", member, "addRole", addRole);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addAssignableRole")
            public static class create extends CommandRef {
                public static final create cmd = new create();
                public create create(String requireRole, String assignableRoles) {
                    return createArgs("requireRole", requireRole, "assignableRoles", assignableRoles);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="listAssignableRoles")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="removeRole")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
                public remove create(String member, String addRole) {
                    return createArgs("member", member, "addRole", addRole);
                }
            }
        }
        public static class settings{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands.class,method="delete")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
                public delete create(String key) {
                    return createArgs("key", key);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands.class,method="info")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create(String key, String value, String listAll) {
                    return createArgs("key", key, "value", value, "listAll", listAll);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands.class,method="sheets")
            public static class sheets extends CommandRef {
                public static final sheets cmd = new sheets();
                public sheets create() {
                    return createArgs();
                }
            }
        }
        public static class settings_artificial_intelligence{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENABLE_GITHUB_COPILOT", field="ENABLE_GITHUB_COPILOT")
            public static class ENABLE_GITHUB_COPILOT extends CommandRef {
                public static final ENABLE_GITHUB_COPILOT cmd = new ENABLE_GITHUB_COPILOT();
                public ENABLE_GITHUB_COPILOT create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="GPT_USAGE_LIMITS", field="GPT_USAGE_LIMITS")
            public static class GPT_USAGE_LIMITS extends CommandRef {
                public static final GPT_USAGE_LIMITS cmd = new GPT_USAGE_LIMITS();
                public GPT_USAGE_LIMITS create(String userTurnLimit, String userDayLimit, String guildTurnLimit, String guildDayLimit) {
                    return createArgs("userTurnLimit", userTurnLimit, "userDayLimit", userDayLimit, "guildTurnLimit", guildTurnLimit, "guildDayLimit", guildDayLimit);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="register_openai_key", field="OPENAI_KEY")
            public static class register_openai_key extends CommandRef {
                public static final register_openai_key cmd = new register_openai_key();
                public register_openai_key create(String apiKey) {
                    return createArgs("apiKey", apiKey);
                }
            }
        }
        public static class settings_audit{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DISABLED_MEMBER_AUDITS", field="DISABLED_MEMBER_AUDITS")
            public static class DISABLED_MEMBER_AUDITS extends CommandRef {
                public static final DISABLED_MEMBER_AUDITS cmd = new DISABLED_MEMBER_AUDITS();
                public DISABLED_MEMBER_AUDITS create(String audits) {
                    return createArgs("audits", audits);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_AUDIT_ALERTS", field="MEMBER_AUDIT_ALERTS")
            public static class MEMBER_AUDIT_ALERTS extends CommandRef {
                public static final MEMBER_AUDIT_ALERTS cmd = new MEMBER_AUDIT_ALERTS();
                public MEMBER_AUDIT_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_LEAVE_ALERT_CHANNEL", field="MEMBER_LEAVE_ALERT_CHANNEL")
            public static class MEMBER_LEAVE_ALERT_CHANNEL extends CommandRef {
                public static final MEMBER_LEAVE_ALERT_CHANNEL cmd = new MEMBER_LEAVE_ALERT_CHANNEL();
                public MEMBER_LEAVE_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_REBUY_INFRA_ALERT", field="MEMBER_REBUY_INFRA_ALERT")
            public static class MEMBER_REBUY_INFRA_ALERT extends CommandRef {
                public static final MEMBER_REBUY_INFRA_ALERT cmd = new MEMBER_REBUY_INFRA_ALERT();
                public MEMBER_REBUY_INFRA_ALERT create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REQUIRED_MMR", field="REQUIRED_MMR")
            public static class REQUIRED_MMR extends CommandRef {
                public static final REQUIRED_MMR cmd = new REQUIRED_MMR();
                public REQUIRED_MMR create(String mmrMap) {
                    return createArgs("mmrMap", mmrMap);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WARCHEST_PER_CITY", field="WARCHEST_PER_CITY")
            public static class WARCHEST_PER_CITY extends CommandRef {
                public static final WARCHEST_PER_CITY cmd = new WARCHEST_PER_CITY();
                public WARCHEST_PER_CITY create(String amount) {
                    return createArgs("amount", amount);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addRequiredMMR", field="REQUIRED_MMR")
            public static class addRequiredMMR extends CommandRef {
                public static final addRequiredMMR cmd = new addRequiredMMR();
                public addRequiredMMR create(String filter, String mmr) {
                    return createArgs("filter", filter, "mmr", mmr);
                }
            }
        }
        public static class settings_bank_access{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ALLOW_UNVERIFIED_BANKING", field="ALLOW_UNVERIFIED_BANKING")
            public static class ALLOW_UNVERIFIED_BANKING extends CommandRef {
                public static final ALLOW_UNVERIFIED_BANKING cmd = new ALLOW_UNVERIFIED_BANKING();
                public ALLOW_UNVERIFIED_BANKING create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BANKER_WITHDRAW_LIMIT", field="BANKER_WITHDRAW_LIMIT")
            public static class BANKER_WITHDRAW_LIMIT extends CommandRef {
                public static final BANKER_WITHDRAW_LIMIT cmd = new BANKER_WITHDRAW_LIMIT();
                public BANKER_WITHDRAW_LIMIT create(String amount) {
                    return createArgs("amount", amount);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BANKER_WITHDRAW_LIMIT_INTERVAL", field="BANKER_WITHDRAW_LIMIT_INTERVAL")
            public static class BANKER_WITHDRAW_LIMIT_INTERVAL extends CommandRef {
                public static final BANKER_WITHDRAW_LIMIT_INTERVAL cmd = new BANKER_WITHDRAW_LIMIT_INTERVAL();
                public BANKER_WITHDRAW_LIMIT_INTERVAL create(String timediff) {
                    return createArgs("timediff", timediff);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DEFAULT_OFFSHORE_ACCOUNT", field="DEFAULT_OFFSHORE_ACCOUNT")
            public static class DEFAULT_OFFSHORE_ACCOUNT extends CommandRef {
                public static final DEFAULT_OFFSHORE_ACCOUNT cmd = new DEFAULT_OFFSHORE_ACCOUNT();
                public DEFAULT_OFFSHORE_ACCOUNT create(String natOrAA) {
                    return createArgs("natOrAA", natOrAA);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="GRANT_LIMIT_DELAY", field="GRANT_LIMIT_DELAY")
            public static class GRANT_LIMIT_DELAY extends CommandRef {
                public static final GRANT_LIMIT_DELAY cmd = new GRANT_LIMIT_DELAY();
                public GRANT_LIMIT_DELAY create(String timediff) {
                    return createArgs("timediff", timediff);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_OFFSHORE", field="MEMBER_CAN_OFFSHORE")
            public static class MEMBER_CAN_OFFSHORE extends CommandRef {
                public static final MEMBER_CAN_OFFSHORE cmd = new MEMBER_CAN_OFFSHORE();
                public MEMBER_CAN_OFFSHORE create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_WITHDRAW", field="MEMBER_CAN_WITHDRAW")
            public static class MEMBER_CAN_WITHDRAW extends CommandRef {
                public static final MEMBER_CAN_WITHDRAW cmd = new MEMBER_CAN_WITHDRAW();
                public MEMBER_CAN_WITHDRAW create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_WITHDRAW_WARTIME", field="MEMBER_CAN_WITHDRAW_WARTIME")
            public static class MEMBER_CAN_WITHDRAW_WARTIME extends CommandRef {
                public static final MEMBER_CAN_WITHDRAW_WARTIME cmd = new MEMBER_CAN_WITHDRAW_WARTIME();
                public MEMBER_CAN_WITHDRAW_WARTIME create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="NON_AA_MEMBERS_CAN_BANK", field="NON_AA_MEMBERS_CAN_BANK")
            public static class NON_AA_MEMBERS_CAN_BANK extends CommandRef {
                public static final NON_AA_MEMBERS_CAN_BANK cmd = new NON_AA_MEMBERS_CAN_BANK();
                public NON_AA_MEMBERS_CAN_BANK create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="PUBLIC_OFFSHORING", field="PUBLIC_OFFSHORING")
            public static class PUBLIC_OFFSHORING extends CommandRef {
                public static final PUBLIC_OFFSHORING cmd = new PUBLIC_OFFSHORING();
                public PUBLIC_OFFSHORING create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RESOURCE_CONVERSION", field="RESOURCE_CONVERSION")
            public static class RESOURCE_CONVERSION extends CommandRef {
                public static final RESOURCE_CONVERSION cmd = new RESOURCE_CONVERSION();
                public RESOURCE_CONVERSION create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ROUTE_ALLIANCE_BANK", field="ROUTE_ALLIANCE_BANK")
            public static class ROUTE_ALLIANCE_BANK extends CommandRef {
                public static final ROUTE_ALLIANCE_BANK cmd = new ROUTE_ALLIANCE_BANK();
                public ROUTE_ALLIANCE_BANK create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WITHDRAW_IGNORES_EXPIRE", field="MEMBER_CAN_WITHDRAW_IGNORES_GRANTS")
            public static class WITHDRAW_IGNORES_EXPIRE extends CommandRef {
                public static final WITHDRAW_IGNORES_EXPIRE cmd = new WITHDRAW_IGNORES_EXPIRE();
                public WITHDRAW_IGNORES_EXPIRE create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addGrantTemplateLimit", field="GRANT_TEMPLATE_LIMITS")
            public static class addGrantTemplateLimit extends CommandRef {
                public static final addGrantTemplateLimit cmd = new addGrantTemplateLimit();
                public addGrantTemplateLimit create(String role, String marketValue) {
                    return createArgs("role", role, "marketValue", marketValue);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addResourceChannel", field="RESOURCE_REQUEST_CHANNEL")
            public static class addResourceChannel extends CommandRef {
                public static final addResourceChannel cmd = new addResourceChannel();
                public addResourceChannel create(String channel, String alliance) {
                    return createArgs("channel", channel, "alliance", alliance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="removeResourceChannel", field="RESOURCE_REQUEST_CHANNEL")
            public static class removeResourceChannel extends CommandRef {
                public static final removeResourceChannel cmd = new removeResourceChannel();
                public removeResourceChannel create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="toggleGrants", field="GRANT_TEMPLATE_BLACKLIST")
            public static class toggleGrants extends CommandRef {
                public static final toggleGrants cmd = new toggleGrants();
                public toggleGrants create(String nation) {
                    return createArgs("nation", nation);
                }
            }
        }
        public static class settings_bank_info{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ADDBALANCE_ALERT_CHANNEL", field="ADDBALANCE_ALERT_CHANNEL")
            public static class ADDBALANCE_ALERT_CHANNEL extends CommandRef {
                public static final ADDBALANCE_ALERT_CHANNEL cmd = new ADDBALANCE_ALERT_CHANNEL();
                public ADDBALANCE_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BANK_ALERT_CHANNEL", field="BANK_ALERT_CHANNEL")
            public static class BANK_ALERT_CHANNEL extends CommandRef {
                public static final BANK_ALERT_CHANNEL cmd = new BANK_ALERT_CHANNEL();
                public BANK_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DEPOSIT_ALERT_CHANNEL", field="DEPOSIT_ALERT_CHANNEL")
            public static class DEPOSIT_ALERT_CHANNEL extends CommandRef {
                public static final DEPOSIT_ALERT_CHANNEL cmd = new DEPOSIT_ALERT_CHANNEL();
                public DEPOSIT_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DISPLAY_CONDENSED_DEPOSITS", field="DISPLAY_CONDENSED_DEPOSITS")
            public static class DISPLAY_CONDENSED_DEPOSITS extends CommandRef {
                public static final DISPLAY_CONDENSED_DEPOSITS cmd = new DISPLAY_CONDENSED_DEPOSITS();
                public DISPLAY_CONDENSED_DEPOSITS create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DISPLAY_ITEMIZED_DEPOSITS", field="DISPLAY_ITEMIZED_DEPOSITS")
            public static class DISPLAY_ITEMIZED_DEPOSITS extends CommandRef {
                public static final DISPLAY_ITEMIZED_DEPOSITS cmd = new DISPLAY_ITEMIZED_DEPOSITS();
                public DISPLAY_ITEMIZED_DEPOSITS create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="GRANT_REQUEST_CHANNEL", field="GRANT_REQUEST_CHANNEL")
            public static class GRANT_REQUEST_CHANNEL extends CommandRef {
                public static final GRANT_REQUEST_CHANNEL cmd = new GRANT_REQUEST_CHANNEL();
                public GRANT_REQUEST_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="LARGE_TRANSFERS_CHANNEL", field="LARGE_TRANSFERS_CHANNEL")
            public static class LARGE_TRANSFERS_CHANNEL extends CommandRef {
                public static final LARGE_TRANSFERS_CHANNEL cmd = new LARGE_TRANSFERS_CHANNEL();
                public LARGE_TRANSFERS_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WITHDRAW_ALERT_CHANNEL", field="WITHDRAW_ALERT_CHANNEL")
            public static class WITHDRAW_ALERT_CHANNEL extends CommandRef {
                public static final WITHDRAW_ALERT_CHANNEL cmd = new WITHDRAW_ALERT_CHANNEL();
                public WITHDRAW_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
        }
        public static class settings_beige_alerts{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BEIGE_ALERT_CHANNEL", field="BEIGE_ALERT_CHANNEL")
            public static class BEIGE_ALERT_CHANNEL extends CommandRef {
                public static final BEIGE_ALERT_CHANNEL cmd = new BEIGE_ALERT_CHANNEL();
                public BEIGE_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BEIGE_VIOLATION_MAIL", field="BEIGE_VIOLATION_MAIL")
            public static class BEIGE_VIOLATION_MAIL extends CommandRef {
                public static final BEIGE_VIOLATION_MAIL cmd = new BEIGE_VIOLATION_MAIL();
                public BEIGE_VIOLATION_MAIL create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_ALERT_CHANNEL", field="ENEMY_ALERT_CHANNEL")
            public static class ENEMY_ALERT_CHANNEL extends CommandRef {
                public static final ENEMY_ALERT_CHANNEL cmd = new ENEMY_ALERT_CHANNEL();
                public ENEMY_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_ALERT_CHANNEL_MODE", field="ENEMY_ALERT_CHANNEL_MODE")
            public static class ENEMY_ALERT_CHANNEL_MODE extends CommandRef {
                public static final ENEMY_ALERT_CHANNEL_MODE cmd = new ENEMY_ALERT_CHANNEL_MODE();
                public ENEMY_ALERT_CHANNEL_MODE create(String mode) {
                    return createArgs("mode", mode);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_ALERT_FILTER", field="ENEMY_ALERT_FILTER")
            public static class ENEMY_ALERT_FILTER extends CommandRef {
                public static final ENEMY_ALERT_FILTER cmd = new ENEMY_ALERT_FILTER();
                public ENEMY_ALERT_FILTER create(String filter) {
                    return createArgs("filter", filter);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_BEIGED_ALERT", field="ENEMY_BEIGED_ALERT")
            public static class ENEMY_BEIGED_ALERT extends CommandRef {
                public static final ENEMY_BEIGED_ALERT cmd = new ENEMY_BEIGED_ALERT();
                public ENEMY_BEIGED_ALERT create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_BEIGED_ALERT_VIOLATIONS", field="ENEMY_BEIGED_ALERT_VIOLATIONS")
            public static class ENEMY_BEIGED_ALERT_VIOLATIONS extends CommandRef {
                public static final ENEMY_BEIGED_ALERT_VIOLATIONS cmd = new ENEMY_BEIGED_ALERT_VIOLATIONS();
                public ENEMY_BEIGED_ALERT_VIOLATIONS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addBeigeReasons", field="ALLOWED_BEIGE_REASONS")
            public static class addBeigeReasons extends CommandRef {
                public static final addBeigeReasons cmd = new addBeigeReasons();
                public addBeigeReasons create(String range, String reasons) {
                    return createArgs("range", range, "reasons", reasons);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="removeBeigeReasons", field="ALLOWED_BEIGE_REASONS")
            public static class removeBeigeReasons extends CommandRef {
                public static final removeBeigeReasons cmd = new removeBeigeReasons();
                public removeBeigeReasons create(String range) {
                    return createArgs("range", range);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="setBeigeReasons", field="ALLOWED_BEIGE_REASONS")
            public static class setBeigeReasons extends CommandRef {
                public static final setBeigeReasons cmd = new setBeigeReasons();
                public setBeigeReasons create(String reasons) {
                    return createArgs("reasons", reasons);
                }
            }
        }
        public static class settings_bounty{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BOUNTY_ALERT_CHANNEL", field="BOUNTY_ALERT_CHANNEL")
            public static class BOUNTY_ALERT_CHANNEL extends CommandRef {
                public static final BOUNTY_ALERT_CHANNEL cmd = new BOUNTY_ALERT_CHANNEL();
                public BOUNTY_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="TREASURE_ALERT_CHANNEL", field="TREASURE_ALERT_CHANNEL")
            public static class TREASURE_ALERT_CHANNEL extends CommandRef {
                public static final TREASURE_ALERT_CHANNEL cmd = new TREASURE_ALERT_CHANNEL();
                public TREASURE_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
        }
        public static class settings_default{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DELEGATE_SERVER", field="DELEGATE_SERVER")
            public static class DELEGATE_SERVER extends CommandRef {
                public static final DELEGATE_SERVER cmd = new DELEGATE_SERVER();
                public DELEGATE_SERVER create(String guild) {
                    return createArgs("guild", guild);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="registerAlliance", field="ALLIANCE_ID")
            public static class registerAlliance extends CommandRef {
                public static final registerAlliance cmd = new registerAlliance();
                public registerAlliance create(String alliances) {
                    return createArgs("alliances", alliances);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="registerApiKey", field="API_KEY")
            public static class registerApiKey extends CommandRef {
                public static final registerApiKey cmd = new registerApiKey();
                public registerApiKey create(String apiKeys) {
                    return createArgs("apiKeys", apiKeys);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="unregisterAlliance", field="ALLIANCE_ID")
            public static class unregisterAlliance extends CommandRef {
                public static final unregisterAlliance cmd = new unregisterAlliance();
                public unregisterAlliance create(String alliances) {
                    return createArgs("alliances", alliances);
                }
            }
        }
        public static class settings_foreign_affairs{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ALLIANCE_CREATE_ALERTS", field="ALLIANCE_CREATE_ALERTS")
            public static class ALLIANCE_CREATE_ALERTS extends CommandRef {
                public static final ALLIANCE_CREATE_ALERTS cmd = new ALLIANCE_CREATE_ALERTS();
                public ALLIANCE_CREATE_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DO_NOT_RAID_TOP_X", field="DO_NOT_RAID_TOP_X")
            public static class DO_NOT_RAID_TOP_X extends CommandRef {
                public static final DO_NOT_RAID_TOP_X cmd = new DO_NOT_RAID_TOP_X();
                public DO_NOT_RAID_TOP_X create(String topAllianceScore) {
                    return createArgs("topAllianceScore", topAllianceScore);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="EMBASSY_CATEGORY", field="EMBASSY_CATEGORY")
            public static class EMBASSY_CATEGORY extends CommandRef {
                public static final EMBASSY_CATEGORY cmd = new EMBASSY_CATEGORY();
                public EMBASSY_CATEGORY create(String category) {
                    return createArgs("category", category);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="FA_SERVER", field="FA_SERVER")
            public static class FA_SERVER extends CommandRef {
                public static final FA_SERVER cmd = new FA_SERVER();
                public FA_SERVER create(String guild) {
                    return createArgs("guild", guild);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="TREATY_ALERTS", field="TREATY_ALERTS")
            public static class TREATY_ALERTS extends CommandRef {
                public static final TREATY_ALERTS cmd = new TREATY_ALERTS();
                public TREATY_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
        }
        public static class settings_interview{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ARCHIVE_CATEGORY", field="ARCHIVE_CATEGORY")
            public static class ARCHIVE_CATEGORY extends CommandRef {
                public static final ARCHIVE_CATEGORY cmd = new ARCHIVE_CATEGORY();
                public ARCHIVE_CATEGORY create(String category) {
                    return createArgs("category", category);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="INTERVIEW_INFO_SPAM", field="INTERVIEW_INFO_SPAM")
            public static class INTERVIEW_INFO_SPAM extends CommandRef {
                public static final INTERVIEW_INFO_SPAM cmd = new INTERVIEW_INFO_SPAM();
                public INTERVIEW_INFO_SPAM create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="INTERVIEW_PENDING_ALERTS", field="INTERVIEW_PENDING_ALERTS")
            public static class INTERVIEW_PENDING_ALERTS extends CommandRef {
                public static final INTERVIEW_PENDING_ALERTS cmd = new INTERVIEW_PENDING_ALERTS();
                public INTERVIEW_PENDING_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
        }
        public static class settings_orbis_alerts{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AA_GROUND_TOP_X", field="AA_GROUND_TOP_X")
            public static class AA_GROUND_TOP_X extends CommandRef {
                public static final AA_GROUND_TOP_X cmd = new AA_GROUND_TOP_X();
                public AA_GROUND_TOP_X create(String topX) {
                    return createArgs("topX", topX);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AA_GROUND_UNIT_ALERTS", field="AA_GROUND_UNIT_ALERTS")
            public static class AA_GROUND_UNIT_ALERTS extends CommandRef {
                public static final AA_GROUND_UNIT_ALERTS cmd = new AA_GROUND_UNIT_ALERTS();
                public AA_GROUND_UNIT_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ACTIVITY_ALERTS", field="ACTIVITY_ALERTS")
            public static class ACTIVITY_ALERTS extends CommandRef {
                public static final ACTIVITY_ALERTS cmd = new ACTIVITY_ALERTS();
                public ACTIVITY_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ALLIANCE_EXODUS_TOP_X", field="ALLIANCE_EXODUS_TOP_X")
            public static class ALLIANCE_EXODUS_TOP_X extends CommandRef {
                public static final ALLIANCE_EXODUS_TOP_X cmd = new ALLIANCE_EXODUS_TOP_X();
                public ALLIANCE_EXODUS_TOP_X create(String rank) {
                    return createArgs("rank", rank);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BAN_ALERT_CHANNEL", field="BAN_ALERT_CHANNEL")
            public static class BAN_ALERT_CHANNEL extends CommandRef {
                public static final BAN_ALERT_CHANNEL cmd = new BAN_ALERT_CHANNEL();
                public BAN_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DELETION_ALERT_CHANNEL", field="DELETION_ALERT_CHANNEL")
            public static class DELETION_ALERT_CHANNEL extends CommandRef {
                public static final DELETION_ALERT_CHANNEL cmd = new DELETION_ALERT_CHANNEL();
                public DELETION_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ESCALATION_ALERTS", field="ESCALATION_ALERTS")
            public static class ESCALATION_ALERTS extends CommandRef {
                public static final ESCALATION_ALERTS cmd = new ESCALATION_ALERTS();
                public ESCALATION_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ORBIS_ALLIANCE_EXODUS_ALERTS", field="ORBIS_ALLIANCE_EXODUS_ALERTS")
            public static class ORBIS_ALLIANCE_EXODUS_ALERTS extends CommandRef {
                public static final ORBIS_ALLIANCE_EXODUS_ALERTS cmd = new ORBIS_ALLIANCE_EXODUS_ALERTS();
                public ORBIS_ALLIANCE_EXODUS_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ORBIS_LEADER_CHANGE_ALERT", field="ORBIS_LEADER_CHANGE_ALERT")
            public static class ORBIS_LEADER_CHANGE_ALERT extends CommandRef {
                public static final ORBIS_LEADER_CHANGE_ALERT cmd = new ORBIS_LEADER_CHANGE_ALERT();
                public ORBIS_LEADER_CHANGE_ALERT create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ORBIS_OFFICER_LEAVE_ALERTS", field="ORBIS_OFFICER_LEAVE_ALERTS")
            public static class ORBIS_OFFICER_LEAVE_ALERTS extends CommandRef {
                public static final ORBIS_OFFICER_LEAVE_ALERTS cmd = new ORBIS_OFFICER_LEAVE_ALERTS();
                public ORBIS_OFFICER_LEAVE_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ORBIS_OFFICER_MMR_CHANGE_ALERTS", field="ORBIS_OFFICER_MMR_CHANGE_ALERTS")
            public static class ORBIS_OFFICER_MMR_CHANGE_ALERTS extends CommandRef {
                public static final ORBIS_OFFICER_MMR_CHANGE_ALERTS cmd = new ORBIS_OFFICER_MMR_CHANGE_ALERTS();
                public ORBIS_OFFICER_MMR_CHANGE_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REPORT_ALERT_CHANNEL", field="REPORT_ALERT_CHANNEL")
            public static class REPORT_ALERT_CHANNEL extends CommandRef {
                public static final REPORT_ALERT_CHANNEL cmd = new REPORT_ALERT_CHANNEL();
                public REPORT_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REROLL_ALERT_CHANNEL", field="REROLL_ALERT_CHANNEL")
            public static class REROLL_ALERT_CHANNEL extends CommandRef {
                public static final REROLL_ALERT_CHANNEL cmd = new REROLL_ALERT_CHANNEL();
                public REROLL_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
        }
        public static class settings_recruit{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RECRUIT_MESSAGE_CONTENT", field="RECRUIT_MESSAGE_CONTENT")
            public static class RECRUIT_MESSAGE_CONTENT extends CommandRef {
                public static final RECRUIT_MESSAGE_CONTENT cmd = new RECRUIT_MESSAGE_CONTENT();
                public RECRUIT_MESSAGE_CONTENT create(String message) {
                    return createArgs("message", message);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RECRUIT_MESSAGE_DELAY", field="RECRUIT_MESSAGE_DELAY")
            public static class RECRUIT_MESSAGE_DELAY extends CommandRef {
                public static final RECRUIT_MESSAGE_DELAY cmd = new RECRUIT_MESSAGE_DELAY();
                public RECRUIT_MESSAGE_DELAY create(String timediff) {
                    return createArgs("timediff", timediff);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RECRUIT_MESSAGE_OUTPUT", field="RECRUIT_MESSAGE_OUTPUT")
            public static class RECRUIT_MESSAGE_OUTPUT extends CommandRef {
                public static final RECRUIT_MESSAGE_OUTPUT cmd = new RECRUIT_MESSAGE_OUTPUT();
                public RECRUIT_MESSAGE_OUTPUT create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RECRUIT_MESSAGE_SUBJECT", field="RECRUIT_MESSAGE_SUBJECT")
            public static class RECRUIT_MESSAGE_SUBJECT extends CommandRef {
                public static final RECRUIT_MESSAGE_SUBJECT cmd = new RECRUIT_MESSAGE_SUBJECT();
                public RECRUIT_MESSAGE_SUBJECT create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="add_timed_message", field="TIMED_MESSAGES")
            public static class add_timed_message extends CommandRef {
                public static final add_timed_message cmd = new add_timed_message();
                public add_timed_message create(String timeDelay, String subject, String message, String trigger) {
                    return createArgs("timeDelay", timeDelay, "subject", subject, "message", message, "trigger", trigger);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="remove_timed_message", field="TIMED_MESSAGES")
            public static class remove_timed_message extends CommandRef {
                public static final remove_timed_message cmd = new remove_timed_message();
                public remove_timed_message create(String trigger, String timeDelay) {
                    return createArgs("trigger", trigger, "timeDelay", timeDelay);
                }
            }
        }
        public static class settings_reward{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REWARD_MENTOR", field="REWARD_MENTOR")
            public static class REWARD_MENTOR extends CommandRef {
                public static final REWARD_MENTOR cmd = new REWARD_MENTOR();
                public REWARD_MENTOR create(String amount) {
                    return createArgs("amount", amount);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REWARD_REFERRAL", field="REWARD_REFERRAL")
            public static class REWARD_REFERRAL extends CommandRef {
                public static final REWARD_REFERRAL cmd = new REWARD_REFERRAL();
                public REWARD_REFERRAL create(String amount) {
                    return createArgs("amount", amount);
                }
            }
        }
        public static class settings_role{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ASSIGNABLE_ROLES", field="ASSIGNABLE_ROLES")
            public static class ASSIGNABLE_ROLES extends CommandRef {
                public static final ASSIGNABLE_ROLES cmd = new ASSIGNABLE_ROLES();
                public ASSIGNABLE_ROLES create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTONICK", field="AUTONICK")
            public static class AUTONICK extends CommandRef {
                public static final AUTONICK cmd = new AUTONICK();
                public AUTONICK create(String mode) {
                    return createArgs("mode", mode);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_ALLIANCES", field="AUTOROLE_ALLIANCES")
            public static class AUTOROLE_ALLIANCES extends CommandRef {
                public static final AUTOROLE_ALLIANCES cmd = new AUTOROLE_ALLIANCES();
                public AUTOROLE_ALLIANCES create(String mode) {
                    return createArgs("mode", mode);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_ALLIANCE_RANK", field="AUTOROLE_ALLIANCE_RANK")
            public static class AUTOROLE_ALLIANCE_RANK extends CommandRef {
                public static final AUTOROLE_ALLIANCE_RANK cmd = new AUTOROLE_ALLIANCE_RANK();
                public AUTOROLE_ALLIANCE_RANK create(String allianceRank) {
                    return createArgs("allianceRank", allianceRank);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_ALLY_GOV", field="AUTOROLE_ALLY_GOV")
            public static class AUTOROLE_ALLY_GOV extends CommandRef {
                public static final AUTOROLE_ALLY_GOV cmd = new AUTOROLE_ALLY_GOV();
                public AUTOROLE_ALLY_GOV create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_ALLY_ROLES", field="AUTOROLE_ALLY_ROLES")
            public static class AUTOROLE_ALLY_ROLES extends CommandRef {
                public static final AUTOROLE_ALLY_ROLES cmd = new AUTOROLE_ALLY_ROLES();
                public AUTOROLE_ALLY_ROLES create(String roles) {
                    return createArgs("roles", roles);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_MEMBER_APPS", field="AUTOROLE_MEMBER_APPS")
            public static class AUTOROLE_MEMBER_APPS extends CommandRef {
                public static final AUTOROLE_MEMBER_APPS cmd = new AUTOROLE_MEMBER_APPS();
                public AUTOROLE_MEMBER_APPS create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_TOP_X", field="AUTOROLE_TOP_X")
            public static class AUTOROLE_TOP_X extends CommandRef {
                public static final AUTOROLE_TOP_X cmd = new AUTOROLE_TOP_X();
                public AUTOROLE_TOP_X create(String topScoreRank) {
                    return createArgs("topScoreRank", topScoreRank);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="CONDITIONAL_ROLES", field="CONDITIONAL_ROLES")
            public static class CONDITIONAL_ROLES extends CommandRef {
                public static final CONDITIONAL_ROLES cmd = new CONDITIONAL_ROLES();
                public CONDITIONAL_ROLES create(String roleMap) {
                    return createArgs("roleMap", roleMap);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addAssignableRole", field="ASSIGNABLE_ROLES")
            public static class addAssignableRole extends CommandRef {
                public static final addAssignableRole cmd = new addAssignableRole();
                public addAssignableRole create(String role, String roles) {
                    return createArgs("role", role, "roles", roles);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addConditionalRole", field="CONDITIONAL_ROLES")
            public static class addConditionalRole extends CommandRef {
                public static final addConditionalRole cmd = new addConditionalRole();
                public addConditionalRole create(String filter, String role) {
                    return createArgs("filter", filter, "role", role);
                }
            }
        }
        public static class settings_sheet{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="listSheetKeys")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="setSheetKey")
            public static class set extends CommandRef {
                public static final set cmd = new set();
                public set create(String key, String sheetId, String tab) {
                    return createArgs("key", key, "sheetId", sheetId, "tab", tab);
                }
            }
        }
        public static class settings_tax{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_SET_BRACKET", field="MEMBER_CAN_SET_BRACKET")
            public static class MEMBER_CAN_SET_BRACKET extends CommandRef {
                public static final MEMBER_CAN_SET_BRACKET cmd = new MEMBER_CAN_SET_BRACKET();
                public MEMBER_CAN_SET_BRACKET create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REQUIRED_INTERNAL_TAXRATE", field="REQUIRED_INTERNAL_TAXRATE")
            public static class REQUIRED_INTERNAL_TAXRATE extends CommandRef {
                public static final REQUIRED_INTERNAL_TAXRATE cmd = new REQUIRED_INTERNAL_TAXRATE();
                public REQUIRED_INTERNAL_TAXRATE create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REQUIRED_TAX_BRACKET", field="REQUIRED_TAX_BRACKET")
            public static class REQUIRED_TAX_BRACKET extends CommandRef {
                public static final REQUIRED_TAX_BRACKET cmd = new REQUIRED_TAX_BRACKET();
                public REQUIRED_TAX_BRACKET create(String brackets) {
                    return createArgs("brackets", brackets);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="TAX_BASE", field="TAX_BASE")
            public static class TAX_BASE extends CommandRef {
                public static final TAX_BASE cmd = new TAX_BASE();
                public TAX_BASE create(String taxRate) {
                    return createArgs("taxRate", taxRate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addRequiredBracket", field="REQUIRED_TAX_BRACKET")
            public static class addRequiredBracket extends CommandRef {
                public static final addRequiredBracket cmd = new addRequiredBracket();
                public addRequiredBracket create(String filter, String bracket) {
                    return createArgs("filter", filter, "bracket", bracket);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addRequiredInternalTaxrate", field="REQUIRED_INTERNAL_TAXRATE")
            public static class addRequiredInternalTaxrate extends CommandRef {
                public static final addRequiredInternalTaxrate cmd = new addRequiredInternalTaxrate();
                public addRequiredInternalTaxrate create(String filter, String bracket) {
                    return createArgs("filter", filter, "bracket", bracket);
                }
            }
        }
        public static class settings_trade{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="TRADE_ALERT_CHANNEL", field="TRADE_ALERT_CHANNEL")
            public static class TRADE_ALERT_CHANNEL extends CommandRef {
                public static final TRADE_ALERT_CHANNEL cmd = new TRADE_ALERT_CHANNEL();
                public TRADE_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
        }
        public static class settings_war_alerts{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BLOCKADED_ALERTS", field="BLOCKADED_ALERTS")
            public static class BLOCKADED_ALERTS extends CommandRef {
                public static final BLOCKADED_ALERTS cmd = new BLOCKADED_ALERTS();
                public BLOCKADED_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DEFENSE_WAR_CHANNEL", field="DEFENSE_WAR_CHANNEL")
            public static class DEFENSE_WAR_CHANNEL extends CommandRef {
                public static final DEFENSE_WAR_CHANNEL cmd = new DEFENSE_WAR_CHANNEL();
                public DEFENSE_WAR_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_MMR_CHANGE_ALERTS", field="ENEMY_MMR_CHANGE_ALERTS")
            public static class ENEMY_MMR_CHANGE_ALERTS extends CommandRef {
                public static final ENEMY_MMR_CHANGE_ALERTS cmd = new ENEMY_MMR_CHANGE_ALERTS();
                public ENEMY_MMR_CHANGE_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ESPIONAGE_ALERT_CHANNEL", field="ESPIONAGE_ALERT_CHANNEL")
            public static class ESPIONAGE_ALERT_CHANNEL extends CommandRef {
                public static final ESPIONAGE_ALERT_CHANNEL cmd = new ESPIONAGE_ALERT_CHANNEL();
                public ESPIONAGE_ALERT_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="HIDE_APPLICANT_WARS", field="HIDE_APPLICANT_WARS")
            public static class HIDE_APPLICANT_WARS extends CommandRef {
                public static final HIDE_APPLICANT_WARS cmd = new HIDE_APPLICANT_WARS();
                public HIDE_APPLICANT_WARS create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="LOST_WAR_CHANNEL", field="LOST_WAR_CHANNEL")
            public static class LOST_WAR_CHANNEL extends CommandRef {
                public static final LOST_WAR_CHANNEL cmd = new LOST_WAR_CHANNEL();
                public LOST_WAR_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MENTION_MILCOM_FILTER", field="MENTION_MILCOM_FILTER")
            public static class MENTION_MILCOM_FILTER extends CommandRef {
                public static final MENTION_MILCOM_FILTER cmd = new MENTION_MILCOM_FILTER();
                public MENTION_MILCOM_FILTER create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="OFFENSIVE_WAR_CHANNEL", field="OFFENSIVE_WAR_CHANNEL")
            public static class OFFENSIVE_WAR_CHANNEL extends CommandRef {
                public static final OFFENSIVE_WAR_CHANNEL cmd = new OFFENSIVE_WAR_CHANNEL();
                public OFFENSIVE_WAR_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="SHOW_ALLY_DEFENSIVE_WARS", field="SHOW_ALLY_DEFENSIVE_WARS")
            public static class SHOW_ALLY_DEFENSIVE_WARS extends CommandRef {
                public static final SHOW_ALLY_DEFENSIVE_WARS cmd = new SHOW_ALLY_DEFENSIVE_WARS();
                public SHOW_ALLY_DEFENSIVE_WARS create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="SHOW_ALLY_OFFENSIVE_WARS", field="SHOW_ALLY_OFFENSIVE_WARS")
            public static class SHOW_ALLY_OFFENSIVE_WARS extends CommandRef {
                public static final SHOW_ALLY_OFFENSIVE_WARS cmd = new SHOW_ALLY_OFFENSIVE_WARS();
                public SHOW_ALLY_OFFENSIVE_WARS create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="UNBLOCKADED_ALERTS", field="UNBLOCKADED_ALERTS")
            public static class UNBLOCKADED_ALERTS extends CommandRef {
                public static final UNBLOCKADED_ALERTS cmd = new UNBLOCKADED_ALERTS();
                public UNBLOCKADED_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="UNBLOCKADE_REQUESTS", field="UNBLOCKADE_REQUESTS")
            public static class UNBLOCKADE_REQUESTS extends CommandRef {
                public static final UNBLOCKADE_REQUESTS cmd = new UNBLOCKADE_REQUESTS();
                public UNBLOCKADE_REQUESTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_ALERT_FOR_OFFSHORES", field="WAR_ALERT_FOR_OFFSHORES")
            public static class WAR_ALERT_FOR_OFFSHORES extends CommandRef {
                public static final WAR_ALERT_FOR_OFFSHORES cmd = new WAR_ALERT_FOR_OFFSHORES();
                public WAR_ALERT_FOR_OFFSHORES create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_PEACE_ALERTS", field="WAR_PEACE_ALERTS")
            public static class WAR_PEACE_ALERTS extends CommandRef {
                public static final WAR_PEACE_ALERTS cmd = new WAR_PEACE_ALERTS();
                public WAR_PEACE_ALERTS create(String channel) {
                    return createArgs("channel", channel);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_ROOM_FILTER", field="WAR_ROOM_FILTER")
            public static class WAR_ROOM_FILTER extends CommandRef {
                public static final WAR_ROOM_FILTER cmd = new WAR_ROOM_FILTER();
                public WAR_ROOM_FILTER create(String value) {
                    return createArgs("value", value);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WON_WAR_CHANNEL", field="WON_WAR_CHANNEL")
            public static class WON_WAR_CHANNEL extends CommandRef {
                public static final WON_WAR_CHANNEL cmd = new WON_WAR_CHANNEL();
                public WON_WAR_CHANNEL create(String channel) {
                    return createArgs("channel", channel);
                }
            }
        }
        public static class settings_war_room{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENABLE_WAR_ROOMS", field="ENABLE_WAR_ROOMS")
            public static class ENABLE_WAR_ROOMS extends CommandRef {
                public static final ENABLE_WAR_ROOMS cmd = new ENABLE_WAR_ROOMS();
                public ENABLE_WAR_ROOMS create(String enabled) {
                    return createArgs("enabled", enabled);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_SERVER", field="WAR_SERVER")
            public static class WAR_SERVER extends CommandRef {
                public static final WAR_SERVER cmd = new WAR_SERVER();
                public WAR_SERVER create(String guild) {
                    return createArgs("guild", guild);
                }
            }
        }
        public static class sheet_custom{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="addTab")
            public static class add_tab extends CommandRef {
                public static final add_tab cmd = new add_tab();
                public add_tab create(String sheet, String tabName, String select, String columns, String force) {
                    return createArgs("sheet", sheet, "tabName", tabName, "select", select, "columns", columns, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="auto")
            public static class auto extends CommandRef {
                public static final auto cmd = new auto();
                public auto create(String sheet, String saveSheet) {
                    return createArgs("sheet", sheet, "saveSheet", saveSheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="listCustomSheets")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="deleteTab")
            public static class remove_tab extends CommandRef {
                public static final remove_tab cmd = new remove_tab();
                public remove_tab create(String sheet, String tabName) {
                    return createArgs("sheet", sheet, "tabName", tabName);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="updateSheet")
            public static class update extends CommandRef {
                public static final update cmd = new update();
                public update create(String sheet) {
                    return createArgs("sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="info")
            public static class view extends CommandRef {
                public static final view cmd = new view();
                public view create(String sheet) {
                    return createArgs("sheet", sheet);
                }
            }
        }
        public static class sheet_template{
            public static class add{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="ALLIANCES")
                public static class alliance extends CommandRef {
                    public static final alliance cmd = new alliance();
                    public alliance create(String sheet, String column1, String column2, String column3, String column4, String column5, String column6, String column7, String column8, String column9, String column10, String column11, String column12, String column13, String column14, String column15, String column16, String column17, String column18, String column19, String column20, String column21, String column22, String column23, String column24) {
                        return createArgs("sheet", sheet, "column1", column1, "column2", column2, "column3", column3, "column4", column4, "column5", column5, "column6", column6, "column7", column7, "column8", column8, "column9", column9, "column10", column10, "column11", column11, "column12", column12, "column13", column13, "column14", column14, "column15", column15, "column16", column16, "column17", column17, "column18", column18, "column19", column19, "column20", column20, "column21", column21, "column22", column22, "column23", column23, "column24", column24);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="ATTACK_TYPES")
                public static class attacktype extends CommandRef {
                    public static final attacktype cmd = new attacktype();
                    public attacktype create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="AUDIT_TYPES")
                public static class audittype extends CommandRef {
                    public static final audittype cmd = new audittype();
                    public audittype create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="BANS")
                public static class ban extends CommandRef {
                    public static final ban cmd = new ban();
                    public ban create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="BOUNTIES")
                public static class bounty extends CommandRef {
                    public static final bounty cmd = new bounty();
                    public bounty create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="BUILDINGS")
                public static class building extends CommandRef {
                    public static final building cmd = new building();
                    public building create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="CITIES")
                public static class city extends CommandRef {
                    public static final city cmd = new city();
                    public city create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="CONTINENTS")
                public static class continent extends CommandRef {
                    public static final continent cmd = new continent();
                    public continent create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="GUILDS")
                public static class guild extends CommandRef {
                    public static final guild cmd = new guild();
                    public guild create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="SETTINGS")
                public static class guildsetting extends CommandRef {
                    public static final guildsetting cmd = new guildsetting();
                    public guildsetting create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="ATTACKS")
                public static class iattack extends CommandRef {
                    public static final iattack cmd = new iattack();
                    public iattack create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="MILITARY_UNITS")
                public static class militaryunit extends CommandRef {
                    public static final militaryunit cmd = new militaryunit();
                    public militaryunit create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="NATIONS")
                public static class nation extends CommandRef {
                    public static final nation cmd = new nation();
                    public nation create(String sheet, String column1, String column2, String column3, String column4, String column5, String column6, String column7, String column8, String column9, String column10, String column11, String column12, String column13, String column14, String column15, String column16, String column17, String column18, String column19, String column20, String column21, String column22, String column23, String column24) {
                        return createArgs("sheet", sheet, "column1", column1, "column2", column2, "column3", column3, "column4", column4, "column5", column5, "column6", column6, "column7", column7, "column8", column8, "column9", column9, "column10", column10, "column11", column11, "column12", column12, "column13", column13, "column14", column14, "column15", column15, "column16", column16, "column17", column17, "column18", column18, "column19", column19, "column20", column20, "column21", column21, "column22", column22, "column23", column23, "column24", column24);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="NATION_COLORS")
                public static class nationcolor extends CommandRef {
                    public static final nationcolor cmd = new nationcolor();
                    public nationcolor create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="NATION_LIST")
                public static class nationlist extends CommandRef {
                    public static final nationlist cmd = new nationlist();
                    public nationlist create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="NATION_OR_ALLIANCE")
                public static class nationoralliance extends CommandRef {
                    public static final nationoralliance cmd = new nationoralliance();
                    public nationoralliance create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="PROJECTS")
                public static class project extends CommandRef {
                    public static final project cmd = new project();
                    public project create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="RESOURCE_TYPES")
                public static class resourcetype extends CommandRef {
                    public static final resourcetype cmd = new resourcetype();
                    public resourcetype create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TAX_BRACKETS")
                public static class taxbracket extends CommandRef {
                    public static final taxbracket cmd = new taxbracket();
                    public taxbracket create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TAX_DEPOSITS")
                public static class taxdeposit extends CommandRef {
                    public static final taxdeposit cmd = new taxdeposit();
                    public taxdeposit create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TREASURES")
                public static class treasure extends CommandRef {
                    public static final treasure cmd = new treasure();
                    public treasure create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TREATIES")
                public static class treaty extends CommandRef {
                    public static final treaty cmd = new treaty();
                    public treaty create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TREATY_TYPES")
                public static class treatytype extends CommandRef {
                    public static final treatytype cmd = new treatytype();
                    public treatytype create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="USERS")
                public static class user extends CommandRef {
                    public static final user cmd = new user();
                    public user create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="WARS")
                public static class war extends CommandRef {
                    public static final war cmd = new war();
                    public war create(String sheet, String a, String b, String c, String d, String e, String f, String g, String h, String i, String j, String k, String l, String m, String n, String o, String p, String q, String r, String s, String t, String u, String v, String w, String x) {
                        return createArgs("sheet", sheet, "a", a, "b", b, "c", c, "d", d, "e", e, "f", f, "g", g, "h", h, "i", i, "j", j, "k", k, "l", l, "m", m, "n", n, "o", o, "p", p, "q", q, "r", r, "s", s, "t", t, "u", u, "v", v, "w", w, "x", x);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="listSheetTemplates")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String type) {
                    return createArgs("type", type);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="deleteTemplate")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
                public remove create(String sheet) {
                    return createArgs("sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="deleteColumns")
            public static class remove_column extends CommandRef {
                public static final remove_column cmd = new remove_column();
                public remove_column create(String sheet, String columns) {
                    return createArgs("sheet", sheet, "columns", columns);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="renameTemplate")
            public static class rename extends CommandRef {
                public static final rename cmd = new rename();
                public rename create(String sheet, String name) {
                    return createArgs("sheet", sheet, "name", name);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="viewTemplate")
            public static class view extends CommandRef {
                public static final view cmd = new view();
                public view create(String sheet) {
                    return createArgs("sheet", sheet);
                }
            }
        }
        public static class sheets_econ{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="IngameNationTransfersByReceiver")
            public static class IngameNationTransfersByReceiver extends CommandRef {
                public static final IngameNationTransfersByReceiver cmd = new IngameNationTransfersByReceiver();
                public IngameNationTransfersByReceiver create(String receivers, String startTime, String endTime, String sheet) {
                    return createArgs("receivers", receivers, "startTime", startTime, "endTime", endTime, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="IngameNationTransfersBySender")
            public static class IngameNationTransfersBySender extends CommandRef {
                public static final IngameNationTransfersBySender cmd = new IngameNationTransfersBySender();
                public IngameNationTransfersBySender create(String senders, String timeframe, String sheet) {
                    return createArgs("senders", senders, "timeframe", timeframe, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="ProjectSheet")
            public static class ProjectSheet extends CommandRef {
                public static final ProjectSheet cmd = new ProjectSheet();
                public ProjectSheet create(String nations, String sheet, String snapshotTime) {
                    return createArgs("nations", nations, "sheet", sheet, "snapshotTime", snapshotTime);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getIngameNationTransfers")
            public static class getIngameNationTransfers extends CommandRef {
                public static final getIngameNationTransfers cmd = new getIngameNationTransfers();
                public getIngameNationTransfers create(String senders, String receivers, String timeframe, String sheet) {
                    return createArgs("senders", senders, "receivers", receivers, "timeframe", timeframe, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getIngameTransactions")
            public static class getIngameTransactions extends CommandRef {
                public static final getIngameTransactions cmd = new getIngameTransactions();
                public getIngameTransactions create(String sender, String receiver, String banker, String timeframe, String sheet) {
                    return createArgs("sender", sender, "receiver", receiver, "banker", banker, "timeframe", timeframe, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getNationsInternalTransfers")
            public static class getNationsInternalTransfers extends CommandRef {
                public static final getNationsInternalTransfers cmd = new getNationsInternalTransfers();
                public getNationsInternalTransfers create(String nations, String startTime, String endTime, String sheet) {
                    return createArgs("nations", nations, "startTime", startTime, "endTime", endTime, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="projectCostCsv")
            public static class projectCostCsv extends CommandRef {
                public static final projectCostCsv cmd = new projectCostCsv();
                public projectCostCsv create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="revenueSheet")
            public static class revenueSheet extends CommandRef {
                public static final revenueSheet cmd = new revenueSheet();
                public revenueSheet create(String nations, String sheet, String snapshotTime) {
                    return createArgs("nations", nations, "sheet", sheet, "snapshotTime", snapshotTime);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="stockpileSheet")
            public static class stockpileSheet extends CommandRef {
                public static final stockpileSheet cmd = new stockpileSheet();
                public stockpileSheet create(String nationFilter, String normalize, String onlyShowExcess, String forceUpdate) {
                    return createArgs("nationFilter", nationFilter, "normalize", normalize, "onlyShowExcess", onlyShowExcess, "forceUpdate", forceUpdate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxBracketSheet")
            public static class taxBracketSheet extends CommandRef {
                public static final taxBracketSheet cmd = new taxBracketSheet();
                public taxBracketSheet create(String force, String includeApplicants) {
                    return createArgs("force", force, "includeApplicants", includeApplicants);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxRecords")
            public static class taxRecords extends CommandRef {
                public static final taxRecords cmd = new taxRecords();
                public taxRecords create(String nation, String startDate, String endDate, String sheet) {
                    return createArgs("nation", nation, "startDate", startDate, "endDate", endDate, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="taxRevenueSheet")
            public static class taxRevenue extends CommandRef {
                public static final taxRevenue cmd = new taxRevenue();
                public taxRevenue create(String nations, String sheet, String forceUpdate, String includeUntaxable) {
                    return createArgs("nations", nations, "sheet", sheet, "forceUpdate", forceUpdate, "includeUntaxable", includeUntaxable);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warReimburseByNationCsv")
            public static class warReimburseByNationCsv extends CommandRef {
                public static final warReimburseByNationCsv cmd = new warReimburseByNationCsv();
                public warReimburseByNationCsv create(String allies, String enemies, String cutoff, String removeWarsWithNoDefenderActions) {
                    return createArgs("allies", allies, "enemies", enemies, "cutoff", cutoff, "removeWarsWithNoDefenderActions", removeWarsWithNoDefenderActions);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warchestSheet")
            public static class warchestSheet extends CommandRef {
                public static final warchestSheet cmd = new warchestSheet();
                public warchestSheet create(String nations, String perCityWarchest, String includeGrants, String doNotNormalizeDeposits, String ignoreDeposits, String ignoreStockpileInExcess, String includeRevenueDays, String forceUpdate) {
                    return createArgs("nations", nations, "perCityWarchest", perCityWarchest, "includeGrants", includeGrants, "doNotNormalizeDeposits", doNotNormalizeDeposits, "ignoreDeposits", ignoreDeposits, "ignoreStockpileInExcess", ignoreStockpileInExcess, "includeRevenueDays", includeRevenueDays, "forceUpdate", forceUpdate);
                }
            }
        }
        public static class sheets_ia{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ActivitySheet")
            public static class ActivitySheet extends CommandRef {
                public static final ActivitySheet cmd = new ActivitySheet();
                public ActivitySheet create(String nations, String trackTime, String sheet) {
                    return createArgs("nations", nations, "trackTime", trackTime, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ActivitySheetFromId")
            public static class ActivitySheetFromId extends CommandRef {
                public static final ActivitySheetFromId cmd = new ActivitySheetFromId();
                public ActivitySheetFromId create(String nationId, String trackTime, String sheet) {
                    return createArgs("nationId", nationId, "trackTime", trackTime, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="AllianceSheet")
            public static class AllianceSheet extends CommandRef {
                public static final AllianceSheet cmd = new AllianceSheet();
                public AllianceSheet create(String nations, String columns, String sheet) {
                    return createArgs("nations", nations, "columns", columns, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="NationSheet")
            public static class NationSheet extends CommandRef {
                public static final NationSheet cmd = new NationSheet();
                public NationSheet create(String nations, String columns, String snapshotTime, String updateSpies, String sheet) {
                    return createArgs("nations", nations, "columns", columns, "snapshotTime", snapshotTime, "updateSpies", updateSpies, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="dayChangeSheet")
            public static class daychange extends CommandRef {
                public static final daychange cmd = new daychange();
                public daychange create(String nations, String sheet) {
                    return createArgs("nations", nations, "sheet", sheet);
                }
            }
        }
        public static class sheets_milcom{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="DeserterSheet")
            public static class DeserterSheet extends CommandRef {
                public static final DeserterSheet cmd = new DeserterSheet();
                public DeserterSheet create(String alliances, String cuttOff, String filter, String ignoreInactive, String ignoreVM, String ignoreMembers) {
                    return createArgs("alliances", alliances, "cuttOff", cuttOff, "filter", filter, "ignoreInactive", ignoreInactive, "ignoreVM", ignoreVM, "ignoreMembers", ignoreMembers);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="IntelOpSheet")
            public static class IntelOpSheet extends CommandRef {
                public static final IntelOpSheet cmd = new IntelOpSheet();
                public IntelOpSheet create(String time, String attackers, String dnrTopX, String ignoreWithLootHistory, String ignoreDNR, String sheet) {
                    return createArgs("time", time, "attackers", attackers, "dnrTopX", dnrTopX, "ignoreWithLootHistory", ignoreWithLootHistory, "ignoreDNR", ignoreDNR, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="MMRSheet")
            public static class MMRSheet extends CommandRef {
                public static final MMRSheet cmd = new MMRSheet();
                public MMRSheet create(String nations, String sheet, String forceUpdate, String showCities, String snapshotTime) {
                    return createArgs("nations", nations, "sheet", sheet, "forceUpdate", forceUpdate, "showCities", showCities, "snapshotTime", snapshotTime);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="SpySheet")
            public static class SpySheet extends CommandRef {
                public static final SpySheet cmd = new SpySheet();
                public SpySheet create(String attackers, String defenders, String allowedTypes, String forceUpdate, String checkEspionageSlots, String prioritizeKills, String sheet, String maxDef, String doubleOps, String removeSheets, String prioritizeAlliances, String attackerWeighting, String defenderWeighting) {
                    return createArgs("attackers", attackers, "defenders", defenders, "allowedTypes", allowedTypes, "forceUpdate", forceUpdate, "checkEspionageSlots", checkEspionageSlots, "prioritizeKills", prioritizeKills, "sheet", sheet, "maxDef", maxDef, "doubleOps", doubleOps, "removeSheets", removeSheets, "prioritizeAlliances", prioritizeAlliances, "attackerWeighting", attackerWeighting, "defenderWeighting", defenderWeighting);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostByAllianceSheet")
            public static class WarCostByAllianceSheet extends CommandRef {
                public static final WarCostByAllianceSheet cmd = new WarCostByAllianceSheet();
                public WarCostByAllianceSheet create(String nations, String time, String includeInactives, String includeApplicants) {
                    return createArgs("nations", nations, "time", time, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostByResourceSheet")
            public static class WarCostByResourceSheet extends CommandRef {
                public static final WarCostByResourceSheet cmd = new WarCostByResourceSheet();
                public WarCostByResourceSheet create(String attackers, String defenders, String time, String excludeConsumption, String excludeInfra, String excludeLoot, String excludeUnitCost, String includeGray, String includeDefensives, String normalizePerCity, String normalizePerWar, String sheet) {
                    return createArgs("attackers", attackers, "defenders", defenders, "time", time, "excludeConsumption", excludeConsumption, "excludeInfra", excludeInfra, "excludeLoot", excludeLoot, "excludeUnitCost", excludeUnitCost, "includeGray", includeGray, "includeDefensives", includeDefensives, "normalizePerCity", normalizePerCity, "normalizePerWar", normalizePerWar, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostSheet")
            public static class WarCostSheet extends CommandRef {
                public static final WarCostSheet cmd = new WarCostSheet();
                public WarCostSheet create(String attackers, String defenders, String time, String endTime, String excludeConsumption, String excludeInfra, String excludeLoot, String excludeUnitCost, String normalizePerCity, String useLeader, String sheet) {
                    return createArgs("attackers", attackers, "defenders", defenders, "time", time, "endTime", endTime, "excludeConsumption", excludeConsumption, "excludeInfra", excludeInfra, "excludeLoot", excludeLoot, "excludeUnitCost", excludeUnitCost, "normalizePerCity", normalizePerCity, "useLeader", useLeader, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="combatantSheet")
            public static class combatantSheet extends CommandRef {
                public static final combatantSheet cmd = new combatantSheet();
                public combatantSheet create(String nations, String includeInactive, String includeApplicants) {
                    return createArgs("nations", nations, "includeInactive", includeInactive, "includeApplicants", includeApplicants);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertHidudeSpySheet")
            public static class convertHidudeSpySheet extends CommandRef {
                public static final convertHidudeSpySheet cmd = new convertHidudeSpySheet();
                public convertHidudeSpySheet create(String input, String output, String groupByAttacker, String forceUpdate) {
                    return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "forceUpdate", forceUpdate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertTKRSpySheet")
            public static class convertTKRSpySheet extends CommandRef {
                public static final convertTKRSpySheet cmd = new convertTKRSpySheet();
                public convertTKRSpySheet create(String input, String output, String groupByAttacker, String force) {
                    return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertDtCSpySheet")
            public static class convertdtcspysheet extends CommandRef {
                public static final convertdtcspysheet cmd = new convertdtcspysheet();
                public convertdtcspysheet create(String input, String output, String groupByAttacker, String forceUpdate) {
                    return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "forceUpdate", forceUpdate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="listSpyTargets")
            public static class listSpyTargets extends CommandRef {
                public static final listSpyTargets cmd = new listSpyTargets();
                public listSpyTargets create(String spySheet, String attackers, String defenders, String headerRow, String output, String groupByAttacker) {
                    return createArgs("spySheet", spySheet, "attackers", attackers, "defenders", defenders, "headerRow", headerRow, "output", output, "groupByAttacker", groupByAttacker);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="lootValueSheet")
            public static class lootValueSheet extends CommandRef {
                public static final lootValueSheet cmd = new lootValueSheet();
                public lootValueSheet create(String attackers, String sheet) {
                    return createArgs("attackers", attackers, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="validateSpyBlitzSheet")
            public static class validateSpyBlitzSheet extends CommandRef {
                public static final validateSpyBlitzSheet cmd = new validateSpyBlitzSheet();
                public validateSpyBlitzSheet create(String sheet, String dayChange, String filter, String useLeader) {
                    return createArgs("sheet", sheet, "dayChange", dayChange, "filter", filter, "useLeader", useLeader);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warSheet")
            public static class warSheet extends CommandRef {
                public static final warSheet cmd = new warSheet();
                public warSheet create(String allies, String enemies, String cutoff, String includeConcludedWars, String sheet) {
                    return createArgs("allies", allies, "enemies", enemies, "cutoff", cutoff, "includeConcludedWars", includeConcludedWars, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warchestSheet")
            public static class warchestSheet extends CommandRef {
                public static final warchestSheet cmd = new warchestSheet();
                public warchestSheet create(String nations, String perCityWarchest, String includeGrants, String doNotNormalizeDeposits, String ignoreDeposits, String ignoreStockpileInExcess, String includeRevenueDays, String forceUpdate) {
                    return createArgs("nations", nations, "perCityWarchest", perCityWarchest, "includeGrants", includeGrants, "doNotNormalizeDeposits", doNotNormalizeDeposits, "ignoreDeposits", ignoreDeposits, "ignoreStockpileInExcess", ignoreStockpileInExcess, "includeRevenueDays", includeRevenueDays, "forceUpdate", forceUpdate);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="casualties")
            public static class casualties extends CommandRef {
                public static final casualties cmd = new casualties();
                public casualties create(String attack, String warType, String enemy, String me, String attackerMilitary, String defenderMilitary, String attackerPolicy, String defenderPolicy, String defFortified, String attAirControl, String defAirControl, String selfIsDefender, String unequipAttackerSoldiers, String unequipDefenderSoldiers, String attackerProjects, String defenderProjects) {
                    return createArgs("attack", attack, "warType", warType, "enemy", enemy, "me", me, "attackerMilitary", attackerMilitary, "defenderMilitary", defenderMilitary, "attackerPolicy", attackerPolicy, "defenderPolicy", defenderPolicy, "defFortified", defFortified, "attAirControl", attAirControl, "defAirControl", defAirControl, "selfIsDefender", selfIsDefender, "unequipAttackerSoldiers", unequipAttackerSoldiers, "unequipDefenderSoldiers", unequipDefenderSoldiers, "attackerProjects", attackerProjects, "defenderProjects", defenderProjects);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="quickestBeige")
            public static class fastBeige extends CommandRef {
                public static final fastBeige cmd = new fastBeige();
                public fastBeige create(String resistance, String noGround, String noShip, String noAir, String noMissile, String noNuke) {
                    return createArgs("resistance", resistance, "noGround", noGround, "noShip", noShip, "noAir", noAir, "noMissile", noMissile, "noNuke", noNuke);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="groundSim")
            public static class ground extends CommandRef {
                public static final ground cmd = new ground();
                public ground create(String attSoldiersUnarmed, String attSoldiers, String attTanks, String defSoldiersUnarmed, String defSoldiers, String defTanks) {
                    return createArgs("attSoldiersUnarmed", attSoldiersUnarmed, "attSoldiers", attSoldiers, "attTanks", attTanks, "defSoldiersUnarmed", defSoldiersUnarmed, "defSoldiers", defSoldiers, "defTanks", defTanks);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="navalSim")
            public static class naval extends CommandRef {
                public static final naval cmd = new naval();
                public naval create(String attShips, String defShips) {
                    return createArgs("attShips", attShips, "defShips", defShips);
                }
            }
        }
        public static class spy{
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="intel")
                public static class intel extends CommandRef {
                    public static final intel cmd = new intel();
                    public intel create(String dnrTopX, String ignoreDNR, String attacker, String score) {
                        return createArgs("dnrTopX", dnrTopX, "ignoreDNR", ignoreDNR, "attacker", attacker, "score", score);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="Spyops")
                public static class target extends CommandRef {
                    public static final target cmd = new target();
                    public target create(String targets, String operations, String requiredSuccess, String directMesssage, String prioritizeKills, String attacker) {
                        return createArgs("targets", targets, "operations", operations, "requiredSuccess", requiredSuccess, "directMesssage", directMesssage, "prioritizeKills", prioritizeKills, "attacker", attacker);
                    }
                }
            }
            public static class sheet{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertHidudeSpySheet")
                public static class convertHidude extends CommandRef {
                    public static final convertHidude cmd = new convertHidude();
                    public convertHidude create(String input, String output, String groupByAttacker, String forceUpdate) {
                        return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "forceUpdate", forceUpdate);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertTKRSpySheet")
                public static class convertTKR extends CommandRef {
                    public static final convertTKR cmd = new convertTKR();
                    public convertTKR create(String input, String output, String groupByAttacker, String force) {
                        return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertDtCSpySheet")
                public static class convertdtc extends CommandRef {
                    public static final convertdtc cmd = new convertdtc();
                    public convertdtc create(String input, String output, String groupByAttacker, String forceUpdate) {
                        return createArgs("input", input, "output", output, "groupByAttacker", groupByAttacker, "forceUpdate", forceUpdate);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="listSpyTargets")
                public static class copyForAlliance extends CommandRef {
                    public static final copyForAlliance cmd = new copyForAlliance();
                    public copyForAlliance create(String spySheet, String attackers, String defenders, String headerRow, String output, String groupByAttacker) {
                        return createArgs("spySheet", spySheet, "attackers", attackers, "defenders", defenders, "headerRow", headerRow, "output", output, "groupByAttacker", groupByAttacker);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="freeSpyOpsSheet")
                public static class free_ops extends CommandRef {
                    public static final free_ops cmd = new free_ops();
                    public free_ops create(String nations, String addColumns, String requireXFreeOps, String requireSpies, String sheet) {
                        return createArgs("nations", nations, "addColumns", addColumns, "requireXFreeOps", requireXFreeOps, "requireSpies", requireSpies, "sheet", sheet);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="SpySheet")
                public static class generate extends CommandRef {
                    public static final generate cmd = new generate();
                    public generate create(String attackers, String defenders, String allowedTypes, String forceUpdate, String checkEspionageSlots, String prioritizeKills, String sheet, String maxDef, String doubleOps, String removeSheets, String prioritizeAlliances, String attackerWeighting, String defenderWeighting) {
                        return createArgs("attackers", attackers, "defenders", defenders, "allowedTypes", allowedTypes, "forceUpdate", forceUpdate, "checkEspionageSlots", checkEspionageSlots, "prioritizeKills", prioritizeKills, "sheet", sheet, "maxDef", maxDef, "doubleOps", doubleOps, "removeSheets", removeSheets, "prioritizeAlliances", prioritizeAlliances, "attackerWeighting", attackerWeighting, "defenderWeighting", defenderWeighting);
                    }
                }
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
                    public validate create(String sheet, String dayChange, String filter, String useLeader) {
                        return createArgs("sheet", sheet, "dayChange", dayChange, "filter", filter, "useLeader", useLeader);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="spyTierGraph")
            public static class tierGraph extends CommandRef {
                public static final tierGraph cmd = new tierGraph();
                public tierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants, String total, String barGraph, String attachJson, String attachCsv) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "total", total, "barGraph", barGraph, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
        }
        public static class stats_other{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsAB")
            public static class allianceMetricsAB extends CommandRef {
                public static final allianceMetricsAB cmd = new allianceMetricsAB();
                public allianceMetricsAB create(String metric, String coalition1, String coalition2, String time, String attachJson, String attachCsv) {
                    return createArgs("metric", metric, "coalition1", coalition1, "coalition2", coalition2, "time", time, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricsCompareByTurn")
            public static class allianceMetricsCompareByTurn extends CommandRef {
                public static final allianceMetricsCompareByTurn cmd = new allianceMetricsCompareByTurn();
                public allianceMetricsCompareByTurn create(String metric, String alliances, String time, String attachJson, String attachCsv) {
                    return createArgs("metric", metric, "alliances", alliances, "time", time, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceNationsSheet")
            public static class allianceNationsSheet extends CommandRef {
                public static final allianceNationsSheet cmd = new allianceNationsSheet();
                public allianceNationsSheet create(String nations, String columns, String sheet, String useTotal, String includeInactives, String includeApplicants) {
                    return createArgs("nations", nations, "columns", columns, "sheet", sheet, "useTotal", useTotal, "includeInactives", includeInactives, "includeApplicants", includeApplicants);
                }
            }
            public static class data_csv{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AllianceMetricCommands.class,method="AlliancesDataByDay")
                public static class AlliancesDataByDay extends CommandRef {
                    public static final AlliancesDataByDay cmd = new AlliancesDataByDay();
                    public AlliancesDataByDay create(String metric, String start, String end, String mode, String alliances, String graph) {
                        return createArgs("metric", metric, "start", start, "end", end, "mode", mode, "alliances", alliances, "graph", graph);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="findProducer")
            public static class findProducer extends CommandRef {
                public static final findProducer cmd = new findProducer();
                public findProducer create(String resources, String nationList, String ignoreMilitaryUpkeep, String ignoreTradeBonus, String ignoreNationBonus, String includeNegative, String listByNation, String listAverage, String uploadFile, String includeInactive, String snapshotDate) {
                    return createArgs("resources", resources, "nationList", nationList, "ignoreMilitaryUpkeep", ignoreMilitaryUpkeep, "ignoreTradeBonus", ignoreTradeBonus, "ignoreNationBonus", ignoreNationBonus, "includeNegative", includeNegative, "listByNation", listByNation, "listAverage", listAverage, "uploadFile", uploadFile, "includeInactive", includeInactive, "snapshotDate", snapshotDate);
                }
            }
            public static class global_metrics{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="orbisStatByDay")
                public static class by_time extends CommandRef {
                    public static final by_time cmd = new by_time();
                    public by_time create(String metrics, String start, String end, String attachJson, String attachCsv) {
                        return createArgs("metrics", metrics, "start", start, "end", end, "attachJson", attachJson, "attachCsv", attachCsv);
                    }
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
                public nationRanking create(String nations, String attribute, String groupByAlliance, String reverseOrder, String snapshotDate, String total) {
                    return createArgs("nations", nations, "attribute", attribute, "groupByAlliance", groupByAlliance, "reverseOrder", reverseOrder, "snapshotDate", snapshotDate, "total", total);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="radiationByTurn")
            public static class radiationByTurn extends CommandRef {
                public static final radiationByTurn cmd = new radiationByTurn();
                public radiationByTurn create(String continents, String time, String attachJson, String attachCsv) {
                    return createArgs("continents", continents, "time", time, "attachJson", attachJson, "attachCsv", attachCsv);
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
                public tradepricebyday create(String resources, String numDays, String attachJson, String attachCsv) {
                    return createArgs("resources", resources, "numDays", numDays, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
        }
        public static class stats_tier{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="attributeTierGraph")
            public static class attributeTierGraph extends CommandRef {
                public static final attributeTierGraph cmd = new attributeTierGraph();
                public attributeTierGraph create(String metric, String coalition1, String coalition2, String includeInactives, String includeApplicants, String total, String snapshotDate) {
                    return createArgs("metric", metric, "coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "total", total, "snapshotDate", snapshotDate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="cityTierGraph")
            public static class cityTierGraph extends CommandRef {
                public static final cityTierGraph cmd = new cityTierGraph();
                public cityTierGraph create(String coalition1, String coalition2, String includeInactives, String barGraph, String includeApplicants, String attachJson, String attachCsv, String snapshotDate) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "barGraph", barGraph, "includeApplicants", includeApplicants, "attachJson", attachJson, "attachCsv", attachCsv, "snapshotDate", snapshotDate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AllianceMetricCommands.class,method="metricByGroup")
            public static class metric_by_group extends CommandRef {
                public static final metric_by_group cmd = new metric_by_group();
                public metric_by_group create(String metrics, String nations, String groupBy, String includeInactives, String includeApplicants, String total, String snapshotDate, String attachJson, String attachCsv) {
                    return createArgs("metrics", metrics, "nations", nations, "groupBy", groupBy, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "total", total, "snapshotDate", snapshotDate, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="mmrTierGraph")
            public static class mmrTierGraph extends CommandRef {
                public static final mmrTierGraph cmd = new mmrTierGraph();
                public mmrTierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants, String sheet, String buildings, String snapshotDate) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "sheet", sheet, "buildings", buildings, "snapshotDate", snapshotDate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="scoreTierGraph")
            public static class scoreTierGraph extends CommandRef {
                public static final scoreTierGraph cmd = new scoreTierGraph();
                public scoreTierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants, String snapshotDate, String attachJson, String attachCsv) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "snapshotDate", snapshotDate, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="spyTierGraph")
            public static class spyTierGraph extends CommandRef {
                public static final spyTierGraph cmd = new spyTierGraph();
                public spyTierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants, String total, String barGraph, String attachJson, String attachCsv) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "total", total, "barGraph", barGraph, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="strengthTierGraph")
            public static class strengthTierGraph extends CommandRef {
                public static final strengthTierGraph cmd = new strengthTierGraph();
                public strengthTierGraph create(String coalition1, String coalition2, String includeInactives, String includeApplicants, String col1MMR, String col2MMR, String col1Infra, String col2Infra, String snapshotDate, String attachJson, String attachCsv) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "col1MMR", col1MMR, "col2MMR", col2MMR, "col1Infra", col1Infra, "col2Infra", col2Infra, "snapshotDate", snapshotDate, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
        }
        public static class stats_war{
            public static class attack_breakdown{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="attackBreakdownSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                    public sheet create(String attackers, String defenders, String start, String end, String sheet, String checkActivity) {
                        return createArgs("attackers", attackers, "defenders", defenders, "start", start, "end", end, "sheet", sheet, "checkActivity", checkActivity);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
            public static class counterStats extends CommandRef {
                public static final counterStats cmd = new counterStats();
                public counterStats create(String alliance) {
                    return createArgs("alliance", alliance);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="myloot")
            public static class myloot extends CommandRef {
                public static final myloot cmd = new myloot();
                public myloot create(String coalition2, String timeStart, String timeEnd, String ignoreUnits, String ignoreInfra, String ignoreConsumption, String ignoreLoot, String ignoreBuildings, String listWarIds, String showWarTypes, String allowedWarTypes, String allowedWarStatus, String allowedAttackTypes, String allowedVictoryTypes) {
                    return createArgs("coalition2", coalition2, "timeStart", timeStart, "timeEnd", timeEnd, "ignoreUnits", ignoreUnits, "ignoreInfra", ignoreInfra, "ignoreConsumption", ignoreConsumption, "ignoreLoot", ignoreLoot, "ignoreBuildings", ignoreBuildings, "listWarIds", listWarIds, "showWarTypes", showWarTypes, "allowedWarTypes", allowedWarTypes, "allowedWarStatus", allowedWarStatus, "allowedAttackTypes", allowedAttackTypes, "allowedVictoryTypes", allowedVictoryTypes);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warCost")
            public static class warCost extends CommandRef {
                public static final warCost cmd = new warCost();
                public warCost create(String war, String ignoreUnits, String ignoreInfra, String ignoreConsumption, String ignoreLoot, String ignoreBuildings) {
                    return createArgs("war", war, "ignoreUnits", ignoreUnits, "ignoreInfra", ignoreInfra, "ignoreConsumption", ignoreConsumption, "ignoreLoot", ignoreLoot, "ignoreBuildings", ignoreBuildings);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warCostRanking")
            public static class warCostRanking extends CommandRef {
                public static final warCostRanking cmd = new warCostRanking();
                public warCostRanking create(String timeStart, String timeEnd, String coalition1, String coalition2, String onlyRankCoalition1, String type, String stat, String excludeInfra, String excludeConsumption, String excludeLoot, String excludeBuildings, String excludeUnits, String groupByAlliance, String scalePerWar, String scalePerCity, String allowedWarTypes, String allowedWarStatuses, String allowedAttacks, String onlyOffensiveWars, String onlyDefensiveWars, String uploadFile) {
                    return createArgs("timeStart", timeStart, "timeEnd", timeEnd, "coalition1", coalition1, "coalition2", coalition2, "onlyRankCoalition1", onlyRankCoalition1, "type", type, "stat", stat, "excludeInfra", excludeInfra, "excludeConsumption", excludeConsumption, "excludeLoot", excludeLoot, "excludeBuildings", excludeBuildings, "excludeUnits", excludeUnits, "groupByAlliance", groupByAlliance, "scalePerWar", scalePerWar, "scalePerCity", scalePerCity, "allowedWarTypes", allowedWarTypes, "allowedWarStatuses", allowedWarStatuses, "allowedAttacks", allowedAttacks, "onlyOffensiveWars", onlyOffensiveWars, "onlyDefensiveWars", onlyDefensiveWars, "uploadFile", uploadFile);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="warRanking")
            public static class warRanking extends CommandRef {
                public static final warRanking cmd = new warRanking();
                public warRanking create(String time, String attackers, String defenders, String onlyOffensives, String onlyDefensives, String normalizePerMember, String ignore2dInactives, String rankByNation, String warType, String statuses) {
                    return createArgs("time", time, "attackers", attackers, "defenders", defenders, "onlyOffensives", onlyOffensives, "onlyDefensives", onlyDefensives, "normalizePerMember", normalizePerMember, "ignore2dInactives", ignore2dInactives, "rankByNation", rankByNation, "warType", warType, "statuses", statuses);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByAA")
            public static class warStatusRankingByAA extends CommandRef {
                public static final warStatusRankingByAA cmd = new warStatusRankingByAA();
                public warStatusRankingByAA create(String attackers, String defenders, String time) {
                    return createArgs("attackers", attackers, "defenders", defenders, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByNation")
            public static class warStatusRankingByNation extends CommandRef {
                public static final warStatusRankingByNation cmd = new warStatusRankingByNation();
                public warStatusRankingByNation create(String attackers, String defenders, String time) {
                    return createArgs("attackers", attackers, "defenders", defenders, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warAttacksByDay")
            public static class warattacksbyday extends CommandRef {
                public static final warattacksbyday cmd = new warattacksbyday();
                public warattacksbyday create(String nations, String cutoff, String allowedTypes) {
                    return createArgs("nations", nations, "cutoff", cutoff, "allowedTypes", allowedTypes);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warsCost")
            public static class warsCost extends CommandRef {
                public static final warsCost cmd = new warsCost();
                public warsCost create(String coalition1, String coalition2, String timeStart, String timeEnd, String ignoreUnits, String ignoreInfra, String ignoreConsumption, String ignoreLoot, String ignoreBuildings, String listWarIds, String showWarTypes, String allowedWarTypes, String allowedWarStatus, String allowedAttackTypes, String allowedVictoryTypes, String onlyOffensiveWars, String onlyDefensiveWars, String onlyOffensiveAttacks, String onlyDefensiveAttacks) {
                    return createArgs("coalition1", coalition1, "coalition2", coalition2, "timeStart", timeStart, "timeEnd", timeEnd, "ignoreUnits", ignoreUnits, "ignoreInfra", ignoreInfra, "ignoreConsumption", ignoreConsumption, "ignoreLoot", ignoreLoot, "ignoreBuildings", ignoreBuildings, "listWarIds", listWarIds, "showWarTypes", showWarTypes, "allowedWarTypes", allowedWarTypes, "allowedWarStatus", allowedWarStatus, "allowedAttackTypes", allowedAttackTypes, "allowedVictoryTypes", allowedVictoryTypes, "onlyOffensiveWars", onlyOffensiveWars, "onlyDefensiveWars", onlyDefensiveWars, "onlyOffensiveAttacks", onlyOffensiveAttacks, "onlyDefensiveAttacks", onlyDefensiveAttacks);
                }
            }
        }
        public static class tax{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxBracketSheet")
            public static class bracketsheet extends CommandRef {
                public static final bracketsheet cmd = new bracketsheet();
                public bracketsheet create(String force, String includeApplicants) {
                    return createArgs("force", force, "includeApplicants", includeApplicants);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxDeposits")
            public static class deposits extends CommandRef {
                public static final deposits cmd = new deposits();
                public deposits create(String nations, String baseTaxRate, String startDate, String endDate, String sheet) {
                    return createArgs("nations", nations, "baseTaxRate", baseTaxRate, "startDate", startDate, "endDate", endDate, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxInfo")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="listRequiredTaxRates")
            public static class listBracketAuto extends CommandRef {
                public static final listBracketAuto cmd = new listBracketAuto();
                public listBracketAuto create(String sheet) {
                    return createArgs("sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxRecords")
            public static class records extends CommandRef {
                public static final records cmd = new records();
                public records create(String nation, String startDate, String endDate, String sheet) {
                    return createArgs("nation", nation, "startDate", startDate, "endDate", endDate, "sheet", sheet);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setNationTaxBrackets")
            public static class setNationBracketAuto extends CommandRef {
                public static final setNationBracketAuto cmd = new setNationBracketAuto();
                public setNationBracketAuto create(String nations, String ping) {
                    return createArgs("nations", nations, "ping", ping);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="setBracketBulk")
            public static class set_from_sheet extends CommandRef {
                public static final set_from_sheet cmd = new set_from_sheet();
                public set_from_sheet create(String sheet, String force) {
                    return createArgs("sheet", sheet, "force", force);
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
        }
        public static class trade{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="acceptTrades")
            public static class accept extends CommandRef {
                public static final accept cmd = new accept();
                public accept create(String receiver, String amount, String useLogin, String force) {
                    return createArgs("receiver", receiver, "amount", amount, "useLogin", useLogin, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="GlobalTradeAverage")
            public static class average extends CommandRef {
                public static final average cmd = new average();
                public average create(String time) {
                    return createArgs("time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="compareStockpileValueByDay")
            public static class compareStockpileValueByDay extends CommandRef {
                public static final compareStockpileValueByDay cmd = new compareStockpileValueByDay();
                public compareStockpileValueByDay create(String stockpile1, String stockpile2, String numDays, String attachJson, String attachCsv) {
                    return createArgs("stockpile1", stockpile1, "stockpile2", stockpile2, "numDays", numDays, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="findProducer")
            public static class findProducer extends CommandRef {
                public static final findProducer cmd = new findProducer();
                public findProducer create(String resources, String nationList, String ignoreMilitaryUpkeep, String ignoreTradeBonus, String ignoreNationBonus, String includeNegative, String listByNation, String listAverage, String uploadFile, String includeInactive, String snapshotDate) {
                    return createArgs("resources", resources, "nationList", nationList, "ignoreMilitaryUpkeep", ignoreMilitaryUpkeep, "ignoreTradeBonus", ignoreTradeBonus, "ignoreNationBonus", ignoreNationBonus, "includeNegative", includeNegative, "listByNation", listByNation, "listAverage", listAverage, "uploadFile", uploadFile, "includeInactive", includeInactive, "snapshotDate", snapshotDate);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="findTrader")
            public static class findTrader extends CommandRef {
                public static final findTrader cmd = new findTrader();
                public findTrader create(String type, String cutoff, String buyOrSell, String groupByAlliance, String includeMoneyTrades) {
                    return createArgs("type", type, "cutoff", cutoff, "buyOrSell", buyOrSell, "groupByAlliance", groupByAlliance, "includeMoneyTrades", includeMoneyTrades);
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
                public marginByDay create(String numDays, String percent, String attachJson, String attachCsv) {
                    return createArgs("numDays", numDays, "percent", percent, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="moneyTrades")
            public static class moneyTrades extends CommandRef {
                public static final moneyTrades cmd = new moneyTrades();
                public moneyTrades create(String nation, String time, String forceUpdate, String addBalance) {
                    return createArgs("nation", nation, "time", time, "forceUpdate", forceUpdate, "addBalance", addBalance);
                }
            }
            public static class offer{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="buyOffer")
                public static class buy extends CommandRef {
                    public static final buy cmd = new buy();
                    public buy create(String resource, String quantity, String minPPU, String maxPPU, String negotiable, String expire, String exchangeFor, String exchangePPU, String force) {
                        return createArgs("resource", resource, "quantity", quantity, "minPPU", minPPU, "maxPPU", maxPPU, "negotiable", negotiable, "expire", expire, "exchangeFor", exchangeFor, "exchangePPU", exchangePPU, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="buyList")
                public static class buy_list extends CommandRef {
                    public static final buy_list cmd = new buy_list();
                    public buy_list create(String youBuy, String youProvide, String allowedTraders, String sortByLowestMinPrice, String sortByLowestMaxPrice) {
                        return createArgs("youBuy", youBuy, "youProvide", youProvide, "allowedTraders", allowedTraders, "sortByLowestMinPrice", sortByLowestMinPrice, "sortByLowestMaxPrice", sortByLowestMaxPrice);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="deleteOffer")
                public static class delete extends CommandRef {
                    public static final delete cmd = new delete();
                    public delete create(String deleteResource, String buyOrSell, String deleteId) {
                        return createArgs("deleteResource", deleteResource, "buyOrSell", buyOrSell, "deleteId", deleteId);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="offerInfo")
                public static class info extends CommandRef {
                    public static final info cmd = new info();
                    public info create(String offerId) {
                        return createArgs("offerId", offerId);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="myOffers")
                public static class my_offers extends CommandRef {
                    public static final my_offers cmd = new my_offers();
                    public my_offers create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="sellOffer")
                public static class sell extends CommandRef {
                    public static final sell cmd = new sell();
                    public sell create(String resource, String quantity, String minPPU, String maxPPU, String negotiable, String expire, String exchangeFor, String exchangePPU, String force) {
                        return createArgs("resource", resource, "quantity", quantity, "minPPU", minPPU, "maxPPU", maxPPU, "negotiable", negotiable, "expire", expire, "exchangeFor", exchangeFor, "exchangePPU", exchangePPU, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="sellList")
                public static class sell_list extends CommandRef {
                    public static final sell_list cmd = new sell_list();
                    public sell_list create(String youSell, String youReceive, String allowedTraders, String sortByLowestMinPrice, String sortByLowestMaxPrice) {
                        return createArgs("youSell", youSell, "youReceive", youReceive, "allowedTraders", allowedTraders, "sortByLowestMinPrice", sortByLowestMinPrice, "sortByLowestMaxPrice", sortByLowestMaxPrice);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="updateOffer")
                public static class update extends CommandRef {
                    public static final update cmd = new update();
                    public update create(String offerId, String quantity, String minPPU, String maxPPU, String negotiable, String expire, String exchangeFor, String exchangePPU, String force) {
                        return createArgs("offerId", offerId, "quantity", quantity, "minPPU", minPPU, "maxPPU", maxPPU, "negotiable", negotiable, "expire", expire, "exchangeFor", exchangeFor, "exchangePPU", exchangePPU, "force", force);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradePrice")
            public static class price extends CommandRef {
                public static final price cmd = new price();
                public price create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradepricebyday")
            public static class priceByDay extends CommandRef {
                public static final priceByDay cmd = new priceByDay();
                public priceByDay create(String resources, String numDays, String attachJson, String attachCsv) {
                    return createArgs("resources", resources, "numDays", numDays, "attachJson", attachJson, "attachCsv", attachCsv);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeProfit")
            public static class profit extends CommandRef {
                public static final profit cmd = new profit();
                public profit create(String nations, String time) {
                    return createArgs("nations", nations, "time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeRanking")
            public static class ranking extends CommandRef {
                public static final ranking cmd = new ranking();
                public ranking create(String nations, String time, String groupByAlliance, String uploadFile) {
                    return createArgs("nations", nations, "time", time, "groupByAlliance", groupByAlliance, "uploadFile", uploadFile);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradetotalbyday")
            public static class totalByDay extends CommandRef {
                public static final totalByDay cmd = new totalByDay();
                public totalByDay create(String numDays, String attachJson, String attachCsv, String resources) {
                    return createArgs("numDays", numDays, "attachJson", attachJson, "attachCsv", attachCsv, "resources", resources);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="trending")
            public static class trending extends CommandRef {
                public static final trending cmd = new trending();
                public trending create(String time) {
                    return createArgs("time", time);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="convertedTotal")
            public static class value extends CommandRef {
                public static final value cmd = new value();
                public value create(String resources, String normalize, String useBuyPrice, String useSellPrice, String convertType) {
                    return createArgs("resources", resources, "normalize", normalize, "useBuyPrice", useBuyPrice, "useSellPrice", useSellPrice, "convertType", convertType);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="GlobalTradeVolume")
            public static class volume extends CommandRef {
                public static final volume cmd = new volume();
                public volume create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradevolumebyday")
            public static class volumebyday extends CommandRef {
                public static final volumebyday cmd = new volumebyday();
                public volumebyday create(String numDays, String attachJson, String attachCsv, String resources) {
                    return createArgs("numDays", numDays, "attachJson", attachJson, "attachCsv", attachCsv, "resources", resources);
                }
            }
        }
        public static class transfer{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transferBulk")
            public static class bulk extends CommandRef {
                public static final bulk cmd = new bulk();
                public bulk create(String sheet, String depositType, String depositsAccount, String useAllianceBank, String useOffshoreAccount, String taxAccount, String existingTaxAccount, String expire, String decay, String convertToMoney, String escrow_mode, String bypassChecks, String force, String key) {
                    return createArgs("sheet", sheet, "depositType", depositType, "depositsAccount", depositsAccount, "useAllianceBank", useAllianceBank, "useOffshoreAccount", useOffshoreAccount, "taxAccount", taxAccount, "existingTaxAccount", existingTaxAccount, "expire", expire, "decay", decay, "convertToMoney", convertToMoney, "escrow_mode", escrow_mode, "bypassChecks", bypassChecks, "force", force, "key", key);
                }
            }
            public static class internal{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="send")
                public static class from_nation_account extends CommandRef {
                    public static final from_nation_account cmd = new from_nation_account();
                    public from_nation_account create(String amount, String receiver_account, String receiver_nation, String sender_alliance, String force) {
                        return createArgs("amount", amount, "receiver_account", receiver_account, "receiver_nation", receiver_nation, "sender_alliance", sender_alliance, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="sendAA")
                public static class from_offshore_account extends CommandRef {
                    public static final from_offshore_account cmd = new from_offshore_account();
                    public from_offshore_account create(String amount, String receiver_account, String receiver_nation, String sender_alliance, String sender_nation, String force) {
                        return createArgs("amount", amount, "receiver_account", receiver_account, "receiver_nation", receiver_nation, "sender_alliance", sender_alliance, "sender_nation", sender_nation, "force", force);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="offshore")
            public static class offshore extends CommandRef {
                public static final offshore cmd = new offshore();
                public offshore create(String to, String account, String keepAmount, String sendAmount) {
                    return createArgs("to", to, "account", account, "keepAmount", keepAmount, "sendAmount", sendAmount);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="disburse")
            public static class raws extends CommandRef {
                public static final raws cmd = new raws();
                public raws create(String nationList, String days, String no_daily_cash, String no_cash, String bank_note, String expire, String decay, String deduct_as_cash, String nation_account, String escrow_mode, String ingame_bank, String offshore_account, String tax_account, String use_receiver_tax_account, String bypass_checks, String force) {
                    return createArgs("nationList", nationList, "days", days, "no_daily_cash", no_daily_cash, "no_cash", no_cash, "bank_note", bank_note, "expire", expire, "decay", decay, "deduct_as_cash", deduct_as_cash, "nation_account", nation_account, "escrow_mode", escrow_mode, "ingame_bank", ingame_bank, "offshore_account", offshore_account, "tax_account", tax_account, "use_receiver_tax_account", use_receiver_tax_account, "bypass_checks", bypass_checks, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transfer")
            public static class resources extends CommandRef {
                public static final resources cmd = new resources();
                public resources create(String receiver, String transfer, String depositType, String nationAccount, String senderAlliance, String allianceAccount, String taxAccount, String existingTaxAccount, String onlyMissingFunds, String expire, String decay, String token, String convertCash, String escrow_mode, String bypassChecks, String force) {
                    return createArgs("receiver", receiver, "transfer", transfer, "depositType", depositType, "nationAccount", nationAccount, "senderAlliance", senderAlliance, "allianceAccount", allianceAccount, "taxAccount", taxAccount, "existingTaxAccount", existingTaxAccount, "onlyMissingFunds", onlyMissingFunds, "expire", expire, "decay", decay, "token", token, "convertCash", convertCash, "escrow_mode", escrow_mode, "bypassChecks", bypassChecks, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="withdraw")
            public static class self extends CommandRef {
                public static final self cmd = new self();
                public self create(String amount, String only_send_missing, String bank_note, String expire, String decay, String deduct_as_cash, String ingame_bank, String offshore_account, String nation_account, String escrow_mode, String tax_account, String use_receiver_tax_account, String bypass_checks, String force) {
                    return createArgs("amount", amount, "only_send_missing", only_send_missing, "bank_note", bank_note, "expire", expire, "decay", decay, "deduct_as_cash", deduct_as_cash, "ingame_bank", ingame_bank, "offshore_account", offshore_account, "nation_account", nation_account, "escrow_mode", escrow_mode, "tax_account", tax_account, "use_receiver_tax_account", use_receiver_tax_account, "bypass_checks", bypass_checks, "force", force);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="warchest")
            public static class warchest extends CommandRef {
                public static final warchest cmd = new warchest();
                public warchest create(String nations, String resourcesPerCity, String note, String skipStockpile, String depositsAccount, String useAllianceBank, String useOffshoreAccount, String taxAccount, String existingTaxAccount, String expire, String decay, String convertToMoney, String escrow_mode, String bypassChecks, String force) {
                    return createArgs("nations", nations, "resourcesPerCity", resourcesPerCity, "note", note, "skipStockpile", skipStockpile, "depositsAccount", depositsAccount, "useAllianceBank", useAllianceBank, "useOffshoreAccount", useOffshoreAccount, "taxAccount", taxAccount, "existingTaxAccount", existingTaxAccount, "expire", expire, "decay", decay, "convertToMoney", convertToMoney, "escrow_mode", escrow_mode, "bypassChecks", bypassChecks, "force", force);
                }
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="cancelTreaty")
            public static class cancel extends CommandRef {
                public static final cancel cmd = new cancel();
                public cancel create(String senders) {
                    return createArgs("senders", senders);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="nap")
            public static class gw_nap extends CommandRef {
                public static final gw_nap cmd = new gw_nap();
                public gw_nap create() {
                    return createArgs();
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="treaties")
            public static class list extends CommandRef {
                public static final list cmd = new list();
                public list create(String alliances, String treatyFilter) {
                    return createArgs("alliances", alliances, "treatyFilter", treatyFilter);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="sendTreaty")
            public static class send extends CommandRef {
                public static final send cmd = new send();
                public send create(String receiver, String type, String days, String message) {
                    return createArgs("receiver", receiver, "type", type, "days", days, "message", message);
                }
            }
        }
        public static class unit{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="unitCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
                public cost create(String units, String wartime) {
                    return createArgs("units", units, "wartime", wartime);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="unitHistory")
            public static class history extends CommandRef {
                public static final history cmd = new history();
                public history create(String nation, String unit, String page) {
                    return createArgs("nation", nation, "unit", unit, "page", page);
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
        public static class war{
            public static class blockade{
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unblockadeMe")
                public static class request extends CommandRef {
                    public static final request cmd = new request();
                    public request create(String diff, String note, String force) {
                        return createArgs("diff", diff, "note", note, "force", force);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="canIBeige")
            public static class canIBeige extends CommandRef {
                public static final canIBeige cmd = new canIBeige();
                public canIBeige create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warcard")
            public static class card extends CommandRef {
                public static final card cmd = new card();
                public card create(String warId) {
                    return createArgs("warId", warId);
                }
            }
            public static class counter{
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
                    public nation create(String target, String counterWith, String allowMaxOffensives, String filterWeak, String onlyOnline, String requireDiscord, String allowSameAlliance, String includeInactive, String includeNonMembers, String ping) {
                        return createArgs("target", target, "counterWith", counterWith, "allowMaxOffensives", allowMaxOffensives, "filterWeak", filterWeak, "onlyOnline", onlyOnline, "requireDiscord", requireDiscord, "allowSameAlliance", allowSameAlliance, "includeInactive", includeInactive, "includeNonMembers", includeNonMembers, "ping", ping);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="counterSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                    public sheet create(String enemyFilter, String allies, String excludeApplicants, String excludeInactives, String includeAllEnemies, String sheetUrl) {
                        return createArgs("enemyFilter", enemyFilter, "allies", allies, "excludeApplicants", excludeApplicants, "excludeInactives", excludeInactives, "includeAllEnemies", includeAllEnemies, "sheetUrl", sheetUrl);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
                public static class stats extends CommandRef {
                    public static final stats cmd = new stats();
                    public stats create(String alliance) {
                        return createArgs("alliance", alliance);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="counterWar")
                public static class url extends CommandRef {
                    public static final url cmd = new url();
                    public url create(String war, String counterWith, String allowAttackersWithMaxOffensives, String filterWeak, String onlyActive, String requireDiscord, String allowSameAlliance, String includeInactive, String includeNonMembers, String ping) {
                        return createArgs("war", war, "counterWith", counterWith, "allowAttackersWithMaxOffensives", allowAttackersWithMaxOffensives, "filterWeak", filterWeak, "onlyActive", onlyActive, "requireDiscord", requireDiscord, "allowSameAlliance", allowSameAlliance, "includeInactive", includeInactive, "includeNonMembers", includeNonMembers, "ping", ping);
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
            public static class find{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="BlitzPractice")
                public static class blitztargets extends CommandRef {
                    public static final blitztargets cmd = new blitztargets();
                    public blitztargets create(String topX, String page) {
                        return createArgs("topX", topX, "page", page);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="findBountyNations")
                public static class bounty extends CommandRef {
                    public static final bounty cmd = new bounty();
                    public bounty create(String onlyWeaker, String ignoreDNR, String bountyTypes, String numResults) {
                        return createArgs("onlyWeaker", onlyWeaker, "ignoreDNR", ignoreDNR, "bountyTypes", bountyTypes, "numResults", numResults);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="damage")
                public static class damage extends CommandRef {
                    public static final damage cmd = new damage();
                    public damage create(String nations, String includeApps, String includeInactives, String filterWeak, String noNavy, String targetMeanInfra, String targetCityMax, String includeBeige, String resultsInDm, String warRange, String relativeNavalStrength) {
                        return createArgs("nations", nations, "includeApps", includeApps, "includeInactives", includeInactives, "filterWeak", filterWeak, "noNavy", noNavy, "targetMeanInfra", targetMeanInfra, "targetCityMax", targetCityMax, "includeBeige", includeBeige, "resultsInDm", resultsInDm, "warRange", warRange, "relativeNavalStrength", relativeNavalStrength);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="war")
                public static class enemy extends CommandRef {
                    public static final enemy cmd = new enemy();
                    public enemy create(String targets, String numResults, String attackerScore, String includeInactives, String includeApplicants, String onlyPriority, String onlyWeak, String onlyEasy, String onlyLessCities, String resultsInDm, String includeStrong) {
                        return createArgs("targets", targets, "numResults", numResults, "attackerScore", attackerScore, "includeInactives", includeInactives, "includeApplicants", includeApplicants, "onlyPriority", onlyPriority, "onlyWeak", onlyWeak, "onlyEasy", onlyEasy, "onlyLessCities", onlyLessCities, "resultsInDm", resultsInDm, "includeStrong", includeStrong);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="raid")
                public static class raid extends CommandRef {
                    public static final raid cmd = new raid();
                    public raid create(String targets, String numResults, String activeTimeCutoff, String weakground, String beigeTurns, String vmTurns, String nationScore, String defensiveSlots, String ignoreDNR, String ignoreBankLoot, String ignoreCityRevenue) {
                        return createArgs("targets", targets, "numResults", numResults, "activeTimeCutoff", activeTimeCutoff, "weakground", weakground, "beigeTurns", beigeTurns, "vmTurns", vmTurns, "nationScore", nationScore, "defensiveSlots", defensiveSlots, "ignoreDNR", ignoreDNR, "ignoreBankLoot", ignoreBankLoot, "ignoreCityRevenue", ignoreCityRevenue);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="findTreasureNations")
                public static class treasure extends CommandRef {
                    public static final treasure cmd = new treasure();
                    public treasure create(String onlyWeaker, String ignoreDNR, String numResults) {
                        return createArgs("onlyWeaker", onlyWeaker, "ignoreDNR", ignoreDNR, "numResults", numResults);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unblockade")
                public static class unblockade extends CommandRef {
                    public static final unblockade cmd = new unblockade();
                    public unblockade create(String allies, String targets, String myShips, String numResults) {
                        return createArgs("allies", allies, "targets", targets, "myShips", myShips, "numResults", numResults);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unprotected")
                public static class unprotected extends CommandRef {
                    public static final unprotected cmd = new unprotected();
                    public unprotected create(String targets, String numResults, String ignoreDNR, String includeAllies, String nationsToBlitzWith, String maxRelativeTargetStrength, String maxRelativeCounterStrength, String withinAllAttackersRange, String ignoreODP, String force) {
                        return createArgs("targets", targets, "numResults", numResults, "ignoreDNR", ignoreDNR, "includeAllies", includeAllies, "nationsToBlitzWith", nationsToBlitzWith, "maxRelativeTargetStrength", maxRelativeTargetStrength, "maxRelativeCounterStrength", maxRelativeCounterStrength, "withinAllAttackersRange", withinAllAttackersRange, "ignoreODP", ignoreODP, "force", force);
                    }
                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="wars")
            public static class info extends CommandRef {
                public static final info cmd = new info();
                public info create(String nation) {
                    return createArgs("nation", nation);
                }
            }
            public static class room{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warroom")
                public static class create extends CommandRef {
                    public static final create cmd = new create();
                    public create create(String enemy, String attackers, String max, String force, String excludeWeakAttackers, String requireDiscord, String allowAttackersWithMaxOffensives, String pingMembers, String skipAddMembers, String sendMail) {
                        return createArgs("enemy", enemy, "attackers", attackers, "max", max, "force", force, "excludeWeakAttackers", excludeWeakAttackers, "requireDiscord", requireDiscord, "allowAttackersWithMaxOffensives", allowAttackersWithMaxOffensives, "pingMembers", pingMembers, "skipAddMembers", skipAddMembers, "sendMail", sendMail);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="deleteForEnemies")
                public static class delete_for_enemies extends CommandRef {
                    public static final delete_for_enemies cmd = new delete_for_enemies();
                    public delete_for_enemies create(String enemy_rooms) {
                        return createArgs("enemy_rooms", enemy_rooms);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="deletePlanningChannel")
                public static class delete_planning extends CommandRef {
                    public static final delete_planning cmd = new delete_planning();
                    public delete_planning create() {
                        return createArgs();
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warRoomSheet")
                public static class from_sheet extends CommandRef {
                    public static final from_sheet cmd = new from_sheet();
                    public from_sheet create(String blitzSheet, String customMessage, String addCounterMessage, String ping, String addMember, String allowedNations, String headerRow, String useLeader, String force) {
                        return createArgs("blitzSheet", blitzSheet, "customMessage", customMessage, "addCounterMessage", addCounterMessage, "ping", ping, "addMember", addMember, "allowedNations", allowedNations, "headerRow", headerRow, "useLeader", useLeader, "force", force);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warRoomList")
                public static class list extends CommandRef {
                    public static final list cmd = new list();
                    public list create(String nation) {
                        return createArgs("nation", nation);
                    }
                }
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
            }
            public static class sheet{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warReimburseByNationCsv")
                public static class ReimburseByNation extends CommandRef {
                    public static final ReimburseByNation cmd = new ReimburseByNation();
                    public ReimburseByNation create(String allies, String enemies, String cutoff, String removeWarsWithNoDefenderActions) {
                        return createArgs("allies", allies, "enemies", enemies, "cutoff", cutoff, "removeWarsWithNoDefenderActions", removeWarsWithNoDefenderActions);
                    }
                }
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostSheet")
                public static class costSheet extends CommandRef {
                    public static final costSheet cmd = new costSheet();
                    public costSheet create(String attackers, String defenders, String time, String endTime, String excludeConsumption, String excludeInfra, String excludeLoot, String excludeUnitCost, String normalizePerCity, String useLeader, String sheet) {
                        return createArgs("attackers", attackers, "defenders", defenders, "time", time, "endTime", endTime, "excludeConsumption", excludeConsumption, "excludeInfra", excludeInfra, "excludeLoot", excludeLoot, "excludeUnitCost", excludeUnitCost, "normalizePerCity", normalizePerCity, "useLeader", useLeader, "sheet", sheet);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="raidSheet")
                public static class raid extends CommandRef {
                    public static final raid cmd = new raid();
                    public raid create(String attackers, String targets, String includeInactiveAttackers, String includeApplicantAttackers, String includeBeigeAttackers, String sheet) {
                        return createArgs("attackers", attackers, "targets", targets, "includeInactiveAttackers", includeInactiveAttackers, "includeApplicantAttackers", includeApplicantAttackers, "includeBeigeAttackers", includeBeigeAttackers, "sheet", sheet);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ValidateBlitzSheet")
                public static class validate extends CommandRef {
                    public static final validate cmd = new validate();
                    public validate create(String sheet, String maxWars, String nationsFilter, String attackerFilter, String useLeader, String headerRow) {
                        return createArgs("sheet", sheet, "maxWars", maxWars, "nationsFilter", nationsFilter, "attackerFilter", attackerFilter, "useLeader", useLeader, "headerRow", headerRow);
                    }
                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warSheet")
                public static class warSheet extends CommandRef {
                    public static final warSheet cmd = new warSheet();
                    public warSheet create(String allies, String enemies, String cutoff, String includeConcludedWars, String sheet) {
                        return createArgs("allies", allies, "enemies", enemies, "cutoff", cutoff, "includeConcludedWars", includeConcludedWars, "sheet", sheet);
                    }
                }
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
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="who")
        public static class who extends CommandRef {
            public static final who cmd = new who();
            public who create(String nationOrAlliances, String sortBy, String list, String listAlliances, String listRawUserIds, String listMentions, String listInfo, String listChannels, String snapshotDate, String page) {
                return createArgs("nationOrAlliances", nationOrAlliances, "sortBy", sortBy, "list", list, "listAlliances", listAlliances, "listRawUserIds", listRawUserIds, "listMentions", listMentions, "listInfo", listInfo, "listChannels", listChannels, "snapshotDate", snapshotDate, "page", page);
            }
        }

}
