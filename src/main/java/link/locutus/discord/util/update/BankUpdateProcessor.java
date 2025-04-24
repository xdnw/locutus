package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.event.bank.TransactionEvent;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.nio.ByteBuffer;
import link.locutus.discord.util.scheduler.KeyValue;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static link.locutus.discord.db.guild.GuildKey.DEPOSIT_ALERT_CHANNEL;
import static link.locutus.discord.db.guild.GuildKey.LARGE_TRANSFERS_CHANNEL;
import static link.locutus.discord.db.guild.GuildKey.WITHDRAW_ALERT_CHANNEL;

public class BankUpdateProcessor {
    @Subscribe
    public void process(TransactionEvent event) {
        Transaction2 transfer = event.getTransaction();
        if (transfer.tx_datetime < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) {
            return;
        }

        boolean isLoot = transfer.note != null && transfer.note.contains("of the alliance bank inventory.");
        if (!transfer.isReceiverNation() && !transfer.isSenderNation()) return;
        if (!transfer.isReceiverAA() && !transfer.isSenderAA()) return;
        int nationId = (int) (transfer.isSenderNation() ? transfer.getSender() : transfer.getReceiver());
        int aaId = (int) (transfer.isSenderAA() ? transfer.getSender() : transfer.getReceiver());

        if (!isLoot) {
            Set<Integer> trackedAlliances = new IntOpenHashSet();
            trackedAlliances.add(aaId);
            if (transfer.note != null) {
                Map<DepositType, Object> noteMap = transfer.getNoteMap();
                for (Map.Entry<DepositType, Object> entry : noteMap.entrySet()) {
                    if (entry.getKey().getParent() != null) continue;
                    if (entry.getValue() instanceof Number n) {
                        try {
                            trackedAlliances.add(Math.toIntExact(n.longValue()));
                        } catch (ArithmeticException ignore) {
                        }
                    }
                }
            }
            for (int allianceId : trackedAlliances) {
                GuildDB guildDb = Locutus.imp().getGuildDBByAA(allianceId);
                if (guildDb != null) {
                    boolean isDeposit = transfer.isReceiverAA();
                    GuildSetting<MessageChannel> key = isDeposit ? DEPOSIT_ALERT_CHANNEL : WITHDRAW_ALERT_CHANNEL;
                    Roles locrole = isDeposit ? Roles.ECON_DEPOSIT_ALERTS : Roles.ECON_WITHDRAW_ALERTS;
                    MessageChannel channel = guildDb.getOrNull(key);

                    if (channel != null) {
                        Guild guild = guildDb.getGuild();
                        if (guild != null) {
                            Map.Entry<String, String> card = createCard(transfer, nationId);
                            try {
                                IMessageBuilder msg = new DiscordChannelIO(channel).create().embed(card.getKey(), card.getValue());
                                Map.Entry<GuildDB, Integer> offshore = guildDb.getOffshoreDB();
                                if (isDeposit && offshore != null) {
                                    msg = msg.commandButton(CM.offshore.send.cmd, "offshore");
                                }
                                Role role = locrole.toRole(aaId, guildDb);
                                if (role != null) {
                                    msg.append(role.getAsMention());
                                }
                                if (isDeposit) {
                                    msg.send();
                                } else {
                                    msg.sendWhenFree();
                                }
                            } catch (InsufficientPermissionException ignore) {
                            }
                        }
                    }
                }
            }
        }

        double value = transfer.convertedTotal();
        if (value > Settings.INSTANCE.UPDATE_PROCESSOR.THRESHOLD_BANK_SUB_ALERT) {
            long longValue = (long) value;

            Set<Long> receiver = new LongOpenHashSet();
            Set<BankDB.Subscription> subs = new HashSet<>();

            subs.addAll(Locutus.imp().getBankDB().getSubscriptions(0, BankDB.BankSubType.ALL, true, longValue));
            subs.addAll(Locutus.imp().getBankDB().getSubscriptions(0, BankDB.BankSubType.ALL, false, longValue));

            subs.addAll(Locutus.imp().getBankDB().getSubscriptions((int) transfer.getSender(), BankDB.BankSubType.of(transfer.isSenderAA()), false, longValue));
            subs.addAll(Locutus.imp().getBankDB().getSubscriptions((int) transfer.getReceiver(), BankDB.BankSubType.of(transfer.isReceiverAA()), true, longValue));

            for (BankDB.Subscription sub : subs) {
                DBNation nation = DiscordUtil.getNation(sub.user);
                if (nation == null) {
                    Locutus.imp().getBankDB().unsubscribeAll(sub.user);
                    continue;
                }
                ByteBuffer thresholdBuf = nation.getMeta(NationMeta.BANK_TRANSFER_REQUIRED_AMOUNT);
                if (thresholdBuf != null) {
                    long threshold = (long) thresholdBuf.getDouble();
                    if (threshold > value) {
                        continue;
                    }
                }
                receiver.add(sub.user);
            }

            if (!receiver.isEmpty()) {
                Map.Entry<String, String> card = createCard(transfer, nationId);

                for (GuildDB guildDB : Locutus.imp().getGuildDatabases().values()) {
                    MessageChannel channel = guildDB.getOrNull(LARGE_TRANSFERS_CHANNEL, false);
                    if (channel == null) {
                        continue;
                    }

                    Guild guild = ((GuildMessageChannel) channel).getGuild();
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
        String fromName = (transfer.isSenderAA() ? "AA:" : "") + PW.getName(transfer.getSender(), transfer.isSenderAA());
        String toName = (transfer.isReceiverAA() ? "AA:" : "") + PW.getName(transfer.getReceiver(), transfer.isReceiverAA());
        String title = "#" + transfer.tx_id + " worth $" + MathMan.format(transfer.convertedTotal()) + " | " + fromName + " > " + toName;
        StringBuilder body = new StringBuilder();
        body.append(PW.getMarkdownUrl((int) transfer.getSender(), transfer.isSenderAA()) + " > " + PW.getMarkdownUrl((int) transfer.getReceiver(), transfer.isReceiverAA()));

        String note = transfer.note == null ? "~~NO NOTE~~" : transfer.note;
        String url = PW.getMarkdownUrl(nationid, false) + "&display=bank";

        if (transfer.note != null) {
            body.append(transfer.note);
        }
        body.append("\n").append("From: " + PW.getMarkdownUrl((int) transfer.sender_id, transfer.isSenderAA()));
        body.append("\n").append("To: " + PW.getMarkdownUrl((int) transfer.receiver_id, transfer.isReceiverAA()));
        body.append("\n").append("Banker: " + PW.getMarkdownUrl(transfer.banker_nation, false));
        body.append("\n").append("Date: " + TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(transfer.tx_datetime)));
        body.append("\n").append(ResourceType.toString(transfer.resources));

        return new KeyValue<>(title, body.toString());
    }
}