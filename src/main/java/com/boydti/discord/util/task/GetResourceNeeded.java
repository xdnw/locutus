package com.boydti.discord.util.task;

import com.boydti.discord.Locutus;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.apiv1.enums.ResourceType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GetResourceNeeded implements Callable<Map<DBNation, Map<ResourceType, Double>>> {
    private final int allianceId;
    private final Consumer<String> update;
    private final double daysDefault;
    private final boolean useExisting;
    private final Consumer<String> errors;
    private List<DBNation> nations;
    private boolean force = false;

    public GetResourceNeeded(int allianceId, Consumer<String> update, double daysDefault, boolean useExisting, Consumer<String> errors) {
        this(
                new ArrayList<>(Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId))),
                allianceId,
                update,
                daysDefault,
                useExisting,
                errors
        );
    }

    public GetResourceNeeded(Collection<DBNation> nations, int allianceId, Consumer<String> update, double daysDefault, boolean useExisting, Consumer<String> errors) {
        this.allianceId = allianceId;
        this.update = update;
        this.daysDefault = daysDefault;
        this.nations = new ArrayList<>(nations);
        this.nations.removeIf(n -> n.getPosition() <= 1 || n.getVm_turns() != 0 || n.getActive_m() > TimeUnit.DAYS.toMinutes(5));
        this.useExisting = useExisting;
        this.errors = errors;
    }

    public GetResourceNeeded setForce(boolean force) {
        this.force = force;
        return this;
    }

    @Override
    public Map<DBNation, Map<ResourceType, Double>> call() throws InterruptedException, ExecutionException, IOException {
        // Get existing resources
        Map<Integer, Map<ResourceType, Double>> existing;
        if (this.useExisting) {
            existing = new GetMemberResources(allianceId).call();
        } else {
            existing = new HashMap<>();
            for (DBNation nation : nations) {
                existing.put(nation.getNation_id(), new HashMap<>());
            }
        }
        Map<DBNation, Map<ResourceType, Double>> result = new HashMap<>();
        for (DBNation nation : nations) {
            Map<ResourceType, Double> stockpile = existing.get(nation.getNation_id());
            if (stockpile == null) {
                errors.accept("Unable to access stockpile for: " + nation.getNation());
                continue;
            }
            Map<ResourceType, Double> needed = nation.getResourcesNeeded(stockpile, daysDefault, force);
            if (!needed.isEmpty()) {
                result.put(nation, needed);
            }
        }

        return result;
    }
}
