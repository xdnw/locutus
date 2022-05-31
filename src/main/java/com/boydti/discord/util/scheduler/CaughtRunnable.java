package com.boydti.discord.util.scheduler;

import com.boydti.discord.util.AlertUtil;

import java.util.concurrent.Callable;

public abstract class CaughtRunnable implements Runnable {
    @Override
    public final void run() {
        try {
            runUnsafe();
        } catch (Throwable e) {
            e.printStackTrace();
            AlertUtil.error(e.getMessage(), e);
        }
    }

    public abstract void runUnsafe() throws Exception;

    public static Runnable wrap(Runnable runnable) {
        if (runnable instanceof CaughtRunnable) return runnable;
        return new CaughtRunnable() {
            @Override
            public void runUnsafe() {
                runnable.run();;
            }
        };
    }

    public static Runnable wrap(Callable callable) {
        return new CaughtRunnable() {
            @Override
            public void runUnsafe() throws Exception {
                callable.call();
            }
        };
    }
}
