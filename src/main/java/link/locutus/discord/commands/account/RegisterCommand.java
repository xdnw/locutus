package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegisterCommand extends Command {

    private final DiscordDB discordDb;

    public RegisterCommand(DiscordDB db) {
        super("validate", "register", "verify", CommandCategory.USER_SETTINGS);
        this.discordDb = db;
    }

    @Override
    public String help() {
        return CM.register.cmd.toSlashMention();
    }

    @Override
    public String desc() {
        return "Register your in-game nation.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        User user = event.getAuthor();
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        if (args.size() >= 2) {
            User mention = DiscordUtil.getMention(args.get(0));
            if (mention == null) {
                return "To manually register, use " + Settings.commandPrefix(true) + "register @mention <nation-link>.";
            }
            Integer nationId = DiscordUtil.parseNationId(args.get(1));
            if (nationId == null) {
                return "Invalid nation: ``" + args.get(1) + "`" + "`";
            }

            if (!Roles.ADMIN.hasOnRoot(user)) {
                if (!Settings.INSTANCE.DISCORD.REGISTER_ANYONE.contains(author.getIdLong())) {

                    if (!Settings.INSTANCE.DISCORD.REGISTER_APPLICANTS.contains(author.getIdLong())) {
                        return usage();
                    } else {
                        Role appRole = Roles.APPLICANT.toRole(guild);
                        Role memberRole = Roles.MEMBER.toRole(guild);
                        Member mentionMember = guild.getMember(mention);

                        if (mentionMember == null) return "User is not in server.";
                        if (appRole == null && memberRole == null) return "No applicant or member role exists.";
                        if (!mentionMember.getRoles().contains(appRole) && !mentionMember.getRoles().contains(memberRole))
                            return "User does not have applicant role.";
                        if (DiscordUtil.getNation(mention) != null) return "User is already registered.";
                        DBNation mentionNation = DBNation.byId(nationId);
                        if (mentionNation == null) return "Invalid nation";
                        if (mentionNation.getUser() != null) return "Nation already registered: " + mentionNation.getNation() + " = " + mentionNation.getUser();
                        if (!guildDb.hasAlliance()) return "This guild is not registered to an alliance";
                        if (!guildDb.isAllianceId(mentionNation.getAlliance_id())) return "Nation has not applied ingame";
                    }
                }
            }

            return register(event, guildDb, mention, nationId, true);
        }
        if (args.size() != 1) {
            DBNation nation = DiscordUtil.getNation(event);
            if (nation == null) {
                return "Usage: " + CM.register.cmd.toSlashMention();
            } else {
                return nation.register(user, guildDb, false);
            }
        }
        if (args.get(0).equalsIgnoreCase("*")) {
            if (!Roles.ADMIN.hasOnRoot(event.getAuthor())) {
                return "No permission.";
            }

            Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
            Map<String, DBNation> byLeader = new HashMap<>();
            for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
                byLeader.put(entry.getValue().getLeader().toLowerCase(), entry.getValue());
                byLeader.putIfAbsent(entry.getValue().getNation().toLowerCase(), entry.getValue());
            }

            for (Member member : event.getGuild().getMembers()) {
                if (member.getUser().isBot()) continue;
                String nick = member.getNickname();
                String name = member.getUser().getName();

                DBNation nation = nick == null ? null : byLeader.get(nick.toLowerCase());
                if (nation == null) {
                    nation = byLeader.get(name.toLowerCase());
                }

                if (nation == null) {
                    continue;
                }

                if (nation.getUserId() != null) continue;
                if (discordDb.getUserFromDiscordId(member.getIdLong()) != null) continue;

                String fullDiscriminator = member.getUser().getName() + "#" + member.getUser().getDiscriminator();
                PNWUser pnwUser = new PNWUser(nation.getNation_id(), member.getIdLong(), fullDiscriminator);

                discordDb.addUser(pnwUser);
            }

            return "";
        }

        Integer nationId = DiscordUtil.parseNationId(args.get(0));
        if (nationId == null) {
            return "Must be an nation id or link: ``" + args.get(0) + "`" + "`";
        }

        return register(event, guildDb, user, nationId, false);
    }

    public String register(MessageReceivedEvent event, GuildDB db, User user, int nationId, boolean force) throws IOException {
        boolean notRegistered = DiscordUtil.getUserByNationId(nationId) == null;

        String fullDiscriminator = user.getName() + "#" + user.getDiscriminator();

        String errorMsg = "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>\n" +
                "2. Scroll down to where it says Discord Username:\n" +
                "3. Put your discord username `" + fullDiscriminator + "` in the field\n" +
                "4. Click save\n" +
                "5. Run the command " + CM.register.cmd.create(nationId + "").toSlashCommand() + " again";

        long id = user.getIdLong();
        boolean checkId = false;

        PNWUser existingUser = Locutus.imp().getDiscordDB().getUser(null, user.getName(), fullDiscriminator);
        if (existingUser != null) {
            if (existingUser.getDiscordId() != id) {
                errorMsg = "That nation is already registered to another user!" +
                        "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>\n" +
                        "2. Scroll down to where it says Discord Username:\n" +
                        "3. Put your **DISCORD ID** `" + user.getIdLong() + "` in the field\n" +
                        "4. Click save\n" +
                        "5. Run the command " + CM.register.cmd.create(nationId + "").toSlashCommand() + " again";
                checkId = true;
            }
        }

        if (!force) {
            try {
//                String endpoint = "" + Settings.INSTANCE.PNW_URL() + "/api/discord/getDiscordFromNation.php?access_key=%s&nation_id=%s";
//                endpoint = String.format(endpoint, Settings.INSTANCE.DISCORD.ACCESS_KEY, nationId);

//                String json = FileUtil.readStringFromURL(endpoint);
//                JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
//
//                boolean success = obj.get("success").getAsBoolean();
//                String error = null;
//                if (!obj.has("discord_username")) return "Unkown nation: " + nationId;
//
//                String pnwDiscordName = obj.get("discord_username").getAsString();
//                if (!success) {
//                    errorMsg = obj.get("err_msg") + "\n\n" + errorMsg;
//                }
//                if (success && (pnwDiscordName == null || pnwDiscordName.isEmpty())) {
//                    success = false;
//                }

                DBNation nation = new DBNation();
                nation.setNation_id(nationId);
                String pnwDiscordName = nation.fetchUsername();
                if (pnwDiscordName == null || pnwDiscordName.isEmpty()) {
                    return "Unable to fetch username. Please ensure you have `Discord Username` set in <https://politicsandwar.com/nation/edit/>.";
                }
                boolean success = true;

                String userName = user.getName() + "#" + user.getDiscriminator();
                if (checkId) {
                    userName = "" + user.getIdLong();
                }
                if (!userName.equalsIgnoreCase(pnwDiscordName) && !pnwDiscordName.contains("" + user.getIdLong())) {
                    return "Your user doesn't match: `" + pnwDiscordName + "` != `" + userName + "`\n\n" + errorMsg;
                }

                PNWUser pnwUser = new PNWUser(nationId, id, userName);
                discordDb.addUser(pnwUser);
                String registerMessage = nation.register(user, event.isFromGuild() ? db : null, notRegistered);

                if (!success) {
                    registerMessage += "\n" + "Error: " + errorMsg;
                }
                return registerMessage;
            } catch (InsufficientPermissionException | HierarchyException | IOException e) {
                return e.getMessage();
            } catch (Throwable e) {
                e.printStackTrace();
                return "Error: " + e.getMessage();
            }
        }

        DBNation nation = new DBNation();
        nation.setNation_id(nationId);

        PNWUser pnwUser = new PNWUser(nationId, id, fullDiscriminator);
        discordDb.addUser(pnwUser);
        return nation.register(user, db, notRegistered);
    }
}