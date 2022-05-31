package com.boydti.discord.commands.manager;

import java.util.concurrent.locks.ReentrantLock;

public class CommandLock {
    private final ReentrantLock lock = new ReentrantLock();
    private Thread thread;

    public ReentrantLock getLock() {
        return lock;
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public Thread getThread() {
        return thread;
    }
}
