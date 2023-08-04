package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.IModerator;
import link.locutus.discord.gpt.ModerationResult;
import link.locutus.discord.gpt.imps.IText2Text;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.TriConsumer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.collections.map.HashedMap;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SimpleGPTProvider extends GPTProvider {
    private final Logger logger;
    private final ExecutorService executor;
    private final IModerator moderator;
    private final ProviderType type;
    private long requireGuild;
    private int turnLimit;
    private int dayLimit;

    private final Map<Integer, Integer> turnUsesByNation = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> dayUsesByNation = new ConcurrentHashMap<>();
    private volatile long lastTurn;
    private volatile long lastDay;

    private ConcurrentHashMap<Integer, String> runningTasks = new ConcurrentHashMap<>();

    public SimpleGPTProvider(ProviderType type, IText2Text text, IModerator moderator, boolean allowMultipleThreads, org.slf4j.Logger logger) {
        super(text);
        this.type = type;
        this.moderator = moderator;
        this.logger = logger;

        if (allowMultipleThreads) {
            this.executor = Executors.newCachedThreadPool();
        } else {
            this.executor = Executors.newSingleThreadExecutor();
        }
    }

    @Override
    public Future<String> submit(GuildDB db, User user, Map<String, String> options, String input) {
        List<ModerationResult> modResult = moderator.moderate(input);
        GPTUtil.checkThrowModeration(modResult, input);

        // handle uses and concurrent tasks
        synchronized (runningTasks) {


            Future<String> result = this.executor.submit(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    IText2Text t2 = getText2Text();
                    return t2.generate(options, input);
                }
            });
            return result;
        }
    }

    @Override
    public ProviderType getType() {
        return type;
    }

    public SimpleGPTProvider requireGuild(Guild guild) {
        this.requireGuild = guild.getIdLong();
        return this;
    }

    public SimpleGPTProvider setTurnLimit(int limit) {
        this.turnLimit = limit;
        return this;
    }

    public SimpleGPTProvider setDayLimit(int limit) {
        this.dayLimit = limit;
        return this;
    }

    private void resetUsage() {
        long turn = TimeUtil.getTurn();
        long day = TimeUtil.getDay();
        if (turn == lastTurn && day == lastDay) return;
        synchronized (turnUsesByNation) {
            if (turn != lastTurn) {
                turnUsesByNation.clear();
                lastTurn = turn;
            }
            if (day != lastDay) {
                dayUsesByNation.clear();
                lastDay = day;
            }
        }
    }

    public int getUsesThisTurn(GuildDB db, DBNation nation) {
        resetUsage();
        return turnUsesByNation.getOrDefault(nation.getId(), 0);
    }

    public int getUsesToday(GuildDB db, DBNation nation) {
        resetUsage();
        return dayUsesByNation.getOrDefault(nation.getId(), 0);
    }

    public void addUse(GuildDB db, DBNation nation) {
        resetUsage();
        turnUsesByNation.merge(nation.getId(), 1, Integer::sum);
        dayUsesByNation.merge(nation.getId(), 1, Integer::sum);
    }

    @Override
    public boolean hasPermission(GuildDB db, User user, boolean checkLimits) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) {
            throw new IllegalArgumentException("User " + user.getName() + " must be registered to a nation. See " + CM.register.cmd.toSlashMention());
        }

        if (requireGuild != 0) {
            if (db.getIdLong() != requireGuild) {
                Guild other = Locutus.imp().getDiscordApi().getGuildById(requireGuild);
                String name = other == null ? "guild:" + requireGuild : other.toString();
                throw new IllegalArgumentException("The GPT provider `" + this.getText2Text().getId() + "` can only be used in the `" + name + "` guild.");
            }
        }

        Member member = db.getGuild().getMember(user);
        if (member == null) {
            throw new IllegalArgumentException("Cannot find member " + user.getName() + " in guild " + db.getGuild().getName());
        }

        if (requireGuild == 0) {
            // root
            if (Roles.AI_COMMAND_ACCESS.hasOnRoot(user)) {
                return true;
            }
        } else {
            if (!Roles.AI_COMMAND_ACCESS.has(member)) {
                throw new IllegalArgumentException("You do not have permission to use the GPT provider `" + this.getText2Text().getId() + "`. Missing role: " + Roles.AI_COMMAND_ACCESS.toDiscordRoleNameElseInstructions(db.getGuild()));
            }
            if (Roles.ADMIN.has(member)) {
                return true;
            }
        }

        if (turnLimit != 0) {
            int usedThisTurn = getUsesThisTurn(db, nation);
            if (usedThisTurn > this.turnLimit) {
                long nexTurnMs = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() + 1);
                throw new IllegalArgumentException("You have used the GPT provider `" + this.getText2Text().getId() + "` too many times this turn (" + usedThisTurn + "). Please wait until the next turn in " + DiscordUtil.timestamp(nexTurnMs, null) + ".");
            }
        }

        if (dayLimit != 0) {
            int usedToday = getUsesToday(db, nation);
            if (usedToday > this.dayLimit) {
                long nextDayMs = TimeUtil.getTimeFromDay(TimeUtil.getDay() + 1);
                throw new IllegalArgumentException("You have used the GPT provider `" + this.getText2Text().getId() + "` too many times today (" + usedToday + "). Please wait until the next day in " + DiscordUtil.timestamp(nextDayMs, null) + ".");
            }
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
    }
}
