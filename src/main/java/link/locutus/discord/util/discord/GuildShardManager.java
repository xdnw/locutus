package link.locutus.discord.util.discord;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class GuildShardManager {
    private ShardManager defaultShardManager;
    private final Set<JDA> instances = new ObjectLinkedOpenHashSet<>();
    private final Map<Long, JDA> discordAPIMap = new ConcurrentHashMap<>();

    public GuildShardManager() {

    }

    public GuildShardManager(JDA jda) {
        this.instances.add(jda);
        defaultShardManager = null;
        for (Guild guild : jda.getGuilds()) {
            this.discordAPIMap.put(guild.getIdLong(), jda);
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

    }

    public void init(GuildShardManager other) {
        this.defaultShardManager = other.defaultShardManager;
        this.instances.addAll(other.instances);
        this.discordAPIMap.putAll(other.discordAPIMap);
    }

    public void add(long guildId, JDA instance) {
        this.discordAPIMap.put(guildId, instance);
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
        String name = user.getName();
        long hash = StringMan.hash(name);

        String globalName = user.getGlobalName();
        long globalHash = globalName == null ? 0 : StringMan.hash(globalName.toLowerCase(Locale.ROOT));

        String nickName = member.getNickname();
        long nickHash = nickName == null ? 0 : StringMan.hash(nickName.toLowerCase(Locale.ROOT));

        synchronized (userIdCache) {
            userIdCache.put(hash, member.getIdLong());
            if (nickHash != 0) userIdCache.putIfAbsent(nickHash, member.getIdLong());
            if (globalHash != 0) userIdCache.putIfAbsent(globalHash, member.getIdLong());
        }
        return member;
    }

    public static User updateUserName(User user) {
        if (user == null || user.isBot() || user.isSystem()) return user;
        String name = user.getName();
        long hash = StringMan.hash(name);

        String globalName = user.getGlobalName();
        long globalHash = globalName == null ? 0 : StringMan.hash(globalName.toLowerCase(Locale.ROOT));

        synchronized (userIdCache) {
            userIdCache.put(hash, user.getIdLong());
            if (globalHash != 0) userIdCache.putIfAbsent(globalHash, user.getIdLong());
        }
        return user;
    }

    public User getUserByName(String searchName, boolean forceCheckRegistered, Guild checkGuild) {
        String usernameLower = searchName.toLowerCase();
        long searchHash = StringMan.hash(usernameLower);
        long foundId;
        synchronized (userIdCache) {
            foundId = userIdCache.getOrDefault(searchHash, 0L);
        }

        if (foundId != 0) {
            return getUserById(foundId);
        }

        if (checkGuild != null) {
            for (Member member : checkGuild.getMembers()) {
                User user = member.getUser();
                String otherName = user.getName();
                long otherHash = StringMan.hash(otherName);

                String globalName = user.getGlobalName();
                long globalHash = globalName == null ? 0 : StringMan.hash(globalName.toLowerCase(Locale.ROOT));

                String nickName = member.getNickname();
                long nickHash = nickName == null ? 0 : StringMan.hash(nickName.toLowerCase(Locale.ROOT));

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

        if (forceCheckRegistered && !initialized) {
            synchronized (userIdCache) {
                if (!initialized) {
                    initialized = true;
                    for (Map.Entry<Long, PNWUser> entry : Locutus.imp().getDiscordDB().getRegisteredUsers().entrySet()) {
                        long userId = entry.getKey();
                        PNWUser pnwUser = entry.getValue();
                        String userName = pnwUser.getDiscordName();
                        if (userName == null || userName.isEmpty()) continue;
                        userName = userName.split("#")[0];
                        long hash = StringMan.hash(userName.toLowerCase(Locale.ROOT));
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
        JDA api = getApiByGuildId(id);
        if (api == null) return null;
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
        Collection<GuildDB> dbs = Locutus.imp().getGuildDatabases().values();
        Set<Guild> guilds = new ObjectLinkedOpenHashSet<>(dbs.size());
        for (GuildDB db : dbs) {
            Guild guild = db.getGuild();
            if (guild.isDetached()) continue;
            guilds.add(guild);
        }
        return guilds;
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
}
