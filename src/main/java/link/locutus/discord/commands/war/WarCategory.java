package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;

import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static link.locutus.discord.util.MathMan.max;
import static com.google.gson.internal.$Gson$Preconditions.checkNotNull;

public class WarCategory {

    public static WarRoom getGlobalWarRoom(MessageChannel channel) {
        if (!(channel instanceof GuildMessageChannel)) return null;
        Guild guild = ((GuildMessageChannel) channel).getGuild();
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db != null) {
            WarCategory warRooms = db.getWarChannel();
            if (warRooms != null) {
                return warRooms.getWarRoom((GuildMessageChannel) channel);
            }
        }
        return null;
    }

    private final Map<Integer, WarRoom> warRoomMap;

    public static Set<WarRoom> getGlobalWarRooms(DBNation target) {
        Set<WarCategory> warCategories = null;
        for (Map.Entry<Long, GuildDB> entry : Locutus.imp().getGuildDatabases().entrySet()) {
            GuildDB db = entry.getValue();
            if (db.isDelegateServer()) continue;
            WarCategory warcat = db.getWarChannel();
            if (warcat != null) {
                if (warCategories == null) warCategories = new HashSet<>();
                warCategories.add(warcat);
            }
        }

        Set<WarRoom> result = null;
        for (WarCategory warcat : warCategories) {
            if (warcat != null) {
                WarRoom channel = warcat.get(target, false, false);
                if (channel != null) {
                    if (result == null) result = new HashSet<>();
                    result.add(channel);
                }
            }
        }

        return result;
    }

    public Map<Integer, WarRoom> getWarRoomMap() {
        return warRoomMap;
    }

    private final String catPrefix;
    private final Guild guild;
    private final Set<Integer> allianceIds = new HashSet<>();
    private final Set<Integer> allies = new HashSet<>();
    private final GuildDB db;

    public Guild getGuild() {
        return guild;
    }

    public WarCategory(Guild guild, String catPrefix) {
        checkNotNull(guild);
        this.warRoomMap = new ConcurrentHashMap<>();
        this.catPrefix = catPrefix.toLowerCase();
        this.guild = guild;
        this.db = Locutus.imp().getGuildDB(guild);

        syncAlliances();
        loadChannels();
        sync();
    }

    public Set<Integer> getTrackedAllianceIds() {
        return allianceIds;
    }

    public void loadChannels() {
        for (Category category : getCategories()) {
            for (GuildMessageChannel channel : category.getTextChannels()) {
                WarRoom room = getWarRoom(channel);
            }
        }
    }

    public void update(DBWar from, DBWar to) {
        try {
            int targetId;
            if (allianceIds.contains(to.getAttacker_aa())) {
                targetId = to.getDefender_id();
            } else if (allianceIds.contains(to.getDefender_aa())) {
                targetId = to.getAttacker_id();
            } else if (warRoomMap.containsKey(to.getAttacker_id())) {
                targetId = to.getAttacker_id();
            } else {
                return;
            }


            int participantId = to.getAttacker_id() == targetId ? to.getDefender_id() : to.getAttacker_id();
            DBNation target = Locutus.imp().getNationDB().getNation(targetId);
            DBNation participant = Locutus.imp().getNationDB().getNation(participantId);

            boolean create = false;
            if (isActive(target) && isActive(participant) && participant.getPosition() > 1 && (to.getDefender_id() == participantId || target.getPosition() > 1)) {
                create = true;
            }

            WarRoom room = target == null ? warRoomMap.get(targetId) : get(target, create, create && from == null);
            if (room != null) {
                if (to.getAttacker_id() == target.getNation_id()) {
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
                if (to != null && (to.getStatus() == WarStatus.PEACE || to.getStatus() == WarStatus.ATTACKER_VICTORY || to.getStatus() == WarStatus.ATTACKER_VICTORY|| to.getStatus() == WarStatus.EXPIRED)) {
                    if (!room.hasOtherWar(to.warId)) {
                        room.delete("War ended: " + to.getStatus());
                        return;
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
            e.printStackTrace();
        }
    }

    public WarRoom getWarRoom(GuildMessageChannel channel) {
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
            DBNation target = Locutus.imp().getNationDB().getNation(targetId);
            if (target != null) {
                return get(target, true, false);
            } else {
                System.out.println("Delete channel (nation not found): " + channel.getName() + " | " + channel.getIdLong());
                RateLimitUtil.queueWhenFree(channel.delete());
            }
        }
        return null;
    }

    public String getCatPrefix() {
        return catPrefix;
    }

    public boolean isActive(Collection<DBWar> wars, DBNation nation) {
        return getActiveReason(wars, nation).isActive();
    }

    public boolean isActive(DBNation nation) {
        return getActiveReason(nation).isActive();
    }

    public WarCatReason getActiveReason(Collection<DBWar> wars, DBNation nation) {
        WarCatReason reason = getActiveReason(nation);
        if (!reason.isActive()) return reason;
        for (DBWar war : wars) {
            int attackerId = war.getAttacker_id() == nation.getNation_id() ? war.getDefender_id() : war.getAttacker_id();
            DBNation attacker = Locutus.imp().getNationDB().getNation(attackerId);
            if (attacker != null) {
                reason = getActiveReason(attacker);
                if (reason.isActive()) return reason;
            }
        }
        if (wars.isEmpty()) {
            // no wars
            return WarCatReason.NO_WARS;
        }
        if (wars.size() == 1) {
            return reason;
        }
        return WarCatReason.WARS_NOT_AGAINST_ACTIVE;
    }

    public WarCatReason getActiveReason(DBNation nation) {
        if (nation == null) {
            return WarCatReason.NATION_NOT_FOUND;
        }
        if (nation.getVm_turns() > 0) {
            return WarCatReason.VACATION_MODE;
        }
        int activeM = nation.active_m();
        if (activeM >= 2880) {
            return WarCatReason.INACTIVE;
        }
        if (activeM > 1440 && nation.getOff() == 0) {
            if (nation.getPositionEnum() == Rank.APPLICANT) {
                return WarCatReason.INACTIVE_APPLICANT;
            }
            if (nation.getPositionEnum().id <= Rank.APPLICANT.id) {
                return WarCatReason.INACTIVE_NONE;
            }
        }
        NationFilter filter = GuildKey.WAR_ROOM_FILTER.getOrNull(db);
        if (filter != null && !filter.test(nation)) {
            return WarCatReason.FILTER;
        }
        return WarCatReason.ACTIVE;
    }

    public void update(AbstractCursor attack) {
        if (attack.getAttack_type() == AttackType.PEACE) {
            return;
        }
        int attackerId = attack.getAttacker_id();
        int defenderId = attack.getDefender_id();
        WarRoom roomTmp = warRoomMap.get(attackerId);
        if (roomTmp == null) roomTmp = warRoomMap.get(defenderId);
        if (roomTmp == null || roomTmp.channel == null) return;
        WarRoom room = roomTmp;

        boolean value = room.target.getNation_id() == attack.getAttacker_id();
        boolean change = attack.getSuccess() == SuccessType.IMMENSE_TRIUMPH || (attack.getSuccess() != SuccessType.UTTER_FAILURE && !value);

        DBNation attacker = Locutus.imp().getNationDB().getNation(attackerId);
        DBNation defender = Locutus.imp().getNationDB().getNation(defenderId);

        String name1 = attacker.getNationUrlMarkup(true) + (attacker.getAlliance_id() != 0 ? (" of " + attacker.getAllianceUrlMarkup(true)) : "");
        String name2 = defender.getNationUrlMarkup(true) + (defender.getAlliance_id() != 0 ? (" of " + defender.getAllianceUrlMarkup(true)) : "");

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
                if (!room.hasOtherWar(attack.getWar_id())) {
                    room.delete("Victory");
                    return;
                } else {
                    room.setPlanning(false);
                }
                message = name1 + " looted " + (attack.getLoot() == null ? "nothing" : ResourceType.resourcesToString(attack.getLoot())) + " from " + name2;
                break;
            case A_LOOT:
                message = name1 + " looted " + (attack.getLoot() == null ? "nothing" : ResourceType.resourcesToString(attack.getLoot())) + " from " + PW.getName(attack.getAllianceIdLooted(), true);
                break;
            case PEACE:
                if (!room.hasOtherWar(attack.getWar_id())) {
                    room.delete("Peace");
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
                double worth = PW.City.Infra.calculateInfra(attack.getCity_infra_before() - attack.getInfra_destroyed(), attack.getCity_infra_before());
                message += ". " + MathMan.format(attack.getInfra_destroyed()) + " infra worth $" + MathMan.format(worth) + " was destroyed";
            }
            if (showCasualties) {
                Map<MilitaryUnit, Integer> attLosses = attack.getUnitLosses2(true);
                if (!attLosses.isEmpty()) message += "\nAttacker unit losses: " + StringMan.getString(attLosses);
                Map<MilitaryUnit, Integer> defLosses = attack.getUnitLosses2(false);
                if (!defLosses.isEmpty()) message += "\nDefender unit losses: " + StringMan.getString(defLosses);
            }

            if (RateLimitUtil.getCurrentUsed() > 10) {
                new DiscordChannelIO(room.getChannel()).create().embed(attack.getAttack_type().toString(), message).sendWhenFree();
            } else {
                String emoji = "War Info";
                String cmd = "_" + Settings.commandPrefix(true) + "WarInfo " + attack.getWar_id();
                message += "\n\nPress `" + emoji + "` to view the war card";
                DiscordUtil.createEmbedCommand(room.getChannel(), attack.getAttack_type().toString(), message, emoji, cmd);
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

        WarRoom room = get(current == null ? previous : current, false, false);
        if (room != null) {
            if (current == null) {
                room.participants.remove(previous);

                if (room.participants.isEmpty() && !room.isPlanning()) {
                    room.delete("No participants");
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
                            int maxBuy = mB.getUnitDailyBuy() * mB.cap(f -> false) * current.getCities();

                            int unitDiff = current.getUnits(mB.getMilitaryUnit()) - previous.getUnits(mB.getMilitaryUnit());
                            if (unitDiff > 0) {
                                rebuys.put(mB.getMilitaryUnit(), new AbstractMap.SimpleEntry<>(unitDiff, unitDiff > maxBuy));
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
                        body.append(room.target.getNationUrlMarkup(true))
                                .append(" | ").append(room.target.getAllianceUrlMarkup(true))
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

    public void processChannelCreation(WarRoom room, TextChannel channel, boolean planning) {
        room.updatePin(false);
        RateLimitUtil.queueWhenFree(channel.upsertPermissionOverride(guild.getMemberById(Settings.INSTANCE.APPLICATION_ID))
                .setAllowed(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS)
        );
        RateLimitUtil.queueWhenFree(channel.upsertPermissionOverride(guild.getRolesByName("@everyone", false).get(0)).deny(Permission.VIEW_CHANNEL));

        room.addInitialParticipants(planning);
    }

    public WarCategory.WarRoom createChannel(User author, Consumer<String> errorOutput, boolean ping, boolean addMember, boolean addMessage, DBNation target, Collection<DBNation> attackers) {
        ApiKeyPool mailKey = db.getMailKey();
        if (addMessage && mailKey == null) {
            errorOutput.accept("No mail key available. See: " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.API_KEY.name() + "`");
            addMessage = false;
        }
        GuildDB db = Locutus.imp().getGuildDB(guild);
        WarCategory.WarRoom room = get(target, true, true, true, true);
        room.getChannel();

        TextChannel channel = room.getChannel();

        String declareUrl = target.getDeclareUrl();
        String channelUrl = "https://discord.com/channels/" + guild.getIdLong() + "/" + channel.getIdLong();
        String info = "> A counter is when an alliance declares a war on a nation for attacking one of its members/applicants. We usually only order counters for unprovoked attacks on members.\n" +
                "About Counters: https://docs.google.com/document/d/1eJfgNRk6L72G6N3MT01xjfn0CzQtYibwnTg9ARFknRg";

        if (addMessage) {
            RateLimitUtil.queueWhenFree(channel.sendMessage(info));
        }

        for (DBNation attacker : attackers) {
            User user = attacker.getUser();
            if (user == null) {
                errorOutput.accept("No user for: " + attacker.getNation() + " | " + attacker.getAllianceName() + ". Have they used " + CM.register.cmd.toSlashMention() + " ?");
                continue;
            }

            Member member = guild.getMemberById(user.getIdLong());
            if (member == null) {
                errorOutput.accept("No member for: " + attacker.getNation() + " | " + attacker.getAllianceName() + ". Are they on this discord?");
                continue;
            }

            if (addMember) {
                List<PermissionOverride> overrideds = channel.getMemberPermissionOverrides();
                boolean contains = false;
                for (PermissionOverride overrided : overrideds) {
                    if (member.equals(overrided.getMember())) {
                        contains = true;
                        break;
                    }
                }

                if (!contains) {
                    RateLimitUtil.complete(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
                    if (ping) {
                        String msg = (author != null ? author.getName() : "null") + " added " + user.getAsMention();

                        if (addMessage) {
                            String warType = target.getAvg_infra() > 2000 && target.getAvg_infra() > attacker.getAvg_infra() ? "attrition" : "raid";
                            msg += ". You have been ordered to declare a war of type `" + warType + "` with reason `counter`.";

                            Role econRole = Roles.ECON.toRole(guild);
                            String econRoleName = econRole != null ? "`@" + econRole.getName() + "`" : "ECON";
                            MessageChannel rssChannel = db.getResourceChannel(attacker.getAlliance_id());
                            MessageChannel grantChannel = db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL);

                            if (rssChannel != null) {
                                if (Boolean.TRUE.equals(db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW))) {
                                    msg += " Withdraw funds from: " + rssChannel.getAsMention() + "  **BEFORE** you declare.";
                                } else {
                                    msg += " Ping " + econRoleName + " in " + rssChannel.getAsMention() + " to withdraw funds **BEFORE** you declare.";
                                }
                            }
                            if (grantChannel != null) msg += " Request funds from: " + grantChannel.getAsMention() + " **BEFORE** you declare.";

                            if (target.getGroundStrength(true, true) > attacker.getGroundStrength(true, false)) {
                                msg += "\nThe enemy has more ground. You must ensure you have funds to switch to e.g. mmr=5551 and buy tanks after declaring";
                            }

                            String title = "Counter Attack/" + channel.getIdLong();
                            StringBuilder body = new StringBuilder();
                            body.append(info);
                            body.append("\n\n" + msg);
                            body.append("\n- target: " + declareUrl);
                            body.append("\n\nCheck the war room for further details: " + channelUrl);
                            String mailBody = MarkupUtil.transformURLIntoLinks(MarkupUtil.markdownToHTML(body.toString()));

                            try {
                                attacker.sendMail(mailKey, title, mailBody, false);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        RateLimitUtil.queue(channel.sendMessage(msg + "\n- <" + declareUrl + (">")));
                    }
                }
            }
        }

        if (addMessage) {

        }

        return room;
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

    public CityRanges getRangeFromCategory(Category category) {
        String[] split = category.getName().split("-", 2);
        if (split.length == 2) {
            String filterStr = split[1];
            if (filterStr.charAt(0) == 'c') {
                if (!filterStr.contains("-") && !filterStr.contains("+")) filterStr += "+";
                try {
                    return CityRanges.parse(filterStr);
                } catch (IllegalArgumentException ignore) {}
            }
        }
        return null;
    }

    public class WarRoom {
        public final DBNation target;
        private final Set<DBNation> participants;
        public TextChannel channel;
        public boolean planning;
        public boolean enemyGc;
        public boolean enemyAc;
        public boolean enemyBlockade;

        public WarRoom(DBNation target) {
            checkNotNull(target);
            this.target = target;
            this.participants = new HashSet<>();
            loadParticipants(false);
        }

        private void loadParticipants(boolean force) {
            if (force) {
                addInitialParticipants(false);
            }
            if (channel != null) {
                for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                    Member member = override.getMember();
                    if (member == null) continue;
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (nation != null) {
                        participants.add(nation);
                    }
                }

            }
        }

        private Message getMessage(String topic) {
            if (topic == null || topic.isEmpty()) return null;
            try {
                topic = topic.split(" ")[0];
                if (topic.contains("/")) {
                    String[] split = topic.split("/");
                    topic = split[split.length - 1];
                }
                return RateLimitUtil.complete(channel.retrieveMessageById(topic));
            } catch (Exception e) {
                return null;
            }
        }

        public String url() {
            if (channel == null) return null;
            return "https://discord.com/channels/" + guild.getIdLong() + "/" + channel.getIdLong();
        }

        public IMessageBuilder updatePin(boolean update) {
            if (channel == null) {
                return null;
            }

            String topic = channel.getTopic();

            boolean updatePin = false;
            DiscordChannelIO io = new DiscordChannelIO(channel, () -> getMessage(topic));
            IMessageBuilder msg = io.getMessage();
            if (msg == null) {
                msg = io.create();
                updatePin = true;
                update = true;
            }

            if (update) {
                StringBuilder body = new StringBuilder();

                body.append("**Enemy:** ").append(target.getNationUrlMarkup(true))
                        .append(" | ").append(target.getAllianceUrlMarkup(true));
                body.append(target.toMarkdown(true, true, false, false, true, false));
                body.append("\n");

                Set<DBWar> wars = target.getActiveWars();
                for (DBWar war : wars) {
                    boolean defensive = war.getAttacker_id() == target.getNation_id();
                    DBNation participant = Locutus.imp().getNationDB().getNation(war.getAttacker_id() == target.getNation_id() ? war.getDefender_id() : war.getAttacker_id());

                    if (participant != null && (participants.contains(participant) || participant.active_m() < 2880)) {
                        String typeStr = defensive ? "\uD83D\uDEE1 " : "\uD83D\uDD2A ";
                        body.append(typeStr).append("`" + participant.getNation() + "`")
                                .append(" | ").append(participant.getAllianceName());

                        WarCard card = new WarCard(war, false);
                        if (card.blockaded == participant.getNation_id()) body.append("\u26F5");
                        if (card.airSuperiority != 0 && card.airSuperiority == participant.getNation_id()) body.append("\u2708");
                        if (card.groundControl != 0 && card.groundControl == participant.getNation_id()) body.append("\uD83D\uDC82");

                        body.append(participant.toMarkdown(true, false, false, true, false));
                    }
                }
                body.append("\n");
                body.append("Note: These figures are only updated every 5m");

                EmbedBuilder builder = new EmbedBuilder();

                builder.setDescription(body.toString().replaceAll(" \\| ", "|"));


                msg.clearEmbeds();
                msg.clearButtons();
                msg.embed(builder.build());
                msg.commandButton(CommandBehavior.UNPRESS, CM.war.room.pin.cmd, "Update");
                try {
                    CompletableFuture<IMessageBuilder> sent = msg.send();
                    if (sent != null) msg = sent.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }

            if (updatePin) {
                String newTopic = DiscordUtil.getChannelUrl(channel) + "/" + msg.getId() + " " + CM.war.room.pin.cmd.toSlashMention();
                RateLimitUtil.queue(channel.getManager().setTopic(newTopic));
                RateLimitUtil.queue(channel.pinMessageById(msg.getId()));
            } else {
                System.out.println("Topic is valid message " + topic + " | " + msg);
            }

            return msg;
        }

        public boolean setGC(boolean value) {
            if (value == enemyGc) return false;
            enemyGc = value;
            return setSymbol("\uD83D\uDC82", value);
        }

        public boolean isGC() {
            if (channel != null) return channel.getName().contains("\uD83D\uDC82");
            return false;
        }

        public boolean setAC(boolean value) {
            if (value == enemyAc) return false;
            enemyAc = value;
            return setSymbol("\u2708", value);
        }

        public boolean isAC() {
            if (channel != null) return channel.getName().contains("\u2708");
            return false;
        }

        public boolean setBlockade(boolean value) {
            if (enemyBlockade == value) return false;
            enemyBlockade = value;
            return setSymbol("\u26F5", value);
        }

        public boolean isBlockade() {
            if (channel != null) return channel.getName().contains("\u26F5");
            return false;
        }

        public boolean setPlanning(boolean value) {
            if (value == planning) return false;
            planning = value;
            return setSymbol("\uD83D\uDCC5", value);
        }

        public boolean isPlanning() {
            if (channel != null) return channel.getName().contains("\uD83D\uDCC5");
            return false;
        }

        public boolean setSymbol(String symbol, boolean value) {
            if (channel != null) {
                String name = channel.getName();
                if (name.contains(symbol) != value) {
                    if (value) {
                        name = symbol + name;
                    } else if (name.contains(symbol)) {
                        name = name.replace(symbol, "");
                        if (name.endsWith("-")) name = name.substring(0, name.length() - 1);
                    } else {
                        return false;
                    }
                    RateLimitUtil.queue(channel.getManager().setName(name));
                    return true;
                }
            }
            return false;
        }

//        public boolean updatePerms() {
//            boolean modified = false;
//            Set<DBNation> toAdd = new HashSet<>(participants.keySet());
//
//            System.out.println("Participants: " + StringMan.getString(participants) + " | " + channel.getName());
//
//            for (PermissionOverride memberOverride : channel.getMemberPermissionOverrides()) {
//                System.out.println("Found permission override: " + memberOverride);
//                Member member = memberOverride.getMember();
//                if (member == null) {
//                    member = memberOverride.getManager().getMember();
//                }
//
//                // Failed to fetch member
//                if (member == null) {
//                    System.out.println("Unknown member override " + memberOverride);
//                    return false;
//                }
//
//                DBNation nation = DiscordUtil.getNation(member.getIdLong());
//                if (nation == null || !participants.containsKey(nation)) {
//                    if (!isPlanning()) {
//                        System.out.println("Remove participant: " + (nation == null));
////                        link.locutus.discord.util.RateLimitUtil.queue(memberOverride.delete());
////                        modified = true;
//                    }
//                } else {
//                    toAdd.remove(nation);
//                }
//            }
//
//            for (DBNation nation : toAdd) {
//                User user = nation.getUser();
//                if (user != null) {
//                    Member member = guild.getMember(user);
//                    if (member != null) {
//                        modified = true;
//                        if (channel.getPermissionOverride(member) == null) {
//                            if (channel.getPermissionOverrides().isEmpty()) {
//                                if (channel.getPermissionOverride(member) != null) continue;
//                            }
//                            {
//                                PermissionOverride result = link.locutus.discord.util.RateLimitUtil.complete(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
//                                String mention = allianceIds.contains(nation.getAlliance_id()) ? member.getAsMention() : member.getEffectiveName();
//                                String msg = "`" + mention + "` joined the fray";
//                                Role milcomRole = Roles.MILCOM.toRole(guild);
//                                if (milcomRole != null) msg += ". Ping `" + milcomRole.getName() + "`  for assistance";
//                                link.locutus.discord.util.RateLimitUtil.queue(channel.sendMessage(msg));
//                            }
//                        }
//                    }
//                }
//            }
//            return modified;
//        }

        public TextChannel getChannel() {
            return getChannel(true);
        }

        public TextChannel getChannel(boolean create) {
            return getChannel(create, false);
        }

        public TextChannel getChannel(boolean create, boolean planning) {
            if (channel != null) {
                long dateCreated = channel.getTimeCreated().toEpochSecond() * 1000L;
                if (System.currentTimeMillis() - dateCreated > TimeUnit.MINUTES.toMillis(1)) {
                    if (guild.getTextChannelById(channel.getIdLong()) == null) {
                        channel = null;
                    }
                }
                if (channel != null) return channel;
            }

            synchronized (target.getName()) {
                for (Category category : guild.getCategories()) {
                    String catName = category.getName().toLowerCase();
                    if (catName.startsWith(catPrefix)) {
                        for (TextChannel channel : category.getTextChannels()) {
                            String channelName = channel.getName();
                            String[] split = channelName.split("-");
                            if (MathMan.isInteger(split[split.length - 1])) {
                                int targetId = Integer.parseInt(split[split.length - 1]);
                                if (targetId == target.getNation_id()) {
                                    this.channel = channel;
                                }
                            }
                        }
                    }
                }

                if (create && channel == null) {
                    Category useCat = null;
                    for (Category category : guild.getCategories()) {
                        String catName = category.getName().toLowerCase();
                        if (!catName.startsWith(catPrefix)) continue;
                        List<GuildChannel> channels = category.getChannels();
                        if (channels.size() >= 49) continue;

                        CityRanges range = getRangeFromCategory(category);
                        if (range != null) {
                            if (range.contains(target.getCities())) {
                                useCat = category;
                                break;
                            } else if (useCat == null) {
                                useCat = category;
                            }
                        } else {
                            useCat = category;
                        }
                    }

                    if (useCat == null) {
                        for (int i = 0; ; i++) {
                            String name = catPrefix + "-" + i;
                            List<Category> existingCat = guild.getCategoriesByName(name, true);
                            if (existingCat.isEmpty()) {
                                useCat = RateLimitUtil.complete(guild.createCategory(name));
                                PermissionOverrideAction upsert = useCat.upsertPermissionOverride(guild.getMemberById(Settings.INSTANCE.APPLICATION_ID));
                                for (Permission perm : getCategoryPermissions()) {
                                    upsert = upsert.setAllowed(perm);
                                }
                                RateLimitUtil.queue(upsert);

                                RateLimitUtil.queue(useCat.upsertPermissionOverride(guild.getRolesByName("@everyone", false).get(0))
                                        .deny(Permission.VIEW_CHANNEL));

                                Role milcomRole = Roles.MILCOM.toRole(guild);
                                if (milcomRole != null) {
                                    RateLimitUtil.complete(useCat.upsertPermissionOverride(milcomRole)
                                            .setAllowed(Permission.VIEW_CHANNEL));
                                }
                                Role advisor = Roles.MILCOM_NO_PINGS.toRole(guild);
                                if (advisor != null) {
                                    RateLimitUtil.complete(useCat.upsertPermissionOverride(advisor)
                                            .setAllowed(Permission.VIEW_CHANNEL));
                                }
                                break;
                            }
                        }
                    }
                    String name = target.getNation() + "-" + target.getNation_id();
                    if (planning) name = "\uD83D\uDCC5" + name;
                    channel = RateLimitUtil.complete(useCat.createTextChannel(name));
                    processChannelCreation(this, channel, planning);
                }
            }
            return channel;
        }

        public boolean hasChannel() {
            return channel != null;
        }

        public void setChannel(TextChannel channel) {
            this.channel = channel;
            planning = isPlanning();
            enemyGc = isGC();
            enemyAc = isAC();
            enemyBlockade = isBlockade();
        }

        public void delete(String reason) {
            if (channel != null) {
                System.out.println("Delete channel (" + reason + "): " + channel.getName() + " | " + channel.getIdLong());
                RateLimitUtil.queue(channel.delete());
                warRoomMap.remove(target.getNation_id());
                channel = null;
            }
        }

//        public boolean update(List<DBWar> wars) {
//            if (wars.isEmpty()) {
//                if (isPlanning()) return false;
//                delete();
//                return false;
//            }
//
//            boolean checkCounter = false;
//
//            Map<Integer, Member> toAdd = new HashMap<>();
//
//            for (DBWar war : wars) {
//                int assignedId = war.attacker_id == target.getNation_id() ? war.defender_id : war.attacker_id;
//                DBNation nation = Locutus.imp().getNationDB().getNation(assignedId);
//                if (nation == null || nation.getPosition() <= 1 || nation.getVm_turns() != 0 || nation.active_m() > 2880 || !allies.contains(nation.getAlliance_id())) {
//                    continue;
//                }
//                User discordUser = nation.getUser();
//                if (discordUser == null) continue;
//                Member member = guild.getMember(discordUser);
//                if (member == null) continue;
//
//                toAdd.put(nation.getNation_id(), member);
//                if (!participants.containsKey(nation)) {
//                    participants.put(nation, new WarCard(war, true));
//                    checkCounter = true;
//                }
//            }
//
//            participants.entrySet().removeIf(e -> !toAdd.containsKey(e.getKey().getNation_id()));
//            if (participants.isEmpty()) {
//                if (channel != null && !isPlanning()) {
//                    delete();
//                }
//                return false;
//            }
//            getChannel();
////            boolean modifiedPerms = updatePerms();
//
//            int isCounter = 0;
//            int isNotCounter = 0;
//            if (checkCounter) {
//                for (DBWar war : wars) {
//                    CounterStat counterStat = Locutus.imp().getWarDb().getCounterStat(war);
//                    if (counterStat != null) {
//                        if (counterStat.type.equals(CounterType.IS_COUNTER) && !enemies.contains(target.getAlliance_id())) {
//                            isCounter++;
//                        } else {
//                            isNotCounter++;
//                        }
//                    }
//                }
//                if (isCounter > 0 && isNotCounter == 0) {
//                    setCounter(true);
//                } else {
//                    setCounter(false);
//                }
//            }
//
//
////            updatePin(modifiedPerms);
//
//            return true;
//        }

        public void updateParticipants(DBWar from, DBWar to) {
            updateParticipants(from, to, false);
        }

        public void updateParticipants(DBWar from, DBWar to, boolean ping) {
            int assignedId = to.getAttacker_id() == target.getNation_id() ? to.getDefender_id() : to.getAttacker_id();
            DBNation nation = Locutus.imp().getNationDB().getNation(assignedId);
            if (nation == null) return;

            User user = nation.getUser();
            Member member = user == null ? null : guild.getMember(user);
            if (from == null) {
                participants.add(nation);

                if (channel != null && member != null && channel.getPermissionOverride(member) == null) {
                    RateLimitUtil.queue(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
                    if (ping && channel != null) {
                        String msg = member.getAsMention() + " joined the fray";
                        RateLimitUtil.queue(channel.sendMessage(msg));
                    }
                }
            } else if (channel != null && (to.getStatus() == WarStatus.EXPIRED || to.getStatus() == WarStatus.ATTACKER_VICTORY || to.getStatus() == WarStatus.DEFENDER_VICTORY)) {
                participants.remove(nation);

                if (member != null) {
                    PermissionOverride override = channel.getPermissionOverride(member);
                    if (override != null) {
                        RateLimitUtil.queue(override.delete());
                    }
                }
            }
        }

        public void addInitialParticipants(boolean planning) {
            addInitialParticipants(target.getActiveWars(), planning);
        }

        public void addInitialParticipants(Collection<DBWar> wars) {
            addInitialParticipants(wars, false);
        }

        public void addInitialParticipants(Collection<DBWar> wars, boolean planning) {
            boolean planned = planning || isPlanning();
            if (!planned && wars.isEmpty()) {
                if (channel != null) {
                    delete("No wars");
                }
                return;
            }

            Member botMember = guild.getMemberById(Settings.INSTANCE.APPLICATION_ID);
            if (botMember != null && channel != null && channel.getPermissionOverride(botMember) == null) {
                RateLimitUtil.queue(channel.upsertPermissionOverride(botMember)
                        .grant(Permission.VIEW_CHANNEL)
                        .grant(Permission.MANAGE_CHANNEL)
                        .grant(Permission.MANAGE_PERMISSIONS)
                );
            }

            Set<DBNation> added = new HashSet<>();
            Set<Member> addedMembers = new HashSet<>();

            for (DBWar war : wars) {
                DBNation other = war.getNation(!war.isAttacker(target));
                if (other == null) continue;

                added.add(other);
                participants.add(other);

                User user = other.getUser();
                Member member = user == null ? null : guild.getMember(user);

                if (member != null) {
                    addedMembers.add(member);
                }
                if (channel != null && member != null && channel.getPermissionOverride(member) == null) {
                    RateLimitUtil.queue(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
                }
            }
            if (!planned && channel != null) {
                for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                    Member member = override.getMember();
                    if (member == null) continue;
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (!added.contains(nation) && !addedMembers.contains(member)) {
                        RateLimitUtil.queue(override.delete());
                    }
                }
            }
        }

        public boolean isParticipant(DBNation nation, boolean forceUpdate) {
            if (forceUpdate) {
                addInitialParticipants(false);
            }
            return participants.contains(nation);
        }

        private boolean hasOtherWar(int warId) {
            boolean hasWar = false;
            for (DBWar war : target.getWars()) {
                if (war.getWarId() == warId) continue;
                DBNation other = war.getNation(!war.isAttacker(target));
                if (other != null && isActive(other) && allianceIds.contains(other.getAlliance_id())) {
                    hasWar = true;
                    break;
                }
            }
            return hasWar;
        }

        public Set<DBNation> getParticipants() {
            return participants;
        }

        public String getChannelMention() {
            TextChannel tc = getChannel(false, false);
            return tc == null ? null : tc.getAsMention();
        }
    }

    private static Permission[] CATEGORY_PERMISSIONS = new Permission[]{
            Permission.VIEW_CHANNEL,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_PERMISSIONS
    };

    public Permission[] getCategoryPermissions() {
        return CATEGORY_PERMISSIONS;
    }

    private synchronized void syncAlliances() {
        Set<Integer> aaIds = db.getAllianceIds();
        allianceIds.clear();
        if (!aaIds.isEmpty()) {
            allianceIds.addAll(aaIds);
        } else {
            allianceIds.addAll(db.getAllies(false));
        }
        for (GuildDB otherDB : Locutus.imp().getGuildDatabases().values()) {
            aaIds = otherDB.getAllianceIds();
            if (!aaIds.isEmpty()) {
                Guild warServer = otherDB.getOrNull(GuildKey.WAR_SERVER);
                if (warServer != null && warServer.getIdLong() == this.db.getIdLong()) {
                    allianceIds.addAll(aaIds);
                }
            }
        }

        allies.clear();
        allies.addAll(allianceIds);

        System.out.println("Allies " + StringMan.getString(allianceIds));
    }

    public Set<Category> getCategories() {
        Set<Category> result = new LinkedHashSet<>();
        for (Category category : guild.getCategories()) {
            if (category.getName().toLowerCase().startsWith(catPrefix.toLowerCase())) {
                result.add(category);
            }
        }
        return result;
    }

    public void sync() {
        sync(null, null, null, null, null, null, null, false);
    }

    public void sync(boolean force) {
        sync(null, null, null, null, null, null, null, force);
    }


    public enum WarCatReason {
        WARS_NOT_AGAINST_ACTIVE("Nation has no wars against active nations", false),
        NO_WARS("Nation has no wars", false),
        NO_WARS_CHANNEL("Channel for nation has no wars and not registered to a room", false),
        NATION_NOT_FOUND("Nation not found in database (did they delete?)", false),

        VACATION_MODE("Nation is in vacation mode", false),
        INACTIVE_APPLICANT("Nation is applicant not active in past 1d, with 0 offensive wars", false),
        INACTIVE("Nation is inactive for 2 days", false),
        INACTIVE_NONE("Nation is not in an alliance and not active in past 1d, with 0 offensive wars", false),
        FILTER("Nation does not match the `WAR_ROOM_FILTER` set", false),
        ACTIVE("Nation is active with wars", true),
        PLANNING_NO_ACTIVE_WARS("War room is marked as planning and has no active wars", true),
        ROOM_ACTIVE_NO_CHANNEL("War room is registered and active but has no channel", true),
        ROOM_ACTIVE_INVALID_CHANNEL("War room is registered and active but has an invalid channel", true),
        ROOM_ACTIVE_EXISTS("War room is registered and active and has a valid channel", true),
        NOT_CREATED("War room is not created yet, but active wars were found", true),
        ROOM_ACTIVE_NO_FREE_CATEGORY("War room is active, but not free category is found", true),
        ;

        private final boolean isActive;
        private final String reason;

        WarCatReason(String reason, boolean isActive) {
            this.reason = reason;
            this.isActive = isActive;

        }

        public String getReason() {
            return reason;
        }

        public boolean isActive() {
            return isActive;
        }
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
        Set<DBWar> wars = Locutus.imp().getWarDb().getActiveWars(allianceIds, WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);
        Map<Integer, List<DBWar>> byTarget = new RankBuilder<>(wars).group(war -> allianceIds.contains(war.getAttacker_aa()) ? war.getDefender_id() : war.getAttacker_id()).get();

        long createDiff = 0;
        long updateDiff = 0;

        for (Map.Entry<Integer, List<DBWar>> entry : byTarget.entrySet()) {
            List<DBWar> currentWars = entry.getValue();
            int targetId = entry.getKey();
            DBNation targetNation = Locutus.imp().getNationDB().getNation(targetId);

            WarCatReason enemyReason = getActiveReason(targetNation);
            if (targetNation != null) {
                for (DBWar war : currentWars) {
                    DBNation ally = war.getNation(!war.isAttacker(targetNation));
                    WarCatReason allyReason = getActiveReason(ally);
                    if (!enemyReason.isActive()) {
                        if (warsLog != null) warsLog.put(war, enemyReason);
                    } else if (!allyReason.isActive()) {
                        if (warsLog != null) warsLog.put(war, allyReason);
                    } else {
                        if (warsLog != null) warsLog.put(war, WarCatReason.ACTIVE);
                    }
                }
            }

            WarCatReason reason = getActiveReason(wars, targetNation);

            if (reason.isActive()) {
                long createStart = System.currentTimeMillis();
                WarRoom room;
                try {
                    room = get(targetNation, create, create, create);
                } catch (ErrorResponseException e) {
                    if (activeRoomLog != null) activeRoomLog.put(targetNation, WarCatReason.ROOM_ACTIVE_NO_FREE_CATEGORY);
                    continue;
//                    room = get(targetNation, false, false, false);
                }
                createDiff += (System.currentTimeMillis() - createStart);

                long updateStart = System.currentTimeMillis();
                if (room != null) {
                    if (room.channel != null) {
                        if (guild.getGuildChannelById(room.channel.getIdLong()) != null) {
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
                updateDiff += (System.currentTimeMillis() - updateStart);

            } else {
                WarRoom room = targetNation == null ? warRoomMap.get(targetId) : get(targetNation, false, false);
                if (room != null && room.channel != null) {
                    if (!room.isPlanning()) {
                        if (targetNation != null) {
                            if (inactiveRoomLog != null) inactiveRoomLog.put(targetNation, reason);
                        }
                        if (toDelete != null) toDelete.put(targetId, reason);
                        if (create) {
                            room.delete("Target is not active");
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

        Set<Integer> duplicateChannels = new HashSet<>();

        for (Category category : guild.getCategories()) {
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
                        if (duplicates != null) duplicates.computeIfAbsent(targetId, f -> new LinkedHashSet<>()).add(channel);
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
                    DBNation target = Locutus.imp().getNationDB().getNation(targetId);
                    WarRoom room = null;
                    if (target != null) {
                        room = get(target, create, false);
                        if (room != null && room.isPlanning()) continue;
                    }
                    if (room != null) {
                        if (toDelete != null) toDelete.put(targetId, WarCatReason.NO_WARS);
                        if (create) {
                            room.delete("No active wars");
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

    public synchronized WarRoom getOrCreate(DBNation target) {
        return get(target, true, false);
    }

    public synchronized void forEachRoom(DBNation targetOrParticipant, Consumer<WarRoom> onEach) {
        for (Map.Entry<Integer, WarRoom> entry : warRoomMap.entrySet()) {
            WarRoom room = entry.getValue();
            if (room.target.equals(targetOrParticipant) || room.participants.contains(targetOrParticipant)) {
                onEach.accept(room);
            }
        }
    }

    public synchronized WarRoom get(DBNation target, boolean createRoom, boolean createChannel) {
        return get(target, createRoom, createChannel, false);
    }

    public synchronized WarRoom get(DBNation target, boolean createRoom, boolean createChannel, boolean force) {
        return get(target, createRoom, createChannel, force, false);
    }

    public synchronized WarRoom get(DBNation target, boolean createRoom, boolean createChannel, boolean force, boolean planning) {
        WarRoom existing = warRoomMap.get(target.getNation_id());
        if (existing == null && createRoom) {
            // create it

//            categorySetMap.computeIfAbsent(useCat.getIdLong(), f -> new HashSet<>()).add(target.getNation_id());

            synchronized (target) {
                existing = warRoomMap.get(target.getNation_id());
                if (existing == null) {
                    existing = new WarRoom(target);
                    existing.getChannel(createChannel, planning);
                    warRoomMap.put(target.getNation_id(), existing);
                }
            }
        } else if (force && existing != null) {
            existing.channel = null;
            existing.getChannel(true, planning);
        }
        return existing;
    }
}
