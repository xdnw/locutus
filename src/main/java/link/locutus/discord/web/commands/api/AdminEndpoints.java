package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ErrorSample;
import link.locutus.discord._main.RepeatingTasks;
import link.locutus.discord._main.RunHistorySnapshot;
import link.locutus.discord._main.TaskDetails;
import link.locutus.discord._main.TaskList;
import link.locutus.discord._main.TaskSummary;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.user.Roles;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdminEndpoints {
    @Command(viewable = true)
    @RolePermission(value = Roles.ADMIN, root = true)
    @ReturnType(value = TaskList.class, cache = CacheType.SessionStorage, duration = 15)
    public TaskList locutus_tasks() {
        RepeatingTasks rt = Locutus.imp().getRepeatingTasks();

        TaskList res = new TaskList();
        res.values = rt.getAllSummaries();
        return res;
    }

    @Command(viewable = true)
    @RolePermission(value = Roles.ADMIN, root = true)
    @ReturnType(value = TaskDetails.class, cache = CacheType.SessionStorage, duration = 15)
    public TaskDetails locutus_task(int id) {
        RepeatingTasks rt = Locutus.imp().getRepeatingTasks();

        long now = System.currentTimeMillis();
        long since = now - TimeUnit.HOURS.toMillis(24);

        TaskSummary summary = rt.getSummary(id);

        TaskDetails res = new TaskDetails();
        res.found = summary != null;
        res.summary = summary;

        res.errors = rt.getRecentDistinctErrors(id);
        res.sinceMs = since;
        res.history = rt.getRunHistorySince(id, since);

        return res;
    }
}
