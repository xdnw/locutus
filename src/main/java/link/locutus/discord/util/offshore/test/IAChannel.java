package link.locutus.discord.util.offshore.test;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class IAChannel {
    private final DBNation nation;
    private final GuildDB db;
    private final Category category;
    private final TextChannel channel;

    public IAChannel(DBNation nation, GuildDB db, Category category, TextChannel channel) {
        this.nation = nation;
        this.db = db;
        this.category = category;
        this.channel = channel;
    }

    public DBNation getNation() {
        if (nation == null) {
            String[] split = channel.getName().split("-");
            if (MathMan.isInteger(split[split.length - 1])) {
                Locutus.imp().getNationDB().getNation(Integer.parseInt(split[split.length - 1]));
            }
        }
        return nation;
    }

    public void updatePerms() {
        User user = nation.getUser();
        if (user == null) return;
        Member member = db.getGuild().getMember(user);
        if (member == null) return;
        channel.putPermissionOverride(member).grant(Permission.VIEW_CHANNEL).complete();

        String expected = DiscordUtil.toDiscordChannelString(nation.getNation()) + "-" + nation.getNation_id();
        String name = channel.getName();
        if (!name.equalsIgnoreCase(expected)) {
            RateLimitUtil.queue(channel.getManager().setName(expected));
        }
    }

    public TextChannel getChannel() {
        return channel;
    }

    public void update(Map<IACheckup.AuditType, Map.Entry<Object, String>> audits) {
        Set<String> emojis = new LinkedHashSet<>();
        if (audits == null) {
            if (nation.getVm_turns() > 0) {
                emojis.add("\uD83C\uDFD6\ufe0f");
            }
            if (nation.getActive_m() > 2440) {
                emojis.add("\uD83E\uDEA6");
            }
            if (nation.getCities() < 10 && nation.getOff() < 4) {
                emojis.add("\uD83E\uDD4A");
            }
            User user = nation.getUser();
            Guild guild = db.getGuild();
            if (user == null || guild.getMember(user) == null) {
                emojis.add("\uD83D\uDCDB");
            }
        } else {
            for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : audits.entrySet()) {
                IACheckup.AuditType type = entry.getKey();
                Map.Entry<Object, String> result = entry.getValue();
                emojis.add(type.emoji);
            }
            String cmd = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "checkcities " + nation.getNation_id();
            IACheckup.createEmbed(channel, null, cmd, nation, audits, 0);
        }
//        emojis.remove("");
//        if (!emojis.isEmpty()) {
//            expected = DiscordUtil.toDiscordChannelString(nation.getNation()) + "-" + StringMan.join(emojis, "") + "-" + nation.getNation_id();
//        }
    }

    public DBNation getLastActiveGov(boolean rankChange) {
        GuildMessageChannel channel = getChannel();
        if (channel != null) {
            List<Message> history = channel.getHistory().retrievePast(20).complete();

            Map<DBNation, Double> numMessage = new HashMap<>();
            DBNation lastGov = null;
            double lastMax = 0;

            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
            for (int i = 0; i < history.size(); i++) {
                Message message = history.get(i);
                if (message.getTimeCreated().toEpochSecond() * 1000L < cutoff) continue;

                Guild guild = message.getGuild();
                User author = message.getAuthor();
                boolean hasRole = Roles.INTERVIEWER.has(author, guild) || Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild);
                if (hasRole) {
                    DBNation senderNation = DiscordUtil.getNation(message.getAuthor().getIdLong());
                    if (senderNation != null) {
                        double add = 1;
                        double factor = 1 - 0.5 * ((double) i / history.size());
                        String content = DiscordUtil.trimContent(message.getContentRaw()).toLowerCase();
                        if (content.contains(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "setrank") || content.contains(Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "setrank") && rankChange) {
                            return senderNation;
                        } else if (content.contains(Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "checkup")) {
                            add += 8;
                        } else if (content.contains(Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "verify")) {
                            add += 4;
                        }
                        numMessage.put(senderNation, numMessage.getOrDefault(senderNation, 0d) + factor * add);
                        double amt = numMessage.get(senderNation);

                        if (amt > lastMax) {
                            lastMax = amt;
                            lastGov = senderNation;
                        }
                    }
                }
            }
            if (lastGov != null) return lastGov;
        }
        return null;
    }
}
