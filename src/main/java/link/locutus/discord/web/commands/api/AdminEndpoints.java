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
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.user.Roles;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import link.locutus.discord.web.commands.binding.value_types.WebSettingValidationCheapness;
import link.locutus.discord.web.commands.binding.value_types.WebSettingValidationErrors;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Command(desc = "Validate the current local values for selected guild settings; missing keys are valid or unset", viewable = true)
    @RolePermission(Roles.ADMIN)
    @ReturnType(WebSettingValidationErrors.class)
    public WebSettingValidationErrors settings_validation_errors(@Me GuildDB db,
                                                                 @Me User user,
                                                                 Set<GuildSetting> settings) {
        Map<Integer, String> errors = new LinkedHashMap<>();
        if (settings == null || settings.isEmpty()) {
            return new WebSettingValidationErrors(errors);
        }

        for (GuildSetting setting : orderedSettings(settings)) {
            int ordinal = setting.getOrdinal();
            if (ordinal < 0) {
                continue;
            }
            String error = setting.getValidationError(db, user, false);
            if (error != null) {
                errors.put(ordinal, error);
            }
        }
        return new WebSettingValidationErrors(errors);
    }

    @Command(desc = "Report whether selected guild settings can be revalidated without expensive remote calls", viewable = true)
    @RolePermission(Roles.ADMIN)
    @ReturnType(WebSettingValidationCheapness.class)
    public WebSettingValidationCheapness settings_validation_cheapness(@Me GuildDB db,
                                                                       Set<GuildSetting> settings) {
        Map<Integer, Boolean> isCheap = new LinkedHashMap<>();
        if (settings == null || settings.isEmpty()) {
            return new WebSettingValidationCheapness(isCheap);
        }

        for (GuildSetting setting : orderedSettings(settings)) {
            int ordinal = setting.getOrdinal();
            if (ordinal < 0) {
                continue;
            }
            isCheap.put(ordinal, setting.isValidationCheap());
        }
        return new WebSettingValidationCheapness(isCheap);
    }

    private static List<GuildSetting> orderedSettings(Set<GuildSetting> settings) {
        List<GuildSetting> ordered = new ArrayList<>(settings);
        ordered.sort(Comparator.comparingInt(GuildSetting::getOrdinal));
        return ordered;
    }
}
