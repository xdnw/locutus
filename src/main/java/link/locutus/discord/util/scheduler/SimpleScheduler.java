package link.locutus.discord.util.scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

public class SimpleScheduler implements Scheduler {
    private static final TaskHolder nullTaskHolder = new TaskHolder(null);
    private final ForkJoinPool pool;
    private final ExecutorService executor;
    private Thread mainThread;
    private Task head;
    private Task tail;

    public SimpleScheduler() {
        this.pool = new ForkJoinPool();
        this.executor = Executors.newCachedThreadPool();
        this.mainThread = Thread.currentThread();
    }

    public boolean isMainThread() {
        return Thread.currentThread() == mainThread;
    }

    @Override
    public Future<?> asyncShort(Runnable task) {
        return pool.submit(task);
    }

    @Override
    public <V> Future<V> asyncShort(Callable<V> task) {
        return pool.submit(task);
    }

    @Override
    public Future<?> async(Runnable task) {
        return executor.submit(task);
    }

    @Override
    public <V> Future<V> async(Callable<V> task) {
        return executor.submit(task);
    }

    private void addTask(Task task) {
        synchronized (this) {
            if (tail == null) {
                head = task;
                tail = task;
            } else {
                tail.next = task;
                tail = task;
            }
        }
    }

    public void runSyncTasks(long time) {
        synchronized (this) {
            long start = System.nanoTime();
            do {
                try {
                    Object result = head.call();
                    if (result == null) {
                        head.result = nullTaskHolder;
                    } else {
                        head.result = result;
                    }
                } catch (Throwable ex) {
                    head.result = new TaskHolder(ex);
                }
                synchronized (head) {
                    head.notifyAll();
                }
                head = head.next;
                if (head == null) {
                    tail = null;
                    break;
                }
            } while (System.nanoTime() - start < time);
        }
    }

    public void tick(long time) {
        mainThread = Thread.currentThread();
        runSyncTasks(time);
    }

    public <V> V sync(Callable<V> task) {
        try {
            if (isMainThread()) return task.call();

            CallableTask<V> syncTask = new CallableTask<V>(task);
            addTask(syncTask);
            synchronized (syncTask) {
                syncTask.wait();
            }
            Object result = syncTask.result;
            if (result instanceof TaskHolder) {
                Throwable ex = ((TaskHolder) result).ex;
                if (ex == null) return null;
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
            return (V) result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void sync(Runnable task) {
        try {
            if (isMainThread()) {
                task.run();
                return;
            } else {
                RunnableTask syncTask = new RunnableTask(task);
                addTask(syncTask);
                synchronized (syncTask) {
                    syncTask.wait();
                }
                Object result = syncTask.result;
                if (result instanceof TaskHolder) {
                    Throwable ex = ((TaskHolder) result).ex;
                    if (ex == null) return;
                    if (ex instanceof RuntimeException) {
                        throw (RuntimeException) ex;
                    }
                    throw new RuntimeException(ex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class TaskHolder {
        private final Throwable ex;

        public TaskHolder(Throwable ex) {
            this.ex = ex;
        }
    }

    private static abstract class Task<V> implements Callable<V> {
        protected volatile Object result;
        protected volatile Task next;
    }

    private final static class RunnableTask extends Task<Void> {
        private final Runnable task;

        public RunnableTask(Runnable task) {
            this.task = task;
        }

        @Override
        public Void call() {
            task.run();
            return null;
        }
    }

    private final static class CallableTask<V> extends Task {
        private final Callable<V> task;

        public CallableTask(Callable<V> task) {
            this.task = task;
        }

        @Override
        public V call() throws Exception {
            return task.call();
        }
    }
}
