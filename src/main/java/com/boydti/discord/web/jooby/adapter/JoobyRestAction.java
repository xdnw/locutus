package com.boydti.discord.web.jooby.adapter;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.RestAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class JoobyRestAction implements RestAction<Void> {
    private final Runnable task;
    private final JDA jda;
    private BooleanSupplier check;

    public JoobyRestAction(JDA jda, Runnable task) {
        this.task = task;
        this.jda = jda;
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return jda;
    }

    @Nonnull
    @Override
    public RestAction<Void> setCheck(@Nullable BooleanSupplier checks) {
        this.check = checks;
        return this;
    }

    @Override
    public void queue(@Nullable Consumer<? super Void> success, @Nullable Consumer<? super Throwable> failure) {
        complete(true);
    }

    @Override
    public Void complete(boolean shouldQueue) {
        try {
            return submit(shouldQueue).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nonnull
    @Override
    public CompletableFuture<Void> submit(boolean shouldQueue) {
        if (check != null) {
            if (!check.getAsBoolean()) throw new IllegalStateException("Check failed");
        }
        task.run();
        return CompletableFuture.completedFuture(null);
    }
}
