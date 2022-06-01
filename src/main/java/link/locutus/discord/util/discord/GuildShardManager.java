package link.locutus.discord.util.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class GuildShardManager {
    private List<JDA> instances = new ArrayList<>();
    private Map<Long, JDA> discordAPIMap = new ConcurrentHashMap<>();

    public void add(long guildId, JDA instance) {
        this.discordAPIMap.put(guildId, instance);
    }

    public JDA getApiByGuildId(long guildId) {
        JDA jda = discordAPIMap.get(guildId);
        return jda;
    }


    public void put(JDA instance) {
        instances.add(instance);
    }

    public void put(long guildId, JDA instance) {
        discordAPIMap.put(guildId, instance);
        instances.add(instance);
    }

    public GuildMessageChannel getGuildChannelById(long id) {
        return get((jda) -> jda.getTextChannelById(id));
    }

    public User getUserById(long id) {
        return get((jda) -> jda.getUserById(id));
    }

    public User getUserByTag(String username, String descriminator) {
        return get((jda) -> jda.getUserByTag(username, descriminator));
    }


    public List<User> getUsersByName(String username, boolean descriminator) {
        List<User> users = new ArrayList<>();
        for (JDA jda : instances) {
            List<User> toAdd = jda.getUsersByName(username, descriminator);
            if (toAdd != null && !toAdd.isEmpty()) {
                users.addAll(toAdd);
            }
        }
        return users;
    }

    public Guild getGuildById(long id) {
        JDA api = getApiByGuildId(id);
        if (api == null) return null;
        return api.getGuildById(id);
    }

    public <T,V> V get(Function<JDA, V> get) {
        if (instances.isEmpty()) return null;
        if (instances.size() == 1) {
            V value = get.apply(instances.get(0));
            return value;
        }
        for (JDA jda : instances) {
            V value = get.apply(jda);
            if (value != null) return value;
        }
        return null;
    }

    public Collection<Guild> getGuilds() {
        LinkedHashSet<Guild> guilds = new LinkedHashSet<>();
        for (JDA jda : instances) {
            guilds.addAll(jda.getGuilds());
        }
        return guilds;
    }

    public Collection<JDA> getApis() {
        return discordAPIMap.values();
    }

    public void awaitReady() throws InterruptedException {
        for (JDA jda : instances) {
            jda.awaitReady();
            for (Guild guild : jda.getGuilds()) {
                discordAPIMap.put(guild.getIdLong(), jda);
            }
        }
    }

    public Set<User> getUsers() {
        Set<User> users = new LinkedHashSet<>();
        for (JDA jda : instances) {
            users.addAll(jda.getUsers());
        }
        return users;
    }
}
