package link.locutus.discord.commands.stock;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Exchange {
    public ExchangeCategory category;
    public int id;
    public String symbol;
    public String name;
    public String description;
    public int shares;
    public int owner;
    private long guildId;
    public Rank requiredRank;
    public String charter;
    public String website;

    public Exchange(ExchangeCategory category, String symbol, String description, int owner, long guildId) {
        this.category = category;
        this.symbol = symbol;
        this.name = symbol;
        this.description = description;
        this.shares = 0;
        this.owner = owner;
        this.guildId = guildId;
        this.requiredRank = Rank.OFFICER;
    }

    public Exchange(ResultSet rs) throws SQLException {
        id = rs.getInt(1);
        symbol = rs.getString(2);
        name = rs.getString(3);
        description = rs.getString(4);
        shares = rs.getInt(5);
        owner = rs.getInt(6);
        guildId = rs.getLong(7);
        charter = rs.getString(8);
        website = rs.getString(9);
        this.category = ExchangeCategory.values[rs.getInt(10)];
        this.requiredRank = Rank.byId(rs.getInt(11));
    }

    public boolean isAlliance() {
        return category == ExchangeCategory.ALLIANCE;
    }

    public Guild getGuild() {
        return Locutus.imp().getDiscordApi().getGuildById(guildId);
    }

    public Invite getInvite() {
        if (isResource()) return null;
        Guild guild = getGuild();
        if (guild == null) return null;
        List<Invite> invites = RateLimitUtil.complete(guild.retrieveInvites());
        for (Invite invite : invites) {
            if (invite.getMaxUses() == 0) {
                return invite;
            }
        }
         return RateLimitUtil.complete(getChannel().createInvite().setUnique(false).setMaxAge(Integer.MAX_VALUE).setMaxUses(0));
    }

    public boolean isResource() {
        return id < ResourceType.values.length;
    }

    public ResourceType getResource() {
        return isResource() ? ResourceType.values[id] : null;
    }

    public boolean checkPermission(DBNation nation, Rank rank) {
        if (getRank(nation).id >= rank.id) return true;
        User user = nation.getUser();
        if (user != null) {
            Guild root = getRootGuild();
            return Roles.ECON.has(user, root);
        }

        return false;
    }

    public Rank getRank(DBNation nation) {
        if (owner == nation.getNation_id()) return Rank.LEADER;
        Rank rank = getOfficers().get(nation.getNation_id());
        if (rank != null) return rank;
        rank = Rank.REMOVE;

        if (isAlliance()) {
            if (nation.getAlliance_id() == id) {
                rank = Rank.byId(nation.getPosition());
            }
        }

        return rank;
    }

    public Map<Integer, Rank> getOfficers() {
        Map<Integer, Rank> map = Locutus.imp().getStockDB().getOfficers(id);
        map.put(owner, Rank.LEADER);
        return map;
    }

    public void addOfficer(DBNation nation, Rank rank) {
        Locutus.imp().getStockDB().addOfficer(id, nation.getNation_id(), rank);
    }

    public void removeOfficer(int officer) {
        Locutus.imp().getStockDB().removeOfficer(id, officer);
    }

    public List<Map.Entry<Date, Long>> esvHistory() {
        return Locutus.imp().getStockDB().getESVHistory(id);
    }

    public void addESV(long value) {
        Locutus.imp().getStockDB().setESV(id, value);
    }

    public Map<Exchange, Long> getBalance() {
        if (isResource()) return Collections.emptyMap();
        StockDB db = Locutus.imp().getStockDB();
        OffshoreInstance offshore = Locutus.imp().getRootBank();

        Map<Exchange, Long> result = db.getSharesByExchange(this);

        if (isAlliance()) {
            Map<ResourceType, Double> deposits = offshore.getDeposits(id, true);
            for (Map.Entry<ResourceType, Double> entry : deposits.entrySet()) {
                ResourceType type = entry.getKey();
                Exchange rssEx = db.getExchange(type.ordinal());


            }

            // add offshore amount (if whitelisted)
        }
        return null;
    }

//    public Map<Exchange, Long> getDeposits(DBNation nation) {
//        if (isResource()) return Collections.emptyMap();
//        StockDB db = Locutus.imp().getStockDB();
//        Map<Exchange, Long> result = new HashMap<>();
//        Map<Exchange, Long> result2 = db.getSharesByExchange(this);
//
//        if (isAlliance()) {
//            int aaId = -id;
//            GuildDB guildDb = Locutus.imp().getGuildDBByAA(aaId);
//            if (guildDb == null) throw new IllegalArgumentException("Locutus is not setup for: AA:" + aaId);
//
//            Map<DepositType, double[]> aaDeposits = nation.getDeposits(guildDb, null, true, true, 0L, 0L);
//            double[] total = ResourceType.getBuffer();
//            for (Map.Entry<DepositType, double[]> entry : aaDeposits.entrySet()) {
//                total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, entry.getValue());
//            }
//            for (ResourceType type : ResourceType.values) {
//                double amt = total[type.ordinal()];
//                if (amt != 0) {
//                    long amtLong = (long) (amt * 100L);
//                    Exchange rssEx = db.getExchange(type.ordinal());
//                    result.put(rssEx, result.getOrDefault(rssEx, 0L) + amtLong);
//                }
//            }
//        } else {
//        }
//        throw new UnsupportedOperationException("TODO WIP");
//    }

    @Override
    public String toString() {
        StockDB db = Locutus.imp().getStockDB();
        Exchange exchange = this;
        StringBuilder body = new StringBuilder();
        body.append("**ID:** " + exchange.id + " | " + category);
        if (requiredRank.id <= 0) {
            body.append(" (public)");
        }
        body.append("\n**Name:** " + exchange.symbol);
        if (exchange.description != null) {
            body.append("\n**Desc:** " + exchange.description);
        }
        body.append("\n**Channel**: " + getChannel().getAsMention());
        Invite invite = getInvite();
        if (invite != null) {
            body.append("\n**Invite**: " + invite.getUrl());
        }
        if (exchange.owner != 0) {
            body.append("\n**Owner:** " + PnwUtil.getMarkdownUrl(exchange.owner, false));
        }
        body.append("\n**Shares**: " + MathMan.format(exchange.shares / 100d));

        Map<Integer, Long> shareholders = db.getShareholdersByCorp(exchange.id);
        body.append("\n**Shareholders**: " + shareholders.size());
        if (exchange.owner != 0 && shareholders.size() > 0) {
            long foreign = 0;
            for (Map.Entry<Integer, Long> entry : shareholders.entrySet()) {
                if (entry.getKey() != exchange.owner) foreign += entry.getValue();
            }
            body.append(" (" + MathMan.format(100 * foreign / (double) exchange.shares) + "% foreign)");
        }

        long maxPrice = 0;
        long minPrice = Long.MAX_VALUE;
        List<StockTrade> open = db.getOpenTradesByCorp(exchange.id);
        for (StockTrade trade : open) {
            if (trade.buyer == 0) {
                maxPrice = Math.max(trade.price, maxPrice);
            } else if (trade.seller == 0) {
                minPrice = Math.min(trade.price, minPrice);
            }
        }
        if (maxPrice != 0) body.append("\n**Buy for**: $" + MathMan.format(minPrice / 100d));
        else body.append("\nBuying: no current buy offers");
        if (minPrice != Long.MAX_VALUE) body.append("\n**Sell for**: $" + MathMan.format(minPrice / 100d));
        else body.append("\nSelling: no current sell offers");
        return body.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Integer) {
            return id == (int) o;
        }
        if (o == null || getClass() != o.getClass()) return false;

        Exchange exchange = (Exchange) o;

        return id == exchange.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public void setGuildId(long guildId) {
        this.guildId = guildId;
    }

    public long getGuildId() {
        if (category == ExchangeCategory.ALLIANCE) {
            GuildDB db = Locutus.imp().getGuildDBByAA(id);
            if (db != null) return guildId = db.getGuild().getIdLong();
        }
        return guildId;
    }

    public String getName() {
        return name;
    }

    public boolean canView(DBNation nation) {
        return getRank(nation).id >= requiredRank.id;
    }

    public TextChannel getChannel() {
        List<Long> categories = Arrays.asList( // TODO make settings
                807937946279608320L, // 0 resource
                791187405805584414L, // 1 Alliance
                791176598220963840L, // 2 corp private
                807141723486552114L // 3 corp public
        );
        long categoryId = 0;
        if (isResource()) {
            categoryId = categories.get(0);
        } else if (isAlliance()) {
            categoryId = categories.get(1);
        } else if (requiredRank.id > 0) {
            categoryId = categories.get(2);
        } else {
            categoryId = categories.get(3);
        }
        Guild guild = getRootGuild();

        // get existing channel
        Category jdaCategory = guild.getCategoryById(categoryId);
        for (TextChannel GuildMessageChannel : jdaCategory.getTextChannels()) {
            String channelName = GuildMessageChannel.getName();
            channelName = channelName.split("-")[0];
            if (channelName.equalsIgnoreCase(id + "")) return GuildMessageChannel;
        }

        List<Future<?>> tasks = new ArrayList<>();
        // Fix category
        for (Long otherCategoryId : categories) {
            if (otherCategoryId == categoryId) continue;
            Category otherCategory = guild.getCategoryById(otherCategoryId);
            if (otherCategory == null) continue;

            for (TextChannel channel : otherCategory.getTextChannels()) {
                String channelName = channel.getName();
                channelName = channelName.split("-")[0];
                if (channelName.equalsIgnoreCase(id + "")) {
                    tasks.add(RateLimitUtil.queue(channel.getManager().setParent(jdaCategory)));
                    return channel;
                }
            }
        }

        CompletableFuture<TextChannel> channelFuture = RateLimitUtil.queue(jdaCategory.createTextChannel(id + "-" + symbol));
        tasks.add(channelFuture);
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        return channelFuture.getNow(null);
    }

    public void setChannelInvite() {
        Invite invite = getInvite();
        if (invite != null) {
            getChannel().getManager().setTopic(invite.getUrl());
        }
    }

    public Guild getRootGuild() {
        return Locutus.imp().getDiscordApi().getGuildById(StockDB.ROOT_GUILD);
    }

    public void alert(String title, String message) {
        DiscordUtil.createEmbedCommand(getChannel(), title, message);
    }

    public String alert(String message) {
        RateLimitUtil.queue(getChannel().sendMessage(message));
        return message;
    }

//    private Map<Rank, Role> roleCache = new HashMap<>();

    public Role getRole(Rank rank) {
        if (rank.id <= 0) throw new IllegalArgumentException("Role cannot be <= 0");

        String rankStr = rank.name();
        String prefix = "EX " + id + " ";
        String expected = "EX " + id + " " + symbol + " " + rank.name();

        Guild guild = getRootGuild();

        List<Role> roles = guild.getRolesByName(expected, true);
        if (roles.size() == 1) return roles.get(0);

        for (Role role : guild.getRoles()) {
            String roleName = role.getName();
            if (roleName.startsWith(prefix)) {
                String[] split = roleName.split(" ");
                if (split[split.length - 1].equalsIgnoreCase(rankStr)) {
                    return role;
                }
            }
        }
        return null;
    }

    public Map<Rank, Role> getCompanyRoles() {
        Map<Rank, Role> result = new HashMap<Rank, Role>();

        Guild guild = getRootGuild();
        String prefix = "EX " + id + " ";
        for (Role role : guild.getRoles()) {
            String roleName = role.getName();
            if (roleName.startsWith(prefix)) {
                String[] split = roleName.split(" ");
                String rankStr = split[split.length - 1];
                Rank rank = Rank.valueOf(rankStr);
                result.put(rank, role);
            }
        }
        return result;
    }

    public void autoRole(Member member) {
        // autoRole shareholders and members
    }

    /**
     * Only includes members pressent on root discord
     * @return
     */
    public Map<Rank, Set<Member>> getMembers() {
        Map<Integer, Rank> officers = getOfficers();

        Map<Rank, Set<Member>> result = new HashMap<>();

        Guild guild = getRootGuild();
        for (Member member : guild.getMembers()) {
            DBNation nation = DiscordUtil.getNation(member.getIdLong());
            if (nation == null) continue;

            Rank rank = officers.get(nation.getNation_id());
            if (rank == null) continue;

            result.computeIfAbsent(rank, f -> new LinkedHashSet<>()).add(member);
        }
        return result;
    }

    public Map<Rank, Set<Member>> getMembersMasked() {
        Map<Rank, Set<Member>> result = new HashMap<>();
        Guild guild = getRootGuild();
        for (Map.Entry<Rank, Role> entry : getCompanyRoles().entrySet()) {
            Rank rank = entry.getKey();
            Role role = entry.getValue();
            List<Member> members = guild.getMembersWithRoles(role);
            if (!members.isEmpty()) result.put(rank, new HashSet<>(members));
        }
        return result;
    }

    public void autoRole() {
        Map<Rank, Role> roles = getCompanyRoles();
        Map<Rank, Set<Member>> requiredRanks = getMembers();
        Map<Rank, Set<Member>> currentlyMasked = getMembersMasked();

        Guild guild = getRootGuild();

        for (Map.Entry<Rank, Role> entry : roles.entrySet()) {
            Rank rank = entry.getKey();
            Role role = entry.getValue();

            Set<Member> toMask = requiredRanks.getOrDefault(rank, Collections.emptySet());
            Set<Member> masked = currentlyMasked.getOrDefault(rank, Collections.emptySet());
            toMask.removeAll(masked);
            masked.removeAll(toMask);
            for (Member member : toMask) {
                RateLimitUtil.queue(guild.addRoleToMember(member, role));
            }
            for (Member member : masked) {
                RateLimitUtil.queue(guild.removeRoleFromMember(member, role));
            }
        }
    }

    public synchronized void createRoles() {
        if (isResource()) return;

        Map<Rank, Role> roles = getCompanyRoles();
        Set<Rank> requiredRanks = new HashSet<>(getOfficers().values());
        requiredRanks.removeIf(f -> f.id <= 0);

        Guild guild = getRootGuild();

        Role positionRole = null;

        for (Rank rank : requiredRanks) {
            if (roles.containsKey(rank)) continue;

            String name = "EX " + id + " " + symbol + " " + rank.name();
            Role role = RateLimitUtil.complete(guild.createRole()
                    .setName(name)
                    .setMentionable(false)
                    .setHoisted(true)
                    );

            if (positionRole == null) positionRole = guild.getRoleById(807944560176529440L);
            if (positionRole != null) {
                RateLimitUtil.queue(guild.modifyRolePositions().selectPosition(role).moveTo(positionRole.getPosition() + 1));
            }
        }
    }

    public void setupChannelPermissions() {
        TextChannel channel = getChannel();
        createRoles();
        Map<Rank, Role> roleMap = getCompanyRoles();
        Role everyone = channel.getGuild().getRolesByName("@everyone", false).get(0);

        boolean hasRoleSet = false;
        List<PermissionOverride> rolePerms = channel.getRolePermissionOverrides();
        for (Map.Entry<Rank, Role> entry : roleMap.entrySet()) {
            Role role = entry.getValue();
            PermissionOverride override = channel.getPermissionOverride(role);
            if (override == null) {
                switch (entry.getKey()) {
                    case LEADER:
//                        link.locutus.discord.util.RateLimitUtil.queue(channel.upsertPermissionOverride(role).setAllowed(Permission.MANAGE_CHANNEL));
                    case HEIR:
                        RateLimitUtil.queue(channel.upsertPermissionOverride(role).setAllowed(Permission.MANAGE_PERMISSIONS));
                    case OFFICER:
                        RateLimitUtil.queue(channel.upsertPermissionOverride(role).setAllowed(Permission.MESSAGE_MANAGE));
                    case MEMBER:
                        RateLimitUtil.queue(channel.upsertPermissionOverride(role).setAllowed(Permission.MESSAGE_SEND));
                    case APPLICANT:
                        RateLimitUtil.queue(channel.upsertPermissionOverride(role).setAllowed(Permission.VIEW_CHANNEL));
//                        link.locutus.discord.util.RateLimitUtil.queue(channel.upsertPermissionOverride(role).setAllowed(Permission.MESSAGE_ADD_REACTION));
                        break;
                }
            } else {
                hasRoleSet = true;
            }
        }

        if (!hasRoleSet) {
            if (requiredRank.id <= 0) {
                RateLimitUtil.queue(channel.upsertPermissionOverride(everyone).setAllowed(Permission.VIEW_CHANNEL));
                RateLimitUtil.queue(channel.upsertPermissionOverride(everyone).setAllowed(Permission.MESSAGE_SEND));
            } else {
                RateLimitUtil.queue(channel.upsertPermissionOverride(everyone).deny(Permission.VIEW_CHANNEL));
                RateLimitUtil.queue(channel.upsertPermissionOverride(everyone).deny(Permission.MESSAGE_SEND));
            }
        }
    }

    // create the missing roles

    //

//    public String alertShareholders(String message) {
//
//    }
//
//    public String alert(String message, Rank rank) {
//        String fullMessage = message;
//        if (rank != null) {
//
//        }
//    }
}
