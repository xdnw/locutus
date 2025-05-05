package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.register.cmd);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        User user = author;
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        if (args.isEmpty()) {
            if (me == null) {
                return "Usage: " + CM.register.cmd.toSlashMention();
            } else {
                return me.register(user, guildDb, false);
            }
        }
        if (args.size() == 2 && args.get(0).contains("<@") && args.get(0).contains(">")) {
            User mention = DiscordBindings.user(null, guild, args.get(0));
            int index = 1;
            if (mention == null) {
                mention = author;
                index = 0;
            }
            String nationLink = String.join(" ", args.subList(index, args.size()));
            DBNation nation = DiscordUtil.parseNation(nationLink, true, true, guild);
            if (nation == null) {
                return "Invalid nation: ``" + nationLink + "`" + "`";
            }

            if (!Roles.ADMIN.hasOnRoot(user) && mention.getIdLong() != author.getIdLong()) {
                if (!Settings.INSTANCE.DISCORD.REGISTER_ANYONE.contains(author.getIdLong())) {

                    if (!Settings.INSTANCE.DISCORD.REGISTER_APPLICANTS.contains(author.getIdLong())) {
                        return usage();
                    } else {
                        Member mentionMember = guild.getMember(mention);

                        if (mentionMember == null) return "User is not in server.";
                        if (Roles.APPLICANT.toRoles(guildDb).isEmpty() && Roles.MEMBER.toRoles(guildDb).isEmpty()) return "No applicant or member role exists.";
                        if (!Roles.APPLICANT.has(mentionMember) && !Roles.MEMBER.has(mentionMember))
                            return "User does not have applicant role.";
                        if (DiscordUtil.getNation(mention) != null) return "User is already registered.";
                        if (nation.getUser() != null) return "Nation already registered: " + nation.getNation() + " = " + nation.getUser();
                        if (!guildDb.hasAlliance()) return "This guild is not registered to an alliance";
                        if (!guildDb.isAllianceId(nation.getAlliance_id())) return "Nation has not applied ingame";
                    }
                }
            }
            return register(guild, guildDb, mention, nation, true);
        }
        String nationName = StringMan.join(args, " ");
        if (nationName.equalsIgnoreCase("*")) {
            if (!Roles.ADMIN.hasOnRoot(author)) {
                return "No permission.";
            }

            Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNationsById();
            Map<String, DBNation> byLeader = new HashMap<>();
            for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
                byLeader.put(entry.getValue().getLeader().toLowerCase(), entry.getValue());
                byLeader.putIfAbsent(entry.getValue().getNation().toLowerCase(), entry.getValue());
            }

            for (Member member : guild.getMembers()) {
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

                String fullDiscriminator = DiscordUtil.getFullUsername(user);
                PNWUser pnwUser = new PNWUser(nation.getNation_id(), member.getIdLong(), fullDiscriminator);

                discordDb.addUser(pnwUser);
            }

            return "";
        }

        DBNation nation = DiscordUtil.parseNation(nationName, true, true, guild);
        if (nation == null) {
            return "Must be an nation id or link: ``" + nationName + "`" + "`";
        }

        return register(guild, guildDb, user, nation, false);
    }

    public String register(Guild guild, GuildDB db, User user, DBNation nation, boolean force) throws IOException {
        int nationId = nation.getNation_id();
        boolean notRegistered = DiscordUtil.getUserByNationId(nationId) == null;

        String fullDiscriminator = DiscordUtil.getFullUsername(user);

        String errorMsg = "1. Go to: <" + Settings.PNW_URL() + "/nation/edit/>\n" +
                "2. Scroll down to where it says Discord Username:\n" +
                "3. Put your discord username `" + fullDiscriminator + "` in the field\n" +
                "4. Click save\n" +
                "5. Run the command " + CM.register.cmd.nation(nationId + "").toSlashCommand() + " again";

        long id = user.getIdLong();
        boolean checkId = false;

        PNWUser existingUser = Locutus.imp().getDiscordDB().getUser(null, user.getName(), fullDiscriminator);
        if (existingUser != null) {
            if (existingUser.getDiscordId() != id) {
                errorMsg = "That nation is already registered to another user!" +
                        "1. Go to: <" + Settings.PNW_URL() + "/nation/edit/>\n" +
                        "2. Scroll down to where it says Discord Username:\n" +
                        "3. Put your **DISCORD ID** `" + user.getIdLong() + "` in the field\n" +
                        "4. Click save\n" +
                        "5. Run the command " + CM.register.cmd.nation(nationId + "").toSlashCommand() + " again";
                checkId = fullDiscriminator.contains("#");
            }
        }

        if (!force) {
            try {
                String pnwDiscordName = nation.fetchUsername();
                if (pnwDiscordName == null || pnwDiscordName.isEmpty()) {
                    return "Unable to fetch username. Please ensure you have `Discord Username` set in <" + Settings.PNW_URL() + "/nation/edit/>.";
                }
                boolean success = true;

                String userName = DiscordUtil.getFullUsername(user);
                if (checkId) {
                    userName = "" + user.getIdLong();
                }
                if (!userName.equalsIgnoreCase(pnwDiscordName) && !pnwDiscordName.contains("" + user.getIdLong())) {
                    return "Your user doesn't match: `" + pnwDiscordName + "` != `" + userName + "`\n\n" + errorMsg;
                }

                PNWUser pnwUser = new PNWUser(nationId, id, userName);
                discordDb.addUser(pnwUser);
                String registerMessage = nation.register(user, guild != null ? db : null, notRegistered);

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

        PNWUser pnwUser = new PNWUser(nationId, id, fullDiscriminator);
        discordDb.addUser(pnwUser);
        return nation.register(user, db, notRegistered);
    }
}