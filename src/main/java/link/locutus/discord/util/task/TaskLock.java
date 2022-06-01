package link.locutus.discord.util.task;

import link.locutus.discord.util.AlertUtil;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

public class TaskLock implements Runnable {
    private final ReentrantLock lock;
    private final Callable task;

    public TaskLock(Callable task) {
        this.lock = new ReentrantLock();
        this.task = task;
    }

    @Override
    public void run() {
        if (lock.tryLock()) {
            try {
                task.call();
            } catch (Throwable e) {
                e.printStackTrace();
                AlertUtil.error(e.getMessage(), e);
            } finally {
                lock.unlock();
            }
        }
    }
}
