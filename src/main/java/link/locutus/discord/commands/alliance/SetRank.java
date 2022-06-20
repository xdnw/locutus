package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SetRank extends Command {
    public SetRank() {
        super("rank", "setrank", "rankup", CommandCategory.GOV, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "setrank <user> <rank>";
    }

    @Override
    public String desc() {
        return "Set the rank of a player in the alliance. Ranks: " + StringMan.getString(Rank.values()) + "\n" +
                "Add `-d` to not set rank on discord";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return db.isValidAlliance() && db.hasAuth() && (Roles.INTERNAL_AFFAIRS.has(user, server) || Roles.INTERNAL_AFFAIRS_STAFF.has(user, server));
    }

    PassiveExpiringMap<Long, Integer> demotions = new PassiveExpiringMap<Long, Integer>(60, TimeUnit.MINUTES);;

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(event);
        }

        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) {
            return "Invalid user: `" + args.get(0) + "`";
        }

        int myPos = (me == null || me.getAlliance_id() != nation.getAlliance_id() ? Rank.MEMBER.id : me.getPosition());

        Rank rank;
        try {
            rank = Rank.valueOf(args.get(1).toUpperCase());
        } catch (IllegalArgumentException ignore) {
            return "Invalid rank `" + args.get(1) + "`" + ". Options: " + StringMan.getString(Rank.values());
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);

        if (Rank.MEMBER.equals(rank) && db.isWhitelisted()) {
            if (!flags.contains('f')) {
                if (nation.getScore() < 256) {
                    nation.getPnwNation();
                }
                List<String> checks = new ArrayList<>();
                if (nation.isGray()) {
                    checks.add("Nation is gray (use `-f` to override this)");
                }
                if (nation.getCities() < 3) {
                    checks.add( "Nation has not bought up to 3 cities (use `-f` to override this)");
                }
                if (nation.getCities() < 10 && nation.getOff() < 5 && db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) {
                    checks.add( "Nation has not declared up to 5 raids ( use `-f` to override this)");
                }
                if (nation.getCities() > 3 && nation.getCities() < 10 && nation.getSoldierPct() < 0.25) {
                    checks.add( "Nation has not bought soldiers (use `-f` to override this)");
                }

                if (nation.getCities() >= 10 && nation.getAircraftPct() < 0.18) {
                    checks.add( "Nation has not bought aircraft (use `-f` to override this)");
                }
                if (nation.getCities() == 10 && nation.getSoldierPct() < 0.25 && nation.getTankPct() < 0.25) {
                    checks.add( "Nation has not bought tanks or soldiers (use `-f` to override this)");
                }
                if (nation.getCities() <= 5 && !nation.getMMRBuildingStr().startsWith("5")) {
                    checks.add( "Nation does not have 5 barracks (use `-f` to override this)");
                }
                if (nation.getCities() >= 10) {
                    String mmr = nation.getMMRBuildingStr();
                    if (!mmr.matches("5.5.") && !mmr.matches(".[2-5]5.")) {
                        checks.add( "Nation is on insufficient MMR (use `-f` to override this)");
                    }
                }

                if (!checks.isEmpty()) {
                    return "The following checks have failed:\n" + StringMan.join(checks, "\n - ");
                }

                if (db.getOffshore() != null) {
                    String title = "Disburse 3 days";
                    String body = "Use this once they have a suitable city build & color to send resources for the next 5 days";
                    String emoji = "\u2705";
                    String cmd = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "disburse " + nation.getNation() + " 3 -f";

                    DiscordUtil.createEmbedCommand(event.getChannel(), title, body, emoji, cmd);
                }
            }
        }

        User discordUser = nation.getUser();

        if ((rank == Rank.LEADER || rank == Rank.HEIR) && (nation.getPosition() < Rank.OFFICER.id || discordUser == null || !Roles.ADMIN.hasOnRoot(author) || !Roles.ADMIN.has(author, guild))) {
            return "You need ingame officer and discord admin to promote to heir+";
        }

        if (rank != Rank.MEMBER && rank != Rank.APPLICANT && rank != Rank.INVITE && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild)) return "No permission to set this rank";

        if (!Roles.ADMIN.has(author, guild) && rank.id >= Rank.OFFICER.id) {
            return "You need discord admin to promote above member ingame";
        }

        if (!Roles.ADMIN.hasOnRoot(author) && myPos < rank.id) {
            return "You are lower rank ingame than: " + rank;
        }

        Auth auth = db.getAuth();
        if (auth == null) return "This guild is not authenticated";

        if (!Roles.ADMIN.has(author, guild) && nation.getActive_m() < 2880 && (auth.getAllianceId() == nation.getAlliance_id()) && (rank == Rank.REMOVE || rank == rank.BAN)) {
            return "You need discord admin to remove or ban active members (set them to applicant first)";
        }

        boolean admin = Roles.ADMIN.has(author, guild) || myPos >= Rank.HEIR.id;
        if (!admin && nation.getActive_m() < 4880 && rank.id < Rank.MEMBER.id && nation.getPosition() > 1 && nation.getAlliance_id() == auth.getAllianceId()) {
            int currentDemotions = demotions.getOrDefault(author.getIdLong(), 0);
            if (currentDemotions > 2) {
                return "Please get an admin to demote multiple active nations, or do so ingame. " + Roles.ADMIN.toRole(guild);
            }
            demotions.put(author.getIdLong(), currentDemotions + 1);
        }

        if (discordUser != null) {
            if (discordUser.getIdLong() != event.getAuthor().getIdLong()) {
                if (!Roles.ADMIN.hasOnRoot(discordUser) && (nation.getPosition() > myPos || nation.getPosition() > Rank.OFFICER.id)) {
                    return "No couping please.";
                }
            }
        } else if (!flags.contains('f')) {
            return "No user registered to that nation. Did they use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "register` ? Add `-f` to override";
        }

        StringBuilder response = new StringBuilder();
        if (discordUser != null && !flags.contains('d')) {
            Member member = guild.getMember(discordUser);
            Role role = Roles.MEMBER.toRole(guild);
            if (member != null && role != null) {
                try {
                    if (rank.id > 1) {
                        RateLimitUtil.queue(guild.addRoleToMember(member, role));
                    } else {
                        RateLimitUtil.queue(guild.removeRoleFromMember(member, role));
                    }
                } catch (HierarchyException e) {
                    response.append(e.getMessage() + "\n");
                }
            }
        }

        int previousRank = nation.getPosition();
        String result = nation.setRank(auth, rank);
        if (result.contains("Set player rank ingame.") && previousRank <= Rank.APPLICANT.id && rank.id >= Rank.MEMBER.id) {
            db.getHandler().onSetRank(author, event.getGuildChannel(), nation, rank);
        }

        response.append(result);
        response.append("\nSee also `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "listAssignableRoles` / `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "addRole @user <role>`");
        return response.toString();
    }
}
