package link.locutus.discord.util.offshore.test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class IACategory {
    private final GuildDB db;
    private final AllianceList alliance;
    private final Map<DBNation, IAChannel> channelMap = new HashMap<>();
    private final Guild guild;
    private IMessageIO output;

    private List<Category> activeCategories = new ArrayList<>();
    private List<Category> inactiveCategories = new ArrayList<>();
    private List<Category> passedCategories = new ArrayList<>();

    public Category getFreeCategory(Collection<Category> categories) {
        return DiscordUtil.findFreeCategory(categories);
    }

    public boolean isInCategory(MessageChannel channel) {
        if (!(channel instanceof ICategorizableChannel)) return false;
        Category parent = ((ICategorizableChannel) channel).getParentCategory();
        for (Category category : getCategories()) {
            if (category.equals(parent)) return true;
        }
        return false;
    }


    public IACategory(GuildDB db) {
        this.db = db;
        MessageChannel outputChannel = db.getOrNull(GuildKey.INTERVIEW_INFO_SPAM);
        if (outputChannel != null) {
            this.output = new DiscordChannelIO(outputChannel);
        } else {
            this.output = null;
        }
        this.guild = db.getGuild();
        this.alliance = db.getAllianceList();
        if (this.alliance == null || this.alliance.isEmpty()) throw new IllegalArgumentException("No ALLIANCE_ID set. See: " + GuildKey.ALLIANCE_ID.getCommandMention());
        fetchChannels();
    }

    public void setOutput(GuildMessageChannel outputChannel) {
        this.output = new DiscordChannelIO(outputChannel);;
    }

    public Map<DBNation, IAChannel> getChannelMap() {
        return Collections.unmodifiableMap(channelMap);
    }

    public IAChannel get(GuildMessageChannel channel) {
        return get(channel, false);
    }

    public IAChannel get(GuildMessageChannel channel, boolean create) {
        if (!(channel instanceof TextChannel)) return null;
        TextChannel tc = (TextChannel) channel;
        Category parent = tc.getParentCategory();
        if (parent == null) return null;
        Map.Entry<DBNation, User> nationUser = getNationUser(channel);
        IAChannel value = channelMap.get(nationUser.getKey());
        if (value == null || !create) {
            return value;
        }
        if (nationUser.getKey() != null) {
            DBNation nation = nationUser.getKey();
            if (getCategories().contains(parent)) {
                IAChannel iaChan = new IAChannel(nation, db, parent, tc);
                channelMap.put(nation, iaChan);
                return iaChan;
            }
        }
        return null;
    }

    public IAChannel get(DBNation nation) {
        return channelMap.get(nation);
    }

    public IAChannel find(DBNation nation) {
        IAChannel result = get(nation);
        if (result != null) return result;
        User user = nation.getUser();
        if (user == null) return null;

        Member member = guild.getMember(user);
        if (member == null) return null;

        for (TextChannel channel : getAllChannels()) {
            Member overrideMem = getOverride(channel.getMemberPermissionOverrides());
            if (member.equals(overrideMem)) {
                IAChannel iaChannel = new IAChannel(nation, db, channel.getParentCategory(), channel);
                iaChannel.updatePerms();
                channelMap.put(nation, iaChannel);
                return iaChannel;
            }
        }
        return null;
    }

    public CompletableFuture<Void> updateChannelMessages() {
        Map<Long, InterviewMessage> messages = db.getLatestInterviewMessages();

        List<Future> tasks = new ArrayList<>();

        for (GuildMessageChannel channel : getAllChannels()) {
            if (channel.getLatestMessageIdLong() == 0) continue;
            long lastMessageId = channel.getLatestMessageIdLong();
            long created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(lastMessageId).toInstant().toEpochMilli();

            InterviewMessage currentMessage = messages.get(channel.getIdLong());

            long channelId = channel.getIdLong();
            if (currentMessage == null || created > Math.max(currentMessage.date_created, currentMessage.date_updated)) {
                CompletableFuture<List<Message>> future = RateLimitUtil.queue(channel.getHistory().retrievePast(15));
                CompletableFuture<List<Message>> whenComplete = future.whenComplete(new BiConsumer<List<Message>, Throwable>() {
                    @Override
                    public void accept(List<Message> messages, Throwable throwable) {
                        if (messages == null) {
                            throwable.printStackTrace();
                            AlertUtil.error("Cannot fetch message", StringMan.stacktraceToString(throwable) + "<@" + Locutus.loader().getAdminUserId() + ">");
                            db.updateInterviewMessageDate(channelId);
                            return;
                        }
                        for (Message message : messages) {
                            db.addInterviewMessage(message, currentMessage == null);
                        }
                    }
                });
                tasks.add(whenComplete);
            }
        }
        return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
    }

    public GuildMessageChannel getOrCreate(User user) {
        return getOrCreate(user, false);
    }
    public GuildMessageChannel getOrCreate(User user, boolean throwError) {
        Member member = guild.getMember(user);
        if (member == null) {
            try {
                member = RateLimitUtil.complete(guild.retrieveMember(user));
                if (member == null) {
                    if (throwError) throw new IllegalArgumentException("Member is null");
                    return null;
                }
            } catch (ErrorResponseException e) {
                if (throwError) throw new IllegalArgumentException("Member is null: " + e.getMessage());
                return null;
            }
        }

        DBNation nation = DiscordUtil.getNation(user);
        if (nation != null) {
            IAChannel iaChannel = channelMap.get(nation);
            if (iaChannel != null && iaChannel.getChannel() != null) {
                TextChannel channel = guild.getTextChannelById(iaChannel.getChannel().getIdLong());
                if (channel != null) return iaChannel.getChannel();
            }

            for (TextChannel channel : getAllChannels()) {
                String[] split = channel.getName().split("-");
                if (split.length >= 2 && MathMan.isInteger(split[split.length - 1])) {
                    int nationId = Integer.parseInt(split[split.length - 1]);
                    if (nationId == nation.getNation_id()) {
                        iaChannel = new IAChannel(nation, db, channel.getParentCategory(), channel);
                        iaChannel.updatePerms();
                        channelMap.put(nation, iaChannel);
                        return channel;
                    }
                }
            }
        }
        for (TextChannel channel : getAllChannels()) {
            Member overrideMem = getOverride(channel.getMemberPermissionOverrides());
            if (member.equals(overrideMem)) {
                return channel;
            }
        }
        Category category = getFreeCategory(activeCategories);

        if (category == null) {
            if (throwError) throw new IllegalArgumentException("No interview category found");
            return null;
        }

        String channelName;
        if (nation != null) {
            channelName = nation.getNation() + "-" + nation.getNation_id();
        } else {
            channelName = user.getName();
        }

        TextChannel channel;
        try {
            channel = RateLimitUtil.complete(category.createTextChannel(channelName));
        } catch (InsufficientPermissionException e) {
            throw new InsufficientPermissionException(guild, Permission.VIEW_CHANNEL, "Cannot create channel in " + category.getName());
        }
        if (channel == null) {
            if (guild.getCategoryById(category.getIdLong()) == null) {
                fetchChannels();
                category = getFreeCategory(activeCategories);
                if (category == null) {
                    if (throwError) throw new IllegalArgumentException("Interview category was deleted or is inaccessible");
                    return null;
                }
                channel = RateLimitUtil.complete(category.createTextChannel(user.getName()));
            }
            if (channel == null) {
                if (throwError) throw new IllegalArgumentException("Error creating channel");
                return null;
            }
        }
        RateLimitUtil.complete(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));

        StringBuilder mentions = new StringBuilder(user.getAsMention());

        Role interviewer = Roles.INTERVIEWER.toRole(user, db);
        if (interviewer != null) {
            RateLimitUtil.queue(channel.upsertPermissionOverride(interviewer).grant(Permission.VIEW_CHANNEL));
            mentions.append(interviewer.getAsMention());
        }
        Role iaRole = Roles.INTERNAL_AFFAIRS_STAFF.toRole(user, db);
        if (iaRole != null && interviewer == null) {
            RateLimitUtil.queue(channel.upsertPermissionOverride(iaRole).grant(Permission.VIEW_CHANNEL));
            mentions.append(iaRole.getAsMention());
        }

        Role iaRole2 = Roles.INTERNAL_AFFAIRS.toRole(user, db);
        if (iaRole2 != null && interviewer == null && iaRole2 != iaRole) {
            RateLimitUtil.queue(channel.upsertPermissionOverride(iaRole2).grant(Permission.VIEW_CHANNEL));
            mentions.append(iaRole2.getAsMention());
        }

        RateLimitUtil.queue(channel.upsertPermissionOverride(guild.getRolesByName("@everyone", false).get(0)).deny(Permission.VIEW_CHANNEL));

        if (nation != null) {
            IAChannel iaChannel = new IAChannel(nation, db, channel.getParentCategory(), channel);
            iaChannel.updatePerms();
            channelMap.put(iaChannel.getNation(), iaChannel);
        }

        IMessageIO io = new DiscordChannelIO(channel);
        IMessageBuilder msg = io.create();

        String body = db.getCopyPasta("interview", true);
        if (body != null) {
            String title = "Welcome to " + guild.getName() + " " + user.getName();
            msg.embed(title, body);
        }

        msg.append(mentions.toString()).send();

        return channel;
    }

    public List<Category> getCategories() {
        Set<Category> result = new ObjectLinkedOpenHashSet<>();
        result.addAll(activeCategories);
        result.addAll(inactiveCategories);
        result.addAll(passedCategories);
        return new ArrayList<>(result);
    }

    public List<TextChannel> getAllChannels() {
        Set<TextChannel> result = new ObjectLinkedOpenHashSet<>();
        for (Category category : getCategories()) {
            result.addAll(category.getTextChannels());
        }
        return new ArrayList<>(result);
    }

    private void fetchChannels() {
        channelMap.clear();

        List<Category> categories = guild.getCategories().stream().filter(f -> f.getName().toLowerCase().startsWith("interview")).toList();

        for (Category category : categories) {
            String name = category.getName().toLowerCase();
            if (name.startsWith("interview")) {
                if (name.contains("archive")) {
                    passedCategories.add(category);
                } else if (name.contains("inactive")) {
                    inactiveCategories.add(category);
                } else if (name.contains("entry") || name.contains("progress")) {
                    activeCategories.add(0, category);
                } else {
                    activeCategories.add(category);
                }
            }
        }
        Category interviewCategory = GuildKey.INTERVIEW_CATEGORY.getOrNull(db);
        if (interviewCategory != null && !interviewCategory.getName().toLowerCase().startsWith("interview")) {
            activeCategories.add(interviewCategory);
        }
    }

    public Map.Entry<DBNation, User> getNationUser(GuildMessageChannel channel) {
        if (!(channel instanceof TextChannel)) return new KeyValue<>(null, null);
        TextChannel tc = (TextChannel) channel;
        DBNation nation = null;
        User user = null;

        String[] split = channel.getName().split("-");
        if (MathMan.isInteger(split[split.length - 1])) {
            int nationId = Integer.parseInt(split[split.length - 1]);
            nation = Locutus.imp().getNationDB().getNationById(nationId);
            if (nation != null) {
                user = nation.getUser();
            }
        }

        if (nation == null) {
            Member member = getOverride(tc.getMemberPermissionOverrides());
            if (member != null) {
                user = member.getUser();
                nation = DiscordUtil.getNation(member.getIdLong());
            }
        }
        return new KeyValue<>(nation, user);

    }

    public void purgeUnusedChannels(IMessageIO output, boolean force) {
        if (output == null) output = this.output;

        List<TextChannel> channels = getAllChannels();
        for (TextChannel channel : channels) {
            IAChannel iaChannel = get(channel, true);
            if (iaChannel == null) {
                Member member = getOverride(channel.getMemberPermissionOverrides());
                if (member == null) {
                    if (force) {
                        if (output != null)
                            output.send("Deleted channel " + channel.getName() + " (no member was found)");
                        RateLimitUtil.queue(channel.delete());
                    } else {
                        // just prompt to use CM.interview.sync
                        if (output != null) {
                            output.send("Channel " + channel.getName() + " has no member assigned. Use " + CM.interview.sync.cmd.toSlashMention() + " to delete unused channels.");
                        }
                    }
                    continue;
                }
                GuildMessageChannel tc = (GuildMessageChannel) channel;
                try {
                    Message latest = RateLimitUtil.complete(tc.retrieveMessageById(tc.getLatestMessageIdLong()));
                    long created = latest.getTimeCreated().toEpochSecond() * 1000L;
                    if (System.currentTimeMillis() - created > TimeUnit.DAYS.toMillis(10)) {
                        if (output != null) output.send("Deleted channel " + channel.getName() + " (no recent message- no nation (found)");
                        RateLimitUtil.queue(tc.delete());
                    }
                } catch (Exception e) {}
            } else {
                DBNation nation = iaChannel.getNation();
                if (nation.active_m() > 20000 || (nation.active_m() > 10000) && !alliance.isInAlliance(nation)) {
                   if (output != null) output.send("Deleted channel " + channel.getName() + " (nation is (inactive)");
                    RateLimitUtil.queue(channel.delete());
                }
            }
        }
    }

    public void alertInvalidChannels(IMessageIO output) {
        if (output == null) output = this.output;

        List<TextChannel> channels = getAllChannels();
        for (TextChannel channel : channels) {
            IAChannel iaChannel = get(channel, true);

            if (
                // channel is null
                    iaChannel == null ||
                            // nation is inactive vm app
                            (iaChannel.getNation().active_m() > 10000 && iaChannel.getNation().getPosition() <= 1) ||
                            // nation not in alliance
                            !alliance.contains(iaChannel.getNation().getAlliance_id()) ||
                            // user is null
                            iaChannel.getNation().getUser() == null ||
                            guild.getMember(iaChannel.getNation().getUser()) == null
            ) {
                try {

                    StringBuilder body = new StringBuilder(channel.getAsMention());
                    DBNation nation = null;
                    if (iaChannel != null) {
                        nation = iaChannel.getNation();
                    }
                    if (nation != null) {
                        body.append("\n").append(nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup());
                        body.append("\nPosition: ").append(Rank.byId(nation.getPosition()));
                        body.append("\nActive: ").append(TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m()));
                        User user = nation.getUser();
                        if (user != null) {
                            body.append("\n").append(user.getAsMention());
                        }
                    }
                    if (channel.getLatestMessageIdLong() > 0) {
                        long latestMessage = channel.getLatestMessageIdLong();
                        Message message = RateLimitUtil.complete(channel.retrieveMessageById(latestMessage));
                        if (message != null) {
                            long diff = System.currentTimeMillis() - message.getTimeCreated().toEpochSecond() * 1000L;
                            body.append("\n\nLast message: ").append(TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
                        }
                    }

                    String emoji = "Delete Channel";

                    body.append("\n\nPress `" + emoji + "` to delete");
                    output.create().embed( "Interview not assigned to a member", body.toString())
                                    .commandButton(CM.channel.delete.current.cmd.channel(channel.getAsMention()), emoji)
                                            .send();

                    if (nation != null && ((nation.active_m() > 7200) || (nation.active_m() > 2880 && (nation.getCities() < 10 || nation.getPosition() <= 1 || !alliance.contains(nation.getAlliance_id()))))) {
                        if (getFreeCategory(inactiveCategories) != null && !inactiveCategories.contains(channel.getParentCategory())) {
                            RateLimitUtil.queue(channel.getManager().setParent(getFreeCategory(inactiveCategories)));
                        }
                    }
                } catch (Throwable ignore) {
                    ignore.printStackTrace();
                }
            }
        }
    }

    public IACategory load() {
        fetchChannels();

        List<TextChannel> channels = getAllChannels();

        Set<Integer> duplicates = new IntOpenHashSet();

        for (TextChannel channel : channels) {
            String name = channel.getName();
            String[] split = name.split("-");
            if (split.length <= 1) continue;
            String idStr = split[split.length - 1];

            IAChannel iaChannel = null;
            if (MathMan.isInteger(idStr)) {
                int nationId = Integer.parseInt(idStr);
                DBNation nation = DBNation.getById(nationId);
                if (nation != null) {
                    iaChannel = new IAChannel(nation, db, channel.getParentCategory(), channel);
//                    iaChannel.updatePerms();
                }
            }
            if (iaChannel == null) {
                Member member = getOverride(channel.getMemberPermissionOverrides());
                if (member != null) {
                    DBNation nation = DiscordUtil.getNation(member.getIdLong());
                    if (nation != null) {
                        iaChannel = new IAChannel(nation, db, channel.getParentCategory(), channel);
                        iaChannel.updatePerms();
                    }
                }
            }
            if (iaChannel != null) {
                DBNation nation = iaChannel.getNation();
                if (!duplicates.add(nation.getNation_id())) {
                    RateLimitUtil.queue(iaChannel.getChannel().delete());
                    continue;
                }
                channelMap.put(iaChannel.getNation(), iaChannel);
                if (getFreeCategory(inactiveCategories) != null) {
                    if ((nation.active_m() > 7200) ||
                            (nation.active_m() > 2880 && (nation.getCities() < 10 || nation.getPosition() <= 1 || !alliance.contains(nation.getAlliance_id()))) ||
                            (nation.active_m() > 2880 && inactiveCategories.contains(channel.getParentCategory()))
                    ) {
                        if (!inactiveCategories.contains(channel.getParentCategory())) {
                            RateLimitUtil.queue(channel.getManager().setParent(getFreeCategory(inactiveCategories)));
                        }
                    } else if (inactiveCategories.contains(channel.getParentCategory())) {
                        RateLimitUtil.queue(channel.getManager().setParent(getFreeCategory(activeCategories)));
                    }
                }
            }
        }

        Set<DBNation> nations = alliance.getNations(true, 2880, true);
        for (DBNation nation : nations) {
            if (channelMap.containsKey(nation)) continue;
        }
        return this;
    }

    private Member getOverride(List<PermissionOverride> overrides) {
        Member member = null;
        for (PermissionOverride override : overrides) {
            if (override.getMember() != null) {
                if (Settings.INSTANCE.DISCORD.BOT_OWNER_IS_LOCUTUS_ADMIN && override.getMember().getIdLong() == Locutus.loader().getAdminUserId()) continue;
                if (member != null) {
                    return null;
                }
                member = override.getMember();
            }
        }
        return member;
    }

    public IMessageIO getOutput() {
        return output;
    }

    public void update(Map<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> result) {
        for (Map.Entry<DBNation, IAChannel> entry : channelMap.entrySet()) {
            DBNation nation = entry.getKey();
            IAChannel iaChannel = entry.getValue();

            Map<IACheckup.AuditType, Map.Entry<Object, String>> audit = result.get(nation);
            if (audit == null) continue;
            audit = IACheckup.simplify(audit);

            Category archived = getFreeCategory(passedCategories);
            if (audit.isEmpty() && archived != null) {
                TextChannel channel = iaChannel.getChannel();
                if (activeCategories.contains(channel.getParentCategory())) {
                    RateLimitUtil.queue(channel.getManager().setParent(archived));
                }
            }

            iaChannel.update(audit);
        }
    }

    public Map<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> update() throws InterruptedException, ExecutionException, IOException {
        ArrayList<DBNation> nations = new ArrayList<>(channelMap.keySet());
        ArrayList<DBNation> toCheckup = new ArrayList<>(nations);
        toCheckup.removeIf(f -> f.getVm_turns() > 0 || f.active_m() > 2880 || f.getPosition() <= 1);
        IACheckup checkup = new IACheckup(db, db.getAllianceList(), true);

        Map<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> result = checkup.checkup(toCheckup, f -> {}, true);

        update(result);

        return result;
    }

    public boolean isValid() {
        return !channelMap.isEmpty();
    }

    public SortedCategory getSortedCategory(GuildMessageChannel channel) {
        if (!(channel instanceof ICategorizableChannel)) return null;
        Category category = ((ICategorizableChannel) channel).getParentCategory();
        if (category != null) {
            return SortedCategory.parse(category.getName());
        }
        return null;
    }

    public void sort(IMessageIO output, Collection<TextChannel> channels, boolean sortCategoried) {
        Set<TextChannel> toSort = new HashSet<>();

        Map<SortedCategory, Set<Category>> categories = new HashMap<>();
        for (Category category : getCategories()) {
            SortedCategory sortCat = SortedCategory.parse(category.getName());
            if (sortCat != null) {
                categories.computeIfAbsent(sortCat, f -> new HashSet<>()).add(category);
            }
        }


        if (!sortCategoried) {
            for (TextChannel channel : channels) {
                Category category = channel.getParentCategory();
                SortedCategory sortedType = SortedCategory.parse(category.getName());
                if (sortedType != null) continue;
                toSort.add(channel);
            }
        } else {
            toSort.addAll(channels);
        }
        toSort.removeIf(f -> f.getParentCategory().getName().contains("archive"));
        if (toSort.isEmpty()) throw new IllegalArgumentException("No channels found to sort");

        List<SortedCategory> fullCategories = new ArrayList<>();

        outer:
        for (TextChannel channel : channels) {
            for (SortedCategory sort : SortedCategory.values()) {
                IAChannel iaCat = get(channel, true);
                if (iaCat == null) continue;
                if (sort.matches(this, db, alliance.getIds(), channel, iaCat)) {
                    SortedCategory currentSort = SortedCategory.parse(channel.getParentCategory().getName());
                    if (currentSort == sort) continue outer;

                    DBNation nation = iaCat.getNation();
                    if (nation != null && currentSort != null && sort != null && sort.ordinal() > currentSort.ordinal()) {
                        ByteBuffer buf = db.getNationMeta(nation.getNation_id(), NationMeta.IA_CATEGORY_MAX_STAGE);
                        int previousMaxStage = -1;
                        if (buf != null) {
                            previousMaxStage = buf.get();
                        }

                        byte[] valueArr = new byte[]{(byte) sort.ordinal()};
                        db.setMeta(nation.getNation_id(), NationMeta.IA_CATEGORY_MAX_STAGE, valueArr);

                        if (previousMaxStage != -1 && previousMaxStage < sort.ordinal()) {
                            int times = sort.ordinal() - previousMaxStage;
                            Map<ResourceType, Double> amtMap = db.getOrNull(GuildKey.REWARD_MENTOR);
                            if (amtMap != null) {
                                double[] amt = ResourceType.resourcesToArray(PW.multiply(amtMap, (double) times));
                                String msg = "Mentoring from " + currentSort.name() + " -> " + sort.name();
                                db.getHandler().reward(nation, NationMeta.INCENTIVE_MENTOR, false, amt, msg, new Supplier<DBNation>() {
                                    @Override
                                    public DBNation get() {
                                        return iaCat.getLastActiveGov(false);
                                    }
                                });
                            }
                        }
                    }

                    Set<Category> newParents = categories.get(sort);
                    if (newParents == null) throw new IllegalArgumentException("No category found for: " + sort);
                    Category parent = getFreeCategory(newParents);
                    if (parent == null) {
                        fullCategories.add(sort);
                        continue outer;
                    }
                    output.send("Moving " + channel.getAsMention() + " from " + channel.getParentCategory().getName() + " to " + parent.getName());
                    RateLimitUtil.complete(channel.getManager().setParent(parent));
                    continue outer;
                }
            }
            Category parent = getFreeCategory(passedCategories);
            if (parent != null && !channel.getParentCategory().equals(parent)) {
                output.send("Moving " + channel.getAsMention() + " from " + channel.getParentCategory().getName() + " to " + parent.getName());
                RateLimitUtil.complete(channel.getManager().setParent(parent));
            }
        }

        if (!fullCategories.isEmpty()) {
            throw new IllegalArgumentException("Categories are full for (50 channels per category): " + StringMan.getString(fullCategories));
        }
    }

    public static class AssignedMentor {
        public final DBNation mentor;
        public final DBNation mentee;
        public final long timeAssigned;
        public final NationMeta type;

        public AssignedMentor(DBNation mentor, DBNation mentee, long timeAssigned, NationMeta type) {
            this.mentor = mentor;
            this.mentee = mentee;
            this.timeAssigned = timeAssigned;
            this.type = type;
        }
    }

    public AssignedMentor getMentor(DBNation member, long updateTimediff) {
        List<NationMeta> metas = Arrays.asList(NationMeta.CURRENT_MENTOR, NationMeta.INCENTIVE_INTERVIEWER, NationMeta.INCENTIVE_REFERRER, NationMeta.REFERRER);
        DBNation latestNation = null;
        NationMeta latestMeta = null;
        long latestTime = 0;
        for (NationMeta meta : metas) {
            ByteBuffer buf = db.getMeta(member.getNation_id(), meta);
            if (buf != null) {
                DBNation nation = DBNation.getById(buf.getInt());
                long timeStamp = buf.getLong();
                if (timeStamp > latestTime) {
                    latestTime = timeStamp;
                    latestNation = nation;
                    latestMeta = meta;
                    break;
                }
            }
        }
        long cutoff = System.currentTimeMillis() - updateTimediff;
        if ((latestMeta != NationMeta.CURRENT_MENTOR) && latestTime < cutoff) {
            IAChannel iaChan = get(member);
            if (iaChan != null) {
                GuildMessageChannel channel = iaChan.getChannel();
                if (channel != null && channel.getLatestMessageIdLong() > 0) {
                    long latestMessageId = channel.getLatestMessageIdLong();
                    OffsetDateTime created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(latestMessageId);
                    if (created.toEpochSecond() * 1000L > cutoff) {
                        latestNation = iaChan.getLastActiveGov(false);

                        if (latestNation != null) {
                            ByteBuffer data = ByteBuffer.allocate(Integer.SIZE + Long.SIZE);
                            data.putInt(latestNation.getNation_id());
                            data.putLong(latestTime = System.currentTimeMillis());
                            db.setMeta(member.getNation_id(), latestMeta = NationMeta.CURRENT_MENTOR, data.array());
                            return new AssignedMentor(latestNation, member, latestTime, latestMeta);
                        }
                    }
                }
            }
            ByteBuffer data = ByteBuffer.allocate(Integer.SIZE + Long.SIZE);
            data.putInt(0);
            data.putLong(latestTime = System.currentTimeMillis());
            latestNation = null;
            db.setMeta(member.getNation_id(), latestMeta = NationMeta.CURRENT_MENTOR, data.array());
        }
        if (latestNation != null) {
            return new AssignedMentor(latestNation, member, latestTime, latestMeta);
        }
        return null;
    }

    public List<Category> getActiveCategories() {
        return activeCategories;
    }

    public enum SortedCategory {
        INACTIVE("Inactive nations") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (!(channel instanceof TextChannel)) return false;
                TextChannel tc = (TextChannel) channel;
                DBNation nation = iaChan == null ? null : iaChan.getNation();
                if (nation != null && Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;
                if (iaCat.inactiveCategories.contains(tc.getParentCategory())) {
                    if (iaChan == null ||
                        (nation.active_m() > 2880 && (nation.getCities() < 10 || nation.getPosition() <= 1 || !allianceIds.contains(nation.getAlliance_id()))) ||
                        nation.active_m() > 7200 ||
                        db.getGuild().getMember(iaChan.getNation().getUser()) == null) {
                        return true;
                    }
                }
                if (iaChan == null) {
                    return false;
                }
                if (nation.active_m() > 7200 || (nation.active_m() > 2880 && (nation.getCities() < 10 || nation.getPosition() <= 1 || !allianceIds.contains(nation.getAlliance_id())))) return true;
                return false;
            }
        },
        ENTRY("people still doing the initial interview / not a member yet") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return true;
                DBNation nation = iaChan.getNation();
                if (!allianceIds.contains(nation.getAlliance_id()) || nation.getPosition() <= 1) return true;
                User user = nation.getUser();
                if (user == null || db.getGuild().getMember(user) == null) return true;
                return false;
            }
        },
        RAIDS("haven't started their initial raids yet") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBNation nation = iaChan.getNation();
                if (Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;
                if (nation.getOff() < 4 && nation.getCities() < 10 && (nation.getDef() == 0 || !nation.isBeige())) {
                    return true;
                }
                if (nation.getMeta(NationMeta.INTERVIEW_DEPOSITS) == null && nation.getOff() < 5) {
                    Set<DBNation> enemies = Locutus.imp().getNationDB().getNationsByAlliance(Collections.singleton(0));
                    enemies.removeIf(f -> f.getVm_turns() > 0 || f.getScore() > nation.getScore() * PW.WAR_RANGE_MAX_MODIFIER || f.getScore() < nation.getScore() * 0.75 || f.active_m() < 10000);
                    int raids = Math.min(4, enemies.size());
                    if (nation.getOff() < raids) return true;
                }
                return false;
            }
        },
        BANK("hasn't deposited/withdrawn (using bot) from the alliance bank before\n") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBNation nation = iaChan.getNation();
                if (Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;
                if (nation.getMeta(NationMeta.INTERVIEW_DEPOSITS) == null) return true;
                if (db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW) == Boolean.TRUE) {
                    List<Transaction2> transactions = nation.getTransactions(-1, false);
                    for (Transaction2 transaction : transactions) {
                        if(transaction.receiver_id == nation.getNation_id() && transaction.note.contains("#deposit")) {
                            return false;
                        }
                    }
                    User user = nation.getUser();
                    if (user == null) return false;
                    Member member = db.getGuild().getMember(user);
                    if (member == null) return false;
                    if (nation.getMeta(NationMeta.INTERVIEW_TRANSFER_SELF) == null && !Roles.ECON_WITHDRAW_SELF.has(member)) {
                        return true;
                    }
                }
                return false;
            }
        },
        SPIES("hasn't used " + CM.nation.spies.cmd.toSlashCommand() + " and " + CM.spy.find.intel.cmd.toSlashCommand() + " (and posted spy report)") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBNation nation = iaChan.getNation();
                if (Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;
                return nation.getMeta(NationMeta.INTERVIEW_SPIES) == null ||
                        nation.getMeta(NationMeta.INTERVIEW_SPYOP) == null;
            }
        },
        BUILD("has not gotten a grant for an optimal build") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBNation nation = iaChan.getNation();
                if (Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;
                if (nation.getCities() >= 10 && nation.getAvg_infra() < 1650 && nation.getAvgBuildings() < 29) return true;

                boolean fighting = nation.getNumWars() > 0 || nation.getColor() == NationColor.BEIGE;
                boolean hasEnemies = !db.getCoalitionRaw(Coalition.ENEMIES).isEmpty();
                boolean fightingEnemy = hasEnemies;
                if (!fightingEnemy && nation.getNumWars() > 0) {
                    for (DBWar war : nation.getActiveWars()) {
                        DBNation other = war.getNation(!war.isAttacker(nation));
                        if (other != null && other.active_m() < 4880) fightingEnemy = true;
                    }

                }

                Set<String> allowedMMR = new HashSet<>();
                if (nation.getCities() < 10 || fighting) allowedMMR.add("500X");
                if (hasEnemies || nation.getCities() > 10 || fighting) {
                    allowedMMR.add("505X");
                }
                if (fightingEnemy) allowedMMR.add("555X");

                List<String> currentMMR = Arrays.asList(nation.getMMR(), nation.getMMRBuildingStr());
                for (String allowed : allowedMMR) {
                    for (String current : currentMMR) {
                        StringBuilder copy = new StringBuilder(current);
                        for (int i = 0; i < 4; i++) {
                            if (allowed.charAt(i) == 'X') {
                                copy.setCharAt(i, 'X');
                            }
                        }
                        if (allowed.equals(copy.toString())) return false;
                    }
                }
                return true;
            }
        },
        COUNTERS("hasn't done two of the following: countered someone, sniped a raid target, fought a defensive war") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBNation nation = iaChan.getNation();
                if (Roles.GRADUATED.has(nation.getUser(), db.getGuild())) return false;

                boolean snipe = false;
                boolean counterOff = false;
                boolean counterDef = false;
                for (DBWar war : nation.getWars()) {
                    if (TimeUtil.checkTurnChange(war.getDate())) snipe = true;
                    if (war.getDefender_id() == nation.getNation_id()) counterDef = true;
                    else {
                        try {
                            if (war.getCounterStat().type == CounterType.IS_COUNTER) counterOff = true;
                        } catch (Throwable ignore) {
                        }
                    }
                }
                int num = (snipe ? 1 : 0) + (counterOff ? 1 : 0) + (counterDef ? 1  : 0);
                return num < 2;
            }
        },
        TEST("hasn't done the tests (and received the graduated role)") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                if (iaChan == null) return false;
                DBNation nation = iaChan.getNation();
                User user = nation.getUser();
                if (user == null) return false;
                Member member = db.getGuild().getMember(user);
                return (member != null && !Roles.GRADUATED.has(member));
            }
        },

        ARCHIVE("graduated nations") {
            @Override
            public boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan) {
                return true;
            }
        }

        ;

        private final String desc;

        SortedCategory(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }

        public static SortedCategory parse(String name) {
            String[] split = name.split("-");
            for (SortedCategory value : SortedCategory.values()) {
                for (String s : split) {
                    if (s.equalsIgnoreCase(value.name())) {
                        return value;
                    }
                }
            }
            return null;
        }

        public abstract boolean matches(IACategory iaCat, GuildDB db, Set<Integer> allianceIds, GuildMessageChannel channel, IAChannel iaChan);
    }
}
