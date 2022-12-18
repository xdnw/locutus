package link.locutus.discord.util.task;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.JsonUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DepositRawTask implements Callable<Map<DBNation, Map<ResourceType, Double>>> {
    private final int allianceId;
    private final Consumer<String> update;
    private final double daysDefault;
    private final Consumer<String> errors;
    private final boolean useExisting;
    private final boolean ignoreInactives;
    private List<DBNation> nations;
    private boolean force = false;

    public DepositRawTask setForce(boolean force) {
        this.force = force;
        return this;
    }

    public DepositRawTask(Collection<DBNation> nations, int allianceId, Consumer<String> update, double daysDefault, boolean useExisting, boolean ignoreInactives, Consumer<String> errors) {
        this.allianceId = allianceId;
        this.update = update;
        this.daysDefault = daysDefault;
        this.nations = new ArrayList<>(nations);
        this.nations.removeIf(n -> n.getPosition() <= 1 || n.getVm_turns() != 0 || n.getActive_m() > TimeUnit.DAYS.toMinutes(5));
        this.useExisting = useExisting;
        this.errors = errors;
        this.ignoreInactives = ignoreInactives;
    }

    @Override
    public Map<DBNation, Map<ResourceType, Double>> call() throws InterruptedException, ExecutionException, IOException {
        Map<DBNation, Map<ResourceType, Double>> nationResourcesNeed;
        GetResourceNeeded rssTask = new GetResourceNeeded(nations, allianceId, update, daysDefault, useExisting, errors).setForce(force);
        nationResourcesNeed = rssTask.call();

        Map<ResourceType, Double> total = new HashMap<>();
        Map<DBNation, Map<ResourceType, Double>> toSend = new HashMap<>();

        List<String> postScript = new ArrayList<>();
        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : nationResourcesNeed.entrySet()) {
            DBNation nation = entry.getKey();
            Map<ResourceType, Double> resources = entry.getValue();
            if (resources.getOrDefault(ResourceType.CREDITS, 0d) != 0) {
                errors.accept(nation.getNation() + " has disabled alliance access to resource information (account page)");
                continue;
            }
            if (((nation.isGray() && nation.getOff() == 0) || nation.getActive_m() > TimeUnit.DAYS.toMinutes(4)) && ignoreInactives) {
                errors.accept(nation.getNation() + " is inactive");
                continue;
            }
            if (nation.isBeige() && nation.getCities() <= 4 && !force) {
                errors.accept(nation.getNation() + " is beige");
                continue;
            }
            Map<ResourceType, Double> nationTotal = entry.getValue();
            if (PnwUtil.convertedTotal(nationTotal) == 0) {
                errors.accept(nation.getNation() + " Does not need to be sent any funds");
                continue;
            }

            postScript.add(PnwUtil.getPostScript(nation.getNation(), true, entry.getValue(), "#raws not for resale"));
            total = PnwUtil.add(total, nationTotal);
            toSend.put(nation, entry.getValue());
        }

        String arr = JsonUtil.toPrettyFormat("[" + StringMan.join(postScript, ",") + "]");

        return toSend;
    }
}
