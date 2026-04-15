package link.locutus.discord.util.task.roles;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import net.dv8tion.jda.api.entities.Member;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class CoalescingAutoRoleTask implements IAutoRoleTask {
    private static final long COALESCE_DELAY_MS = 100;

    private final AutoRoleTask delegate;
    private final Object pendingLock = new Object();
    private final Map<Long, QueuedAutoRoleUpdate> pendingByMember = new LinkedHashMap<>();
    private boolean flushScheduled;

    public CoalescingAutoRoleTask(AutoRoleTask delegate) {
        this.delegate = delegate;
    }

    @Override
    public AutoRoleInfo autoRoleCities(Member member, DBNation nation) {
        return delegate.autoRoleCities(member, nation);
    }

    @Override
    public AutoRoleInfo autoRoleConditions(Member member, DBNation nation) {
        return delegate.autoRoleConditions(member, nation);
    }

    @Override
    public AutoRoleInfo autoRoleMemberApp(Member member, DBNation nation) {
        return delegate.autoRoleMemberApp(member, nation);
    }

    @Override
    public AutoRoleInfo updateTaxRoles(Map<DBNation, TaxBracket> brackets) {
        return delegate.updateTaxRoles(brackets);
    }

    @Override
    public AutoRoleInfo updateTaxRole(Member member, TaxBracket bracket) {
        return delegate.updateTaxRole(member, bracket);
    }

    @Override
    public AutoRoleInfo autoRoleAll() {
        return delegate.autoRoleAll();
    }

    @Override
    public AutoRoleInfo autoRole(Member member, DBNation nation) {
        return delegate.autoRole(member, nation);
    }

    @Override
    public void autoRoleCitiesAsync(Member member, DBNation nation) {
        enqueue(member, update -> update.mergeCities(nation));
    }

    @Override
    public void autoRoleConditionsAsync(Member member, DBNation nation) {
        enqueue(member, update -> update.mergeConditions(nation));
    }

    @Override
    public void autoRoleMemberAppAsync(Member member, DBNation nation) {
        enqueue(member, update -> update.mergeMemberApp(nation));
    }

    @Override
    public void updateTaxRoleAsync(Member member, TaxBracket bracket) {
        enqueue(member, update -> update.mergeTaxRole(bracket));
    }

    @Override
    public CompletableFuture<AutoRoleInfo> autoRoleAsync(Member member, DBNation nation) {
        if (member == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<AutoRoleInfo> future = new CompletableFuture<>();
        enqueue(member, update -> {
            update.mergeAutoRole(nation);
            update.addAutoRoleFuture(future);
        });
        return future;
    }

    @Override
    public Function<Integer, Boolean> getAllowedAlliances() {
        return delegate.getAllowedAlliances();
    }

    @Override
    public AutoRoleSyncState syncDB() {
        return delegate.syncDB();
    }

    private void enqueue(Member member, java.util.function.Consumer<QueuedAutoRoleUpdate> updateConsumer) {
        if (member == null) {
            return;
        }
        synchronized (pendingLock) {
            QueuedAutoRoleUpdate update = pendingByMember.computeIfAbsent(member.getIdLong(), QueuedAutoRoleUpdate::new);
            updateConsumer.accept(update);
            if (flushScheduled) {
                return;
            }
            flushScheduled = true;
        }
        Locutus.imp().getScheduler().schedule(CaughtRunnable.wrap((Runnable) this::flushPending), COALESCE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void flushPending() {
        while (true) {
            Map<Long, QueuedAutoRoleUpdate> batch;
            synchronized (pendingLock) {
                if (pendingByMember.isEmpty()) {
                    flushScheduled = false;
                    return;
                }
                batch = new LinkedHashMap<>(pendingByMember);
                pendingByMember.clear();
            }

            AutoRoleInfo info = null;
            Throwable failure = null;
            try {
                info = delegate.planQueuedUpdates(batch.values());
                info.execute();
            } catch (Throwable e) {
                failure = e;
                e.printStackTrace();
            } finally {
                for (QueuedAutoRoleUpdate update : batch.values()) {
                    update.completeAutoRoleFutures(info, failure);
                }
            }
        }
    }
}
