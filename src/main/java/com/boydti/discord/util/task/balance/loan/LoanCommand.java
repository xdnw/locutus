package com.boydti.discord.util.task.balance.loan;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.DBLoan;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class LoanCommand extends Command {
    @Override
    public String help() {
        return super.help() + " <user> <amount> <time>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ECON.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3) return usage(event);

        DBNation user = DiscordUtil.parseNation(args.get(0));
        Map<ResourceType, Double> amount = PnwUtil.parseResources(args.get(1));
        long ms = TimeUtil.timeToSec(args.get(2)) * 1000L;

        if (user == null) return "Not registered: `" + args.get(0) + "`";
        if (amount == null || amount.isEmpty()) return "Invalid amount: `" + args.get(1) + "`";
        if (ms <= 0) return "Invalid time period: " + ms;

        String title = "Generating loan for " + user.getNation();
        StringBuilder body = new StringBuilder();
        body.append("Please wait...");

        Message message = DiscordUtil.createEmbedCommand(event.getChannel(), title, body.toString());
        GuildDB guildDB = Locutus.imp().getGuildDB(event);
        long due = System.currentTimeMillis() + ms;

        DBLoan loan = new DBLoan(-1, guild.getIdLong(), message.getIdLong(), user.getNation_id(), PnwUtil.resourcesToArray(amount), due, DBLoan.Status.OPEN);
        guildDB.addLoan(loan);

        loan = guildDB.getLoanByMessageId(message.getIdLong());
        if (loan == null) {
            return "Error: Failed to generate loan";
        }
        MessageEmbed embed = message.getEmbeds().get(0);
        EmbedBuilder builder = new EmbedBuilder(embed);
        updateLoan(loan, builder, event.getChannel(), message.getIdLong());

        return null;
    }

    public static void updateLoan(DBLoan loan, MessageChannel channel) {
        updateLoan(loan, null, channel, 0);
    }

    public static void updateLoan(DBLoan loan, EmbedBuilder builder, MessageChannel channel, long messageId) {
        StringBuilder body = new StringBuilder();
        if (builder == null) {
            builder = new EmbedBuilder();
            String title = "Generating loan";
            Message message = DiscordUtil.createEmbedCommand(channel, title, "Please wait...");
            MessageEmbed embed = message.getEmbeds().get(0);
            builder = new EmbedBuilder(embed);
            messageId = message.getIdLong();
        }

        Map<String, String> reactions = new LinkedHashMap<>();

        builder.setTitle("Loan #" + loan.loanId + " to " + PnwUtil.getName(loan.nationId, false));

        body.append("**Server**: ");

        GuildDB guildDB = Locutus.imp().getGuildDB(loan.serverId);
        String guildName;
        String url = "https://discord.com/channels/" + loan.serverId;
        if (guildDB != null) {
            Guild guild = guildDB.getGuild();
            guildName = guild.getName();
            Integer allianceId = guildDB.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (allianceId == null) {
                try {
                    List<Invite> invites = com.boydti.discord.util.RateLimitUtil.complete(guild.retrieveInvites());
                    for (Invite invite : invites) {
                        if (invite.isTemporary()) continue;
                        if (!invite.isExpanded()) continue;
                        if (invite.getMaxUses() == 0) {
                            url = invite.getUrl();
                        }
                    }
                } catch (Throwable e) {
                }
            } else {
                url = PnwUtil.getUrl(allianceId, true);
            }
            guildName = guild.getName();
        } else {
            guildName = "" + loan.serverId;
        }
        body.append(MarkupUtil.markdownUrl(guildName, url));

        DBNation nation = Locutus.imp().getNationDB().getNation(loan.nationId);
        if (nation != null) {
            body.append("\n**Nation**: " + MarkupUtil.markdownUrl(nation.getNation(), nation.getNationUrl()) + " | " + MarkupUtil.markdownUrl(nation.getAlliance(), nation.getAllianceUrl()));
        } else {
            body.append("\n**Nation**: ~~<DELETED>~~");
        }


        body.append("\n**Amount**: " + PnwUtil.resourcesToString(loan.resources) + " worth: $" + MathMan.format(PnwUtil.convertedTotal(loan.resources)));


        body.append("\n**Due**: " + TimeUtil.F_YYYY_MM_DD.format(new Date(loan.dueDate)));
        long now = System.currentTimeMillis();
        String diffStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, Math.abs(now - loan.dueDate));
        body.append(" (" + diffStr);
        if (now > loan.dueDate) {
            body.append(" ago");
        }
        body.append(")");

        body.append("\n**Status**: " + loan.status.name());

        body.append("\n");

        for (DBLoan.Status status : DBLoan.Status.values()) {
            if (status == loan.status) continue;
            switch (loan.status) {
                case CLOSED:
                    if (status != DBLoan.Status.OPEN) continue;
                case EXTENDED:
                case MISSED_PAYMENT:
                case DEFAULTED:
            }
            reactions.put(status.emoji, "~!updateloan " + loan.loanId + " " + status.name());
            body.append("\nPress " + status.emoji + " to mark as " + status.name());
        }

        builder.setDescription(body);

        long finalMessageId = messageId;
        DiscordUtil.updateEmbed(builder, reactions, new Function<EmbedBuilder, Message>() {
            @Override
            public Message apply(EmbedBuilder builder) {
                 return com.boydti.discord.util.RateLimitUtil.complete(channel.editMessageEmbedsById(finalMessageId, builder.build()));
            }
        });
    }
}
