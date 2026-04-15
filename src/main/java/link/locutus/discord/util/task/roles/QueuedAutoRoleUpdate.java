package link.locutus.discord.util.task.roles;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Member;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class QueuedAutoRoleUpdate {
    private final long memberId;
    private boolean autoRole;
    private boolean cities;
    private boolean conditions;
    private boolean memberApp;
    private boolean explicitNullNation;
    @Nullable
    private DBNation latestNation;
    private boolean taxRole;
    @Nullable
    private TaxBracket latestTaxBracket;
    private final List<CompletableFuture<AutoRoleInfo>> autoRoleFutures = new ArrayList<>();

    QueuedAutoRoleUpdate(long memberId) {
        this.memberId = memberId;
    }

    public long getMemberId() {
        return memberId;
    }

    public boolean isAutoRole() {
        return autoRole;
    }

    public boolean isCities() {
        return cities;
    }

    public boolean isConditions() {
        return conditions;
    }

    public boolean isMemberApp() {
        return memberApp;
    }

    public boolean isTaxRole() {
        return taxRole;
    }

    @Nullable
    public TaxBracket getLatestTaxBracket() {
        return latestTaxBracket;
    }

    public void mergeAutoRole(@Nullable DBNation nation) {
        autoRole = true;
        cities = false;
        conditions = false;
        memberApp = false;
        mergeNation(nation);
    }

    public void addAutoRoleFuture(CompletableFuture<AutoRoleInfo> future) {
        if (future != null) {
            autoRoleFutures.add(future);
        }
    }

    public void mergeCities(@Nullable DBNation nation) {
        if (!autoRole) {
            cities = true;
        }
        mergeNation(nation);
    }

    public void mergeConditions(@Nullable DBNation nation) {
        if (!autoRole) {
            conditions = true;
        }
        mergeNation(nation);
    }

    public void mergeMemberApp(@Nullable DBNation nation) {
        if (!autoRole) {
            memberApp = true;
        }
        mergeNation(nation);
    }

    public void mergeTaxRole(@Nullable TaxBracket bracket) {
        taxRole = true;
        latestTaxBracket = bracket;
    }

    @Nullable
    public DBNation resolveNation(Member member) {
        if (explicitNullNation) {
            return null;
        }
        DBNation currentNation = DiscordUtil.getNation(member.getIdLong());
        return currentNation != null ? currentNation : latestNation;
    }

    private void mergeNation(@Nullable DBNation nation) {
        if (nation == null) {
            explicitNullNation = true;
            latestNation = null;
            return;
        }
        explicitNullNation = false;
        latestNation = nation;
    }

    public void completeAutoRoleFutures(@Nullable AutoRoleInfo info, @Nullable Throwable error) {
        if (autoRoleFutures.isEmpty()) {
            return;
        }
        if (error != null) {
            for (CompletableFuture<AutoRoleInfo> future : autoRoleFutures) {
                future.completeExceptionally(error);
            }
        } else {
            for (CompletableFuture<AutoRoleInfo> future : autoRoleFutures) {
                future.complete(info);
            }
        }
        autoRoleFutures.clear();
    }
}
