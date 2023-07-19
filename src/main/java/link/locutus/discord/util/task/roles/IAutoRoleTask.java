package link.locutus.discord.util.task.roles;

import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Member;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IAutoRoleTask {
    AutoRoleInfo autoRoleCities(Member member, DBNation nation);

    AutoRoleInfo updateTaxRoles(Map<DBNation, TaxBracket> brackets);

    AutoRoleInfo updateTaxRole(Member member, TaxBracket bracket);

    AutoRoleInfo autoRoleAll();

    AutoRoleInfo autoRole(Member member, DBNation nation);

    String syncDB();
}
