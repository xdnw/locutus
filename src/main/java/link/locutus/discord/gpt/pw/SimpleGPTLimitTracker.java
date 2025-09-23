package link.locutus.discord.gpt.pw;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.ModerationResult;
import link.locutus.discord.gpt.imps.moderator.IModerator;
import link.locutus.discord.gpt.imps.text2text.IText2Text;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class SimpleGPTLimitTracker extends GptLimitTracker {
    private final Logger logger;
    private final ExecutorService executor;


    private final Supplier<IModerator> getModerator;
    private final Supplier<IText2Text> getText2Text;
    private final BiConsumer<IText2Text, Integer> addUsage;

    private volatile boolean paused = false;
    private static final ReentrantLock lock = new ReentrantLock();
    private static final Condition condition = lock.newCondition();
    private long requireGuild;
    private int turnLimit;
    private int dayLimit;
    private int guildTurnLimit;
    private int guildDayLimit;

    private final Map<Long, Integer> turnUsesByGuild = new ConcurrentHashMap<>();
    private final Map<Long, Integer> dayUsesByGuild = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> turnUsesByNation = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> dayUsesByNation = new ConcurrentHashMap<>();
    private volatile long lastTurn;
    private volatile long lastDay;
    private ConcurrentHashMap<Integer, String> runningTasks = new ConcurrentHashMap<>();
    private Throwable pauseError;

    public SimpleGPTLimitTracker(Supplier<IModerator> getModerator,
                                 Supplier<IText2Text> getText2Text,
                                 BiConsumer<IText2Text, Integer> addUsage,
                                 boolean allowMultipleThreads,
                                 org.slf4j.Logger logger) {
        this.logger = logger;
        this.getModerator = getModerator;
        this.getText2Text = getText2Text;
        this.addUsage = addUsage;
        this.executor = allowMultipleThreads ? Executors.newCachedThreadPool() : Executors.newSingleThreadExecutor();
    }

    // Change the existing constructor to delegate to the new one
    public SimpleGPTLimitTracker(GptHandler handler, boolean allowMultipleThreads, org.slf4j.Logger logger) {
        this(
                handler::getModerator,
                handler::getText2Text,
                (t2t, tokens) -> handler.getVectorDB().addUsage(t2t.getId(), tokens),
                allowMultipleThreads,
                logger
        );
    }

    @Override
    public void pause(Throwable e) {
        pauseError = e;
        paused = true;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public Throwable getPauseError() {
        return pauseError;
    }

    @Override
    public void resume() {
        lock.lock();
        try {
            pauseError = null;
            paused = false;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private String getPauseStr() {
        return (pauseError != null ? " Reason: " + pauseError.getMessage() : "");
    }

    private final List<Integer> executionTimes = new IntArrayList(100);
    private final List<Integer> executionDelays = new IntArrayList(100);

    private int getMedianExecutionTime() {
        synchronized (executionTimes) {
            return executionTimes.stream().mapToInt(i -> i).sorted().skip(executionTimes.size() / 2).findFirst().orElse(0);
        }
    }

    private double getAverageExecutionTime() {
        synchronized (executionTimes) {
            return executionTimes.stream().mapToInt(i -> i).average().orElse(0d);
        }
    }

    private double getMedianExecutionDelay() {
        synchronized (executionDelays) {
            return executionDelays.stream().mapToInt(i -> i).sorted().skip(executionDelays.size() / 2).findFirst().orElse(0);
        }
    }

    private double getAverageExecutionDelay() {
        synchronized (executionDelays) {
            return executionDelays.stream().mapToInt(i -> i).average().orElse(0d);
        }
    }

    private int getLatestExecutionTime() {
        synchronized (executionTimes) {
            if (executionTimes.isEmpty()) {
                return 0;
            }
            return executionTimes.get(executionTimes.size() - 1);
        }
    }

    private int getLatestExecutionDelay() {
        synchronized (executionDelays) {
            if (executionDelays.isEmpty()) {
                return 0;
            }
            return executionDelays.get(executionDelays.size() - 1);
        }
    }

    private void addExecutionTime(int ms) {
        synchronized (executionTimes) {
            executionTimes.add(ms);
            if (executionTimes.size() > 100) {
                executionTimes.remove(0);
            }
        }
    }

    private void addExecutionDelay(int ms) {
        synchronized (executionDelays) {
            executionDelays.add(ms);
            if (executionDelays.size() > 100) {
                executionDelays.remove(0);
            }
        }
    }

    @Override
    public String toString(GuildDB db, User user) {
        StringBuilder result = new StringBuilder();
        if (requireGuild != 0) {
            result.append("## Guild: ").append(requireGuild);
        } else {
            result.append("## PUBLIC");
        }
        result.append("\n");

        if (paused) {
            result.append("Status: `Paused` (Use: " + CM.chat.providers.resume.cmd.toSlashMention() + ")");
            if (pauseError != null) {
                result.append(" (`").append(pauseError.getMessage()).append("`)");
            }
            result.append("\n");
        } else {
            result.append("Status: `Running`\n");
        }

        try {
            if (hasPermission(db, user, false)) {
                result.append("Permission: `True`\n");
            } else {
                result.append("Permission: `False`\n");
            }
        } catch (IllegalArgumentException e) {
            result.append("Permission: `").append(e.getMessage() + "`\n");
        }

        DBNation nation = DiscordUtil.getNation(user);
        if (nation != null) {
            IText2Text t2t = getText2Text.get();
            if (t2t != null) {
//                Map<String, String> myOptions = Locutus.imp().getCommandManager().getV2().getPwgptHandler().getPlayerGPTConfig().getOptions(nation, t2t.getId());
//                if (!myOptions.isEmpty()) {
//                    result.append("Your Options: `").append(myOptions).append("`\n");
//                }
            }
            int turnUse = getUsesThisTurn(db, nation);
            int dayUse = getUsesToday(db, nation);
            result.append("User Usage: {turn: " + turnUse + (turnLimit == 0 ? "" : "/" + turnLimit) + ", day: " + dayUse + (dayLimit == 0 ? "" : "/" + dayLimit) + "}\n");
        }

        int guildUse = getUsesThisTurn(db);
        int guildDayUse = getUsesToday(db);
        result.append("Guild Usage: `{turn: " + guildUse + (guildTurnLimit == 0 ? "" : "/" + guildTurnLimit) + ", day: " + guildDayUse + (guildDayLimit == 0 ? "" : "/" + guildDayLimit) + "}`\n");

        result.append("Execution Time (ms): `{Average: " + Math.round(getAverageExecutionTime()) + ", Median: " + getMedianExecutionTime() + " , Latest: " + getLatestExecutionTime() + "}`\n");
        result.append("Execution Delay (ms): `{Average: " + Math.round(getAverageExecutionDelay()) + ", Median: " + getMedianExecutionDelay() + " , Latest: " + getLatestExecutionDelay() + "}`\n");

        return result.toString();
    }

    private final Map<Integer, Object> userLocks = new Int2ObjectOpenHashMap<>();

    @Override
    public CompletableFuture<String> submit(GuildDB db, User user, DBNation nation, String input) {
        if (paused) {
            throw new IllegalStateException("Executor is paused, cannot submit new task." + getPauseStr());
        }
        ByteBuffer moderatedBuf = nation.getMeta(NationMeta.GPT_MODERATED);
        if (moderatedBuf != null) {
            throw new IllegalStateException("No permission. Please open a ticket to appeal your automatic ban.\n" + new String(moderatedBuf.array(), StandardCharsets.ISO_8859_1) + ")");
        }


        // check if task is running via userLocks
        synchronized (userLocks) {
            Object lock = userLocks.get(nation.getId());
            if (lock != null) {
                throw new IllegalStateException("You already have a task running, please wait for it to finish.");
            }
            userLocks.put(nation.getId(), new Object());
        }
        IText2Text t2 = getText2Text.get();
        if (t2 == null) {
            synchronized (userLocks) {
                userLocks.remove(nation.getId());
            }
            throw new IllegalStateException("No Text2Text provider available at this time. Please try again later, or ask an administrator to define a larger usage limit");
        }

        addUse(db, nation);
        try {
            long start = System.currentTimeMillis();
            List<ModerationResult> modResult = getModerator.get().moderate(input);
            try {
                GPTUtil.checkThrowModeration(modResult, input);
            } catch (IllegalArgumentException e) {
                nation.setMeta(NationMeta.GPT_MODERATED, e.getMessage());
                throw e;
            }

            System.out.println("Moderation: " + modResult);
            logger.info("GPT-{}: {} - {}", db.getId(), user.getName(), input);

            Supplier<String> task = () -> {
                try {
                    long delay = System.currentTimeMillis() - start;
                    try {
                        lock.lock();
                        while (paused) {
                            condition.await();
                        }
                    } finally {
                        lock.unlock();
                    }
                    long start2 = System.currentTimeMillis();
                    String result = t2.generate(input, tokensUsed -> {
                        if (tokensUsed > 0) addUsage.accept(t2, tokensUsed);
                    });
                    long execTime = System.currentTimeMillis() - start2;
                    addExecutionTime((int) execTime);
                    addExecutionDelay((int) (delay));
                    return result;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Throwable e) {
                    pause(e);
                    throw e;
                } finally {
                    synchronized (userLocks) {
                        userLocks.remove(nation.getId());
                    }
                }
            };
            return CompletableFuture.supplyAsync(task, executor);
        } catch (Throwable t) {
            synchronized (userLocks) {
                userLocks.remove(nation.getId());
            }
            throw t;
        }
    }

    @Override
    public boolean checkAdminPermission(GuildDB db, User user, boolean throwError) {
        if (requireGuild > 0) {
            if (db.getIdLong() != requireGuild) {
                if (throwError) {
                    throw new IllegalArgumentException("This command is only available in guild " + requireGuild);
                } else {
                    return false;
                }
            }
            if (!Roles.ADMIN.has(user, db.getGuild())) {
                if (throwError) {
                    throw new IllegalArgumentException("Missing role: " + Roles.ADMIN.toDiscordRoleNameElseInstructions(db.getGuild()));
                } else {
                    return false;
                }
            }
        }
        if (!Roles.ADMIN.hasOnRoot(user)) {
            if (throwError) {
                throw new IllegalArgumentException("Missing role on the root server (" +
                        Locutus.imp().getRootDb().getGuild() + "): " +
                        Roles.ADMIN.toDiscordRoleNameElseInstructions(db.getGuild()));
            } else {
                return false;
            }
        }
        return true;
    }

    public SimpleGPTLimitTracker requireGuild(Guild guild) {
        this.requireGuild = guild.getIdLong();
        return this;
    }

    public SimpleGPTLimitTracker setTurnLimit(int limit) {
        this.turnLimit = limit;
        return this;
    }

    public SimpleGPTLimitTracker setDayLimit(int limit) {
        this.dayLimit = limit;
        return this;
    }

    public SimpleGPTLimitTracker setGuildTurnLimit(int limit) {
        this.guildTurnLimit = limit;
        return this;
    }

    public SimpleGPTLimitTracker setGuildDayLimit(int limit) {
        this.guildDayLimit = limit;
        return this;
    }

    private void resetUsage() {
        long turn = TimeUtil.getTurn();
        long day = TimeUtil.getDay();
        if (turn == lastTurn && day == lastDay) return;
        synchronized (turnUsesByNation) {
            // decrement guild uses by amount of turns passed, max it to 0 (so not negative)
            if (turn != lastTurn) {
                lastTurn = turn;

                turnUsesByGuild.replaceAll((k, v) -> Math.max(0, v - guildTurnLimit));
                turnUsesByNation.replaceAll((k, v) -> Math.max(0, v - turnLimit));
            }

            if (day != lastDay) {
                lastDay = day;

                dayUsesByGuild.replaceAll((k, v) -> Math.max(0, v - guildDayLimit));
                dayUsesByNation.replaceAll((k, v) -> Math.max(0, v - dayLimit));
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

    public int getUsesThisTurn(GuildDB db) {
        resetUsage();
        return turnUsesByGuild.getOrDefault(db.getIdLong(), 0);
    }

    public int getUsesToday(GuildDB db) {
        resetUsage();
        return dayUsesByGuild.getOrDefault(db.getIdLong(), 0);
    }

    public void addUse(GuildDB db, DBNation nation) {
        resetUsage();
        turnUsesByNation.merge(nation.getId(), 1, Integer::sum);
        dayUsesByNation.merge(nation.getId(), 1, Integer::sum);
        turnUsesByGuild.merge(db.getIdLong(), 1, Integer::sum);
        dayUsesByGuild.merge(db.getIdLong(), 1, Integer::sum);
    }

    @Override
    public boolean hasPermission(GuildDB db, User user, boolean checkLimits) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) {
            throw new IllegalArgumentException("User " + user.getName() + " must be registered to a nation. See " + CM.register.cmd.toSlashMention());
        }
        boolean isAdmin = Roles.ADMIN.hasOnRoot(user);
        if (isAdmin) {
            return true;
        }
        boolean isWhitelisted = isAdmin || Roles.AI_COMMAND_ACCESS.hasOnRoot(user);

        if (requireGuild != 0) {
            if (db.getIdLong() != requireGuild) {
                Guild other = Locutus.imp().getDiscordApi().getGuildById(requireGuild);
                String name = other == null ? "guild:" + requireGuild : other.toString();
                throw new IllegalArgumentException("The GPT provider can only be used in the `" + name + "` guild.");
            }
        }

        Member member = db.getGuild().getMember(user);
        if (member == null) {
            throw new IllegalArgumentException("Cannot find member " + user.getName() + " in guild " + db.getGuild().getName());
        }

        if (requireGuild == 0) {
            Guild guild = db.getGuild();
            OffsetDateTime created = guild.getTimeCreated();
            // require 10 days old
            if (created.plusDays(50).isAfter(OffsetDateTime.now())) {
                throw new IllegalArgumentException("The GPT provider can only be used in guilds that are at least 50 days old.");
            }
            if (isWhitelisted) {
                return true;
            }
        } else {
            if (!Roles.AI_COMMAND_ACCESS.has(member)) {
                throw new IllegalArgumentException("You do not have permission to use the GPT provider. Missing role: " + Roles.AI_COMMAND_ACCESS.toDiscordRoleNameElseInstructions(db.getGuild()));
            }
            if (Roles.ADMIN.has(member)) {
                return true;
            }
        }

        if (turnLimit != 0) {
            int usedThisTurn = getUsesThisTurn(db, nation);
            if (usedThisTurn > this.turnLimit) {
                long nexTurnMs = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() + 1);
                throw new IllegalArgumentException("You have used the GPT provider too many times this turn (" + usedThisTurn + "). Please wait until the next turn in " + DiscordUtil.timestamp(nexTurnMs, null) + ".");
            }
        }

        if (dayLimit != 0) {
            int usedToday = getUsesToday(db, nation);
            if (usedToday > this.dayLimit) {
                long nextDayMs = TimeUtil.getTimeFromDay(TimeUtil.getDay() + 1);
                throw new IllegalArgumentException("You have used the GPT provider too many times today (" + usedToday + "). Please wait until the next day in " + DiscordUtil.timestamp(nextDayMs, null) + ".");
            }
        }

        if (guildTurnLimit != 0) {
            int usedThisTurn = getUsesThisTurn(db);
            if (usedThisTurn > this.guildTurnLimit) {
                long nexTurnMs = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() + 1);
                throw new IllegalArgumentException("Your guild has used the GPT provider too many times this turn (" + usedThisTurn + "). Please wait until the next turn in " + DiscordUtil.timestamp(nexTurnMs, null) + ".");
            }
        }
        return true;
    }

    @Override
    public void setUsageLimits(int turnLimit, int dayLimit, int guildTurnLimit, int guildDayLimit) {
        this.turnLimit = turnLimit;
        this.dayLimit = dayLimit;
        this.guildTurnLimit = guildTurnLimit;
        this.guildDayLimit = guildDayLimit;
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
    }

    @Override
    public int getTurnLimit() {
        return turnLimit;
    }

    @Override
    public int getDayLimit() {
        return dayLimit;
    }

    @Override
    public int getGuildTurnLimit() {
        return guildTurnLimit;
    }

    @Override
    public int getGuildDayLimit() {
        return guildDayLimit;
    }
}
