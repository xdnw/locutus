package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM {
        public static class admin{
            public static class bot{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="importEmojis")
                public static class import_emojis extends CommandRef {
                    public static final import_emojis cmd = new import_emojis();
                public import_emojis guild(String value) {
                    return set("guild", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="importGuildKeys")
                public static class import_settings extends CommandRef {
                    public static final import_settings cmd = new import_settings();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="setProfile")
                public static class profile extends CommandRef {
                    public static final profile cmd = new profile();
                public profile url(String value) {
                    return set("url", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="removeInvalidOffshoring")
                public static class remove_deleted_offshores extends CommandRef {
                    public static final remove_deleted_offshores cmd = new remove_deleted_offshores();
                public remove_deleted_offshores coalition(String value) {
                    return set("coalition", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="setBotName")
                public static class rename extends CommandRef {
                    public static final rename cmd = new rename();
                public rename name(String value) {
                    return set("name", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="stop")
                public static class stop extends CommandRef {
                    public static final stop cmd = new stop();
                public stop save(String value) {
                    return set("save", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="upsertCommands")
                public static class update_commands extends CommandRef {
                    public static final update_commands cmd = new update_commands();

                }
            }
            public static class command{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="runForNations")
                public static class format_for_nations extends CommandRef {
                    public static final format_for_nations cmd = new format_for_nations();
                public format_for_nations nations(String value) {
                    return set("nations", value);
                }

                public format_for_nations command(String value) {
                    return set("command", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="runMultiple")
                public static class multiple extends CommandRef {
                    public static final multiple cmd = new multiple();
                public multiple commands(String value) {
                    return set("commands", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="sudo")
                public static class sudo extends CommandRef {
                    public static final sudo cmd = new sudo();
                public sudo command(String value) {
                    return set("command", value);
                }

                public sudo user(String value) {
                    return set("user", value);
                }

                public sudo nation(String value) {
                    return set("nation", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="sudoNations")
                public static class sudo_nations extends CommandRef {
                    public static final sudo_nations cmd = new sudo_nations();
                public sudo_nations nations(String value) {
                    return set("nations", value);
                }

                public sudo_nations command(String value) {
                    return set("command", value);
                }

                }
            }
            public static class debug{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="apiUsageStats")
                public static class api_usage extends CommandRef {
                    public static final api_usage cmd = new api_usage();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="guildInfo")
                public static class guild extends CommandRef {
                    public static final guild cmd = new guild();
                public guild guild(String value) {
                    return set("guild", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="msgInfo")
                public static class msg_info extends CommandRef {
                    public static final msg_info cmd = new msg_info();
                public msg_info message(String value) {
                    return set("message", value);
                }

                public msg_info useIds(String value) {
                    return set("useIds", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="nationMeta")
                public static class nation_meta extends CommandRef {
                    public static final nation_meta cmd = new nation_meta();
                public nation_meta nation(String value) {
                    return set("nation", value);
                }

                public nation_meta meta(String value) {
                    return set("meta", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="newOffshore")
                public static class new_offshore extends CommandRef {
                    public static final new_offshore cmd = new new_offshore();
                public new_offshore alliance(String value) {
                    return set("alliance", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="tradeId")
                public static class trade_id extends CommandRef {
                    public static final trade_id cmd = new trade_id();
                public trade_id ids(String value) {
                    return set("ids", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="dm")
            public static class dm extends CommandRef {
                public static final dm cmd = new dm();
            public dm nations(String value) {
                return set("nations", value);
            }

            public dm message(String value) {
                return set("message", value);
            }

            public dm force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="leaveServer")
            public static class leaveServer extends CommandRef {
                public static final leaveServer cmd = new leaveServer();
            public leaveServer guildId(String value) {
                return set("guildId", value);
            }

            }
            public static class list{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listAuthenticated")
                public static class authenticated extends CommandRef {
                    public static final authenticated cmd = new authenticated();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listExpiredGuilds")
                public static class expired_guilds extends CommandRef {
                    public static final expired_guilds cmd = new expired_guilds();
                public expired_guilds checkMessages(String value) {
                    return set("checkMessages", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listExpiredOffshores")
                public static class expired_offshores extends CommandRef {
                    public static final expired_offshores cmd = new expired_offshores();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="listGuildOwners")
                public static class guild_owners extends CommandRef {
                    public static final guild_owners cmd = new guild_owners();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="loginTimes")
                public static class login_times extends CommandRef {
                    public static final login_times cmd = new login_times();
                public login_times nations(String value) {
                    return set("nations", value);
                }

                public login_times cutoff(String value) {
                    return set("cutoff", value);
                }

                public login_times sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="hasSameNetworkAsBan")
                public static class multis extends CommandRef {
                    public static final multis cmd = new multis();
                public multis nations(String value) {
                    return set("nations", value);
                }

                public multis listExpired(String value) {
                    return set("listExpired", value);
                }

                public multis onlySameAlliance(String value) {
                    return set("onlySameAlliance", value);
                }

                public multis onlySimilarTime(String value) {
                    return set("onlySimilarTime", value);
                }

                public multis sortByAgeDays(String value) {
                    return set("sortByAgeDays", value);
                }

                public multis sortByLogin(String value) {
                    return set("sortByLogin", value);
                }

                public multis forceUpdate(String value) {
                    return set("forceUpdate", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="multiInfoSheet")
                public static class multis_land extends CommandRef {
                    public static final multis_land cmd = new multis_land();
                public multis_land nations(String value) {
                    return set("nations", value);
                }

                public multis_land sheet(String value) {
                    return set("sheet", value);
                }

                public multis_land mark(String value) {
                    return set("mark", value);
                }

                }
            }
            public static class queue{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="conditionalMessageSettings")
                public static class custom_messages extends CommandRef {
                    public static final custom_messages cmd = new custom_messages();
                public custom_messages setMeta(String value) {
                    return set("setMeta", value);
                }

                public custom_messages sendMessages(String value) {
                    return set("sendMessages", value);
                }

                public custom_messages run(String value) {
                    return set("run", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="showFileQueue")
                public static class file extends CommandRef {
                    public static final file cmd = new file();
                public file timestamp(String value) {
                    return set("timestamp", value);
                }

                public file numResults(String value) {
                    return set("numResults", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="setV2")
            public static class set_v2 extends CommandRef {
                public static final set_v2 cmd = new set_v2();
            public set_v2 value(String value) {
                return set("value", value);
            }

            }
            public static class settings{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="infoBulk")
                public static class info_servers extends CommandRef {
                    public static final info_servers cmd = new info_servers();
                public info_servers setting(String value) {
                    return set("setting", value);
                }

                public info_servers guilds(String value) {
                    return set("guilds", value);
                }

                public info_servers sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="unsetNews")
                public static class subscribe extends CommandRef {
                    public static final subscribe cmd = new subscribe();
                public subscribe setting(String value) {
                    return set("setting", value);
                }

                public subscribe guilds(String value) {
                    return set("guilds", value);
                }

                public subscribe news_channel(String value) {
                    return set("news_channel", value);
                }

                public subscribe unset_on_error(String value) {
                    return set("unset_on_error", value);
                }

                public subscribe force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="unsetKeys")
                public static class unset extends CommandRef {
                    public static final unset cmd = new unset();
                public unset settings(String value) {
                    return set("settings", value);
                }

                public unset guilds(String value) {
                    return set("guilds", value);
                }

                public unset unset_cant_talk(String value) {
                    return set("unset_cant_talk", value);
                }

                public unset unset_null(String value) {
                    return set("unset_null", value);
                }

                public unset unset_key_no_perms(String value) {
                    return set("unset_key_no_perms", value);
                }

                public unset unset_invalid_aa(String value) {
                    return set("unset_invalid_aa", value);
                }

                public unset unset_all(String value) {
                    return set("unset_all", value);
                }

                public unset unset_validate(String value) {
                    return set("unset_validate", value);
                }

                public unset unsetMessage(String value) {
                    return set("unsetMessage", value);
                }

                public unset force(String value) {
                    return set("force", value);
                }

                }
            }
            public static class sync{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncAttacks")
                public static class attacks extends CommandRef {
                    public static final attacks cmd = new attacks();
                public attacks runAlerts(String value) {
                    return set("runAlerts", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBans")
                public static class bans extends CommandRef {
                    public static final bans cmd = new bans();
                public bans discordBans(String value) {
                    return set("discordBans", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBounties")
                public static class bounties extends CommandRef {
                    public static final bounties cmd = new bounties();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncCitiesTest")
                public static class cities extends CommandRef {
                    public static final cities cmd = new cities();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncDiscordWithLocutus")
                public static class discord extends CommandRef {
                    public static final discord cmd = new discord();
                public discord url(String value) {
                    return set("url", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncForumProfiles")
                public static class forum_profiles extends CommandRef {
                    public static final forum_profiles cmd = new forum_profiles();
                public forum_profiles sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="importLinkedBans")
                public static class multi_bans extends CommandRef {
                    public static final multi_bans cmd = new multi_bans();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncOffshore")
                public static class offshore extends CommandRef {
                    public static final offshore cmd = new offshore();
                public offshore alliance(String value) {
                    return set("alliance", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="savePojos")
                public static class pojos extends CommandRef {
                    public static final pojos cmd = new pojos();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AllianceMetricCommands.class,method="saveMetrics")
                public static class saveMetrics extends CommandRef {
                    public static final saveMetrics cmd = new saveMetrics();
                public saveMetrics metrics(String value) {
                    return set("metrics", value);
                }

                public saveMetrics start(String value) {
                    return set("start", value);
                }

                public saveMetrics end(String value) {
                    return set("end", value);
                }

                public saveMetrics overwrite(String value) {
                    return set("overwrite", value);
                }

                public saveMetrics saveAllTurns(String value) {
                    return set("saveAllTurns", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBanks")
                public static class syncBanks extends CommandRef {
                    public static final syncBanks cmd = new syncBanks();
                public syncBanks alliance(String value) {
                    return set("alliance", value);
                }

                public syncBanks timestamp(String value) {
                    return set("timestamp", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncBlockades")
                public static class syncBlockades extends CommandRef {
                    public static final syncBlockades cmd = new syncBlockades();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncCities")
                public static class syncCities extends CommandRef {
                    public static final syncCities cmd = new syncCities();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncLootFromAttacks")
                public static class syncLootFromAttacks extends CommandRef {
                    public static final syncLootFromAttacks cmd = new syncLootFromAttacks();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncMetrics")
                public static class syncMetrics extends CommandRef {
                    public static final syncMetrics cmd = new syncMetrics();
                public syncMetrics topX(String value) {
                    return set("topX", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncNations")
                public static class syncNations extends CommandRef {
                    public static final syncNations cmd = new syncNations();
                public syncNations nations(String value) {
                    return set("nations", value);
                }

                public syncNations dirtyNations(String value) {
                    return set("dirtyNations", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncReferrals")
                public static class syncReferrals extends CommandRef {
                    public static final syncReferrals cmd = new syncReferrals();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncForum")
                public static class syncforum extends CommandRef {
                    public static final syncforum cmd = new syncforum();
                public syncforum sectionId(String value) {
                    return set("sectionId", value);
                }

                public syncforum sectionName(String value) {
                    return set("sectionName", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="syncInterviews")
                public static class syncinterviews extends CommandRef {
                    public static final syncinterviews cmd = new syncinterviews();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncTrade")
                public static class trade extends CommandRef {
                    public static final trade cmd = new trade();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncTreasures")
                public static class treasures extends CommandRef {
                    public static final treasures cmd = new treasures();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncTreaties")
                public static class treaties extends CommandRef {
                    public static final treaties cmd = new treaties();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncWarrooms")
                public static class warrooms extends CommandRef {
                    public static final warrooms cmd = new warrooms();
                public warrooms force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncWars")
                public static class wars extends CommandRef {
                    public static final wars cmd = new wars();
                public wars updateCityCounts(String value) {
                    return set("updateCityCounts", value);
                }

                }
            }
            public static class sync2{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="checkActiveConflicts")
                public static class active_conflicts extends CommandRef {
                    public static final active_conflicts cmd = new active_conflicts();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncAlliances")
                public static class alliances extends CommandRef {
                    public static final alliances cmd = new alliances();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncCityAvg")
                public static class city_avg extends CommandRef {
                    public static final city_avg cmd = new city_avg();
                public city_avg force_value(String value) {
                    return set("force_value", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncCityRefund")
                public static class city_refund extends CommandRef {
                    public static final city_refund cmd = new city_refund();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="reloadConfig")
                public static class config extends CommandRef {
                    public static final config cmd = new config();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="dumpWiki")
                public static class export_wiki extends CommandRef {
                    public static final export_wiki cmd = new export_wiki();
                public export_wiki pathRelative(String value) {
                    return set("pathRelative", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncMail")
                public static class mail extends CommandRef {
                    public static final mail cmd = new mail();
                public mail account(String value) {
                    return set("account", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="runMilitarizationAlerts")
                public static class militarization_alerts extends CommandRef {
                    public static final militarization_alerts cmd = new militarization_alerts();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncTaxes")
                public static class taxes extends CommandRef {
                    public static final taxes cmd = new taxes();
                public taxes alliance(String value) {
                    return set("alliance", value);
                }

                public taxes timestamp(String value) {
                    return set("timestamp", value);
                }

                public taxes sheet_deprecated(String value) {
                    return set("sheet_deprecated", value);
                }

                public taxes legacy_deprecated(String value) {
                    return set("legacy_deprecated", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="syncUid")
                public static class uid extends CommandRef {
                    public static final uid cmd = new uid();
                public uid all(String value) {
                    return set("all", value);
                }

                }
            }
        }
        public static class alerts{
            public static class audit{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="auditAlertOptOut")
                public static class optout extends CommandRef {
                    public static final optout cmd = new optout();

                }
            }
            public static class bank{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="bankAlertList")
                public static class list extends CommandRef {
                    public static final list cmd = new list();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="bankAlertRequiredValue")
                public static class min_value extends CommandRef {
                    public static final min_value cmd = new min_value();
                public min_value requiredValue(String value) {
                    return set("requiredValue", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="bankAlert")
                public static class subscribe extends CommandRef {
                    public static final subscribe cmd = new subscribe();
                public subscribe nation_or_alliances(String value) {
                    return set("nation_or_alliances", value);
                }

                public subscribe send_or_receive(String value) {
                    return set("send_or_receive", value);
                }

                public subscribe amount(String value) {
                    return set("amount", value);
                }

                public subscribe duration(String value) {
                    return set("duration", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="bankAlertUnsubscribe")
                public static class unsubscribe extends CommandRef {
                    public static final unsubscribe cmd = new unsubscribe();
                public unsubscribe nation_or_alliances(String value) {
                    return set("nation_or_alliances", value);
                }

                }
            }
            public static class beige{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeReminder")
                public static class beigeAlert extends CommandRef {
                    public static final beigeAlert cmd = new beigeAlert();
                public beigeAlert targets(String value) {
                    return set("targets", value);
                }

                public beigeAlert requiredLoot(String value) {
                    return set("requiredLoot", value);
                }

                public beigeAlert allowOutOfScore(String value) {
                    return set("allowOutOfScore", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertMode")
                public static class beigeAlertMode extends CommandRef {
                    public static final beigeAlertMode cmd = new beigeAlertMode();
                public beigeAlertMode mode(String value) {
                    return set("mode", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="beigeAlertOptOut")
                public static class beigeAlertOptOut extends CommandRef {
                    public static final beigeAlertOptOut cmd = new beigeAlertOptOut();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertRequiredLoot")
                public static class beigeAlertRequiredLoot extends CommandRef {
                    public static final beigeAlertRequiredLoot cmd = new beigeAlertRequiredLoot();
                public beigeAlertRequiredLoot requiredLoot(String value) {
                    return set("requiredLoot", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeAlertRequiredStatus")
                public static class beigeAlertRequiredStatus extends CommandRef {
                    public static final beigeAlertRequiredStatus cmd = new beigeAlertRequiredStatus();
                public beigeAlertRequiredStatus status(String value) {
                    return set("status", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="beigeReminders")
                public static class beigeReminders extends CommandRef {
                    public static final beigeReminders cmd = new beigeReminders();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="removeBeigeReminder")
                public static class removeBeigeReminder extends CommandRef {
                    public static final removeBeigeReminder cmd = new removeBeigeReminder();
                public removeBeigeReminder nationsToRemove(String value) {
                    return set("nationsToRemove", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="setBeigeAlertScoreLeeway")
                public static class setBeigeAlertScoreLeeway extends CommandRef {
                    public static final setBeigeAlertScoreLeeway cmd = new setBeigeAlertScoreLeeway();
                public setBeigeAlertScoreLeeway scoreLeeway(String value) {
                    return set("scoreLeeway", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="testBeigeAlertAuto")
                public static class test_auto extends CommandRef {
                    public static final test_auto cmd = new test_auto();

                }
            }
            public static class bounty{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="bountyAlertOptOut")
                public static class opt_out extends CommandRef {
                    public static final opt_out cmd = new opt_out();

                }
            }
            public static class enemy{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="enemyAlertOptOut")
                public static class optout extends CommandRef {
                    public static final optout cmd = new optout();

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="loginNotifier")
            public static class login extends CommandRef {
                public static final login cmd = new login();
            public login target(String value) {
                return set("target", value);
            }

            public login doNotRequireWar(String value) {
                return set("doNotRequireWar", value);
            }

            }
            public static class trade{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeSubs")
                public static class list extends CommandRef {
                    public static final list cmd = new list();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertDisparity")
                public static class margin extends CommandRef {
                    public static final margin cmd = new margin();
                public margin resources(String value) {
                    return set("resources", value);
                }

                public margin aboveOrBelow(String value) {
                    return set("aboveOrBelow", value);
                }

                public margin ppu(String value) {
                    return set("ppu", value);
                }

                public margin duration(String value) {
                    return set("duration", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertMistrade")
                public static class mistrade extends CommandRef {
                    public static final mistrade cmd = new mistrade();
                public mistrade resources(String value) {
                    return set("resources", value);
                }

                public mistrade aboveOrBelow(String value) {
                    return set("aboveOrBelow", value);
                }

                public mistrade ppu(String value) {
                    return set("ppu", value);
                }

                public mistrade duration(String value) {
                    return set("duration", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertNoOffer")
                public static class no_offers extends CommandRef {
                    public static final no_offers cmd = new no_offers();
                public no_offers resources(String value) {
                    return set("resources", value);
                }

                public no_offers duration(String value) {
                    return set("duration", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertAbsolute")
                public static class price extends CommandRef {
                    public static final price cmd = new price();
                public price resource(String value) {
                    return set("resource", value);
                }

                public price buyOrSell(String value) {
                    return set("buyOrSell", value);
                }

                public price aboveOrBelow(String value) {
                    return set("aboveOrBelow", value);
                }

                public price ppu(String value) {
                    return set("ppu", value);
                }

                public price duration(String value) {
                    return set("duration", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeAlertUndercut")
                public static class undercut extends CommandRef {
                    public static final undercut cmd = new undercut();
                public undercut resources(String value) {
                    return set("resources", value);
                }

                public undercut buyOrSell(String value) {
                    return set("buyOrSell", value);
                }

                public undercut duration(String value) {
                    return set("duration", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="unsubTrade")
                public static class unsubscribe extends CommandRef {
                    public static final unsubscribe cmd = new unsubscribe();
                public unsubscribe resource(String value) {
                    return set("resource", value);
                }

                }
            }
        }
        public static class alliance{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="allianceCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost nations(String value) {
                return set("nations", value);
            }

            public cost update(String value) {
                return set("update", value);
            }

            public cost includeProjects(String value) {
                return set("includeProjects", value);
            }

            public cost snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="leftAA")
            public static class departures extends CommandRef {
                public static final departures cmd = new departures();
            public departures nationOrAlliance(String value) {
                return set("nationOrAlliance", value);
            }

            public departures time(String value) {
                return set("time", value);
            }

            public departures filter(String value) {
                return set("filter", value);
            }

            public departures ignoreInactives(String value) {
                return set("ignoreInactives", value);
            }

            public departures ignoreVM(String value) {
                return set("ignoreVM", value);
            }

            public departures ignoreMembers(String value) {
                return set("ignoreMembers", value);
            }

            public departures listIds(String value) {
                return set("listIds", value);
            }

            public departures sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="editAlliance")
            public static class edit extends CommandRef {
                public static final edit cmd = new edit();
            public edit alliance(String value) {
                return set("alliance", value);
            }

            public edit attribute(String value) {
                return set("attribute", value);
            }

            public edit value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="listAllianceMembers")
            public static class listAllianceMembers extends CommandRef {
                public static final listAllianceMembers cmd = new listAllianceMembers();
            public listAllianceMembers page(String value) {
                return set("page", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="markAsOffshore")
            public static class markAsOffshore extends CommandRef {
                public static final markAsOffshore cmd = new markAsOffshore();
            public markAsOffshore offshore(String value) {
                return set("offshore", value);
            }

            public markAsOffshore parent(String value) {
                return set("parent", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="revenue")
            public static class revenue extends CommandRef {
                public static final revenue cmd = new revenue();
            public revenue nations(String value) {
                return set("nations", value);
            }

            public revenue includeUntaxable(String value) {
                return set("includeUntaxable", value);
            }

            public revenue excludeNationBonus(String value) {
                return set("excludeNationBonus", value);
            }

            public revenue rads(String value) {
                return set("rads", value);
            }

            public revenue forceAtWar(String value) {
                return set("forceAtWar", value);
            }

            public revenue forceAtPeace(String value) {
                return set("forceAtPeace", value);
            }

            public revenue includeWarCosts(String value) {
                return set("includeWarCosts", value);
            }

            public revenue snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            public static class sheets{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="AllianceSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                public sheet nations(String value) {
                    return set("nations", value);
                }

                public sheet columns(String value) {
                    return set("columns", value);
                }

                public sheet sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="stockpileSheet")
                public static class stockpileSheet extends CommandRef {
                    public static final stockpileSheet cmd = new stockpileSheet();
                public stockpileSheet nationFilter(String value) {
                    return set("nationFilter", value);
                }

                public stockpileSheet normalize(String value) {
                    return set("normalize", value);
                }

                public stockpileSheet onlyShowExcess(String value) {
                    return set("onlyShowExcess", value);
                }

                public stockpileSheet forceUpdate(String value) {
                    return set("forceUpdate", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warchestSheet")
                public static class warchestSheet extends CommandRef {
                    public static final warchestSheet cmd = new warchestSheet();
                public warchestSheet nations(String value) {
                    return set("nations", value);
                }

                public warchestSheet perCityWarchest(String value) {
                    return set("perCityWarchest", value);
                }

                public warchestSheet includeGrants(String value) {
                    return set("includeGrants", value);
                }

                public warchestSheet doNotNormalizeDeposits(String value) {
                    return set("doNotNormalizeDeposits", value);
                }

                public warchestSheet ignoreDeposits(String value) {
                    return set("ignoreDeposits", value);
                }

                public warchestSheet ignoreStockpileInExcess(String value) {
                    return set("ignoreStockpileInExcess", value);
                }

                public warchestSheet includeRevenueDays(String value) {
                    return set("includeRevenueDays", value);
                }

                public warchestSheet forceUpdate(String value) {
                    return set("forceUpdate", value);
                }

                }
            }
            public static class stats{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricAB")
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

                public allianceMetricAB attachJson(String value) {
                    return set("attachJson", value);
                }

                public allianceMetricAB attachCsv(String value) {
                    return set("attachCsv", value);
                }

                public allianceMetricAB attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceNationsSheet")
                public static class allianceNationsSheet extends CommandRef {
                    public static final allianceNationsSheet cmd = new allianceNationsSheet();
                public allianceNationsSheet nations(String value) {
                    return set("nations", value);
                }

                public allianceNationsSheet columns(String value) {
                    return set("columns", value);
                }

                public allianceNationsSheet sheet(String value) {
                    return set("sheet", value);
                }

                public allianceNationsSheet useTotal(String value) {
                    return set("useTotal", value);
                }

                public allianceNationsSheet includeInactives(String value) {
                    return set("includeInactives", value);
                }

                public allianceNationsSheet includeApplicants(String value) {
                    return set("includeApplicants", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceAttributeRanking")
                public static class attribute_ranking extends CommandRef {
                    public static final attribute_ranking cmd = new attribute_ranking();
                public attribute_ranking attribute(String value) {
                    return set("attribute", value);
                }

                public attribute_ranking alliances(String value) {
                    return set("alliances", value);
                }

                public attribute_ranking num_results(String value) {
                    return set("num_results", value);
                }

                public attribute_ranking reverseOrder(String value) {
                    return set("reverseOrder", value);
                }

                public attribute_ranking uploadFile(String value) {
                    return set("uploadFile", value);
                }

                public attribute_ranking highlight(String value) {
                    return set("highlight", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="compareStats")
                public static class coalition_metric_by_turn extends CommandRef {
                    public static final coalition_metric_by_turn cmd = new coalition_metric_by_turn();
                public coalition_metric_by_turn metric(String value) {
                    return set("metric", value);
                }

                public coalition_metric_by_turn start(String value) {
                    return set("start", value);
                }

                public coalition_metric_by_turn end(String value) {
                    return set("end", value);
                }

                public coalition_metric_by_turn coalition1(String value) {
                    return set("coalition1", value);
                }

                public coalition_metric_by_turn coalition2(String value) {
                    return set("coalition2", value);
                }

                public coalition_metric_by_turn coalition3(String value) {
                    return set("coalition3", value);
                }

                public coalition_metric_by_turn coalition4(String value) {
                    return set("coalition4", value);
                }

                public coalition_metric_by_turn coalition5(String value) {
                    return set("coalition5", value);
                }

                public coalition_metric_by_turn coalition6(String value) {
                    return set("coalition6", value);
                }

                public coalition_metric_by_turn coalition7(String value) {
                    return set("coalition7", value);
                }

                public coalition_metric_by_turn coalition8(String value) {
                    return set("coalition8", value);
                }

                public coalition_metric_by_turn coalition9(String value) {
                    return set("coalition9", value);
                }

                public coalition_metric_by_turn coalition10(String value) {
                    return set("coalition10", value);
                }

                public coalition_metric_by_turn attach_json(String value) {
                    return set("attach_json", value);
                }

                public coalition_metric_by_turn attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public coalition_metric_by_turn attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
                public static class counterStats extends CommandRef {
                    public static final counterStats cmd = new counterStats();
                public counterStats alliance(String value) {
                    return set("alliance", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceByLoot")
                public static class loot_ranking extends CommandRef {
                    public static final loot_ranking cmd = new loot_ranking();
                public loot_ranking time(String value) {
                    return set("time", value);
                }

                public loot_ranking show_total(String value) {
                    return set("show_total", value);
                }

                public loot_ranking attach_file(String value) {
                    return set("attach_file", value);
                }

                public loot_ranking min_score(String value) {
                    return set("min_score", value);
                }

                public loot_ranking max_score(String value) {
                    return set("max_score", value);
                }

                public loot_ranking highlight(String value) {
                    return set("highlight", value);
                }

                public loot_ranking num_results(String value) {
                    return set("num_results", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="listMerges")
                public static class merges extends CommandRef {
                    public static final merges cmd = new merges();
                public merges sheet(String value) {
                    return set("sheet", value);
                }

                public merges threshold(String value) {
                    return set("threshold", value);
                }

                public merges dayWindow(String value) {
                    return set("dayWindow", value);
                }

                public merges minMembers(String value) {
                    return set("minMembers", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="metric_compare_by_turn")
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

                public metric_compare_by_turn attachJson(String value) {
                    return set("attachJson", value);
                }

                public metric_compare_by_turn attachCsv(String value) {
                    return set("attachCsv", value);
                }

                public metric_compare_by_turn attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricByTurn")
                public static class metricsByTurn extends CommandRef {
                    public static final metricsByTurn cmd = new metricsByTurn();
                public metricsByTurn metric(String value) {
                    return set("metric", value);
                }

                public metricsByTurn coalition(String value) {
                    return set("coalition", value);
                }

                public metricsByTurn start(String value) {
                    return set("start", value);
                }

                public metricsByTurn end(String value) {
                    return set("end", value);
                }

                public metricsByTurn attachJson(String value) {
                    return set("attachJson", value);
                }

                public metricsByTurn attachCsv(String value) {
                    return set("attachCsv", value);
                }

                public metricsByTurn attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceStats")
                public static class metrics_by_turn extends CommandRef {
                    public static final metrics_by_turn cmd = new metrics_by_turn();
                public metrics_by_turn metrics(String value) {
                    return set("metrics", value);
                }

                public metrics_by_turn start(String value) {
                    return set("start", value);
                }

                public metrics_by_turn end(String value) {
                    return set("end", value);
                }

                public metrics_by_turn coalition(String value) {
                    return set("coalition", value);
                }

                public metrics_by_turn attach_json(String value) {
                    return set("attach_json", value);
                }

                public metrics_by_turn attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public metrics_by_turn attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="militaryRanking")
                public static class militarization extends CommandRef {
                    public static final militarization cmd = new militarization();
                public militarization nations(String value) {
                    return set("nations", value);
                }

                public militarization top_n_alliances(String value) {
                    return set("top_n_alliances", value);
                }

                public militarization sheet(String value) {
                    return set("sheet", value);
                }

                public militarization removeUntaxable(String value) {
                    return set("removeUntaxable", value);
                }

                public militarization removeInactive(String value) {
                    return set("removeInactive", value);
                }

                public militarization includeApplicants(String value) {
                    return set("includeApplicants", value);
                }

                public militarization snapshotDate(String value) {
                    return set("snapshotDate", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="militarizationTime")
                public static class militarization_time extends CommandRef {
                    public static final militarization_time cmd = new militarization_time();
                public militarization_time alliance(String value) {
                    return set("alliance", value);
                }

                public militarization_time start_time(String value) {
                    return set("start_time", value);
                }

                public militarization_time end_time(String value) {
                    return set("end_time", value);
                }

                public militarization_time attach_json(String value) {
                    return set("attach_json", value);
                }

                public militarization_time attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public militarization_time attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceRanking")
                public static class ranking extends CommandRef {
                    public static final ranking cmd = new ranking();
                public ranking metric(String value) {
                    return set("metric", value);
                }

                public ranking alliances(String value) {
                    return set("alliances", value);
                }

                public ranking reverseOrder(String value) {
                    return set("reverseOrder", value);
                }

                public ranking uploadFile(String value) {
                    return set("uploadFile", value);
                }

                public ranking num_results(String value) {
                    return set("num_results", value);
                }

                public ranking highlight(String value) {
                    return set("highlight", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceRankingTime")
                public static class rankingTime extends CommandRef {
                    public static final rankingTime cmd = new rankingTime();
                public rankingTime alliances(String value) {
                    return set("alliances", value);
                }

                public rankingTime metric(String value) {
                    return set("metric", value);
                }

                public rankingTime timeStart(String value) {
                    return set("timeStart", value);
                }

                public rankingTime timeEnd(String value) {
                    return set("timeEnd", value);
                }

                public rankingTime reverseOrder(String value) {
                    return set("reverseOrder", value);
                }

                public rankingTime uploadFile(String value) {
                    return set("uploadFile", value);
                }

                public rankingTime num_results(String value) {
                    return set("num_results", value);
                }

                public rankingTime highlight(String value) {
                    return set("highlight", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="compareTierStats")
                public static class tier_by_coalition extends CommandRef {
                    public static final tier_by_coalition cmd = new tier_by_coalition();
                public tier_by_coalition metric(String value) {
                    return set("metric", value);
                }

                public tier_by_coalition groupBy(String value) {
                    return set("groupBy", value);
                }

                public tier_by_coalition coalition1(String value) {
                    return set("coalition1", value);
                }

                public tier_by_coalition coalition2(String value) {
                    return set("coalition2", value);
                }

                public tier_by_coalition coalition3(String value) {
                    return set("coalition3", value);
                }

                public tier_by_coalition coalition4(String value) {
                    return set("coalition4", value);
                }

                public tier_by_coalition coalition5(String value) {
                    return set("coalition5", value);
                }

                public tier_by_coalition coalition6(String value) {
                    return set("coalition6", value);
                }

                public tier_by_coalition coalition7(String value) {
                    return set("coalition7", value);
                }

                public tier_by_coalition coalition8(String value) {
                    return set("coalition8", value);
                }

                public tier_by_coalition coalition9(String value) {
                    return set("coalition9", value);
                }

                public tier_by_coalition coalition10(String value) {
                    return set("coalition10", value);
                }

                public tier_by_coalition total(String value) {
                    return set("total", value);
                }

                public tier_by_coalition includeApps(String value) {
                    return set("includeApps", value);
                }

                public tier_by_coalition includeVm(String value) {
                    return set("includeVm", value);
                }

                public tier_by_coalition includeInactive(String value) {
                    return set("includeInactive", value);
                }

                public tier_by_coalition snapshotDate(String value) {
                    return set("snapshotDate", value);
                }

                public tier_by_coalition attach_json(String value) {
                    return set("attach_json", value);
                }

                public tier_by_coalition attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public tier_by_coalition attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="stockpile")
            public static class stockpile extends CommandRef {
                public static final stockpile cmd = new stockpile();
            public stockpile nationOrAlliance(String value) {
                return set("nationOrAlliance", value);
            }

            }
            public static class treaty{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="approveTreaty")
                public static class approve extends CommandRef {
                    public static final approve cmd = new approve();
                public approve senders(String value) {
                    return set("senders", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="cancelTreaty")
                public static class cancel extends CommandRef {
                    public static final cancel cmd = new cancel();
                public cancel senders(String value) {
                    return set("senders", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="treaties")
                public static class list extends CommandRef {
                    public static final list cmd = new list();
                public list alliances(String value) {
                    return set("alliances", value);
                }

                public list treatyFilter(String value) {
                    return set("treatyFilter", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="sendTreaty")
                public static class send extends CommandRef {
                    public static final send cmd = new send();
                public send receiver(String value) {
                    return set("receiver", value);
                }

                public send type(String value) {
                    return set("type", value);
                }

                public send days(String value) {
                    return set("days", value);
                }

                public send message(String value) {
                    return set("message", value);
                }

                }
            }
        }
        public static class announcement{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="archiveAnnouncement")
            public static class archive extends CommandRef {
                public static final archive cmd = new archive();
            public archive announcementId(String value) {
                return set("announcementId", value);
            }

            public archive archive(String value) {
                return set("archive", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="announce")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create sendTo(String value) {
                return set("sendTo", value);
            }

            public create subject(String value) {
                return set("subject", value);
            }

            public create announcement(String value) {
                return set("announcement", value);
            }

            public create replacements(String value) {
                return set("replacements", value);
            }

            public create channel(String value) {
                return set("channel", value);
            }

            public create bottomText(String value) {
                return set("bottomText", value);
            }

            public create requiredVariation(String value) {
                return set("requiredVariation", value);
            }

            public create requiredDepth(String value) {
                return set("requiredDepth", value);
            }

            public create seed(String value) {
                return set("seed", value);
            }

            public create sendMail(String value) {
                return set("sendMail", value);
            }

            public create sendDM(String value) {
                return set("sendDM", value);
            }

            public create force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="announceDocument")
            public static class document extends CommandRef {
                public static final document cmd = new document();
            public document original(String value) {
                return set("original", value);
            }

            public document sendTo(String value) {
                return set("sendTo", value);
            }

            public document replacements(String value) {
                return set("replacements", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="find_announcement")
            public static class find extends CommandRef {
                public static final find cmd = new find();
            public find announcementId(String value) {
                return set("announcementId", value);
            }

            public find message(String value) {
                return set("message", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="find_invite")
            public static class find_invite extends CommandRef {
                public static final find_invite cmd = new find_invite();
            public find_invite invite(String value) {
                return set("invite", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="sendInvite")
            public static class invite extends CommandRef {
                public static final invite cmd = new invite();
            public invite message(String value) {
                return set("message", value);
            }

            public invite inviteTo(String value) {
                return set("inviteTo", value);
            }

            public invite sendTo(String value) {
                return set("sendTo", value);
            }

            public invite expire(String value) {
                return set("expire", value);
            }

            public invite maxUsesEach(String value) {
                return set("maxUsesEach", value);
            }

            public invite sendDM(String value) {
                return set("sendDM", value);
            }

            public invite sendMail(String value) {
                return set("sendMail", value);
            }

            public invite allowCreation(String value) {
                return set("allowCreation", value);
            }

            public invite force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="ocr")
            public static class ocr extends CommandRef {
                public static final ocr cmd = new ocr();
            public ocr discordImageUrl(String value) {
                return set("discordImageUrl", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="readAnnouncement")
            public static class read extends CommandRef {
                public static final read cmd = new read();
            public read ann_id(String value) {
                return set("ann_id", value);
            }

            public read markRead(String value) {
                return set("markRead", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="viewAnnouncement")
            public static class view extends CommandRef {
                public static final view cmd = new view();
            public view ann_id(String value) {
                return set("ann_id", value);
            }

            public view nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="addWatermark")
            public static class watermark extends CommandRef {
                public static final watermark cmd = new watermark();
            public watermark imageUrl(String value) {
                return set("imageUrl", value);
            }

            public watermark watermarkText(String value) {
                return set("watermarkText", value);
            }

            public watermark color(String value) {
                return set("color", value);
            }

            public watermark opacity(String value) {
                return set("opacity", value);
            }

            public watermark font(String value) {
                return set("font", value);
            }

            public watermark repeat(String value) {
                return set("repeat", value);
            }

            }
        }
        public static class audit{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="hasNotBoughtSpies")
            public static class hasNotBoughtSpies extends CommandRef {
                public static final hasNotBoughtSpies cmd = new hasNotBoughtSpies();
            public hasNotBoughtSpies nations(String value) {
                return set("nations", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="checkCities")
            public static class run extends CommandRef {
                public static final run cmd = new run();
            public run nationList(String value) {
                return set("nationList", value);
            }

            public run audits(String value) {
                return set("audits", value);
            }

            public run pingUser(String value) {
                return set("pingUser", value);
            }

            public run mailResults(String value) {
                return set("mailResults", value);
            }

            public run postInInterviewChannels(String value) {
                return set("postInInterviewChannels", value);
            }

            public run skipUpdate(String value) {
                return set("skipUpdate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="auditSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
            public sheet nations(String value) {
                return set("nations", value);
            }

            public sheet includeAudits(String value) {
                return set("includeAudits", value);
            }

            public sheet excludeAudits(String value) {
                return set("excludeAudits", value);
            }

            public sheet forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            public sheet verbose(String value) {
                return set("verbose", value);
            }

            public sheet allowNonAlliance(String value) {
                return set("allowNonAlliance", value);
            }

            public sheet skipDiscordAudits(String value) {
                return set("skipDiscordAudits", value);
            }

            public sheet skipApiAudits(String value) {
                return set("skipApiAudits", value);
            }

            public sheet warningOrHigher(String value) {
                return set("warningOrHigher", value);
            }

            public sheet sheet(String value) {
                return set("sheet", value);
            }

            }
        }
        public static class bank{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="depositResources")
            public static class deposit extends CommandRef {
                public static final deposit cmd = new deposit();
            public deposit nations(String value) {
                return set("nations", value);
            }

            public deposit sheetAmounts(String value) {
                return set("sheetAmounts", value);
            }

            public deposit amount(String value) {
                return set("amount", value);
            }

            public deposit rawsDays(String value) {
                return set("rawsDays", value);
            }

            public deposit raws_days_by_resource(String value) {
                return set("raws_days_by_resource", value);
            }

            public deposit rawsNoDailyCash(String value) {
                return set("rawsNoDailyCash", value);
            }

            public deposit rawsNoCash(String value) {
                return set("rawsNoCash", value);
            }

            public deposit keepWarchestFactor(String value) {
                return set("keepWarchestFactor", value);
            }

            public deposit keepPerCity(String value) {
                return set("keepPerCity", value);
            }

            public deposit keepTotal(String value) {
                return set("keepTotal", value);
            }

            public deposit unitResources(String value) {
                return set("unitResources", value);
            }

            public deposit units_no_cash(String value) {
                return set("units_no_cash", value);
            }

            public deposit note(String value) {
                return set("note", value);
            }

            public deposit customMessage(String value) {
                return set("customMessage", value);
            }

            public deposit mailResults(String value) {
                return set("mailResults", value);
            }

            public deposit dm(String value) {
                return set("dm", value);
            }

            public deposit useApi(String value) {
                return set("useApi", value);
            }

            public deposit force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands.class,method="importTransactions")
            public static class import_transfers extends CommandRef {
                public static final import_transfers cmd = new import_transfers();
            public import_transfers server(String value) {
                return set("server", value);
            }

            public import_transfers nations(String value) {
                return set("nations", value);
            }

            public import_transfers force(String value) {
                return set("force", value);
            }

            }
            public static class limits{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setTransferLimit")
                public static class setTransferLimit extends CommandRef {
                    public static final setTransferLimit cmd = new setTransferLimit();
                public setTransferLimit nations(String value) {
                    return set("nations", value);
                }

                public setTransferLimit limit(String value) {
                    return set("limit", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transactions")
            public static class records extends CommandRef {
                public static final records cmd = new records();
            public records nationOrAllianceOrGuild(String value) {
                return set("nationOrAllianceOrGuild", value);
            }

            public records timeframe(String value) {
                return set("timeframe", value);
            }

            public records useTaxBase(String value) {
                return set("useTaxBase", value);
            }

            public records useOffset(String value) {
                return set("useOffset", value);
            }

            public records sheet(String value) {
                return set("sheet", value);
            }

            public records onlyOffshoreTransfers(String value) {
                return set("onlyOffshoreTransfers", value);
            }

            }
            public static class stats{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="inflows")
                public static class inflows extends CommandRef {
                    public static final inflows cmd = new inflows();
                public inflows nationOrAlliances(String value) {
                    return set("nationOrAlliances", value);
                }

                public inflows cutoffMs(String value) {
                    return set("cutoffMs", value);
                }

                public inflows hideInflows(String value) {
                    return set("hideInflows", value);
                }

                public inflows hideOutflows(String value) {
                    return set("hideOutflows", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="weeklyInterest")
                public static class weeklyInterest extends CommandRef {
                    public static final weeklyInterest cmd = new weeklyInterest();
                public weeklyInterest amount(String value) {
                    return set("amount", value);
                }

                public weeklyInterest pct(String value) {
                    return set("pct", value);
                }

                public weeklyInterest weeks(String value) {
                    return set("weeks", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="unlockTransfers")
            public static class unlockTransfers extends CommandRef {
                public static final unlockTransfers cmd = new unlockTransfers();
            public unlockTransfers nationOrAllianceOrGuild(String value) {
                return set("nationOrAllianceOrGuild", value);
            }

            public unlockTransfers unlockAll(String value) {
                return set("unlockAll", value);
            }

            }
        }
        public static class baseball{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseBallChallengeInflow")
            public static class baseBallChallengeInflow extends CommandRef {
                public static final baseBallChallengeInflow cmd = new baseBallChallengeInflow();
            public baseBallChallengeInflow nationId(String value) {
                return set("nationId", value);
            }

            public baseBallChallengeInflow dateSince(String value) {
                return set("dateSince", value);
            }

            public baseBallChallengeInflow uploadFile(String value) {
                return set("uploadFile", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballChallengeEarningsRanking")
            public static class baseballChallengeEarningsRanking extends CommandRef {
                public static final baseballChallengeEarningsRanking cmd = new baseballChallengeEarningsRanking();
            public baseballChallengeEarningsRanking uploadFile(String value) {
                return set("uploadFile", value);
            }

            public baseballChallengeEarningsRanking byAlliance(String value) {
                return set("byAlliance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballChallengeRanking")
            public static class baseballChallengeRanking extends CommandRef {
                public static final baseballChallengeRanking cmd = new baseballChallengeRanking();
            public baseballChallengeRanking uploadFile(String value) {
                return set("uploadFile", value);
            }

            public baseballChallengeRanking byAlliance(String value) {
                return set("byAlliance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballEarningsRanking")
            public static class baseballEarningsRanking extends CommandRef {
                public static final baseballEarningsRanking cmd = new baseballEarningsRanking();
            public baseballEarningsRanking date(String value) {
                return set("date", value);
            }

            public baseballEarningsRanking uploadFile(String value) {
                return set("uploadFile", value);
            }

            public baseballEarningsRanking byAlliance(String value) {
                return set("byAlliance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="baseballRanking")
            public static class baseballRanking extends CommandRef {
                public static final baseballRanking cmd = new baseballRanking();
            public baseballRanking date(String value) {
                return set("date", value);
            }

            public baseballRanking uploadFile(String value) {
                return set("uploadFile", value);
            }

            public baseballRanking byAlliance(String value) {
                return set("byAlliance", value);
            }

            }
        }
        public static class build{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="add")
            public static class add extends CommandRef {
                public static final add cmd = new add();
            public add category(String value) {
                return set("category", value);
            }

            public add ranges(String value) {
                return set("ranges", value);
            }

            public add build(String value) {
                return set("build", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="assign")
            public static class assign extends CommandRef {
                public static final assign cmd = new assign();
            public assign category(String value) {
                return set("category", value);
            }

            public assign nation(String value) {
                return set("nation", value);
            }

            public assign cities(String value) {
                return set("cities", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="delete")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
            public delete category(String value) {
                return set("category", value);
            }

            public delete minCities(String value) {
                return set("minCities", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="get")
            public static class get extends CommandRef {
                public static final get cmd = new get();
            public get nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BuildCommands.class,method="listall")
            public static class listall extends CommandRef {
                public static final listall cmd = new listall();

            }
        }
        public static class building{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="buildingCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost build(String value) {
                return set("build", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord._test.command.CustomCommands.class,method="buyInfra")
        public static class buyInfra extends CommandRef {
            public static final buyInfra cmd = new buyInfra();
        public buyInfra upTo(String value) {
            return set("upTo", value);
        }

        public buyInfra force(String value) {
            return set("force", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord._test.command.CustomCommands.class,method="buyLand")
        public static class buyLand extends CommandRef {
            public static final buyLand cmd = new buyLand();
        public buyLand upTo(String value) {
            return set("upTo", value);
        }

        public buyLand force(String value) {
            return set("force", value);
        }

        }
        public static class channel{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="channelMembers")
            public static class channelMembers extends CommandRef {
                public static final channelMembers cmd = new channelMembers();
            public channelMembers channel(String value) {
                return set("channel", value);
            }

            }
            public static class close{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="close")
                public static class current extends CommandRef {
                    public static final current cmd = new current();
                public current forceDelete(String value) {
                    return set("forceDelete", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="closeInactiveChannels")
                public static class inactive extends CommandRef {
                    public static final inactive cmd = new inactive();
                public inactive category(String value) {
                    return set("category", value);
                }

                public inactive age(String value) {
                    return set("age", value);
                }

                public inactive force(String value) {
                    return set("force", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="channelCount")
            public static class count extends CommandRef {
                public static final count cmd = new count();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channel")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create channelName(String value) {
                return set("channelName", value);
            }

            public create category(String value) {
                return set("category", value);
            }

            public create copypasta(String value) {
                return set("copypasta", value);
            }

            public create addInternalAffairsRole(String value) {
                return set("addInternalAffairsRole", value);
            }

            public create addMilcom(String value) {
                return set("addMilcom", value);
            }

            public create addForeignAffairs(String value) {
                return set("addForeignAffairs", value);
            }

            public create addEcon(String value) {
                return set("addEcon", value);
            }

            public create pingRoles(String value) {
                return set("pingRoles", value);
            }

            public create pingAuthor(String value) {
                return set("pingAuthor", value);
            }

            }
            public static class delete{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="deleteChannel")
                public static class current extends CommandRef {
                    public static final current cmd = new current();
                public current channel(String value) {
                    return set("channel", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="deleteAllInaccessibleChannels")
                public static class inaccessible extends CommandRef {
                    public static final inaccessible cmd = new inaccessible();
                public inaccessible force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="debugPurgeChannels")
                public static class inactive extends CommandRef {
                    public static final inactive cmd = new inactive();
                public inactive category(String value) {
                    return set("category", value);
                }

                public inactive cutoff(String value) {
                    return set("cutoff", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="memberChannels")
            public static class memberChannels extends CommandRef {
                public static final memberChannels cmd = new memberChannels();
            public memberChannels member(String value) {
                return set("member", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="allChannelMembers")
            public static class members extends CommandRef {
                public static final members cmd = new members();

            }
            public static class move{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelDown")
                public static class Down extends CommandRef {
                    public static final Down cmd = new Down();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelUp")
                public static class Up extends CommandRef {
                    public static final Up cmd = new Up();

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="open")
            public static class open extends CommandRef {
                public static final open cmd = new open();
            public open category(String value) {
                return set("category", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelPermissions")
            public static class permissions extends CommandRef {
                public static final permissions cmd = new permissions();
            public permissions channel(String value) {
                return set("channel", value);
            }

            public permissions nations(String value) {
                return set("nations", value);
            }

            public permissions permission(String value) {
                return set("permission", value);
            }

            public permissions negate(String value) {
                return set("negate", value);
            }

            public permissions removeOthers(String value) {
                return set("removeOthers", value);
            }

            public permissions listChanges(String value) {
                return set("listChanges", value);
            }

            public permissions pingAddedUsers(String value) {
                return set("pingAddedUsers", value);
            }

            }
            public static class rename{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="emojifyChannels")
                public static class bulk extends CommandRef {
                    public static final bulk cmd = new bulk();
                public bulk sheet(String value) {
                    return set("sheet", value);
                }

                public bulk excludeCategories(String value) {
                    return set("excludeCategories", value);
                }

                public bulk includeCategories(String value) {
                    return set("includeCategories", value);
                }

                public bulk force(String value) {
                    return set("force", value);
                }

                public bulk popCultureQuotes(String value) {
                    return set("popCultureQuotes", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="channelCategory")
            public static class setCategory extends CommandRef {
                public static final setCategory cmd = new setCategory();
            public setCategory category(String value) {
                return set("category", value);
            }

            }
            public static class sort{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="sortChannelsName")
                public static class category_filter extends CommandRef {
                    public static final category_filter cmd = new category_filter();
                public category_filter from(String value) {
                    return set("from", value);
                }

                public category_filter categoryPrefix(String value) {
                    return set("categoryPrefix", value);
                }

                public category_filter filter(String value) {
                    return set("filter", value);
                }

                public category_filter warn_on_filter_fail(String value) {
                    return set("warn_on_filter_fail", value);
                }

                public category_filter force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="sortChannelsSheetRules")
                public static class category_rule_sheet extends CommandRef {
                    public static final category_rule_sheet cmd = new category_rule_sheet();
                public category_rule_sheet sheet(String value) {
                    return set("sheet", value);
                }

                public category_rule_sheet filter(String value) {
                    return set("filter", value);
                }

                public category_rule_sheet warn_on_filter_fail(String value) {
                    return set("warn_on_filter_fail", value);
                }

                public category_rule_sheet force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="sortChannelsSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                public sheet from(String value) {
                    return set("from", value);
                }

                public sheet sheet(String value) {
                    return set("sheet", value);
                }

                public sheet filter(String value) {
                    return set("filter", value);
                }

                public sheet warn_on_filter_fail(String value) {
                    return set("warn_on_filter_fail", value);
                }

                public sheet force(String value) {
                    return set("force", value);
                }

                }
            }
        }
        public static class chat{
            public static class conversion{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="generate_factsheet")
                public static class add_document extends CommandRef {
                    public static final add_document cmd = new add_document();
                public add_document googleDocumentUrl(String value) {
                    return set("googleDocumentUrl", value);
                }

                public add_document document_name(String value) {
                    return set("document_name", value);
                }

                public add_document force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="deleteConversion")
                public static class delete extends CommandRef {
                    public static final delete cmd = new delete();
                public delete source(String value) {
                    return set("source", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="showConverting")
                public static class list extends CommandRef {
                    public static final list cmd = new list();
                public list showRoot(String value) {
                    return set("showRoot", value);
                }

                public list showOtherGuilds(String value) {
                    return set("showOtherGuilds", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="pauseConversion")
                public static class pause extends CommandRef {
                    public static final pause cmd = new pause();
                public pause source(String value) {
                    return set("source", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="resumeConversion")
                public static class resume extends CommandRef {
                    public static final resume cmd = new resume();
                public resume source(String value) {
                    return set("source", value);
                }

                }
            }
            public static class dataset{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="delete_document")
                public static class delete extends CommandRef {
                    public static final delete cmd = new delete();
                public delete source(String value) {
                    return set("source", value);
                }

                public delete force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="save_embeddings")
                public static class import_sheet extends CommandRef {
                    public static final import_sheet cmd = new import_sheet();
                public import_sheet sheet(String value) {
                    return set("sheet", value);
                }

                public import_sheet document_name(String value) {
                    return set("document_name", value);
                }

                public import_sheet force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="list_documents")
                public static class list extends CommandRef {
                    public static final list cmd = new list();
                public list listRoot(String value) {
                    return set("listRoot", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="embeddingSelect")
                public static class select extends CommandRef {
                    public static final select cmd = new select();
                public select excludeTypes(String value) {
                    return set("excludeTypes", value);
                }

                public select includeWikiCategories(String value) {
                    return set("includeWikiCategories", value);
                }

                public select excludeWikiCategories(String value) {
                    return set("excludeWikiCategories", value);
                }

                public select excludeSources(String value) {
                    return set("excludeSources", value);
                }

                public select addSources(String value) {
                    return set("addSources", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="view_document")
                public static class view extends CommandRef {
                    public static final view cmd = new view();
                public view source(String value) {
                    return set("source", value);
                }

                public view getAnswers(String value) {
                    return set("getAnswers", value);
                }

                public view sheet(String value) {
                    return set("sheet", value);
                }

                }
            }
            public static class providers{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="chatProviderConfigure")
                public static class configure extends CommandRef {
                    public static final configure cmd = new configure();
                public configure provider(String value) {
                    return set("provider", value);
                }

                public configure options(String value) {
                    return set("options", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="listChatProviders")
                public static class list extends CommandRef {
                    public static final list cmd = new list();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="chatPause")
                public static class pause extends CommandRef {
                    public static final pause cmd = new pause();
                public pause provider(String value) {
                    return set("provider", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="chatResume")
                public static class resume extends CommandRef {
                    public static final resume cmd = new resume();
                public resume provider(String value) {
                    return set("provider", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="setChatProviders")
                public static class set extends CommandRef {
                    public static final set cmd = new set();
                public set providerTypes(String value) {
                    return set("providerTypes", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="unban")
            public static class unban extends CommandRef {
                public static final unban cmd = new unban();
            public unban nation(String value) {
                return set("nation", value);
            }

            public unban force(String value) {
                return set("force", value);
            }

            }
        }
        public static class city{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="CityCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost currentCity(String value) {
                return set("currentCity", value);
            }

            public cost maxCity(String value) {
                return set("maxCity", value);
            }

            public cost manifestDestiny(String value) {
                return set("manifestDestiny", value);
            }

            public cost governmentSupportAgency(String value) {
                return set("governmentSupportAgency", value);
            }

            public cost domestic_affairs(String value) {
                return set("domestic_affairs", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="optimalBuild")
            public static class optimalBuild extends CommandRef {
                public static final optimalBuild cmd = new optimalBuild();
            public optimalBuild build(String value) {
                return set("build", value);
            }

            public optimalBuild days(String value) {
                return set("days", value);
            }

            public optimalBuild buildMMR(String value) {
                return set("buildMMR", value);
            }

            public optimalBuild age(String value) {
                return set("age", value);
            }

            public optimalBuild infra(String value) {
                return set("infra", value);
            }

            public optimalBuild baseReducedInfra(String value) {
                return set("baseReducedInfra", value);
            }

            public optimalBuild land(String value) {
                return set("land", value);
            }

            public optimalBuild radiation(String value) {
                return set("radiation", value);
            }

            public optimalBuild diseaseCap(String value) {
                return set("diseaseCap", value);
            }

            public optimalBuild crimeCap(String value) {
                return set("crimeCap", value);
            }

            public optimalBuild minPopulation(String value) {
                return set("minPopulation", value);
            }

            public optimalBuild useRawsForManu(String value) {
                return set("useRawsForManu", value);
            }

            public optimalBuild moneyPositive(String value) {
                return set("moneyPositive", value);
            }

            public optimalBuild nationalProjects(String value) {
                return set("nationalProjects", value);
            }

            public optimalBuild geographicContinent(String value) {
                return set("geographicContinent", value);
            }

            public optimalBuild taxRate(String value) {
                return set("taxRate", value);
            }

            public optimalBuild writePlaintext(String value) {
                return set("writePlaintext", value);
            }

            public optimalBuild calc_time(String value) {
                return set("calc_time", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="cityRevenue")
            public static class revenue extends CommandRef {
                public static final revenue cmd = new revenue();
            public revenue city(String value) {
                return set("city", value);
            }

            public revenue nation(String value) {
                return set("nation", value);
            }

            public revenue excludeNationBonus(String value) {
                return set("excludeNationBonus", value);
            }

            public revenue land(String value) {
                return set("land", value);
            }

            public revenue age(String value) {
                return set("age", value);
            }

            }
        }
        public static class coalition{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="addCoalition")
            public static class add extends CommandRef {
                public static final add cmd = new add();
            public add alliances(String value) {
                return set("alliances", value);
            }

            public add coalitionName(String value) {
                return set("coalitionName", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="createCoalition")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create alliances(String value) {
                return set("alliances", value);
            }

            public create coalitionName(String value) {
                return set("coalitionName", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="deleteCoalition")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
            public delete coalitionName(String value) {
                return set("coalitionName", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="generateSphere")
            public static class generate extends CommandRef {
                public static final generate cmd = new generate();
            public generate coalition(String value) {
                return set("coalition", value);
            }

            public generate rootAlliance(String value) {
                return set("rootAlliance", value);
            }

            public generate topX(String value) {
                return set("topX", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="listCoalition")
            public static class list extends CommandRef {
                public static final list cmd = new list();
            public list filter(String value) {
                return set("filter", value);
            }

            public list listIds(String value) {
                return set("listIds", value);
            }

            public list ignoreDeleted(String value) {
                return set("ignoreDeleted", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="removeCoalition")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
            public remove alliances(String value) {
                return set("alliances", value);
            }

            public remove coalitionName(String value) {
                return set("coalitionName", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="generateCoalitionSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
            public sheet sheet(String value) {
                return set("sheet", value);
            }

            }
        }
        public static class color{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="calculateColorRevenue")
            public static class revenue extends CommandRef {
                public static final revenue cmd = new revenue();
            public revenue set_aqua(String value) {
                return set("set_aqua", value);
            }

            public revenue set_black(String value) {
                return set("set_black", value);
            }

            public revenue set_blue(String value) {
                return set("set_blue", value);
            }

            public revenue set_brown(String value) {
                return set("set_brown", value);
            }

            public revenue set_green(String value) {
                return set("set_green", value);
            }

            public revenue set_lime(String value) {
                return set("set_lime", value);
            }

            public revenue set_maroon(String value) {
                return set("set_maroon", value);
            }

            public revenue set_olive(String value) {
                return set("set_olive", value);
            }

            public revenue set_orange(String value) {
                return set("set_orange", value);
            }

            public revenue set_pink(String value) {
                return set("set_pink", value);
            }

            public revenue set_purple(String value) {
                return set("set_purple", value);
            }

            public revenue set_red(String value) {
                return set("set_red", value);
            }

            public revenue set_white(String value) {
                return set("set_white", value);
            }

            public revenue set_yellow(String value) {
                return set("set_yellow", value);
            }

            public revenue set_gray_or_beige(String value) {
                return set("set_gray_or_beige", value);
            }

            }
        }
        public static class conflict{
            public static class alliance{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="addCoalition")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                public add conflict(String value) {
                    return set("conflict", value);
                }

                public add alliances(String value) {
                    return set("alliances", value);
                }

                public add isCoalition1(String value) {
                    return set("isCoalition1", value);
                }

                public add isCoalition2(String value) {
                    return set("isCoalition2", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="removeCoalition")
                public static class remove extends CommandRef {
                    public static final remove cmd = new remove();
                public remove conflict(String value) {
                    return set("conflict", value);
                }

                public remove alliances(String value) {
                    return set("alliances", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="addConflict")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create category(String value) {
                return set("category", value);
            }

            public create coalition1(String value) {
                return set("coalition1", value);
            }

            public create coalition2(String value) {
                return set("coalition2", value);
            }

            public create start(String value) {
                return set("start", value);
            }

            public create end(String value) {
                return set("end", value);
            }

            public create conflictName(String value) {
                return set("conflictName", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.VirtualConflictCommands.class,method="createTemporary")
            public static class create_temp extends CommandRef {
                public static final create_temp cmd = new create_temp();
            public create_temp col1(String value) {
                return set("col1", value);
            }

            public create_temp col2(String value) {
                return set("col2", value);
            }

            public create_temp start(String value) {
                return set("start", value);
            }

            public create_temp end(String value) {
                return set("end", value);
            }

            public create_temp includeGraphs(String value) {
                return set("includeGraphs", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="deleteConflict")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
            public delete conflict(String value) {
                return set("conflict", value);
            }

            public delete force(String value) {
                return set("force", value);
            }

            }
            public static class edit{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="addAnnouncement")
                public static class add_forum_post extends CommandRef {
                    public static final add_forum_post cmd = new add_forum_post();
                public add_forum_post conflict(String value) {
                    return set("conflict", value);
                }

                public add_forum_post url(String value) {
                    return set("url", value);
                }

                public add_forum_post desc(String value) {
                    return set("desc", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="addManualWars")
                public static class add_none_war extends CommandRef {
                    public static final add_none_war cmd = new add_none_war();
                public add_none_war conflict(String value) {
                    return set("conflict", value);
                }

                public add_none_war nation(String value) {
                    return set("nation", value);
                }

                public add_none_war mark_as_alliance(String value) {
                    return set("mark_as_alliance", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setCB")
                public static class casus_belli extends CommandRef {
                    public static final casus_belli cmd = new casus_belli();
                public casus_belli conflict(String value) {
                    return set("conflict", value);
                }

                public casus_belli casus_belli(String value) {
                    return set("casus_belli", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setCategory")
                public static class category extends CommandRef {
                    public static final category cmd = new category();
                public category conflict(String value) {
                    return set("conflict", value);
                }

                public category category(String value) {
                    return set("category", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setConflictEnd")
                public static class end extends CommandRef {
                    public static final end cmd = new end();
                public end conflict(String value) {
                    return set("conflict", value);
                }

                public end time(String value) {
                    return set("time", value);
                }

                public end alliance(String value) {
                    return set("alliance", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setConflictName")
                public static class rename extends CommandRef {
                    public static final rename cmd = new rename();
                public rename conflict(String value) {
                    return set("conflict", value);
                }

                public rename name(String value) {
                    return set("name", value);
                }

                public rename isCoalition1(String value) {
                    return set("isCoalition1", value);
                }

                public rename isCoalition2(String value) {
                    return set("isCoalition2", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setConflictStart")
                public static class start extends CommandRef {
                    public static final start cmd = new start();
                public start conflict(String value) {
                    return set("conflict", value);
                }

                public start time(String value) {
                    return set("time", value);
                }

                public start alliance(String value) {
                    return set("alliance", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setStatus")
                public static class status extends CommandRef {
                    public static final status cmd = new status();
                public status conflict(String value) {
                    return set("conflict", value);
                }

                public status status(String value) {
                    return set("status", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="setWiki")
                public static class wiki extends CommandRef {
                    public static final wiki cmd = new wiki();
                public wiki conflict(String value) {
                    return set("conflict", value);
                }

                public wiki url(String value) {
                    return set("url", value);
                }

                }
            }
            public static class featured{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="featureConflicts")
                public static class add_rule extends CommandRef {
                    public static final add_rule cmd = new add_rule();
                public add_rule conflicts(String value) {
                    return set("conflicts", value);
                }

                public add_rule guild(String value) {
                    return set("guild", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="listFeaturedRuleset")
                public static class list_rules extends CommandRef {
                    public static final list_rules cmd = new list_rules();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="removeFeature")
                public static class remove_rule extends CommandRef {
                    public static final remove_rule cmd = new remove_rule();
                public remove_rule conflicts(String value) {
                    return set("conflicts", value);
                }

                public remove_rule guild(String value) {
                    return set("guild", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="info")
            public static class info extends CommandRef {
                public static final info cmd = new info();
            public info conflict(String value) {
                return set("conflict", value);
            }

            public info showParticipants(String value) {
                return set("showParticipants", value);
            }

            public info hideDeleted(String value) {
                return set("hideDeleted", value);
            }

            public info showIds(String value) {
                return set("showIds", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="listConflicts")
            public static class list extends CommandRef {
                public static final list cmd = new list();
            public list includeInactive(String value) {
                return set("includeInactive", value);
            }

            }
            public static class purge{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="purgeFeatured")
                public static class featured extends CommandRef {
                    public static final featured cmd = new featured();
                public featured olderThan(String value) {
                    return set("olderThan", value);
                }

                public featured force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="purgeTemporaryConflicts")
                public static class user_generated extends CommandRef {
                    public static final user_generated cmd = new user_generated();
                public user_generated olderThan(String value) {
                    return set("olderThan", value);
                }

                public user_generated force(String value) {
                    return set("force", value);
                }

                }
            }
            public static class sync{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importAllianceNames")
                public static class alliance_names extends CommandRef {
                    public static final alliance_names cmd = new alliance_names();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importCtowned")
                public static class ctowned extends CommandRef {
                    public static final ctowned cmd = new ctowned();
                public ctowned conflictName(String value) {
                    return set("conflictName", value);
                }

                public ctowned useCache(String value) {
                    return set("useCache", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importExternal")
                public static class db_file extends CommandRef {
                    public static final db_file cmd = new db_file();
                public db_file fileLocation(String value) {
                    return set("fileLocation", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importConflictData")
                public static class multiple_sources extends CommandRef {
                    public static final multiple_sources cmd = new multiple_sources();
                public multiple_sources ctowned(String value) {
                    return set("ctowned", value);
                }

                public multiple_sources graphData(String value) {
                    return set("graphData", value);
                }

                public multiple_sources allianceNames(String value) {
                    return set("allianceNames", value);
                }

                public multiple_sources wiki(String value) {
                    return set("wiki", value);
                }

                public multiple_sources all(String value) {
                    return set("all", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="recalculateGraphs")
                public static class recalculate_graphs extends CommandRef {
                    public static final recalculate_graphs cmd = new recalculate_graphs();
                public recalculate_graphs conflicts(String value) {
                    return set("conflicts", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="recalculateTables")
                public static class recalculate_tables extends CommandRef {
                    public static final recalculate_tables cmd = new recalculate_tables();
                public recalculate_tables conflicts(String value) {
                    return set("conflicts", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="syncConflictData")
                public static class website extends CommandRef {
                    public static final website cmd = new website();
                public website conflicts(String value) {
                    return set("conflicts", value);
                }

                public website upload_graph(String value) {
                    return set("upload_graph", value);
                }

                public website reinitialize_wars(String value) {
                    return set("reinitialize_wars", value);
                }

                public website reinitialize_graphs(String value) {
                    return set("reinitialize_graphs", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importWikiAll")
                public static class wiki_all extends CommandRef {
                    public static final wiki_all cmd = new wiki_all();
                public wiki_all useCache(String value) {
                    return set("useCache", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ConflictCommands.class,method="importWikiPage")
                public static class wiki_page extends CommandRef {
                    public static final wiki_page cmd = new wiki_page();
                public wiki_page name(String value) {
                    return set("name", value);
                }

                public wiki_page url(String value) {
                    return set("url", value);
                }

                public wiki_page useCache(String value) {
                    return set("useCache", value);
                }

                public wiki_page skipPushToSite(String value) {
                    return set("skipPushToSite", value);
                }

                }
            }
        }
        public static class continent{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="continent")
            public static class info extends CommandRef {
                public static final info cmd = new info();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="radiation")
            public static class radiation extends CommandRef {
                public static final radiation cmd = new radiation();

            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="copyPasta")
        public static class copyPasta extends CommandRef {
            public static final copyPasta cmd = new copyPasta();
        public copyPasta key(String value) {
            return set("key", value);
        }

        public copyPasta message(String value) {
            return set("message", value);
        }

        public copyPasta requiredRolesAny(String value) {
            return set("requiredRolesAny", value);
        }

        public copyPasta formatNation(String value) {
            return set("formatNation", value);
        }

        }
        public static class credentials{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="addApiKey")
            public static class addApiKey extends CommandRef {
                public static final addApiKey cmd = new addApiKey();
            public addApiKey apiKey(String value) {
                return set("apiKey", value);
            }

            public addApiKey verifiedBotKey(String value) {
                return set("verifiedBotKey", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="login")
            public static class login extends CommandRef {
                public static final login cmd = new login();
            public login username(String value) {
                return set("username", value);
            }

            public login password(String value) {
                return set("password", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="logout")
            public static class logout extends CommandRef {
                public static final logout cmd = new logout();

            }
        }
        public static class deposits{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="addBalance")
            public static class add extends CommandRef {
                public static final add cmd = new add();
            public add accounts(String value) {
                return set("accounts", value);
            }

            public add amount(String value) {
                return set("amount", value);
            }

            public add note(String value) {
                return set("note", value);
            }

            public add force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="addBalanceSheet")
            public static class addSheet extends CommandRef {
                public static final addSheet cmd = new addSheet();
            public addSheet sheet(String value) {
                return set("sheet", value);
            }

            public addSheet note(String value) {
                return set("note", value);
            }

            public addSheet force(String value) {
                return set("force", value);
            }

            public addSheet negative(String value) {
                return set("negative", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="deposits")
            public static class check extends CommandRef {
                public static final check cmd = new check();
            public check nationOrAllianceOrGuild(String value) {
                return set("nationOrAllianceOrGuild", value);
            }

            public check offshores(String value) {
                return set("offshores", value);
            }

            public check timeCutoff(String value) {
                return set("timeCutoff", value);
            }

            public check includeBaseTaxes(String value) {
                return set("includeBaseTaxes", value);
            }

            public check ignoreInternalOffsets(String value) {
                return set("ignoreInternalOffsets", value);
            }

            public check showCategories(String value) {
                return set("showCategories", value);
            }

            public check replyInDMs(String value) {
                return set("replyInDMs", value);
            }

            public check includeExpired(String value) {
                return set("includeExpired", value);
            }

            public check includeIgnored(String value) {
                return set("includeIgnored", value);
            }

            public check allowCheckDeleted(String value) {
                return set("allowCheckDeleted", value);
            }

            public check hideEscrowed(String value) {
                return set("hideEscrowed", value);
            }

            public check show_expiring_records(String value) {
                return set("show_expiring_records", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="convertDeposits")
            public static class convert extends CommandRef {
                public static final convert cmd = new convert();
            public convert nations(String value) {
                return set("nations", value);
            }

            public convert mode(String value) {
                return set("mode", value);
            }

            public convert from_resources(String value) {
                return set("from_resources", value);
            }

            public convert to_resource(String value) {
                return set("to_resource", value);
            }

            public convert includeGrants(String value) {
                return set("includeGrants", value);
            }

            public convert depositType(String value) {
                return set("depositType", value);
            }

            public convert conversionFactor(String value) {
                return set("conversionFactor", value);
            }

            public convert note(String value) {
                return set("note", value);
            }

            public convert sheet(String value) {
                return set("sheet", value);
            }

            public convert force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="viewFlow")
            public static class flows extends CommandRef {
                public static final flows cmd = new flows();
            public flows nation(String value) {
                return set("nation", value);
            }

            public flows note(String value) {
                return set("note", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="interest")
            public static class interest extends CommandRef {
                public static final interest cmd = new interest();
            public interest nations(String value) {
                return set("nations", value);
            }

            public interest interestPositivePercent(String value) {
                return set("interestPositivePercent", value);
            }

            public interest interestNegativePercent(String value) {
                return set("interestNegativePercent", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="resetDeposits")
            public static class reset extends CommandRef {
                public static final reset cmd = new reset();
            public reset nations(String value) {
                return set("nations", value);
            }

            public reset ignoreGrants(String value) {
                return set("ignoreGrants", value);
            }

            public reset ignoreLoans(String value) {
                return set("ignoreLoans", value);
            }

            public reset ignoreTaxes(String value) {
                return set("ignoreTaxes", value);
            }

            public reset ignoreBankDeposits(String value) {
                return set("ignoreBankDeposits", value);
            }

            public reset ignoreEscrow(String value) {
                return set("ignoreEscrow", value);
            }

            public reset force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="depositSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
            public sheet nations(String value) {
                return set("nations", value);
            }

            public sheet offshores(String value) {
                return set("offshores", value);
            }

            public sheet ignoreTaxBase(String value) {
                return set("ignoreTaxBase", value);
            }

            public sheet ignoreOffsets(String value) {
                return set("ignoreOffsets", value);
            }

            public sheet includeExpired(String value) {
                return set("includeExpired", value);
            }

            public sheet includeIgnored(String value) {
                return set("includeIgnored", value);
            }

            public sheet noTaxes(String value) {
                return set("noTaxes", value);
            }

            public sheet noLoans(String value) {
                return set("noLoans", value);
            }

            public sheet noGrants(String value) {
                return set("noGrants", value);
            }

            public sheet noDeposits(String value) {
                return set("noDeposits", value);
            }

            public sheet includePastDepositors(String value) {
                return set("includePastDepositors", value);
            }

            public sheet noEscrowSheet(String value) {
                return set("noEscrowSheet", value);
            }

            public sheet useFlowNote(String value) {
                return set("useFlowNote", value);
            }

            public sheet force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="shiftDeposits")
            public static class shift extends CommandRef {
                public static final shift cmd = new shift();
            public shift nation(String value) {
                return set("nation", value);
            }

            public shift from(String value) {
                return set("from", value);
            }

            public shift to(String value) {
                return set("to", value);
            }

            public shift expireTime(String value) {
                return set("expireTime", value);
            }

            public shift decayTime(String value) {
                return set("decayTime", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="shiftFlow")
            public static class shiftFlow extends CommandRef {
                public static final shiftFlow cmd = new shiftFlow();
            public shiftFlow nation(String value) {
                return set("nation", value);
            }

            public shiftFlow noteFrom(String value) {
                return set("noteFrom", value);
            }

            public shiftFlow flowType(String value) {
                return set("flowType", value);
            }

            public shiftFlow amount(String value) {
                return set("amount", value);
            }

            public shiftFlow noteTo(String value) {
                return set("noteTo", value);
            }

            public shiftFlow alliance(String value) {
                return set("alliance", value);
            }

            public shiftFlow force(String value) {
                return set("force", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="embassy")
        public static class embassy extends CommandRef {
            public static final embassy cmd = new embassy();
        public embassy nation(String value) {
            return set("nation", value);
        }

        }
        public static class embed{
            public static class add{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="addButton")
                public static class command extends CommandRef {
                    public static final command cmd = new command();
                public command message(String value) {
                    return set("message", value);
                }

                public command label(String value) {
                    return set("label", value);
                }

                public command behavior(String value) {
                    return set("behavior", value);
                }

                public command command(String value) {
                    return set("command", value);
                }

                public command arguments(String value) {
                    return set("arguments", value);
                }

                public command channel(String value) {
                    return set("channel", value);
                }

                public command force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="addModal")
                public static class modal extends CommandRef {
                    public static final modal cmd = new modal();
                public modal message(String value) {
                    return set("message", value);
                }

                public modal label(String value) {
                    return set("label", value);
                }

                public modal behavior(String value) {
                    return set("behavior", value);
                }

                public modal command(String value) {
                    return set("command", value);
                }

                public modal arguments(String value) {
                    return set("arguments", value);
                }

                public modal defaults(String value) {
                    return set("defaults", value);
                }

                public modal channel(String value) {
                    return set("channel", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="addButtonRaw")
                public static class raw extends CommandRef {
                    public static final raw cmd = new raw();
                public raw message(String value) {
                    return set("message", value);
                }

                public raw label(String value) {
                    return set("label", value);
                }

                public raw behavior(String value) {
                    return set("behavior", value);
                }

                public raw command(String value) {
                    return set("command", value);
                }

                public raw channel(String value) {
                    return set("channel", value);
                }

                public raw force(String value) {
                    return set("force", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="card")
            public static class commands extends CommandRef {
                public static final commands cmd = new commands();
            public commands title(String value) {
                return set("title", value);
            }

            public commands body(String value) {
                return set("body", value);
            }

            public commands commands(String value) {
                return set("commands", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="create")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create title(String value) {
                return set("title", value);
            }

            public create description(String value) {
                return set("description", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="description")
            public static class description extends CommandRef {
                public static final description cmd = new description();
            public description discMessage(String value) {
                return set("discMessage", value);
            }

            public description description(String value) {
                return set("description", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="embedInfo")
            public static class info extends CommandRef {
                public static final info cmd = new info();
            public info embedMessage(String value) {
                return set("embedMessage", value);
            }

            public info copyToMessage(String value) {
                return set("copyToMessage", value);
            }

            }
            public static class remove{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="removeButton")
                public static class button extends CommandRef {
                    public static final button cmd = new button();
                public button message(String value) {
                    return set("message", value);
                }

                public button labels(String value) {
                    return set("labels", value);
                }

                }
            }
            public static class rename{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="renameButton")
                public static class button extends CommandRef {
                    public static final button cmd = new button();
                public button message(String value) {
                    return set("message", value);
                }

                public button label(String value) {
                    return set("label", value);
                }

                public button rename_to(String value) {
                    return set("rename_to", value);
                }

                }
            }
            public static class template{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="allyEnemySheets")
                public static class ally_enemy_sheets extends CommandRef {
                    public static final ally_enemy_sheets cmd = new ally_enemy_sheets();
                public ally_enemy_sheets outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public ally_enemy_sheets allEnemiesSheet(String value) {
                    return set("allEnemiesSheet", value);
                }

                public ally_enemy_sheets priorityEnemiesSheet(String value) {
                    return set("priorityEnemiesSheet", value);
                }

                public ally_enemy_sheets allAlliesSheet(String value) {
                    return set("allAlliesSheet", value);
                }

                public ally_enemy_sheets underutilizedAlliesSheet(String value) {
                    return set("underutilizedAlliesSheet", value);
                }

                public ally_enemy_sheets behavior(String value) {
                    return set("behavior", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="depositsPanel")
                public static class deposits extends CommandRef {
                    public static final deposits cmd = new deposits();
                public deposits bankerNation(String value) {
                    return set("bankerNation", value);
                }

                public deposits outputChannel(String value) {
                    return set("outputChannel", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="econPanel")
                public static class econ_gov extends CommandRef {
                    public static final econ_gov cmd = new econ_gov();
                public econ_gov outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public econ_gov behavior(String value) {
                    return set("behavior", value);
                }

                public econ_gov useFlowNote(String value) {
                    return set("useFlowNote", value);
                }

                public econ_gov includePastDepositors(String value) {
                    return set("includePastDepositors", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="warGuerilla")
                public static class guerilla extends CommandRef {
                    public static final guerilla cmd = new guerilla();
                public guerilla outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public guerilla behavior(String value) {
                    return set("behavior", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="iaPanel")
                public static class ia_gov extends CommandRef {
                    public static final ia_gov cmd = new ia_gov();
                public ia_gov outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public ia_gov behavior(String value) {
                    return set("behavior", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="memberEconPanel")
                public static class member_econ extends CommandRef {
                    public static final member_econ cmd = new member_econ();
                public member_econ outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public member_econ behavior(String value) {
                    return set("behavior", value);
                }

                public member_econ showDepositsInDms(String value) {
                    return set("showDepositsInDms", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="raid")
                public static class raid extends CommandRef {
                    public static final raid cmd = new raid();
                public raid outputChannel(String value) {
                    return set("outputChannel", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="spyEnemy")
                public static class spy_enemy extends CommandRef {
                    public static final spy_enemy cmd = new spy_enemy();
                public spy_enemy coalition(String value) {
                    return set("coalition", value);
                }

                public spy_enemy outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public spy_enemy behavior(String value) {
                    return set("behavior", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="spySheets")
                public static class spy_sheets extends CommandRef {
                    public static final spy_sheets cmd = new spy_sheets();
                public spy_sheets allies(String value) {
                    return set("allies", value);
                }

                public spy_sheets outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public spy_sheets spySheet(String value) {
                    return set("spySheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="unblockadeRequests")
                public static class unblockade_requests extends CommandRef {
                    public static final unblockade_requests cmd = new unblockade_requests();
                public unblockade_requests outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public unblockade_requests behavior(String value) {
                    return set("behavior", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="warContestedRange")
                public static class war_contested_range extends CommandRef {
                    public static final war_contested_range cmd = new war_contested_range();
                public war_contested_range greaterOrLess(String value) {
                    return set("greaterOrLess", value);
                }

                public war_contested_range score(String value) {
                    return set("score", value);
                }

                public war_contested_range outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public war_contested_range behavior(String value) {
                    return set("behavior", value);
                }

                public war_contested_range resultsInDm(String value) {
                    return set("resultsInDm", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="warWinning")
                public static class war_winning extends CommandRef {
                    public static final war_winning cmd = new war_winning();
                public war_winning outputChannel(String value) {
                    return set("outputChannel", value);
                }

                public war_winning behavior(String value) {
                    return set("behavior", value);
                }

                public war_winning resultsInDm(String value) {
                    return set("resultsInDm", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.EmbedCommands.class,method="title")
            public static class title extends CommandRef {
                public static final title cmd = new title();
            public title discMessage(String value) {
                return set("discMessage", value);
            }

            public title title(String value) {
                return set("title", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="updateEmbed")
            public static class update extends CommandRef {
                public static final update cmd = new update();
            public update requiredRole(String value) {
                return set("requiredRole", value);
            }

            public update color(String value) {
                return set("color", value);
            }

            public update title(String value) {
                return set("title", value);
            }

            public update desc(String value) {
                return set("desc", value);
            }

            }
        }
        public static class escrow{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="addEscrow")
            public static class add extends CommandRef {
                public static final add cmd = new add();
            public add nations(String value) {
                return set("nations", value);
            }

            public add amountBase(String value) {
                return set("amountBase", value);
            }

            public add amountPerCity(String value) {
                return set("amountPerCity", value);
            }

            public add amountExtra(String value) {
                return set("amountExtra", value);
            }

            public add subtractStockpile(String value) {
                return set("subtractStockpile", value);
            }

            public add subtractNationsUnits(String value) {
                return set("subtractNationsUnits", value);
            }

            public add subtractDeposits(String value) {
                return set("subtractDeposits", value);
            }

            public add expireAfter(String value) {
                return set("expireAfter", value);
            }

            public add force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setEscrow")
            public static class set extends CommandRef {
                public static final set cmd = new set();
            public set nations(String value) {
                return set("nations", value);
            }

            public set amountBase(String value) {
                return set("amountBase", value);
            }

            public set amountPerCity(String value) {
                return set("amountPerCity", value);
            }

            public set amountExtra(String value) {
                return set("amountExtra", value);
            }

            public set subtractStockpile(String value) {
                return set("subtractStockpile", value);
            }

            public set subtractNationsUnits(String value) {
                return set("subtractNationsUnits", value);
            }

            public set subtractDeposits(String value) {
                return set("subtractDeposits", value);
            }

            public set expireAfter(String value) {
                return set("expireAfter", value);
            }

            public set force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setEscrowSheet")
            public static class set_sheet extends CommandRef {
                public static final set_sheet cmd = new set_sheet();
            public set_sheet sheet(String value) {
                return set("sheet", value);
            }

            public set_sheet expireAfter(String value) {
                return set("expireAfter", value);
            }

            public set_sheet force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="escrowSheetCmd")
            public static class view_sheet extends CommandRef {
                public static final view_sheet cmd = new view_sheet();
            public view_sheet nations(String value) {
                return set("nations", value);
            }

            public view_sheet includePastDepositors(String value) {
                return set("includePastDepositors", value);
            }

            public view_sheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="withdrawEscrowed")
            public static class withdraw extends CommandRef {
                public static final withdraw cmd = new withdraw();
            public withdraw receiver(String value) {
                return set("receiver", value);
            }

            public withdraw amount(String value) {
                return set("amount", value);
            }

            public withdraw force(String value) {
                return set("force", value);
            }

            }
        }
        public static class fun{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="borg")
            public static class borg extends CommandRef {
                public static final borg cmd = new borg();
            public borg msg(String value) {
                return set("msg", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="borgmas")
            public static class borgmas extends CommandRef {
                public static final borgmas cmd = new borgmas();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="joke")
            public static class joke extends CommandRef {
                public static final joke cmd = new joke();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="resetCityNames")
            public static class reset_borgs_cities extends CommandRef {
                public static final reset_borgs_cities cmd = new reset_borgs_cities();
            public reset_borgs_cities name(String value) {
                return set("name", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="say")
            public static class say extends CommandRef {
                public static final say cmd = new say();
            public say msg(String value) {
                return set("msg", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FunCommands.class,method="stealBorgsCity")
            public static class stealborgscity extends CommandRef {
                public static final stealborgscity cmd = new stealborgscity();

            }
        }
        public static class grant{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantBuild")
            public static class build extends CommandRef {
                public static final build cmd = new build();
            public build receivers(String value) {
                return set("receivers", value);
            }

            public build build(String value) {
                return set("build", value);
            }

            public build is_new_city(String value) {
                return set("is_new_city", value);
            }

            public build city_ids(String value) {
                return set("city_ids", value);
            }

            public build grant_infra(String value) {
                return set("grant_infra", value);
            }

            public build grant_land(String value) {
                return set("grant_land", value);
            }

            public build bonus_percent(String value) {
                return set("bonus_percent", value);
            }

            public build onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public build depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public build useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public build useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public build taxAccount(String value) {
                return set("taxAccount", value);
            }

            public build existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public build expire(String value) {
                return set("expire", value);
            }

            public build decay(String value) {
                return set("decay", value);
            }

            public build ignore(String value) {
                return set("ignore", value);
            }

            public build convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public build escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public build ping_role(String value) {
                return set("ping_role", value);
            }

            public build ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public build bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public build force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantCity")
            public static class city extends CommandRef {
                public static final city cmd = new city();
            public city receivers(String value) {
                return set("receivers", value);
            }

            public city amount(String value) {
                return set("amount", value);
            }

            public city upTo(String value) {
                return set("upTo", value);
            }

            public city onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public city depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public city useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public city useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public city taxAccount(String value) {
                return set("taxAccount", value);
            }

            public city existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public city expire(String value) {
                return set("expire", value);
            }

            public city decay(String value) {
                return set("decay", value);
            }

            public city ignore(String value) {
                return set("ignore", value);
            }

            public city convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public city escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public city manifest_destiny(String value) {
                return set("manifest_destiny", value);
            }

            public city gov_support_agency(String value) {
                return set("gov_support_agency", value);
            }

            public city domestic_affairs(String value) {
                return set("domestic_affairs", value);
            }

            public city exclude_city_refund(String value) {
                return set("exclude_city_refund", value);
            }

            public city ping_role(String value) {
                return set("ping_role", value);
            }

            public city ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public city bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public city force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantConsumption")
            public static class consumption extends CommandRef {
                public static final consumption cmd = new consumption();
            public consumption receivers(String value) {
                return set("receivers", value);
            }

            public consumption soldier_attacks(String value) {
                return set("soldier_attacks", value);
            }

            public consumption tank_attacks(String value) {
                return set("tank_attacks", value);
            }

            public consumption airstrikes(String value) {
                return set("airstrikes", value);
            }

            public consumption naval_attacks(String value) {
                return set("naval_attacks", value);
            }

            public consumption missiles(String value) {
                return set("missiles", value);
            }

            public consumption nukes(String value) {
                return set("nukes", value);
            }

            public consumption bonus_percent(String value) {
                return set("bonus_percent", value);
            }

            public consumption onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public consumption depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public consumption useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public consumption useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public consumption taxAccount(String value) {
                return set("taxAccount", value);
            }

            public consumption existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public consumption expire(String value) {
                return set("expire", value);
            }

            public consumption decay(String value) {
                return set("decay", value);
            }

            public consumption ignore(String value) {
                return set("ignore", value);
            }

            public consumption convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public consumption escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public consumption ping_role(String value) {
                return set("ping_role", value);
            }

            public consumption ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public consumption bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public consumption force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="costBulk")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost receivers(String value) {
                return set("receivers", value);
            }

            public cost cities(String value) {
                return set("cities", value);
            }

            public cost cities_up_to(String value) {
                return set("cities_up_to", value);
            }

            public cost buy_projects(String value) {
                return set("buy_projects", value);
            }

            public cost infra_level(String value) {
                return set("infra_level", value);
            }

            public cost land_level(String value) {
                return set("land_level", value);
            }

            public cost force_policy(String value) {
                return set("force_policy", value);
            }

            public cost force_projects(String value) {
                return set("force_projects", value);
            }

            public cost exclude_city_refund(String value) {
                return set("exclude_city_refund", value);
            }

            public cost research(String value) {
                return set("research", value);
            }

            public cost research_from_zero(String value) {
                return set("research_from_zero", value);
            }

            public cost sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantInfra")
            public static class infra extends CommandRef {
                public static final infra cmd = new infra();
            public infra receivers(String value) {
                return set("receivers", value);
            }

            public infra infra_level(String value) {
                return set("infra_level", value);
            }

            public infra single_new_city(String value) {
                return set("single_new_city", value);
            }

            public infra onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public infra depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public infra useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public infra useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public infra taxAccount(String value) {
                return set("taxAccount", value);
            }

            public infra existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public infra expire(String value) {
                return set("expire", value);
            }

            public infra decay(String value) {
                return set("decay", value);
            }

            public infra ignore(String value) {
                return set("ignore", value);
            }

            public infra convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public infra escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public infra urbanization(String value) {
                return set("urbanization", value);
            }

            public infra advanced_engineering_corps(String value) {
                return set("advanced_engineering_corps", value);
            }

            public infra center_for_civil_engineering(String value) {
                return set("center_for_civil_engineering", value);
            }

            public infra gov_support_agency(String value) {
                return set("gov_support_agency", value);
            }

            public infra domestic_affairs(String value) {
                return set("domestic_affairs", value);
            }

            public infra ping_role(String value) {
                return set("ping_role", value);
            }

            public infra ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public infra bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public infra force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantLand")
            public static class land extends CommandRef {
                public static final land cmd = new land();
            public land receivers(String value) {
                return set("receivers", value);
            }

            public land to_land(String value) {
                return set("to_land", value);
            }

            public land single_new_city(String value) {
                return set("single_new_city", value);
            }

            public land onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public land depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public land useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public land useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public land taxAccount(String value) {
                return set("taxAccount", value);
            }

            public land existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public land expire(String value) {
                return set("expire", value);
            }

            public land decay(String value) {
                return set("decay", value);
            }

            public land ignore(String value) {
                return set("ignore", value);
            }

            public land convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public land escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public land rapid_expansion(String value) {
                return set("rapid_expansion", value);
            }

            public land advanced_engineering_corps(String value) {
                return set("advanced_engineering_corps", value);
            }

            public land arable_land_agency(String value) {
                return set("arable_land_agency", value);
            }

            public land gov_support_agency(String value) {
                return set("gov_support_agency", value);
            }

            public land domestic_affairs(String value) {
                return set("domestic_affairs", value);
            }

            public land ping_role(String value) {
                return set("ping_role", value);
            }

            public land ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public land bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public land force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantMMR")
            public static class mmr extends CommandRef {
                public static final mmr cmd = new mmr();
            public mmr receivers(String value) {
                return set("receivers", value);
            }

            public mmr mmr(String value) {
                return set("mmr", value);
            }

            public mmr is_additional_units(String value) {
                return set("is_additional_units", value);
            }

            public mmr onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public mmr depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public mmr useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public mmr useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public mmr taxAccount(String value) {
                return set("taxAccount", value);
            }

            public mmr existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public mmr expire(String value) {
                return set("expire", value);
            }

            public mmr decay(String value) {
                return set("decay", value);
            }

            public mmr ignore(String value) {
                return set("ignore", value);
            }

            public mmr convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public mmr escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public mmr ping_role(String value) {
                return set("ping_role", value);
            }

            public mmr ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public mmr bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public mmr force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantProject")
            public static class project extends CommandRef {
                public static final project cmd = new project();
            public project receivers(String value) {
                return set("receivers", value);
            }

            public project project(String value) {
                return set("project", value);
            }

            public project onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public project depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public project useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public project useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public project taxAccount(String value) {
                return set("taxAccount", value);
            }

            public project existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public project expire(String value) {
                return set("expire", value);
            }

            public project decay(String value) {
                return set("decay", value);
            }

            public project ignore(String value) {
                return set("ignore", value);
            }

            public project convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public project escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public project technological_advancement(String value) {
                return set("technological_advancement", value);
            }

            public project gov_support_agency(String value) {
                return set("gov_support_agency", value);
            }

            public project domestic_affairs(String value) {
                return set("domestic_affairs", value);
            }

            public project ping_role(String value) {
                return set("ping_role", value);
            }

            public project ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public project bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public project force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantResearch")
            public static class research extends CommandRef {
                public static final research cmd = new research();
            public research receivers(String value) {
                return set("receivers", value);
            }

            public research research(String value) {
                return set("research", value);
            }

            public research research_from_zero(String value) {
                return set("research_from_zero", value);
            }

            public research onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public research depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public research useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public research useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public research taxAccount(String value) {
                return set("taxAccount", value);
            }

            public research existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public research expire(String value) {
                return set("expire", value);
            }

            public research decay(String value) {
                return set("decay", value);
            }

            public research ignore(String value) {
                return set("ignore", value);
            }

            public research convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public research escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public research ping_role(String value) {
                return set("ping_role", value);
            }

            public research ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public research bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public research force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantUnit")
            public static class unit extends CommandRef {
                public static final unit cmd = new unit();
            public unit receivers(String value) {
                return set("receivers", value);
            }

            public unit units(String value) {
                return set("units", value);
            }

            public unit scale_per_city(String value) {
                return set("scale_per_city", value);
            }

            public unit only_missing_units(String value) {
                return set("only_missing_units", value);
            }

            public unit onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public unit no_cash(String value) {
                return set("no_cash", value);
            }

            public unit depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public unit useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public unit useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public unit taxAccount(String value) {
                return set("taxAccount", value);
            }

            public unit existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public unit expire(String value) {
                return set("expire", value);
            }

            public unit decay(String value) {
                return set("decay", value);
            }

            public unit ignore(String value) {
                return set("ignore", value);
            }

            public unit convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public unit escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public unit ping_role(String value) {
                return set("ping_role", value);
            }

            public unit ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public unit bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public unit force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="grantWarchest")
            public static class warchest extends CommandRef {
                public static final warchest cmd = new warchest();
            public warchest receivers(String value) {
                return set("receivers", value);
            }

            public warchest ratio(String value) {
                return set("ratio", value);
            }

            public warchest onlySendMissingFunds(String value) {
                return set("onlySendMissingFunds", value);
            }

            public warchest depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public warchest useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public warchest useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public warchest taxAccount(String value) {
                return set("taxAccount", value);
            }

            public warchest existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public warchest expire(String value) {
                return set("expire", value);
            }

            public warchest decay(String value) {
                return set("decay", value);
            }

            public warchest ignore(String value) {
                return set("ignore", value);
            }

            public warchest convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public warchest escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public warchest ping_role(String value) {
                return set("ping_role", value);
            }

            public warchest ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public warchest bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public warchest force(String value) {
                return set("force", value);
            }

            }
        }
        public static class grant_template{
            public static class create{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateBuild")
                public static class build extends CommandRef {
                    public static final build cmd = new build();
                public build name(String value) {
                    return set("name", value);
                }

                public build allowedRecipients(String value) {
                    return set("allowedRecipients", value);
                }

                public build build(String value) {
                    return set("build", value);
                }

                public build mmr(String value) {
                    return set("mmr", value);
                }

                public build only_new_cities(String value) {
                    return set("only_new_cities", value);
                }

                public build allow_after_days(String value) {
                    return set("allow_after_days", value);
                }

                public build allow_after_offensive(String value) {
                    return set("allow_after_offensive", value);
                }

                public build allow_after_infra(String value) {
                    return set("allow_after_infra", value);
                }

                public build allow_all(String value) {
                    return set("allow_all", value);
                }

                public build allow_after_land_or_project(String value) {
                    return set("allow_after_land_or_project", value);
                }

                public build econRole(String value) {
                    return set("econRole", value);
                }

                public build selfRole(String value) {
                    return set("selfRole", value);
                }

                public build bracket(String value) {
                    return set("bracket", value);
                }

                public build useReceiverBracket(String value) {
                    return set("useReceiverBracket", value);
                }

                public build maxTotal(String value) {
                    return set("maxTotal", value);
                }

                public build maxDay(String value) {
                    return set("maxDay", value);
                }

                public build maxGranterDay(String value) {
                    return set("maxGranterDay", value);
                }

                public build maxGranterTotal(String value) {
                    return set("maxGranterTotal", value);
                }

                public build expireTime(String value) {
                    return set("expireTime", value);
                }

                public build decayTime(String value) {
                    return set("decayTime", value);
                }

                public build allowIgnore(String value) {
                    return set("allowIgnore", value);
                }

                public build repeatable_time(String value) {
                    return set("repeatable_time", value);
                }

                public build include_infra(String value) {
                    return set("include_infra", value);
                }

                public build include_land(String value) {
                    return set("include_land", value);
                }

                public build force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateCity")
                public static class city extends CommandRef {
                    public static final city cmd = new city();
                public city name(String value) {
                    return set("name", value);
                }

                public city allowedRecipients(String value) {
                    return set("allowedRecipients", value);
                }

                public city minCity(String value) {
                    return set("minCity", value);
                }

                public city maxCity(String value) {
                    return set("maxCity", value);
                }

                public city econRole(String value) {
                    return set("econRole", value);
                }

                public city selfRole(String value) {
                    return set("selfRole", value);
                }

                public city bracket(String value) {
                    return set("bracket", value);
                }

                public city useReceiverBracket(String value) {
                    return set("useReceiverBracket", value);
                }

                public city maxTotal(String value) {
                    return set("maxTotal", value);
                }

                public city maxDay(String value) {
                    return set("maxDay", value);
                }

                public city maxGranterDay(String value) {
                    return set("maxGranterDay", value);
                }

                public city maxGranterTotal(String value) {
                    return set("maxGranterTotal", value);
                }

                public city expireTime(String value) {
                    return set("expireTime", value);
                }

                public city decayTime(String value) {
                    return set("decayTime", value);
                }

                public city allowIgnore(String value) {
                    return set("allowIgnore", value);
                }

                public city force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateInfra")
                public static class infra extends CommandRef {
                    public static final infra cmd = new infra();
                public infra name(String value) {
                    return set("name", value);
                }

                public infra allowedRecipients(String value) {
                    return set("allowedRecipients", value);
                }

                public infra level(String value) {
                    return set("level", value);
                }

                public infra onlyNewCities(String value) {
                    return set("onlyNewCities", value);
                }

                public infra requireNOffensives(String value) {
                    return set("requireNOffensives", value);
                }

                public infra allowRebuild(String value) {
                    return set("allowRebuild", value);
                }

                public infra econRole(String value) {
                    return set("econRole", value);
                }

                public infra selfRole(String value) {
                    return set("selfRole", value);
                }

                public infra bracket(String value) {
                    return set("bracket", value);
                }

                public infra useReceiverBracket(String value) {
                    return set("useReceiverBracket", value);
                }

                public infra maxTotal(String value) {
                    return set("maxTotal", value);
                }

                public infra maxDay(String value) {
                    return set("maxDay", value);
                }

                public infra maxGranterDay(String value) {
                    return set("maxGranterDay", value);
                }

                public infra maxGranterTotal(String value) {
                    return set("maxGranterTotal", value);
                }

                public infra expireTime(String value) {
                    return set("expireTime", value);
                }

                public infra decayTime(String value) {
                    return set("decayTime", value);
                }

                public infra allowIgnore(String value) {
                    return set("allowIgnore", value);
                }

                public infra repeatable_time(String value) {
                    return set("repeatable_time", value);
                }

                public infra force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateLand")
                public static class land extends CommandRef {
                    public static final land cmd = new land();
                public land name(String value) {
                    return set("name", value);
                }

                public land allowedRecipients(String value) {
                    return set("allowedRecipients", value);
                }

                public land level(String value) {
                    return set("level", value);
                }

                public land onlyNewCities(String value) {
                    return set("onlyNewCities", value);
                }

                public land econRole(String value) {
                    return set("econRole", value);
                }

                public land selfRole(String value) {
                    return set("selfRole", value);
                }

                public land bracket(String value) {
                    return set("bracket", value);
                }

                public land useReceiverBracket(String value) {
                    return set("useReceiverBracket", value);
                }

                public land maxTotal(String value) {
                    return set("maxTotal", value);
                }

                public land maxDay(String value) {
                    return set("maxDay", value);
                }

                public land maxGranterDay(String value) {
                    return set("maxGranterDay", value);
                }

                public land maxGranterTotal(String value) {
                    return set("maxGranterTotal", value);
                }

                public land expireTime(String value) {
                    return set("expireTime", value);
                }

                public land decayTime(String value) {
                    return set("decayTime", value);
                }

                public land allowIgnore(String value) {
                    return set("allowIgnore", value);
                }

                public land repeatable_time(String value) {
                    return set("repeatable_time", value);
                }

                public land force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateProject")
                public static class project extends CommandRef {
                    public static final project cmd = new project();
                public project name(String value) {
                    return set("name", value);
                }

                public project allowedRecipients(String value) {
                    return set("allowedRecipients", value);
                }

                public project project(String value) {
                    return set("project", value);
                }

                public project econRole(String value) {
                    return set("econRole", value);
                }

                public project selfRole(String value) {
                    return set("selfRole", value);
                }

                public project bracket(String value) {
                    return set("bracket", value);
                }

                public project useReceiverBracket(String value) {
                    return set("useReceiverBracket", value);
                }

                public project maxTotal(String value) {
                    return set("maxTotal", value);
                }

                public project maxDay(String value) {
                    return set("maxDay", value);
                }

                public project maxGranterDay(String value) {
                    return set("maxGranterDay", value);
                }

                public project maxGranterTotal(String value) {
                    return set("maxGranterTotal", value);
                }

                public project expireTime(String value) {
                    return set("expireTime", value);
                }

                public project decayTime(String value) {
                    return set("decayTime", value);
                }

                public project allowIgnore(String value) {
                    return set("allowIgnore", value);
                }

                public project force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateRaws")
                public static class raws extends CommandRef {
                    public static final raws cmd = new raws();
                public raws name(String value) {
                    return set("name", value);
                }

                public raws allowedRecipients(String value) {
                    return set("allowedRecipients", value);
                }

                public raws days(String value) {
                    return set("days", value);
                }

                public raws overdrawPercent(String value) {
                    return set("overdrawPercent", value);
                }

                public raws econRole(String value) {
                    return set("econRole", value);
                }

                public raws selfRole(String value) {
                    return set("selfRole", value);
                }

                public raws bracket(String value) {
                    return set("bracket", value);
                }

                public raws useReceiverBracket(String value) {
                    return set("useReceiverBracket", value);
                }

                public raws maxTotal(String value) {
                    return set("maxTotal", value);
                }

                public raws maxDay(String value) {
                    return set("maxDay", value);
                }

                public raws maxGranterDay(String value) {
                    return set("maxGranterDay", value);
                }

                public raws maxGranterTotal(String value) {
                    return set("maxGranterTotal", value);
                }

                public raws expireTime(String value) {
                    return set("expireTime", value);
                }

                public raws decayTime(String value) {
                    return set("decayTime", value);
                }

                public raws allowIgnore(String value) {
                    return set("allowIgnore", value);
                }

                public raws repeatable_time(String value) {
                    return set("repeatable_time", value);
                }

                public raws force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateResearch")
                public static class research extends CommandRef {
                    public static final research cmd = new research();
                public research name(String value) {
                    return set("name", value);
                }

                public research allowedRecipients(String value) {
                    return set("allowedRecipients", value);
                }

                public research research(String value) {
                    return set("research", value);
                }

                public research from_zero(String value) {
                    return set("from_zero", value);
                }

                public research econRole(String value) {
                    return set("econRole", value);
                }

                public research selfRole(String value) {
                    return set("selfRole", value);
                }

                public research bracket(String value) {
                    return set("bracket", value);
                }

                public research useReceiverBracket(String value) {
                    return set("useReceiverBracket", value);
                }

                public research maxTotal(String value) {
                    return set("maxTotal", value);
                }

                public research maxDay(String value) {
                    return set("maxDay", value);
                }

                public research maxGranterDay(String value) {
                    return set("maxGranterDay", value);
                }

                public research maxGranterTotal(String value) {
                    return set("maxGranterTotal", value);
                }

                public research expireTime(String value) {
                    return set("expireTime", value);
                }

                public research decayTime(String value) {
                    return set("decayTime", value);
                }

                public research allowIgnore(String value) {
                    return set("allowIgnore", value);
                }

                public research force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateCreateWarchest")
                public static class warchest extends CommandRef {
                    public static final warchest cmd = new warchest();
                public warchest name(String value) {
                    return set("name", value);
                }

                public warchest allowedRecipients(String value) {
                    return set("allowedRecipients", value);
                }

                public warchest allowancePerCity(String value) {
                    return set("allowancePerCity", value);
                }

                public warchest trackDays(String value) {
                    return set("trackDays", value);
                }

                public warchest subtractExpenditure(String value) {
                    return set("subtractExpenditure", value);
                }

                public warchest overdrawPercent(String value) {
                    return set("overdrawPercent", value);
                }

                public warchest econRole(String value) {
                    return set("econRole", value);
                }

                public warchest selfRole(String value) {
                    return set("selfRole", value);
                }

                public warchest bracket(String value) {
                    return set("bracket", value);
                }

                public warchest useReceiverBracket(String value) {
                    return set("useReceiverBracket", value);
                }

                public warchest maxTotal(String value) {
                    return set("maxTotal", value);
                }

                public warchest maxDay(String value) {
                    return set("maxDay", value);
                }

                public warchest maxGranterDay(String value) {
                    return set("maxGranterDay", value);
                }

                public warchest maxGranterTotal(String value) {
                    return set("maxGranterTotal", value);
                }

                public warchest expireTime(String value) {
                    return set("expireTime", value);
                }

                public warchest decayTime(String value) {
                    return set("decayTime", value);
                }

                public warchest allowIgnore(String value) {
                    return set("allowIgnore", value);
                }

                public warchest repeatable_time(String value) {
                    return set("repeatable_time", value);
                }

                public warchest force(String value) {
                    return set("force", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateDelete")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
            public delete template(String value) {
                return set("template", value);
            }

            public delete force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateDisable")
            public static class disable extends CommandRef {
                public static final disable cmd = new disable();
            public disable template(String value) {
                return set("template", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateEnabled")
            public static class enable extends CommandRef {
                public static final enable cmd = new enable();
            public enable template(String value) {
                return set("template", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateInfo")
            public static class info extends CommandRef {
                public static final info cmd = new info();
            public info template(String value) {
                return set("template", value);
            }

            public info receiver(String value) {
                return set("receiver", value);
            }

            public info value(String value) {
                return set("value", value);
            }

            public info show_command(String value) {
                return set("show_command", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateList")
            public static class list extends CommandRef {
                public static final list cmd = new list();
            public list category(String value) {
                return set("category", value);
            }

            public list listDisabled(String value) {
                return set("listDisabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GrantCommands.class,method="templateSend")
            public static class send extends CommandRef {
                public static final send cmd = new send();
            public send template(String value) {
                return set("template", value);
            }

            public send receiver(String value) {
                return set("receiver", value);
            }

            public send expire(String value) {
                return set("expire", value);
            }

            public send decay(String value) {
                return set("decay", value);
            }

            public send ignore(String value) {
                return set("ignore", value);
            }

            public send customValue(String value) {
                return set("customValue", value);
            }

            public send escrowMode(String value) {
                return set("escrowMode", value);
            }

            public send force(String value) {
                return set("force", value);
            }

            }
        }
        public static class help{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="argument")
            public static class argument extends CommandRef {
                public static final argument cmd = new argument();
            public argument argument(String value) {
                return set("argument", value);
            }

            public argument skipOptionalArgs(String value) {
                return set("skipOptionalArgs", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="command")
            public static class command extends CommandRef {
                public static final command cmd = new command();
            public command command(String value) {
                return set("command", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="find_argument")
            public static class find_argument extends CommandRef {
                public static final find_argument cmd = new find_argument();
            public find_argument search(String value) {
                return set("search", value);
            }

            public find_argument instructions(String value) {
                return set("instructions", value);
            }

            public find_argument useGPT(String value) {
                return set("useGPT", value);
            }

            public find_argument numResults(String value) {
                return set("numResults", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="find_command2")
            public static class find_command extends CommandRef {
                public static final find_command cmd = new find_command();
            public find_command search(String value) {
                return set("search", value);
            }

            public find_command instructions(String value) {
                return set("instructions", value);
            }

            public find_command useGPT(String value) {
                return set("useGPT", value);
            }

            public find_command numResults(String value) {
                return set("numResults", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.GPTCommands.class,method="find_placeholder")
            public static class find_nation_placeholder extends CommandRef {
                public static final find_nation_placeholder cmd = new find_nation_placeholder();
            public find_nation_placeholder search(String value) {
                return set("search", value);
            }

            public find_nation_placeholder instructions(String value) {
                return set("instructions", value);
            }

            public find_nation_placeholder useGPT(String value) {
                return set("useGPT", value);
            }

            public find_nation_placeholder numResults(String value) {
                return set("numResults", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="find_setting")
            public static class find_setting extends CommandRef {
                public static final find_setting cmd = new find_setting();
            public find_setting query(String value) {
                return set("query", value);
            }

            public find_setting num_results(String value) {
                return set("num_results", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="moderation_check")
            public static class moderation_check extends CommandRef {
                public static final moderation_check cmd = new moderation_check();
            public moderation_check input(String value) {
                return set("input", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.HelpCommands.class,method="nation_placeholder")
            public static class nation_placeholder extends CommandRef {
                public static final nation_placeholder cmd = new nation_placeholder();
            public nation_placeholder command(String value) {
                return set("command", value);
            }

            }
        }
        public static class infra{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="InfraCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost currentInfra(String value) {
                return set("currentInfra", value);
            }

            public cost maxInfra(String value) {
                return set("maxInfra", value);
            }

            public cost urbanization(String value) {
                return set("urbanization", value);
            }

            public cost center_for_civil_engineering(String value) {
                return set("center_for_civil_engineering", value);
            }

            public cost advanced_engineering_corps(String value) {
                return set("advanced_engineering_corps", value);
            }

            public cost government_support_agency(String value) {
                return set("government_support_agency", value);
            }

            public cost cities(String value) {
                return set("cities", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="infraROI")
            public static class roi extends CommandRef {
                public static final roi cmd = new roi();
            public roi city(String value) {
                return set("city", value);
            }

            public roi infraLevel(String value) {
                return set("infraLevel", value);
            }

            public roi continent(String value) {
                return set("continent", value);
            }

            public roi rads(String value) {
                return set("rads", value);
            }

            public roi forceProjects(String value) {
                return set("forceProjects", value);
            }

            public roi openMarkets(String value) {
                return set("openMarkets", value);
            }

            public roi mmr(String value) {
                return set("mmr", value);
            }

            public roi land(String value) {
                return set("land", value);
            }

            }
        }
        public static class interview{
            public static class channel{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="renameInterviewChannels")
                public static class auto_rename extends CommandRef {
                    public static final auto_rename cmd = new auto_rename();
                public auto_rename categories(String value) {
                    return set("categories", value);
                }

                public auto_rename allow_non_members(String value) {
                    return set("allow_non_members", value);
                }

                public auto_rename allow_vm(String value) {
                    return set("allow_vm", value);
                }

                public auto_rename list_missing(String value) {
                    return set("list_missing", value);
                }

                public auto_rename force(String value) {
                    return set("force", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="interview")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create user(String value) {
                return set("user", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="iaCat")
            public static class iacat extends CommandRef {
                public static final iacat cmd = new iacat();
            public iacat category(String value) {
                return set("category", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="iachannels")
            public static class iachannels extends CommandRef {
                public static final iachannels cmd = new iachannels();
            public iachannels filter(String value) {
                return set("filter", value);
            }

            public iachannels time(String value) {
                return set("time", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="incentiveRanking")
            public static class incentiveRanking extends CommandRef {
                public static final incentiveRanking cmd = new incentiveRanking();
            public incentiveRanking timestamp(String value) {
                return set("timestamp", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="interviewMessage")
            public static class interviewMessage extends CommandRef {
                public static final interviewMessage cmd = new interviewMessage();
            public interviewMessage nations(String value) {
                return set("nations", value);
            }

            public interviewMessage message(String value) {
                return set("message", value);
            }

            public interviewMessage pingMentee(String value) {
                return set("pingMentee", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="listMentors")
            public static class listMentors extends CommandRef {
                public static final listMentors cmd = new listMentors();
            public listMentors mentors(String value) {
                return set("mentors", value);
            }

            public listMentors mentees(String value) {
                return set("mentees", value);
            }

            public listMentors timediff(String value) {
                return set("timediff", value);
            }

            public listMentors includeAudit(String value) {
                return set("includeAudit", value);
            }

            public listMentors ignoreUnallocatedMembers(String value) {
                return set("ignoreUnallocatedMembers", value);
            }

            public listMentors listIdleMentors(String value) {
                return set("listIdleMentors", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mentee")
            public static class mentee extends CommandRef {
                public static final mentee cmd = new mentee();
            public mentee mentee(String value) {
                return set("mentee", value);
            }

            public mentee force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mentor")
            public static class mentor extends CommandRef {
                public static final mentor cmd = new mentor();
            public mentor mentor(String value) {
                return set("mentor", value);
            }

            public mentor mentee(String value) {
                return set("mentee", value);
            }

            public mentor force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="myMentees")
            public static class mymentees extends CommandRef {
                public static final mymentees cmd = new mymentees();
            public mymentees mentees(String value) {
                return set("mentees", value);
            }

            public mymentees timediff(String value) {
                return set("timediff", value);
            }

            }
            public static class questions{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setInterview")
                public static class set extends CommandRef {
                    public static final set cmd = new set();
                public set message(String value) {
                    return set("message", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="viewInterview")
                public static class view extends CommandRef {
                    public static final view cmd = new view();

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="recruitmentRankings")
            public static class recruitmentRankings extends CommandRef {
                public static final recruitmentRankings cmd = new recruitmentRankings();
            public recruitmentRankings cutoff(String value) {
                return set("cutoff", value);
            }

            public recruitmentRankings topX(String value) {
                return set("topX", value);
            }

            public recruitmentRankings uploadFile(String value) {
                return set("uploadFile", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setReferrer")
            public static class setReferrer extends CommandRef {
                public static final setReferrer cmd = new setReferrer();
            public setReferrer user(String value) {
                return set("user", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setReferrerId")
            public static class setreferrerid extends CommandRef {
                public static final setreferrerid cmd = new setreferrerid();
            public setreferrerid userId(String value) {
                return set("userId", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="interviewSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
            public sheet nations(String value) {
                return set("nations", value);
            }

            public sheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="sortInterviews")
            public static class sortInterviews extends CommandRef {
                public static final sortInterviews cmd = new sortInterviews();
            public sortInterviews sortCategorized(String value) {
                return set("sortCategorized", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="syncInterviews")
            public static class syncInterviews extends CommandRef {
                public static final syncInterviews cmd = new syncInterviews();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="unassignMentee")
            public static class unassignMentee extends CommandRef {
                public static final unassignMentee cmd = new unassignMentee();
            public unassignMentee mentee(String value) {
                return set("mentee", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="invite")
        public static class invite extends CommandRef {
            public static final invite cmd = new invite();

        }
        public static class land{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="LandCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost currentLand(String value) {
                return set("currentLand", value);
            }

            public cost maxLand(String value) {
                return set("maxLand", value);
            }

            public cost rapidExpansion(String value) {
                return set("rapidExpansion", value);
            }

            public cost arable_land_agency(String value) {
                return set("arable_land_agency", value);
            }

            public cost advanced_engineering_corps(String value) {
                return set("advanced_engineering_corps", value);
            }

            public cost government_support_agency(String value) {
                return set("government_support_agency", value);
            }

            public cost cities(String value) {
                return set("cities", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="landROI")
            public static class roi extends CommandRef {
                public static final roi cmd = new roi();
            public roi city(String value) {
                return set("city", value);
            }

            public roi landLevel(String value) {
                return set("landLevel", value);
            }

            public roi continent(String value) {
                return set("continent", value);
            }

            public roi rads(String value) {
                return set("rads", value);
            }

            public roi forceProjects(String value) {
                return set("forceProjects", value);
            }

            public roi openMarkets(String value) {
                return set("openMarkets", value);
            }

            public roi mmr(String value) {
                return set("mmr", value);
            }

            public roi infra(String value) {
                return set("infra", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord._test.command.CustomCommands.class,method="loadAttacks")
        public static class loadAttacks extends CommandRef {
            public static final loadAttacks cmd = new loadAttacks();
        public loadAttacks naval(String value) {
            return set("naval", value);
        }

        public loadAttacks nations(String value) {
            return set("nations", value);
        }

        public loadAttacks allowMunitions(String value) {
            return set("allowMunitions", value);
        }

        public loadAttacks allowGas(String value) {
            return set("allowGas", value);
        }

        public loadAttacks attackAtPeace(String value) {
            return set("attackAtPeace", value);
        }

        public loadAttacks rebuy(String value) {
            return set("rebuy", value);
        }

        }
        public static class mail{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mailCommandOutput")
            public static class command extends CommandRef {
                public static final command cmd = new command();
            public command nations(String value) {
                return set("nations", value);
            }

            public command subject(String value) {
                return set("subject", value);
            }

            public command command(String value) {
                return set("command", value);
            }

            public command body(String value) {
                return set("body", value);
            }

            public command sheet(String value) {
                return set("sheet", value);
            }

            public command sendDM(String value) {
                return set("sendDM", value);
            }

            public command skipMail(String value) {
                return set("skipMail", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="readMail")
            public static class read extends CommandRef {
                public static final read cmd = new read();
            public read message_id(String value) {
                return set("message_id", value);
            }

            public read account(String value) {
                return set("account", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="testRecruitMessage")
            public static class recruit extends CommandRef {
                public static final recruit cmd = new recruit();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="reply")
            public static class reply extends CommandRef {
                public static final reply cmd = new reply();
            public reply receiver(String value) {
                return set("receiver", value);
            }

            public reply url(String value) {
                return set("url", value);
            }

            public reply message(String value) {
                return set("message", value);
            }

            public reply sender(String value) {
                return set("sender", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="searchMail")
            public static class search extends CommandRef {
                public static final search cmd = new search();
            public search account(String value) {
                return set("account", value);
            }

            public search search_for(String value) {
                return set("search_for", value);
            }

            public search skip_unread(String value) {
                return set("skip_unread", value);
            }

            public search check_read(String value) {
                return set("check_read", value);
            }

            public search read_content(String value) {
                return set("read_content", value);
            }

            public search group_by_nation(String value) {
                return set("group_by_nation", value);
            }

            public search count_replies(String value) {
                return set("count_replies", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mail")
            public static class send extends CommandRef {
                public static final send cmd = new send();
            public send nations(String value) {
                return set("nations", value);
            }

            public send subject(String value) {
                return set("subject", value);
            }

            public send message(String value) {
                return set("message", value);
            }

            public send force(String value) {
                return set("force", value);
            }

            public send sendFromGuildAccount(String value) {
                return set("sendFromGuildAccount", value);
            }

            public send apiKey(String value) {
                return set("apiKey", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="mailSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
            public sheet sheet(String value) {
                return set("sheet", value);
            }

            public sheet force(String value) {
                return set("force", value);
            }

            public sheet dm(String value) {
                return set("dm", value);
            }

            public sheet skipMail(String value) {
                return set("skipMail", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="mailTargets")
            public static class targets extends CommandRef {
                public static final targets cmd = new targets();
            public targets blitzSheet(String value) {
                return set("blitzSheet", value);
            }

            public targets spySheet(String value) {
                return set("spySheet", value);
            }

            public targets allowedNations(String value) {
                return set("allowedNations", value);
            }

            public targets allowedEnemies(String value) {
                return set("allowedEnemies", value);
            }

            public targets header(String value) {
                return set("header", value);
            }

            public targets sendFromGuildAccount(String value) {
                return set("sendFromGuildAccount", value);
            }

            public targets apiKey(String value) {
                return set("apiKey", value);
            }

            public targets hideDefaultBlurb(String value) {
                return set("hideDefaultBlurb", value);
            }

            public targets force(String value) {
                return set("force", value);
            }

            public targets useLeader(String value) {
                return set("useLeader", value);
            }

            public targets dm(String value) {
                return set("dm", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.web.commands.WebCommands.class,method="mailLogin")
            public static class web_login extends CommandRef {
                public static final web_login cmd = new web_login();
            public web_login nations(String value) {
                return set("nations", value);
            }

            public web_login reset_sessions(String value) {
                return set("reset_sessions", value);
            }

            public web_login force(String value) {
                return set("force", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="me")
        public static class me extends CommandRef {
            public static final me cmd = new me();
        public me snapshotDate(String value) {
            return set("snapshotDate", value);
        }

        }
        public static class menu{
            public static class button{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="addMenuButton")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                public add menu(String value) {
                    return set("menu", value);
                }

                public add label(String value) {
                    return set("label", value);
                }

                public add command(String value) {
                    return set("command", value);
                }

                public add force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="removeMenuButton")
                public static class remove extends CommandRef {
                    public static final remove cmd = new remove();
                public remove menu(String value) {
                    return set("menu", value);
                }

                public remove label(String value) {
                    return set("label", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="renameMenuButton")
                public static class rename extends CommandRef {
                    public static final rename cmd = new rename();
                public rename menu(String value) {
                    return set("menu", value);
                }

                public rename label(String value) {
                    return set("label", value);
                }

                public rename new_label(String value) {
                    return set("new_label", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="swapMenuButtons")
                public static class swap extends CommandRef {
                    public static final swap cmd = new swap();
                public swap menu(String value) {
                    return set("menu", value);
                }

                public swap label1(String value) {
                    return set("label1", value);
                }

                public swap label2(String value) {
                    return set("label2", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="cancel")
            public static class cancel extends CommandRef {
                public static final cancel cmd = new cancel();
            public cancel menu(String value) {
                return set("menu", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="setMenuState")
            public static class context extends CommandRef {
                public static final context cmd = new context();
            public context menu(String value) {
                return set("menu", value);
            }

            public context state(String value) {
                return set("state", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="newMenu")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create name(String value) {
                return set("name", value);
            }

            public create description(String value) {
                return set("description", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="deleteMenu")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
            public delete menu(String value) {
                return set("menu", value);
            }

            public delete force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="describeMenu")
            public static class description extends CommandRef {
                public static final description cmd = new description();
            public description menu(String value) {
                return set("menu", value);
            }

            public description description(String value) {
                return set("description", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="editMenu")
            public static class edit extends CommandRef {
                public static final edit cmd = new edit();
            public edit menu(String value) {
                return set("menu", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="info")
            public static class info extends CommandRef {
                public static final info cmd = new info();
            public info menu(String value) {
                return set("menu", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="list")
            public static class list extends CommandRef {
                public static final list cmd = new list();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="openMenu")
            public static class open extends CommandRef {
                public static final open cmd = new open();
            public open menu(String value) {
                return set("menu", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.class,method="renameMenu")
            public static class title extends CommandRef {
                public static final title cmd = new title();
            public title menu(String value) {
                return set("menu", value);
            }

            public title name(String value) {
                return set("name", value);
            }

            }
        }
        public static class modal{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="modal")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create command(String value) {
                return set("command", value);
            }

            public create arguments(String value) {
                return set("arguments", value);
            }

            public create defaults(String value) {
                return set("defaults", value);
            }

            }
        }
        public static class nation{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="TurnTimer")
            public static class TurnTimer extends CommandRef {
                public static final TurnTimer cmd = new TurnTimer();
            public TurnTimer nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="beigeTurns")
            public static class beigeTurns extends CommandRef {
                public static final beigeTurns cmd = new beigeTurns();
            public beigeTurns nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="canIBeige")
            public static class canIBeige extends CommandRef {
                public static final canIBeige cmd = new canIBeige();
            public canIBeige nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="leftAA")
            public static class departures extends CommandRef {
                public static final departures cmd = new departures();
            public departures nationOrAlliance(String value) {
                return set("nationOrAlliance", value);
            }

            public departures time(String value) {
                return set("time", value);
            }

            public departures filter(String value) {
                return set("filter", value);
            }

            public departures ignoreInactives(String value) {
                return set("ignoreInactives", value);
            }

            public departures ignoreVM(String value) {
                return set("ignoreVM", value);
            }

            public departures ignoreMembers(String value) {
                return set("ignoreMembers", value);
            }

            public departures listIds(String value) {
                return set("listIds", value);
            }

            public departures sheet(String value) {
                return set("sheet", value);
            }

            }
            public static class history{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="grayStreak")
                public static class gray_streak extends CommandRef {
                    public static final gray_streak cmd = new gray_streak();
                public gray_streak nations(String value) {
                    return set("nations", value);
                }

                public gray_streak daysInactive(String value) {
                    return set("daysInactive", value);
                }

                public gray_streak timeframe(String value) {
                    return set("timeframe", value);
                }

                public gray_streak sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="vmHistory")
                public static class vm extends CommandRef {
                    public static final vm cmd = new vm();
                public vm nations(String value) {
                    return set("nations", value);
                }

                public vm sheet(String value) {
                    return set("sheet", value);
                }

                }
            }
            public static class list{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="viewBans")
                public static class bans extends CommandRef {
                    public static final bans cmd = new bans();
                public bans nationId(String value) {
                    return set("nationId", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="inactive")
                public static class inactive extends CommandRef {
                    public static final inactive cmd = new inactive();
                public inactive nations(String value) {
                    return set("nations", value);
                }

                public inactive days(String value) {
                    return set("days", value);
                }

                public inactive includeApplicants(String value) {
                    return set("includeApplicants", value);
                }

                public inactive includeVacationMode(String value) {
                    return set("includeVacationMode", value);
                }

                public inactive page(String value) {
                    return set("page", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="multi")
                public static class multi extends CommandRef {
                    public static final multi cmd = new multi();
                public multi nation(String value) {
                    return set("nation", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="rebuy")
                public static class rebuy extends CommandRef {
                    public static final rebuy cmd = new rebuy();
                public rebuy nation(String value) {
                    return set("nation", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="loot")
            public static class loot extends CommandRef {
                public static final loot cmd = new loot();
            public loot nationOrAlliance(String value) {
                return set("nationOrAlliance", value);
            }

            public loot nationScore(String value) {
                return set("nationScore", value);
            }

            public loot pirate(String value) {
                return set("pirate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="moneyTrades")
            public static class moneyTrades extends CommandRef {
                public static final moneyTrades cmd = new moneyTrades();
            public moneyTrades nation(String value) {
                return set("nation", value);
            }

            public moneyTrades time(String value) {
                return set("time", value);
            }

            public moneyTrades forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            public moneyTrades addBalance(String value) {
                return set("addBalance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="reroll")
            public static class reroll extends CommandRef {
                public static final reroll cmd = new reroll();
            public reroll nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="revenue")
            public static class revenue extends CommandRef {
                public static final revenue cmd = new revenue();
            public revenue nations(String value) {
                return set("nations", value);
            }

            public revenue includeUntaxable(String value) {
                return set("includeUntaxable", value);
            }

            public revenue excludeNationBonus(String value) {
                return set("excludeNationBonus", value);
            }

            public revenue rads(String value) {
                return set("rads", value);
            }

            public revenue forceAtWar(String value) {
                return set("forceAtWar", value);
            }

            public revenue forceAtPeace(String value) {
                return set("forceAtPeace", value);
            }

            public revenue includeWarCosts(String value) {
                return set("includeWarCosts", value);
            }

            public revenue snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="revenueSheet")
            public static class revenueSheet extends CommandRef {
                public static final revenueSheet cmd = new revenueSheet();
            public revenueSheet nations(String value) {
                return set("nations", value);
            }

            public revenueSheet sheet(String value) {
                return set("sheet", value);
            }

            public revenueSheet include_untaxable(String value) {
                return set("include_untaxable", value);
            }

            public revenueSheet snapshotTime(String value) {
                return set("snapshotTime", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="score")
            public static class score extends CommandRef {
                public static final score cmd = new score();
            public score nation(String value) {
                return set("nation", value);
            }

            public score cities(String value) {
                return set("cities", value);
            }

            public score soldiers(String value) {
                return set("soldiers", value);
            }

            public score tanks(String value) {
                return set("tanks", value);
            }

            public score aircraft(String value) {
                return set("aircraft", value);
            }

            public score ships(String value) {
                return set("ships", value);
            }

            public score missiles(String value) {
                return set("missiles", value);
            }

            public score nukes(String value) {
                return set("nukes", value);
            }

            public score projects(String value) {
                return set("projects", value);
            }

            public score avg_infra(String value) {
                return set("avg_infra", value);
            }

            public score infraTotal(String value) {
                return set("infraTotal", value);
            }

            public score builtMMR(String value) {
                return set("builtMMR", value);
            }

            }
            public static class set{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="setLoot")
                public static class loot extends CommandRef {
                    public static final loot cmd = new loot();
                public loot nation(String value) {
                    return set("nation", value);
                }

                public loot resources(String value) {
                    return set("resources", value);
                }

                public loot type(String value) {
                    return set("type", value);
                }

                public loot fraction(String value) {
                    return set("fraction", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setRank")
                public static class rank extends CommandRef {
                    public static final rank cmd = new rank();
                public rank nation(String value) {
                    return set("nation", value);
                }

                public rank position(String value) {
                    return set("position", value);
                }

                public rank force(String value) {
                    return set("force", value);
                }

                public rank doNotUpdateDiscord(String value) {
                    return set("doNotUpdateDiscord", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="setBracket")
                public static class taxbracket extends CommandRef {
                    public static final taxbracket cmd = new taxbracket();
                public taxbracket nations(String value) {
                    return set("nations", value);
                }

                public taxbracket bracket(String value) {
                    return set("bracket", value);
                }

                public taxbracket internalTaxRate(String value) {
                    return set("internalTaxRate", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setNationTaxBrackets")
                public static class taxbracketAuto extends CommandRef {
                    public static final taxbracketAuto cmd = new taxbracketAuto();
                public taxbracketAuto nations(String value) {
                    return set("nations", value);
                }

                public taxbracketAuto ping(String value) {
                    return set("ping", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setInternalTaxRate")
                public static class taxinternal extends CommandRef {
                    public static final taxinternal cmd = new taxinternal();
                public taxinternal nations(String value) {
                    return set("nations", value);
                }

                public taxinternal taxRate(String value) {
                    return set("taxRate", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setNationInternalTaxRates")
                public static class taxinternalAuto extends CommandRef {
                    public static final taxinternalAuto cmd = new taxinternalAuto();
                public taxinternalAuto nations(String value) {
                    return set("nations", value);
                }

                public taxinternalAuto ping(String value) {
                    return set("ping", value);
                }

                }
            }
            public static class sheet{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="NationSheet")
                public static class NationSheet extends CommandRef {
                    public static final NationSheet cmd = new NationSheet();
                public NationSheet nations(String value) {
                    return set("nations", value);
                }

                public NationSheet columns(String value) {
                    return set("columns", value);
                }

                public NationSheet snapshotTime(String value) {
                    return set("snapshotTime", value);
                }

                public NationSheet updateSpies(String value) {
                    return set("updateSpies", value);
                }

                public NationSheet sheet(String value) {
                    return set("sheet", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectSlots")
            public static class slots extends CommandRef {
                public static final slots cmd = new slots();
            public slots nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="spies")
            public static class spies extends CommandRef {
                public static final spies cmd = new spies();
            public spies nation(String value) {
                return set("nation", value);
            }

            public spies spiesUsed(String value) {
                return set("spiesUsed", value);
            }

            public spies requiredSafety(String value) {
                return set("requiredSafety", value);
            }

            }
            public static class stats{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="inflows")
                public static class inflows extends CommandRef {
                    public static final inflows cmd = new inflows();
                public inflows nationOrAlliances(String value) {
                    return set("nationOrAlliances", value);
                }

                public inflows cutoffMs(String value) {
                    return set("cutoffMs", value);
                }

                public inflows hideInflows(String value) {
                    return set("hideInflows", value);
                }

                public inflows hideOutflows(String value) {
                    return set("hideOutflows", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="nationRanking")
                public static class nationRanking extends CommandRef {
                    public static final nationRanking cmd = new nationRanking();
                public nationRanking nations(String value) {
                    return set("nations", value);
                }

                public nationRanking attribute(String value) {
                    return set("attribute", value);
                }

                public nationRanking groupByAlliance(String value) {
                    return set("groupByAlliance", value);
                }

                public nationRanking reverseOrder(String value) {
                    return set("reverseOrder", value);
                }

                public nationRanking snapshotDate(String value) {
                    return set("snapshotDate", value);
                }

                public nationRanking total(String value) {
                    return set("total", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByNation")
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
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="stockpile")
            public static class stockpile extends CommandRef {
                public static final stockpile cmd = new stockpile();
            public stockpile nationOrAlliance(String value) {
                return set("nationOrAlliance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="unitHistory")
            public static class unitHistory extends CommandRef {
                public static final unitHistory cmd = new unitHistory();
            public unitHistory nation(String value) {
                return set("nation", value);
            }

            public unitHistory unit(String value) {
                return set("unit", value);
            }

            public unitHistory page(String value) {
                return set("page", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="wars")
            public static class wars extends CommandRef {
                public static final wars cmd = new wars();
            public wars nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="who")
            public static class who extends CommandRef {
                public static final who cmd = new who();
            public who nationOrAlliances(String value) {
                return set("nationOrAlliances", value);
            }

            public who sortBy(String value) {
                return set("sortBy", value);
            }

            public who list(String value) {
                return set("list", value);
            }

            public who listAlliances(String value) {
                return set("listAlliances", value);
            }

            public who listRawUserIds(String value) {
                return set("listRawUserIds", value);
            }

            public who listMentions(String value) {
                return set("listMentions", value);
            }

            public who listInfo(String value) {
                return set("listInfo", value);
            }

            public who listChannels(String value) {
                return set("listChannels", value);
            }

            public who snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public who page(String value) {
                return set("page", value);
            }

            }
        }
        public static class newsletter{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="autosend")
            public static class auto extends CommandRef {
                public static final auto cmd = new auto();
            public auto newsletter(String value) {
                return set("newsletter", value);
            }

            public auto interval(String value) {
                return set("interval", value);
            }

            public auto pingRole(String value) {
                return set("pingRole", value);
            }

            }
            public static class channel{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="channelAdd")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                public add newsletter(String value) {
                    return set("newsletter", value);
                }

                public add channel(String value) {
                    return set("channel", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="channelRemove")
                public static class remove extends CommandRef {
                    public static final remove cmd = new remove();
                public remove newsletter(String value) {
                    return set("newsletter", value);
                }

                public remove channel(String value) {
                    return set("channel", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="create")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create name(String value) {
                return set("name", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="delete")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
            public delete newsletter(String value) {
                return set("newsletter", value);
            }

            public delete force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="info")
            public static class info extends CommandRef {
                public static final info cmd = new info();
            public info newsletter(String value) {
                return set("newsletter", value);
            }

            public info listNations(String value) {
                return set("listNations", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="list")
            public static class list extends CommandRef {
                public static final list cmd = new list();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="send")
            public static class send extends CommandRef {
                public static final send cmd = new send();
            public send newsletter(String value) {
                return set("newsletter", value);
            }

            public send sendSince(String value) {
                return set("sendSince", value);
            }

            public send document(String value) {
                return set("document", value);
            }

            public send endDate(String value) {
                return set("endDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="subscribe")
            public static class subscribe extends CommandRef {
                public static final subscribe cmd = new subscribe();
            public subscribe newsletter(String value) {
                return set("newsletter", value);
            }

            public subscribe nations(String value) {
                return set("nations", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.NewsletterCommands.class,method="unsubscribe")
            public static class unsubscribe extends CommandRef {
                public static final unsubscribe cmd = new unsubscribe();
            public unsubscribe newsletter(String value) {
                return set("newsletter", value);
            }

            public unsubscribe nations(String value) {
                return set("nations", value);
            }

            }
        }
        public static class offshore{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="compareOffshoreStockpile")
            public static class accountSheet extends CommandRef {
                public static final accountSheet cmd = new accountSheet();
            public accountSheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="addOffshore")
            public static class add extends CommandRef {
                public static final add cmd = new add();
            public add offshoreAlliance(String value) {
                return set("offshoreAlliance", value);
            }

            public add newAccount(String value) {
                return set("newAccount", value);
            }

            public add importAccount(String value) {
                return set("importAccount", value);
            }

            public add force(String value) {
                return set("force", value);
            }

            }
            public static class find{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="findOffshore")
                public static class for_coalition extends CommandRef {
                    public static final for_coalition cmd = new for_coalition();
                public for_coalition alliance(String value) {
                    return set("alliance", value);
                }

                public for_coalition cutoffMs(String value) {
                    return set("cutoffMs", value);
                }

                public for_coalition transfer_count(String value) {
                    return set("transfer_count", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="findOffshores")
                public static class for_enemies extends CommandRef {
                    public static final for_enemies cmd = new for_enemies();
                public for_enemies cutoff(String value) {
                    return set("cutoff", value);
                }

                public for_enemies enemiesList(String value) {
                    return set("enemiesList", value);
                }

                public for_enemies alliesList(String value) {
                    return set("alliesList", value);
                }

                }
            }
            public static class list{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="listOffshores")
                public static class all extends CommandRef {
                    public static final all cmd = new all();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="prolificOffshores")
                public static class prolific extends CommandRef {
                    public static final prolific cmd = new prolific();
                public prolific days(String value) {
                    return set("days", value);
                }

                public prolific upload_file(String value) {
                    return set("upload_file", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="markAsOffshore")
            public static class markAsOffshore extends CommandRef {
                public static final markAsOffshore cmd = new markAsOffshore();
            public markAsOffshore offshore(String value) {
                return set("offshore", value);
            }

            public markAsOffshore parent(String value) {
                return set("parent", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="offshore")
            public static class send extends CommandRef {
                public static final send cmd = new send();
            public send to(String value) {
                return set("to", value);
            }

            public send account(String value) {
                return set("account", value);
            }

            public send keepAmount(String value) {
                return set("keepAmount", value);
            }

            public send sendAmount(String value) {
                return set("sendAmount", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="unlockTransfers")
            public static class unlockTransfers extends CommandRef {
                public static final unlockTransfers cmd = new unlockTransfers();
            public unlockTransfers nationOrAllianceOrGuild(String value) {
                return set("nationOrAllianceOrGuild", value);
            }

            public unlockTransfers unlockAll(String value) {
                return set("unlockAll", value);
            }

            }
        }
        public static class project{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost projects(String value) {
                return set("projects", value);
            }

            public cost technologicalAdvancement(String value) {
                return set("technologicalAdvancement", value);
            }

            public cost governmentSupportAgency(String value) {
                return set("governmentSupportAgency", value);
            }

            public cost domesticAffairs(String value) {
                return set("domesticAffairs", value);
            }

            public cost nations(String value) {
                return set("nations", value);
            }

            public cost sheet(String value) {
                return set("sheet", value);
            }

            public cost ignoreProjectSlots(String value) {
                return set("ignoreProjectSlots", value);
            }

            public cost ignoreRequirements(String value) {
                return set("ignoreRequirements", value);
            }

            public cost ignoreProjectCity(String value) {
                return set("ignoreProjectCity", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="projectCostCsv")
            public static class costsheet extends CommandRef {
                public static final costsheet cmd = new costsheet();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="ProjectSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
            public sheet nations(String value) {
                return set("nations", value);
            }

            public sheet sheet(String value) {
                return set("sheet", value);
            }

            public sheet snapshotTime(String value) {
                return set("snapshotTime", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="ProjectSlots")
            public static class slots extends CommandRef {
                public static final slots cmd = new slots();
            public slots nation(String value) {
                return set("nation", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="register")
        public static class register extends CommandRef {
            public static final register cmd = new register();
        public register nation(String value) {
            return set("nation", value);
        }

        }
        public static class report{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="createReport")
            public static class add extends CommandRef {
                public static final add cmd = new add();
            public add type(String value) {
                return set("type", value);
            }

            public add message(String value) {
                return set("message", value);
            }

            public add nation(String value) {
                return set("nation", value);
            }

            public add discord_user_id(String value) {
                return set("discord_user_id", value);
            }

            public add imageEvidenceUrl(String value) {
                return set("imageEvidenceUrl", value);
            }

            public add forum_post(String value) {
                return set("forum_post", value);
            }

            public add news_post(String value) {
                return set("news_post", value);
            }

            public add updateReport(String value) {
                return set("updateReport", value);
            }

            public add force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="riskFactors")
            public static class analyze extends CommandRef {
                public static final analyze cmd = new analyze();
            public analyze nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="approveReport")
            public static class approve extends CommandRef {
                public static final approve cmd = new approve();
            public approve report(String value) {
                return set("report", value);
            }

            public approve force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="ban")
            public static class ban extends CommandRef {
                public static final ban cmd = new ban();
            public ban nation(String value) {
                return set("nation", value);
            }

            public ban timestamp(String value) {
                return set("timestamp", value);
            }

            public ban reason(String value) {
                return set("reason", value);
            }

            public ban force(String value) {
                return set("force", value);
            }

            }
            public static class comment{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="comment")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                public add report(String value) {
                    return set("report", value);
                }

                public add comment(String value) {
                    return set("comment", value);
                }

                public add force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="removeComment")
                public static class delete extends CommandRef {
                    public static final delete cmd = new delete();
                public delete report(String value) {
                    return set("report", value);
                }

                public delete nationCommenting(String value) {
                    return set("nationCommenting", value);
                }

                public delete force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="purgeComments")
                public static class purge extends CommandRef {
                    public static final purge cmd = new purge();
                public purge report(String value) {
                    return set("report", value);
                }

                public purge nation_id(String value) {
                    return set("nation_id", value);
                }

                public purge discord_id(String value) {
                    return set("discord_id", value);
                }

                public purge force(String value) {
                    return set("force", value);
                }

                }
            }
            public static class loan{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="addLoan")
                public static class add extends CommandRef {
                    public static final add cmd = new add();
                public add receiver(String value) {
                    return set("receiver", value);
                }

                public add status(String value) {
                    return set("status", value);
                }

                public add overwriteLoan(String value) {
                    return set("overwriteLoan", value);
                }

                public add principal(String value) {
                    return set("principal", value);
                }

                public add remaining(String value) {
                    return set("remaining", value);
                }

                public add amountPaid(String value) {
                    return set("amountPaid", value);
                }

                public add dueDate(String value) {
                    return set("dueDate", value);
                }

                public add allianceLending(String value) {
                    return set("allianceLending", value);
                }

                public add force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="purgeLoans")
                public static class purge extends CommandRef {
                    public static final purge cmd = new purge();
                public purge guildOrAllianceId(String value) {
                    return set("guildOrAllianceId", value);
                }

                public purge force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="deleteLoan")
                public static class remove extends CommandRef {
                    public static final remove cmd = new remove();
                public remove loan(String value) {
                    return set("loan", value);
                }

                public remove force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="getLoanSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                public sheet nations(String value) {
                    return set("nations", value);
                }

                public sheet sheet(String value) {
                    return set("sheet", value);
                }

                public sheet loanStatus(String value) {
                    return set("loanStatus", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="updateLoan")
                public static class update extends CommandRef {
                    public static final update cmd = new update();
                public update loan(String value) {
                    return set("loan", value);
                }

                public update principal(String value) {
                    return set("principal", value);
                }

                public update remaining(String value) {
                    return set("remaining", value);
                }

                public update amountPaid(String value) {
                    return set("amountPaid", value);
                }

                public update dueDate(String value) {
                    return set("dueDate", value);
                }

                public update force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="markAllLoansAsUpdated")
                public static class update_all extends CommandRef {
                    public static final update_all cmd = new update_all();
                public update_all loanStatus(String value) {
                    return set("loanStatus", value);
                }

                public update_all force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="importLoans")
                public static class upload extends CommandRef {
                    public static final upload cmd = new upload();
                public upload sheet(String value) {
                    return set("sheet", value);
                }

                public upload defaultStatus(String value) {
                    return set("defaultStatus", value);
                }

                public upload overwriteLoans(String value) {
                    return set("overwriteLoans", value);
                }

                public upload overwriteSameNation(String value) {
                    return set("overwriteSameNation", value);
                }

                public upload addLoans(String value) {
                    return set("addLoans", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="purgeReports")
            public static class purge extends CommandRef {
                public static final purge cmd = new purge();
            public purge nationIdReported(String value) {
                return set("nationIdReported", value);
            }

            public purge userIdReported(String value) {
                return set("userIdReported", value);
            }

            public purge reportingNation(String value) {
                return set("reportingNation", value);
            }

            public purge reportingUser(String value) {
                return set("reportingUser", value);
            }

            public purge force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="removeReport")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
            public remove report(String value) {
                return set("report", value);
            }

            public remove force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="searchReports")
            public static class search extends CommandRef {
                public static final search cmd = new search();
            public search nationIdReported(String value) {
                return set("nationIdReported", value);
            }

            public search userIdReported(String value) {
                return set("userIdReported", value);
            }

            public search reportingNation(String value) {
                return set("reportingNation", value);
            }

            public search reportingUser(String value) {
                return set("reportingUser", value);
            }

            }
            public static class sheet{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="reportSheet")
                public static class generate extends CommandRef {
                    public static final generate cmd = new generate();
                public generate sheet(String value) {
                    return set("sheet", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="showReport")
            public static class show extends CommandRef {
                public static final show cmd = new show();
            public show report(String value) {
                return set("report", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="unban")
            public static class unban extends CommandRef {
                public static final unban cmd = new unban();
            public unban nation(String value) {
                return set("nation", value);
            }

            public unban force(String value) {
                return set("force", value);
            }

            }
            public static class upload{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands.class,method="importLegacyBlacklist")
                public static class legacy_reports extends CommandRef {
                    public static final legacy_reports cmd = new legacy_reports();
                public legacy_reports sheet(String value) {
                    return set("sheet", value);
                }

                }
            }
        }
        public static class research{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ResearchCommands.class,method="researchCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost start_level(String value) {
                return set("start_level", value);
            }

            public cost end_level(String value) {
                return set("end_level", value);
            }

            public cost military_doctrine(String value) {
                return set("military_doctrine", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ResearchCommands.class,method="researchSheet")
            public static class sheet extends CommandRef {
                public static final sheet cmd = new sheet();
            public sheet nations(String value) {
                return set("nations", value);
            }

            public sheet update(String value) {
                return set("update", value);
            }

            public sheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="updateResearch")
            public static class sync extends CommandRef {
                public static final sync cmd = new sync();
            public sync nations(String value) {
                return set("nations", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.ResearchCommands.class,method="getResearch")
            public static class view_nation extends CommandRef {
                public static final view_nation cmd = new view_nation();
            public view_nation nation(String value) {
                return set("nation", value);
            }

            }
        }
        public static class role{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addRoleToAllMembers")
            public static class addRoleToAllMembers extends CommandRef {
                public static final addRoleToAllMembers cmd = new addRoleToAllMembers();
            public addRoleToAllMembers role(String value) {
                return set("role", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="autoroleall")
            public static class autoassign extends CommandRef {
                public static final autoassign cmd = new autoassign();
            public autoassign force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="autorole")
            public static class autorole extends CommandRef {
                public static final autorole cmd = new autorole();
            public autorole member(String value) {
                return set("member", value);
            }

            public autorole force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="clearAllianceRoles")
            public static class clearAllianceRoles extends CommandRef {
                public static final clearAllianceRoles cmd = new clearAllianceRoles();
            public clearAllianceRoles type(String value) {
                return set("type", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="clearNicks")
            public static class clearNicks extends CommandRef {
                public static final clearNicks cmd = new clearNicks();
            public clearNicks undo(String value) {
                return set("undo", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="hasRole")
            public static class hasRole extends CommandRef {
                public static final hasRole cmd = new hasRole();
            public hasRole user(String value) {
                return set("user", value);
            }

            public hasRole role(String value) {
                return set("role", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="mask")
            public static class mask extends CommandRef {
                public static final mask cmd = new mask();
            public mask members(String value) {
                return set("members", value);
            }

            public mask role(String value) {
                return set("role", value);
            }

            public mask value(String value) {
                return set("value", value);
            }

            public mask toggleMaskFromOthers(String value) {
                return set("toggleMaskFromOthers", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="maskSheet")
            public static class mask_sheet extends CommandRef {
                public static final mask_sheet cmd = new mask_sheet();
            public mask_sheet sheet(String value) {
                return set("sheet", value);
            }

            public mask_sheet removeRoles(String value) {
                return set("removeRoles", value);
            }

            public mask_sheet removeAll(String value) {
                return set("removeAll", value);
            }

            public mask_sheet listMissing(String value) {
                return set("listMissing", value);
            }

            public mask_sheet force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.PlayerSettingCommands.class,method="optOut")
            public static class optOut extends CommandRef {
                public static final optOut cmd = new optOut();
            public optOut optOut(String value) {
                return set("optOut", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="removeAssignableRole")
            public static class removeAssignableRole extends CommandRef {
                public static final removeAssignableRole cmd = new removeAssignableRole();
            public removeAssignableRole requireRole(String value) {
                return set("requireRole", value);
            }

            public removeAssignableRole assignableRoles(String value) {
                return set("assignableRoles", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="aliasRole")
            public static class setAlias extends CommandRef {
                public static final setAlias cmd = new setAlias();
            public setAlias locutusRole(String value) {
                return set("locutusRole", value);
            }

            public setAlias discordRole(String value) {
                return set("discordRole", value);
            }

            public setAlias alliance(String value) {
                return set("alliance", value);
            }

            public setAlias removeRole(String value) {
                return set("removeRole", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="unregisterRole")
            public static class unregister extends CommandRef {
                public static final unregister cmd = new unregister();
            public unregister locutusRole(String value) {
                return set("locutusRole", value);
            }

            public unregister alliance(String value) {
                return set("alliance", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord._test.command.CustomCommands.class,method="safekeep")
        public static class safekeep extends CommandRef {
            public static final safekeep cmd = new safekeep();
        public safekeep warchest(String value) {
            return set("warchest", value);
        }

        }
        public static class selection_alias{
            public static class add{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="ALLIANCES")
                public static class alliance extends CommandRef {
                    public static final alliance cmd = new alliance();
                public alliance name(String value) {
                    return set("name", value);
                }

                public alliance alliances(String value) {
                    return set("alliances", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="ATTACK_TYPES")
                public static class attacktype extends CommandRef {
                    public static final attacktype cmd = new attacktype();
                public attacktype name(String value) {
                    return set("name", value);
                }

                public attacktype attack_types(String value) {
                    return set("attack_types", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="AUDIT_TYPES")
                public static class audittype extends CommandRef {
                    public static final audittype cmd = new audittype();
                public audittype name(String value) {
                    return set("name", value);
                }

                public audittype audit_types(String value) {
                    return set("audit_types", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="BANS")
                public static class ban extends CommandRef {
                    public static final ban cmd = new ban();
                public ban name(String value) {
                    return set("name", value);
                }

                public ban bans(String value) {
                    return set("bans", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="BOUNTIES")
                public static class bounty extends CommandRef {
                    public static final bounty cmd = new bounty();
                public bounty name(String value) {
                    return set("name", value);
                }

                public bounty bounties(String value) {
                    return set("bounties", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="BUILDINGS")
                public static class building extends CommandRef {
                    public static final building cmd = new building();
                public building name(String value) {
                    return set("name", value);
                }

                public building Buildings(String value) {
                    return set("Buildings", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="CITIES")
                public static class city extends CommandRef {
                    public static final city cmd = new city();
                public city name(String value) {
                    return set("name", value);
                }

                public city cities(String value) {
                    return set("cities", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="CONTINENTS")
                public static class continent extends CommandRef {
                    public static final continent cmd = new continent();
                public continent name(String value) {
                    return set("name", value);
                }

                public continent continents(String value) {
                    return set("continents", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="GUILDS")
                public static class guild extends CommandRef {
                    public static final guild cmd = new guild();
                public guild name(String value) {
                    return set("name", value);
                }

                public guild guilds(String value) {
                    return set("guilds", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="SETTINGS")
                public static class guildsetting extends CommandRef {
                    public static final guildsetting cmd = new guildsetting();
                public guildsetting name(String value) {
                    return set("name", value);
                }

                public guildsetting settings(String value) {
                    return set("settings", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="ATTACKS")
                public static class iattack extends CommandRef {
                    public static final iattack cmd = new iattack();
                public iattack name(String value) {
                    return set("name", value);
                }

                public iattack attacks(String value) {
                    return set("attacks", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="MILITARY_UNITS")
                public static class militaryunit extends CommandRef {
                    public static final militaryunit cmd = new militaryunit();
                public militaryunit name(String value) {
                    return set("name", value);
                }

                public militaryunit military_units(String value) {
                    return set("military_units", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="NATIONS")
                public static class nation extends CommandRef {
                    public static final nation cmd = new nation();
                public nation name(String value) {
                    return set("name", value);
                }

                public nation nations(String value) {
                    return set("nations", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="NATION_COLORS")
                public static class nationcolor extends CommandRef {
                    public static final nationcolor cmd = new nationcolor();
                public nationcolor name(String value) {
                    return set("name", value);
                }

                public nationcolor colors(String value) {
                    return set("colors", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="NATION_LIST")
                public static class nationlist extends CommandRef {
                    public static final nationlist cmd = new nationlist();
                public nationlist name(String value) {
                    return set("name", value);
                }

                public nationlist nationlists(String value) {
                    return set("nationlists", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="NATION_OR_ALLIANCE")
                public static class nationoralliance extends CommandRef {
                    public static final nationoralliance cmd = new nationoralliance();
                public nationoralliance name(String value) {
                    return set("name", value);
                }

                public nationoralliance nationoralliances(String value) {
                    return set("nationoralliances", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="PROJECTS")
                public static class project extends CommandRef {
                    public static final project cmd = new project();
                public project name(String value) {
                    return set("name", value);
                }

                public project projects(String value) {
                    return set("projects", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="RESOURCE_TYPES")
                public static class resourcetype extends CommandRef {
                    public static final resourcetype cmd = new resourcetype();
                public resourcetype name(String value) {
                    return set("name", value);
                }

                public resourcetype resources(String value) {
                    return set("resources", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TAX_BRACKETS")
                public static class taxbracket extends CommandRef {
                    public static final taxbracket cmd = new taxbracket();
                public taxbracket name(String value) {
                    return set("name", value);
                }

                public taxbracket taxbrackets(String value) {
                    return set("taxbrackets", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TAX_DEPOSITS")
                public static class taxdeposit extends CommandRef {
                    public static final taxdeposit cmd = new taxdeposit();
                public taxdeposit name(String value) {
                    return set("name", value);
                }

                public taxdeposit taxes(String value) {
                    return set("taxes", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TREASURES")
                public static class treasure extends CommandRef {
                    public static final treasure cmd = new treasure();
                public treasure name(String value) {
                    return set("name", value);
                }

                public treasure treasures(String value) {
                    return set("treasures", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TREATIES")
                public static class treaty extends CommandRef {
                    public static final treaty cmd = new treaty();
                public treaty name(String value) {
                    return set("name", value);
                }

                public treaty treaties(String value) {
                    return set("treaties", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="TREATY_TYPES")
                public static class treatytype extends CommandRef {
                    public static final treatytype cmd = new treatytype();
                public treatytype name(String value) {
                    return set("name", value);
                }

                public treatytype treaty_types(String value) {
                    return set("treaty_types", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="USERS")
                public static class user extends CommandRef {
                    public static final user cmd = new user();
                public user name(String value) {
                    return set("name", value);
                }

                public user users(String value) {
                    return set("users", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addSelectionAlias", field="WARS")
                public static class war extends CommandRef {
                    public static final war cmd = new war();
                public war name(String value) {
                    return set("name", value);
                }

                public war wars(String value) {
                    return set("wars", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="listSelectionAliases")
            public static class list extends CommandRef {
                public static final list cmd = new list();
            public list type(String value) {
                return set("type", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="deleteSelectionAlias")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
            public remove selection(String value) {
                return set("selection", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="renameSelection")
            public static class rename extends CommandRef {
                public static final rename cmd = new rename();
            public rename sheet(String value) {
                return set("sheet", value);
            }

            public rename name(String value) {
                return set("name", value);
            }

            }
        }
        public static class self{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addRole")
            public static class add extends CommandRef {
                public static final add cmd = new add();
            public add member(String value) {
                return set("member", value);
            }

            public add addRole(String value) {
                return set("addRole", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="addAssignableRole")
            public static class create extends CommandRef {
                public static final create cmd = new create();
            public create requireRole(String value) {
                return set("requireRole", value);
            }

            public create assignableRoles(String value) {
                return set("assignableRoles", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="listAssignableRoles")
            public static class list extends CommandRef {
                public static final list cmd = new list();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="removeRole")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
            public remove member(String value) {
                return set("member", value);
            }

            public remove addRole(String value) {
                return set("addRole", value);
            }

            }
        }
        public static class settings{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands.class,method="delete")
            public static class delete extends CommandRef {
                public static final delete cmd = new delete();
            public delete key(String value) {
                return set("key", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands.class,method="info")
            public static class info extends CommandRef {
                public static final info cmd = new info();
            public info key(String value) {
                return set("key", value);
            }

            public info value(String value) {
                return set("value", value);
            }

            public info listAll(String value) {
                return set("listAll", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.SettingCommands.class,method="sheets")
            public static class sheets extends CommandRef {
                public static final sheets cmd = new sheets();

            }
        }
        public static class settings_artificial_intelligence{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENABLE_GITHUB_COPILOT", field="ENABLE_GITHUB_COPILOT")
            public static class ENABLE_GITHUB_COPILOT extends CommandRef {
                public static final ENABLE_GITHUB_COPILOT cmd = new ENABLE_GITHUB_COPILOT();
            public ENABLE_GITHUB_COPILOT value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="GPT_USAGE_LIMITS", field="GPT_USAGE_LIMITS")
            public static class GPT_USAGE_LIMITS extends CommandRef {
                public static final GPT_USAGE_LIMITS cmd = new GPT_USAGE_LIMITS();
            public GPT_USAGE_LIMITS userTurnLimit(String value) {
                return set("userTurnLimit", value);
            }

            public GPT_USAGE_LIMITS userDayLimit(String value) {
                return set("userDayLimit", value);
            }

            public GPT_USAGE_LIMITS guildTurnLimit(String value) {
                return set("guildTurnLimit", value);
            }

            public GPT_USAGE_LIMITS guildDayLimit(String value) {
                return set("guildDayLimit", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="register_openai_key", field="OPENAI_KEY")
            public static class register_openai_key extends CommandRef {
                public static final register_openai_key cmd = new register_openai_key();
            public register_openai_key apiKey(String value) {
                return set("apiKey", value);
            }

            }
        }
        public static class settings_audit{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DISABLED_MEMBER_AUDITS", field="DISABLED_MEMBER_AUDITS")
            public static class DISABLED_MEMBER_AUDITS extends CommandRef {
                public static final DISABLED_MEMBER_AUDITS cmd = new DISABLED_MEMBER_AUDITS();
            public DISABLED_MEMBER_AUDITS audits(String value) {
                return set("audits", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_AUDIT_ALERTS", field="MEMBER_AUDIT_ALERTS")
            public static class MEMBER_AUDIT_ALERTS extends CommandRef {
                public static final MEMBER_AUDIT_ALERTS cmd = new MEMBER_AUDIT_ALERTS();
            public MEMBER_AUDIT_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_LEAVE_ALERT_CHANNEL", field="MEMBER_LEAVE_ALERT_CHANNEL")
            public static class MEMBER_LEAVE_ALERT_CHANNEL extends CommandRef {
                public static final MEMBER_LEAVE_ALERT_CHANNEL cmd = new MEMBER_LEAVE_ALERT_CHANNEL();
            public MEMBER_LEAVE_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_REBUY_INFRA_ALERT", field="MEMBER_REBUY_INFRA_ALERT")
            public static class MEMBER_REBUY_INFRA_ALERT extends CommandRef {
                public static final MEMBER_REBUY_INFRA_ALERT cmd = new MEMBER_REBUY_INFRA_ALERT();
            public MEMBER_REBUY_INFRA_ALERT channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REQUIRED_MMR", field="REQUIRED_MMR")
            public static class REQUIRED_MMR extends CommandRef {
                public static final REQUIRED_MMR cmd = new REQUIRED_MMR();
            public REQUIRED_MMR mmrMap(String value) {
                return set("mmrMap", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WARCHEST_PER_CITY", field="WARCHEST_PER_CITY")
            public static class WARCHEST_PER_CITY extends CommandRef {
                public static final WARCHEST_PER_CITY cmd = new WARCHEST_PER_CITY();
            public WARCHEST_PER_CITY amount(String value) {
                return set("amount", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addRequiredMMR", field="REQUIRED_MMR")
            public static class addRequiredMMR extends CommandRef {
                public static final addRequiredMMR cmd = new addRequiredMMR();
            public addRequiredMMR filter(String value) {
                return set("filter", value);
            }

            public addRequiredMMR mmr(String value) {
                return set("mmr", value);
            }

            }
        }
        public static class settings_bank_access{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ADD_RSS_CONVERSION_RATE", field="RSS_CONVERSION_RATES")
            public static class ADD_RSS_CONVERSION_RATE extends CommandRef {
                public static final ADD_RSS_CONVERSION_RATE cmd = new ADD_RSS_CONVERSION_RATE();
            public ADD_RSS_CONVERSION_RATE filter(String value) {
                return set("filter", value);
            }

            public ADD_RSS_CONVERSION_RATE prices(String value) {
                return set("prices", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ALLOW_NEGATIVE_RESOURCES", field="ALLOW_NEGATIVE_RESOURCES")
            public static class ALLOW_NEGATIVE_RESOURCES extends CommandRef {
                public static final ALLOW_NEGATIVE_RESOURCES cmd = new ALLOW_NEGATIVE_RESOURCES();
            public ALLOW_NEGATIVE_RESOURCES enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ALLOW_UNVERIFIED_BANKING", field="ALLOW_UNVERIFIED_BANKING")
            public static class ALLOW_UNVERIFIED_BANKING extends CommandRef {
                public static final ALLOW_UNVERIFIED_BANKING cmd = new ALLOW_UNVERIFIED_BANKING();
            public ALLOW_UNVERIFIED_BANKING value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BANKER_WITHDRAW_LIMIT", field="BANKER_WITHDRAW_LIMIT")
            public static class BANKER_WITHDRAW_LIMIT extends CommandRef {
                public static final BANKER_WITHDRAW_LIMIT cmd = new BANKER_WITHDRAW_LIMIT();
            public BANKER_WITHDRAW_LIMIT amount(String value) {
                return set("amount", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BANKER_WITHDRAW_LIMIT_INTERVAL", field="BANKER_WITHDRAW_LIMIT_INTERVAL")
            public static class BANKER_WITHDRAW_LIMIT_INTERVAL extends CommandRef {
                public static final BANKER_WITHDRAW_LIMIT_INTERVAL cmd = new BANKER_WITHDRAW_LIMIT_INTERVAL();
            public BANKER_WITHDRAW_LIMIT_INTERVAL timediff(String value) {
                return set("timediff", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DEFAULT_OFFSHORE_ACCOUNT", field="DEFAULT_OFFSHORE_ACCOUNT")
            public static class DEFAULT_OFFSHORE_ACCOUNT extends CommandRef {
                public static final DEFAULT_OFFSHORE_ACCOUNT cmd = new DEFAULT_OFFSHORE_ACCOUNT();
            public DEFAULT_OFFSHORE_ACCOUNT natOrAA(String value) {
                return set("natOrAA", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="FORCE_RSS_CONVERSION", field="FORCE_RSS_CONVERSION")
            public static class FORCE_RSS_CONVERSION extends CommandRef {
                public static final FORCE_RSS_CONVERSION cmd = new FORCE_RSS_CONVERSION();
            public FORCE_RSS_CONVERSION enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="GRANT_LIMIT_DELAY", field="GRANT_LIMIT_DELAY")
            public static class GRANT_LIMIT_DELAY extends CommandRef {
                public static final GRANT_LIMIT_DELAY cmd = new GRANT_LIMIT_DELAY();
            public GRANT_LIMIT_DELAY timediff(String value) {
                return set("timediff", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_ESCROW", field="MEMBER_CAN_ESCROW")
            public static class MEMBER_CAN_ESCROW extends CommandRef {
                public static final MEMBER_CAN_ESCROW cmd = new MEMBER_CAN_ESCROW();
            public MEMBER_CAN_ESCROW enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_OFFSHORE", field="MEMBER_CAN_OFFSHORE")
            public static class MEMBER_CAN_OFFSHORE extends CommandRef {
                public static final MEMBER_CAN_OFFSHORE cmd = new MEMBER_CAN_OFFSHORE();
            public MEMBER_CAN_OFFSHORE value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_WITHDRAW", field="MEMBER_CAN_WITHDRAW")
            public static class MEMBER_CAN_WITHDRAW extends CommandRef {
                public static final MEMBER_CAN_WITHDRAW cmd = new MEMBER_CAN_WITHDRAW();
            public MEMBER_CAN_WITHDRAW enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_WITHDRAW_WARTIME", field="MEMBER_CAN_WITHDRAW_WARTIME")
            public static class MEMBER_CAN_WITHDRAW_WARTIME extends CommandRef {
                public static final MEMBER_CAN_WITHDRAW_WARTIME cmd = new MEMBER_CAN_WITHDRAW_WARTIME();
            public MEMBER_CAN_WITHDRAW_WARTIME enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="NON_AA_MEMBERS_CAN_BANK", field="NO_DISCORD_CAN_BANK")
            public static class NON_AA_MEMBERS_CAN_BANK extends CommandRef {
                public static final NON_AA_MEMBERS_CAN_BANK cmd = new NON_AA_MEMBERS_CAN_BANK();
            public NON_AA_MEMBERS_CAN_BANK enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="PUBLIC_OFFSHORING", field="PUBLIC_OFFSHORING")
            public static class PUBLIC_OFFSHORING extends CommandRef {
                public static final PUBLIC_OFFSHORING cmd = new PUBLIC_OFFSHORING();
            public PUBLIC_OFFSHORING enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RESOURCE_CONVERSION", field="RESOURCE_CONVERSION")
            public static class RESOURCE_CONVERSION extends CommandRef {
                public static final RESOURCE_CONVERSION cmd = new RESOURCE_CONVERSION();
            public RESOURCE_CONVERSION enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ROUTE_ALLIANCE_BANK", field="ROUTE_ALLIANCE_BANK")
            public static class ROUTE_ALLIANCE_BANK extends CommandRef {
                public static final ROUTE_ALLIANCE_BANK cmd = new ROUTE_ALLIANCE_BANK();
            public ROUTE_ALLIANCE_BANK value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WITHDRAW_IGNORES_EXPIRE", field="MEMBER_CAN_WITHDRAW_IGNORES_GRANTS")
            public static class WITHDRAW_IGNORES_EXPIRE extends CommandRef {
                public static final WITHDRAW_IGNORES_EXPIRE cmd = new WITHDRAW_IGNORES_EXPIRE();
            public WITHDRAW_IGNORES_EXPIRE enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addGrantTemplateLimit", field="GRANT_TEMPLATE_LIMITS")
            public static class addGrantTemplateLimit extends CommandRef {
                public static final addGrantTemplateLimit cmd = new addGrantTemplateLimit();
            public addGrantTemplateLimit role(String value) {
                return set("role", value);
            }

            public addGrantTemplateLimit marketValue(String value) {
                return set("marketValue", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addResourceChannel", field="RESOURCE_REQUEST_CHANNEL")
            public static class addResourceChannel extends CommandRef {
                public static final addResourceChannel cmd = new addResourceChannel();
            public addResourceChannel channel(String value) {
                return set("channel", value);
            }

            public addResourceChannel alliance(String value) {
                return set("alliance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="removeResourceChannel", field="RESOURCE_REQUEST_CHANNEL")
            public static class removeResourceChannel extends CommandRef {
                public static final removeResourceChannel cmd = new removeResourceChannel();
            public removeResourceChannel channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="toggleGrants", field="GRANT_TEMPLATE_BLACKLIST")
            public static class toggleGrants extends CommandRef {
                public static final toggleGrants cmd = new toggleGrants();
            public toggleGrants nation(String value) {
                return set("nation", value);
            }

            }
        }
        public static class settings_bank_info{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ADDBALANCE_ALERT_CHANNEL", field="ADDBALANCE_ALERT_CHANNEL")
            public static class ADDBALANCE_ALERT_CHANNEL extends CommandRef {
                public static final ADDBALANCE_ALERT_CHANNEL cmd = new ADDBALANCE_ALERT_CHANNEL();
            public ADDBALANCE_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DEPOSIT_ALERT_CHANNEL", field="DEPOSIT_ALERT_CHANNEL")
            public static class DEPOSIT_ALERT_CHANNEL extends CommandRef {
                public static final DEPOSIT_ALERT_CHANNEL cmd = new DEPOSIT_ALERT_CHANNEL();
            public DEPOSIT_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DISPLAY_CONDENSED_DEPOSITS", field="DISPLAY_CONDENSED_DEPOSITS")
            public static class DISPLAY_CONDENSED_DEPOSITS extends CommandRef {
                public static final DISPLAY_CONDENSED_DEPOSITS cmd = new DISPLAY_CONDENSED_DEPOSITS();
            public DISPLAY_CONDENSED_DEPOSITS enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DISPLAY_ITEMIZED_DEPOSITS", field="DISPLAY_ITEMIZED_DEPOSITS")
            public static class DISPLAY_ITEMIZED_DEPOSITS extends CommandRef {
                public static final DISPLAY_ITEMIZED_DEPOSITS cmd = new DISPLAY_ITEMIZED_DEPOSITS();
            public DISPLAY_ITEMIZED_DEPOSITS enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="GRANT_REQUEST_CHANNEL", field="GRANT_REQUEST_CHANNEL")
            public static class GRANT_REQUEST_CHANNEL extends CommandRef {
                public static final GRANT_REQUEST_CHANNEL cmd = new GRANT_REQUEST_CHANNEL();
            public GRANT_REQUEST_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="LARGE_TRANSFERS_CHANNEL", field="LARGE_TRANSFERS_CHANNEL")
            public static class LARGE_TRANSFERS_CHANNEL extends CommandRef {
                public static final LARGE_TRANSFERS_CHANNEL cmd = new LARGE_TRANSFERS_CHANNEL();
            public LARGE_TRANSFERS_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WITHDRAW_ALERT_CHANNEL", field="WITHDRAW_ALERT_CHANNEL")
            public static class WITHDRAW_ALERT_CHANNEL extends CommandRef {
                public static final WITHDRAW_ALERT_CHANNEL cmd = new WITHDRAW_ALERT_CHANNEL();
            public WITHDRAW_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
        }
        public static class settings_beige_alerts{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BEIGE_ALERT_CHANNEL", field="BEIGE_ALERT_CHANNEL")
            public static class BEIGE_ALERT_CHANNEL extends CommandRef {
                public static final BEIGE_ALERT_CHANNEL cmd = new BEIGE_ALERT_CHANNEL();
            public BEIGE_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BEIGE_VIOLATION_MAIL", field="BEIGE_VIOLATION_MAIL")
            public static class BEIGE_VIOLATION_MAIL extends CommandRef {
                public static final BEIGE_VIOLATION_MAIL cmd = new BEIGE_VIOLATION_MAIL();
            public BEIGE_VIOLATION_MAIL value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_ALERT_CHANNEL", field="ENEMY_ALERT_CHANNEL")
            public static class ENEMY_ALERT_CHANNEL extends CommandRef {
                public static final ENEMY_ALERT_CHANNEL cmd = new ENEMY_ALERT_CHANNEL();
            public ENEMY_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_ALERT_CHANNEL_MODE", field="ENEMY_ALERT_CHANNEL_MODE")
            public static class ENEMY_ALERT_CHANNEL_MODE extends CommandRef {
                public static final ENEMY_ALERT_CHANNEL_MODE cmd = new ENEMY_ALERT_CHANNEL_MODE();
            public ENEMY_ALERT_CHANNEL_MODE mode(String value) {
                return set("mode", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_ALERT_FILTER", field="ENEMY_ALERT_FILTER")
            public static class ENEMY_ALERT_FILTER extends CommandRef {
                public static final ENEMY_ALERT_FILTER cmd = new ENEMY_ALERT_FILTER();
            public ENEMY_ALERT_FILTER filter(String value) {
                return set("filter", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_BEIGED_ALERT", field="ENEMY_BEIGED_ALERT")
            public static class ENEMY_BEIGED_ALERT extends CommandRef {
                public static final ENEMY_BEIGED_ALERT cmd = new ENEMY_BEIGED_ALERT();
            public ENEMY_BEIGED_ALERT channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_BEIGED_ALERT_VIOLATIONS", field="ENEMY_BEIGED_ALERT_VIOLATIONS")
            public static class ENEMY_BEIGED_ALERT_VIOLATIONS extends CommandRef {
                public static final ENEMY_BEIGED_ALERT_VIOLATIONS cmd = new ENEMY_BEIGED_ALERT_VIOLATIONS();
            public ENEMY_BEIGED_ALERT_VIOLATIONS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addBeigeReasons", field="ALLOWED_BEIGE_REASONS")
            public static class addBeigeReasons extends CommandRef {
                public static final addBeigeReasons cmd = new addBeigeReasons();
            public addBeigeReasons range(String value) {
                return set("range", value);
            }

            public addBeigeReasons reasons(String value) {
                return set("reasons", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="removeBeigeReasons", field="ALLOWED_BEIGE_REASONS")
            public static class removeBeigeReasons extends CommandRef {
                public static final removeBeigeReasons cmd = new removeBeigeReasons();
            public removeBeigeReasons range(String value) {
                return set("range", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="setBeigeReasons", field="ALLOWED_BEIGE_REASONS")
            public static class setBeigeReasons extends CommandRef {
                public static final setBeigeReasons cmd = new setBeigeReasons();
            public setBeigeReasons reasons(String value) {
                return set("reasons", value);
            }

            }
        }
        public static class settings_bounty{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BOUNTY_ALERT_CHANNEL", field="BOUNTY_ALERT_CHANNEL")
            public static class BOUNTY_ALERT_CHANNEL extends CommandRef {
                public static final BOUNTY_ALERT_CHANNEL cmd = new BOUNTY_ALERT_CHANNEL();
            public BOUNTY_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="TREASURE_ALERT_CHANNEL", field="TREASURE_ALERT_CHANNEL")
            public static class TREASURE_ALERT_CHANNEL extends CommandRef {
                public static final TREASURE_ALERT_CHANNEL cmd = new TREASURE_ALERT_CHANNEL();
            public TREASURE_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
        }
        public static class settings_default{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DELEGATE_SERVER", field="DELEGATE_SERVER")
            public static class DELEGATE_SERVER extends CommandRef {
                public static final DELEGATE_SERVER cmd = new DELEGATE_SERVER();
            public DELEGATE_SERVER guild(String value) {
                return set("guild", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="HIDE_LEGACY_NOTICE", field="HIDE_LEGACY_NOTICE")
            public static class HIDE_LEGACY_NOTICE extends CommandRef {
                public static final HIDE_LEGACY_NOTICE cmd = new HIDE_LEGACY_NOTICE();
            public HIDE_LEGACY_NOTICE value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="registerAlliance", field="ALLIANCE_ID")
            public static class registerAlliance extends CommandRef {
                public static final registerAlliance cmd = new registerAlliance();
            public registerAlliance alliances(String value) {
                return set("alliances", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="registerApiKey", field="API_KEY")
            public static class registerApiKey extends CommandRef {
                public static final registerApiKey cmd = new registerApiKey();
            public registerApiKey apiKeys(String value) {
                return set("apiKeys", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="unregisterAlliance", field="ALLIANCE_ID")
            public static class unregisterAlliance extends CommandRef {
                public static final unregisterAlliance cmd = new unregisterAlliance();
            public unregisterAlliance alliances(String value) {
                return set("alliances", value);
            }

            }
        }
        public static class settings_foreign_affairs{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ALLIANCE_CREATE_ALERTS", field="ALLIANCE_CREATE_ALERTS")
            public static class ALLIANCE_CREATE_ALERTS extends CommandRef {
                public static final ALLIANCE_CREATE_ALERTS cmd = new ALLIANCE_CREATE_ALERTS();
            public ALLIANCE_CREATE_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DO_NOT_RAID_TOP_X", field="DO_NOT_RAID_TOP_X")
            public static class DO_NOT_RAID_TOP_X extends CommandRef {
                public static final DO_NOT_RAID_TOP_X cmd = new DO_NOT_RAID_TOP_X();
            public DO_NOT_RAID_TOP_X topAllianceScore(String value) {
                return set("topAllianceScore", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="EMBASSY_CATEGORY", field="EMBASSY_CATEGORY")
            public static class EMBASSY_CATEGORY extends CommandRef {
                public static final EMBASSY_CATEGORY cmd = new EMBASSY_CATEGORY();
            public EMBASSY_CATEGORY category(String value) {
                return set("category", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="FA_SERVER", field="FA_SERVER")
            public static class FA_SERVER extends CommandRef {
                public static final FA_SERVER cmd = new FA_SERVER();
            public FA_SERVER guild(String value) {
                return set("guild", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="TREATY_ALERTS", field="TREATY_ALERTS")
            public static class TREATY_ALERTS extends CommandRef {
                public static final TREATY_ALERTS cmd = new TREATY_ALERTS();
            public TREATY_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
        }
        public static class settings_interview{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ARCHIVE_CATEGORY", field="ARCHIVE_CATEGORY")
            public static class ARCHIVE_CATEGORY extends CommandRef {
                public static final ARCHIVE_CATEGORY cmd = new ARCHIVE_CATEGORY();
            public ARCHIVE_CATEGORY category(String value) {
                return set("category", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="INTERVIEW_INFO_SPAM", field="INTERVIEW_INFO_SPAM")
            public static class INTERVIEW_INFO_SPAM extends CommandRef {
                public static final INTERVIEW_INFO_SPAM cmd = new INTERVIEW_INFO_SPAM();
            public INTERVIEW_INFO_SPAM channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="INTERVIEW_PENDING_ALERTS", field="INTERVIEW_PENDING_ALERTS")
            public static class INTERVIEW_PENDING_ALERTS extends CommandRef {
                public static final INTERVIEW_PENDING_ALERTS cmd = new INTERVIEW_PENDING_ALERTS();
            public INTERVIEW_PENDING_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
        }
        public static class settings_orbis_alerts{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AA_GROUND_TOP_X", field="AA_GROUND_TOP_X")
            public static class AA_GROUND_TOP_X extends CommandRef {
                public static final AA_GROUND_TOP_X cmd = new AA_GROUND_TOP_X();
            public AA_GROUND_TOP_X topX(String value) {
                return set("topX", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AA_GROUND_UNIT_ALERTS", field="AA_GROUND_UNIT_ALERTS")
            public static class AA_GROUND_UNIT_ALERTS extends CommandRef {
                public static final AA_GROUND_UNIT_ALERTS cmd = new AA_GROUND_UNIT_ALERTS();
            public AA_GROUND_UNIT_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ACTIVITY_ALERTS", field="ACTIVITY_ALERTS")
            public static class ACTIVITY_ALERTS extends CommandRef {
                public static final ACTIVITY_ALERTS cmd = new ACTIVITY_ALERTS();
            public ACTIVITY_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ALLIANCE_EXODUS_TOP_X", field="ALLIANCE_EXODUS_TOP_X")
            public static class ALLIANCE_EXODUS_TOP_X extends CommandRef {
                public static final ALLIANCE_EXODUS_TOP_X cmd = new ALLIANCE_EXODUS_TOP_X();
            public ALLIANCE_EXODUS_TOP_X rank(String value) {
                return set("rank", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BAN_ALERT_CHANNEL", field="BAN_ALERT_CHANNEL")
            public static class BAN_ALERT_CHANNEL extends CommandRef {
                public static final BAN_ALERT_CHANNEL cmd = new BAN_ALERT_CHANNEL();
            public BAN_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DELETION_ALERT_CHANNEL", field="DELETION_ALERT_CHANNEL")
            public static class DELETION_ALERT_CHANNEL extends CommandRef {
                public static final DELETION_ALERT_CHANNEL cmd = new DELETION_ALERT_CHANNEL();
            public DELETION_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ESCALATION_ALERTS", field="ESCALATION_ALERTS")
            public static class ESCALATION_ALERTS extends CommandRef {
                public static final ESCALATION_ALERTS cmd = new ESCALATION_ALERTS();
            public ESCALATION_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ORBIS_ALLIANCE_EXODUS_ALERTS", field="ORBIS_ALLIANCE_EXODUS_ALERTS")
            public static class ORBIS_ALLIANCE_EXODUS_ALERTS extends CommandRef {
                public static final ORBIS_ALLIANCE_EXODUS_ALERTS cmd = new ORBIS_ALLIANCE_EXODUS_ALERTS();
            public ORBIS_ALLIANCE_EXODUS_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ORBIS_LEADER_CHANGE_ALERT", field="ORBIS_LEADER_CHANGE_ALERT")
            public static class ORBIS_LEADER_CHANGE_ALERT extends CommandRef {
                public static final ORBIS_LEADER_CHANGE_ALERT cmd = new ORBIS_LEADER_CHANGE_ALERT();
            public ORBIS_LEADER_CHANGE_ALERT channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ORBIS_OFFICER_LEAVE_ALERTS", field="ORBIS_OFFICER_LEAVE_ALERTS")
            public static class ORBIS_OFFICER_LEAVE_ALERTS extends CommandRef {
                public static final ORBIS_OFFICER_LEAVE_ALERTS cmd = new ORBIS_OFFICER_LEAVE_ALERTS();
            public ORBIS_OFFICER_LEAVE_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ORBIS_OFFICER_MMR_CHANGE_ALERTS", field="ORBIS_OFFICER_MMR_CHANGE_ALERTS")
            public static class ORBIS_OFFICER_MMR_CHANGE_ALERTS extends CommandRef {
                public static final ORBIS_OFFICER_MMR_CHANGE_ALERTS cmd = new ORBIS_OFFICER_MMR_CHANGE_ALERTS();
            public ORBIS_OFFICER_MMR_CHANGE_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REPORT_ALERT_CHANNEL", field="REPORT_ALERT_CHANNEL")
            public static class REPORT_ALERT_CHANNEL extends CommandRef {
                public static final REPORT_ALERT_CHANNEL cmd = new REPORT_ALERT_CHANNEL();
            public REPORT_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REROLL_ALERT_CHANNEL", field="REROLL_ALERT_CHANNEL")
            public static class REROLL_ALERT_CHANNEL extends CommandRef {
                public static final REROLL_ALERT_CHANNEL cmd = new REROLL_ALERT_CHANNEL();
            public REROLL_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="VM_ALERT_CHANNEL", field="VM_ALERT_CHANNEL")
            public static class VM_ALERT_CHANNEL extends CommandRef {
                public static final VM_ALERT_CHANNEL cmd = new VM_ALERT_CHANNEL();
            public VM_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
        }
        public static class settings_recruit{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MAIL_NEW_APPLICANTS", field="MAIL_NEW_APPLICANTS")
            public static class MAIL_NEW_APPLICANTS extends CommandRef {
                public static final MAIL_NEW_APPLICANTS cmd = new MAIL_NEW_APPLICANTS();
            public MAIL_NEW_APPLICANTS enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MAIL_NEW_APPLICANTS_TEXT", field="MAIL_NEW_APPLICANTS_TEXT")
            public static class MAIL_NEW_APPLICANTS_TEXT extends CommandRef {
                public static final MAIL_NEW_APPLICANTS_TEXT cmd = new MAIL_NEW_APPLICANTS_TEXT();
            public MAIL_NEW_APPLICANTS_TEXT message(String value) {
                return set("message", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RECRUIT_MESSAGE_CONTENT", field="RECRUIT_MESSAGE_CONTENT")
            public static class RECRUIT_MESSAGE_CONTENT extends CommandRef {
                public static final RECRUIT_MESSAGE_CONTENT cmd = new RECRUIT_MESSAGE_CONTENT();
            public RECRUIT_MESSAGE_CONTENT message(String value) {
                return set("message", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RECRUIT_MESSAGE_DELAY", field="RECRUIT_MESSAGE_DELAY")
            public static class RECRUIT_MESSAGE_DELAY extends CommandRef {
                public static final RECRUIT_MESSAGE_DELAY cmd = new RECRUIT_MESSAGE_DELAY();
            public RECRUIT_MESSAGE_DELAY timediff(String value) {
                return set("timediff", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RECRUIT_MESSAGE_OUTPUT", field="RECRUIT_MESSAGE_OUTPUT")
            public static class RECRUIT_MESSAGE_OUTPUT extends CommandRef {
                public static final RECRUIT_MESSAGE_OUTPUT cmd = new RECRUIT_MESSAGE_OUTPUT();
            public RECRUIT_MESSAGE_OUTPUT channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="RECRUIT_MESSAGE_SUBJECT", field="RECRUIT_MESSAGE_SUBJECT")
            public static class RECRUIT_MESSAGE_SUBJECT extends CommandRef {
                public static final RECRUIT_MESSAGE_SUBJECT cmd = new RECRUIT_MESSAGE_SUBJECT();
            public RECRUIT_MESSAGE_SUBJECT value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="add_timed_message", field="TIMED_MESSAGES")
            public static class add_timed_message extends CommandRef {
                public static final add_timed_message cmd = new add_timed_message();
            public add_timed_message timeDelay(String value) {
                return set("timeDelay", value);
            }

            public add_timed_message subject(String value) {
                return set("subject", value);
            }

            public add_timed_message message(String value) {
                return set("message", value);
            }

            public add_timed_message trigger(String value) {
                return set("trigger", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="remove_timed_message", field="TIMED_MESSAGES")
            public static class remove_timed_message extends CommandRef {
                public static final remove_timed_message cmd = new remove_timed_message();
            public remove_timed_message trigger(String value) {
                return set("trigger", value);
            }

            public remove_timed_message timeDelay(String value) {
                return set("timeDelay", value);
            }

            }
        }
        public static class settings_reward{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REWARD_MENTOR", field="REWARD_MENTOR")
            public static class REWARD_MENTOR extends CommandRef {
                public static final REWARD_MENTOR cmd = new REWARD_MENTOR();
            public REWARD_MENTOR amount(String value) {
                return set("amount", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REWARD_REFERRAL", field="REWARD_REFERRAL")
            public static class REWARD_REFERRAL extends CommandRef {
                public static final REWARD_REFERRAL cmd = new REWARD_REFERRAL();
            public REWARD_REFERRAL amount(String value) {
                return set("amount", value);
            }

            }
        }
        public static class settings_role{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ASSIGNABLE_ROLES", field="ASSIGNABLE_ROLES")
            public static class ASSIGNABLE_ROLES extends CommandRef {
                public static final ASSIGNABLE_ROLES cmd = new ASSIGNABLE_ROLES();
            public ASSIGNABLE_ROLES value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTONICK", field="AUTONICK")
            public static class AUTONICK extends CommandRef {
                public static final AUTONICK cmd = new AUTONICK();
            public AUTONICK mode(String value) {
                return set("mode", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_ALLIANCES", field="AUTOROLE_ALLIANCES")
            public static class AUTOROLE_ALLIANCES extends CommandRef {
                public static final AUTOROLE_ALLIANCES cmd = new AUTOROLE_ALLIANCES();
            public AUTOROLE_ALLIANCES mode(String value) {
                return set("mode", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_ALLIANCE_RANK", field="AUTOROLE_ALLIANCE_RANK")
            public static class AUTOROLE_ALLIANCE_RANK extends CommandRef {
                public static final AUTOROLE_ALLIANCE_RANK cmd = new AUTOROLE_ALLIANCE_RANK();
            public AUTOROLE_ALLIANCE_RANK allianceRank(String value) {
                return set("allianceRank", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_ALLY_GOV", field="AUTOROLE_ALLY_GOV")
            public static class AUTOROLE_ALLY_GOV extends CommandRef {
                public static final AUTOROLE_ALLY_GOV cmd = new AUTOROLE_ALLY_GOV();
            public AUTOROLE_ALLY_GOV enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_ALLY_ROLES", field="AUTOROLE_ALLY_ROLES")
            public static class AUTOROLE_ALLY_ROLES extends CommandRef {
                public static final AUTOROLE_ALLY_ROLES cmd = new AUTOROLE_ALLY_ROLES();
            public AUTOROLE_ALLY_ROLES roles(String value) {
                return set("roles", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_MEMBER_APPS", field="AUTOROLE_MEMBER_APPS")
            public static class AUTOROLE_MEMBER_APPS extends CommandRef {
                public static final AUTOROLE_MEMBER_APPS cmd = new AUTOROLE_MEMBER_APPS();
            public AUTOROLE_MEMBER_APPS enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="AUTOROLE_TOP_X", field="AUTOROLE_TOP_X")
            public static class AUTOROLE_TOP_X extends CommandRef {
                public static final AUTOROLE_TOP_X cmd = new AUTOROLE_TOP_X();
            public AUTOROLE_TOP_X topScoreRank(String value) {
                return set("topScoreRank", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="CONDITIONAL_ROLES", field="CONDITIONAL_ROLES")
            public static class CONDITIONAL_ROLES extends CommandRef {
                public static final CONDITIONAL_ROLES cmd = new CONDITIONAL_ROLES();
            public CONDITIONAL_ROLES roleMap(String value) {
                return set("roleMap", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addAssignableRole", field="ASSIGNABLE_ROLES")
            public static class addAssignableRole extends CommandRef {
                public static final addAssignableRole cmd = new addAssignableRole();
            public addAssignableRole role(String value) {
                return set("role", value);
            }

            public addAssignableRole roles(String value) {
                return set("roles", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addConditionalRole", field="CONDITIONAL_ROLES")
            public static class addConditionalRole extends CommandRef {
                public static final addConditionalRole cmd = new addConditionalRole();
            public addConditionalRole filter(String value) {
                return set("filter", value);
            }

            public addConditionalRole role(String value) {
                return set("role", value);
            }

            }
        }
        public static class settings_sheet{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="listSheetKeys")
            public static class list extends CommandRef {
                public static final list cmd = new list();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="setSheetKey")
            public static class set extends CommandRef {
                public static final set cmd = new set();
            public set key(String value) {
                return set("key", value);
            }

            public set sheetId(String value) {
                return set("sheetId", value);
            }

            public set tab(String value) {
                return set("tab", value);
            }

            }
        }
        public static class settings_tax{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ALLOWED_TAX_BRACKETS", field="ALLOWED_TAX_BRACKETS")
            public static class ALLOWED_TAX_BRACKETS extends CommandRef {
                public static final ALLOWED_TAX_BRACKETS cmd = new ALLOWED_TAX_BRACKETS();
            public ALLOWED_TAX_BRACKETS brackets(String value) {
                return set("brackets", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MEMBER_CAN_SET_BRACKET", field="MEMBER_CAN_SET_BRACKET")
            public static class MEMBER_CAN_SET_BRACKET extends CommandRef {
                public static final MEMBER_CAN_SET_BRACKET cmd = new MEMBER_CAN_SET_BRACKET();
            public MEMBER_CAN_SET_BRACKET value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REQUIRED_INTERNAL_TAXRATE", field="REQUIRED_INTERNAL_TAXRATE")
            public static class REQUIRED_INTERNAL_TAXRATE extends CommandRef {
                public static final REQUIRED_INTERNAL_TAXRATE cmd = new REQUIRED_INTERNAL_TAXRATE();
            public REQUIRED_INTERNAL_TAXRATE value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="REQUIRED_TAX_BRACKET", field="REQUIRED_TAX_BRACKET")
            public static class REQUIRED_TAX_BRACKET extends CommandRef {
                public static final REQUIRED_TAX_BRACKET cmd = new REQUIRED_TAX_BRACKET();
            public REQUIRED_TAX_BRACKET brackets(String value) {
                return set("brackets", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="TAX_BASE", field="TAX_BASE")
            public static class TAX_BASE extends CommandRef {
                public static final TAX_BASE cmd = new TAX_BASE();
            public TAX_BASE taxRate(String value) {
                return set("taxRate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addRequiredBracket", field="REQUIRED_TAX_BRACKET")
            public static class addRequiredBracket extends CommandRef {
                public static final addRequiredBracket cmd = new addRequiredBracket();
            public addRequiredBracket filter(String value) {
                return set("filter", value);
            }

            public addRequiredBracket bracket(String value) {
                return set("bracket", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="addRequiredInternalTaxrate", field="REQUIRED_INTERNAL_TAXRATE")
            public static class addRequiredInternalTaxrate extends CommandRef {
                public static final addRequiredInternalTaxrate cmd = new addRequiredInternalTaxrate();
            public addRequiredInternalTaxrate filter(String value) {
                return set("filter", value);
            }

            public addRequiredInternalTaxrate bracket(String value) {
                return set("bracket", value);
            }

            }
        }
        public static class settings_trade{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="TRADE_ALERT_CHANNEL", field="TRADE_ALERT_CHANNEL")
            public static class TRADE_ALERT_CHANNEL extends CommandRef {
                public static final TRADE_ALERT_CHANNEL cmd = new TRADE_ALERT_CHANNEL();
            public TRADE_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
        }
        public static class settings_war_alerts{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="BLOCKADED_ALERTS", field="BLOCKADED_ALERTS")
            public static class BLOCKADED_ALERTS extends CommandRef {
                public static final BLOCKADED_ALERTS cmd = new BLOCKADED_ALERTS();
            public BLOCKADED_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="DEFENSE_WAR_CHANNEL", field="DEFENSE_WAR_CHANNEL")
            public static class DEFENSE_WAR_CHANNEL extends CommandRef {
                public static final DEFENSE_WAR_CHANNEL cmd = new DEFENSE_WAR_CHANNEL();
            public DEFENSE_WAR_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENEMY_MMR_CHANGE_ALERTS", field="ENEMY_MMR_CHANGE_ALERTS")
            public static class ENEMY_MMR_CHANGE_ALERTS extends CommandRef {
                public static final ENEMY_MMR_CHANGE_ALERTS cmd = new ENEMY_MMR_CHANGE_ALERTS();
            public ENEMY_MMR_CHANGE_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ESPIONAGE_ALERT_CHANNEL", field="ESPIONAGE_ALERT_CHANNEL")
            public static class ESPIONAGE_ALERT_CHANNEL extends CommandRef {
                public static final ESPIONAGE_ALERT_CHANNEL cmd = new ESPIONAGE_ALERT_CHANNEL();
            public ESPIONAGE_ALERT_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="HIDE_APPLICANT_WARS", field="HIDE_APPLICANT_WARS")
            public static class HIDE_APPLICANT_WARS extends CommandRef {
                public static final HIDE_APPLICANT_WARS cmd = new HIDE_APPLICANT_WARS();
            public HIDE_APPLICANT_WARS value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="LOST_WAR_CHANNEL", field="LOST_WAR_CHANNEL")
            public static class LOST_WAR_CHANNEL extends CommandRef {
                public static final LOST_WAR_CHANNEL cmd = new LOST_WAR_CHANNEL();
            public LOST_WAR_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MENTION_MILCOM_COUNTERS", field="MENTION_MILCOM_COUNTERS")
            public static class MENTION_MILCOM_COUNTERS extends CommandRef {
                public static final MENTION_MILCOM_COUNTERS cmd = new MENTION_MILCOM_COUNTERS();
            public MENTION_MILCOM_COUNTERS value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="MENTION_MILCOM_FILTER", field="MENTION_MILCOM_FILTER")
            public static class MENTION_MILCOM_FILTER extends CommandRef {
                public static final MENTION_MILCOM_FILTER cmd = new MENTION_MILCOM_FILTER();
            public MENTION_MILCOM_FILTER value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="OFFENSIVE_WAR_CHANNEL", field="OFFENSIVE_WAR_CHANNEL")
            public static class OFFENSIVE_WAR_CHANNEL extends CommandRef {
                public static final OFFENSIVE_WAR_CHANNEL cmd = new OFFENSIVE_WAR_CHANNEL();
            public OFFENSIVE_WAR_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="SHOW_ALLY_DEFENSIVE_WARS", field="SHOW_ALLY_DEFENSIVE_WARS")
            public static class SHOW_ALLY_DEFENSIVE_WARS extends CommandRef {
                public static final SHOW_ALLY_DEFENSIVE_WARS cmd = new SHOW_ALLY_DEFENSIVE_WARS();
            public SHOW_ALLY_DEFENSIVE_WARS enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="SHOW_ALLY_OFFENSIVE_WARS", field="SHOW_ALLY_OFFENSIVE_WARS")
            public static class SHOW_ALLY_OFFENSIVE_WARS extends CommandRef {
                public static final SHOW_ALLY_OFFENSIVE_WARS cmd = new SHOW_ALLY_OFFENSIVE_WARS();
            public SHOW_ALLY_OFFENSIVE_WARS enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="UNBLOCKADED_ALERTS", field="UNBLOCKADED_ALERTS")
            public static class UNBLOCKADED_ALERTS extends CommandRef {
                public static final UNBLOCKADED_ALERTS cmd = new UNBLOCKADED_ALERTS();
            public UNBLOCKADED_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="UNBLOCKADE_REQUESTS", field="UNBLOCKADE_REQUESTS")
            public static class UNBLOCKADE_REQUESTS extends CommandRef {
                public static final UNBLOCKADE_REQUESTS cmd = new UNBLOCKADE_REQUESTS();
            public UNBLOCKADE_REQUESTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_ALERT_FOR_OFFSHORES", field="WAR_ALERT_FOR_OFFSHORES")
            public static class WAR_ALERT_FOR_OFFSHORES extends CommandRef {
                public static final WAR_ALERT_FOR_OFFSHORES cmd = new WAR_ALERT_FOR_OFFSHORES();
            public WAR_ALERT_FOR_OFFSHORES enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_PEACE_ALERTS", field="WAR_PEACE_ALERTS")
            public static class WAR_PEACE_ALERTS extends CommandRef {
                public static final WAR_PEACE_ALERTS cmd = new WAR_PEACE_ALERTS();
            public WAR_PEACE_ALERTS channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_ROOM_FILTER", field="WAR_ROOM_FILTER")
            public static class WAR_ROOM_FILTER extends CommandRef {
                public static final WAR_ROOM_FILTER cmd = new WAR_ROOM_FILTER();
            public WAR_ROOM_FILTER value(String value) {
                return set("value", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WON_WAR_CHANNEL", field="WON_WAR_CHANNEL")
            public static class WON_WAR_CHANNEL extends CommandRef {
                public static final WON_WAR_CHANNEL cmd = new WON_WAR_CHANNEL();
            public WON_WAR_CHANNEL channel(String value) {
                return set("channel", value);
            }

            }
        }
        public static class settings_war_room{
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="ENABLE_WAR_ROOMS", field="ENABLE_WAR_ROOMS")
            public static class ENABLE_WAR_ROOMS extends CommandRef {
                public static final ENABLE_WAR_ROOMS cmd = new ENABLE_WAR_ROOMS();
            public ENABLE_WAR_ROOMS enabled(String value) {
                return set("enabled", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_ROOM_LOG", field="WAR_ROOM_LOG")
            public static class WAR_ROOM_LOG extends CommandRef {
                public static final WAR_ROOM_LOG cmd = new WAR_ROOM_LOG();
            public WAR_ROOM_LOG channel(String value) {
                return set("channel", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.db.guild.GuildKey.class,method="WAR_SERVER", field="WAR_SERVER")
            public static class WAR_SERVER extends CommandRef {
                public static final WAR_SERVER cmd = new WAR_SERVER();
            public WAR_SERVER guild(String value) {
                return set("guild", value);
            }

            }
        }
        public static class sheet_custom{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="addTab")
            public static class add_tab extends CommandRef {
                public static final add_tab cmd = new add_tab();
            public add_tab sheet(String value) {
                return set("sheet", value);
            }

            public add_tab tabName(String value) {
                return set("tabName", value);
            }

            public add_tab select(String value) {
                return set("select", value);
            }

            public add_tab columns(String value) {
                return set("columns", value);
            }

            public add_tab force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="auto")
            public static class auto extends CommandRef {
                public static final auto cmd = new auto();
            public auto sheet(String value) {
                return set("sheet", value);
            }

            public auto saveSheet(String value) {
                return set("saveSheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="fromFile")
            public static class from_file extends CommandRef {
                public static final from_file cmd = new from_file();
            public from_file message(String value) {
                return set("message", value);
            }

            public from_file sheet(String value) {
                return set("sheet", value);
            }

            public from_file index(String value) {
                return set("index", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="listCustomSheets")
            public static class list extends CommandRef {
                public static final list cmd = new list();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="deleteTab")
            public static class remove_tab extends CommandRef {
                public static final remove_tab cmd = new remove_tab();
            public remove_tab sheet(String value) {
                return set("sheet", value);
            }

            public remove_tab tabName(String value) {
                return set("tabName", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="updateSheet")
            public static class update extends CommandRef {
                public static final update cmd = new update();
            public update sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="info")
            public static class view extends CommandRef {
                public static final view cmd = new view();
            public view sheet(String value) {
                return set("sheet", value);
            }

            }
        }
        public static class sheet_template{
            public static class add{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="ALLIANCES")
                public static class alliance extends CommandRef {
                    public static final alliance cmd = new alliance();
                public alliance sheet(String value) {
                    return set("sheet", value);
                }

                public alliance a(String value) {
                    return set("a", value);
                }

                public alliance b(String value) {
                    return set("b", value);
                }

                public alliance c(String value) {
                    return set("c", value);
                }

                public alliance d(String value) {
                    return set("d", value);
                }

                public alliance e(String value) {
                    return set("e", value);
                }

                public alliance f(String value) {
                    return set("f", value);
                }

                public alliance g(String value) {
                    return set("g", value);
                }

                public alliance h(String value) {
                    return set("h", value);
                }

                public alliance i(String value) {
                    return set("i", value);
                }

                public alliance j(String value) {
                    return set("j", value);
                }

                public alliance k(String value) {
                    return set("k", value);
                }

                public alliance l(String value) {
                    return set("l", value);
                }

                public alliance m(String value) {
                    return set("m", value);
                }

                public alliance n(String value) {
                    return set("n", value);
                }

                public alliance o(String value) {
                    return set("o", value);
                }

                public alliance p(String value) {
                    return set("p", value);
                }

                public alliance q(String value) {
                    return set("q", value);
                }

                public alliance r(String value) {
                    return set("r", value);
                }

                public alliance s(String value) {
                    return set("s", value);
                }

                public alliance t(String value) {
                    return set("t", value);
                }

                public alliance u(String value) {
                    return set("u", value);
                }

                public alliance v(String value) {
                    return set("v", value);
                }

                public alliance w(String value) {
                    return set("w", value);
                }

                public alliance x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="ATTACK_TYPES")
                public static class attacktype extends CommandRef {
                    public static final attacktype cmd = new attacktype();
                public attacktype sheet(String value) {
                    return set("sheet", value);
                }

                public attacktype a(String value) {
                    return set("a", value);
                }

                public attacktype b(String value) {
                    return set("b", value);
                }

                public attacktype c(String value) {
                    return set("c", value);
                }

                public attacktype d(String value) {
                    return set("d", value);
                }

                public attacktype e(String value) {
                    return set("e", value);
                }

                public attacktype f(String value) {
                    return set("f", value);
                }

                public attacktype g(String value) {
                    return set("g", value);
                }

                public attacktype h(String value) {
                    return set("h", value);
                }

                public attacktype i(String value) {
                    return set("i", value);
                }

                public attacktype j(String value) {
                    return set("j", value);
                }

                public attacktype k(String value) {
                    return set("k", value);
                }

                public attacktype l(String value) {
                    return set("l", value);
                }

                public attacktype m(String value) {
                    return set("m", value);
                }

                public attacktype n(String value) {
                    return set("n", value);
                }

                public attacktype o(String value) {
                    return set("o", value);
                }

                public attacktype p(String value) {
                    return set("p", value);
                }

                public attacktype q(String value) {
                    return set("q", value);
                }

                public attacktype r(String value) {
                    return set("r", value);
                }

                public attacktype s(String value) {
                    return set("s", value);
                }

                public attacktype t(String value) {
                    return set("t", value);
                }

                public attacktype u(String value) {
                    return set("u", value);
                }

                public attacktype v(String value) {
                    return set("v", value);
                }

                public attacktype w(String value) {
                    return set("w", value);
                }

                public attacktype x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="AUDIT_TYPES")
                public static class audittype extends CommandRef {
                    public static final audittype cmd = new audittype();
                public audittype sheet(String value) {
                    return set("sheet", value);
                }

                public audittype a(String value) {
                    return set("a", value);
                }

                public audittype b(String value) {
                    return set("b", value);
                }

                public audittype c(String value) {
                    return set("c", value);
                }

                public audittype d(String value) {
                    return set("d", value);
                }

                public audittype e(String value) {
                    return set("e", value);
                }

                public audittype f(String value) {
                    return set("f", value);
                }

                public audittype g(String value) {
                    return set("g", value);
                }

                public audittype h(String value) {
                    return set("h", value);
                }

                public audittype i(String value) {
                    return set("i", value);
                }

                public audittype j(String value) {
                    return set("j", value);
                }

                public audittype k(String value) {
                    return set("k", value);
                }

                public audittype l(String value) {
                    return set("l", value);
                }

                public audittype m(String value) {
                    return set("m", value);
                }

                public audittype n(String value) {
                    return set("n", value);
                }

                public audittype o(String value) {
                    return set("o", value);
                }

                public audittype p(String value) {
                    return set("p", value);
                }

                public audittype q(String value) {
                    return set("q", value);
                }

                public audittype r(String value) {
                    return set("r", value);
                }

                public audittype s(String value) {
                    return set("s", value);
                }

                public audittype t(String value) {
                    return set("t", value);
                }

                public audittype u(String value) {
                    return set("u", value);
                }

                public audittype v(String value) {
                    return set("v", value);
                }

                public audittype w(String value) {
                    return set("w", value);
                }

                public audittype x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="BANS")
                public static class ban extends CommandRef {
                    public static final ban cmd = new ban();
                public ban sheet(String value) {
                    return set("sheet", value);
                }

                public ban a(String value) {
                    return set("a", value);
                }

                public ban b(String value) {
                    return set("b", value);
                }

                public ban c(String value) {
                    return set("c", value);
                }

                public ban d(String value) {
                    return set("d", value);
                }

                public ban e(String value) {
                    return set("e", value);
                }

                public ban f(String value) {
                    return set("f", value);
                }

                public ban g(String value) {
                    return set("g", value);
                }

                public ban h(String value) {
                    return set("h", value);
                }

                public ban i(String value) {
                    return set("i", value);
                }

                public ban j(String value) {
                    return set("j", value);
                }

                public ban k(String value) {
                    return set("k", value);
                }

                public ban l(String value) {
                    return set("l", value);
                }

                public ban m(String value) {
                    return set("m", value);
                }

                public ban n(String value) {
                    return set("n", value);
                }

                public ban o(String value) {
                    return set("o", value);
                }

                public ban p(String value) {
                    return set("p", value);
                }

                public ban q(String value) {
                    return set("q", value);
                }

                public ban r(String value) {
                    return set("r", value);
                }

                public ban s(String value) {
                    return set("s", value);
                }

                public ban t(String value) {
                    return set("t", value);
                }

                public ban u(String value) {
                    return set("u", value);
                }

                public ban v(String value) {
                    return set("v", value);
                }

                public ban w(String value) {
                    return set("w", value);
                }

                public ban x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="BOUNTIES")
                public static class bounty extends CommandRef {
                    public static final bounty cmd = new bounty();
                public bounty sheet(String value) {
                    return set("sheet", value);
                }

                public bounty a(String value) {
                    return set("a", value);
                }

                public bounty b(String value) {
                    return set("b", value);
                }

                public bounty c(String value) {
                    return set("c", value);
                }

                public bounty d(String value) {
                    return set("d", value);
                }

                public bounty e(String value) {
                    return set("e", value);
                }

                public bounty f(String value) {
                    return set("f", value);
                }

                public bounty g(String value) {
                    return set("g", value);
                }

                public bounty h(String value) {
                    return set("h", value);
                }

                public bounty i(String value) {
                    return set("i", value);
                }

                public bounty j(String value) {
                    return set("j", value);
                }

                public bounty k(String value) {
                    return set("k", value);
                }

                public bounty l(String value) {
                    return set("l", value);
                }

                public bounty m(String value) {
                    return set("m", value);
                }

                public bounty n(String value) {
                    return set("n", value);
                }

                public bounty o(String value) {
                    return set("o", value);
                }

                public bounty p(String value) {
                    return set("p", value);
                }

                public bounty q(String value) {
                    return set("q", value);
                }

                public bounty r(String value) {
                    return set("r", value);
                }

                public bounty s(String value) {
                    return set("s", value);
                }

                public bounty t(String value) {
                    return set("t", value);
                }

                public bounty u(String value) {
                    return set("u", value);
                }

                public bounty v(String value) {
                    return set("v", value);
                }

                public bounty w(String value) {
                    return set("w", value);
                }

                public bounty x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="BUILDINGS")
                public static class building extends CommandRef {
                    public static final building cmd = new building();
                public building sheet(String value) {
                    return set("sheet", value);
                }

                public building a(String value) {
                    return set("a", value);
                }

                public building b(String value) {
                    return set("b", value);
                }

                public building c(String value) {
                    return set("c", value);
                }

                public building d(String value) {
                    return set("d", value);
                }

                public building e(String value) {
                    return set("e", value);
                }

                public building f(String value) {
                    return set("f", value);
                }

                public building g(String value) {
                    return set("g", value);
                }

                public building h(String value) {
                    return set("h", value);
                }

                public building i(String value) {
                    return set("i", value);
                }

                public building j(String value) {
                    return set("j", value);
                }

                public building k(String value) {
                    return set("k", value);
                }

                public building l(String value) {
                    return set("l", value);
                }

                public building m(String value) {
                    return set("m", value);
                }

                public building n(String value) {
                    return set("n", value);
                }

                public building o(String value) {
                    return set("o", value);
                }

                public building p(String value) {
                    return set("p", value);
                }

                public building q(String value) {
                    return set("q", value);
                }

                public building r(String value) {
                    return set("r", value);
                }

                public building s(String value) {
                    return set("s", value);
                }

                public building t(String value) {
                    return set("t", value);
                }

                public building u(String value) {
                    return set("u", value);
                }

                public building v(String value) {
                    return set("v", value);
                }

                public building w(String value) {
                    return set("w", value);
                }

                public building x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="CITIES")
                public static class city extends CommandRef {
                    public static final city cmd = new city();
                public city sheet(String value) {
                    return set("sheet", value);
                }

                public city a(String value) {
                    return set("a", value);
                }

                public city b(String value) {
                    return set("b", value);
                }

                public city c(String value) {
                    return set("c", value);
                }

                public city d(String value) {
                    return set("d", value);
                }

                public city e(String value) {
                    return set("e", value);
                }

                public city f(String value) {
                    return set("f", value);
                }

                public city g(String value) {
                    return set("g", value);
                }

                public city h(String value) {
                    return set("h", value);
                }

                public city i(String value) {
                    return set("i", value);
                }

                public city j(String value) {
                    return set("j", value);
                }

                public city k(String value) {
                    return set("k", value);
                }

                public city l(String value) {
                    return set("l", value);
                }

                public city m(String value) {
                    return set("m", value);
                }

                public city n(String value) {
                    return set("n", value);
                }

                public city o(String value) {
                    return set("o", value);
                }

                public city p(String value) {
                    return set("p", value);
                }

                public city q(String value) {
                    return set("q", value);
                }

                public city r(String value) {
                    return set("r", value);
                }

                public city s(String value) {
                    return set("s", value);
                }

                public city t(String value) {
                    return set("t", value);
                }

                public city u(String value) {
                    return set("u", value);
                }

                public city v(String value) {
                    return set("v", value);
                }

                public city w(String value) {
                    return set("w", value);
                }

                public city x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="CONTINENTS")
                public static class continent extends CommandRef {
                    public static final continent cmd = new continent();
                public continent sheet(String value) {
                    return set("sheet", value);
                }

                public continent a(String value) {
                    return set("a", value);
                }

                public continent b(String value) {
                    return set("b", value);
                }

                public continent c(String value) {
                    return set("c", value);
                }

                public continent d(String value) {
                    return set("d", value);
                }

                public continent e(String value) {
                    return set("e", value);
                }

                public continent f(String value) {
                    return set("f", value);
                }

                public continent g(String value) {
                    return set("g", value);
                }

                public continent h(String value) {
                    return set("h", value);
                }

                public continent i(String value) {
                    return set("i", value);
                }

                public continent j(String value) {
                    return set("j", value);
                }

                public continent k(String value) {
                    return set("k", value);
                }

                public continent l(String value) {
                    return set("l", value);
                }

                public continent m(String value) {
                    return set("m", value);
                }

                public continent n(String value) {
                    return set("n", value);
                }

                public continent o(String value) {
                    return set("o", value);
                }

                public continent p(String value) {
                    return set("p", value);
                }

                public continent q(String value) {
                    return set("q", value);
                }

                public continent r(String value) {
                    return set("r", value);
                }

                public continent s(String value) {
                    return set("s", value);
                }

                public continent t(String value) {
                    return set("t", value);
                }

                public continent u(String value) {
                    return set("u", value);
                }

                public continent v(String value) {
                    return set("v", value);
                }

                public continent w(String value) {
                    return set("w", value);
                }

                public continent x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="GUILDS")
                public static class guild extends CommandRef {
                    public static final guild cmd = new guild();
                public guild sheet(String value) {
                    return set("sheet", value);
                }

                public guild a(String value) {
                    return set("a", value);
                }

                public guild b(String value) {
                    return set("b", value);
                }

                public guild c(String value) {
                    return set("c", value);
                }

                public guild d(String value) {
                    return set("d", value);
                }

                public guild e(String value) {
                    return set("e", value);
                }

                public guild f(String value) {
                    return set("f", value);
                }

                public guild g(String value) {
                    return set("g", value);
                }

                public guild h(String value) {
                    return set("h", value);
                }

                public guild i(String value) {
                    return set("i", value);
                }

                public guild j(String value) {
                    return set("j", value);
                }

                public guild k(String value) {
                    return set("k", value);
                }

                public guild l(String value) {
                    return set("l", value);
                }

                public guild m(String value) {
                    return set("m", value);
                }

                public guild n(String value) {
                    return set("n", value);
                }

                public guild o(String value) {
                    return set("o", value);
                }

                public guild p(String value) {
                    return set("p", value);
                }

                public guild q(String value) {
                    return set("q", value);
                }

                public guild r(String value) {
                    return set("r", value);
                }

                public guild s(String value) {
                    return set("s", value);
                }

                public guild t(String value) {
                    return set("t", value);
                }

                public guild u(String value) {
                    return set("u", value);
                }

                public guild v(String value) {
                    return set("v", value);
                }

                public guild w(String value) {
                    return set("w", value);
                }

                public guild x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="SETTINGS")
                public static class guildsetting extends CommandRef {
                    public static final guildsetting cmd = new guildsetting();
                public guildsetting sheet(String value) {
                    return set("sheet", value);
                }

                public guildsetting a(String value) {
                    return set("a", value);
                }

                public guildsetting b(String value) {
                    return set("b", value);
                }

                public guildsetting c(String value) {
                    return set("c", value);
                }

                public guildsetting d(String value) {
                    return set("d", value);
                }

                public guildsetting e(String value) {
                    return set("e", value);
                }

                public guildsetting f(String value) {
                    return set("f", value);
                }

                public guildsetting g(String value) {
                    return set("g", value);
                }

                public guildsetting h(String value) {
                    return set("h", value);
                }

                public guildsetting i(String value) {
                    return set("i", value);
                }

                public guildsetting j(String value) {
                    return set("j", value);
                }

                public guildsetting k(String value) {
                    return set("k", value);
                }

                public guildsetting l(String value) {
                    return set("l", value);
                }

                public guildsetting m(String value) {
                    return set("m", value);
                }

                public guildsetting n(String value) {
                    return set("n", value);
                }

                public guildsetting o(String value) {
                    return set("o", value);
                }

                public guildsetting p(String value) {
                    return set("p", value);
                }

                public guildsetting q(String value) {
                    return set("q", value);
                }

                public guildsetting r(String value) {
                    return set("r", value);
                }

                public guildsetting s(String value) {
                    return set("s", value);
                }

                public guildsetting t(String value) {
                    return set("t", value);
                }

                public guildsetting u(String value) {
                    return set("u", value);
                }

                public guildsetting v(String value) {
                    return set("v", value);
                }

                public guildsetting w(String value) {
                    return set("w", value);
                }

                public guildsetting x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="ATTACKS")
                public static class iattack extends CommandRef {
                    public static final iattack cmd = new iattack();
                public iattack sheet(String value) {
                    return set("sheet", value);
                }

                public iattack a(String value) {
                    return set("a", value);
                }

                public iattack b(String value) {
                    return set("b", value);
                }

                public iattack c(String value) {
                    return set("c", value);
                }

                public iattack d(String value) {
                    return set("d", value);
                }

                public iattack e(String value) {
                    return set("e", value);
                }

                public iattack f(String value) {
                    return set("f", value);
                }

                public iattack g(String value) {
                    return set("g", value);
                }

                public iattack h(String value) {
                    return set("h", value);
                }

                public iattack i(String value) {
                    return set("i", value);
                }

                public iattack j(String value) {
                    return set("j", value);
                }

                public iattack k(String value) {
                    return set("k", value);
                }

                public iattack l(String value) {
                    return set("l", value);
                }

                public iattack m(String value) {
                    return set("m", value);
                }

                public iattack n(String value) {
                    return set("n", value);
                }

                public iattack o(String value) {
                    return set("o", value);
                }

                public iattack p(String value) {
                    return set("p", value);
                }

                public iattack q(String value) {
                    return set("q", value);
                }

                public iattack r(String value) {
                    return set("r", value);
                }

                public iattack s(String value) {
                    return set("s", value);
                }

                public iattack t(String value) {
                    return set("t", value);
                }

                public iattack u(String value) {
                    return set("u", value);
                }

                public iattack v(String value) {
                    return set("v", value);
                }

                public iattack w(String value) {
                    return set("w", value);
                }

                public iattack x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="MILITARY_UNITS")
                public static class militaryunit extends CommandRef {
                    public static final militaryunit cmd = new militaryunit();
                public militaryunit sheet(String value) {
                    return set("sheet", value);
                }

                public militaryunit a(String value) {
                    return set("a", value);
                }

                public militaryunit b(String value) {
                    return set("b", value);
                }

                public militaryunit c(String value) {
                    return set("c", value);
                }

                public militaryunit d(String value) {
                    return set("d", value);
                }

                public militaryunit e(String value) {
                    return set("e", value);
                }

                public militaryunit f(String value) {
                    return set("f", value);
                }

                public militaryunit g(String value) {
                    return set("g", value);
                }

                public militaryunit h(String value) {
                    return set("h", value);
                }

                public militaryunit i(String value) {
                    return set("i", value);
                }

                public militaryunit j(String value) {
                    return set("j", value);
                }

                public militaryunit k(String value) {
                    return set("k", value);
                }

                public militaryunit l(String value) {
                    return set("l", value);
                }

                public militaryunit m(String value) {
                    return set("m", value);
                }

                public militaryunit n(String value) {
                    return set("n", value);
                }

                public militaryunit o(String value) {
                    return set("o", value);
                }

                public militaryunit p(String value) {
                    return set("p", value);
                }

                public militaryunit q(String value) {
                    return set("q", value);
                }

                public militaryunit r(String value) {
                    return set("r", value);
                }

                public militaryunit s(String value) {
                    return set("s", value);
                }

                public militaryunit t(String value) {
                    return set("t", value);
                }

                public militaryunit u(String value) {
                    return set("u", value);
                }

                public militaryunit v(String value) {
                    return set("v", value);
                }

                public militaryunit w(String value) {
                    return set("w", value);
                }

                public militaryunit x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="NATIONS")
                public static class nation extends CommandRef {
                    public static final nation cmd = new nation();
                public nation sheet(String value) {
                    return set("sheet", value);
                }

                public nation a(String value) {
                    return set("a", value);
                }

                public nation b(String value) {
                    return set("b", value);
                }

                public nation c(String value) {
                    return set("c", value);
                }

                public nation d(String value) {
                    return set("d", value);
                }

                public nation e(String value) {
                    return set("e", value);
                }

                public nation f(String value) {
                    return set("f", value);
                }

                public nation g(String value) {
                    return set("g", value);
                }

                public nation h(String value) {
                    return set("h", value);
                }

                public nation i(String value) {
                    return set("i", value);
                }

                public nation j(String value) {
                    return set("j", value);
                }

                public nation k(String value) {
                    return set("k", value);
                }

                public nation l(String value) {
                    return set("l", value);
                }

                public nation m(String value) {
                    return set("m", value);
                }

                public nation n(String value) {
                    return set("n", value);
                }

                public nation o(String value) {
                    return set("o", value);
                }

                public nation p(String value) {
                    return set("p", value);
                }

                public nation q(String value) {
                    return set("q", value);
                }

                public nation r(String value) {
                    return set("r", value);
                }

                public nation s(String value) {
                    return set("s", value);
                }

                public nation t(String value) {
                    return set("t", value);
                }

                public nation u(String value) {
                    return set("u", value);
                }

                public nation v(String value) {
                    return set("v", value);
                }

                public nation w(String value) {
                    return set("w", value);
                }

                public nation x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="NATION_COLORS")
                public static class nationcolor extends CommandRef {
                    public static final nationcolor cmd = new nationcolor();
                public nationcolor sheet(String value) {
                    return set("sheet", value);
                }

                public nationcolor a(String value) {
                    return set("a", value);
                }

                public nationcolor b(String value) {
                    return set("b", value);
                }

                public nationcolor c(String value) {
                    return set("c", value);
                }

                public nationcolor d(String value) {
                    return set("d", value);
                }

                public nationcolor e(String value) {
                    return set("e", value);
                }

                public nationcolor f(String value) {
                    return set("f", value);
                }

                public nationcolor g(String value) {
                    return set("g", value);
                }

                public nationcolor h(String value) {
                    return set("h", value);
                }

                public nationcolor i(String value) {
                    return set("i", value);
                }

                public nationcolor j(String value) {
                    return set("j", value);
                }

                public nationcolor k(String value) {
                    return set("k", value);
                }

                public nationcolor l(String value) {
                    return set("l", value);
                }

                public nationcolor m(String value) {
                    return set("m", value);
                }

                public nationcolor n(String value) {
                    return set("n", value);
                }

                public nationcolor o(String value) {
                    return set("o", value);
                }

                public nationcolor p(String value) {
                    return set("p", value);
                }

                public nationcolor q(String value) {
                    return set("q", value);
                }

                public nationcolor r(String value) {
                    return set("r", value);
                }

                public nationcolor s(String value) {
                    return set("s", value);
                }

                public nationcolor t(String value) {
                    return set("t", value);
                }

                public nationcolor u(String value) {
                    return set("u", value);
                }

                public nationcolor v(String value) {
                    return set("v", value);
                }

                public nationcolor w(String value) {
                    return set("w", value);
                }

                public nationcolor x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="NATION_LIST")
                public static class nationlist extends CommandRef {
                    public static final nationlist cmd = new nationlist();
                public nationlist sheet(String value) {
                    return set("sheet", value);
                }

                public nationlist a(String value) {
                    return set("a", value);
                }

                public nationlist b(String value) {
                    return set("b", value);
                }

                public nationlist c(String value) {
                    return set("c", value);
                }

                public nationlist d(String value) {
                    return set("d", value);
                }

                public nationlist e(String value) {
                    return set("e", value);
                }

                public nationlist f(String value) {
                    return set("f", value);
                }

                public nationlist g(String value) {
                    return set("g", value);
                }

                public nationlist h(String value) {
                    return set("h", value);
                }

                public nationlist i(String value) {
                    return set("i", value);
                }

                public nationlist j(String value) {
                    return set("j", value);
                }

                public nationlist k(String value) {
                    return set("k", value);
                }

                public nationlist l(String value) {
                    return set("l", value);
                }

                public nationlist m(String value) {
                    return set("m", value);
                }

                public nationlist n(String value) {
                    return set("n", value);
                }

                public nationlist o(String value) {
                    return set("o", value);
                }

                public nationlist p(String value) {
                    return set("p", value);
                }

                public nationlist q(String value) {
                    return set("q", value);
                }

                public nationlist r(String value) {
                    return set("r", value);
                }

                public nationlist s(String value) {
                    return set("s", value);
                }

                public nationlist t(String value) {
                    return set("t", value);
                }

                public nationlist u(String value) {
                    return set("u", value);
                }

                public nationlist v(String value) {
                    return set("v", value);
                }

                public nationlist w(String value) {
                    return set("w", value);
                }

                public nationlist x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="NATION_OR_ALLIANCE")
                public static class nationoralliance extends CommandRef {
                    public static final nationoralliance cmd = new nationoralliance();
                public nationoralliance sheet(String value) {
                    return set("sheet", value);
                }

                public nationoralliance a(String value) {
                    return set("a", value);
                }

                public nationoralliance b(String value) {
                    return set("b", value);
                }

                public nationoralliance c(String value) {
                    return set("c", value);
                }

                public nationoralliance d(String value) {
                    return set("d", value);
                }

                public nationoralliance e(String value) {
                    return set("e", value);
                }

                public nationoralliance f(String value) {
                    return set("f", value);
                }

                public nationoralliance g(String value) {
                    return set("g", value);
                }

                public nationoralliance h(String value) {
                    return set("h", value);
                }

                public nationoralliance i(String value) {
                    return set("i", value);
                }

                public nationoralliance j(String value) {
                    return set("j", value);
                }

                public nationoralliance k(String value) {
                    return set("k", value);
                }

                public nationoralliance l(String value) {
                    return set("l", value);
                }

                public nationoralliance m(String value) {
                    return set("m", value);
                }

                public nationoralliance n(String value) {
                    return set("n", value);
                }

                public nationoralliance o(String value) {
                    return set("o", value);
                }

                public nationoralliance p(String value) {
                    return set("p", value);
                }

                public nationoralliance q(String value) {
                    return set("q", value);
                }

                public nationoralliance r(String value) {
                    return set("r", value);
                }

                public nationoralliance s(String value) {
                    return set("s", value);
                }

                public nationoralliance t(String value) {
                    return set("t", value);
                }

                public nationoralliance u(String value) {
                    return set("u", value);
                }

                public nationoralliance v(String value) {
                    return set("v", value);
                }

                public nationoralliance w(String value) {
                    return set("w", value);
                }

                public nationoralliance x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="PROJECTS")
                public static class project extends CommandRef {
                    public static final project cmd = new project();
                public project sheet(String value) {
                    return set("sheet", value);
                }

                public project a(String value) {
                    return set("a", value);
                }

                public project b(String value) {
                    return set("b", value);
                }

                public project c(String value) {
                    return set("c", value);
                }

                public project d(String value) {
                    return set("d", value);
                }

                public project e(String value) {
                    return set("e", value);
                }

                public project f(String value) {
                    return set("f", value);
                }

                public project g(String value) {
                    return set("g", value);
                }

                public project h(String value) {
                    return set("h", value);
                }

                public project i(String value) {
                    return set("i", value);
                }

                public project j(String value) {
                    return set("j", value);
                }

                public project k(String value) {
                    return set("k", value);
                }

                public project l(String value) {
                    return set("l", value);
                }

                public project m(String value) {
                    return set("m", value);
                }

                public project n(String value) {
                    return set("n", value);
                }

                public project o(String value) {
                    return set("o", value);
                }

                public project p(String value) {
                    return set("p", value);
                }

                public project q(String value) {
                    return set("q", value);
                }

                public project r(String value) {
                    return set("r", value);
                }

                public project s(String value) {
                    return set("s", value);
                }

                public project t(String value) {
                    return set("t", value);
                }

                public project u(String value) {
                    return set("u", value);
                }

                public project v(String value) {
                    return set("v", value);
                }

                public project w(String value) {
                    return set("w", value);
                }

                public project x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="RESOURCE_TYPES")
                public static class resourcetype extends CommandRef {
                    public static final resourcetype cmd = new resourcetype();
                public resourcetype sheet(String value) {
                    return set("sheet", value);
                }

                public resourcetype a(String value) {
                    return set("a", value);
                }

                public resourcetype b(String value) {
                    return set("b", value);
                }

                public resourcetype c(String value) {
                    return set("c", value);
                }

                public resourcetype d(String value) {
                    return set("d", value);
                }

                public resourcetype e(String value) {
                    return set("e", value);
                }

                public resourcetype f(String value) {
                    return set("f", value);
                }

                public resourcetype g(String value) {
                    return set("g", value);
                }

                public resourcetype h(String value) {
                    return set("h", value);
                }

                public resourcetype i(String value) {
                    return set("i", value);
                }

                public resourcetype j(String value) {
                    return set("j", value);
                }

                public resourcetype k(String value) {
                    return set("k", value);
                }

                public resourcetype l(String value) {
                    return set("l", value);
                }

                public resourcetype m(String value) {
                    return set("m", value);
                }

                public resourcetype n(String value) {
                    return set("n", value);
                }

                public resourcetype o(String value) {
                    return set("o", value);
                }

                public resourcetype p(String value) {
                    return set("p", value);
                }

                public resourcetype q(String value) {
                    return set("q", value);
                }

                public resourcetype r(String value) {
                    return set("r", value);
                }

                public resourcetype s(String value) {
                    return set("s", value);
                }

                public resourcetype t(String value) {
                    return set("t", value);
                }

                public resourcetype u(String value) {
                    return set("u", value);
                }

                public resourcetype v(String value) {
                    return set("v", value);
                }

                public resourcetype w(String value) {
                    return set("w", value);
                }

                public resourcetype x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TAX_BRACKETS")
                public static class taxbracket extends CommandRef {
                    public static final taxbracket cmd = new taxbracket();
                public taxbracket sheet(String value) {
                    return set("sheet", value);
                }

                public taxbracket a(String value) {
                    return set("a", value);
                }

                public taxbracket b(String value) {
                    return set("b", value);
                }

                public taxbracket c(String value) {
                    return set("c", value);
                }

                public taxbracket d(String value) {
                    return set("d", value);
                }

                public taxbracket e(String value) {
                    return set("e", value);
                }

                public taxbracket f(String value) {
                    return set("f", value);
                }

                public taxbracket g(String value) {
                    return set("g", value);
                }

                public taxbracket h(String value) {
                    return set("h", value);
                }

                public taxbracket i(String value) {
                    return set("i", value);
                }

                public taxbracket j(String value) {
                    return set("j", value);
                }

                public taxbracket k(String value) {
                    return set("k", value);
                }

                public taxbracket l(String value) {
                    return set("l", value);
                }

                public taxbracket m(String value) {
                    return set("m", value);
                }

                public taxbracket n(String value) {
                    return set("n", value);
                }

                public taxbracket o(String value) {
                    return set("o", value);
                }

                public taxbracket p(String value) {
                    return set("p", value);
                }

                public taxbracket q(String value) {
                    return set("q", value);
                }

                public taxbracket r(String value) {
                    return set("r", value);
                }

                public taxbracket s(String value) {
                    return set("s", value);
                }

                public taxbracket t(String value) {
                    return set("t", value);
                }

                public taxbracket u(String value) {
                    return set("u", value);
                }

                public taxbracket v(String value) {
                    return set("v", value);
                }

                public taxbracket w(String value) {
                    return set("w", value);
                }

                public taxbracket x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TAX_DEPOSITS")
                public static class taxdeposit extends CommandRef {
                    public static final taxdeposit cmd = new taxdeposit();
                public taxdeposit sheet(String value) {
                    return set("sheet", value);
                }

                public taxdeposit a(String value) {
                    return set("a", value);
                }

                public taxdeposit b(String value) {
                    return set("b", value);
                }

                public taxdeposit c(String value) {
                    return set("c", value);
                }

                public taxdeposit d(String value) {
                    return set("d", value);
                }

                public taxdeposit e(String value) {
                    return set("e", value);
                }

                public taxdeposit f(String value) {
                    return set("f", value);
                }

                public taxdeposit g(String value) {
                    return set("g", value);
                }

                public taxdeposit h(String value) {
                    return set("h", value);
                }

                public taxdeposit i(String value) {
                    return set("i", value);
                }

                public taxdeposit j(String value) {
                    return set("j", value);
                }

                public taxdeposit k(String value) {
                    return set("k", value);
                }

                public taxdeposit l(String value) {
                    return set("l", value);
                }

                public taxdeposit m(String value) {
                    return set("m", value);
                }

                public taxdeposit n(String value) {
                    return set("n", value);
                }

                public taxdeposit o(String value) {
                    return set("o", value);
                }

                public taxdeposit p(String value) {
                    return set("p", value);
                }

                public taxdeposit q(String value) {
                    return set("q", value);
                }

                public taxdeposit r(String value) {
                    return set("r", value);
                }

                public taxdeposit s(String value) {
                    return set("s", value);
                }

                public taxdeposit t(String value) {
                    return set("t", value);
                }

                public taxdeposit u(String value) {
                    return set("u", value);
                }

                public taxdeposit v(String value) {
                    return set("v", value);
                }

                public taxdeposit w(String value) {
                    return set("w", value);
                }

                public taxdeposit x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TREASURES")
                public static class treasure extends CommandRef {
                    public static final treasure cmd = new treasure();
                public treasure sheet(String value) {
                    return set("sheet", value);
                }

                public treasure a(String value) {
                    return set("a", value);
                }

                public treasure b(String value) {
                    return set("b", value);
                }

                public treasure c(String value) {
                    return set("c", value);
                }

                public treasure d(String value) {
                    return set("d", value);
                }

                public treasure e(String value) {
                    return set("e", value);
                }

                public treasure f(String value) {
                    return set("f", value);
                }

                public treasure g(String value) {
                    return set("g", value);
                }

                public treasure h(String value) {
                    return set("h", value);
                }

                public treasure i(String value) {
                    return set("i", value);
                }

                public treasure j(String value) {
                    return set("j", value);
                }

                public treasure k(String value) {
                    return set("k", value);
                }

                public treasure l(String value) {
                    return set("l", value);
                }

                public treasure m(String value) {
                    return set("m", value);
                }

                public treasure n(String value) {
                    return set("n", value);
                }

                public treasure o(String value) {
                    return set("o", value);
                }

                public treasure p(String value) {
                    return set("p", value);
                }

                public treasure q(String value) {
                    return set("q", value);
                }

                public treasure r(String value) {
                    return set("r", value);
                }

                public treasure s(String value) {
                    return set("s", value);
                }

                public treasure t(String value) {
                    return set("t", value);
                }

                public treasure u(String value) {
                    return set("u", value);
                }

                public treasure v(String value) {
                    return set("v", value);
                }

                public treasure w(String value) {
                    return set("w", value);
                }

                public treasure x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TREATIES")
                public static class treaty extends CommandRef {
                    public static final treaty cmd = new treaty();
                public treaty sheet(String value) {
                    return set("sheet", value);
                }

                public treaty a(String value) {
                    return set("a", value);
                }

                public treaty b(String value) {
                    return set("b", value);
                }

                public treaty c(String value) {
                    return set("c", value);
                }

                public treaty d(String value) {
                    return set("d", value);
                }

                public treaty e(String value) {
                    return set("e", value);
                }

                public treaty f(String value) {
                    return set("f", value);
                }

                public treaty g(String value) {
                    return set("g", value);
                }

                public treaty h(String value) {
                    return set("h", value);
                }

                public treaty i(String value) {
                    return set("i", value);
                }

                public treaty j(String value) {
                    return set("j", value);
                }

                public treaty k(String value) {
                    return set("k", value);
                }

                public treaty l(String value) {
                    return set("l", value);
                }

                public treaty m(String value) {
                    return set("m", value);
                }

                public treaty n(String value) {
                    return set("n", value);
                }

                public treaty o(String value) {
                    return set("o", value);
                }

                public treaty p(String value) {
                    return set("p", value);
                }

                public treaty q(String value) {
                    return set("q", value);
                }

                public treaty r(String value) {
                    return set("r", value);
                }

                public treaty s(String value) {
                    return set("s", value);
                }

                public treaty t(String value) {
                    return set("t", value);
                }

                public treaty u(String value) {
                    return set("u", value);
                }

                public treaty v(String value) {
                    return set("v", value);
                }

                public treaty w(String value) {
                    return set("w", value);
                }

                public treaty x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="TREATY_TYPES")
                public static class treatytype extends CommandRef {
                    public static final treatytype cmd = new treatytype();
                public treatytype sheet(String value) {
                    return set("sheet", value);
                }

                public treatytype a(String value) {
                    return set("a", value);
                }

                public treatytype b(String value) {
                    return set("b", value);
                }

                public treatytype c(String value) {
                    return set("c", value);
                }

                public treatytype d(String value) {
                    return set("d", value);
                }

                public treatytype e(String value) {
                    return set("e", value);
                }

                public treatytype f(String value) {
                    return set("f", value);
                }

                public treatytype g(String value) {
                    return set("g", value);
                }

                public treatytype h(String value) {
                    return set("h", value);
                }

                public treatytype i(String value) {
                    return set("i", value);
                }

                public treatytype j(String value) {
                    return set("j", value);
                }

                public treatytype k(String value) {
                    return set("k", value);
                }

                public treatytype l(String value) {
                    return set("l", value);
                }

                public treatytype m(String value) {
                    return set("m", value);
                }

                public treatytype n(String value) {
                    return set("n", value);
                }

                public treatytype o(String value) {
                    return set("o", value);
                }

                public treatytype p(String value) {
                    return set("p", value);
                }

                public treatytype q(String value) {
                    return set("q", value);
                }

                public treatytype r(String value) {
                    return set("r", value);
                }

                public treatytype s(String value) {
                    return set("s", value);
                }

                public treatytype t(String value) {
                    return set("t", value);
                }

                public treatytype u(String value) {
                    return set("u", value);
                }

                public treatytype v(String value) {
                    return set("v", value);
                }

                public treatytype w(String value) {
                    return set("w", value);
                }

                public treatytype x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="USERS")
                public static class user extends CommandRef {
                    public static final user cmd = new user();
                public user sheet(String value) {
                    return set("sheet", value);
                }

                public user a(String value) {
                    return set("a", value);
                }

                public user b(String value) {
                    return set("b", value);
                }

                public user c(String value) {
                    return set("c", value);
                }

                public user d(String value) {
                    return set("d", value);
                }

                public user e(String value) {
                    return set("e", value);
                }

                public user f(String value) {
                    return set("f", value);
                }

                public user g(String value) {
                    return set("g", value);
                }

                public user h(String value) {
                    return set("h", value);
                }

                public user i(String value) {
                    return set("i", value);
                }

                public user j(String value) {
                    return set("j", value);
                }

                public user k(String value) {
                    return set("k", value);
                }

                public user l(String value) {
                    return set("l", value);
                }

                public user m(String value) {
                    return set("m", value);
                }

                public user n(String value) {
                    return set("n", value);
                }

                public user o(String value) {
                    return set("o", value);
                }

                public user p(String value) {
                    return set("p", value);
                }

                public user q(String value) {
                    return set("q", value);
                }

                public user r(String value) {
                    return set("r", value);
                }

                public user s(String value) {
                    return set("s", value);
                }

                public user t(String value) {
                    return set("t", value);
                }

                public user u(String value) {
                    return set("u", value);
                }

                public user v(String value) {
                    return set("v", value);
                }

                public user w(String value) {
                    return set("w", value);
                }

                public user x(String value) {
                    return set("x", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap.class,method="addColumns", field="WARS")
                public static class war extends CommandRef {
                    public static final war cmd = new war();
                public war sheet(String value) {
                    return set("sheet", value);
                }

                public war a(String value) {
                    return set("a", value);
                }

                public war b(String value) {
                    return set("b", value);
                }

                public war c(String value) {
                    return set("c", value);
                }

                public war d(String value) {
                    return set("d", value);
                }

                public war e(String value) {
                    return set("e", value);
                }

                public war f(String value) {
                    return set("f", value);
                }

                public war g(String value) {
                    return set("g", value);
                }

                public war h(String value) {
                    return set("h", value);
                }

                public war i(String value) {
                    return set("i", value);
                }

                public war j(String value) {
                    return set("j", value);
                }

                public war k(String value) {
                    return set("k", value);
                }

                public war l(String value) {
                    return set("l", value);
                }

                public war m(String value) {
                    return set("m", value);
                }

                public war n(String value) {
                    return set("n", value);
                }

                public war o(String value) {
                    return set("o", value);
                }

                public war p(String value) {
                    return set("p", value);
                }

                public war q(String value) {
                    return set("q", value);
                }

                public war r(String value) {
                    return set("r", value);
                }

                public war s(String value) {
                    return set("s", value);
                }

                public war t(String value) {
                    return set("t", value);
                }

                public war u(String value) {
                    return set("u", value);
                }

                public war v(String value) {
                    return set("v", value);
                }

                public war w(String value) {
                    return set("w", value);
                }

                public war x(String value) {
                    return set("x", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="listSheetTemplates")
            public static class list extends CommandRef {
                public static final list cmd = new list();
            public list type(String value) {
                return set("type", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="deleteTemplate")
            public static class remove extends CommandRef {
                public static final remove cmd = new remove();
            public remove sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="deleteColumns")
            public static class remove_column extends CommandRef {
                public static final remove_column cmd = new remove_column();
            public remove_column sheet(String value) {
                return set("sheet", value);
            }

            public remove_column columns(String value) {
                return set("columns", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="renameTemplate")
            public static class rename extends CommandRef {
                public static final rename cmd = new rename();
            public rename sheet(String value) {
                return set("sheet", value);
            }

            public rename name(String value) {
                return set("name", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands.class,method="viewTemplate")
            public static class view extends CommandRef {
                public static final view cmd = new view();
            public view sheet(String value) {
                return set("sheet", value);
            }

            }
        }
        public static class sheets_econ{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="IngameNationTransfersByReceiver")
            public static class IngameNationTransfersByReceiver extends CommandRef {
                public static final IngameNationTransfersByReceiver cmd = new IngameNationTransfersByReceiver();
            public IngameNationTransfersByReceiver receivers(String value) {
                return set("receivers", value);
            }

            public IngameNationTransfersByReceiver startTime(String value) {
                return set("startTime", value);
            }

            public IngameNationTransfersByReceiver endTime(String value) {
                return set("endTime", value);
            }

            public IngameNationTransfersByReceiver sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="IngameNationTransfersBySender")
            public static class IngameNationTransfersBySender extends CommandRef {
                public static final IngameNationTransfersBySender cmd = new IngameNationTransfersBySender();
            public IngameNationTransfersBySender senders(String value) {
                return set("senders", value);
            }

            public IngameNationTransfersBySender timeframe(String value) {
                return set("timeframe", value);
            }

            public IngameNationTransfersBySender sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="ProjectSheet")
            public static class ProjectSheet extends CommandRef {
                public static final ProjectSheet cmd = new ProjectSheet();
            public ProjectSheet nations(String value) {
                return set("nations", value);
            }

            public ProjectSheet sheet(String value) {
                return set("sheet", value);
            }

            public ProjectSheet snapshotTime(String value) {
                return set("snapshotTime", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getIngameNationTransfers")
            public static class getIngameNationTransfers extends CommandRef {
                public static final getIngameNationTransfers cmd = new getIngameNationTransfers();
            public getIngameNationTransfers senders(String value) {
                return set("senders", value);
            }

            public getIngameNationTransfers receivers(String value) {
                return set("receivers", value);
            }

            public getIngameNationTransfers start_time(String value) {
                return set("start_time", value);
            }

            public getIngameNationTransfers end_time(String value) {
                return set("end_time", value);
            }

            public getIngameNationTransfers sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getIngameTransactions")
            public static class getIngameTransactions extends CommandRef {
                public static final getIngameTransactions cmd = new getIngameTransactions();
            public getIngameTransactions sender(String value) {
                return set("sender", value);
            }

            public getIngameTransactions receiver(String value) {
                return set("receiver", value);
            }

            public getIngameTransactions banker(String value) {
                return set("banker", value);
            }

            public getIngameTransactions timeframe(String value) {
                return set("timeframe", value);
            }

            public getIngameTransactions sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="getNationsInternalTransfers")
            public static class getNationsInternalTransfers extends CommandRef {
                public static final getNationsInternalTransfers cmd = new getNationsInternalTransfers();
            public getNationsInternalTransfers nations(String value) {
                return set("nations", value);
            }

            public getNationsInternalTransfers startTime(String value) {
                return set("startTime", value);
            }

            public getNationsInternalTransfers endTime(String value) {
                return set("endTime", value);
            }

            public getNationsInternalTransfers sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="projectCostCsv")
            public static class projectCostCsv extends CommandRef {
                public static final projectCostCsv cmd = new projectCostCsv();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="revenueSheet")
            public static class revenueSheet extends CommandRef {
                public static final revenueSheet cmd = new revenueSheet();
            public revenueSheet nations(String value) {
                return set("nations", value);
            }

            public revenueSheet sheet(String value) {
                return set("sheet", value);
            }

            public revenueSheet include_untaxable(String value) {
                return set("include_untaxable", value);
            }

            public revenueSheet snapshotTime(String value) {
                return set("snapshotTime", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="stockpileSheet")
            public static class stockpileSheet extends CommandRef {
                public static final stockpileSheet cmd = new stockpileSheet();
            public stockpileSheet nationFilter(String value) {
                return set("nationFilter", value);
            }

            public stockpileSheet normalize(String value) {
                return set("normalize", value);
            }

            public stockpileSheet onlyShowExcess(String value) {
                return set("onlyShowExcess", value);
            }

            public stockpileSheet forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxBracketSheet")
            public static class taxBracketSheet extends CommandRef {
                public static final taxBracketSheet cmd = new taxBracketSheet();
            public taxBracketSheet force(String value) {
                return set("force", value);
            }

            public taxBracketSheet includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxRecords")
            public static class taxRecords extends CommandRef {
                public static final taxRecords cmd = new taxRecords();
            public taxRecords nation(String value) {
                return set("nation", value);
            }

            public taxRecords startDate(String value) {
                return set("startDate", value);
            }

            public taxRecords endDate(String value) {
                return set("endDate", value);
            }

            public taxRecords sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="taxRevenueSheet")
            public static class taxRevenue extends CommandRef {
                public static final taxRevenue cmd = new taxRevenue();
            public taxRevenue nations(String value) {
                return set("nations", value);
            }

            public taxRevenue sheet(String value) {
                return set("sheet", value);
            }

            public taxRevenue forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            public taxRevenue includeUntaxable(String value) {
                return set("includeUntaxable", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warReimburseByNationCsv")
            public static class warReimburseByNationCsv extends CommandRef {
                public static final warReimburseByNationCsv cmd = new warReimburseByNationCsv();
            public warReimburseByNationCsv allies(String value) {
                return set("allies", value);
            }

            public warReimburseByNationCsv enemies(String value) {
                return set("enemies", value);
            }

            public warReimburseByNationCsv cutoff(String value) {
                return set("cutoff", value);
            }

            public warReimburseByNationCsv removeWarsWithNoDefenderActions(String value) {
                return set("removeWarsWithNoDefenderActions", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warchestSheet")
            public static class warchestSheet extends CommandRef {
                public static final warchestSheet cmd = new warchestSheet();
            public warchestSheet nations(String value) {
                return set("nations", value);
            }

            public warchestSheet perCityWarchest(String value) {
                return set("perCityWarchest", value);
            }

            public warchestSheet includeGrants(String value) {
                return set("includeGrants", value);
            }

            public warchestSheet doNotNormalizeDeposits(String value) {
                return set("doNotNormalizeDeposits", value);
            }

            public warchestSheet ignoreDeposits(String value) {
                return set("ignoreDeposits", value);
            }

            public warchestSheet ignoreStockpileInExcess(String value) {
                return set("ignoreStockpileInExcess", value);
            }

            public warchestSheet includeRevenueDays(String value) {
                return set("includeRevenueDays", value);
            }

            public warchestSheet forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            }
        }
        public static class sheets_ia{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ActivitySheet")
            public static class ActivitySheet extends CommandRef {
                public static final ActivitySheet cmd = new ActivitySheet();
            public ActivitySheet nations(String value) {
                return set("nations", value);
            }

            public ActivitySheet startTime(String value) {
                return set("startTime", value);
            }

            public ActivitySheet endTime(String value) {
                return set("endTime", value);
            }

            public ActivitySheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ActivitySheetFromId")
            public static class ActivitySheetFromId extends CommandRef {
                public static final ActivitySheetFromId cmd = new ActivitySheetFromId();
            public ActivitySheetFromId nationId(String value) {
                return set("nationId", value);
            }

            public ActivitySheetFromId startTime(String value) {
                return set("startTime", value);
            }

            public ActivitySheetFromId endTime(String value) {
                return set("endTime", value);
            }

            public ActivitySheetFromId sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="AllianceSheet")
            public static class AllianceSheet extends CommandRef {
                public static final AllianceSheet cmd = new AllianceSheet();
            public AllianceSheet nations(String value) {
                return set("nations", value);
            }

            public AllianceSheet columns(String value) {
                return set("columns", value);
            }

            public AllianceSheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="NationSheet")
            public static class NationSheet extends CommandRef {
                public static final NationSheet cmd = new NationSheet();
            public NationSheet nations(String value) {
                return set("nations", value);
            }

            public NationSheet columns(String value) {
                return set("columns", value);
            }

            public NationSheet snapshotTime(String value) {
                return set("snapshotTime", value);
            }

            public NationSheet updateSpies(String value) {
                return set("updateSpies", value);
            }

            public NationSheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ActivitySheetDate")
            public static class activity_date extends CommandRef {
                public static final activity_date cmd = new activity_date();
            public activity_date nations(String value) {
                return set("nations", value);
            }

            public activity_date start_time(String value) {
                return set("start_time", value);
            }

            public activity_date end_time(String value) {
                return set("end_time", value);
            }

            public activity_date by_turn(String value) {
                return set("by_turn", value);
            }

            public activity_date sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="dayChangeSheet")
            public static class daychange extends CommandRef {
                public static final daychange cmd = new daychange();
            public daychange nations(String value) {
                return set("nations", value);
            }

            public daychange sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="WarDecSheetDate")
            public static class declares_date extends CommandRef {
                public static final declares_date cmd = new declares_date();
            public declares_date nations(String value) {
                return set("nations", value);
            }

            public declares_date off(String value) {
                return set("off", value);
            }

            public declares_date def(String value) {
                return set("def", value);
            }

            public declares_date start_time(String value) {
                return set("start_time", value);
            }

            public declares_date end_time(String value) {
                return set("end_time", value);
            }

            public declares_date split_off_def(String value) {
                return set("split_off_def", value);
            }

            public declares_date by_turn(String value) {
                return set("by_turn", value);
            }

            public declares_date sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="DepositSheetDate")
            public static class deposits_date extends CommandRef {
                public static final deposits_date cmd = new deposits_date();
            public deposits_date nations(String value) {
                return set("nations", value);
            }

            public deposits_date deposit(String value) {
                return set("deposit", value);
            }

            public deposits_date withdraw(String value) {
                return set("withdraw", value);
            }

            public deposits_date start_time(String value) {
                return set("start_time", value);
            }

            public deposits_date end_time(String value) {
                return set("end_time", value);
            }

            public deposits_date split_deposit_withdraw(String value) {
                return set("split_deposit_withdraw", value);
            }

            public deposits_date by_turn(String value) {
                return set("by_turn", value);
            }

            public deposits_date sheet(String value) {
                return set("sheet", value);
            }

            }
        }
        public static class sheets_milcom{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="DeserterSheet")
            public static class DeserterSheet extends CommandRef {
                public static final DeserterSheet cmd = new DeserterSheet();
            public DeserterSheet alliances(String value) {
                return set("alliances", value);
            }

            public DeserterSheet cuttOff(String value) {
                return set("cuttOff", value);
            }

            public DeserterSheet filter(String value) {
                return set("filter", value);
            }

            public DeserterSheet ignoreInactive(String value) {
                return set("ignoreInactive", value);
            }

            public DeserterSheet ignoreVM(String value) {
                return set("ignoreVM", value);
            }

            public DeserterSheet ignoreMembers(String value) {
                return set("ignoreMembers", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="IntelOpSheet")
            public static class IntelOpSheet extends CommandRef {
                public static final IntelOpSheet cmd = new IntelOpSheet();
            public IntelOpSheet time(String value) {
                return set("time", value);
            }

            public IntelOpSheet attackers(String value) {
                return set("attackers", value);
            }

            public IntelOpSheet dnrTopX(String value) {
                return set("dnrTopX", value);
            }

            public IntelOpSheet ignoreWithLootHistory(String value) {
                return set("ignoreWithLootHistory", value);
            }

            public IntelOpSheet ignoreDNR(String value) {
                return set("ignoreDNR", value);
            }

            public IntelOpSheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="MMRSheet")
            public static class MMRSheet extends CommandRef {
                public static final MMRSheet cmd = new MMRSheet();
            public MMRSheet nations(String value) {
                return set("nations", value);
            }

            public MMRSheet sheet(String value) {
                return set("sheet", value);
            }

            public MMRSheet forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            public MMRSheet showCities(String value) {
                return set("showCities", value);
            }

            public MMRSheet snapshotTime(String value) {
                return set("snapshotTime", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="SpySheet")
            public static class SpySheet extends CommandRef {
                public static final SpySheet cmd = new SpySheet();
            public SpySheet attackers(String value) {
                return set("attackers", value);
            }

            public SpySheet defenders(String value) {
                return set("defenders", value);
            }

            public SpySheet allowedTypes(String value) {
                return set("allowedTypes", value);
            }

            public SpySheet forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            public SpySheet checkEspionageSlots(String value) {
                return set("checkEspionageSlots", value);
            }

            public SpySheet prioritizeKills(String value) {
                return set("prioritizeKills", value);
            }

            public SpySheet sheet(String value) {
                return set("sheet", value);
            }

            public SpySheet maxDef(String value) {
                return set("maxDef", value);
            }

            public SpySheet doubleOps(String value) {
                return set("doubleOps", value);
            }

            public SpySheet removeSheets(String value) {
                return set("removeSheets", value);
            }

            public SpySheet prioritizeAlliances(String value) {
                return set("prioritizeAlliances", value);
            }

            public SpySheet attackerWeighting(String value) {
                return set("attackerWeighting", value);
            }

            public SpySheet defenderWeighting(String value) {
                return set("defenderWeighting", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostByAllianceSheet")
            public static class WarCostByAllianceSheet extends CommandRef {
                public static final WarCostByAllianceSheet cmd = new WarCostByAllianceSheet();
            public WarCostByAllianceSheet nations(String value) {
                return set("nations", value);
            }

            public WarCostByAllianceSheet time(String value) {
                return set("time", value);
            }

            public WarCostByAllianceSheet includeInactives(String value) {
                return set("includeInactives", value);
            }

            public WarCostByAllianceSheet includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostByResourceSheet")
            public static class WarCostByResourceSheet extends CommandRef {
                public static final WarCostByResourceSheet cmd = new WarCostByResourceSheet();
            public WarCostByResourceSheet attackers(String value) {
                return set("attackers", value);
            }

            public WarCostByResourceSheet defenders(String value) {
                return set("defenders", value);
            }

            public WarCostByResourceSheet time(String value) {
                return set("time", value);
            }

            public WarCostByResourceSheet excludeConsumption(String value) {
                return set("excludeConsumption", value);
            }

            public WarCostByResourceSheet excludeInfra(String value) {
                return set("excludeInfra", value);
            }

            public WarCostByResourceSheet excludeLoot(String value) {
                return set("excludeLoot", value);
            }

            public WarCostByResourceSheet excludeUnitCost(String value) {
                return set("excludeUnitCost", value);
            }

            public WarCostByResourceSheet includeGray(String value) {
                return set("includeGray", value);
            }

            public WarCostByResourceSheet includeDefensives(String value) {
                return set("includeDefensives", value);
            }

            public WarCostByResourceSheet normalizePerCity(String value) {
                return set("normalizePerCity", value);
            }

            public WarCostByResourceSheet normalizePerWar(String value) {
                return set("normalizePerWar", value);
            }

            public WarCostByResourceSheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostSheet")
            public static class WarCostSheet extends CommandRef {
                public static final WarCostSheet cmd = new WarCostSheet();
            public WarCostSheet attackers(String value) {
                return set("attackers", value);
            }

            public WarCostSheet defenders(String value) {
                return set("defenders", value);
            }

            public WarCostSheet time(String value) {
                return set("time", value);
            }

            public WarCostSheet endTime(String value) {
                return set("endTime", value);
            }

            public WarCostSheet excludeConsumption(String value) {
                return set("excludeConsumption", value);
            }

            public WarCostSheet excludeInfra(String value) {
                return set("excludeInfra", value);
            }

            public WarCostSheet excludeLoot(String value) {
                return set("excludeLoot", value);
            }

            public WarCostSheet excludeUnitCost(String value) {
                return set("excludeUnitCost", value);
            }

            public WarCostSheet normalizePerCity(String value) {
                return set("normalizePerCity", value);
            }

            public WarCostSheet useLeader(String value) {
                return set("useLeader", value);
            }

            public WarCostSheet total(String value) {
                return set("total", value);
            }

            public WarCostSheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="combatantSheet")
            public static class combatantSheet extends CommandRef {
                public static final combatantSheet cmd = new combatantSheet();
            public combatantSheet nations(String value) {
                return set("nations", value);
            }

            public combatantSheet includeInactive(String value) {
                return set("includeInactive", value);
            }

            public combatantSheet includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertHidudeSpySheet")
            public static class convertHidudeSpySheet extends CommandRef {
                public static final convertHidudeSpySheet cmd = new convertHidudeSpySheet();
            public convertHidudeSpySheet input(String value) {
                return set("input", value);
            }

            public convertHidudeSpySheet output(String value) {
                return set("output", value);
            }

            public convertHidudeSpySheet groupByAttacker(String value) {
                return set("groupByAttacker", value);
            }

            public convertHidudeSpySheet forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertTKRSpySheet")
            public static class convertTKRSpySheet extends CommandRef {
                public static final convertTKRSpySheet cmd = new convertTKRSpySheet();
            public convertTKRSpySheet input(String value) {
                return set("input", value);
            }

            public convertTKRSpySheet output(String value) {
                return set("output", value);
            }

            public convertTKRSpySheet groupByAttacker(String value) {
                return set("groupByAttacker", value);
            }

            public convertTKRSpySheet force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertDtCSpySheet")
            public static class convertdtcspysheet extends CommandRef {
                public static final convertdtcspysheet cmd = new convertdtcspysheet();
            public convertdtcspysheet input(String value) {
                return set("input", value);
            }

            public convertdtcspysheet output(String value) {
                return set("output", value);
            }

            public convertdtcspysheet groupByAttacker(String value) {
                return set("groupByAttacker", value);
            }

            public convertdtcspysheet forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="listSpyTargets")
            public static class listSpyTargets extends CommandRef {
                public static final listSpyTargets cmd = new listSpyTargets();
            public listSpyTargets spySheet(String value) {
                return set("spySheet", value);
            }

            public listSpyTargets attackers(String value) {
                return set("attackers", value);
            }

            public listSpyTargets defenders(String value) {
                return set("defenders", value);
            }

            public listSpyTargets headerRow(String value) {
                return set("headerRow", value);
            }

            public listSpyTargets output(String value) {
                return set("output", value);
            }

            public listSpyTargets groupByAttacker(String value) {
                return set("groupByAttacker", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands.class,method="lootValueSheet")
            public static class lootValueSheet extends CommandRef {
                public static final lootValueSheet cmd = new lootValueSheet();
            public lootValueSheet attackers(String value) {
                return set("attackers", value);
            }

            public lootValueSheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="unitBuySheet")
            public static class unit_buy_sheet extends CommandRef {
                public static final unit_buy_sheet cmd = new unit_buy_sheet();
            public unit_buy_sheet nations(String value) {
                return set("nations", value);
            }

            public unit_buy_sheet addColumns(String value) {
                return set("addColumns", value);
            }

            public unit_buy_sheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="validateSpyBlitzSheet")
            public static class validateSpyBlitzSheet extends CommandRef {
                public static final validateSpyBlitzSheet cmd = new validateSpyBlitzSheet();
            public validateSpyBlitzSheet sheet(String value) {
                return set("sheet", value);
            }

            public validateSpyBlitzSheet dayChange(String value) {
                return set("dayChange", value);
            }

            public validateSpyBlitzSheet filter(String value) {
                return set("filter", value);
            }

            public validateSpyBlitzSheet useLeader(String value) {
                return set("useLeader", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warSheet")
            public static class warSheet extends CommandRef {
                public static final warSheet cmd = new warSheet();
            public warSheet allies(String value) {
                return set("allies", value);
            }

            public warSheet enemies(String value) {
                return set("enemies", value);
            }

            public warSheet startTime(String value) {
                return set("startTime", value);
            }

            public warSheet endTime(String value) {
                return set("endTime", value);
            }

            public warSheet includeConcludedWars(String value) {
                return set("includeConcludedWars", value);
            }

            public warSheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warchestSheet")
            public static class warchestSheet extends CommandRef {
                public static final warchestSheet cmd = new warchestSheet();
            public warchestSheet nations(String value) {
                return set("nations", value);
            }

            public warchestSheet perCityWarchest(String value) {
                return set("perCityWarchest", value);
            }

            public warchestSheet includeGrants(String value) {
                return set("includeGrants", value);
            }

            public warchestSheet doNotNormalizeDeposits(String value) {
                return set("doNotNormalizeDeposits", value);
            }

            public warchestSheet ignoreDeposits(String value) {
                return set("ignoreDeposits", value);
            }

            public warchestSheet ignoreStockpileInExcess(String value) {
                return set("ignoreStockpileInExcess", value);
            }

            public warchestSheet includeRevenueDays(String value) {
                return set("includeRevenueDays", value);
            }

            public warchestSheet forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            }
        }
        public static class simulate{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="airSim")
            public static class air extends CommandRef {
                public static final air cmd = new air();
            public air attAircraft(String value) {
                return set("attAircraft", value);
            }

            public air defAircraft(String value) {
                return set("defAircraft", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="casualties")
            public static class casualties extends CommandRef {
                public static final casualties cmd = new casualties();
            public casualties attack(String value) {
                return set("attack", value);
            }

            public casualties warType(String value) {
                return set("warType", value);
            }

            public casualties enemy(String value) {
                return set("enemy", value);
            }

            public casualties me(String value) {
                return set("me", value);
            }

            public casualties attackerMilitary(String value) {
                return set("attackerMilitary", value);
            }

            public casualties defenderMilitary(String value) {
                return set("defenderMilitary", value);
            }

            public casualties attackerPolicy(String value) {
                return set("attackerPolicy", value);
            }

            public casualties defenderPolicy(String value) {
                return set("defenderPolicy", value);
            }

            public casualties defFortified(String value) {
                return set("defFortified", value);
            }

            public casualties attAirControl(String value) {
                return set("attAirControl", value);
            }

            public casualties defAirControl(String value) {
                return set("defAirControl", value);
            }

            public casualties att_ground_control(String value) {
                return set("att_ground_control", value);
            }

            public casualties selfIsDefender(String value) {
                return set("selfIsDefender", value);
            }

            public casualties unequipAttackerSoldiers(String value) {
                return set("unequipAttackerSoldiers", value);
            }

            public casualties unequipDefenderSoldiers(String value) {
                return set("unequipDefenderSoldiers", value);
            }

            public casualties attackerProjects(String value) {
                return set("attackerProjects", value);
            }

            public casualties defenderProjects(String value) {
                return set("defenderProjects", value);
            }

            public casualties attacker_infra(String value) {
                return set("attacker_infra", value);
            }

            public casualties defender_infra(String value) {
                return set("defender_infra", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="quickestBeige")
            public static class fastBeige extends CommandRef {
                public static final fastBeige cmd = new fastBeige();
            public fastBeige resistance(String value) {
                return set("resistance", value);
            }

            public fastBeige noGround(String value) {
                return set("noGround", value);
            }

            public fastBeige noShip(String value) {
                return set("noShip", value);
            }

            public fastBeige noAir(String value) {
                return set("noAir", value);
            }

            public fastBeige noMissile(String value) {
                return set("noMissile", value);
            }

            public fastBeige noNuke(String value) {
                return set("noNuke", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="groundSim")
            public static class ground extends CommandRef {
                public static final ground cmd = new ground();
            public ground attSoldiersUnarmed(String value) {
                return set("attSoldiersUnarmed", value);
            }

            public ground attSoldiers(String value) {
                return set("attSoldiers", value);
            }

            public ground attTanks(String value) {
                return set("attTanks", value);
            }

            public ground defSoldiersUnarmed(String value) {
                return set("defSoldiersUnarmed", value);
            }

            public ground defSoldiers(String value) {
                return set("defSoldiers", value);
            }

            public ground defTanks(String value) {
                return set("defTanks", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AttackCommands.class,method="navalSim")
            public static class naval extends CommandRef {
                public static final naval cmd = new naval();
            public naval attShips(String value) {
                return set("attShips", value);
            }

            public naval defShips(String value) {
                return set("defShips", value);
            }

            }
        }
        public static class spy{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="Counterspy")
            public static class counter extends CommandRef {
                public static final counter cmd = new counter();
            public counter enemy(String value) {
                return set("enemy", value);
            }

            public counter operations(String value) {
                return set("operations", value);
            }

            public counter counterWith(String value) {
                return set("counterWith", value);
            }

            public counter minSuccess(String value) {
                return set("minSuccess", value);
            }

            }
            public static class find{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="intel")
                public static class intel extends CommandRef {
                    public static final intel cmd = new intel();
                public intel dnrTopX(String value) {
                    return set("dnrTopX", value);
                }

                public intel ignoreDNR(String value) {
                    return set("ignoreDNR", value);
                }

                public intel attacker(String value) {
                    return set("attacker", value);
                }

                public intel score(String value) {
                    return set("score", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="Spyops")
                public static class target extends CommandRef {
                    public static final target cmd = new target();
                public target targets(String value) {
                    return set("targets", value);
                }

                public target operations(String value) {
                    return set("operations", value);
                }

                public target requiredSuccess(String value) {
                    return set("requiredSuccess", value);
                }

                public target directMesssage(String value) {
                    return set("directMesssage", value);
                }

                public target prioritizeKills(String value) {
                    return set("prioritizeKills", value);
                }

                public target attacker(String value) {
                    return set("attacker", value);
                }

                }
            }
            public static class sheet{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertHidudeSpySheet")
                public static class convertHidude extends CommandRef {
                    public static final convertHidude cmd = new convertHidude();
                public convertHidude input(String value) {
                    return set("input", value);
                }

                public convertHidude output(String value) {
                    return set("output", value);
                }

                public convertHidude groupByAttacker(String value) {
                    return set("groupByAttacker", value);
                }

                public convertHidude forceUpdate(String value) {
                    return set("forceUpdate", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertTKRSpySheet")
                public static class convertTKR extends CommandRef {
                    public static final convertTKR cmd = new convertTKR();
                public convertTKR input(String value) {
                    return set("input", value);
                }

                public convertTKR output(String value) {
                    return set("output", value);
                }

                public convertTKR groupByAttacker(String value) {
                    return set("groupByAttacker", value);
                }

                public convertTKR force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="convertDtCSpySheet")
                public static class convertdtc extends CommandRef {
                    public static final convertdtc cmd = new convertdtc();
                public convertdtc input(String value) {
                    return set("input", value);
                }

                public convertdtc output(String value) {
                    return set("output", value);
                }

                public convertdtc groupByAttacker(String value) {
                    return set("groupByAttacker", value);
                }

                public convertdtc forceUpdate(String value) {
                    return set("forceUpdate", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="listSpyTargets")
                public static class copyForAlliance extends CommandRef {
                    public static final copyForAlliance cmd = new copyForAlliance();
                public copyForAlliance spySheet(String value) {
                    return set("spySheet", value);
                }

                public copyForAlliance attackers(String value) {
                    return set("attackers", value);
                }

                public copyForAlliance defenders(String value) {
                    return set("defenders", value);
                }

                public copyForAlliance headerRow(String value) {
                    return set("headerRow", value);
                }

                public copyForAlliance output(String value) {
                    return set("output", value);
                }

                public copyForAlliance groupByAttacker(String value) {
                    return set("groupByAttacker", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="freeSpyOpsSheet")
                public static class free_ops extends CommandRef {
                    public static final free_ops cmd = new free_ops();
                public free_ops nations(String value) {
                    return set("nations", value);
                }

                public free_ops addColumns(String value) {
                    return set("addColumns", value);
                }

                public free_ops requireXFreeOps(String value) {
                    return set("requireXFreeOps", value);
                }

                public free_ops requireSpies(String value) {
                    return set("requireSpies", value);
                }

                public free_ops sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="SpySheet")
                public static class generate extends CommandRef {
                    public static final generate cmd = new generate();
                public generate attackers(String value) {
                    return set("attackers", value);
                }

                public generate defenders(String value) {
                    return set("defenders", value);
                }

                public generate allowedTypes(String value) {
                    return set("allowedTypes", value);
                }

                public generate forceUpdate(String value) {
                    return set("forceUpdate", value);
                }

                public generate checkEspionageSlots(String value) {
                    return set("checkEspionageSlots", value);
                }

                public generate prioritizeKills(String value) {
                    return set("prioritizeKills", value);
                }

                public generate sheet(String value) {
                    return set("sheet", value);
                }

                public generate maxDef(String value) {
                    return set("maxDef", value);
                }

                public generate doubleOps(String value) {
                    return set("doubleOps", value);
                }

                public generate removeSheets(String value) {
                    return set("removeSheets", value);
                }

                public generate prioritizeAlliances(String value) {
                    return set("prioritizeAlliances", value);
                }

                public generate attackerWeighting(String value) {
                    return set("attackerWeighting", value);
                }

                public generate defenderWeighting(String value) {
                    return set("defenderWeighting", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="IntelOpSheet")
                public static class intel extends CommandRef {
                    public static final intel cmd = new intel();
                public intel time(String value) {
                    return set("time", value);
                }

                public intel attackers(String value) {
                    return set("attackers", value);
                }

                public intel dnrTopX(String value) {
                    return set("dnrTopX", value);
                }

                public intel ignoreWithLootHistory(String value) {
                    return set("ignoreWithLootHistory", value);
                }

                public intel ignoreDNR(String value) {
                    return set("ignoreDNR", value);
                }

                public intel sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="validateSpyBlitzSheet")
                public static class validate extends CommandRef {
                    public static final validate cmd = new validate();
                public validate sheet(String value) {
                    return set("sheet", value);
                }

                public validate dayChange(String value) {
                    return set("dayChange", value);
                }

                public validate filter(String value) {
                    return set("filter", value);
                }

                public validate useLeader(String value) {
                    return set("useLeader", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="spyTierGraph")
            public static class tierGraph extends CommandRef {
                public static final tierGraph cmd = new tierGraph();
            public tierGraph coalition1(String value) {
                return set("coalition1", value);
            }

            public tierGraph coalition2(String value) {
                return set("coalition2", value);
            }

            public tierGraph includeInactives(String value) {
                return set("includeInactives", value);
            }

            public tierGraph includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public tierGraph total(String value) {
                return set("total", value);
            }

            public tierGraph barGraph(String value) {
                return set("barGraph", value);
            }

            public tierGraph attachJson(String value) {
                return set("attachJson", value);
            }

            public tierGraph attachCsv(String value) {
                return set("attachCsv", value);
            }

            public tierGraph attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
        }
        public static class stats{
            public static class other{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceStats")
                public static class aa_metrics_by_turn extends CommandRef {
                    public static final aa_metrics_by_turn cmd = new aa_metrics_by_turn();
                public aa_metrics_by_turn metrics(String value) {
                    return set("metrics", value);
                }

                public aa_metrics_by_turn start(String value) {
                    return set("start", value);
                }

                public aa_metrics_by_turn end(String value) {
                    return set("end", value);
                }

                public aa_metrics_by_turn coalition(String value) {
                    return set("coalition", value);
                }

                public aa_metrics_by_turn attach_json(String value) {
                    return set("attach_json", value);
                }

                public aa_metrics_by_turn attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public aa_metrics_by_turn attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="compareStats")
                public static class coalition_metric_by_turn extends CommandRef {
                    public static final coalition_metric_by_turn cmd = new coalition_metric_by_turn();
                public coalition_metric_by_turn metric(String value) {
                    return set("metric", value);
                }

                public coalition_metric_by_turn start(String value) {
                    return set("start", value);
                }

                public coalition_metric_by_turn end(String value) {
                    return set("end", value);
                }

                public coalition_metric_by_turn coalition1(String value) {
                    return set("coalition1", value);
                }

                public coalition_metric_by_turn coalition2(String value) {
                    return set("coalition2", value);
                }

                public coalition_metric_by_turn coalition3(String value) {
                    return set("coalition3", value);
                }

                public coalition_metric_by_turn coalition4(String value) {
                    return set("coalition4", value);
                }

                public coalition_metric_by_turn coalition5(String value) {
                    return set("coalition5", value);
                }

                public coalition_metric_by_turn coalition6(String value) {
                    return set("coalition6", value);
                }

                public coalition_metric_by_turn coalition7(String value) {
                    return set("coalition7", value);
                }

                public coalition_metric_by_turn coalition8(String value) {
                    return set("coalition8", value);
                }

                public coalition_metric_by_turn coalition9(String value) {
                    return set("coalition9", value);
                }

                public coalition_metric_by_turn coalition10(String value) {
                    return set("coalition10", value);
                }

                public coalition_metric_by_turn attach_json(String value) {
                    return set("attach_json", value);
                }

                public coalition_metric_by_turn attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public coalition_metric_by_turn attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
            }
            public static class tier{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="compareTierStats")
                public static class tier_by_coalition extends CommandRef {
                    public static final tier_by_coalition cmd = new tier_by_coalition();
                public tier_by_coalition metric(String value) {
                    return set("metric", value);
                }

                public tier_by_coalition groupBy(String value) {
                    return set("groupBy", value);
                }

                public tier_by_coalition coalition1(String value) {
                    return set("coalition1", value);
                }

                public tier_by_coalition coalition2(String value) {
                    return set("coalition2", value);
                }

                public tier_by_coalition coalition3(String value) {
                    return set("coalition3", value);
                }

                public tier_by_coalition coalition4(String value) {
                    return set("coalition4", value);
                }

                public tier_by_coalition coalition5(String value) {
                    return set("coalition5", value);
                }

                public tier_by_coalition coalition6(String value) {
                    return set("coalition6", value);
                }

                public tier_by_coalition coalition7(String value) {
                    return set("coalition7", value);
                }

                public tier_by_coalition coalition8(String value) {
                    return set("coalition8", value);
                }

                public tier_by_coalition coalition9(String value) {
                    return set("coalition9", value);
                }

                public tier_by_coalition coalition10(String value) {
                    return set("coalition10", value);
                }

                public tier_by_coalition total(String value) {
                    return set("total", value);
                }

                public tier_by_coalition includeApps(String value) {
                    return set("includeApps", value);
                }

                public tier_by_coalition includeVm(String value) {
                    return set("includeVm", value);
                }

                public tier_by_coalition includeInactive(String value) {
                    return set("includeInactive", value);
                }

                public tier_by_coalition snapshotDate(String value) {
                    return set("snapshotDate", value);
                }

                public tier_by_coalition attach_json(String value) {
                    return set("attach_json", value);
                }

                public tier_by_coalition attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public tier_by_coalition attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
            }
        }
        public static class stats_other{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceMetricAB")
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

            public allianceMetricAB attachJson(String value) {
                return set("attachJson", value);
            }

            public allianceMetricAB attachCsv(String value) {
                return set("attachCsv", value);
            }

            public allianceMetricAB attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="allianceNationsSheet")
            public static class allianceNationsSheet extends CommandRef {
                public static final allianceNationsSheet cmd = new allianceNationsSheet();
            public allianceNationsSheet nations(String value) {
                return set("nations", value);
            }

            public allianceNationsSheet columns(String value) {
                return set("columns", value);
            }

            public allianceNationsSheet sheet(String value) {
                return set("sheet", value);
            }

            public allianceNationsSheet useTotal(String value) {
                return set("useTotal", value);
            }

            public allianceNationsSheet includeInactives(String value) {
                return set("includeInactives", value);
            }

            public allianceNationsSheet includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            }
            public static class data_csv{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AllianceMetricCommands.class,method="AlliancesDataByDay")
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

                public AlliancesDataByDay graph(String value) {
                    return set("graph", value);
                }

                public AlliancesDataByDay includeApps(String value) {
                    return set("includeApps", value);
                }

                public AlliancesDataByDay attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="findProducer")
            public static class findProducer extends CommandRef {
                public static final findProducer cmd = new findProducer();
            public findProducer resources(String value) {
                return set("resources", value);
            }

            public findProducer nationList(String value) {
                return set("nationList", value);
            }

            public findProducer ignoreMilitaryUpkeep(String value) {
                return set("ignoreMilitaryUpkeep", value);
            }

            public findProducer ignoreTradeBonus(String value) {
                return set("ignoreTradeBonus", value);
            }

            public findProducer ignoreNationBonus(String value) {
                return set("ignoreNationBonus", value);
            }

            public findProducer includeNegative(String value) {
                return set("includeNegative", value);
            }

            public findProducer listByNation(String value) {
                return set("listByNation", value);
            }

            public findProducer listAverage(String value) {
                return set("listAverage", value);
            }

            public findProducer uploadFile(String value) {
                return set("uploadFile", value);
            }

            public findProducer includeInactive(String value) {
                return set("includeInactive", value);
            }

            public findProducer snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public findProducer num_results(String value) {
                return set("num_results", value);
            }

            public findProducer highlight(String value) {
                return set("highlight", value);
            }

            }
            public static class global_metrics{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="orbisStatByDay")
                public static class by_time extends CommandRef {
                    public static final by_time cmd = new by_time();
                public by_time metrics(String value) {
                    return set("metrics", value);
                }

                public by_time start(String value) {
                    return set("start", value);
                }

                public by_time end(String value) {
                    return set("end", value);
                }

                public by_time attachJson(String value) {
                    return set("attachJson", value);
                }

                public by_time attachCsv(String value) {
                    return set("attachCsv", value);
                }

                public by_time attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="inflows")
            public static class inflows extends CommandRef {
                public static final inflows cmd = new inflows();
            public inflows nationOrAlliances(String value) {
                return set("nationOrAlliances", value);
            }

            public inflows cutoffMs(String value) {
                return set("cutoffMs", value);
            }

            public inflows hideInflows(String value) {
                return set("hideInflows", value);
            }

            public inflows hideOutflows(String value) {
                return set("hideOutflows", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="metric_compare_by_turn")
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

            public metric_compare_by_turn attachJson(String value) {
                return set("attachJson", value);
            }

            public metric_compare_by_turn attachCsv(String value) {
                return set("attachCsv", value);
            }

            public metric_compare_by_turn attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="nationRanking")
            public static class nationRanking extends CommandRef {
                public static final nationRanking cmd = new nationRanking();
            public nationRanking nations(String value) {
                return set("nations", value);
            }

            public nationRanking attribute(String value) {
                return set("attribute", value);
            }

            public nationRanking groupByAlliance(String value) {
                return set("groupByAlliance", value);
            }

            public nationRanking reverseOrder(String value) {
                return set("reverseOrder", value);
            }

            public nationRanking snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public nationRanking total(String value) {
                return set("total", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="radiationByTurn")
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

            public radiationByTurn attachJson(String value) {
                return set("attachJson", value);
            }

            public radiationByTurn attachCsv(String value) {
                return set("attachCsv", value);
            }

            public radiationByTurn attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="recruitmentRankings")
            public static class recruitmentRankings extends CommandRef {
                public static final recruitmentRankings cmd = new recruitmentRankings();
            public recruitmentRankings cutoff(String value) {
                return set("cutoff", value);
            }

            public recruitmentRankings topX(String value) {
                return set("topX", value);
            }

            public recruitmentRankings uploadFile(String value) {
                return set("uploadFile", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradepricebyday")
            public static class tradepricebyday extends CommandRef {
                public static final tradepricebyday cmd = new tradepricebyday();
            public tradepricebyday resources(String value) {
                return set("resources", value);
            }

            public tradepricebyday numDays(String value) {
                return set("numDays", value);
            }

            public tradepricebyday attachJson(String value) {
                return set("attachJson", value);
            }

            public tradepricebyday attachCsv(String value) {
                return set("attachCsv", value);
            }

            public tradepricebyday attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
        }
        public static class stats_tier{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="attributeTierGraph")
            public static class attributeTierGraph extends CommandRef {
                public static final attributeTierGraph cmd = new attributeTierGraph();
            public attributeTierGraph metric(String value) {
                return set("metric", value);
            }

            public attributeTierGraph coalition1(String value) {
                return set("coalition1", value);
            }

            public attributeTierGraph coalition2(String value) {
                return set("coalition2", value);
            }

            public attributeTierGraph includeInactives(String value) {
                return set("includeInactives", value);
            }

            public attributeTierGraph includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public attributeTierGraph total(String value) {
                return set("total", value);
            }

            public attributeTierGraph attachJson(String value) {
                return set("attachJson", value);
            }

            public attributeTierGraph attachCsv(String value) {
                return set("attachCsv", value);
            }

            public attributeTierGraph snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public attributeTierGraph groupBy(String value) {
                return set("groupBy", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="cityTierGraph")
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

            public cityTierGraph attachJson(String value) {
                return set("attachJson", value);
            }

            public cityTierGraph attachCsv(String value) {
                return set("attachCsv", value);
            }

            public cityTierGraph attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            public cityTierGraph snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AllianceMetricCommands.class,method="metricByGroup")
            public static class metric_by_group extends CommandRef {
                public static final metric_by_group cmd = new metric_by_group();
            public metric_by_group metrics(String value) {
                return set("metrics", value);
            }

            public metric_by_group nations(String value) {
                return set("nations", value);
            }

            public metric_by_group groupBy(String value) {
                return set("groupBy", value);
            }

            public metric_by_group includeInactives(String value) {
                return set("includeInactives", value);
            }

            public metric_by_group includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public metric_by_group total(String value) {
                return set("total", value);
            }

            public metric_by_group snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public metric_by_group attachJson(String value) {
                return set("attachJson", value);
            }

            public metric_by_group attachCsv(String value) {
                return set("attachCsv", value);
            }

            public metric_by_group attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="mmrTierGraph")
            public static class mmrTierGraph extends CommandRef {
                public static final mmrTierGraph cmd = new mmrTierGraph();
            public mmrTierGraph coalition1(String value) {
                return set("coalition1", value);
            }

            public mmrTierGraph coalition2(String value) {
                return set("coalition2", value);
            }

            public mmrTierGraph includeInactives(String value) {
                return set("includeInactives", value);
            }

            public mmrTierGraph includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            public mmrTierGraph sheet(String value) {
                return set("sheet", value);
            }

            public mmrTierGraph buildings(String value) {
                return set("buildings", value);
            }

            public mmrTierGraph snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="NthBeigeLootByScoreRange")
            public static class nth_loot_by_score extends CommandRef {
                public static final nth_loot_by_score cmd = new nth_loot_by_score();
            public nth_loot_by_score nations(String value) {
                return set("nations", value);
            }

            public nth_loot_by_score n(String value) {
                return set("n", value);
            }

            public nth_loot_by_score snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public nth_loot_by_score attachCsv(String value) {
                return set("attachCsv", value);
            }

            public nth_loot_by_score attachJson(String value) {
                return set("attachJson", value);
            }

            public nth_loot_by_score attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="scoreTierGraph")
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

            public scoreTierGraph attachJson(String value) {
                return set("attachJson", value);
            }

            public scoreTierGraph attachCsv(String value) {
                return set("attachCsv", value);
            }

            public scoreTierGraph attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="spyTierGraph")
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

            public spyTierGraph attachJson(String value) {
                return set("attachJson", value);
            }

            public spyTierGraph attachCsv(String value) {
                return set("attachCsv", value);
            }

            public spyTierGraph attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="strengthTierGraph")
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

            public strengthTierGraph attachJson(String value) {
                return set("attachJson", value);
            }

            public strengthTierGraph attachCsv(String value) {
                return set("attachCsv", value);
            }

            public strengthTierGraph attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
        }
        public static class stats_war{
            public static class attack_breakdown{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="attackBreakdownSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                public sheet attackers(String value) {
                    return set("attackers", value);
                }

                public sheet defenders(String value) {
                    return set("defenders", value);
                }

                public sheet start(String value) {
                    return set("start", value);
                }

                public sheet end(String value) {
                    return set("end", value);
                }

                public sheet sheet(String value) {
                    return set("sheet", value);
                }

                public sheet checkActivity(String value) {
                    return set("checkActivity", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="attackTypeBreakdownAB")
                public static class versus extends CommandRef {
                    public static final versus cmd = new versus();
                public versus coalition1(String value) {
                    return set("coalition1", value);
                }

                public versus coalition2(String value) {
                    return set("coalition2", value);
                }

                public versus timeStart(String value) {
                    return set("timeStart", value);
                }

                public versus timeEnd(String value) {
                    return set("timeEnd", value);
                }

                public versus allowedWarTypes(String value) {
                    return set("allowedWarTypes", value);
                }

                public versus allowedWarStatus(String value) {
                    return set("allowedWarStatus", value);
                }

                public versus allowedAttackTypes(String value) {
                    return set("allowedAttackTypes", value);
                }

                public versus allowedVictoryTypes(String value) {
                    return set("allowedVictoryTypes", value);
                }

                public versus onlyOffensiveWars(String value) {
                    return set("onlyOffensiveWars", value);
                }

                public versus onlyDefensiveWars(String value) {
                    return set("onlyDefensiveWars", value);
                }

                public versus onlyOffensiveAttacks(String value) {
                    return set("onlyOffensiveAttacks", value);
                }

                public versus onlyDefensiveAttacks(String value) {
                    return set("onlyDefensiveAttacks", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="attackTypeRanking")
            public static class attack_ranking extends CommandRef {
                public static final attack_ranking cmd = new attack_ranking();
            public attack_ranking time(String value) {
                return set("time", value);
            }

            public attack_ranking type(String value) {
                return set("type", value);
            }

            public attack_ranking alliances(String value) {
                return set("alliances", value);
            }

            public attack_ranking only_top_x(String value) {
                return set("only_top_x", value);
            }

            public attack_ranking percent(String value) {
                return set("percent", value);
            }

            public attack_ranking only_off_wars(String value) {
                return set("only_off_wars", value);
            }

            public attack_ranking only_def_wars(String value) {
                return set("only_def_wars", value);
            }

            }
            public static class by_day{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warsCostRankingByDay")
                public static class warcost_global extends CommandRef {
                    public static final warcost_global cmd = new warcost_global();
                public warcost_global type(String value) {
                    return set("type", value);
                }

                public warcost_global mode(String value) {
                    return set("mode", value);
                }

                public warcost_global time_start(String value) {
                    return set("time_start", value);
                }

                public warcost_global time_end(String value) {
                    return set("time_end", value);
                }

                public warcost_global coalition1(String value) {
                    return set("coalition1", value);
                }

                public warcost_global coalition2(String value) {
                    return set("coalition2", value);
                }

                public warcost_global coalition3(String value) {
                    return set("coalition3", value);
                }

                public warcost_global coalition4(String value) {
                    return set("coalition4", value);
                }

                public warcost_global coalition5(String value) {
                    return set("coalition5", value);
                }

                public warcost_global coalition6(String value) {
                    return set("coalition6", value);
                }

                public warcost_global coalition7(String value) {
                    return set("coalition7", value);
                }

                public warcost_global coalition8(String value) {
                    return set("coalition8", value);
                }

                public warcost_global coalition9(String value) {
                    return set("coalition9", value);
                }

                public warcost_global coalition10(String value) {
                    return set("coalition10", value);
                }

                public warcost_global running_total(String value) {
                    return set("running_total", value);
                }

                public warcost_global allowedWarStatus(String value) {
                    return set("allowedWarStatus", value);
                }

                public warcost_global allowedWarTypes(String value) {
                    return set("allowedWarTypes", value);
                }

                public warcost_global allowedAttackTypes(String value) {
                    return set("allowedAttackTypes", value);
                }

                public warcost_global allowedVictoryTypes(String value) {
                    return set("allowedVictoryTypes", value);
                }

                public warcost_global attach_json(String value) {
                    return set("attach_json", value);
                }

                public warcost_global attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public warcost_global attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warCostsByDay")
                public static class warcost_versus extends CommandRef {
                    public static final warcost_versus cmd = new warcost_versus();
                public warcost_versus coalition1(String value) {
                    return set("coalition1", value);
                }

                public warcost_versus coalition2(String value) {
                    return set("coalition2", value);
                }

                public warcost_versus type(String value) {
                    return set("type", value);
                }

                public warcost_versus time_start(String value) {
                    return set("time_start", value);
                }

                public warcost_versus time_end(String value) {
                    return set("time_end", value);
                }

                public warcost_versus running_total(String value) {
                    return set("running_total", value);
                }

                public warcost_versus allowedWarStatus(String value) {
                    return set("allowedWarStatus", value);
                }

                public warcost_versus allowedWarTypes(String value) {
                    return set("allowedWarTypes", value);
                }

                public warcost_versus allowedAttackTypes(String value) {
                    return set("allowedAttackTypes", value);
                }

                public warcost_versus allowedVictoryTypes(String value) {
                    return set("allowedVictoryTypes", value);
                }

                public warcost_versus attach_json(String value) {
                    return set("attach_json", value);
                }

                public warcost_versus attach_csv(String value) {
                    return set("attach_csv", value);
                }

                public warcost_versus attach_sheet(String value) {
                    return set("attach_sheet", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
            public static class counterStats extends CommandRef {
                public static final counterStats cmd = new counterStats();
            public counterStats alliance(String value) {
                return set("alliance", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="myloot")
            public static class myloot extends CommandRef {
                public static final myloot cmd = new myloot();
            public myloot coalition2(String value) {
                return set("coalition2", value);
            }

            public myloot timeStart(String value) {
                return set("timeStart", value);
            }

            public myloot timeEnd(String value) {
                return set("timeEnd", value);
            }

            public myloot ignoreUnits(String value) {
                return set("ignoreUnits", value);
            }

            public myloot ignoreInfra(String value) {
                return set("ignoreInfra", value);
            }

            public myloot ignoreConsumption(String value) {
                return set("ignoreConsumption", value);
            }

            public myloot ignoreLoot(String value) {
                return set("ignoreLoot", value);
            }

            public myloot ignoreBuildings(String value) {
                return set("ignoreBuildings", value);
            }

            public myloot listWarIds(String value) {
                return set("listWarIds", value);
            }

            public myloot showWarTypes(String value) {
                return set("showWarTypes", value);
            }

            public myloot allowedWarTypes(String value) {
                return set("allowedWarTypes", value);
            }

            public myloot allowedWarStatus(String value) {
                return set("allowedWarStatus", value);
            }

            public myloot allowedAttackTypes(String value) {
                return set("allowedAttackTypes", value);
            }

            public myloot allowedVictoryTypes(String value) {
                return set("allowedVictoryTypes", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warCost")
            public static class warCost extends CommandRef {
                public static final warCost cmd = new warCost();
            public warCost war(String value) {
                return set("war", value);
            }

            public warCost ignoreUnits(String value) {
                return set("ignoreUnits", value);
            }

            public warCost ignoreInfra(String value) {
                return set("ignoreInfra", value);
            }

            public warCost ignoreConsumption(String value) {
                return set("ignoreConsumption", value);
            }

            public warCost ignoreLoot(String value) {
                return set("ignoreLoot", value);
            }

            public warCost ignoreBuildings(String value) {
                return set("ignoreBuildings", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warCostRanking")
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

            public warCostRanking uploadFile(String value) {
                return set("uploadFile", value);
            }

            public warCostRanking num_results(String value) {
                return set("num_results", value);
            }

            public warCostRanking highlight(String value) {
                return set("highlight", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="warRanking")
            public static class warRanking extends CommandRef {
                public static final warRanking cmd = new warRanking();
            public warRanking time(String value) {
                return set("time", value);
            }

            public warRanking attackers(String value) {
                return set("attackers", value);
            }

            public warRanking defenders(String value) {
                return set("defenders", value);
            }

            public warRanking onlyOffensives(String value) {
                return set("onlyOffensives", value);
            }

            public warRanking onlyDefensives(String value) {
                return set("onlyDefensives", value);
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByAA")
            public static class warStatusRankingByAA extends CommandRef {
                public static final warStatusRankingByAA cmd = new warStatusRankingByAA();
            public warStatusRankingByAA attackers(String value) {
                return set("attackers", value);
            }

            public warStatusRankingByAA defenders(String value) {
                return set("defenders", value);
            }

            public warStatusRankingByAA time(String value) {
                return set("time", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warStatusRankingByNation")
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
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warAttacksByDay")
            public static class warattacksbyday extends CommandRef {
                public static final warattacksbyday cmd = new warattacksbyday();
            public warattacksbyday nations(String value) {
                return set("nations", value);
            }

            public warattacksbyday cutoff(String value) {
                return set("cutoff", value);
            }

            public warattacksbyday allowedTypes(String value) {
                return set("allowedTypes", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="warsCost")
            public static class warsCost extends CommandRef {
                public static final warsCost cmd = new warsCost();
            public warsCost coalition1(String value) {
                return set("coalition1", value);
            }

            public warsCost coalition2(String value) {
                return set("coalition2", value);
            }

            public warsCost timeStart(String value) {
                return set("timeStart", value);
            }

            public warsCost timeEnd(String value) {
                return set("timeEnd", value);
            }

            public warsCost ignoreUnits(String value) {
                return set("ignoreUnits", value);
            }

            public warsCost ignoreInfra(String value) {
                return set("ignoreInfra", value);
            }

            public warsCost ignoreConsumption(String value) {
                return set("ignoreConsumption", value);
            }

            public warsCost ignoreLoot(String value) {
                return set("ignoreLoot", value);
            }

            public warsCost ignoreBuildings(String value) {
                return set("ignoreBuildings", value);
            }

            public warsCost listWarIds(String value) {
                return set("listWarIds", value);
            }

            public warsCost showWarTypes(String value) {
                return set("showWarTypes", value);
            }

            public warsCost allowedWarTypes(String value) {
                return set("allowedWarTypes", value);
            }

            public warsCost allowedWarStatus(String value) {
                return set("allowedWarStatus", value);
            }

            public warsCost allowedAttackTypes(String value) {
                return set("allowedAttackTypes", value);
            }

            public warsCost allowedVictoryTypes(String value) {
                return set("allowedVictoryTypes", value);
            }

            public warsCost onlyOffensiveWars(String value) {
                return set("onlyOffensiveWars", value);
            }

            public warsCost onlyDefensiveWars(String value) {
                return set("onlyDefensiveWars", value);
            }

            public warsCost onlyOffensiveAttacks(String value) {
                return set("onlyOffensiveAttacks", value);
            }

            public warsCost onlyDefensiveAttacks(String value) {
                return set("onlyDefensiveAttacks", value);
            }

            }
        }
        public static class tax{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxBracketSheet")
            public static class bracketsheet extends CommandRef {
                public static final bracketsheet cmd = new bracketsheet();
            public bracketsheet force(String value) {
                return set("force", value);
            }

            public bracketsheet includeApplicants(String value) {
                return set("includeApplicants", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxDeposits")
            public static class deposits extends CommandRef {
                public static final deposits cmd = new deposits();
            public deposits nations(String value) {
                return set("nations", value);
            }

            public deposits baseTaxRate(String value) {
                return set("baseTaxRate", value);
            }

            public deposits startDate(String value) {
                return set("startDate", value);
            }

            public deposits endDate(String value) {
                return set("endDate", value);
            }

            public deposits sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxInfo")
            public static class info extends CommandRef {
                public static final info cmd = new info();
            public info nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="listRequiredTaxRates")
            public static class listBracketAuto extends CommandRef {
                public static final listBracketAuto cmd = new listBracketAuto();
            public listBracketAuto sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="taxRecords")
            public static class records extends CommandRef {
                public static final records cmd = new records();
            public records nation(String value) {
                return set("nation", value);
            }

            public records startDate(String value) {
                return set("startDate", value);
            }

            public records endDate(String value) {
                return set("endDate", value);
            }

            public records sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="setNationTaxBrackets")
            public static class setNationBracketAuto extends CommandRef {
                public static final setNationBracketAuto cmd = new setNationBracketAuto();
            public setNationBracketAuto nations(String value) {
                return set("nations", value);
            }

            public setNationBracketAuto ping(String value) {
                return set("ping", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="setBracketBulk")
            public static class set_from_sheet extends CommandRef {
                public static final set_from_sheet cmd = new set_from_sheet();
            public set_from_sheet sheet(String value) {
                return set("sheet", value);
            }

            public set_from_sheet force(String value) {
                return set("force", value);
            }

            }
        }
        public static class test{
            @AutoRegister(clazz=link.locutus.discord.web.test.TestCommands.class,method="dummy")
            public static class dummy extends CommandRef {
                public static final dummy cmd = new dummy();

            }
        }
        public static class trade{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="acceptTrades")
            public static class accept extends CommandRef {
                public static final accept cmd = new accept();
            public accept receiver(String value) {
                return set("receiver", value);
            }

            public accept amount(String value) {
                return set("amount", value);
            }

            public accept useLogin(String value) {
                return set("useLogin", value);
            }

            public accept force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="GlobalTradeAverage")
            public static class average extends CommandRef {
                public static final average cmd = new average();
            public average time(String value) {
                return set("time", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="compareStockpileValueByDay")
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

            public compareStockpileValueByDay attachJson(String value) {
                return set("attachJson", value);
            }

            public compareStockpileValueByDay attachCsv(String value) {
                return set("attachCsv", value);
            }

            public compareStockpileValueByDay attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="findProducer")
            public static class findProducer extends CommandRef {
                public static final findProducer cmd = new findProducer();
            public findProducer resources(String value) {
                return set("resources", value);
            }

            public findProducer nationList(String value) {
                return set("nationList", value);
            }

            public findProducer ignoreMilitaryUpkeep(String value) {
                return set("ignoreMilitaryUpkeep", value);
            }

            public findProducer ignoreTradeBonus(String value) {
                return set("ignoreTradeBonus", value);
            }

            public findProducer ignoreNationBonus(String value) {
                return set("ignoreNationBonus", value);
            }

            public findProducer includeNegative(String value) {
                return set("includeNegative", value);
            }

            public findProducer listByNation(String value) {
                return set("listByNation", value);
            }

            public findProducer listAverage(String value) {
                return set("listAverage", value);
            }

            public findProducer uploadFile(String value) {
                return set("uploadFile", value);
            }

            public findProducer includeInactive(String value) {
                return set("includeInactive", value);
            }

            public findProducer snapshotDate(String value) {
                return set("snapshotDate", value);
            }

            public findProducer num_results(String value) {
                return set("num_results", value);
            }

            public findProducer highlight(String value) {
                return set("highlight", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="findTrader")
            public static class findTrader extends CommandRef {
                public static final findTrader cmd = new findTrader();
            public findTrader type(String value) {
                return set("type", value);
            }

            public findTrader cutoff(String value) {
                return set("cutoff", value);
            }

            public findTrader buyOrSell(String value) {
                return set("buyOrSell", value);
            }

            public findTrader groupByAlliance(String value) {
                return set("groupByAlliance", value);
            }

            public findTrader includeMoneyTrades(String value) {
                return set("includeMoneyTrades", value);
            }

            public findTrader nations(String value) {
                return set("nations", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeMargin")
            public static class margin extends CommandRef {
                public static final margin cmd = new margin();
            public margin usePercent(String value) {
                return set("usePercent", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="trademarginbyday")
            public static class marginByDay extends CommandRef {
                public static final marginByDay cmd = new marginByDay();
            public marginByDay start(String value) {
                return set("start", value);
            }

            public marginByDay end(String value) {
                return set("end", value);
            }

            public marginByDay percent(String value) {
                return set("percent", value);
            }

            public marginByDay attachJson(String value) {
                return set("attachJson", value);
            }

            public marginByDay attachCsv(String value) {
                return set("attachCsv", value);
            }

            public marginByDay attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="moneyTrades")
            public static class moneyTrades extends CommandRef {
                public static final moneyTrades cmd = new moneyTrades();
            public moneyTrades nation(String value) {
                return set("nation", value);
            }

            public moneyTrades time(String value) {
                return set("time", value);
            }

            public moneyTrades forceUpdate(String value) {
                return set("forceUpdate", value);
            }

            public moneyTrades addBalance(String value) {
                return set("addBalance", value);
            }

            }
            public static class offer{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="buyOffer")
                public static class buy extends CommandRef {
                    public static final buy cmd = new buy();
                public buy resource(String value) {
                    return set("resource", value);
                }

                public buy quantity(String value) {
                    return set("quantity", value);
                }

                public buy minPPU(String value) {
                    return set("minPPU", value);
                }

                public buy maxPPU(String value) {
                    return set("maxPPU", value);
                }

                public buy negotiable(String value) {
                    return set("negotiable", value);
                }

                public buy expire(String value) {
                    return set("expire", value);
                }

                public buy exchangeFor(String value) {
                    return set("exchangeFor", value);
                }

                public buy exchangePPU(String value) {
                    return set("exchangePPU", value);
                }

                public buy force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="buyList")
                public static class buy_list extends CommandRef {
                    public static final buy_list cmd = new buy_list();
                public buy_list youBuy(String value) {
                    return set("youBuy", value);
                }

                public buy_list youProvide(String value) {
                    return set("youProvide", value);
                }

                public buy_list allowedTraders(String value) {
                    return set("allowedTraders", value);
                }

                public buy_list sortByLowestMinPrice(String value) {
                    return set("sortByLowestMinPrice", value);
                }

                public buy_list sortByLowestMaxPrice(String value) {
                    return set("sortByLowestMaxPrice", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="deleteOffer")
                public static class delete extends CommandRef {
                    public static final delete cmd = new delete();
                public delete deleteResource(String value) {
                    return set("deleteResource", value);
                }

                public delete buyOrSell(String value) {
                    return set("buyOrSell", value);
                }

                public delete deleteId(String value) {
                    return set("deleteId", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="offerInfo")
                public static class info extends CommandRef {
                    public static final info cmd = new info();
                public info offerId(String value) {
                    return set("offerId", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="myOffers")
                public static class my_offers extends CommandRef {
                    public static final my_offers cmd = new my_offers();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="sellOffer")
                public static class sell extends CommandRef {
                    public static final sell cmd = new sell();
                public sell resource(String value) {
                    return set("resource", value);
                }

                public sell quantity(String value) {
                    return set("quantity", value);
                }

                public sell minPPU(String value) {
                    return set("minPPU", value);
                }

                public sell maxPPU(String value) {
                    return set("maxPPU", value);
                }

                public sell negotiable(String value) {
                    return set("negotiable", value);
                }

                public sell expire(String value) {
                    return set("expire", value);
                }

                public sell exchangeFor(String value) {
                    return set("exchangeFor", value);
                }

                public sell exchangePPU(String value) {
                    return set("exchangePPU", value);
                }

                public sell force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="sellList")
                public static class sell_list extends CommandRef {
                    public static final sell_list cmd = new sell_list();
                public sell_list youSell(String value) {
                    return set("youSell", value);
                }

                public sell_list youReceive(String value) {
                    return set("youReceive", value);
                }

                public sell_list allowedTraders(String value) {
                    return set("allowedTraders", value);
                }

                public sell_list sortByLowestMinPrice(String value) {
                    return set("sortByLowestMinPrice", value);
                }

                public sell_list sortByLowestMaxPrice(String value) {
                    return set("sortByLowestMaxPrice", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="updateOffer")
                public static class update extends CommandRef {
                    public static final update cmd = new update();
                public update offerId(String value) {
                    return set("offerId", value);
                }

                public update quantity(String value) {
                    return set("quantity", value);
                }

                public update minPPU(String value) {
                    return set("minPPU", value);
                }

                public update maxPPU(String value) {
                    return set("maxPPU", value);
                }

                public update negotiable(String value) {
                    return set("negotiable", value);
                }

                public update expire(String value) {
                    return set("expire", value);
                }

                public update exchangeFor(String value) {
                    return set("exchangeFor", value);
                }

                public update exchangePPU(String value) {
                    return set("exchangePPU", value);
                }

                public update force(String value) {
                    return set("force", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradePrice")
            public static class price extends CommandRef {
                public static final price cmd = new price();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradepricebyday")
            public static class priceByDay extends CommandRef {
                public static final priceByDay cmd = new priceByDay();
            public priceByDay resources(String value) {
                return set("resources", value);
            }

            public priceByDay numDays(String value) {
                return set("numDays", value);
            }

            public priceByDay attachJson(String value) {
                return set("attachJson", value);
            }

            public priceByDay attachCsv(String value) {
                return set("attachCsv", value);
            }

            public priceByDay attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeProfit")
            public static class profit extends CommandRef {
                public static final profit cmd = new profit();
            public profit nations(String value) {
                return set("nations", value);
            }

            public profit time(String value) {
                return set("time", value);
            }

            public profit include_outliers(String value) {
                return set("include_outliers", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradeRanking")
            public static class ranking extends CommandRef {
                public static final ranking cmd = new ranking();
            public ranking nations(String value) {
                return set("nations", value);
            }

            public ranking time(String value) {
                return set("time", value);
            }

            public ranking groupByAlliance(String value) {
                return set("groupByAlliance", value);
            }

            public ranking uploadFile(String value) {
                return set("uploadFile", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradetotalbyday")
            public static class totalByDay extends CommandRef {
                public static final totalByDay cmd = new totalByDay();
            public totalByDay start(String value) {
                return set("start", value);
            }

            public totalByDay end(String value) {
                return set("end", value);
            }

            public totalByDay attachJson(String value) {
                return set("attachJson", value);
            }

            public totalByDay attachCsv(String value) {
                return set("attachCsv", value);
            }

            public totalByDay attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            public totalByDay resources(String value) {
                return set("resources", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="trending")
            public static class trending extends CommandRef {
                public static final trending cmd = new trending();
            public trending time(String value) {
                return set("time", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="convertedTotal")
            public static class value extends CommandRef {
                public static final value cmd = new value();
            public value resources(String value) {
                return set("resources", value);
            }

            public value normalize(String value) {
                return set("normalize", value);
            }

            public value useBuyPrice(String value) {
                return set("useBuyPrice", value);
            }

            public value useSellPrice(String value) {
                return set("useSellPrice", value);
            }

            public value convertType(String value) {
                return set("convertType", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="GlobalTradeVolume")
            public static class volume extends CommandRef {
                public static final volume cmd = new volume();

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.TradeCommands.class,method="tradevolumebyday")
            public static class volumebyday extends CommandRef {
                public static final volumebyday cmd = new volumebyday();
            public volumebyday start(String value) {
                return set("start", value);
            }

            public volumebyday end(String value) {
                return set("end", value);
            }

            public volumebyday attachJson(String value) {
                return set("attachJson", value);
            }

            public volumebyday attachCsv(String value) {
                return set("attachCsv", value);
            }

            public volumebyday attach_sheet(String value) {
                return set("attach_sheet", value);
            }

            public volumebyday resources(String value) {
                return set("resources", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord._test.command.CustomCommands.class,method="tradeAverageCodes")
        public static class tradeAverageCodes extends CommandRef {
            public static final tradeAverageCodes cmd = new tradeAverageCodes();

        }
        public static class transfer{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transferBulk")
            public static class bulk extends CommandRef {
                public static final bulk cmd = new bulk();
            public bulk sheet(String value) {
                return set("sheet", value);
            }

            public bulk depositType(String value) {
                return set("depositType", value);
            }

            public bulk depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public bulk useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public bulk useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public bulk taxAccount(String value) {
                return set("taxAccount", value);
            }

            public bulk existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public bulk expire(String value) {
                return set("expire", value);
            }

            public bulk decay(String value) {
                return set("decay", value);
            }

            public bulk convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public bulk escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public bulk bypassChecks(String value) {
                return set("bypassChecks", value);
            }

            public bulk force(String value) {
                return set("force", value);
            }

            public bulk key(String value) {
                return set("key", value);
            }

            }
            public static class internal{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="send")
                public static class from_nation_account extends CommandRef {
                    public static final from_nation_account cmd = new from_nation_account();
                public from_nation_account amount(String value) {
                    return set("amount", value);
                }

                public from_nation_account receiver_account(String value) {
                    return set("receiver_account", value);
                }

                public from_nation_account receiver_nation(String value) {
                    return set("receiver_nation", value);
                }

                public from_nation_account sender_alliance(String value) {
                    return set("sender_alliance", value);
                }

                public from_nation_account force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="sendAA")
                public static class from_offshore_account extends CommandRef {
                    public static final from_offshore_account cmd = new from_offshore_account();
                public from_offshore_account amount(String value) {
                    return set("amount", value);
                }

                public from_offshore_account receiver_account(String value) {
                    return set("receiver_account", value);
                }

                public from_offshore_account receiver_nation(String value) {
                    return set("receiver_nation", value);
                }

                public from_offshore_account sender_alliance(String value) {
                    return set("sender_alliance", value);
                }

                public from_offshore_account sender_nation(String value) {
                    return set("sender_nation", value);
                }

                public from_offshore_account force(String value) {
                    return set("force", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="offshore")
            public static class offshore extends CommandRef {
                public static final offshore cmd = new offshore();
            public offshore to(String value) {
                return set("to", value);
            }

            public offshore account(String value) {
                return set("account", value);
            }

            public offshore keepAmount(String value) {
                return set("keepAmount", value);
            }

            public offshore sendAmount(String value) {
                return set("sendAmount", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="disburse")
            public static class raws extends CommandRef {
                public static final raws cmd = new raws();
            public raws nationList(String value) {
                return set("nationList", value);
            }

            public raws days(String value) {
                return set("days", value);
            }

            public raws no_daily_cash(String value) {
                return set("no_daily_cash", value);
            }

            public raws no_cash(String value) {
                return set("no_cash", value);
            }

            public raws bank_note(String value) {
                return set("bank_note", value);
            }

            public raws expire(String value) {
                return set("expire", value);
            }

            public raws decay(String value) {
                return set("decay", value);
            }

            public raws deduct_as_cash(String value) {
                return set("deduct_as_cash", value);
            }

            public raws nation_account(String value) {
                return set("nation_account", value);
            }

            public raws escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public raws ingame_bank(String value) {
                return set("ingame_bank", value);
            }

            public raws offshore_account(String value) {
                return set("offshore_account", value);
            }

            public raws tax_account(String value) {
                return set("tax_account", value);
            }

            public raws use_receiver_tax_account(String value) {
                return set("use_receiver_tax_account", value);
            }

            public raws bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public raws ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public raws ping_role(String value) {
                return set("ping_role", value);
            }

            public raws force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="transfer")
            public static class resources extends CommandRef {
                public static final resources cmd = new resources();
            public resources receiver(String value) {
                return set("receiver", value);
            }

            public resources transfer(String value) {
                return set("transfer", value);
            }

            public resources depositType(String value) {
                return set("depositType", value);
            }

            public resources nationAccount(String value) {
                return set("nationAccount", value);
            }

            public resources senderAlliance(String value) {
                return set("senderAlliance", value);
            }

            public resources allianceAccount(String value) {
                return set("allianceAccount", value);
            }

            public resources taxAccount(String value) {
                return set("taxAccount", value);
            }

            public resources existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public resources onlyMissingFunds(String value) {
                return set("onlyMissingFunds", value);
            }

            public resources expire(String value) {
                return set("expire", value);
            }

            public resources decay(String value) {
                return set("decay", value);
            }

            public resources token(String value) {
                return set("token", value);
            }

            public resources convertCash(String value) {
                return set("convertCash", value);
            }

            public resources escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public resources bypassChecks(String value) {
                return set("bypassChecks", value);
            }

            public resources ping_when_sent(String value) {
                return set("ping_when_sent", value);
            }

            public resources force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="withdraw")
            public static class self extends CommandRef {
                public static final self cmd = new self();
            public self amount(String value) {
                return set("amount", value);
            }

            public self only_send_missing(String value) {
                return set("only_send_missing", value);
            }

            public self bank_note(String value) {
                return set("bank_note", value);
            }

            public self expire(String value) {
                return set("expire", value);
            }

            public self decay(String value) {
                return set("decay", value);
            }

            public self deduct_as_cash(String value) {
                return set("deduct_as_cash", value);
            }

            public self ingame_bank(String value) {
                return set("ingame_bank", value);
            }

            public self offshore_account(String value) {
                return set("offshore_account", value);
            }

            public self nation_account(String value) {
                return set("nation_account", value);
            }

            public self escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public self tax_account(String value) {
                return set("tax_account", value);
            }

            public self use_receiver_tax_account(String value) {
                return set("use_receiver_tax_account", value);
            }

            public self bypass_checks(String value) {
                return set("bypass_checks", value);
            }

            public self force(String value) {
                return set("force", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="warchest")
            public static class warchest extends CommandRef {
                public static final warchest cmd = new warchest();
            public warchest nations(String value) {
                return set("nations", value);
            }

            public warchest resourcesPerCity(String value) {
                return set("resourcesPerCity", value);
            }

            public warchest note(String value) {
                return set("note", value);
            }

            public warchest skipStockpile(String value) {
                return set("skipStockpile", value);
            }

            public warchest depositsAccount(String value) {
                return set("depositsAccount", value);
            }

            public warchest useAllianceBank(String value) {
                return set("useAllianceBank", value);
            }

            public warchest useOffshoreAccount(String value) {
                return set("useOffshoreAccount", value);
            }

            public warchest taxAccount(String value) {
                return set("taxAccount", value);
            }

            public warchest existingTaxAccount(String value) {
                return set("existingTaxAccount", value);
            }

            public warchest expire(String value) {
                return set("expire", value);
            }

            public warchest decay(String value) {
                return set("decay", value);
            }

            public warchest convertToMoney(String value) {
                return set("convertToMoney", value);
            }

            public warchest escrow_mode(String value) {
                return set("escrow_mode", value);
            }

            public warchest bypassChecks(String value) {
                return set("bypassChecks", value);
            }

            public warchest force(String value) {
                return set("force", value);
            }

            }
        }
        public static class treaty{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="approveTreaty")
            public static class approve extends CommandRef {
                public static final approve cmd = new approve();
            public approve senders(String value) {
                return set("senders", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="cancelTreaty")
            public static class cancel extends CommandRef {
                public static final cancel cmd = new cancel();
            public cancel senders(String value) {
                return set("senders", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="nap")
            public static class gw_nap extends CommandRef {
                public static final gw_nap cmd = new gw_nap();
            public gw_nap listExpired(String value) {
                return set("listExpired", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="treaties")
            public static class list extends CommandRef {
                public static final list cmd = new list();
            public list alliances(String value) {
                return set("alliances", value);
            }

            public list treatyFilter(String value) {
                return set("treatyFilter", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands.class,method="sendTreaty")
            public static class send extends CommandRef {
                public static final send cmd = new send();
            public send receiver(String value) {
                return set("receiver", value);
            }

            public send type(String value) {
                return set("type", value);
            }

            public send days(String value) {
                return set("days", value);
            }

            public send message(String value) {
                return set("message", value);
            }

            }
        }
        public static class unit{
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="unitBuySheet")
            public static class buy_sheet extends CommandRef {
                public static final buy_sheet cmd = new buy_sheet();
            public buy_sheet nations(String value) {
                return set("nations", value);
            }

            public buy_sheet addColumns(String value) {
                return set("addColumns", value);
            }

            public buy_sheet sheet(String value) {
                return set("sheet", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="unitCost")
            public static class cost extends CommandRef {
                public static final cost cmd = new cost();
            public cost units(String value) {
                return set("units", value);
            }

            public cost wartime(String value) {
                return set("wartime", value);
            }

            public cost nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.class,method="unitHistory")
            public static class history extends CommandRef {
                public static final history cmd = new history();
            public history nation(String value) {
                return set("nation", value);
            }

            public history unit(String value) {
                return set("unit", value);
            }

            public history page(String value) {
                return set("page", value);
            }

            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.DiscordCommands.class,method="unregister")
        public static class unregister extends CommandRef {
            public static final unregister cmd = new unregister();
        public unregister nation(String value) {
            return set("nation", value);
        }

        public unregister force(String value) {
            return set("force", value);
        }

        }
        public static class war{
            public static class blockade{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="cancelUnblockadeRequest")
                public static class cancelRequest extends CommandRef {
                    public static final cancelRequest cmd = new cancelRequest();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unblockade")
                public static class find extends CommandRef {
                    public static final find cmd = new find();
                public find allies(String value) {
                    return set("allies", value);
                }

                public find targets(String value) {
                    return set("targets", value);
                }

                public find myShips(String value) {
                    return set("myShips", value);
                }

                public find numResults(String value) {
                    return set("numResults", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unblockadeMe")
                public static class request extends CommandRef {
                    public static final request cmd = new request();
                public request diff(String value) {
                    return set("diff", value);
                }

                public request note(String value) {
                    return set("note", value);
                }

                public request force(String value) {
                    return set("force", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="canIBeige")
            public static class canIBeige extends CommandRef {
                public static final canIBeige cmd = new canIBeige();
            public canIBeige nation(String value) {
                return set("nation", value);
            }

            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warcard")
            public static class card extends CommandRef {
                public static final card cmd = new card();
            public card warId(String value) {
                return set("warId", value);
            }

            }
            public static class counter{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="autocounter")
                public static class auto extends CommandRef {
                    public static final auto cmd = new auto();
                public auto enemy(String value) {
                    return set("enemy", value);
                }

                public auto attackers(String value) {
                    return set("attackers", value);
                }

                public auto max(String value) {
                    return set("max", value);
                }

                public auto pingMembers(String value) {
                    return set("pingMembers", value);
                }

                public auto skipAddMembers(String value) {
                    return set("skipAddMembers", value);
                }

                public auto sendMail(String value) {
                    return set("sendMail", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="counter")
                public static class nation extends CommandRef {
                    public static final nation cmd = new nation();
                public nation target(String value) {
                    return set("target", value);
                }

                public nation counterWith(String value) {
                    return set("counterWith", value);
                }

                public nation allowMaxOffensives(String value) {
                    return set("allowMaxOffensives", value);
                }

                public nation filterWeak(String value) {
                    return set("filterWeak", value);
                }

                public nation onlyOnline(String value) {
                    return set("onlyOnline", value);
                }

                public nation requireDiscord(String value) {
                    return set("requireDiscord", value);
                }

                public nation allowSameAlliance(String value) {
                    return set("allowSameAlliance", value);
                }

                public nation includeInactive(String value) {
                    return set("includeInactive", value);
                }

                public nation includeNonMembers(String value) {
                    return set("includeNonMembers", value);
                }

                public nation ping(String value) {
                    return set("ping", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="counterSheet")
                public static class sheet extends CommandRef {
                    public static final sheet cmd = new sheet();
                public sheet enemyFilter(String value) {
                    return set("enemyFilter", value);
                }

                public sheet allies(String value) {
                    return set("allies", value);
                }

                public sheet excludeApplicants(String value) {
                    return set("excludeApplicants", value);
                }

                public sheet excludeInactives(String value) {
                    return set("excludeInactives", value);
                }

                public sheet includeAllEnemies(String value) {
                    return set("includeAllEnemies", value);
                }

                public sheet sheetUrl(String value) {
                    return set("sheetUrl", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="counterStats")
                public static class stats extends CommandRef {
                    public static final stats cmd = new stats();
                public stats alliance(String value) {
                    return set("alliance", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="counterWar")
                public static class url extends CommandRef {
                    public static final url cmd = new url();
                public url war(String value) {
                    return set("war", value);
                }

                public url counterWith(String value) {
                    return set("counterWith", value);
                }

                public url allowAttackersWithMaxOffensives(String value) {
                    return set("allowAttackersWithMaxOffensives", value);
                }

                public url filterWeak(String value) {
                    return set("filterWeak", value);
                }

                public url onlyActive(String value) {
                    return set("onlyActive", value);
                }

                public url requireDiscord(String value) {
                    return set("requireDiscord", value);
                }

                public url allowSameAlliance(String value) {
                    return set("allowSameAlliance", value);
                }

                public url includeInactive(String value) {
                    return set("includeInactive", value);
                }

                public url includeNonMembers(String value) {
                    return set("includeNonMembers", value);
                }

                public url ping(String value) {
                    return set("ping", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="dnr")
            public static class dnr extends CommandRef {
                public static final dnr cmd = new dnr();
            public dnr nation(String value) {
                return set("nation", value);
            }

            }
            public static class find{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="BlitzPractice")
                public static class blitztargets extends CommandRef {
                    public static final blitztargets cmd = new blitztargets();
                public blitztargets topX(String value) {
                    return set("topX", value);
                }

                public blitztargets page(String value) {
                    return set("page", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="findBountyNations")
                public static class bounty extends CommandRef {
                    public static final bounty cmd = new bounty();
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="damage")
                public static class damage extends CommandRef {
                    public static final damage cmd = new damage();
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

                public damage includeBeige(String value) {
                    return set("includeBeige", value);
                }

                public damage resultsInDm(String value) {
                    return set("resultsInDm", value);
                }

                public damage warRange(String value) {
                    return set("warRange", value);
                }

                public damage relativeNavalStrength(String value) {
                    return set("relativeNavalStrength", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="war")
                public static class enemy extends CommandRef {
                    public static final enemy cmd = new enemy();
                public enemy targets(String value) {
                    return set("targets", value);
                }

                public enemy numResults(String value) {
                    return set("numResults", value);
                }

                public enemy attackerScore(String value) {
                    return set("attackerScore", value);
                }

                public enemy includeInactives(String value) {
                    return set("includeInactives", value);
                }

                public enemy includeApplicants(String value) {
                    return set("includeApplicants", value);
                }

                public enemy onlyPriority(String value) {
                    return set("onlyPriority", value);
                }

                public enemy onlyWeak(String value) {
                    return set("onlyWeak", value);
                }

                public enemy onlyEasy(String value) {
                    return set("onlyEasy", value);
                }

                public enemy onlyLessCities(String value) {
                    return set("onlyLessCities", value);
                }

                public enemy resultsInDm(String value) {
                    return set("resultsInDm", value);
                }

                public enemy includeStrong(String value) {
                    return set("includeStrong", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="raid")
                public static class raid extends CommandRef {
                    public static final raid cmd = new raid();
                public raid targets(String value) {
                    return set("targets", value);
                }

                public raid numResults(String value) {
                    return set("numResults", value);
                }

                public raid activeTimeCutoff(String value) {
                    return set("activeTimeCutoff", value);
                }

                public raid weakground(String value) {
                    return set("weakground", value);
                }

                public raid beigeTurns(String value) {
                    return set("beigeTurns", value);
                }

                public raid vmTurns(String value) {
                    return set("vmTurns", value);
                }

                public raid nationScore(String value) {
                    return set("nationScore", value);
                }

                public raid defensiveSlots(String value) {
                    return set("defensiveSlots", value);
                }

                public raid ignoreDNR(String value) {
                    return set("ignoreDNR", value);
                }

                public raid ignoreBankLoot(String value) {
                    return set("ignoreBankLoot", value);
                }

                public raid ignoreCityRevenue(String value) {
                    return set("ignoreCityRevenue", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="findTreasureNations")
                public static class treasure extends CommandRef {
                    public static final treasure cmd = new treasure();
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
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unblockade")
                public static class unblockade extends CommandRef {
                    public static final unblockade cmd = new unblockade();
                public unblockade allies(String value) {
                    return set("allies", value);
                }

                public unblockade targets(String value) {
                    return set("targets", value);
                }

                public unblockade myShips(String value) {
                    return set("myShips", value);
                }

                public unblockade numResults(String value) {
                    return set("numResults", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="unprotected")
                public static class unprotected extends CommandRef {
                    public static final unprotected cmd = new unprotected();
                public unprotected targets(String value) {
                    return set("targets", value);
                }

                public unprotected numResults(String value) {
                    return set("numResults", value);
                }

                public unprotected ignoreDNR(String value) {
                    return set("ignoreDNR", value);
                }

                public unprotected includeAllies(String value) {
                    return set("includeAllies", value);
                }

                public unprotected nationsToBlitzWith(String value) {
                    return set("nationsToBlitzWith", value);
                }

                public unprotected maxRelativeTargetStrength(String value) {
                    return set("maxRelativeTargetStrength", value);
                }

                public unprotected maxRelativeCounterStrength(String value) {
                    return set("maxRelativeCounterStrength", value);
                }

                public unprotected withinAllAttackersRange(String value) {
                    return set("withinAllAttackersRange", value);
                }

                public unprotected ignoreODP(String value) {
                    return set("ignoreODP", value);
                }

                public unprotected force(String value) {
                    return set("force", value);
                }

                }
            }
            @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="wars")
            public static class info extends CommandRef {
                public static final info cmd = new info();
            public info nation(String value) {
                return set("nation", value);
            }

            }
            public static class room{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warroom")
                public static class create extends CommandRef {
                    public static final create cmd = new create();
                public create enemy(String value) {
                    return set("enemy", value);
                }

                public create attackers(String value) {
                    return set("attackers", value);
                }

                public create max(String value) {
                    return set("max", value);
                }

                public create force(String value) {
                    return set("force", value);
                }

                public create excludeWeakAttackers(String value) {
                    return set("excludeWeakAttackers", value);
                }

                public create requireDiscord(String value) {
                    return set("requireDiscord", value);
                }

                public create allowAttackersWithMaxOffensives(String value) {
                    return set("allowAttackersWithMaxOffensives", value);
                }

                public create pingMembers(String value) {
                    return set("pingMembers", value);
                }

                public create skipAddMembers(String value) {
                    return set("skipAddMembers", value);
                }

                public create sendMail(String value) {
                    return set("sendMail", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="deleteForEnemies")
                public static class delete_for_enemies extends CommandRef {
                    public static final delete_for_enemies cmd = new delete_for_enemies();
                public delete_for_enemies enemy_rooms(String value) {
                    return set("enemy_rooms", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="deletePlanningChannel")
                public static class delete_planning extends CommandRef {
                    public static final delete_planning cmd = new delete_planning();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warRoomSheet")
                public static class from_sheet extends CommandRef {
                    public static final from_sheet cmd = new from_sheet();
                public from_sheet blitzSheet(String value) {
                    return set("blitzSheet", value);
                }

                public from_sheet customMessage(String value) {
                    return set("customMessage", value);
                }

                public from_sheet addCounterMessage(String value) {
                    return set("addCounterMessage", value);
                }

                public from_sheet ping(String value) {
                    return set("ping", value);
                }

                public from_sheet addMember(String value) {
                    return set("addMember", value);
                }

                public from_sheet allowedNations(String value) {
                    return set("allowedNations", value);
                }

                public from_sheet headerRow(String value) {
                    return set("headerRow", value);
                }

                public from_sheet useLeader(String value) {
                    return set("useLeader", value);
                }

                public from_sheet force(String value) {
                    return set("force", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warRoomList")
                public static class list extends CommandRef {
                    public static final list cmd = new list();
                public list nation(String value) {
                    return set("nation", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warpin")
                public static class pin extends CommandRef {
                    public static final pin cmd = new pin();

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands.class,method="purgeWarRooms")
                public static class purge extends CommandRef {
                    public static final purge cmd = new purge();
                public purge channel(String value) {
                    return set("channel", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warcat")
                public static class setCategory extends CommandRef {
                    public static final setCategory cmd = new setCategory();
                public setCategory category(String value) {
                    return set("category", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="sortWarRooms")
                public static class sort extends CommandRef {
                    public static final sort cmd = new sort();

                }
            }
            public static class sheet{
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands.class,method="warReimburseByNationCsv")
                public static class ReimburseByNation extends CommandRef {
                    public static final ReimburseByNation cmd = new ReimburseByNation();
                public ReimburseByNation allies(String value) {
                    return set("allies", value);
                }

                public ReimburseByNation enemies(String value) {
                    return set("enemies", value);
                }

                public ReimburseByNation cutoff(String value) {
                    return set("cutoff", value);
                }

                public ReimburseByNation removeWarsWithNoDefenderActions(String value) {
                    return set("removeWarsWithNoDefenderActions", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="blitzSheet")
                public static class blitzSheet extends CommandRef {
                    public static final blitzSheet cmd = new blitzSheet();
                public blitzSheet attNations(String value) {
                    return set("attNations", value);
                }

                public blitzSheet defNations(String value) {
                    return set("defNations", value);
                }

                public blitzSheet maxOff(String value) {
                    return set("maxOff", value);
                }

                public blitzSheet sameAAPriority(String value) {
                    return set("sameAAPriority", value);
                }

                public blitzSheet sameActivityPriority(String value) {
                    return set("sameActivityPriority", value);
                }

                public blitzSheet turn(String value) {
                    return set("turn", value);
                }

                public blitzSheet attActivity(String value) {
                    return set("attActivity", value);
                }

                public blitzSheet defActivity(String value) {
                    return set("defActivity", value);
                }

                public blitzSheet processActiveWars(String value) {
                    return set("processActiveWars", value);
                }

                public blitzSheet onlyEasyTargets(String value) {
                    return set("onlyEasyTargets", value);
                }

                public blitzSheet maxCityRatio(String value) {
                    return set("maxCityRatio", value);
                }

                public blitzSheet maxGroundRatio(String value) {
                    return set("maxGroundRatio", value);
                }

                public blitzSheet maxAirRatio(String value) {
                    return set("maxAirRatio", value);
                }

                public blitzSheet sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostByResourceSheet")
                public static class costByResource extends CommandRef {
                    public static final costByResource cmd = new costByResource();
                public costByResource attackers(String value) {
                    return set("attackers", value);
                }

                public costByResource defenders(String value) {
                    return set("defenders", value);
                }

                public costByResource time(String value) {
                    return set("time", value);
                }

                public costByResource excludeConsumption(String value) {
                    return set("excludeConsumption", value);
                }

                public costByResource excludeInfra(String value) {
                    return set("excludeInfra", value);
                }

                public costByResource excludeLoot(String value) {
                    return set("excludeLoot", value);
                }

                public costByResource excludeUnitCost(String value) {
                    return set("excludeUnitCost", value);
                }

                public costByResource includeGray(String value) {
                    return set("includeGray", value);
                }

                public costByResource includeDefensives(String value) {
                    return set("includeDefensives", value);
                }

                public costByResource normalizePerCity(String value) {
                    return set("normalizePerCity", value);
                }

                public costByResource normalizePerWar(String value) {
                    return set("normalizePerWar", value);
                }

                public costByResource sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands.class,method="WarCostSheet")
                public static class costSheet extends CommandRef {
                    public static final costSheet cmd = new costSheet();
                public costSheet attackers(String value) {
                    return set("attackers", value);
                }

                public costSheet defenders(String value) {
                    return set("defenders", value);
                }

                public costSheet time(String value) {
                    return set("time", value);
                }

                public costSheet endTime(String value) {
                    return set("endTime", value);
                }

                public costSheet excludeConsumption(String value) {
                    return set("excludeConsumption", value);
                }

                public costSheet excludeInfra(String value) {
                    return set("excludeInfra", value);
                }

                public costSheet excludeLoot(String value) {
                    return set("excludeLoot", value);
                }

                public costSheet excludeUnitCost(String value) {
                    return set("excludeUnitCost", value);
                }

                public costSheet normalizePerCity(String value) {
                    return set("normalizePerCity", value);
                }

                public costSheet useLeader(String value) {
                    return set("useLeader", value);
                }

                public costSheet total(String value) {
                    return set("total", value);
                }

                public costSheet sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="raidSheet")
                public static class raid extends CommandRef {
                    public static final raid cmd = new raid();
                public raid attackers(String value) {
                    return set("attackers", value);
                }

                public raid targets(String value) {
                    return set("targets", value);
                }

                public raid includeInactiveAttackers(String value) {
                    return set("includeInactiveAttackers", value);
                }

                public raid includeApplicantAttackers(String value) {
                    return set("includeApplicantAttackers", value);
                }

                public raid includeBeigeAttackers(String value) {
                    return set("includeBeigeAttackers", value);
                }

                public raid sheet(String value) {
                    return set("sheet", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="ValidateBlitzSheet")
                public static class validate extends CommandRef {
                    public static final validate cmd = new validate();
                public validate sheet(String value) {
                    return set("sheet", value);
                }

                public validate maxWars(String value) {
                    return set("maxWars", value);
                }

                public validate nationsFilter(String value) {
                    return set("nationsFilter", value);
                }

                public validate attackerFilter(String value) {
                    return set("attackerFilter", value);
                }

                public validate useLeader(String value) {
                    return set("useLeader", value);
                }

                public validate headerRow(String value) {
                    return set("headerRow", value);
                }

                }
                @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.class,method="warSheet")
                public static class warSheet extends CommandRef {
                    public static final warSheet cmd = new warSheet();
                public warSheet allies(String value) {
                    return set("allies", value);
                }

                public warSheet enemies(String value) {
                    return set("enemies", value);
                }

                public warSheet startTime(String value) {
                    return set("startTime", value);
                }

                public warSheet endTime(String value) {
                    return set("endTime", value);
                }

                public warSheet includeConcludedWars(String value) {
                    return set("includeConcludedWars", value);
                }

                public warSheet sheet(String value) {
                    return set("sheet", value);
                }

                }
            }
        }
        @AutoRegister(clazz=link.locutus.discord.web.commands.WebCommands.class,method="web")
        public static class web extends CommandRef {
            public static final web cmd = new web();

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands.class,method="who")
        public static class who extends CommandRef {
            public static final who cmd = new who();
        public who nationOrAlliances(String value) {
            return set("nationOrAlliances", value);
        }

        public who sortBy(String value) {
            return set("sortBy", value);
        }

        public who list(String value) {
            return set("list", value);
        }

        public who listAlliances(String value) {
            return set("listAlliances", value);
        }

        public who listRawUserIds(String value) {
            return set("listRawUserIds", value);
        }

        public who listMentions(String value) {
            return set("listMentions", value);
        }

        public who listInfo(String value) {
            return set("listInfo", value);
        }

        public who listChannels(String value) {
            return set("listChannels", value);
        }

        public who snapshotDate(String value) {
            return set("snapshotDate", value);
        }

        public who page(String value) {
            return set("page", value);
        }

        }

}
