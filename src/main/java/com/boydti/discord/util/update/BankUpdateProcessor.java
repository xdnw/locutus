package com.boydti.discord.util.update;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.trade.subbank.BankAlerts;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.BankDB;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.Transaction2;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.AlertUtil;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.AbstractMap;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.boydti.discord.db.GuildDB.Key.BANK_ALERT_CHANNEL;
import static com.boydti.discord.db.GuildDB.Key.DEPOSIT_ALERT_CHANNEL;
import static com.boydti.discord.db.GuildDB.Key.WITHDRAW_ALERT_CHANNEL;

public class BankUpdateProcessor {
    public static void process(Transaction2 transfer) {
        if (transfer.note != null && transfer.note.contains("of the alliance bank inventory.")) return;
        if (!transfer.isReceiverNation() && !transfer.isSenderNation()) return;
        if (!transfer.isReceiverAA() && !transfer.isSenderAA()) return;
        int nationId = (int) (transfer.isSenderNation() ? transfer.getSender() : transfer.getReceiver());
        int aaId = (int) (transfer.isSenderAA() ? transfer.getSender() : transfer.getReceiver());

        GuildDB guildDb = Locutus.imp().getGuildDBByAA(aaId);
        if (guildDb != null) {
            GuildDB.Key key = transfer.isReceiverAA() ? DEPOSIT_ALERT_CHANNEL : WITHDRAW_ALERT_CHANNEL;
            Roles locrole = transfer.isReceiverAA() ? Roles.ECON_DEPOSIT_ALERTS : Roles.ECON_WITHDRAW_ALERTS;
            String channelId = guildDb.getInfo(key);

            if (channelId != null) {
                Guild guild = guildDb.getGuild();
                if (guild != null) {
                    MessageChannel channel = DiscordUtil.getChannel(guild, channelId);
                    if (channel != null) {
                        Map.Entry<String, String> card = createCard(transfer, nationId);
                        if (card != null) {
                            try {
                                DiscordUtil.createEmbedCommand(channel, card.getKey(), card.getValue());
                                Role role = locrole.toRole(guild);
                                if (role != null) {
                                    AlertUtil.bufferPing(channel, role.getAsMention());
                                }
                            } catch (InsufficientPermissionException ignore) {}
                        }
                    }
                }
            }
        }

        double value = transfer.convertedTotal();
        if (value > Settings.INSTANCE.UPDATE_PROCESSOR.THRESHOLD_BANK_SUB_ALERT) {
            long longValue = (long) value;

            Set<Long> receiver = new HashSet<>();
            Set<BankDB.Subscription> subs = new HashSet<>();

            subs.addAll(Locutus.imp().getBankDB().getSubscriptions(0, BankDB.BankSubType.ALL, true, longValue));
            subs.addAll(Locutus.imp().getBankDB().getSubscriptions(0, BankDB.BankSubType.ALL, false, longValue));

            subs.addAll(Locutus.imp().getBankDB().getSubscriptions((int) transfer.getSender(), BankDB.BankSubType.of(transfer.isSenderAA()), false, longValue));
            subs.addAll(Locutus.imp().getBankDB().getSubscriptions((int) transfer.getReceiver(), BankDB.BankSubType.of(transfer.isReceiverAA()), true, longValue));

            for (BankDB.Subscription sub : subs) {
                receiver.add(sub.user);
            }

            if (!receiver.isEmpty()) {
                Map.Entry<String, String> card = createCard(transfer, nationId);

                for (GuildDB guildDB : Locutus.imp().getGuildDatabases().values()) {
                    Integer perm = guildDB.getPermission(BankAlerts.class);
                    if (perm == null || perm <= 0) continue;
                    String channelId = guildDB.getInfo(BANK_ALERT_CHANNEL, false);
                    if (channelId == null) {
                        continue;
                    }

                    GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(Long.parseLong(channelId));
                    if (channel == null) {
                        continue;
                    }

                    Guild guild = channel.getGuild();
                    Set<String> mentions = new HashSet<>();
                    for (long user : receiver) {
                        Member member = guild.getMemberById(user);
                        if (member != null) {
                            mentions.add(member.getAsMention());
                        }
                    }
                    if (mentions.isEmpty() && value < Settings.INSTANCE.UPDATE_PROCESSOR.THRESHOLD_ALL_BANK_ALERT) continue;

                    DiscordUtil.createEmbedCommand(channel, card.getKey(), card.getValue());
                    AlertUtil.bufferPing(channel, mentions.toArray(new String[0]));
                }
            }
        }
    }

    public static Map.Entry<String, String> createCard(Transaction2 transfer, int nationid) {
        String fromName = (transfer.isSenderAA() ? "AA:" : "") + PnwUtil.getName(transfer.getSender(), transfer.isSenderAA());
        String toName = (transfer.isReceiverAA() ? "AA:" : "") + PnwUtil.getName(transfer.getReceiver(), transfer.isReceiverAA());
        String title = "#" + transfer.tx_id + " worth $" + MathMan.format(transfer.convertedTotal()) + " | " + fromName + " > " + toName;
        StringBuilder body = new StringBuilder();
        body.append(PnwUtil.getBBUrl((int) transfer.getSender(), transfer.isSenderAA()) + " > " + PnwUtil.getBBUrl((int) transfer.getReceiver(), transfer.isReceiverAA()));

        String note = transfer.note == null ? "~~NO NOTE~~" : transfer.note;
        String url = PnwUtil.getBBUrl(nationid, false) + "&display=bank";

        if (transfer.note != null) {
            body.append(transfer.note);
        }
        body.append("\n").append("From: " + PnwUtil.getBBUrl((int) transfer.sender_id, transfer.isSenderAA()));
        body.append("\n").append("To: " + PnwUtil.getBBUrl((int) transfer.receiver_id, transfer.isReceiverAA()));
        body.append("\n").append("Banker: " + PnwUtil.getBBUrl(transfer.banker_nation, false));
        body.append("\n").append("Date: " + TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(transfer.tx_datetime)));
        body.append("\n").append(PnwUtil.resourcesToString(transfer.resources));

        return new AbstractMap.SimpleEntry<>(title, body.toString());
    }
}