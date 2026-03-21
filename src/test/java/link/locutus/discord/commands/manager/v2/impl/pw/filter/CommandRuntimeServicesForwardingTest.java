package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.discord.GuildShardManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRuntimeServicesForwardingTest {
    @Test
    void forwardsDiscordIdentityHelpersFromConcreteServices() throws Exception {
        long discordId = 900000000000123L;
        long guildId = 123456789012345L;
        long otherGuildId = 123456789012346L;
        String dbName = "command-runtime-services-forwarding-" + System.nanoTime();

        DiscordDB discordDb = new DiscordDB(dbName);
        File dbFile = discordDb.getFile();
        try {
            discordDb.addUser(new PNWUser(42, discordId, "alpha#0001"));

            User user = user(discordId, "alpha");
            Member member = member(user, null);
            Guild guild = guild(guildId, List.of(member));
            Guild otherGuild = guild(otherGuildId, List.of());
            GuildShardManager shardManager = new GuildShardManager(jda(List.of(user), List.of(guild, otherGuild)));

            CommandRuntimeServices services = CommandRuntimeServices.builder(modifier -> null)
                    .discordDb(() -> discordDb)
                    .shardManager(() -> shardManager)
                    .build();

            assertEquals(42, services.getRegisteredUserById(discordId).getNationId());
            assertEquals(42, services.getRegisteredUser("alpha", "alpha#0001").getNationId());
            assertSame(user, services.getDiscordUserById(discordId));
            assertSame(user, services.findDiscordUser("AlPhA", guild));
            assertSame(user, services.findDiscordUser("aLpHa", null));
            assertSame(guild, services.getGuild(guildId));
            assertEquals(Set.of(guildId), services.getMutualGuilds(user).stream()
                    .map(Guild::getIdLong)
                    .collect(Collectors.toSet()));
        } finally {
            discordDb.close();
            deleteDbFiles(dbFile);
        }
    }

    @Test
    void forwardsConfiguredAdminRegistrationWithoutLocutusFallback() throws Exception {
        long adminDiscordId = 900000000000999L;
        int originalNationId = Settings.INSTANCE.NATION_ID;
        long originalAdminUserId = Settings.INSTANCE.ADMIN_USER_ID;
        String dbName = "command-runtime-services-admin-forwarding-" + System.nanoTime();

        DiscordDB discordDb = new DiscordDB(dbName);
        File dbFile = discordDb.getFile();
        try {
            Settings.INSTANCE.NATION_ID = 77;
            Settings.INSTANCE.ADMIN_USER_ID = adminDiscordId;

            User adminUser = user(adminDiscordId, "admin");
            GuildShardManager shardManager = new GuildShardManager(jda(List.of(adminUser), List.of()));

            CommandRuntimeServices services = CommandRuntimeServices.builder(modifier -> null)
                    .discordDb(() -> discordDb)
                    .shardManager(() -> shardManager)
                    .build();

            PNWUser adminRegistration = services.getRegisteredUserById(adminDiscordId);

            assertEquals(77, adminRegistration.getNationId());
            assertEquals(adminDiscordId, adminRegistration.getDiscordId());
            assertEquals("admin", adminRegistration.getDiscordName());
        } finally {
            Settings.INSTANCE.NATION_ID = originalNationId;
            Settings.INSTANCE.ADMIN_USER_ID = originalAdminUserId;
            discordDb.close();
            deleteDbFiles(dbFile);
        }
    }

    @Test
    void forwardsNationHelpersFromNationDbByDefault() throws Exception {
        NationDB nationDb = allocateNationDb();
        DBAlliance storedAlliance = alliance(10, "Rose");
        DBNation storedNation = nation(77, "Alpha", 10);
        storedNation.setTax_id(444);

        putAlliance(nationDb, storedAlliance);
        putNation(nationDb, storedNation);

        CommandRuntimeServices services = CommandRuntimeServices.builder(NationSnapshotService.fixed(nationDb))
                .nationDb(() -> nationDb)
                .build();

        assertEquals(Set.of(10), services.getAlliances().stream()
                .map(DBAlliance::getId)
                .collect(Collectors.toSet()));
        assertSame(storedAlliance, services.lookup().getAllianceById(10));
        assertEquals(11, services.lookup().getAllianceOrCreate(11).getId());
        assertSame(storedNation, services.lookup().getNationOrCreate(77));
        assertEquals(88, services.lookup().getNationOrCreate(88).getId());
        assertEquals(10, services.taxBracketLookup().getAllianceIdByTaxId(444));
        assertEquals(Set.of("Alpha"), services.taxBracketLookup().getNationsByBracket(444).stream()
            .map(DBNation::getName)
            .collect(Collectors.toSet()));

        services.markNationDirty(77);
        assertTrue(dirtyNations(nationDb).contains(77));
    }

    private static NationDB allocateNationDb() throws Exception {
        NationDB nationDb = (NationDB) allocateWithoutConstructor(NationDB.class);
        setField(nationDb, "nationsById", new Int2ObjectOpenHashMap<DBNation>());
        setField(nationDb, "nationsByAlliance", new Int2ObjectOpenHashMap<Map<Integer, DBNation>>());
        setField(nationDb, "alliancesById", new Int2ObjectOpenHashMap<DBAlliance>());
        setField(nationDb, "citiesByNation", new Int2ObjectOpenHashMap<>());
        setField(nationDb, "positionsById", new Int2ObjectOpenHashMap<>());
        setField(nationDb, "positionsByAllianceId", new Int2ObjectOpenHashMap<>());
        setField(nationDb, "treatiesByAlliance", new Int2ObjectOpenHashMap<>());
        setField(nationDb, "treatyVersion", new AtomicLong(1));
        setField(nationDb, "dirtyCities", new IntOpenHashSet());
        setField(nationDb, "dirtyCityNations", Collections.synchronizedSet(new IntOpenHashSet()));
        setField(nationDb, "dirtyNations", Collections.synchronizedSet(new IntOpenHashSet()));
        setField(nationDb, "treasuresByNation", new Int2ObjectOpenHashMap<>());
        setField(nationDb, "treasuresByName", new ConcurrentHashMap<>());
        return nationDb;
    }

    private static void putNation(NationDB nationDb, DBNation nation) throws Exception {
        @SuppressWarnings("unchecked")
        Map<Integer, DBNation> nationsById = (Map<Integer, DBNation>) getField(nationDb, "nationsById");
        @SuppressWarnings("unchecked")
        Map<Integer, Map<Integer, DBNation>> nationsByAlliance =
            (Map<Integer, Map<Integer, DBNation>>) getField(nationDb, "nationsByAlliance");
        nationsById.put(nation.getId(), nation);
        nationsByAlliance.computeIfAbsent(nation.getAlliance_id(), ignored -> new Int2ObjectOpenHashMap<>())
            .put(nation.getId(), nation);
    }

    private static void putAlliance(NationDB nationDb, DBAlliance alliance) throws Exception {
        @SuppressWarnings("unchecked")
        Map<Integer, DBAlliance> alliancesById = (Map<Integer, DBAlliance>) getField(nationDb, "alliancesById");
        alliancesById.put(alliance.getId(), alliance);
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> dirtyNations(NationDB nationDb) throws Exception {
        return (Set<Integer>) getField(nationDb, "dirtyNations");
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = NationDB.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = NationDB.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object allocateWithoutConstructor(Class<?> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(unsafe, type);
    }

    private static DBNation nation(int id, String name, int allianceId) {
        SimpleDBNation nation = new SimpleDBNation(new DBNationData());
        nation.setNation_id(id);
        nation.setNation(name);
        nation.setAlliance_id(allianceId);
        return nation;
    }

    private static DBAlliance alliance(int id, String name) {
        return new DBAlliance(id, name, "", "", "", "", "", 0L, NationColor.GRAY,
                (Int2ObjectOpenHashMap<byte[]>) null);
    }

    private static User user(long id, String name) {
        return proxy(User.class, (proxy, method, args) -> switch (method.getName()) {
            case "getIdLong" -> id;
            case "getId" -> Long.toString(id);
            case "getName" -> name;
            case "getGlobalName" -> null;
            case "isBot", "isSystem" -> false;
            default -> defaultValue(proxy, method, args);
        });
    }

    private static Member member(User user, String nickname) {
        return proxy(Member.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUser" -> user;
            case "getIdLong" -> user.getIdLong();
            case "getId" -> user.getId();
            case "getNickname" -> nickname;
            default -> defaultValue(proxy, method, args);
        });
    }

    private static Guild guild(long id, List<Member> members) {
        return proxy(Guild.class, (proxy, method, args) -> switch (method.getName()) {
            case "getIdLong" -> id;
            case "getId" -> Long.toString(id);
            case "getMembers" -> members;
            case "isMember" -> members.stream().anyMatch(member -> member.getUser().getIdLong() == ((User) args[0]).getIdLong());
            default -> defaultValue(proxy, method, args);
        });
    }

    private static JDA jda(List<User> users, List<Guild> guilds) {
        Map<Long, User> usersById = new LinkedHashMap<>();
        for (User user : users) {
            usersById.put(user.getIdLong(), user);
        }
        Map<Long, Guild> guildsById = new LinkedHashMap<>();
        for (Guild guild : guilds) {
            guildsById.put(guild.getIdLong(), guild);
        }
        return proxy(JDA.class, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getGuilds" -> guilds;
                case "getUsers" -> users;
                case "getUserById" -> usersById.get(idArg(args[0]));
                case "getGuildById" -> guildsById.get(idArg(args[0]));
                default -> defaultValue(proxy, method, args);
            };
        });
    }

    private static long idArg(Object arg) {
        if (arg instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(arg));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> method.getDeclaringClass().getSimpleName() + "Proxy";
                default -> null;
            };
        }
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static void deleteDbFiles(File dbFile) {
        if (dbFile == null) {
            return;
        }
        deleteIfPresent(dbFile);
        deleteIfPresent(new File(dbFile.getAbsolutePath() + "-wal"));
        deleteIfPresent(new File(dbFile.getAbsolutePath() + "-shm"));
    }

    private static void deleteIfPresent(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Failed to delete test database file: " + file);
        }
    }
}
