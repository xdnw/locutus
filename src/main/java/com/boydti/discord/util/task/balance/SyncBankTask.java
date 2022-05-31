package com.boydti.discord.util.task.balance;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.bank.SyncBanks;
import com.boydti.discord.db.entities.Transfer;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.TimeUtil;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

public class SyncBankTask implements Callable<Boolean>, Runnable {
    private final Consumer<String> update;

    public SyncBankTask(Consumer<String> updates) {
        this.update = updates;
    }

    public SyncBankTask() {
        this(System.out::println);
    }

    @Override
    public Boolean call() throws Exception {
        update.accept("Fetching nations");

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();

        update.accept("Fetching transfers");

        ArrayDeque<Future<List<Transfer>>> tasks = new ArrayDeque<>();
        int maxSize = 32;

        Boolean result = TimeUtil.runTurnTask(SyncBanks.class.getSimpleName(), new Function<Long, Boolean>() {
            long last = System.currentTimeMillis();

            @Override
            public Boolean apply(Long turns) {
                if (tasks.size() > maxSize) {
                    try {
                        tasks.poll().get();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
                int cutoffMins = (int) (Math.min(48, turns) * 60 * 2 + 30);
                int size = nations.size();
                int done = 0;
                for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
                    done++;
                    DBNation nation = entry.getValue();
                    if (nation.getActive_m() < cutoffMins) {
                        if (-last + (last = System.currentTimeMillis()  ) > 2000) {
                            update.accept("(" + done + "/" + size + ") Fetching transactions for " + entry.getValue().getNation());
                        }
                        nation.getTransactions(1);
                    }
                }

                return true;
            }
        });

        while (!tasks.isEmpty()) {
            tasks.poll().get();
        }

        update.accept("Done!");
        return result != null;
    }

    @Override
    public void run() {
        try {
            call();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
