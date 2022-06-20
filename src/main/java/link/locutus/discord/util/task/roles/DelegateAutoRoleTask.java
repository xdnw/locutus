package link.locutus.discord.util.task.roles;

import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Member;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DelegateAutoRoleTask implements IAutoRoleTask {
    private final IAutoRoleTask task;

    @Override
    public void autoRoleCities(Member member, Supplier<DBNation> nationSup, Consumer<String> output, Consumer<Future> tasks) {
        task.autoRoleCities(member, nationSup, output, tasks);
    }

    @Override
    public void updateTaxRoles(Map<DBNation, TaxBracket> brackets) {
        task.updateTaxRoles(brackets);
    }

    @Override
    public void updateTaxRole(Member member, TaxBracket bracket) {
        task.updateTaxRole(member, bracket);
    }

    @Override
    public void autoRoleAll(Consumer<String> output) {
        task.autoRoleAll(output);
    }

    @Override
    public void autoRole(Member member, Consumer<String> output) {
        task.autoRole(member, output);
    }

    @Override
    public void syncDB() {
        task.syncDB();
    }

    public DelegateAutoRoleTask(IAutoRoleTask task) {
        this.task = task;
    }
}
