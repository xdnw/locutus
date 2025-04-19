package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.war.WarCatReason;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.war.WarRoom;
import link.locutus.discord.commands.war.WarRoomUtil;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;

import java.util.*;
import java.util.stream.Collectors;

public class WarRoomCmd extends Command {
    public WarRoomCmd() {
        super("warroom", CommandCategory.MILCOM, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.war.room.create.cmd);
    }
    @Override
    public String help() {
        return "" + super.help() + " [target] [att1] [att2] [att3] ...\n" +
                "OR\n" +
                super.help() + " <sheet> <message>";
    }

    @Override
    public String desc() {
        return """
                Create a war room
                Add `-p` to ping users that are added
                Add `-a` to skip adding users
                Add `-f` to force create channels (if checks fail)
                Add `-m` to send standard counter messages
                Add `-h:1` to change the header row (0 index)
                Add `-l` to use leader name instead of nation
                Add `filter:<filter>` to filter nations.""";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage();

        if ((flags.contains('p') || flags.contains('m')) && !Roles.MILCOM.has(guild.getMember(author))) {
            return "You need to have milcom role to use `-p` or `-m`.";
        }
        GuildDB db = Locutus.imp().getGuildDB(guild);
        WarCategory warCat = db.getWarChannel(true);
        if (warCat == null) {
            return "War categories are not enabled. See " + GuildKey.ENABLE_WAR_ROOMS.getCommandObj(db, true) + "";
        }
        String filterArg = DiscordUtil.parseArg(args, "filter");

        boolean useLeader = flags.contains('l');
        boolean ping = flags.contains('p');
        boolean addMember = !flags.contains('a');
        boolean addMessage = flags.contains('m');
        String headerStr = DiscordUtil.parseArg(args, "-h");
        int headerRow = headerStr == null ? 0 : Integer.parseInt(headerStr);

        String arg = args.get(0);
        if (arg.equalsIgnoreCase("close") || arg.equalsIgnoreCase("delete")) {
            MessageChannel textChannel = channel instanceof DiscordChannelIO ? ((DiscordChannelIO) channel).getChannel() : null;
            link.locutus.discord.commands.war.WarRoom room = warCat.getWarRoom((StandardGuildMessageChannel) textChannel, WarCatReason.WARROOM_COMMAND);
            if (room != null) {
                warCat.deleteRoom(room, "Closed by " + DiscordUtil.getFullUsername(author));
                return "Goodbye.";
            } else {
                return "You are not in a war room!";
            }
        }
        if (args.size() < 2) return usage(args.size(), 2, channel);

        if (arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:")) {
            SpreadSheet sheet = SpreadSheet.create(arg);
            StringBuilder response = new StringBuilder();
            Map<DBNation, Set<DBNation>> targets = BlitzGenerator.getTargets(sheet, useLeader, headerRow, f -> 3, 0.75, PW.WAR_RANGE_MAX_MODIFIER, true, true, false, f -> true,
                    (dbNationDBNationEntry, s) -> response.append(s).append("\n"),
                    info -> response.append("```\n" + info.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n")) + "\n```").append("\n"));
            if (response.length() != 0) {
                channel.send(response.toString());
                if (!flags.contains('f')) {
                    return "Add `-f` to force create the channels anyway.";
                }
            }

            channel.sendMessage("Generating channels...");

            if (filterArg != null) {
                Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, filterArg, false, false);
                for (Map.Entry<DBNation, Set<DBNation>> entry : targets.entrySet()) {
                    entry.getValue().removeIf(f -> !nations.contains(f));
                }
            }
            targets.entrySet().removeIf(f -> f.getValue().isEmpty());

            Set<GuildMessageChannel> channels = new LinkedHashSet<>();
            for (Map.Entry<DBNation, Set<DBNation>> entry : targets.entrySet()) {
                DBNation target = entry.getKey();
                Set<DBNation> attackers = entry.getValue();
                WarRoom room = warCat.createWarRoom(target, true, true, true, WarCatReason.WARCAT_SHEET);
                WarRoomUtil.handleRoomCreation(room, author, db, s -> response.append(s).append("\n"), ping, addMember, addMessage, target, attackers);
                GuildMessageChannel warChan = room.getChannel();
                if (warChan == null) {
                    response.append("Failed to create channel for ").append(target.getName()).append("\n");
                    continue;
                }
                try {
                    if (args.get(1).length() > 1 && !args.get(1).equalsIgnoreCase("null")) {
                        RateLimitUtil.queue(warChan.sendMessage(args.get(1)));
                    }
                    channels.add(warChan);
                } catch (Throwable e) {
                    e.printStackTrace();
                    response.append(e.getMessage());
                }
            }

            return "Created " + channels.size() + " for " + targets.size() + " targets";
        }
        DBNation target = DiscordUtil.parseNation(arg, true);
        if (target == null) return "Invalid target: `" + args.get(0) + "`";
        Set<DBNation> attackers = new LinkedHashSet<>();
        for (int i = 1; i < args.size(); i++) {
            DBNation attacker = DiscordUtil.parseNation(args.get(i), true);
            if (attacker == null) {
                return "Invalid attacker: `" + args.get(i) + "`. Maybe try using the nation id or the link.";
            }
            attackers.add(attacker);
        }

        StringBuilder response = new StringBuilder();
        WarRoom room = warCat.createWarRoom(target, true, true, true, WarCatReason.WARROOM_COMMAND);
        if (room == null) {
            response.append("Failed to create channel for ").append(target.getName()).append("\n");
        } else {
            WarRoomUtil.handleRoomCreation(room, author, db, s -> response.append(s).append("\n"), ping, addMember, addMessage, target, attackers);
            GuildMessageChannel roomChan = room.getChannel();
            if (roomChan != null) {
                response.append(roomChan.getAsMention());
            } else {
                response.append("Failed to create channel for ").append(target.getName());
            }
        }
        me.setMeta(NationMeta.INTERVIEW_WAR_ROOM, (byte) 1);

        if (!flags.contains('m') && db.getOrNull(GuildKey.API_KEY) != null)
            response.append("\n- add `-m` to send standard counter instructions");
        if (!flags.contains('p') && db.getOrNull(GuildKey.API_KEY) != null)
            response.append("\n- add `-p` to ping users in the war channel");

        return response.toString();
    }
}
