package link.locutus.discord.commands.manager.v2.impl.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.managers.Manager;
import net.dv8tion.jda.api.managers.channel.ChannelManager;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.Result;
import net.dv8tion.jda.api.utils.concurrent.DelayedCompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

public class ChannelManagerDelegate {
    protected final ChannelManager parent;

    public ChannelManagerDelegate(ChannelManager parent) {
        this.parent = parent;
    }

    @Nonnull
    public ChannelManager reset(long fields) {
        return parent.reset(fields);
    }

    @Nonnull
    public ChannelManager reset(long... fields) {
        return parent.reset(fields);
    }

    @Nonnull
    public GuildChannel getChannel() {
        return parent.getChannel();
    }

    @Nonnull
    public Guild getGuild() {
        return parent.getGuild();
    }

    @CheckReturnValue
    @Nonnull
    public ChannelManager setName(@NotNull String name) {
        return parent.setName(name);
    }

    @Nonnull
    public Manager setCheck(BooleanSupplier checks) {
        return parent.setCheck(checks);
    }

    @Nonnull
    public Manager timeout(long timeout, @NotNull TimeUnit unit) {
        return parent.timeout(timeout, unit);
    }

    @Nonnull
    public Manager deadline(long timestamp) {
        return parent.deadline(timestamp);
    }

    @CheckReturnValue
    @Nonnull
    public Manager reset() {
        return parent.reset();
    }

    @Nonnull
    public AuditableRestAction reason(@Nullable String reason) {
        return parent.reason(reason);
    }

    @Nonnull
    public JDA getJDA() {
        return parent.getJDA();
    }

    @javax.annotation.Nullable
    public BooleanSupplier getCheck() {
        return parent.getCheck();
    }

    @CheckReturnValue
    @Nonnull
    public RestAction addCheck(@NotNull BooleanSupplier checks) {
        return parent.addCheck(checks);
    }

    public void queue() {
        parent.queue();
    }

    public void queue(@Nullable Consumer success) {
        parent.queue(success);
    }

    public void queue(@Nullable Consumer success, @Nullable Consumer failure) {
        parent.queue(success, failure);
    }

    public Object complete() {
        return parent.complete();
    }

    public Object complete(boolean shouldQueue) throws RateLimitedException {
        return parent.complete(shouldQueue);
    }

    @Nonnull
    public CompletableFuture submit() {
        return parent.submit();
    }

    @Nonnull
    public CompletableFuture submit(boolean shouldQueue) {
        return parent.submit(shouldQueue);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction<Result> mapToResult() {
        return parent.mapToResult();
    }

    @CheckReturnValue
    @Nonnull
    public RestAction map(@NotNull Function map) {
        return parent.map(map);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction onErrorMap(@NotNull Function map) {
        return parent.onErrorMap(map);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction onErrorMap(@Nullable Predicate condition, @NotNull Function map) {
        return parent.onErrorMap(condition, map);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction onErrorFlatMap(@NotNull Function map) {
        return parent.onErrorFlatMap(map);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction onErrorFlatMap(@Nullable Predicate condition, @NotNull Function map) {
        return parent.onErrorFlatMap(condition, map);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction flatMap(@NotNull Function flatMap) {
        return parent.flatMap(flatMap);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction flatMap(@Nullable Predicate condition, @NotNull Function flatMap) {
        return parent.flatMap(condition, flatMap);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction and(@NotNull RestAction other, @NotNull BiFunction accumulator) {
        return parent.and(other, accumulator);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction<Void> and(@NotNull RestAction other) {
        return parent.and(other);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction<List> zip(@NotNull RestAction first, @NotNull RestAction[] other) {
        return parent.zip(first, other);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction delay(@NotNull Duration duration) {
        return parent.delay(duration);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction delay(@NotNull Duration duration, @Nullable ScheduledExecutorService scheduler) {
        return parent.delay(duration, scheduler);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction delay(long delay, @NotNull TimeUnit unit) {
        return parent.delay(delay, unit);
    }

    @CheckReturnValue
    @Nonnull
    public RestAction delay(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService scheduler) {
        return parent.delay(delay, unit, scheduler);
    }

    @Nonnull
    public DelayedCompletableFuture submitAfter(long delay, @NotNull TimeUnit unit) {
        return parent.submitAfter(delay, unit);
    }

    @Nonnull
    public DelayedCompletableFuture submitAfter(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService executor) {
        return parent.submitAfter(delay, unit, executor);
    }

    public Object completeAfter(long delay, @NotNull TimeUnit unit) {
        return parent.completeAfter(delay, unit);
    }

    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit) {
        return parent.queueAfter(delay, unit);
    }

    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer success) {
        return parent.queueAfter(delay, unit, success);
    }

    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer success, @Nullable Consumer failure) {
        return parent.queueAfter(delay, unit, success, failure);
    }

    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, executor);
    }

    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer success, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, success, executor);
    }

    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer success, @Nullable Consumer failure, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, success, failure, executor);
    }
}
