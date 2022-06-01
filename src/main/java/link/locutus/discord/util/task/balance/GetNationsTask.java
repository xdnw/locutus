package link.locutus.discord.util.task.balance;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.SNationContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class GetNationsTask implements Callable<Map<Integer, SNationContainer>> {
    @Override
    public Map<Integer, SNationContainer> call() throws Exception {
        List<SNationContainer> nations = Locutus.imp().getPnwApi().getNations().getNationsContainer();

        Map<Integer, SNationContainer> map = new HashMap<>();
        for (SNationContainer nation : nations) {
            map.put(nation.getNationid(), nation);
        }

        return map;
    }
}
