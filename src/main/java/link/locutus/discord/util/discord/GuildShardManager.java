package link.locutus.discord.util.discord;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.sharding.DefaultShardManager;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class GuildShardManager {
    private ShardManager defaultShardManager;
    private final Set<JDA> instances = new ObjectLinkedOpenHashSet<>();
    private final Map<Long, JDA> discordAPIMap = new ConcurrentHashMap<>();
    private volatile Supplier<Map<Long, PNWUser>> registeredUsersSupplier;
    private volatile Supplier<? extends Collection<Guild>> cachedGuildsSupplier;

    public GuildShardManager() {

    }

    public GuildShardManager(JDA jda) {
        this.instances.add(jda);
        defaultShardManager = null;
        for (Guild guild : jda.getGuilds()) {
            this.discordAPIMap.put(guild.getIdLong(), jda);
        }
    }

    public void addEventListener(Object... listeners) {
        if (defaultShardManager != null) {
            defaultShardManager.addEventListener(listeners);
        } else {
            for (JDA instance : instances) {
                instance.addEventListener(listeners);
            }

        }
    }

    public GuildShardManager(ShardManager defaultShardManager) {
        this.defaultShardManager = defaultShardManager;
        for (JDA shard : defaultShardManager.getShards()) {
            this.instances.add(shard);
            for (Guild guild : shard.getGuilds()) {
                this.discordAPIMap.put(guild.getIdLong(), shard);
            }
        }
        for (Guild guild : defaultShardManager.getGuilds()) {
        }


    }

    public void init(GuildShardManager other) {
        this.defaultShardManager = other.defaultShardManager;
        this.instances.addAll(other.instances);
        this.discordAPIMap.putAll(other.discordAPIMap);
    }

    public void add(long guildId, JDA instance) {
        this.discordAPIMap.put(guildId, instance);
    }

    public GuildShardManager setRegisteredUsersSupplier(Supplier<Map<Long, PNWUser>> supplier) {
        this.registeredUsersSupplier = supplier;
        return this;
    }

    public GuildShardManager setCachedGuildsSupplier(Supplier<? extends Collection<Guild>> supplier) {
        this.cachedGuildsSupplier = supplier;
        return this;
    }

    public JDA getApiByGuildId(long guildId) {
        JDA jda = discordAPIMap.get(guildId);
        return jda;
    }

    public void put(long guildId, JDA instance) {
        discordAPIMap.put(guildId, instance);
        instances.add(instance);
    }

    public GuildMessageChannel getGuildChannelById(long id) {
        GuildChannel result = get((jda) -> jda.getGuildChannelById(id));
        if (result instanceof GuildMessageChannel gmc) return gmc;
        return null;
    }

    public User getUserById(long id) {
        return get((jda) -> jda.getUserById(id));
    }

    private static final Map<Long, Long> userIdCache = new Long2LongOpenHashMap();
    private volatile boolean initialized = false;

    public static Member updateUserName(Member member) {
        if (member == null) return null;
        User user = member.getUser();
        if (user.isBot() || user.isSystem()) return member;
        long hash = usernameHash(user.getName());

        String globalName = user.getGlobalName();
        long globalHash = usernameHash(globalName);

        String nickName = member.getNickname();
        long nickHash = usernameHash(nickName);

        synchronized (userIdCache) {
            userIdCache.put(hash, member.getIdLong());
            if (nickHash != 0) userIdCache.putIfAbsent(nickHash, member.getIdLong());
            if (globalHash != 0) userIdCache.putIfAbsent(globalHash, member.getIdLong());
        }
        return member;
    }

    public static User updateUserName(User user) {
        if (user == null || user.isBot() || user.isSystem()) return user;
        long hash = usernameHash(user.getName());

        String globalName = user.getGlobalName();
        long globalHash = usernameHash(globalName);

        synchronized (userIdCache) {
            userIdCache.put(hash, user.getIdLong());
            if (globalHash != 0) userIdCache.putIfAbsent(globalHash, user.getIdLong());
        }
        return user;
    }

    public User getUserByName(String searchName, boolean forceCheckRegistered, Guild checkGuild) {
        return getUserByName(searchName, forceCheckRegistered, checkGuild, null);
    }

    public User getUserByName(String searchName, boolean forceCheckRegistered, Guild checkGuild,
            Supplier<Map<Long, PNWUser>> registeredUsersSupplierOverride) {
        if (searchName == null || searchName.isBlank()) {
            return null;
        }
        long searchHash = usernameHash(searchName.trim());
        long foundId;
        synchronized (userIdCache) {
            foundId = userIdCache.getOrDefault(searchHash, 0L);
        }

        if (foundId != 0) {
            User cachedUser = getUserById(foundId);
            if (cachedUser != null) {
                return cachedUser;
            }
        }

        if (checkGuild != null) {
            for (Member member : checkGuild.getMembers()) {
                User user = member.getUser();
                long otherHash = usernameHash(user.getName());

                String globalName = user.getGlobalName();
                long globalHash = usernameHash(globalName);

                String nickName = member.getNickname();
                long nickHash = usernameHash(nickName);

                synchronized (userIdCache) {
                    userIdCache.put(otherHash, member.getIdLong());
                    if (nickHash != 0) userIdCache.putIfAbsent(nickHash, member.getIdLong());
                    if (globalHash != 0) userIdCache.putIfAbsent(globalHash, member.getIdLong());
                }
                if (otherHash == searchHash) {
                    return user;
                }
                if (nickHash == searchHash) {
                    return user;
                }
                if (globalHash == searchHash) {
                    return user;
                }
            }
        }

        Supplier<Map<Long, PNWUser>> registeredUsers = registeredUsersSupplierOverride != null
                ? registeredUsersSupplierOverride
                : this.registeredUsersSupplier;
        if (forceCheckRegistered && registeredUsers != null && !initialized) {
            synchronized (userIdCache) {
                if (!initialized) {
                    initialized = true;
                    for (Map.Entry<Long, PNWUser> entry : registeredUsers.get().entrySet()) {
                        long userId = entry.getKey();
                        PNWUser pnwUser = entry.getValue();
                        String userName = pnwUser.getDiscordName();
                        if (userName == null || userName.isEmpty()) continue;
                        userName = userName.split("#")[0];
                        long hash = usernameHash(userName);
                        userIdCache.putIfAbsent(hash, userId);
                    }
                }
                foundId = userIdCache.getOrDefault(searchHash, 0L);
            }
            if (foundId != 0) {
                return getUserById(foundId);
            }
        }
        return null;
    }

    public Guild getGuildById(long id) {
        if (defaultShardManager != null) {
            return defaultShardManager.getGuildById(id);
        }
        JDA api = getApiByGuildId(id);
        if (api == null) {
            return null;
        }
        return api.getGuildById(id);
    }

    public <T,V> V get(Function<JDA, V> get) {
        if (instances.isEmpty()) return null;
        if (instances.size() == 1) {
            V value = get.apply(instances.iterator().next());
            return value;
        }
        for (JDA jda : instances) {
            V value = get.apply(jda);
            if (value != null) return value;
        }
        return null;
    }

    public Collection<Guild> getGuilds() {
        Set<Guild> guilds = new ObjectLinkedOpenHashSet<>();
        for (JDA jda : instances) {
            guilds.addAll(jda.getGuilds());
        }
        return guilds;
    }

    public Collection<Guild> getCachedGuilds() {
        Supplier<? extends Collection<Guild>> supplier = cachedGuildsSupplier;
        if (supplier != null) {
            Collection<Guild> guilds = supplier.get();
            return guilds == null ? List.of() : guilds;
        }
        return getGuilds();
    }

    public Collection<JDA> getApis() {
        return discordAPIMap.values();
    }

    public void awaitReady() throws InterruptedException {
        Set<JDA> copy = new HashSet<>(instances);
        for (JDA jda : copy) {
            jda.awaitReady();
            for (Guild guild : jda.getGuilds()) {
                discordAPIMap.put(guild.getIdLong(), jda);
            }
        }
    }

    public Set<User> getUsers() {
        Set<User> users = new ObjectLinkedOpenHashSet<>();
        for (JDA jda : instances) {
            users.addAll(jda.getUsers());
        }
        return users;
    }

    public JDA.Status getStatus() {
        JDA.Status status = null;
        for (JDA instance : instances) {
            JDA.Status curr = instance.getStatus();
            if (status == null || curr.ordinal() < status.ordinal()) {
                status = curr;
            }
        }
        return status;
    }

    public SelfUser getSelfUser() {
        for (JDA jda : instances) {
            SelfUser selfUser = jda.getSelfUser();
            if (selfUser != null) return selfUser;
        }
        return null;
    }

    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    public Set<Guild> getMutualGuilds(User user) {
        if (user == null) {
            return Set.of();
        }
        Collection<Guild> guilds = getCachedGuilds();
        int total = guilds.size();
        if (total < 2000) {
            Set<Guild> mutualGuilds = new ObjectLinkedOpenHashSet<>();
            for (Guild guild : guilds) {
                if (guild == null) continue;
                if (guild.isDetached()) continue;
                if (guild.isMember(user)) {
                    mutualGuilds.add(guild);
                }
            }
            return mutualGuilds;
        }
        final int estSize = Math.max(16, total / NUM_THREADS);
        return guilds.parallelStream()
                .filter(Objects::nonNull)
                .filter(g -> !g.isDetached() && g.isMember(user))
                .collect(Collectors.toCollection(() -> new ObjectOpenHashSet<>(estSize)));
    }


    public Member getFirstMember(User user) {
        Guild guild = getFirstMutualGuild(user);
        return guild == null ? null : guild.getMember(user);
    }

    public OnlineStatus getOnlineStatus(User user) {
        if (user == null) return null;
        Member member = getFirstMember(user);
        return member == null ? null : member.getOnlineStatus();
    }

    public Guild getFirstMutualGuild(User user) {
        for (Guild guild : getCachedGuilds()) {
            if (guild == null) continue;
            if (guild.isDetached()) continue;
            if (guild.isMember(user)) {
                return guild;
            }
        }
        return null;
    }

    private static long usernameHash(String value) {
        return value == null ? 0L : StringMan.hash(value.toLowerCase(Locale.ROOT));
    }
}
