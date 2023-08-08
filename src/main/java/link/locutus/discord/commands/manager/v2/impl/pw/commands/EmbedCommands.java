package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.politicsandwar.graphql.model.Trade;
import link.locutus.discord.commands.bank.Offshore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.bindings.Operation;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.kefirsf.bb.conf.If;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EmbedCommands {
//    @Command(desc = "Add a command button to an embed")
//    @RolePermission(Roles.INTERNAL_AFFAIRS)
//    public String addCommand(Message message, String label, CommandRef command, @Default CommandBehavior behavior, @Default TextChannel output) {
//
//    }

    /*
    @Loto !embed "Set Brackets" "0 = Set yourself to 25/25
1 = Set yourself to 90/90
2 = Set yourself to 100/100
3 = Set all unblockaded 25/25 nations to 90/90 (Gov)" "~!SetBracket {nation_id} 25/25" "~!SetBracket {nation_id} 90/90" "~!SetBracket {nation_id} 100/100" "~$nation set taxbracket tax_id=19107,tax_id=19091,#isblockaded=0 90/90"
     */

    /*
    !embed "Set your required status for beige alerts" "0 = online
1 = Online or Away
2 = Online, Away or DoNotDisturb
3 = Any status
>results in #🤖│war-bot" "#🤖│war-bot ~$beigeAlertRequiredStatus ONLINE" "#🤖│war-bot ~$beigeAlertRequiredStatus ONLINE_AWAY" "#🤖│war-bot ~$beigeAlertRequiredStatus ONLINE_AWAY_DND" "~$beigeAlertRequiredStatus ANY"
     */

    /*
    !embed "Econ Shortcuts" "0 = Check deposits
1 = Nation checkup
2 = Check ROI
3 = Disburse funds for 3 days
`Open a grant request if you are unable to disburse`
#grant-requests

Results in #econ-bot" "#econ-bot ~!depo %user%" "#econ-bot ~!checkup %user%" "#econ-bot ~!ROI %user% 120" "#econ-bot ~!disburse %user% 3 -f"
     */

    /*
    !embed "Request a grant or loan" "0 = Project grant
1 = Infra/Land/Building grant
2 = City grant
3 = Reimburse deposits after a war
4 = Warchest (before fighting a war)
5 = **REBUILD GRANT**
6 = Other" "~!channel project-{nation_id} grants-2,grants-3 project_grant -a -e -p" "~!channel build-{nation_id} grants-2,grants-3 build_grant -a -e -p" "~!channel city-{nation_id} grants-2,grants-3 city_grant -a -e -p" "~!channel reimburse-{nation_id} grants-2,grants-3 reimburse_grant -a -e -p" "~!channel warchest-{nation_id} grants-2,grants-3 warchest_grant -a -e -p" "~!channel other-{nation_id} grants-2,grants-3 rebuild -a -e -p" "~!channel other-{nation_id} grants-2,grants-3 other_grant -a -e -p"
     */

    /*
    !embed "Find targets" "**------ RAID TARGETS ------**
Click :zero: for safe inactive nones/apps
Click :one: to include inactives in alliances
Click :two: to include nations on beige
Click :three: to include actives with minimal ground
Click :four: to include actives with minimal ground (2d inactive)
Click :five: to include actives losing their current wars

**------ Attrition/War Targets ------**
Click :six: for **high infra** (attrition) targets
Click :seven:  for standard war targets

*ping a gov member if you'd like any help/advice
Results are sorted best to last in #:robot:│war-bot" "#:robot:│war-bot ~!raid 10" "#:robot:│war-bot ~!raid * 25" "#:robot:│war-bot ~!raid * 25 -beige<24" "#:robot:│war-bot ~!raid #tank%<20,#soldier%<40,* 25 -a -w" "#:robot:│war-bot ~!raid #tank%<20,#soldier%<40,* 25 -2d -w" "#:robot:│war-bot ~!raid #def>0,#strength<1,* 25 -a -w" "#:robot:│war-bot ~!damage ~enemies" "#:robot:│war-bot ~!war ~enemies"
     */

    // TODO disburse to tax brackets

    /*
    !embed "Find targets" "**------ RAID TARGETS ------**
Click  for safe inactive nones/apps
Click  to include inactives in alliances
Click  to include nations on beige
Click  to include actives with minimal ground
Click  to include actives with minimal ground (2d inactive)
Click  to include actives losing their current wars
Click  to find unprotected nations (be careful with this)

*ping a gov member if you'd like any help/advice
Results are sorted best to last in <#995168236213633024>" "<#995168236213633024> ~!raid 10" "<#995168236213633024> ~!raid * 25" "<#995168236213633024> ~!raid * 25 -beige<24" "<#995168236213633024> ~!raid #tank%<20,#soldier%<40,* 25 -a -w" "<#995168236213633024> ~!raid #tank%<20,#soldier%<40,* 25 -2d -w" "<#995168236213633024> ~!raid #def>0,#strength<1,* 25 -a -w" "<#995168236213633024> ~$unprotected * -a -c 90"
     */
    @Command(desc="Makes a raid panel, which is a discord embed with buttons for different options for finding raid targets")
    @RolePermission(Roles.ADMIN)
    public void raid(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel) {
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        String title = "Find Raid Targets";
        String body = """
                        Press `7d_app` for inactive nones/apps
                        Press `7d_members` for inactives in alliances
                        Press `7d_beige` for nations on beige
                        Press `ground` for actives with low ground
                        Press `2d_ground` for 2d inactives with low ground
                        Press `losing` for actives losing their current wars
                        Press `unprotected` for with weak or no available counters
                        """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        CM.war.find.raid app = CM.war.find.raid.cmd.create(
                null, "10", null, null, null, null, null, null, null, null, null);
        CM.war.find.raid members = CM.war.find.raid.cmd.create(
                "*", "25", null, null, null, null, null, null, null, null, null);
        CM.war.find.raid beige = CM.war.find.raid.cmd.create(
                "*", "25", null, null, "24", null, null, null, null, null, null);
        CM.war.find.raid ground = CM.war.find.raid.cmd.create(
                "#tank%<20,#soldier%<40,*", "25", "0d", "true", null, null, null, null, null, null, null);
        CM.war.find.raid ground_2d = CM.war.find.raid.cmd.create(
                "#tank%<20,#soldier%<40,*", "25", "2d",  "true", null,null, null, null, null, null, null);
        CM.war.find.raid losing = CM.war.find.raid.cmd.create(
                "#def>0,#strength<1,*", "25", "0d", "true", null, null, null, null, null, null, null);
        CM.war.find.unprotected unprotected = CM.war.find.unprotected.cmd.create(
                "*", "25", null, "true", null,  null, "90", null, null);

        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;
        io.create().embed(title, body)
                .commandButton(behavior, channelId, app, "7d_app")
                .commandButton(behavior, channelId, members, "7d_members")
                .commandButton(behavior, channelId, beige, "7d_beige")
                .commandButton(behavior, channelId, ground, "ground")
                .commandButton(behavior, channelId, ground_2d, "2d_ground")
                .commandButton(behavior, channelId, losing, "losing")
                .commandButton(behavior, channelId, unprotected, "unprotected")
                .send();
    }

        /*
    @Locutus#7602 !embed "Blockade Target & Requests" "**Request your blockade broken**
See e.g.: `/war blockade request diff: 3d note: some reason`
0 = Low on resources
1 = Need to deposit
2 = Broke
---
3 = Find enemies w/ blockades
3 = Find enemies w/ blockades on unpowered allies
See e.g: `/war blockade find allies: ~allies numships: 250`

>results in #milcom-bot"
"#milcom-bot ~$war blockade request 3d 'Low on resources'"
"#milcom-bot ~$war blockade request 3d 'need to deposit'"
"#milcom-bot ~$war blockade request 3d 'broke'"
"#milcom-bot ~$war blockade find ~allies,#active_m<2880 -r 10"
"#milcom-bot ~$war blockade find ~allies,#ispowered=0,#active_m<2880 -r 10"
     */
        @Command(desc="Blockader Target & Requests discord embed template")
        @RolePermission(Roles.ADMIN)
        public void unblockadeRequests(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel) {
            if (db.getCoalition(Coalition.ALLIES).isEmpty()) {
                throw new IllegalArgumentException("No `" + Coalition.ALLIES.name() + "` coalition. See " + CM.coalition.create.cmd.toSlashMention());
            }
            db.getOrThrow(GuildKey.UNBLOCKADE_REQUESTS);
            db.getOrThrow(GuildKey.UNBLOCKADED_ALERTS);
            if (Roles.UNBLOCKADED_ALERT.toRole(db) == null) {
                throw new IllegalArgumentException(Roles.UNBLOCKADED_ALERT.toDiscordRoleNameElseInstructions(db.getGuild()));
            }

            Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
            String title = "Blockade Target & Requests";
            String body = """
                    **Request your blockade broken**
                    See e.g.: """ + CM.war.blockade.request.cmd.create("3d", "some reason", null).toSlashCommand(true)+ """
                    Press `Low` if low on resources
                    Press `deposit` if you need to deposit
                    Press `broke` if you are out of resources
                    ---
                    Press `break` to find enemies w/ blockades
                    Press `unpowered` to find enemies w/ blockades on unpowered allies
                    """;
            body += "\nSee e.g: " + CM.war.blockade.find.cmd.create("~allies", null, "250", null).toSlashCommand();

            if (channelId != null) {
                body += "\n\n> Results in <#" + channelId + ">";
            }

            CM.war.blockade.request low = CM.war.blockade.request.cmd.create("3d", "Low on resources", null);
            CM.war.blockade.request deposit = CM.war.blockade.request.cmd.create("3d", "Need to deposit", null);
            CM.war.blockade.request broke = CM.war.blockade.request.cmd.create("3d", "Broke", null);
            CM.war.blockade.find breakCmd = CM.war.blockade.find.cmd.create("~allies,#active_m<2880", null, null, "10");
            CM.war.blockade.find breakUnpowered = CM.war.blockade.find.cmd.create("~allies,#ispowered=0,#active_m<2880", null, null, "10");

            CommandBehavior behavior = CommandBehavior.UNDO_REACTION;
            io.create().embed(title, body)
                    .commandButton(behavior, channelId, low, "low")
                    .commandButton(behavior, channelId, deposit, "deposit")
                    .commandButton(behavior, channelId, broke, "broke")
                    .commandButton(behavior, channelId, breakCmd, "break")
                    .commandButton(behavior, channelId, breakUnpowered, "unpowered")
                    .send();
        }

        @Command(desc="Econ panel for members")
        public void memberEconPanel(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel) {
            Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
            String title = "Econ Panel";
            String body = """
                    Press `offshore` to send funds offshore
                    Press `balance` to view your deposits
                    Press `breakdown` to view your deposits breakdown
                    Press `tax` to view your tax rate 
                    Press `revenue` to check your revenue
                    Press `optimal` to optimize your build (same mmr/infra)
                    Press `price` to check the trade price
                    Press `margin` to check the trade margin
                    Press `profit` to check your trade profit
                    """;

            if (channelId != null) {
                body += "\n\n> Results in <#" + channelId + ">";
            }

            CM.offshore.send send = CM.offshore.send.cmd.create(null, null, null);
            CM.deposits.check deposits = CM.deposits.check.cmd.create("{nation_id}", null, null, null, null, null, null, null, null, null);
            CM.deposits.check depositsBreakdown = CM.deposits.check.cmd.create("{nation_id}", null, null, null, null, "true", null, null, null, null);
            CM.tax.info taxInfo = CM.tax.info.cmd.create("{nation_id}");
            CM.nation.revenue revenue = CM.nation.revenue.cmd.create("{nation_id}", "true", null);
            CM.city.optimalBuild optimalbuild = CM.city.optimalBuild.cmd.create("{city 1}", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            CM.trade.price tradeprice = CM.trade.price.cmd.create();
            CM.trade.margin trademargin = CM.trade.margin.cmd.create(null);
            CM.trade.profit tradeprofit = CM.trade.profit.cmd.create("{nation_id}", "7d");

            CommandBehavior behavior = CommandBehavior.UNDO_REACTION;
            io.create().embed(title, body)
                    .commandButton(behavior, channelId, send, "offshore")
                    .commandButton(behavior, channelId, deposits, "balance")
                    .commandButton(behavior, channelId, depositsBreakdown, "breakdown")
                    .commandButton(behavior, channelId, taxInfo, "tax_info")
                    .commandButton(behavior, channelId, revenue, "revenue")
                    .commandButton(behavior, channelId, optimalbuild, "optimal")
                    .commandButton(behavior, channelId, tradeprice, "price")
                    .commandButton(behavior, channelId, trademargin, "margin")
                    .commandButton(behavior, channelId, tradeprofit, "profit")
                    .send();

        }




//    Spy embed with spy - airplane - tank - ship spying
//    Winning target being high average infra, high mil, low mil, off beige soon
//
//    @Command(desc="Enemy war targets when it is even and unknowable\n" +
//            "Prioritizes overextended enemies\n" +
//            "To find contestable range, see: strengthTierGraph")
//    @RolePermission(Roles.ADMIN)
//    public void warEqual(@Me User user, @Me GuildDB db, @Me IMessageIO io,
//                         @Arg("If the cutoff is greater or less than the score") Operation greaterOrLess,
//                         @Arg("The score at which the conflict is not contestable")
//                         double score, @Default MessageChannel outputChannel, @Switch("d") boolean resultsInDm) {
//
//    }
//


    /*
    @Locutus#7602 !embed "High infra enemies" "0 = Active enemies
1 = Weak enemies
2 = No navy
3 = no vital defense system
4 = no iron dome
5 = no VDS or ID" "~$war find damage ~enemies"
"~$war find damage ~enemies -w -i -a"
"~$war find damage ~enemies -n"
"~$war find damage ~enemies,#vds=0"
"~$war find damage ~enemies,#id=0"
"~$war find damage ~enemies,#id=0,#vds=0"
     */
    @Command(desc="High infra targets where you are losing\n" +
            "To find contestable range, see: strengthTierGraph")
    @RolePermission(Roles.ADMIN)
    public void warGuerilla(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel) {
        if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            throw new IllegalArgumentException("No `" + Coalition.ENEMIES.name() + "` coalition. See " + CM.coalition.create.cmd.toSlashMention());
        }
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String title = "High infra enemies";
        String body = """
                Press `active` for active enemies
                Press `weak` for weak enemies
                Press `no_navy` for enemies w/o navy
                Press `no_vds` for enemies w/o vital defense system
                Press `no_id` for enemies w/o iron dome
                Press `no_vds_id` for enemies w/o vds & id
                """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;

        CM.war.find.damage damage = CM.war.find.damage.cmd.create(
                "~enemies", null, null, null, null, null, null, null, null, null, null);
        CM.war.find.damage damageWeak = CM.war.find.damage.cmd.create(
                "~enemies", "true", "true", "true", null, null, null, null, null, null, null);
        CM.war.find.damage damageNoNavy = CM.war.find.damage.cmd.create(
                "~enemies", null, null, null, "true", null, null, null, null, null, null);
        CM.war.find.damage damageNoVDS = CM.war.find.damage.cmd.create(
                "~enemies,#vds=0", null, null, null, null, null, null, null, null, null, null);
        CM.war.find.damage damageNoID = CM.war.find.damage.cmd.create(
                "~enemies,#iron_dome=0", null, null, null, null, null, null, null, null, null, null);
        CM.war.find.damage damageNoVDSID = CM.war.find.damage.cmd.create(
                "~enemies,#iron_dome=0,#vds=0", null, null, null, null, null, null, null, null, null, null);

        io.create().embed(title, body)
                .commandButton(behavior, channelId, damage, "active")
                .commandButton(behavior, channelId, damageWeak, "weak")
                .commandButton(behavior, channelId, damageNoNavy, "no_navy")
                .commandButton(behavior, channelId, damageNoVDS, "no_vds")
                .commandButton(behavior, channelId, damageNoID, "no_id")
                .commandButton(behavior, channelId, damageNoVDSID, "no_vds_id")
                .send();
    }

            /*
            @Locutus#7602 !embed "War Finder" "**If you are above 5,500 score, ONLY go for easy targets**
(Or if you have defensive wars, and dont want to over extend)
0 = Easy enemies
**If you are below 5.5k score, go for priority targets**
2 = High priority
3 = Lower priority
**If you are weak, and want to stat pad**
4 = Weak enemies
5 = High infra enemies

>results in #raid-results" "#raid-results ~!war ~enemies,#off>0 -e" "#raid-results ~!war ~enemies,#off>0,#score<7333,#attackingenemyofscore<5500,#attacking3/4strengthenemyofscore<5500 -p" "#raid-results ~!war ~enemies,#off>0,#score<7333,#attackingenemyofscore<5500,#attacking3/4strengthenemyofscore<5500 -e -p" "#raid-results ~!war ~enemies -i -a -e -f" "#raid-results ~!damage ~enemies,#active_m>2880||~enemies,#score>7333||~enemies,#barracks=0,#off=0 -i -a"
             */
    @Command(desc="Enemy war targets where a score range is not contestable\n" +
            "To find contestable range, see: strengthTierGraph")
    @RolePermission(Roles.ADMIN)
    public void warContestedRange(@Me User user, @Me GuildDB db, @Me IMessageIO io,
                                  @Arg("If the cutoff is greater or less than the score") Operation greaterOrLess,
                                  @Arg("The score at which the conflict is not contestable")
                                  double score, @Default MessageChannel outputChannel, @Switch("d") boolean resultsInDm) {
        if (greaterOrLess == Operation.EQUAL || greaterOrLess == Operation.NOT_EQUAL) {
            if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
                throw new IllegalArgumentException("No " + Coalition.ENEMIES.name() + " coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
            }
            throw new IllegalArgumentException("Cannot use " + greaterOrLess + " in this command");
        }
        if (resultsInDm && outputChannel != null) {
            throw new IllegalArgumentException("Cannot specify both a channel and DM results");
        }
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        String title = "War Finder";
        String body = String.format("""
                **If you are %1$s %2$s score, ONLY go for easy targets**
                (Or if you have defensive wars, and dont want to over extend)
                Press `easy` for weaker enemies
                
                **If you are %1$s %2$s, go for priority targets**
                Press `high` for high priority enemies
                Press `low` for lower priority enemies
                
                **If you are weak or have no targets and want to stat pad**
                Press `weak` for weak enemies
                Press `infra` for high infra enemies
                """, greaterOrLess.toString(), MathMan.format(score));

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }
        if (resultsInDm) {
            body += "\n\n> Results in DM";
        }

        Operation opposite = greaterOrLess.opposite();

        String dmStr = resultsInDm ? "true" : null;
        CM.war.find.enemy easy = CM.war.find.enemy.cmd.create(
                "~enemies,#off>0", null, null, null, null, null, null, "true", null, dmStr, null);
        int scoreMax;
        if (greaterOrLess == Operation.GREATER || greaterOrLess == Operation.GREATER_EQUAL) {
            scoreMax = (int) Math.ceil(score / 0.75);
        } else {
            scoreMax = (int) Math.ceil(score * 0.75);
        }
        int scoreInt = (int) score;
        CM.war.find.enemy high = CM.war.find.enemy.cmd.create(
                "~enemies,#off>0,#score" + opposite + scoreMax + ",#attackingenemyofscore" + opposite + scoreInt + ",#attacking3/4strengthenemyofscore" + opposite + scoreInt, null, null, null, null, "true", null, null, null, dmStr, null);
        CM.war.find.enemy low = CM.war.find.enemy.cmd.create(
                "~enemies,#off>0,#score" + opposite + scoreMax + ",#attackingenemyofscore" + opposite + scoreInt + ",#attacking3/4strengthenemyofscore" + opposite + scoreInt, null, null, null, null, "true", null, "true", null, dmStr, null);
        CM.war.find.enemy weak = CM.war.find.enemy.cmd.create(
                "~enemies", null, null, "true", "true", null, null, "true", null, dmStr, "true");
        CM.war.find.damage infra = CM.war.find.damage.cmd.create(
                "~enemies,#active_m>2880||~enemies,#score" + greaterOrLess + scoreMax +"||~enemies,#barracks=0,#off=0", "true", "true", null, null, null, null, null, dmStr, null, null);

        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;
        io.create().embed(title, body)
                .commandButton(behavior, channelId, easy, "easy")
                .commandButton(behavior, channelId, high, "high")
                .commandButton(behavior, channelId, low, "low")
                .commandButton(behavior, channelId, weak, "weak")
                .commandButton(behavior, channelId, infra, "infra")
                .send();
    }

    // Spy embed with spy - airplane - tank - ship spying - auto
  @Command(desc="Enemy espionage finder discord embed template")
  @RolePermission(Roles.ADMIN)
  public void spyEnemy(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default @GuildCoalition String coalition, @Default MessageChannel outputChannel) {
        if (coalition == null) coalition = Coalition.ENEMIES.name();
        if (db.getCoalition(coalition).isEmpty()) {
            throw new IllegalArgumentException("No `" + coalition + "` coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String title = "Espionage Enemies";
        String body = """
                Press `spy` to target spies
                Press `airplane` to target airplanes
                Press `tank` to target tanks
                Press `ship` to target ships
                Press `missile` to target missiles
                Press `nuke` to target nukes
                --------------------
                Press `dmg` to target highest net dmg
                Press `kill` to targets highest kills
                """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

      CM.spy.find.target spy = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", SpyCount.Operation.SPIES.name(), null, null, null, null);
        CM.spy.find.target airplane = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", SpyCount.Operation.AIRCRAFT.name(), null, null, null, null);
        CM.spy.find.target tank = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", SpyCount.Operation.TANKS.name(), null, null, null, null);
        CM.spy.find.target ship = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", SpyCount.Operation.SHIPS.name(), null, null, null, null);
        CM.spy.find.target missile = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<2880", SpyCount.Operation.MISSILE.name(), null, null, null, null);
        CM.spy.find.target nuke = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<2880", SpyCount.Operation.NUKE.name(), null, null, null, null);
        CM.spy.find.target dmg = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", "*", null, null, null, null);
        CM.spy.find.target kill = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", "*", null, null, "true", null);

        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;

        io.create().embed(title, body)
            .commandButton(behavior, channelId, spy, "spy")
            .commandButton(behavior, channelId, airplane, "airplane")
            .commandButton(behavior, channelId, tank, "tank")
            .commandButton(behavior, channelId, ship, "ship")
            .commandButton(behavior, channelId, missile, "missile")
            .commandButton(behavior, channelId, nuke, "nuke")
            .commandButton(behavior, channelId, dmg, "dmg")
            .commandButton(behavior, channelId, kill, "kill")
            .send();
  }

  // Winning target being high average infra, high mil, low mil, off beige soon

    /*
    high: war find target ~enemies,#attacking=~allies,#attacking2/3strengthenemyofscore<99999 -p -e
    med: war find target ~enemies,#attacking=~allies -p -e
    low: war find target ~enemies -p -e

    easy: war find target ~enemies -e
    inactive: war find target ~enemies -e -i -a
    infra: war find damage ~enemies

    beige: war find ~enemies,#color=beige -i -a -b
     */
    @Command(desc="Enemy war targets when you are winning\n" +
            "Prioritizes down declares\n" +
            "To find contestable range, see: strengthTierGraph")
    @RolePermission(Roles.ADMIN)
    public void warWinning(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel, @Switch("d") boolean resultsInDm) {
        if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            throw new IllegalArgumentException("No " + Coalition.ENEMIES.name() + " coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String title = "War Targets";
        String body = """
                Press `high` for high priority enemies
                Press `med` for medium priority enemies
                Press `low` for low priority enemies
                --------------------
                Press `easy` to target easy enemies
                Press `inactive` to include all enemies
                Press `infra` to target high infra enemies
                Press `beige` to target enemies on beige
                """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        String dmStr = resultsInDm ? "true" : null;
        CM.war.find.enemy high = CM.war.find.enemy.cmd.create(
                "~enemies,#attacking=~allies,#attacking2/3strengthenemyofscore<99999", null, null, null, null, "true", null, "true", null, dmStr, null);
        CM.war.find.enemy med = CM.war.find.enemy.cmd.create(
                "~enemies,#attacking=~allies", null, null, null, null, "true", null, "true", null, dmStr, null);
        CM.war.find.enemy low = CM.war.find.enemy.cmd.create(
                "~enemies", null, null, null, null, "true", null, "true", null, dmStr, null);
        CM.war.find.enemy easy = CM.war.find.enemy.cmd.create(
                "~enemies", null, null, null, null, null, null, "true", null, dmStr, null);
        CM.war.find.enemy inactive = CM.war.find.enemy.cmd.create(
                "~enemies", null, null, "true", "true", null, null, "true", null, dmStr, null);
        CM.war.find.damage infra = CM.war.find.damage.cmd.create(
                "~enemies", "true", "true", null, null, null, null, null, null, dmStr, null);
        CM.war.find.enemy beige = CM.war.find.enemy.cmd.create(
                "~enemies,#color=beige", null, null, "true", "true", null, null, "true", null, dmStr, null);

        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;

        io.create().embed(title, body)
            .commandButton(behavior, channelId, high, "high")
            .commandButton(behavior, channelId, med, "med")
            .commandButton(behavior, channelId, low, "low")
            .commandButton(behavior, channelId, easy, "easy")
            .commandButton(behavior, channelId, inactive, "inactive")
            .commandButton(behavior, channelId, infra, "infra")
            .commandButton(behavior, channelId, beige, "beige")
            .send();
    }

    @Command(desc="Generates sheets for a coalition war:" +
            "- All enemies\n" +
            "- Priority enemies\n" +
            "- All allies\n" +
            "- Underutilized allies")
    @RolePermission(Roles.ADMIN)
    public void allyEnemySheets(ValueStore store, NationPlaceholders placeholders,
            @Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel,
                                @Default SpreadSheet allEnemiesSheet,
                                @Default SpreadSheet priorityEnemiesSheet,
                                @Default SpreadSheet allAlliesSheet,
                                @Default SpreadSheet underutilizedAlliesSheet) throws GeneralSecurityException, IOException {
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        if (db.getCoalition(Coalition.ALLIES).isEmpty()) {
            throw new IllegalArgumentException("No `" + Coalition.ALLIES.name() + "` coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
        if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            throw new IllegalArgumentException("No `" + Coalition.ENEMIES.name() + "` coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
        if (allEnemiesSheet == null) {
            allEnemiesSheet = SpreadSheet.create(db, SheetKeys.ENEMY_SHEET);
        }
        if (priorityEnemiesSheet == null) {
            priorityEnemiesSheet = SpreadSheet.create(db, SheetKeys.PRIORITY_ENEMY_SHEET);
        }
        if (allAlliesSheet == null) {
            allAlliesSheet = SpreadSheet.create(db, SheetKeys.ALLY_SHEET);
        }
        if (underutilizedAlliesSheet == null) {
            underutilizedAlliesSheet = SpreadSheet.create(db, SheetKeys.UNDERUTILIZED_ALLY_SHEET);
        }

        Map.Entry<String, List<String>> allEnemies = Map.entry(
                "~enemies,#position>1,#vm_turns=0,#active_m<10800",
                Arrays.asList(
                        "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "'=MROUND({score}/1.75,1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/1.75,1)'",
                        "{color}",
                        "{beigeturns}",
                        "{soldiers}",
                        "{tanks}",
                        "{aircraft}",
                        "{ships}",
                        "{missiles}",
                        "{nukes}",
                        "{spies}",
                        "={active_m}/60",
                        "{avg_daily_login}",
                        "'=\"{mmr}\"'",
                        "{dayssincelastdefensivewarloss}",
                        "{dayssincelastoffensive}",
                        "{dayssince3consecutivelogins}",
                        "{dayssince4consecutivelogins}",
                        "{dayssince5consecutivelogins}"
                )
        );

        Map.Entry<String, List<String>> allAllies = Map.entry(
                "~allies,#position>1,#vm_turns=0,#active_m<10800",
                Arrays.asList(
                        "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "{beigeturns}",
                        "'=MROUND({score}/1.75,1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/1.75,1)'",
                        "{soldiers}",
                        "{tanks}",
                        "{aircraft}",
                        "{ships}",
                        "{missiles}",
                        "{nukes}",
                        "{spies}",
                        "={active_m}/60",
                        "{avg_daily_login}",
                        "'=\"{mmr}\"'",
                        "{dayssincelastdefensivewarloss}",
                        "{dayssincelastoffensive}",
                        "{dayssince3consecutivelogins}"
                )
        );

        Map.Entry<String, List<String>> priorityEnemies = Map.entry(
                "#cities>10,~enemies,#active_m<2880,#def<3,#off>0,#strength>0.7,#vm_turns=0,#isbeige=0,#attacking=~allies",
                Arrays.asList(
                        "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{strongestenemyrelative}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "'=MROUND({score}/1.75,1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/1.75,1)'",
                        "{soldiers}",
                        "{tanks}",
                        "{aircraft}",
                        "{ships}",
                        "{missiles}",
                        "{nukes}",
                        "{spies}",
                        "={active_m}/60",
                        "{avg_daily_login}",
                        "'=\"{mmr}\"'"
                )
        );

        Map.Entry<String, List<String>> underutilizedAllies = Map.entry(
                "~allies,#active_m<2880,#freeoffensiveslots>0,#tankpct>0.8,#aircraftpct>0.8,#strength>1.3,#vm_turns=0,#isbeige=0",
                Arrays.asList(
                        "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{strongestenemyrelative}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "{beigeturns}",
                        "'=MROUND({score}/1.75,1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/1.75,1)'",
                        "{soldiers}",
                        "{tanks}",
                        "{aircraft}",
                        "{ships}",
                        "{missiles}",
                        "{nukes}",
                        "{spies}",
                        "={active_m}/60",
                        "{avg_daily_login}",
                        "'=\"{mmr}\"'"
                )
        );

        String footer = "";
        if (outputChannel != null) {
            footer = "\n\n> Output in " + outputChannel.getAsMention();
        }

        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;

        io.create()
                .embed("All Enemies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                        CM.nation.sheet.NationSheet.cmd.create(
                                allEnemies.getKey(),
                                StringMan.join(allEnemies.getValue(), " "),
                                null,
                                "sheet:" + allEnemiesSheet.getSpreadsheetId()
                        ), "update").send();

        io.create().embed("All Allies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.create(
                        allAllies.getKey(),
                        StringMan.join(allAllies.getValue(), " "),
                        null,
                        "sheet:" + allAlliesSheet.getSpreadsheetId()

                ), "update").send();
        io.create().embed("Priority Enemies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.create(
                        priorityEnemies.getKey(),
                        StringMan.join(priorityEnemies.getValue(), " "),
                        null,
                        "sheet:" + priorityEnemiesSheet.getSpreadsheetId()

                ), "update").send();

        io.create().embed("Underutilized Allies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.create(
                        underutilizedAllies.getKey(),
                        StringMan.join(underutilizedAllies.getValue(), " "),
                        null,
                        "sheet:" + underutilizedAlliesSheet.getSpreadsheetId()

                ), "update").send();
//
//        UtilityCommands.NationSheet(store, placeholders, io, db, DiscordUtil.parseNations(db.getGuild(), allEnemies.getKey()), allEnemies.getValue(), false, allEnemiesSheet);
//        UtilityCommands.NationSheet(store, placeholders, io, db, DiscordUtil.parseNations(db.getGuild(), allAllies.getKey()), allAllies.getValue(), false, allAlliesSheet);
//        UtilityCommands.NationSheet(store, placeholders, io, db, DiscordUtil.parseNations(db.getGuild(), priorityEnemies.getKey()), priorityEnemies.getValue(), false, priorityEnemiesSheet);
//        UtilityCommands.NationSheet(store, placeholders, io, db, DiscordUtil.parseNations(db.getGuild(), underutilizedAllies.getKey()), underutilizedAllies.getValue(), false, underutilizedAlliesSheet);
    }

    @Command(desc = "Discord embed for sheet to update ally and enemy spy counts, generate and send spy blitz targets")
    public void spySheets(@Me GuildDB db, @Me IMessageIO io, @Default("spyops") @GuildCoalition String allies, @Default MessageChannel outputChannel, @Default SpreadSheet spySheet) throws GeneralSecurityException, IOException {
        if (allies == null) allies = Coalition.ALLIES.name();

        if (db.getCoalition(allies).isEmpty()) {
            throw new IllegalArgumentException("No `" + allies + "` coalition found (for enemy targets). See: " + CM.coalition.create.cmd.toSlashMention());
        }
        if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            throw new IllegalArgumentException("No `" + Coalition.ENEMIES.name() + "` coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
//        1. Update active ally spy counts
//        2. Update active enemy spy counts
//        3. Spy blitz sheet (spyops coalition)
//        4. Mail targets (spyops coalition)

        String title = "Spy Sheets";

        String footer = "\n\n(do not spam)";
        if (outputChannel != null) {
            footer += "\n\n> Output in " + outputChannel.getAsMention();
        }

        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String columns = StringMan.join(Arrays.asList(
                "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                "{alliancename}",
                "{relativestrength}",
                "{strongestenemyrelative}",
                "{cities}",
                "{score}",
                "{off}",
                "{def}",
                "'=MROUND({score}/1.75,1) & \"-\" & MROUND({score}/0.75,1)'",
                "'=MROUND({score}/0.4,1) & \"-\" & MROUND({score}/2.5,1)'",
                "{soldiers}",
                "{tanks}",
                "{aircraft}",
                "{ships}",
                "{missiles}",
                "{nukes}",
                "{spies}",
                "{war_policy}",
                "={active_m}/60",
                "{avg_daily_login}",
                "'=\"{mmr}\"'"
        ), " ");

        String spySheetId = spySheet != null ? spySheet.getSpreadsheetId() : SpreadSheet.create(db, SheetKeys.SPYOP_SHEET).getSpreadsheetId();

        io.create().embed("Update ally", "Press `allies` to update active ally spy counts" + footer)
                .commandButton(behavior, channelId, CM.nation.sheet.NationSheet.cmd.create(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        columns,
                        "true",
                        null
                ), "allies").send();

        io.create().embed("Update enemy", "Press `enemies` to update active enemy spy counts" + footer)
                .commandButton(behavior, channelId, CM.nation.sheet.NationSheet.cmd.create(
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        columns,
                        "true",
                        null
                ), "enemies").send();

        io.create().embed("Blitz priority kills", "Press `blitz_kill` for a spy blitz sheet focusing spies/air" + footer)
        .commandButton(behavior, channelId, CM.spy.sheet.generate.cmd.create(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        StringMan.join(Arrays.asList(SpyCount.Operation.SPIES.name(),SpyCount.Operation.AIRCRAFT.name()), ","),
                        "true",
                        "true",
                        "true",
                        "sheet:" + spySheetId,
                        null,
                        null
                ), "blitz_kill").send();

        io.create().embed("Blitz priority damage", "Press `blitz_dmg` for a spy blitz sheet focusing damage" + footer)
                .commandButton(behavior, channelId, CM.spy.sheet.generate.cmd.create(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        null,
                        "true",
                        "true",
                        "true",
                        "sheet:" + spySheetId,
                        null,
                        null
                ), "blitz_dmg").send();
        io.create().embed("Validate and send", """
                        Press `check` to validate spy blitz sheet
                        Press `mail` to mail targets
                        """ + footer)
                .commandButton(behavior, channelId, CM.spy.sheet.validate.cmd.create(
                        "sheet:" + spySheetId,
                        null,
                        "~" + allies
                ), "validate")
        .commandButton(behavior, channelId, CM.mail.targets.cmd.create(
                        null,
                        "sheet:" + spySheetId,
                        "~" + allies,
                        null,
                        "true",
                        null,
                        null,
                        null,
                        null
                ), "mail").send();
    }
}
