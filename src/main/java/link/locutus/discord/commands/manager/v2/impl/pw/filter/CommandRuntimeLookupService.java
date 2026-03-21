package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Snapshot-aware nation and Discord identity lookup for the neutral command runtime path.
 */
public final class CommandRuntimeLookupService {
    private static final Pattern NATION_ID_FORMULA_PATTERN = Pattern.compile("nation/id=([0-9]+)");

    private final CommandRuntimeLookupResolver resolver;

    CommandRuntimeLookupService(CommandRuntimeLookupResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public INationSnapshot currentSnapshot() {
        return resolver.nationSnapshots().resolve(null);
    }

    public DBNation getNationById(int nationId) {
        return currentSnapshot().getNationById(nationId);
    }

    public DBNation getNationOrCreate(int nationId) {
        return getNationOrCreate(currentSnapshot(), nationId);
    }

    public DBNation getNationOrCreate(INationSnapshot snapshot, int nationId) {
        DBNation nation = snapshot.getNationById(nationId, true);
        if (nation != null) {
            return nation;
        }
        return resolver.getNationOrCreate(nationId);
    }

    public DBAlliance getAllianceById(int allianceId) {
        return resolver.getAllianceById(allianceId);
    }

    public DBAlliance getAllianceOrCreate(int allianceId) {
        return resolver.getAllianceOrCreate(allianceId);
    }

    public PNWUser getRegisteredUser(User user) {
        return user == null ? null : resolver.getRegisteredUserById(user.getIdLong());
    }

    public PNWUser getRegisteredUser(String userName, String fullTag) {
        return resolver.getRegisteredUser(userName, fullTag);
    }

    public User getDiscordUserById(long userId) {
        return resolver.getDiscordUserById(userId);
    }

    public DBNation getNationByUser(User user) {
        return getNationByUser(currentSnapshot(), user, false);
    }

    public DBNation getNationByUser(long userId) {
        return getNationByUser(currentSnapshot(), userId, false);
    }

    public DBNation getNationByUser(INationSnapshot snapshot, User user) {
        return getNationByUser(snapshot, user, false);
    }

    public DBNation getNationByUser(INationSnapshot snapshot, User user, boolean allowDeleted) {
        return user == null ? null : getNationByUser(snapshot, user.getIdLong(), allowDeleted);
    }

    public DBNation getNationByUser(INationSnapshot snapshot, long userId, boolean allowDeleted) {
        PNWUser registeredUser = resolver.getRegisteredUserById(userId);
        if (registeredUser == null) {
            return null;
        }
        return snapshot.getNationById(registeredUser.getNationId(), allowDeleted);
    }

    public DBNation parseNation(String arg, boolean throwError, Guild guildOrNull) {
        return parseNation(currentSnapshot(), arg, false, false, throwError, guildOrNull);
    }

    public DBNation parseNation(String arg, boolean allowDeleted, boolean throwError, Guild guildOrNull) {
        return parseNation(currentSnapshot(), arg, allowDeleted, false, throwError, guildOrNull);
    }

    public DBNation parseNation(String arg, boolean allowDeleted, boolean useLeader, boolean throwError,
            Guild guildOrNull) {
        return parseNation(currentSnapshot(), arg, allowDeleted, useLeader, throwError, guildOrNull);
    }

    public DBNation parseNation(INationSnapshot snapshot, String arg, boolean allowDeleted, boolean throwError,
            Guild guildOrNull) {
        return parseNation(snapshot, arg, allowDeleted, false, throwError, guildOrNull);
    }

    public DBNation parseNation(INationSnapshot snapshot, String arg, boolean allowDeleted, boolean useLeader,
            boolean throwError, Guild guildOrNull) {
        String argLower = arg.toLowerCase(Locale.ROOT);
        if (argLower.contains("/alliance/") || argLower.startsWith("aa:") || argLower.startsWith("alliance:")) {
            return null;
        }
        if (argLower.startsWith("leader:")) {
            arg = arg.substring(7);
            useLeader = true;
        }
        if (useLeader) {
            DBNation nation = snapshot.getNationByLeader(arg);
            if (nation != null) {
                return nation;
            }
            if (throwError) {
                throw new IllegalArgumentException("Nation not found matching leader name: `" + arg + "`");
            }
        }
        DBNation nation = parseNationCore(snapshot, arg, allowDeleted, throwError, guildOrNull);
        if (nation != null) {
            return nation;
        }
        if (throwError) {
            throw new IllegalArgumentException("Nation not found matching: `" + arg + "`");
        }
        return null;
    }

    public Long parseUserId(Guild guild, String arg) {
        return parseUserId(currentSnapshot(), guild, arg);
    }

    public Long parseUserId(INationSnapshot snapshot, Guild guild, String arg) {
        arg = normalizeUserLookupArg(arg);
        if (MathMan.isInteger(arg)) {
            return Long.parseLong(arg);
        }
        User user = resolver.findDiscordUser(arg, guild);
        if (user != null) {
            return user.getIdLong();
        }
        DBNation nation = parseNation(snapshot, arg, false, false, false, guild);
        if (nation != null) {
            return nation.getUserId();
        }
        return null;
    }

    public User getUser(String arg, Guild guildOrNull) {
        return getUser(currentSnapshot(), arg, guildOrNull);
    }

    public User getUser(INationSnapshot snapshot, String arg, Guild guildOrNull) {
        arg = normalizeUserLookupArg(arg);
        if (MathMan.isInteger(arg)) {
            return resolver.getDiscordUserById(Long.parseLong(arg));
        }
        DBNation nation = parseNation(snapshot, arg, false, false, false, guildOrNull);
        if (nation != null) {
            return nation.getUser();
        }
        return null;
    }

    private DBNation parseNationUser(INationSnapshot snapshot, String arg, boolean allowDeleted,
            boolean throwError) {
        String argNoBrackets = arg.substring(1, arg.length() - 1);
        if (argNoBrackets.charAt(0) == '@') {
            String idStr = argNoBrackets.substring(1);
            if (idStr.charAt(0) == '!') {
                idStr = idStr.substring(1);
            }
            if (MathMan.isInteger(idStr)) {
                long discordId = Long.parseLong(idStr);
                PNWUser dbUser = resolver.getRegisteredUserById(discordId);
                if (dbUser != null) {
                    int nationId = dbUser.getNationId();
                    DBNation nation = snapshot.getNationById(nationId, allowDeleted);
                    if (nation != null) {
                        return nation;
                    }
                    if (throwError) {
                        throw new IllegalArgumentException("User: `" + dbUser.getDiscordName()
                                + "` is registered to `nation:" + nationId + "` which does not exist (was it deleted?)");
                    }
                    return null;
                }

                if (throwError) {
                    User user = resolver.getDiscordUserById(discordId);
                    if (user != null) {
                        throw new IllegalArgumentException("User: `" + user
                                + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                    }
                    throw new IllegalArgumentException(
                            "No registered user found by user-id: `" + discordId + "` (are you sure they are registered?)");
                }
                return null;
            }
        }
        if (MathMan.isInteger(argNoBrackets)) {
            long id = Long.parseLong(argNoBrackets);
            DBNation nation;
            if (id > Integer.MAX_VALUE) {
                nation = getNationByUser(snapshot, id, false);
            } else {
                nation = snapshot.getNationById((int) id, allowDeleted);
            }
            if (nation != null) {
                return nation;
            }
            if (throwError) {
                if (id > Integer.MAX_VALUE) {
                    User user = resolver.getDiscordUserById(id);
                    if (user != null) {
                        throw new IllegalArgumentException("User: `" + user
                                + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                    }
                    throw new IllegalArgumentException(
                            "No registered user found by id: `" + id + "` (are you sure they are registered?)");
                } else {
                    throw new IllegalArgumentException("No registered nation found with id: `" + id
                            + "` (did they delete?. See also `"
                            + CM.admin.sync.syncNations.cmd.nations(Long.toString(id)) + "`)");
                }
            }
        }
        if (throwError) {
            throw new IllegalArgumentException("Invalid user syntax: `" + arg + "`");
        }
        return null;
    }

    private DBNation parseNationCore(INationSnapshot snapshot, String arg, boolean allowDeleted, boolean throwError,
            Guild guildOrNull) {
        arg = arg.trim();
        if (arg.isEmpty()) {
            if (throwError) {
                throw new IllegalArgumentException("Empty text provided for nation: `" + arg + "`");
            }
            return null;
        }
        if (arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            return parseNationUser(snapshot, arg, allowDeleted, throwError);
        }
        boolean checkUser = true;
        String argLower = arg.toLowerCase(Locale.ROOT);
        if (argLower.startsWith("nation:")) {
            arg = arg.substring(7);
            checkUser = false;
            argLower = arg.toLowerCase(Locale.ROOT);
        }
        if (argLower.startsWith("leader:")) {
            arg = arg.substring(7);
            DBNation nation = snapshot.getNationByLeader(arg);
            if (nation != null) {
                return nation;
            }
            if (MathMan.isInteger(arg)) {
                long id = Long.parseLong(arg);
                nation = snapshot.getNationById((int) id, allowDeleted);
                if (nation != null) {
                    return nation;
                }
            }
            if (throwError) {
                throw new IllegalArgumentException("No registered nation found by leader: `" + arg + "`");
            }
            return null;
        }
        if (arg.contains("/nation/id=") || arg.contains("politicsandwar.com/nation/war/declare/id=")
                || arg.contains("politicsandwar.com/nation/espionage/eid=")) {
            String[] split = arg.split("=");
            if (split.length == 2) {
                arg = split[1].replaceAll("/", "");
            }
            if (MathMan.isInteger(arg)) {
                long id = Long.parseLong(arg);
                DBNation nation = snapshot.getNationById((int) id, allowDeleted);
                if (nation != null) {
                    return nation;
                }
                if (throwError) {
                    throw new IllegalArgumentException("No registered nation found by id: `" + arg
                            + "` (did they delete?. See also `" + CM.admin.sync.syncNations.cmd.nations(arg)
                            + "`)");
                }
            }
            if (throwError) {
                throw new IllegalArgumentException("Invalid nation id: `" + arg + "`");
            }
            return null;
        }
        if (arg.charAt(0) == '@') {
            String idStr = arg.substring(1);
            if (idStr.charAt(0) == '!') {
                idStr = idStr.substring(1);
            }
            if (MathMan.isInteger(idStr)) {
                long discordId = Long.parseLong(idStr);
                PNWUser dbUser = resolver.getRegisteredUserById(discordId);
                if (dbUser != null) {
                    int nationId = dbUser.getNationId();
                    DBNation nation = snapshot.getNationById(nationId, allowDeleted);
                    if (nation != null) {
                        return nation;
                    }
                    if (throwError) {
                        throw new IllegalArgumentException("User: `" + dbUser.getDiscordName()
                                + "` is registered to `nation:" + nationId + "` which does not exist (was it deleted?)");
                    }
                    return null;
                }
                if (throwError) {
                    User user = resolver.getDiscordUserById(discordId);
                    if (user != null) {
                        throw new IllegalArgumentException("User: `" + user
                                + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                    }
                    throw new IllegalArgumentException(
                            "No registered user found by id: `" + discordId + "` (are you sure they are registered?)");
                }
            }
            PNWUser dbUser = resolver.getRegisteredUser(arg, arg);
            if (dbUser != null) {
                int nationId = dbUser.getNationId();
                DBNation nation = snapshot.getNationById(nationId, allowDeleted);
                if (nation != null) {
                    return nation;
                }
                if (throwError) {
                    throw new IllegalArgumentException("User: `" + dbUser.getDiscordName()
                            + "` is registered to `nation:" + nationId + "` which does not exist (was it deleted?)");
                }
                return null;
            }
            User discUser = resolver.findDiscordUser(arg, guildOrNull);
            if (discUser != null) {
                DBNation nation = getNationByUser(snapshot, discUser.getIdLong(), false);
                if (nation != null) {
                    return nation;
                }
                if (throwError) {
                    throw new IllegalArgumentException("User: `" + discUser
                            + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                }
            }
            if (throwError) {
                throw new IllegalArgumentException(
                        "No registered discord user found for: `" + arg + "` (are you sure they are registered?)");
            }
            return null;
        }
        if (MathMan.isInteger(arg)) {
            long id = Long.parseLong(arg);
            DBNation nation;
            if (id > Integer.MAX_VALUE) {
                nation = getNationByUser(snapshot, id, false);
            } else {
                nation = snapshot.getNationById((int) id, allowDeleted);
            }
            if (nation != null) {
                return nation;
            }
            if (throwError) {
                if (id > Integer.MAX_VALUE) {
                    User user = resolver.getDiscordUserById(id);
                    if (user != null) {
                        throw new IllegalArgumentException("User: `" + user
                                + "` is not registered to a nation. See: " + CM.register.cmd.toSlashMention());
                    }
                    throw new IllegalArgumentException(
                            "No registered user found by id: `" + id + "` (are you sure they are registered?)");
                } else {
                    throw new IllegalArgumentException("No registered nation found by id: `" + id
                            + "` (did they delete?. See also `" + CM.admin.sync.syncNations.cmd.nations(arg)
                            + "`)");
                }
            }
            return null;
        }
        if (arg.startsWith("=")) {
            if (arg.contains("=HYPERLINK") && arg.contains("nation/id=")) {
                Matcher matcher = NATION_ID_FORMULA_PATTERN.matcher(arg);
                matcher.find();
                arg = matcher.group(1);
                int id = Integer.parseInt(arg);
                DBNation nation = snapshot.getNationById(id, allowDeleted);
                if (nation != null) {
                    return nation;
                }
                if (throwError) {
                    throw new IllegalArgumentException("No registered nation found by id: `" + id
                            + "` (did they delete?. See also `" + CM.admin.sync.syncNations.cmd.nations(arg)
                            + "`)");
                }
            }
            if (throwError) {
                throw new IllegalArgumentException("Invalid formula: `" + arg + "`");
            }
            return null;
        }
        DBNation nation = snapshot.getNationByNameOrLeader(arg);
        if (nation != null) {
            return nation;
        }
        PNWUser dbUser = resolver.getRegisteredUser(arg, arg);
        if (dbUser != null) {
            int nationId = dbUser.getNationId();
            nation = snapshot.getNationById(nationId, allowDeleted);
            if (nation != null) {
                return nation;
            }
            if (throwError) {
                throw new IllegalArgumentException("User: `" + dbUser.getDiscordName()
                        + "` is registered to `nation:" + nationId + "` which does not exist (was it deleted?)");
            }
            return null;
        }
        if (checkUser && arg.matches("([a-zA-Z0-9_.]{2,32})")) {
            User discordUser = resolver.findDiscordUser(arg, guildOrNull);
            if (discordUser != null) {
                nation = getNationByUser(snapshot, discordUser.getIdLong(), false);
                if (nation != null) {
                    return nation;
                }
            }
            if (throwError) {
                throw new IllegalArgumentException("No registered nation or discord user: `" + arg + "`");
            }
        }
        if (throwError) {
            throw new IllegalArgumentException("Invalid nation syntax: `" + arg + "`");
        }
        return null;
    }

    private static String normalizeUserLookupArg(String arg) {
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '@') {
            arg = arg.substring(1);
        }
        if (arg.charAt(0) == '!') {
            arg = arg.substring(1);
        }
        return arg;
    }
}
