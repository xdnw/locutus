package link.locutus.discord.commands.war;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static link.locutus.discord.commands.war.WarCatReason.*;
import static link.locutus.discord.commands.war.WarRoomUtil.getRangeFromCategory;

public class WarCategory {
    private final GuildDB db;

    private final Map<Integer, WarRoom> warRoomMap;

    private final String catPrefix;
    private final Set<Integer> allianceIds = new IntOpenHashSet();

    public WarCategory(GuildDB db, String catPrefix) {
        this.warRoomMap = new ConcurrentHashMap<>();
        this.catPrefix = catPrefix.toLowerCase();
        this.db = db;

        syncAlliances();
        this.loadChannels();
        this.loadCache();

        Set<DBNation> toCreate = new ObjectLinkedOpenHashSet<>();
        Map<Integer, WarCatReason> toDelete = new Int2ObjectLinkedOpenHashMap<>();
        Map<DBNation, TextChannel> toReassign = new Object2ObjectOpenHashMap<>();
        Map<Integer, Set<TextChannel>> duplicates = new Int2ObjectLinkedOpenHashMap<>();

        sync(null, null, null, toCreate, toDelete, toReassign, duplicates, false);
        if (!toCreate.isEmpty() || !toDelete.isEmpty() || !toReassign.isEmpty() || !duplicates.isEmpty()) {
            MessageChannel logChan = GuildKey.WAR_ROOM_LOG.getOrNull(getGuildDb());
            if (logChan != null) {
                StringBuilder pretty = new StringBuilder();
                if (!toCreate.isEmpty()) {
                    pretty.append("\n### Create\n");
                    for (DBNation nation : toCreate) {
                        pretty.append("\n").append(nation.getNation_id()).append(" - Create");
                    }
                }
                if (!toDelete.isEmpty()) {
                    pretty.append("\n### Delete\n");
                    for (Map.Entry<Integer, WarCatReason> entry : toDelete.entrySet()) {
                        pretty.append("\n").append(entry.getKey()).append(" - ").append(entry.getValue());
                    }
                }
                if (!toReassign.isEmpty()) {
                    pretty.append("\n### Reassign\n");
                    for (Map.Entry<DBNation, TextChannel> entry : toReassign.entrySet()) {
                        pretty.append("\n").append(entry.getKey().getNation_id()).append(" - ").append(entry.getValue().getName());
                    }
                }
                if (!duplicates.isEmpty()) {
                    pretty.append("\n### Duplicates\n");
                    for (Map.Entry<Integer, Set<TextChannel>> entry : duplicates.entrySet()) {
                        pretty.append("\n").append(entry.getKey()).append(" - ").append(entry.getValue().size());
                    }
                }
                if (pretty.length() > 0) {
                    String msg = "(startup) Synced war rooms. To confirm these changes, run:" +
                            CM.admin.sync.warrooms.cmd.toSlashMention() + "\n" + pretty;
                    RateLimitUtil.queueMessage(logChan, msg, true, 60);
                }
            }
        }
    }

    public void deleteRoom(WarRoom room, String message) {
        warRoomMap.remove(room.target.getNation_id());
        room.handleDelete(message);
        db.deleteWarRoomCache(Set.of(room.target.getNation_id()));
    }

    public WarRoom getWarRoomByChannelId(long channelId, boolean checkObject, WarCatReason reason) {
        WarRoom found = null;
        for (Map.Entry<Integer, WarRoom> entry : warRoomMap.entrySet()) {
            WarRoom room = entry.getValue();
            if (room.channelId == channelId) {
                found = room;
                break;
            }
        }
        if (checkObject) {
            GuildChannel channel = db.getGuild().getGuildChannelById(channelId);
            if (channel != null && channel instanceof StandardGuildMessageChannel gmc) {
                WarRoom room = getWarRoom(gmc, reason);
                if (room != null) return room;
            }
        }
        return null;
    }

    public void onChannelParent(StandardGuildMessageChannel channel, Category oldValue, Category newValue) {
        long channelId = channel.getIdLong();
        boolean isOldWarCat = WarRoomUtil.isWarRoomCategory(oldValue, catPrefix);
        boolean isNewWarCat = WarRoomUtil.isWarRoomCategory(newValue, catPrefix);

        if (isOldWarCat && !isNewWarCat) {
            // if new category is not a war room, delete the channel and war room
            WarRoom room = getWarRoomByChannelId(channelId, false, CHANNEL_MOVE);
            if (room != null) {
                deleteRoom(room, "No longer in a war room category");
            }
        } else if (!isOldWarCat && isNewWarCat) {
            // if category is a war room, add it if valid, else, set a message saying this channel is not a valid war room, and to setup war room
            WarRoom room = getWarRoom(channel, CHANNEL_MOVE);
            if (room == null) {
                DiscordUtil.sendMessage(channel, "This channel is not a valid war room. Rename it to match a valid nation ID or deleting the channel");
            }
        }
    }

    public void onChannelCreate(StandardGuildMessageChannel channel) {
        if (!WarRoomUtil.isWarRoomCategory(channel.getParentCategory(), catPrefix)) return;
        long channelId = channel.getIdLong();
        WarRoom existing = getWarRoomByChannelId(channelId, false, CHANNEL_CREATE);
        if (existing != null) {
            existing.addChannel(channelId, channel, CHANNEL_CREATE, false);
        }
    }

    public void onChannelDelete(StandardGuildMessageChannel channel) {
        if (!WarRoomUtil.isWarRoomCategory(channel.getParentCategory(), catPrefix)) return;
        long channelId = channel.getIdLong();
        WarRoom existing = getWarRoomByChannelId(channelId, false, CHANNEL_DELETE);
        if (existing != null) {
            warRoomMap.remove(existing.target.getNation_id());
            db.deleteWarRoomCache(Set.of(existing.target.getNation_id()));
        }
    }

    public void loadCache() {
        Map<Integer, Long> channelCache = db.loadWarRoomCache(new Function<Integer, Boolean>() {
            @Override
            public Boolean apply(Integer targetId) {
                WarRoom room = warRoomMap.get(targetId);
                DBNation nation = DBNation.getById(targetId);
                if (nation == null) {
                    if (room != null) deleteRoom(room, "Nation not found");
                    return true;
                }
                if (room != null && !room.isPlanning()) {
                    deleteRoom(room, "No active wars");
                    return true;
                }
                return false;
            }
        });
        for (Map.Entry<Integer, Long> entry : channelCache.entrySet()) {
            int targetId = entry.getKey();
            DBNation target = DBNation.getById(targetId);
            long channelId = entry.getValue();
            GuildChannel channel = db.getGuild().getGuildChannelById(channelId);
            if (target == null) {
                DiscordUtil.deleteChannelSafe(channel);
                continue;
            }
            WarRoom room = createInMap(target, WarCatReason.CACHE);
            room.addChannel(channelId, channel instanceof StandardGuildMessageChannel gmc ? gmc : null, WarCatReason.CACHE, false);
        }
    }

    public void loadChannels() {
        for (Category category : getCategories()) {
            for (GuildChannel channel : category.getChannels()) {
                if (channel instanceof StandardGuildMessageChannel gmc) {
                    WarRoom room = getWarRoom(gmc, EXISTING);
                }
            }
        }
    }

    public synchronized WarRoom createWarRoom(DBNation target, boolean createRoom, boolean forceCreate, boolean planning, WarCatReason reason) {
        WarRoom existing = warRoomMap.get(target.getNation_id());
        if (existing == null) {
            synchronized (target) {
                existing = warRoomMap.get(target.getNation_id());
                if (existing != null) {
                    if (existing.isChannelValid()) {
                        return existing;
                    }
                    if (forceCreate) {
                        synchronized (existing) {
                            long oldChannelId = existing.channelId;
                            StandardGuildMessageChannel channel = WarRoomUtil.createChannel2(this, existing, target, true, planning);
                            if (channel != null) {
                                existing.addChannel(channel.getIdLong(), channel, reason, oldChannelId != channel.getIdLong());
                            }
                        }
                    }
                    return existing;
                }
                if (createRoom || forceCreate) {
                    existing = createInMap(target, reason);
                    if (!existing.isChannelValid()) {
                        synchronized (existing) {
                            long oldChannelId = existing.channelId;
                            StandardGuildMessageChannel channel = WarRoomUtil.createChannel2(this, existing, target, true, planning);
                            if (channel != null) {
                                existing.addChannel(channel.getIdLong(), channel, reason, oldChannelId != channel.getIdLong());
                            }
                        }
                    }
                }
            }
        } else if (((createRoom || forceCreate) && existing.channelId == 0) || (forceCreate && !existing.isChannelValid())) {
            synchronized (existing) {
                if (!existing.isChannelValid()) {
                    long oldChannelId = existing.channelId;
                    StandardGuildMessageChannel channel = WarRoomUtil.createChannel2(this, existing, target, true, planning);
                    if (channel != null) {
                        existing.addChannel(channel.getIdLong(), channel, reason, oldChannelId != channel.getIdLong());
                    }
                }
            }
        }
        return existing;
    }

    public Map<Integer, WarRoom> getWarRoomMap() {
        return warRoomMap;
    }

    public Guild getGuild() {
        return db.getGuild();
    }

    public Set<Integer> getTrackedAllianceIds() {
        return allianceIds;
    }

    public void update(NationFilter filter, DBWar from, DBWar to) {
        int targetId;
        if (allianceIds.contains(to.getAttacker_aa())) {
            targetId = to.getDefender_id();
        } else if (allianceIds.contains(to.getDefender_aa())) {
            targetId = to.getAttacker_id();
        } else if (warRoomMap.containsKey(to.getAttacker_id())) {
            targetId = to.getAttacker_id();
        } else if (warRoomMap.containsKey(to.getDefender_id())) {
            targetId = to.getDefender_id();
        } else {
            return;
        }
        try {
            int participantId = to.getAttacker_id() == targetId ? to.getDefender_id() : to.getAttacker_id();
            DBNation target = Locutus.imp().getNationDB().getNationById(targetId);
            DBNation participant = Locutus.imp().getNationDB().getNationById(participantId);

            boolean create = false;
            WarCatReason reason = to.getStatus().isActive() ? getActiveReason(filter, target) : WarCatReason.NO_WARS;
            if (reason.isActive()) {
                reason = getActiveReason(filter, participant);
            }

            if (reason.isActive() && participant.getPosition() > 1 && (to.getDefender_id() == participantId || target.getPosition() > 1)) {
                create = true;
            }
            WarRoom room = target == null ? warRoomMap.get(targetId) : createWarRoom(target, create, create && from == null, false, reason);
            if (room != null) {
                if ((to.getStatus() == WarStatus.PEACE || to.getStatus() == WarStatus.ATTACKER_VICTORY || to.getStatus() == WarStatus.ATTACKER_VICTORY|| to.getStatus() == WarStatus.EXPIRED)) {
                    if (!room.hasOtherWar(filter, to.warId)) {
                        deleteRoom(room, "War ended: " + to.getStatus());
                        return;
                    }
                }
                if (target != null && to.getAttacker_id() == target.getNation_id()) {
                    CounterStat counterStat = Locutus.imp().getWarDb().getCounterStat(to);
                    if (counterStat != null) {
                        switch (counterStat.type) {
                            case UNCONTESTED:
                                break;
                            case GETS_COUNTERED:
                                break;
                            case IS_COUNTER:
                                break;
                            case ESCALATION:
                                break;
                        }
                    }
                }
                if ((from == null || to.getStatus() != from.getStatus())) {
                    room.updateParticipants(from, to, from == null);
                }
            }
        } catch (InsufficientPermissionException e) {
            db.setWarCatError(e);
            GuildKey.ENABLE_WAR_ROOMS.set(db, null, false);
        } catch (Throwable e) {
            MessageChannel logChan = GuildKey.WAR_ROOM_LOG.getOrNull(getGuildDb());
            if (logChan != null) {
                String msg = "Error with war war room for target-id: " + (targetId) + ":\n```java\n" + StringMan.stacktraceToString(e.getStackTrace()) + "```";
                RateLimitUtil.queueMessage(logChan, msg, true, 60);
            }
        }
    }

    private WarRoom createInMap(DBNation target, WarCatReason reason) {
        synchronized (warRoomMap) {
            WarRoom room = warRoomMap.get(target.getNation_id());
            if (room == null) {
                room = new WarRoom(this, target, reason);
                warRoomMap.put(target.getNation_id(), room);
            }
            return room;
        }
    }

    public WarRoom getWarRoom(StandardGuildMessageChannel channel, WarCatReason createReason) {
        if (!(channel instanceof ICategorizableChannel)) return null;
        ICategorizableChannel ic = (ICategorizableChannel) channel;
        Category parent = ic.getParentCategory();
        if (parent == null || !parent.getName().toLowerCase().startsWith(catPrefix)) {
            return null;
        }

        String channelName = channel.getName();
        String[] split = channelName.split("-");
        if (MathMan.isInteger(split[split.length - 1])) {
            int targetId = Integer.parseInt(split[split.length - 1]);
            DBNation target = Locutus.imp().getNationDB().getNationById(targetId);
            if (target != null) {
                WarRoom existing = warRoomMap.get(target.getNation_id());
                if (existing != null) {
                    existing.addChannel(channel.getIdLong(), channel, createReason, false);
                    return existing;
                }
                WarRoom room = createInMap(target, createReason);
                room.addChannel(channel.getIdLong(), channel, createReason, false);
                return room;
            } else {
                WarRoom room = warRoomMap.get(targetId);
                if (room != null) {
                    deleteRoom(room, "Nation not found");
                } else {
                    Logg.info("Delete channel (nation not found): " + channel.getName() + " | " + channel.getIdLong());
                    DiscordUtil.deleteChannelSafe(channel);
                }
            }
        }
        return null;
    }

    public String getCatPrefix() {
        return catPrefix;
    }

    public boolean isActive(NationFilter filter, Collection<DBWar> wars, DBNation nation) {
        return getActiveReason(filter, wars, nation).isActive();
    }

    public boolean isActive(NationFilter filter, DBNation nation) {
        return getActiveReason(filter, nation).isActive();
    }

    public void update(NationFilter filter, AbstractCursor attack) {
        if (attack.getAttack_type() == AttackType.PEACE) {
            return;
        }
        int attackerId = attack.getAttacker_id();
        int defenderId = attack.getDefender_id();
        WarRoom roomTmp = warRoomMap.get(attackerId);
        if (roomTmp == null) roomTmp = warRoomMap.get(defenderId);
        if (roomTmp == null) return;
        StandardGuildMessageChannel channel = roomTmp.channel;
        if (channel == null) return;
        WarRoom room = roomTmp;

        boolean value = room.target.getNation_id() == attack.getAttacker_id();
        boolean change = attack.getSuccess() == SuccessType.IMMENSE_TRIUMPH || (attack.getSuccess() != SuccessType.UTTER_FAILURE && !value);

        DBNation attacker = Locutus.imp().getNationDB().getNationById(attackerId);
        DBNation defender = Locutus.imp().getNationDB().getNationById(defenderId);

        String name1 = attacker.getNationUrlMarkup() + (attacker.getAlliance_id() != 0 ? (" of " + attacker.getAllianceUrlMarkup()) : "");
        String name2 = defender.getNationUrlMarkup() + (defender.getAlliance_id() != 0 ? (" of " + defender.getAllianceUrlMarkup()) : "");

        String message;
        boolean showLoot = false;
        boolean showInfra = false;
        boolean showCasualties = false;
        boolean showSuccess = false;

        switch (attack.getAttack_type()) {
            case AIRSTRIKE_INFRA:
                message = name1 + " issued " + attack.getAttack_type() + " against " + name2;
                showCasualties = true;
                showInfra = true;
                showSuccess = true;
                break;
            case AIRSTRIKE_SOLDIER:
            case AIRSTRIKE_TANK:
            case AIRSTRIKE_MONEY:
            case AIRSTRIKE_SHIP:
                String typeStr = attack.getAttack_type() + "";
                message = name1 + " issued " + typeStr + " against " + name2;
                showCasualties = true;
                showInfra = true;
                showSuccess = true;
                break;
            case AIRSTRIKE_AIRCRAFT:
                message = name1 + " issued " + attack.getAttack_type() + " against " + name2;
                showCasualties = true;
                showInfra = true;
                showSuccess = true;
                break;
            case GROUND:
                showCasualties = true;
                showLoot = true;
            case NAVAL_INFRA:
            case NAVAL_GROUND:
            case NAVAL_AIR:
            case NAVAL:
                message = name1 + " issued a " + attack.getAttack_type() + " attack against " + name2;
                showInfra = true;
                showSuccess = true;
                showCasualties = true;
                break;
            case MISSILE:
            case NUKE:
                message = name1 + " launched a " + attack.getAttack_type() + " against " + name2;
                showInfra = true;
                showSuccess = true;
                break;
            case FORTIFY:
                message = name1 + " fortified against " + name2;
                break;
            case VICTORY:
                if (!room.hasOtherWar(filter, attack.getWar_id())) {
                    deleteRoom(room, "Victory");
                    return;
                } else {
                    room.setPlanning(false);
                }
                message = name1 + " looted " + (attack.getLoot() == null ? "nothing" : ResourceType.toString(attack.getLoot())) + " from " + name2;
                break;
            case A_LOOT:
                message = name1 + " looted " + (attack.getLoot() == null ? "nothing" : ResourceType.toString(attack.getLoot())) + " from " + PW.getName(attack.getAllianceIdLooted(), true);
                break;
            case PEACE:
                if (!room.hasOtherWar(filter, attack.getWar_id())) {
                    deleteRoom(room, "Peace");
                    return;
                } else {
                    room.setPlanning(false);
                }
                message = name1 + " agreed to peace with " + name2;
                break;
            default:
                message = null;
                break;
        }

        if (change) {
            switch (attack.getAttack_type()) {
                case GROUND:
                    RateLimitUtil.queueWhenFree(() -> room.setGC(value));
                    break;
                case AIRSTRIKE_INFRA:
                case AIRSTRIKE_SOLDIER:
                case AIRSTRIKE_TANK:
                case AIRSTRIKE_MONEY:
                case AIRSTRIKE_SHIP:
                case AIRSTRIKE_AIRCRAFT:
                    RateLimitUtil.queueWhenFree(() -> room.setAC(value));
                    break;
                case NAVAL:
                    RateLimitUtil.queueWhenFree(() -> room.setBlockade(value));
                    break;
            }
        }

        if (message != null) {
            if (showSuccess) {
                String successType = attack.getSuccess().name();
                message += ". It was a " + successType;
            }
            if (showLoot && attack.getMoney_looted() != 0) {
                message += " and looted $" + MathMan.format(attack.getMoney_looted());
            }
            if (showInfra && attack.getInfra_destroyed() != 0) {
                double worth = PW.City.Infra.calculateInfra(Math.max(0, attack.getCity_infra_before() - attack.getInfra_destroyed()), attack.getCity_infra_before());
                message += ". " + MathMan.format(attack.getInfra_destroyed()) + " infra worth $" + MathMan.format(worth) + " was destroyed (previously " + attack.getCity_infra_before() + ")";
            }
            if (showCasualties) {
                Map<MilitaryUnit, Integer> attLosses = attack.getUnitLosses2(true);
                if (!attLosses.isEmpty()) message += "\nAttacker unit losses: " + StringMan.getString(attLosses);
                Map<MilitaryUnit, Integer> defLosses = attack.getUnitLosses2(false);
                if (!defLosses.isEmpty()) message += "\nDefender unit losses: " + StringMan.getString(defLosses);
            }

            if (RateLimitUtil.getCurrentUsed() > 10) {
                new DiscordChannelIO(channel).create().embed(attack.getAttack_type().toString(), message).sendWhenFree();
            } else {
                String emoji = "War Info";
                String cmd = "_" + Settings.commandPrefix(true) + "WarInfo " + attack.getWar_id();
                message += "\n\nPress `" + emoji + "` to view the war card";
                DiscordUtil.createEmbedCommand(channel, attack.getAttack_type().toString(), message, emoji, cmd);
            }
        }
    }

    public void update(DBNation previous, DBNation current) {
        DBNation nation = current;
        if (nation == null) nation = previous;

        boolean update = current == null;
        if (!update || (previous == null || previous.hasUnsetMil()) || (current == null || current.hasUnsetMil())) {
            return;
        }

        WarCatReason reason = NATION_UPDATE;
        WarRoom room = createWarRoom(current == null ? previous : current, false, false, false, reason);
        if (room != null) {
            if (current == null) {
                room.participants.remove(previous);

                if (room.participants.isEmpty() && !room.isPlanning()) {
                    deleteRoom(room, "No participants");
                }
                return;
            }
            if (previous == null || current.hasUnsetMil() || previous.hasUnsetMil()) {
                return;
            }
            if (current.getShips() == 0 & previous.getShips() > 0) {
                room.setBlockade(false);
            }
            if (current.getAircraft() == 0 & previous.getAircraft() > 0) {
                room.setAC(false);
            }
            if (current.getGroundStrength(false, false) == 0 & previous.getGroundStrength(false, false) > 0) {
                room.setGC(false);
            }

            if (room.channel != null) {
                if (room.target.equals(current)) {
                    Map<MilitaryUnit, Map.Entry<Integer, Boolean>> rebuys = new HashMap<>();
                    for (Building value : Buildings.values()) {
                        if (value instanceof MilitaryBuilding) {
                            MilitaryBuilding mB = (MilitaryBuilding) value;
                            int maxBuy = mB.getUnitDailyBuy() * mB.cap(Predicates.alwaysFalse()) * current.getCities();

                            int unitDiff = current.getUnits(mB.getMilitaryUnit()) - previous.getUnits(mB.getMilitaryUnit());
                            if (unitDiff > 0) {
                                rebuys.put(mB.getMilitaryUnit(), new KeyValue<>(unitDiff, unitDiff > maxBuy));
                            }
                        }
                    }

                    if (!rebuys.isEmpty()) {
                        String title = "Enemy rebuy";
                        StringBuilder body = new StringBuilder();
//                    if (rebuys.size() == 1) {
//                        Map.Entry<MilitaryUnit, Map.Entry<Integer, Boolean>> entry = rebuys.entrySet().iterator().next();
//                        title = "Enemy " + entry.getKey().name();
//                        Map.Entry<Integer, Boolean> rebuy = entry.getValue();
//                        if (rebuy.getValue()) title += " double";
//                        title += " rebuy";
//                    }
                        body.append(room.target.getNationUrlMarkup())
                                .append(" | ").append(room.target.getAllianceUrlMarkup())
                                .append('\n');
                        for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Boolean>> entry : rebuys.entrySet()) {
                            Map.Entry<Integer, Boolean> rebuy = entry.getValue();
                            body.append(entry.getKey().name());
                            if (rebuy.getValue()) body.append(" double");
                            body.append(" rebuy: +").append(MathMan.format(rebuy.getKey())).append('\n');
                        }
                        DiscordUtil.createEmbedCommand(room.channel, title, body.toString());
                    }
                }
            }

            if (current.getScore() != previous.getScore()) {
                room.updatePin(true);
            }
        }
    }

    public void processChannelCreation(WarRoom room, StandardGuildMessageChannel channel, boolean planning) {
        room.updatePin(false);
        RateLimitUtil.queueWhenFree(channel.upsertPermissionOverride(getGuild().getMemberById(Settings.INSTANCE.APPLICATION_ID))
                .setAllowed(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS)
        );
        RateLimitUtil.queueWhenFree(channel.upsertPermissionOverride(getGuild().getRolesByName("@everyone", false).get(0)).deny(Permission.VIEW_CHANNEL));

        room.addInitialParticipants(planning);
    }

    public int sort() {
        int moved = 0;
        Map<CityRanges, List<Category>> categoryRanges = new HashMap<>();
        List<Category> noRange = new ArrayList<>();
        for (Category category : getCategories()) {
            CityRanges range = getRangeFromCategory(category);
            if (range != null) categoryRanges.computeIfAbsent(range, f -> new ArrayList<>()).add(category);
            else noRange.add(category);
        }

        outer:
        for (Map.Entry<Integer, WarRoom> entry : warRoomMap.entrySet()) {
            WarRoom room = entry.getValue();
            if (room.channel != null) {
                Category category = room.channel.getParentCategory();
                CityRanges range = getRangeFromCategory(category);
                if (range != null && range.contains(room.target.getCities())) continue;

                for (Map.Entry<CityRanges, List<Category>> rangeEntry : categoryRanges.entrySet()) {
                    if (rangeEntry.getKey().contains(room.target.getCities())) {
                        for (Category newCat : rangeEntry.getValue()) {
                            if (newCat.getChannels().size() > 49) continue;
                            RateLimitUtil.queue(room.channel.getManager().setParent(newCat));
                            moved++;
                            continue outer;
                        }
                    }
                }

                if (range != null && !noRange.isEmpty()) {
                    for (Category newCat : noRange) {
                        if (newCat.getChannels().size() > 49) continue;
                        RateLimitUtil.queue(room.channel.getManager().setParent(newCat));
                        moved++;
                        continue outer;
                    }
                }

            }
        }
        return moved;
    }

    public Set<Category> getCategories() {
        return WarRoomUtil.getCategories(db, catPrefix);
    }

    public void sync() {
        sync(null, null, null, null, null, null, null, false);
    }

    public void sync(boolean force) {
        sync(null, null, null, null, null, null, null, force);
    }

    private void syncAlliances() {
        WarRoomUtil.updateAllianceIds(db, allianceIds);
    }


    public void sync(Map<DBWar, WarCatReason> warsLog,
                     Map<DBNation, WarCatReason> inactiveRoomLog,
                     Map<DBNation, WarCatReason> activeRoomLog,
                     Set<DBNation> toCreate,
                     Map<Integer, WarCatReason> toDelete,
                     Map<DBNation, TextChannel> toReassign,
                     Map<Integer, Set<TextChannel>> duplicates,
                     boolean create) {
        if (warRoomMap.isEmpty() && !create && warsLog == null) return;
        syncAlliances();
        NationFilter filter = getFilter();

        Set<DBWar> wars = Locutus.imp().getWarDb().getActiveWars(allianceIds, WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);
        Map<Integer, List<DBWar>> byTarget = new RankBuilder<>(wars).group(war -> allianceIds.contains(war.getAttacker_aa()) ? war.getDefender_id() : war.getAttacker_id()).get();

        for (Map.Entry<Integer, List<DBWar>> entry : byTarget.entrySet()) {
            List<DBWar> currentWars = entry.getValue();
            int targetId = entry.getKey();
            DBNation targetNation = Locutus.imp().getNationDB().getNationById(targetId);

            WarCatReason enemyReason = getActiveReason(filter, targetNation);
            boolean nonApp = false;
            if (targetNation != null) {
                for (DBWar war : currentWars) {
                    DBNation ally = war.getNation(!war.isAttacker(targetNation));
                    if (!nonApp && ally != null &&
                            ally.getPositionEnum().id > Rank.APPLICANT.id &&
                            (war.getDefender_id() == ally.getNation_id() || targetNation.getPositionEnum().id > Rank.APPLICANT.id)) {
                        nonApp = true;
                    }
                    WarCatReason allyReason = getActiveReason(filter, ally);
                    if (!enemyReason.isActive()) {
                        if (warsLog != null) warsLog.put(war, enemyReason);
                    } else if (!allyReason.isActive()) {
                        if (warsLog != null) warsLog.put(war, allyReason);
                    } else {
                        if (warsLog != null) warsLog.put(war, WarCatReason.ACTIVE);
                    }
                }
            }

            WarCatReason reason = getActiveReason(filter, currentWars, targetNation);
            if (!nonApp) {
                reason = ACTIVE_BUT_APPLICANT;
            }

            if (reason.isActive()) {
                WarRoom room;
                try {
                    room = createWarRoom(targetNation, create, create, false, reason);
                } catch (ErrorResponseException e) {
                    if (activeRoomLog != null) activeRoomLog.put(targetNation, WarCatReason.ROOM_ACTIVE_NO_FREE_CATEGORY);
                    continue;
                }
                if (room != null) {
                    if (room.channel != null) {
                        if (getGuild().getGuildChannelById(room.channel.getIdLong()) != null) {
                            if (activeRoomLog != null) activeRoomLog.put(targetNation, WarCatReason.ROOM_ACTIVE_EXISTS);
                        } else {
                            if (activeRoomLog != null) activeRoomLog.put(targetNation, WarCatReason.ROOM_ACTIVE_INVALID_CHANNEL);
                        }
                    } else {
                        if (toCreate != null) toCreate.add(targetNation);
                        if (activeRoomLog != null) activeRoomLog.put(targetNation, WarCatReason.ROOM_ACTIVE_NO_CHANNEL);
                    }
                    if (create) {
                        room.addInitialParticipants(currentWars);
                    }
                } else {
                    if (toCreate != null) toCreate.add(targetNation);
                    if (activeRoomLog != null) activeRoomLog.put(targetNation, WarCatReason.NOT_CREATED);
                }
            } else {
                WarRoom room = targetNation == null ? warRoomMap.get(targetId) : createWarRoom(targetNation, false, false, false, reason);
                if (room != null && room.channel != null) {
                    if (!room.isPlanning()) {
                        if (targetNation != null) {
                            if (inactiveRoomLog != null) inactiveRoomLog.put(targetNation, reason);
                        }
                        if (reason != ACTIVE_BUT_APPLICANT) {
                            if (toDelete != null) toDelete.put(targetId, reason);
                            if (create) {
                                deleteRoom(room, "Target is not active");
                            }
                        }
                    } else {
                        if (activeRoomLog != null) activeRoomLog.put(targetNation, WarCatReason.PLANNING_NO_ACTIVE_WARS);
                    }
                } else {
                    if (targetNation != null) {
                        if (inactiveRoomLog != null) inactiveRoomLog.put(targetNation, reason);
                    }
                }
            }
        }

        Set<Integer> duplicateChannels = new IntOpenHashSet();

        for (Category category : getGuild().getCategories()) {
            String catName = category.getName().toLowerCase();
            if (catName.startsWith(catPrefix)) {
                for (TextChannel channel : category.getTextChannels()) {
                    String channelName = channel.getName();
                    String[] split = channelName.split("-");
                    int targetId;
                    if (MathMan.isInteger(split[split.length - 1])) {
                        targetId = Integer.parseInt(split[split.length - 1]);
                    } else if (MathMan.isInteger(split[0])) {
                        targetId = Integer.parseInt(split[0]);
                    } else {
                        continue;
                    }

                    if (!duplicateChannels.add(targetId)) {
                        if (duplicates != null) duplicates.computeIfAbsent(targetId, f -> new ObjectLinkedOpenHashSet<>()).add(channel);
                        if (create) {
                            RateLimitUtil.queueWhenFree(channel.delete());
                        }
                    } else {
                        WarRoom existing = warRoomMap.get(targetId);
                        if (existing != null && existing.channel != null && existing.channel.getIdLong() != channel.getIdLong()) {
                            if (toReassign != null) toReassign.put(existing.target, channel);
                            if (create) {
                                existing.setChannel(channel);
                            }
                        }
                    }

                    if (byTarget.containsKey(targetId)) continue;

                    // delete because no active wars
                    DBNation target = Locutus.imp().getNationDB().getNationById(targetId);
                    WarRoom room = null;
                    WarCatReason reason = NO_WARS;
                    if (target != null) {
                        room = createWarRoom(target, create, false, false, reason);
                        if (room != null && room.isPlanning()) continue;
                    }
                    if (room != null) {
                        if (toDelete != null) toDelete.put(targetId, WarCatReason.NO_WARS);
                        if (create) {
                            deleteRoom(room, "No active wars");
                        }
                    } else {
                        if (toDelete != null) toDelete.put(targetId, WarCatReason.NO_WARS_CHANNEL);
                        if (create) {
                            RateLimitUtil.queueWhenFree(channel.delete());
                        }
                    }
                }
            }
        }
    }

    public synchronized void forEachRoom(DBNation targetOrParticipant, Consumer<WarRoom> onEach) {
        for (Map.Entry<Integer, WarRoom> entry : warRoomMap.entrySet()) {
            WarRoom room = entry.getValue();
            if (room.target.equals(targetOrParticipant) || room.participants.contains(targetOrParticipant)) {
                onEach.accept(room);
            }
        }
    }

    public GuildDB getGuildDb() {
        return db;
    }

    public NationFilter getFilter() {
        return WarRoomUtil.getFilter(db);
    }
}
