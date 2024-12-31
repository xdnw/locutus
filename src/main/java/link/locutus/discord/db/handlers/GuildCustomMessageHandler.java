package link.locutus.discord.db.handlers;

import com.google.common.eventbus.Subscribe;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceChange;
import link.locutus.discord.db.entities.CustomConditionMessage;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.MessageTrigger;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.nation.NationChangeActiveEvent;
import link.locutus.discord.event.nation.NationChangeAllianceEvent;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class GuildCustomMessageHandler implements Runnable {
    private boolean sendEnabled;
    private boolean updateMeta;
    private Map<Long, List<CustomConditionMessage>> messagesByGuild = new ConcurrentHashMap<>();
    private Map<Integer, Long> allianceLeave = new ConcurrentHashMap<>();

    public GuildCustomMessageHandler() {
        // get the scheduler
        // schedule this to run every 5 minutes
        this.sendEnabled = true;
        this.updateMeta = true;
    }

    public void init() {
        // Create allianceLeave map. Setup this map on first start - which stores timestamp of time left alliance
        // Get the nations from locutus
        long now = System.currentTimeMillis();
        long cutoff = now - TimeUnit.DAYS.toMillis(30);
        for (DBNation nation : Locutus.imp().getNationDB().getNationsMatching(f -> f.getAlliance_id() == 0 && f.active_m() < 10800 && f.getVm_turns() == 0)) {
            long seniorityNone = nation.allianceSeniorityNoneMs();
            long timestamp = System.currentTimeMillis() - seniorityNone;
            if (timestamp < cutoff) continue;
            AllianceChange position = nation.getPreviousAlliance(false, now - seniorityNone);
            if (position == null || position.getFromRank().id >= Rank.OFFICER.id) continue;
            allianceLeave.put(nation.getId(), timestamp);
        }
    }

    private void updateMessages() {
        Map<Long, List<CustomConditionMessage>> newMessagesByGuild = new ConcurrentHashMap<>();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            List<CustomConditionMessage> messages = GuildKey.TIMED_MESSAGES.getOrNull(db, false);
            if (messages == null || messages.isEmpty()) continue;
            // ensure output channel is set
            MessageChannel output = GuildKey.RECRUIT_MESSAGE_OUTPUT.getOrNull(db);
            if (output == null) {
                // delete TIMED_MESSAGES
                db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                continue;
            }
            ApiKeyPool mailkey = db.getMailKey();
            if (mailkey == null) {
                try {
                    RateLimitUtil.queue(output.sendMessage("No mail key set with " + CM.settings_default.registerApiKey.cmd.toSlashMention() + ". Disabling `" + GuildKey.RECRUIT_MESSAGE_OUTPUT.name() + "`" + " <@" + db.getGuild().getOwnerId() + ">"));
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                continue;
            }
            if (!db.hasCoalitionPermsOnRoot("recruit")) {
                try {
                    RateLimitUtil.queue(output.sendMessage("No permission. Disabling `" + GuildKey.TIMED_MESSAGES.name() + "`. Previous value:\n" +
                            "```json\n" +
                            GuildKey.TIMED_MESSAGES.toReadableString(db, messages) +
                            "\n```" + "\n<@" + db.getGuild().getOwnerId() + ">"));
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                continue;
            }
            // sort messages by delay desc
            messages.sort(Comparator.comparingLong(CustomConditionMessage::getDelay).reversed());
            newMessagesByGuild.put(db.getIdLong(), messages);
        }
        messagesByGuild = newMessagesByGuild;
    }

    public boolean hasMessage(Predicate<CustomConditionMessage> condition) {
        for (Map.Entry<Long, List<CustomConditionMessage>> longListEntry : messagesByGuild.entrySet()) {
            for (CustomConditionMessage message : longListEntry.getValue()) {
                if (condition.test(message)) return true;
            }
        }
        return false;
    }

    @Subscribe
    public void onAllianceLeave(NationChangeAllianceEvent event) {
        // If not officer or above, Add to allianceLeave map, with the date
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();
        if (previous == null || current == null) return;
        if (current.getPositionEnum() != Rank.REMOVE) return;
        if (previous.getPositionEnum().id >= Rank.OFFICER.id || previous.getPositionEnum() == Rank.REMOVE) {
            allianceLeave.remove(current.getId());
            return;
        }
        allianceLeave.put(current.getId(), System.currentTimeMillis());
    }

    @Subscribe
    public synchronized void onActive(NationChangeActiveEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();
        if (previous == null || current == null || previous.lastActiveMs() == 0) return;
        if (current.getPositionEnum().id > Rank.APPLICANT.id) return;
        // - If inactive for X delay and now active (i.e. within last 15 minutes)
        if (current.lastActiveMs() < System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15)) return;
        long diff = current.lastActiveMs() - previous.lastActiveMs();
        //- X delay must be more than 7 days
        if (diff < TimeUnit.DAYS.toMillis(7)) return;

        if (!hasMessage(m -> m.getTrigger() == MessageTrigger.GRAVEYARD_ACTIVE && m.getDelay() < diff)) return;
        //- If on None or applicant outside top 50 or allies or member or below in an alliance with no active leadership

        boolean isValid = current.getAlliance_id() == 0;
        if (!isValid) {
            DBAlliance aa = current.getAlliance();
            if (aa.getNations(f -> f.active_m() < 7200 && f.getPositionEnum().id >= Rank.OFFICER.id).isEmpty()) {
                isValid = true;
            } else if (aa.getRank() > 80) {
                boolean hasTop80Ally = false;
                for (DBAlliance alliance : aa.getTreatiedAllies(true)) {
                    if (alliance.getRank() <= 80) {
                        hasTop80Ally = true;
                        break;
                    }
                }
                isValid = !hasTop80Ally;
            }
        }
        if (!isValid) return;

        long now = System.currentTimeMillis();

        for (Map.Entry<Long, List<CustomConditionMessage>> entry : messagesByGuild.entrySet()) {
            GuildDB db = Locutus.imp().getGuildDatabases().get(entry.getKey());
            if (db == null) continue;
            List<CustomConditionMessage> messages = entry.getValue();
            for (CustomConditionMessage message : messages) {
                if (message.getTrigger() != MessageTrigger.GRAVEYARD_ACTIVE) continue;
                if (message.getDelay() < diff) continue;
                if (getMeta(current, db, NationMeta.LAST_SENT_ACTIVE, 0L) > now - TimeUnit.DAYS.toMillis(1)) break;
                try {
                    // ensure no other message sent recently
                    if (getMeta(current, db, NationMeta.LAST_SENT_CREATION, 0L) > now - TimeUnit.DAYS.toMillis(1)) break;
                    if (getMeta(current, db, NationMeta.LAST_SENT_LEAVE, 0L) > now - TimeUnit.DAYS.toMillis(1)) break;
                    message.send(db, current, sendEnabled);
                    break;
                } finally {
                    if (updateMeta) {
                        byte[] nowBytes = ByteBuffer.allocate(8).putLong(now).array();
                        db.setMeta(current.getId(), NationMeta.LAST_SENT_ACTIVE, nowBytes);
                    }
                }
            }
        }
    }

    private Long getMeta(DBNation nation, GuildDB db, NationMeta meta, Long def) {
        ByteBuffer buf = db.getNationMeta(nation.getId(), meta);
        if (buf == null) return def;
        return buf.getLong();
    }

    public Map<MessageTrigger, List<Map.Entry<GuildDB, CustomConditionMessage>>> getMessages() {
        Map<MessageTrigger, List<Map.Entry<GuildDB, CustomConditionMessage>>> messages = new HashMap<>();
        for (Map.Entry<Long, List<CustomConditionMessage>> entry : messagesByGuild.entrySet()) {
            GuildDB db = Locutus.imp().getGuildDatabases().get(entry.getKey());
            if (db == null) continue;
            for (CustomConditionMessage message : entry.getValue()) {
                List<Map.Entry<GuildDB, CustomConditionMessage>> list = messages.computeIfAbsent(message.getTrigger(), k -> new ArrayList<>());
                list.add(Map.entry(db, message));
            }
        }

        for (Map.Entry<MessageTrigger, List<Map.Entry<GuildDB, CustomConditionMessage>>> entry : messages.entrySet()) {
            entry.getValue().sort(Comparator.comparingLong(o -> o.getValue().getDelay()));
        }
        return messages;
    }

    public List<Map.Entry<GuildDB, CustomConditionMessage>> getMessagesBetween(List<Map.Entry<GuildDB, CustomConditionMessage>> messages, long min, long max) {
        List<Map.Entry<GuildDB, CustomConditionMessage>> result = new ObjectArrayList<>();
        for (Map.Entry<GuildDB, CustomConditionMessage> message : messages) {
            long delay = message.getValue().getDelay();
            if (delay >= min && delay <= max) {
                result.add(message);
            }
        }
        return result;

//        return messages.stream().filter(f -> f.getValue().getDelay() >= min && f.getValue().getDelay() <= max).toList();
//        int start = ArrayUtil.binarySearchGreater(messages, o -> o.getValue().getDelay() >= min);
//        if (start == -1) {
//            return Collections.emptyList();
//        }
//        if (max == Long.MAX_VALUE) {
//            return new ObjectArrayList<>(messages.subList(start, messages.size()));
//        }
//        int end = ArrayUtil.binarySearchGreater(messages, o -> o.getValue().getDelay() <= max, start, messages.size());
//        if (end == -1) {
//            return new ObjectArrayList<>(messages.subList(start, messages.size()));
//        }
//        return new ObjectArrayList<>(messages.subList(start, end));
    }

    @Override
    public void run() {
        // Check it is NOT near turn change
        if (!TimeUtil.checkTurnChange()) {
            return;
        }
        updateMessages();
        // creation messages
        long now = System.currentTimeMillis();
        byte[] nowBuf = ByteBuffer.allocate(8).putLong(now).array();

        long createCutoff = now - TimeUnit.DAYS.toMillis(30);

        Map<MessageTrigger, List<Map.Entry<GuildDB, CustomConditionMessage>>> messagesByType = getMessages();
        List<Map.Entry<GuildDB, CustomConditionMessage>> creation = messagesByType.get(MessageTrigger.CREATION);

        List<DBNation> nationsNone = new ArrayList<>(Locutus.imp().getNationDB().getNationsMatching(f -> f.getAlliance_id() == 0 && f.active_m() < 1440 && f.getVm_turns() == 0));

        List<DBNation> nationsCreated = nationsNone.stream().filter(f -> f.getDate() > createCutoff).toList();

        if (creation != null) {
            for (DBNation nation : nationsCreated) {
                long date = nation.getDate();
                long ageMs = now - date;

                // get the messages which are below ageMs but above the lastSent
                List<Map.Entry<GuildDB, CustomConditionMessage>> messages = getMessagesBetween(creation, 0, ageMs);
                messages.removeIf(f -> f.getValue().getOriginDate() > date || Math.abs(ageMs - f.getValue().getDelay()) > TimeUnit.DAYS.toMillis(7));
                if (!messages.isEmpty()) {
                    Set<Long> guildsSent = new LongArraySet();
                    for (Map.Entry<GuildDB, CustomConditionMessage> entry : messages) {
                        GuildDB db = entry.getKey();
                        if (!guildsSent.add(db.getIdLong())) continue;
                        try {
                            CustomConditionMessage message = entry.getValue();

                            long lastMs = getMeta(nation, db, NationMeta.LAST_SENT_CREATION, -1L);
                            long lastAge = Math.max(lastMs - date + 1, 0);
                            if (lastAge > message.getDelay()) {
                                continue;
                            }

                            message.send(db, nation, sendEnabled);
                        } finally {
                            if (updateMeta) {
                                db.setMeta(nation.getId(), NationMeta.LAST_SENT_CREATION, nowBuf);
                            }
                        }
                    }
                }
            }
        }

        List<DBNation> nationsLeave = nationsNone.stream().filter(f -> {
            Long leftDate = allianceLeave.get(f.getId());
            if (leftDate == null) return false;
            if (leftDate > now - TimeUnit.DAYS.toMillis(30)) {
                if (f.getDate() == 0 || f.getAgeDays() <= 3) return false;
                return true;
            }
            return false;
        }).toList();

        for (DBNation nation : nationsLeave) {
            Long leftDate = allianceLeave.get(nation.getId());
            if (leftDate == null) continue;
            long diff = now - leftDate;

            List<Map.Entry<GuildDB, CustomConditionMessage>> messages = getMessagesBetween(creation, 0, diff);

            if (!messages.isEmpty()) {
                Set<Long> guildsSent = new LongArraySet();
                for (Map.Entry<GuildDB, CustomConditionMessage> entry : messages) {
                    GuildDB db = entry.getKey();
                    if (!guildsSent.add(db.getIdLong())) continue;
                    try {
                        CustomConditionMessage message = entry.getValue();

                        long lastMs = getMeta(nation, db, NationMeta.LAST_SENT_LEAVE, -1L);
                        long lastAge = Math.max(lastMs - leftDate + 1, 0);
                        if (lastAge > message.getDelay()) continue;

                        if (getMeta(nation, db, NationMeta.LAST_SENT_CREATION, 0L) > now - TimeUnit.DAYS.toMillis(1)) break;
                        if (getMeta(nation, db, NationMeta.LAST_SENT_ACTIVE, 0L) > now - TimeUnit.DAYS.toMillis(1)) break;

                        message.send(db, nation, sendEnabled);
                    } finally {
                        if (updateMeta) {
                            db.setMeta(nation.getId(), NationMeta.LAST_SENT_LEAVE, nowBuf);
                        }
                    }
                }
            }
        }
    }

    public void setMeta(boolean setMeta) {
        this.updateMeta = setMeta;
    }

    public void setSendMessages(boolean sendMessages) {
        this.sendEnabled = sendMessages;
    }
}
