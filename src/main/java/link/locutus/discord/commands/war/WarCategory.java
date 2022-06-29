package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.ICategorizableChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.exceptions.MissingAccessException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private final Set<Integer> enemies = new HashSet<>();
    private final GuildDB db    ;

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
            if (allianceIds.contains(to.attacker_aa)) {
                targetId = to.defender_id;
            } else if (allianceIds.contains(to.defender_aa)) {
                targetId = to.attacker_id;
            } else {
                return;
            }
            int participantId = to.attacker_id == targetId ? to.defender_id : to.attacker_id;
            DBNation target = Locutus.imp().getNationDB().getNation(targetId);
            DBNation participant = Locutus.imp().getNationDB().getNation(participantId);

            boolean create = false;
            if (isActive(target) && isActive(participant) && participant.getPosition() > 1 && (to.defender_id == participantId || target.getPosition() > 1)) {
                create = true;
            }

            WarRoom room = target == null ? warRoomMap.get(targetId) : get(target, create, create && from == null);
            if (room != null) {
                if (to.attacker_id == target.getNation_id()) {
                    CounterStat counterStat = Locutus.imp().getWarDb().getCounterStat(to);
                    if (counterStat != null) {
                        switch (counterStat.type) {
                            case UNCONTESTED:
                                break;
                            case GETS_COUNTERED:
                                break;
                            case IS_COUNTER:
                                room.setCounter(true);
                                break;
                            case ESCALATION:
                                break;
                        }
                    }
                }
                if ((from == null || to.status != from.status)) {
                    room.updateParticipants(from, to, from == null);
                }
            }
        } catch (MissingAccessException e) {
            db.setInfo(GuildDB.Key.ENABLE_WAR_ROOMS, "false");
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

    private static boolean isActive(Collection<DBWar> wars, DBNation nation) {
        if (!isActive(nation)) return false;
        for (DBWar war : wars) {
            int attackerId = war.attacker_id == nation.getNation_id() ? war.defender_id : war.attacker_id;
            DBNation attacker = Locutus.imp().getNationDB().getNation(attackerId);
            if (attacker != null) {
                if (isActive(attacker)) return true;
            }
        }
        return false;
    }

    public static boolean isActive(DBNation nation) {
        if (nation != null && nation.getVm_turns() == 0 && nation.getActive_m() < 2880 && (nation.getPosition() <= Rank.APPLICANT.id || nation.getActive_m() < 1440 || nation.getOff() > 0)) return true;
        return false;
    }

    public void update(DBAttack attack) {
        RateLimitUtil.queueWhenFree(new Runnable() {
            @Override
            public void run() {
                int attackerId = attack.attacker_nation_id;
                int defenderId = attack.defender_nation_id;
                WarRoom room = warRoomMap.get(attackerId);
                if (room == null) room = warRoomMap.get(defenderId);

                if (room != null && room.channel != null) {
                    boolean value = room.target.getNation_id() == attack.attacker_nation_id;
                    boolean change = attack.success == 3 || (attack.success > 0 && !value);

                    if (change) {
                        switch (attack.attack_type) {
                            case GROUND:
                                room.setGC(value);
                                break;
                            case AIRSTRIKE1:
                            case AIRSTRIKE2:
                            case AIRSTRIKE3:
                            case AIRSTRIKE4:
                            case AIRSTRIKE5:
                            case AIRSTRIKE6:
                                room.setAC(value);
                                break;
                            case NAVAL:
                                room.setBlockade(value);
                                break;
                        }
                    }

                    if (attack.attacker_nation_id == room.target.getNation_id() || true) {

                        DBNation attacker = Locutus.imp().getNationDB().getNation(attackerId);
                        DBNation defender = Locutus.imp().getNationDB().getNation(defenderId);

                        String name1 = attacker.getNationUrlMarkup(true) + (attacker.getAlliance_id() != 0 ? (" of " + attacker.getAllianceUrlMarkup(true)) : "");
                        String name2 = defender.getNationUrlMarkup(true) + (defender.getAlliance_id() != 0 ? (" of " + defender.getAllianceUrlMarkup(true)) : "");

                        String message;
                        boolean showLoot = false;
                        boolean showInfra = false;
                        boolean showCasualties = false;
                        boolean showSuccess = false;

                        switch (attack.attack_type) {
                            case AIRSTRIKE1:
                                message = name1 + " issued " + attack.attack_type + " INFRA against " + name2;
                                showCasualties = true;
                                showInfra = true;
                                showSuccess = true;
                                break;
                            case AIRSTRIKE2:
                            case AIRSTRIKE3:
                            case AIRSTRIKE4:
                            case AIRSTRIKE5:
                                String typeStr = attack.attack_type + " " + attack.attack_type.getUnits()[1].getName();
                                message = name1 + " issued " + typeStr + " against " + name2;
                                showCasualties = true;
                                showInfra = true;
                                showSuccess = true;
                                break;
                            case AIRSTRIKE6:
                                message = name1 + " issued " + attack.attack_type + " AIRCRAFT against " + name2;
                                showCasualties = true;
                                showInfra = true;
                                showSuccess = true;
                                break;
                            case GROUND:
                                showLoot = true;
                            case NAVAL:
                                message = name1 + " issued a " + attack.attack_type + " attack against " + name2;
                                showInfra = true;
                                showSuccess = true;
                                break;
                            case MISSILE:
                            case NUKE:
                                message = name1 + " launched a " + attack.attack_type + " against " + name2;
                                showInfra = true;
                                showSuccess = true;
                                break;
                            case FORTIFY:
                                message = name1 + " fortified against " + name2;
                                break;
                            case VICTORY:
                                room.setPlanning(false);
                                message = name1 + "looted " + PnwUtil.resourcesToString(attack.getLoot()) + " from " + name2;
                                break;
                            case A_LOOT:
                                message = name1 + "looted " + PnwUtil.resourcesToString(attack.getLoot()) + " from " + PnwUtil.getName(attack.getLooted(), true);
                                break;
                            case PEACE:
                                message = name1 + " agreed to peace with " + name2;
                                break;
                            default:
                                message = null;
                                break;
                        }

                        if (message != null) {
                            if (showSuccess) {
                                String successType;
                                switch (attack.success) {
                                    default:
                                    case 0:
                                        successType = "UTTER_FAILURE";
                                        break;
                                    case 1:
                                        successType = "PYRRHIC_VICTORY";
                                        break;
                                    case 2:
                                        successType = "MODERATE_SUCCESS";
                                        break;
                                    case 3:
                                        successType = "IMMENSE_TRIUMPH";
                                        break;
                                }
                                message += ". It was a " + successType;
                            }
                            if (showLoot && attack.money_looted != 0) {
                                message += " and looted $" + MathMan.format(attack.money_looted);
                            }
                            if (showInfra && attack.infra_destroyed != 0) {
                                double worth = PnwUtil.calculateInfra(attack.city_infra_before - attack.infra_destroyed, attack.city_infra_before);
                                message += ". " + attack.infra_destroyed + " infra worth $" + MathMan.format(worth) + " was destroyed";
                            }
                            if (showCasualties) {
                                Map<MilitaryUnit, Integer> attLosses = attack.getUnitLosses(true);
                                if (!attLosses.isEmpty()) message += "\nAttacker unit losses: " + StringMan.getString(attLosses);
                                Map<MilitaryUnit, Integer> defLosses = attack.getUnitLosses(false);
                                if (!defLosses.isEmpty()) message += "\nDefender unit losses: " + StringMan.getString(defLosses);
                            }

                            if (RateLimitUtil.getCurrentUsed() > 25) {
                                DiscordUtil.createEmbedCommand(room.getChannel(), attack.attack_type.toString(), message);
                            } else {
                                String emoji = "\u2139";
                                String cmd = "_" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "WarInfo " + attack.war_id;
                                message += "\n\nPress " + emoji + " to view the war card";
                                DiscordUtil.createEmbedCommand(room.getChannel(), attack.attack_type.toString(), message, emoji, cmd);
                            }
                        }

                    }
                }
            }
        });
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
                    System.out.println("Delete " + room.target.getNation_id() + " (no participants)");
                    room.delete();
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
                            int maxBuy = mB.perDay() * mB.cap(f -> false) * current.getCities();

                            int unitDiff = current.getUnits(mB.unit()) - previous.getUnits(mB.unit());
                            if (unitDiff > 0) {
                                rebuys.put(mB.unit(), new AbstractMap.SimpleEntry<>(unitDiff, unitDiff > maxBuy));
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
        RateLimitUtil.queueWhenFree(channel.putPermissionOverride(guild.getRolesByName("@everyone", false).get(0)).deny(Permission.VIEW_CHANNEL));

        room.addInitialParticipants(planning);
    }

    public WarCategory.WarRoom createChannel(User author, Consumer<String> errorOutput, boolean ping, boolean addMember, boolean addMessage, DBNation target, Collection<DBNation> attackers) {
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
                errorOutput.accept("No user for: " + attacker.getNation() + " | " + attacker.getAllianceName() + ". Have they used `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "verify` ?");
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
                    channel.putPermissionOverride(member).grant(Permission.VIEW_CHANNEL).complete();
                    if (ping) {
                        String msg = (author != null ? author.getName() : "null") + " added " + user.getAsMention();

                        if (addMessage) {
                            String warType = target.getAvg_infra() > 2000 && target.getAvg_infra() > attacker.getAvg_infra() ? "attrition" : "raid";
                            msg += ". You have been ordered to declare a war of type `" + warType + "` with reason `counter`.";

                            Role econRole = Roles.ECON.toRole(guild);
                            String econRoleName = econRole != null ? "`@" + econRole.getName() + "`" : "ECON";
                            GuildMessageChannel rssChannel = db.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                            GuildMessageChannel grantChannel = db.getOrNull(GuildDB.Key.GRANT_REQUEST_CHANNEL);

                            if (rssChannel != null) {
                                if (Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW))) {
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
                            body.append("\n - target: " + declareUrl);
                            body.append("\n\nCheck the war room for further details: " + channelUrl);
                            String mailBody = MarkupUtil.transformURLIntoLinks(MarkupUtil.markdownToHTML(body.toString()));

                            try {
                                System.out.println("Send mail " + title);
                                attacker.sendMail(Locutus.imp().getRootAuth(), title, mailBody);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        RateLimitUtil.queue(channel.sendMessage(msg + "\n - <" + declareUrl + (">")));
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
        List<Category> noRange = new LinkedList<>();
        for (Category category : getCategories()) {
            CityRanges range = getRangeFromCategory(category);
            if (range != null) categoryRanges.computeIfAbsent(range, f -> new LinkedList<>()).add(category);
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
                            room.channel.getManager().setParent(newCat);
                            moved++;
                            continue outer;
                        }
                    }
                }

                if (range != null && !noRange.isEmpty()) {
                    for (Category newCat : noRange) {
                        if (newCat.getChannels().size() > 49) continue;
                        room.channel.getManager().setParent(newCat);
                        moved++;
                        continue outer;
                    }
                }

            }
        }
        return moved;
    }

    private CityRanges getRangeFromCategory(Category category) {
        String[] split = category.getName().split("-", 2);
        if (split.length == 2) {
            String filterStr = split[1];
            if (filterStr.charAt(0) == 'c') {
                if (!filterStr.contains("-")) filterStr += "+";
                return CityRanges.parse(filterStr);
            }
        }
        return null;
    }

    public class WarRoom {
        public final DBNation target;
        private final Set<DBNation> participants;
        public TextChannel channel;

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
            try {
                 return RateLimitUtil.complete(channel.retrieveMessageById(topic));
            } catch (Exception e) {
                return null;
            }
        }

        public String url() {
            if (channel == null) return null;
            return "https://discord.com/channels/" + guild.getIdLong() + "/" + channel.getIdLong();
        }

        public Message updatePin(boolean update) {
            if (channel == null) return null;

            Message msg = null;
            String topic = channel.getTopic();
            if (topic == null || topic.isEmpty() || !MathMan.isInteger(topic) || (msg = getMessage(topic)) == null) {
                update = true;
                try {
                    CompletableFuture<Void> t1 = channel.getManager().setPosition(0).submit();
                    msg = RateLimitUtil.complete(channel.sendMessage("Creating embed..."));
                    CompletableFuture<Void> t2 = channel.getManager().setTopic(msg.getId()).submit();
                    CompletableFuture<Void> t3 = channel.pinMessageById(msg.getIdLong()).submit();
                    t1.get();
                    t2.get();
                    t3.get();
                } catch (InterruptedException | ExecutionException | InsufficientPermissionException e) {
                    e.printStackTrace();
                    return msg;
                }
            }

            if (update) {
                StringBuilder body = new StringBuilder();

                body.append("**Enemy:** ").append(target.getNationUrlMarkup(true))
                        .append(" | ").append(target.getAllianceUrlMarkup(true));
                body.append(target.toMarkdown(true, false, false, true, false));
                body.append("\n");

                List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(target.getNation_id(), WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);
                for (DBWar war : wars) {
                    boolean defensive = war.attacker_id == target.getNation_id();
                    DBNation participant = Locutus.imp().getNationDB().getNation(war.attacker_id == target.getNation_id() ? war.defender_id : war.attacker_id);

                    if (participant != null && (participants.contains(participant) || participant.getActive_m() < 2880)) {
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

                Map<String, String> reactions = new HashMap<>();
                Message finalCard = msg;
                DiscordUtil.updateEmbed(builder, reactions, embedBuilder -> {
                     RateLimitUtil.queueWhenFree(channel.editMessageEmbedsById(finalCard.getIdLong(), embedBuilder.build()));
                     return null;
                });
            }
            return msg;
        }

        public boolean setCounter(boolean value) {
            return setSymbol("\uD83D\uDD19", value);
        }

        public boolean setGC(boolean value) {
            return setSymbol("\uD83D\uDC82", value);
        }

        public boolean setAC(boolean value) {
            return setSymbol("\u2708", value);
        }

        public boolean setBlockade(boolean value) {
            return setSymbol("\u26F5", value);
        }

        public boolean setPeace(boolean value) {
            return setSymbol("\u2661", value);
        }

        public boolean setPlanning(boolean value) {
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
                        System.out.println("Name: " + name + " | " + (name.contains(symbol)));
                        return false;
                    }
                    RateLimitUtil.complete(channel.getManager().setName(name));
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
//                                PermissionOverride result = link.locutus.discord.util.RateLimitUtil.complete(channel.putPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
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
                if (guild.getTextChannelById(channel.getIdLong()) == null) channel = null;
                else return channel;
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
                                RateLimitUtil.complete(useCat.putPermissionOverride(guild.getRolesByName("@everyone", false).get(0))
                                        .deny(Permission.VIEW_CHANNEL));

                                Role milcomRole = Roles.MILCOM.toRole(guild);
                                if (milcomRole != null) {
                                    RateLimitUtil.complete(useCat.putPermissionOverride(milcomRole)
                                            .setAllow(Permission.VIEW_CHANNEL));
                                }
                                Role advisor = Roles.MILCOM_ADVISOR.toRole(guild);
                                if (advisor != null) {
                                    RateLimitUtil.complete(useCat.putPermissionOverride(advisor)
                                            .setAllow(Permission.VIEW_CHANNEL));
                                }
                                break;
                            }
                        }
                    }
                    String name = "-" + target.getNation() + "-" + target.getNation_id();
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
        }

        public void delete() {
            if (channel != null) {
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
//                if (nation == null || nation.getPosition() <= 1 || nation.getVm_turns() != 0 || nation.getActive_m() > 2880 || !allies.contains(nation.getAlliance_id())) {
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
            int assignedId = to.attacker_id == target.getNation_id() ? to.defender_id : to.attacker_id;
            DBNation nation = Locutus.imp().getNationDB().getNation(assignedId);
            if (nation == null) return;

            User user = nation.getUser();
            Member member = user == null ? null : guild.getMember(user);
            if (from == null) {
                participants.add(nation);

                if (channel != null && member != null && channel.getPermissionOverride(member) == null) {
                    RateLimitUtil.queue(channel.putPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
                    if (ping && channel != null) {
                        String msg = member.getAsMention() + " joined the fray";
                        RateLimitUtil.queue(channel.sendMessage(msg));
                    }
                }
            } else if (channel != null && (to.status == WarStatus.EXPIRED || to.status == WarStatus.ATTACKER_VICTORY || to.status == WarStatus.DEFENDER_VICTORY)) {
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
                    new Exception().printStackTrace();
                    System.out.println("Delete " + target.getNation_id() + " (no wars)");
                    delete();
                }
                return;
            }

            Set<DBNation> added = new HashSet<>();
            Set<Member> addedMembers = new HashSet<>();

            for (DBWar war : wars) {
                DBNation other = war.getNation(!war.isAttacker(target));
                if (other == null) continue;;

                added.add(other);
                participants.add(other);

                User user = other.getUser();
                Member member = user == null ? null : guild.getMember(user);

                if (member != null) {
                    addedMembers.add(member);
                }
                if (channel != null && member != null && channel.getPermissionOverride(member) == null) {
                    RateLimitUtil.queue(channel.putPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
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
    }

    private synchronized void syncAlliances() {
        Integer allianceId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        allianceIds.clear();
        if (allianceId != null) {
            allianceIds.add(allianceId);
        } else {
            allianceIds.addAll(db.getAllies(true));
        }
        allies.clear();
        allies.addAll(allianceIds);

        Set<Integer> toAddChained = new HashSet<>();
        Set<Integer> toAddSingle = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            toAddChained.clear();
            for (Integer id : allies) {
                Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(id);
                for (Map.Entry<Integer, Treaty> e : treaties.entrySet()) {
                    switch (e.getValue().type) {
                        case MDP:
                        case MDOAP:
                        case PROTECTORATE:
                            toAddChained.add(e.getKey());
                            break;
                        case ODP:
                        case ODOAP:
                            toAddSingle.add(e.getKey());
                            break;
                    }
                }
            }
            allies.addAll(toAddChained);
        }
        allies.addAll(toAddSingle);

        enemies.clear();
        enemies.addAll(db.getCoalition("enemies"));
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
        sync(false);
    }

    public void sync(boolean force) {
        if (warRoomMap.isEmpty()) return;

        long start = System.currentTimeMillis();
        List<DBWar> wars = Locutus.imp().getWarDb().getActiveWars(allianceIds, WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);
        Map<Integer, List<DBWar>> byTarget = new RankBuilder<>(wars).group(war -> allianceIds.contains(war.attacker_aa) ? war.defender_id : war.attacker_id).get();

        long createDiff = 0;
        long updateDiff = 0;

        for (Map.Entry<Integer, List<DBWar>> entry : byTarget.entrySet()) {
            wars = entry.getValue();
            int targetId = entry.getKey();
            DBNation targetNation = Locutus.imp().getNationDB().getNation(targetId);
            if (isActive(wars, targetNation)) {

                long createStart = System.currentTimeMillis();
                WarRoom room = get(targetNation, true, force);
                createDiff += (System.currentTimeMillis() - createStart);

                long updateStart = System.currentTimeMillis();
                room.addInitialParticipants(wars);
                updateDiff += (System.currentTimeMillis() - updateStart);
            } else {
                WarRoom room = targetNation == null ? warRoomMap.get(targetId) : get(targetNation, false, false);
                if (room != null && room.channel != null) {

                    if (!room.isPlanning()) {
                        new Exception().printStackTrace();
                        System.out.println("Delete " + room.target.getNation_id() + " (target is not active)");
                        room.delete();
                    }
                }
            }
        }

        start = System.currentTimeMillis();

        for (Category category : guild.getCategories()) {
            String catName = category.getName().toLowerCase();
            if (catName.startsWith(catPrefix)) {
                for (GuildMessageChannel channel : category.getTextChannels()) {
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
                    if (byTarget.containsKey(targetId)) continue;

                    // delete because no active wars
                    DBNation target = Locutus.imp().getNationDB().getNation(targetId);
                    WarRoom room = null;
                    if (target != null) {
                        room = get(target, true, false);
                        if (room != null && room.isPlanning()) continue;
                    }
                    if (room != null) {
                        room.delete();
                    } else {
                        RateLimitUtil.queueWhenFree(channel.delete());
                    }
                }
            }
        }

        System.out.println("Clean up categories: " + (-start + (start = System.currentTimeMillis())));
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
