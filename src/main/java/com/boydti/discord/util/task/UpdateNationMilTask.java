package com.boydti.discord.util.task;

import com.boydti.discord.Locutus;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.apiv1.domains.subdomains.NationMilitaryContainer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class UpdateNationMilTask implements Callable<Boolean> {

    private final Map<Integer, DBNation> nations;

    public UpdateNationMilTask(Map<Integer, DBNation> nations) {
        this.nations = nations;
    }

    @Override
    public Boolean call() throws IOException {
        List<NationMilitaryContainer> militaries = Locutus.imp().getPnwApi().getAllMilitaries().getNationMilitaries();
        for (NationMilitaryContainer military : militaries) {
            int nationId = military.getNationId();
            DBNation nation = nations.get(nationId);
            if (nation == null) {
                continue;
            }

            nation.setSoldiers(military.getSoldiers());
            nation.setTanks(military.getTanks());
            nation.setAircraft(military.getAircraft());
            nation.setShips(military.getShips());
            nation.setMissiles(military.getMissiles());
            nation.setNukes(military.getNukes());
        }
        return true;
    }
}
