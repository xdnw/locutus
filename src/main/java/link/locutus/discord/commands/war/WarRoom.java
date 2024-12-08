package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.war.WarCard;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.google.gson.internal.$Gson$Preconditions.checkNotNull;

public class WarRoom {
    protected final WarCategory warCategory;
    public final DBNation target;
    protected final Set<DBNation> participants;
    public TextChannel channel;
    public boolean planning;
    public boolean enemyGc;
    public boolean enemyAc;
    public boolean enemyBlockade;

    public WarRoom(WarCategory warCategory, DBNation target) {
        this.warCategory = warCategory;
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
        return "https://discord.com/channels/" + warCategory.guild.getIdLong() + "/" + channel.getIdLong();
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

            body.append("**Enemy:** ").append(target.getNationUrlMarkup())
                    .append(" | ").append(target.getAllianceUrlMarkup());
            body.append(target.toMarkdown(true, true, false, false, true, false));
            body.append("\n");

            Set<DBWar> wars = target.getActiveWars();
            for (DBWar war : wars) {
                boolean defensive = war.getAttacker_id() == target.getNation_id();
                DBNation participant = Locutus.imp().getNationDB().getNationById(war.getAttacker_id() == target.getNation_id() ? war.getDefender_id() : war.getAttacker_id());

                if (participant != null && (participants.contains(participant) || participant.active_m() < 2880)) {
                    String typeStr = defensive ? "\uD83D\uDEE1 " : "\uD83D\uDD2A ";
                    body.append(typeStr).append("`" + participant.getNation() + "`")
                            .append(" | ").append(participant.getAllianceName());

                    WarCard card = new WarCard(war, false);
                    if (card.blockaded == participant.getNation_id()) body.append("\u26F5");
                    if (card.airSuperiority != 0 && card.airSuperiority == participant.getNation_id())
                        body.append("\u2708");
                    if (card.groundControl != 0 && card.groundControl == participant.getNation_id())
                        body.append("\uD83D\uDC82");

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

    /// /                        link.locutus.discord.util.RateLimitUtil.queue(memberOverride.delete());
    /// /                        modified = true;
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
                if (warCategory.guild.getTextChannelById(channel.getIdLong()) == null) {
                    channel = null;
                }
            }
            if (channel != null) return channel;
        }

        synchronized (target.getName()) {
            for (Category category : warCategory.guild.getCategories()) {
                String catName = category.getName().toLowerCase();
                if (catName.startsWith(warCategory.catPrefix)) {
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
                for (Category category : warCategory.guild.getCategories()) {
                    String catName = category.getName().toLowerCase();
                    if (!catName.startsWith(warCategory.catPrefix)) continue;
                    List<GuildChannel> channels = category.getChannels();
                    if (channels.size() >= 49) continue;

                    CityRanges range = warCategory.getRangeFromCategory(category);
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
                        String name = warCategory.catPrefix + "-" + i;
                        List<Category> existingCat = warCategory.guild.getCategoriesByName(name, true);
                        if (existingCat.isEmpty()) {
                            useCat = RateLimitUtil.complete(warCategory.guild.createCategory(name));
                            PermissionOverrideAction upsert = useCat.upsertPermissionOverride(warCategory.guild.getMemberById(Settings.INSTANCE.APPLICATION_ID));
                            for (Permission perm : warCategory.getCategoryPermissions()) {
                                upsert = upsert.setAllowed(perm);
                            }
                            RateLimitUtil.queue(upsert);
                            RateLimitUtil.queue(useCat.upsertPermissionOverride(warCategory.guild.getRolesByName("@everyone", false).get(0)).deny(Permission.VIEW_CHANNEL));

                            List<CompletableFuture<PermissionOverride>> futures = new ArrayList<>();

                            for (Role role : Roles.MILCOM.toRoles(warCategory.db)) {
                                futures.add(RateLimitUtil.queue(useCat.upsertPermissionOverride(role)
                                        .setAllowed(Permission.VIEW_CHANNEL)));
                            }
                            for (Role role : Roles.MILCOM_NO_PINGS.toRoles(warCategory.db)) {
                                futures.add(RateLimitUtil.queue(useCat.upsertPermissionOverride(role)
                                        .setAllowed(Permission.VIEW_CHANNEL)));
                            }
                            if (!futures.isEmpty()) {
                                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                            }
                            break;
                        }
                    }
                }
                String name = target.getNation() + "-" + target.getNation_id();
                if (planning) name = "\uD83D\uDCC5" + name;
                channel = RateLimitUtil.complete(useCat.createTextChannel(name));
                warCategory.processChannelCreation(this, channel, planning);
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
            warCategory.warRoomMap.remove(target.getNation_id());
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

    /// /            updatePin(modifiedPerms);
//
//            return true;
//        }
    public void updateParticipants(DBWar from, DBWar to) {
        updateParticipants(from, to, false);
    }

    public void updateParticipants(DBWar from, DBWar to, boolean ping) {
        int assignedId = to.getAttacker_id() == target.getNation_id() ? to.getDefender_id() : to.getAttacker_id();
        DBNation nation = Locutus.imp().getNationDB().getNationById(assignedId);
        if (nation == null) return;

        User user = nation.getUser();
        Member member = user == null ? null : warCategory.guild.getMember(user);
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

        Member botMember = warCategory.guild.getMemberById(Settings.INSTANCE.APPLICATION_ID);
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
            Member member = user == null ? null : warCategory.guild.getMember(user);

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

    protected boolean hasOtherWar(int warId) {
        boolean hasWar = false;
        for (DBWar war : target.getWars()) {
            if (war.getWarId() == warId) continue;
            DBNation other = war.getNation(!war.isAttacker(target));
            if (other != null && warCategory.isActive(other) && warCategory.allianceIds.contains(other.getAlliance_id())) {
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
