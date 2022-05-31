package com.boydti.discord.commands.manager.dummy;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.Result;
import net.dv8tion.jda.api.utils.concurrent.DelayedCompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

public class AdapterAuditableRestAction<T> implements AuditableRestAction<T> {
    public static void setPassContext(boolean enable) {
        RestAction.setPassContext(enable);
    }

    public static boolean isPassContext() {
        return RestAction.isPassContext();
    }

    public static void setDefaultFailure(@Nullable Consumer<? super Throwable> callback) {
        RestAction.setDefaultFailure(callback);
    }

    public static void setDefaultSuccess(@Nullable Consumer<Object> callback) {
        RestAction.setDefaultSuccess(callback);
    }

    public static void setDefaultTimeout(long timeout, @NotNull TimeUnit unit) {
        RestAction.setDefaultTimeout(timeout, unit);
    }

    public static long getDefaultTimeout() {
        return RestAction.getDefaultTimeout();
    }

    @Nonnull
    public static Consumer<? super Throwable> getDefaultFailure() {
        return RestAction.getDefaultFailure();
    }

    @Nonnull
    public static Consumer<Object> getDefaultSuccess() {
        return RestAction.getDefaultSuccess();
    }

    @CheckReturnValue
    @SafeVarargs
    @Nonnull
    public static <E> RestAction<List<E>> allOf(@NotNull RestAction<? extends E> first, @NotNull RestAction<? extends E>... others) {
        return RestAction.allOf(first, others);
    }

    @CheckReturnValue
    @Nonnull
    public static <E> RestAction<List<E>> allOf(@NotNull Collection<? extends RestAction<? extends E>> restActions) {
        return RestAction.allOf(restActions);
    }

    @CheckReturnValue
    @Nonnull
    public static <E, A, O> RestAction<O> accumulate(@NotNull Collection<? extends RestAction<? extends E>> restActions, @NotNull Collector<? super E, A, ? extends O> collector) {
        return RestAction.accumulate(restActions, collector);
    }

    @Override
    @Nonnull
    public JDA getJDA() {
        return parent.getJDA();
    }

    @NotNull
    @Override
    public AuditableRestAction<T> reason(@Nullable String reason) {
        return this;
    }

    @Override
    @Nonnull
    public AdapterAuditableRestAction<T> setCheck(@Nullable BooleanSupplier checks) {
        parent.setCheck(checks);
        return this;
    }

    @Override
    @javax.annotation.Nullable
    public BooleanSupplier getCheck() {
        return parent.getCheck();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> addCheck(@NotNull BooleanSupplier checks) {
        return parent.addCheck(checks);
    }

    @Override
    @Nonnull
    public AuditableRestAction<T> timeout(long timeout, @NotNull TimeUnit unit) {
        parent.timeout(timeout, unit);
        return this;
    }

    @Override
    @Nonnull
    public AuditableRestAction<T> deadline(long timestamp) {
        parent.deadline(timestamp);
        return this;
    }

    @Override
    public void queue() {
        parent.queue();
    }

    @Override
    public void queue(@Nullable Consumer<? super T> success) {
        parent.queue(success);
    }

    @Override
    public void queue(@Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure) {
        parent.queue(success, failure);
    }

    @Override
    public T complete() {
        return parent.complete();
    }

    @Override
    public T complete(boolean shouldQueue) throws RateLimitedException {
        return parent.complete(shouldQueue);
    }

    @Override
    @Nonnull
    public CompletableFuture<T> submit() {
        return parent.submit();
    }

    @Override
    @Nonnull
    public CompletableFuture<T> submit(boolean shouldQueue) {
        return parent.submit(shouldQueue);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Result<T>> mapToResult() {
        return parent.mapToResult();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <O> RestAction<O> map(@NotNull Function<? super T, ? extends O> map) {
        return parent.map(map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> onErrorMap(@NotNull Function<? super Throwable, ? extends T> map) {
        return parent.onErrorMap(map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> onErrorMap(@Nullable Predicate<? super Throwable> condition, @NotNull Function<? super Throwable, ? extends T> map) {
        return parent.onErrorMap(condition, map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> onErrorFlatMap(@NotNull Function<? super Throwable, ? extends RestAction<? extends T>> map) {
        return parent.onErrorFlatMap(map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> onErrorFlatMap(@Nullable Predicate<? super Throwable> condition, @NotNull Function<? super Throwable, ? extends RestAction<? extends T>> map) {
        return parent.onErrorFlatMap(condition, map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <O> RestAction<O> flatMap(@NotNull Function<? super T, ? extends RestAction<O>> flatMap) {
        return parent.flatMap(flatMap);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <O> RestAction<O> flatMap(@Nullable Predicate<? super T> condition, @NotNull Function<? super T, ? extends RestAction<O>> flatMap) {
        return parent.flatMap(condition, flatMap);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <U, O> RestAction<O> and(@NotNull RestAction<U> other, @NotNull BiFunction<? super T, ? super U, ? extends O> accumulator) {
        return parent.and(other, accumulator);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <U> RestAction<Void> and(@NotNull RestAction<U> other) {
        return parent.and(other);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<List<T>> zip(@NotNull RestAction<? extends T> first, @NotNull RestAction<? extends T>... other) {
        return parent.zip(first, other);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> delay(@NotNull Duration duration) {
        return parent.delay(duration);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> delay(@NotNull Duration duration, @Nullable ScheduledExecutorService scheduler) {
        return parent.delay(duration, scheduler);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> delay(long delay, @NotNull TimeUnit unit) {
        return parent.delay(delay, unit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<T> delay(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService scheduler) {
        return parent.delay(delay, unit, scheduler);
    }

    @Override
    @Nonnull
    public DelayedCompletableFuture<T> submitAfter(long delay, @NotNull TimeUnit unit) {
        return parent.submitAfter(delay, unit);
    }

    @Override
    @Nonnull
    public DelayedCompletableFuture<T> submitAfter(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService executor) {
        return parent.submitAfter(delay, unit, executor);
    }

    @Override
    public T completeAfter(long delay, @NotNull TimeUnit unit) {
        return parent.completeAfter(delay, unit);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit) {
        return parent.queueAfter(delay, unit);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer<? super T> success) {
        return parent.queueAfter(delay, unit, success);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure) {
        return parent.queueAfter(delay, unit, success, failure);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, executor);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer<? super T> success, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, success, executor);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, success, failure, executor);
    }

    private final RestAction<T> parent;

    public AdapterAuditableRestAction(RestAction<T> parent) {
        this.parent = parent;
    }
}
