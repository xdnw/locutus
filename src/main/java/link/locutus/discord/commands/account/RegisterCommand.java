package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
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
    private final DiscordDB db;

    public RegisterCommand(DiscordDB db) {
        super("validate", "register", "verify", CommandCategory.USER_SETTINGS);
        this.db = db;
    }

    @Override
    public String help() {
        return "!validate <nation-id>";
    }

    @Override
    public String desc() {
        return "Register your politicsandwar nation";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        User user = event.getAuthor();
        if (args.size() >= 2) {
            User mention = DiscordUtil.getMention(args.get(0));
            if (mention == null) {
                return "To manually register, use !validate @mention <nation-link>";
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
                        Member mentionMember = guild.getMember(mention);

                        if (mentionMember == null) return "User is not in server";
                        if (appRole == null) return "No applicant role exists";
                        if (!mentionMember.getRoles().contains(appRole)) return "User does not have applicant role";
                        if (DiscordUtil.getNation(mention) != null) return "User is already registered";
                        DBNation mentionNation = DBNation.byId(nationId);
                        if (mentionNation == null) return "Invalid nation";
                        if (mentionNation.getUser() == null) return "Nation already registered";
                        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
                        Integer aaId = guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID);
                        if (aaId == null) aaId = me.getAlliance_id();
                        if (aaId != mentionNation.getAlliance_id()) return "Nation has not applied ingame";
                    }
                }
            }

            return register(event, mention, nationId, true);
        }
        if (args.size() != 1) {
            DBNation nation = DiscordUtil.getNation(event);
            if (nation == null) {
                return "Usage: `!validate <nation link>`";
            } else {
                return nation.register(user, guild, false);
            }
        }
        if (args.get(0).equalsIgnoreCase("*")) {
            if (!Roles.ADMIN.hasOnRoot(event.getAuthor())) {
                return "No perm";
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

                if (db.getUserFromNationId(nation.getNation_id()) != null) continue;
                if (db.getUserFromDiscordId(member.getIdLong()) != null) continue;

                String fullDiscriminator = member.getUser().getName() + "#" + member.getUser().getDiscriminator();
                PNWUser pnwUser = new PNWUser(nation.getNation_id(), member.getIdLong(), fullDiscriminator);

                db.addUser(pnwUser);
            }

            return "";
        }

        Integer nationId = DiscordUtil.parseNationId(args.get(0));
        if (nationId == null) {
            return "Must be an nation id or link: ``" + args.get(0) + "`" + "`";
        }

        return register(event, user, nationId, false);
    }

    public String register(MessageReceivedEvent event, User user, int nationId, boolean force) throws IOException {
        boolean notRegistered = DiscordUtil.getUserByNationId(nationId) == null;

        String fullDiscriminator = user.getName() + "#" + user.getDiscriminator();

        String errorMsg = "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>\n" +
                "2. Scroll down to where it says Discord Username:\n" +
                "3. Put your discord username `" + fullDiscriminator + "` in the field\n" +
                "4. Click save\n" +
                "5. Run the command `!validate " + nationId + "` again";

        long id = user.getIdLong();
        boolean checkId = false;

        PNWUser existingUser = Locutus.imp().getDiscordDB().getUser(null, user.getName(), fullDiscriminator);
        if (existingUser != null) {
            if (existingUser.getDiscordId() == null && !existingUser.getDiscordId().equals(id)) {
                errorMsg = "That nation is already registered to another user!" +
                        "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>\n" +
                        "2. Scroll down to where it says Discord Username:\n" +
                        "3. Put your **DISCORD ID** `" + user.getIdLong() + "` in the field\n" +
                        "4. Click save\n" +
                        "5. Run the command `!validate " + nationId + "` again";
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
                    return "Unable to fetch username. Please ensure you have `Discord Username` set in <https://politicsandwar.com/nation/edit/>";
                }
                boolean success = true;

                String userName = user.getName() + "#" + user.getDiscriminator();
                if (checkId) {
                    userName = "" + user.getIdLong();
                }
                if (!userName.equalsIgnoreCase(pnwDiscordName)) {
                    return "Your user doesnt match: `" + pnwDiscordName + "` != `" + userName + "`\n\n" + errorMsg;
                }

                PNWUser pnwUser = new PNWUser(nationId, id, fullDiscriminator);
                db.addUser(pnwUser);
                String registerMessage = nation.register(user, event.isFromGuild() ? event.getGuild() : null, notRegistered);;

                if (!success) {
                    registerMessage += "\n" + "Error: " + errorMsg;
                }
                return registerMessage;
            } catch (InsufficientPermissionException | HierarchyException e) {
                return e.getMessage();
            } catch (IOException e) {
                return e.getMessage();
            } catch (Throwable e) {
                e.printStackTrace();
                return "Error (see console) <@" + Settings.INSTANCE.ADMIN_USER_ID + ">";
            }
        }

        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);

        PNWUser pnwUser = new PNWUser(nationId, id, fullDiscriminator);
        db.addUser(pnwUser);
        return nation.register(user, event.isFromGuild() ? event.getGuild() : null, notRegistered);
    }
}