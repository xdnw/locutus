package link.locutus.discord.util.task.roles;

import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Member;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface IAutoRoleTask {
    AutoRoleInfo autoRoleCities(Member member, DBNation nation);
    AutoRoleInfo autoRoleConditions(Member member, DBNation nation);
    AutoRoleInfo autoRoleMemberApp(Member member, DBNation nation);

    AutoRoleInfo updateTaxRoles(Map<DBNation, TaxBracket> brackets);

    AutoRoleInfo updateTaxRole(Member member, TaxBracket bracket);

    AutoRoleInfo autoRoleAll();

    AutoRoleInfo autoRole(Member member, DBNation nation);

    void autoRoleCitiesAsync(Member member, DBNation nation);

    void autoRoleConditionsAsync(Member member, DBNation nation);

    void autoRoleMemberAppAsync(Member member, DBNation nation);

    void updateTaxRoleAsync(Member member, TaxBracket bracket);

    CompletableFuture<AutoRoleInfo> autoRoleAsync(Member member, DBNation nation);

    Function<Integer, Boolean> getAllowedAlliances();

    AutoRoleSyncState syncDB();
}
