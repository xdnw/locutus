package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ErrorSample;
import link.locutus.discord._main.RepeatingTasks;
import link.locutus.discord._main.RunHistorySnapshot;
import link.locutus.discord._main.TaskDetails;
import link.locutus.discord._main.TaskList;
import link.locutus.discord._main.TaskSummary;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.user.Roles;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdminEndpoints {
    @Command(desc = "List repeating tasks, hiding errors for non-admins", viewable = true)
    @ReturnType(value = TaskList.class, cache = CacheType.SessionStorage, duration = 15)
    public TaskList locutus_tasks(@Me @Default User user) {
        boolean isAdmin = user != null && Roles.ADMIN.hasOnRoot(user);
        RepeatingTasks rt = Locutus.imp().getRepeatingTasks();

        TaskList res = new TaskList();
        if (!isAdmin) {
            res.values = rt.getAllSummaries().stream()
                    .map(TaskSummary::stripError).toList();
        } else {
            res.values = rt.getAllSummaries();
        }
        return res;
    }

    @Command(desc = "Get detailed information and recent history for a specific repeating task", viewable = true)
    @ReturnType(value = TaskDetails.class, cache = CacheType.SessionStorage, duration = 15)
    public TaskDetails locutus_task(@Me @Default User user, int id) {
        boolean isAdmin = user != null && Roles.ADMIN.hasOnRoot(user);

        RepeatingTasks rt = Locutus.imp().getRepeatingTasks();

        long now = System.currentTimeMillis();
        long since = now - TimeUnit.HOURS.toMillis(24);

        TaskSummary summary = rt.getSummary(id);
        if (!isAdmin && summary != null) summary = summary.stripError();

        TaskDetails res = new TaskDetails();
        res.found = summary != null;
        res.summary = summary;

        res.errors = isAdmin ? rt.getRecentDistinctErrors(id) : List.of();
        res.sinceMs = since;
        res.history = rt.getRunHistorySince(id, since);

        return res;
    }
}
