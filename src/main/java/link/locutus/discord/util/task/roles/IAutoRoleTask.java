package link.locutus.discord.util.task.roles;

import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.pnw.DBNation;
import net.dv8tion.jda.api.entities.Member;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IAutoRoleTask {
    void autoRoleCities(Member member, Supplier<DBNation> nationSup, Consumer<String> output, Consumer<Future> tasks);

    void updateTaxRoles(Map<DBNation, TaxBracket> brackets);

    void updateTaxRole(Member member, TaxBracket bracket);

    void autoRoleAll(Consumer<String> output);

    void autoRole(Member member, Consumer<String> output);

    void syncDB();
}
