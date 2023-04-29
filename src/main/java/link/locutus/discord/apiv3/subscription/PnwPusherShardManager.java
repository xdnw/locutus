package link.locutus.discord.apiv3.subscription;

import com.politicsandwar.graphql.model.Alliance;
import com.politicsandwar.graphql.model.City;
import com.politicsandwar.graphql.model.Nation;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import graphql.GraphQLException;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.SpyTracker;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PnwPusherShardManager {
    private PnwPusherHandler root;
    private SpyTracker spyTracker;

    public PnwPusherShardManager() {
//        this.allianceHandlers = new ConcurrentHashMap<>();


        /**
         * Spy tracker in Locutus class
         * on pusher update, update spy tracker
         *
         * run spy tracker at interval
         *
         */
    }

    public void load() {
        this.root = new PnwPusherHandler().connect(null, new Consumer<ConnectionStateChange>() {
            private long lastFetch = System.currentTimeMillis();
            @Override
            public void accept(ConnectionStateChange connectionStateChange) {
                if (spyTracker != null && connectionStateChange.getPreviousState() == ConnectionState.RECONNECTING) {
                    if (System.currentTimeMillis() - lastFetch > TimeUnit.MINUTES.toMillis(5)) {
                        lastFetch = System.currentTimeMillis();
                    }
                    Set<Integer> aasToUpdate;
                    synchronized (runningAlliances) {
                        aasToUpdate = new HashSet<>(runningAlliances);
                    }
                    for (int aaId : aasToUpdate) {
                        DBAlliance alliance = DBAlliance.get(aaId);
                        if (alliance == null) continue;
                        spyTracker.loadCasualties(aaId);
                    }
                }
            }
        });
        this.spyTracker = new SpyTracker();
        this.spyTracker.loadCasualties(null);
    }

    public void setupSubscriptions(DBAlliance alliance) {
        GuildDB db = alliance.getGuildDB();
        if (db != null) {
            setupSpySubscriptions(db, alliance);
        }
    }

    private final Set<Integer> runningAlliances = new HashSet<>();

    public boolean setupSpySubscriptions(GuildDB db, DBAlliance alliance) {
        synchronized (runningAlliances) {
            int allianceId = alliance.getAlliance_id();
            if (runningAlliances.contains(allianceId)) {
                return true;
            }
            String key = null;
            try {
                key = getAllianceKey(allianceId);
            } catch (IllegalArgumentException ignore) {}
            if (key == null) {
                MessageChannel channel = db.getOrNull(GuildDB.Key.ESPIONAGE_ALERT_CHANNEL);
                if (channel != null && channel.canTalk()) {
                    try {
                        RateLimitUtil.queueMessage(channel, "Disabling " + GuildDB.Key.ESPIONAGE_ALERT_CHANNEL.name() + " (invalid key)", false);
                    } catch (Throwable ignore2) {}
                }
                db.deleteInfo(GuildDB.Key.ESPIONAGE_ALERT_CHANNEL);
                return false;
            }
            System.out.println("Enabling pusher for " + allianceId);
            runningAlliances.add(allianceId);
            this.root.subscribeBuilder(key, Nation.class, PnwPusherEvent.UPDATE).addFilter(PnwPusherFilter.ALLIANCE_ID, allianceId).build(nations -> {
                try {
                    System.out.println("Subscription alert " + allianceId);
                    spyTracker.updateCasualties(nations);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
            System.out.println("Subscribed to " + allianceId);
            spyTracker.loadCasualties(allianceId);
            System.out.println("Loaded casualties for " + allianceId);
            this.root.connect();
            return true;
        }
    }

    public void subscribeDefaultEvents() {
        // get nation db
        NationDB nationDB = Locutus.imp().getNationDB();

        { // nations
//            root.subscribeBuilder(Nation.class, PnwPusherEvent.CREATE).build(nations -> {
//                for (Nation nation : nations) nationDB.markNationDirty(nation.getId());
//                Locutus.imp().runEventsAsync(events -> nationDB.updateNations(nations, events));
//            });
//            root.subscribeBuilder(Nation.class, PnwPusherEvent.DELETE).build(nations -> {
//                Locutus.imp().runEventsAsync(events -> nationDB.deleteNations(nations.stream().map(Nation::getId).collect(Collectors.toSet()), events));
//            });
            root.subscribeBuilder(Settings.INSTANCE.API_KEY_PRIMARY, Nation.class, PnwPusherEvent.UPDATE).build(nations -> {
                try {
                    System.out.println("Receive nation events");
                    for (Nation nation : nations) {
                        nationDB.markNationDirty(nation.getId());
                    }
                    spyTracker.updateCasualties(nations);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
//                Locutus.imp().runEventsAsync(events -> nationDB.updateNations(nations, events));
            });
            System.out.println("Loading alliances");
            for (DBAlliance alliance : nationDB.getAlliances()) {
                if (!alliance.getNations(f -> f.active_m() < 10000 && f.getPositionEnum().id > Rank.APPLICANT.id && f.getVm_turns() == 0).isEmpty()) {
                    try {
                        setupSubscriptions(alliance);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
            System.out.println("Done loading alliances");
            // register alliances
        }

//        { // cities
//            root.subscribeBuilder(City.class, PnwPusherEvent.CREATE).build(cities ->
//                    Locutus.imp().runEventsAsync(events -> nationDB.updateCities(cities, events)));
//            root.subscribeBuilder(City.class, PnwPusherEvent.UPDATE).build(cities ->
//                    Locutus.imp().runEventsAsync(events -> nationDB.updateCities(cities, events)));
//            root.subscribeBuilder(City.class, PnwPusherEvent.DELETE).build(cities ->
//                    Locutus.imp().runEventsAsync(events -> nationDB.deleteCities(cities, events)));
//        }
//
//        { // alliance
//            root.subscribeBuilder(Alliance.class, PnwPusherEvent.CREATE).build(alliances -> {
//                Locutus.imp().runEventsAsync(events -> nationDB.processUpdatedAlliances(alliances, events));
//            });
//            root.subscribeBuilder(Alliance.class, PnwPusherEvent.UPDATE).build(alliances -> {
//                Locutus.imp().runEventsAsync(events -> nationDB.processUpdatedAlliances(alliances, events));
//            });
//            root.subscribeBuilder(Alliance.class, PnwPusherEvent.DELETE).build(alliances -> {
//                Locutus.imp().runEventsAsync(events -> nationDB.deleteAlliances(alliances.stream().map(Alliance::getId).collect(Collectors.toSet()), events));
//            });
//        }
//
        root.connect();
    }

    public void onAllianceError(int allianceId){
        System.out.println("Alliance error " + allianceId);
        spyTracker.loadCasualties(allianceId);
    }

    public String getAllianceKey(int allianceId) {
        DBAlliance alliance = DBAlliance.get(allianceId);

        // get api (see spies)
        ApiKeyPool keys = alliance.getApiKeys(AlliancePermission.SEE_SPIES);
        if (keys == null || keys.size() == 0) return null;

        String validKey = null;

        RuntimeException lastError = null;
        for (ApiKeyPool.ApiKey key : keys.getKeys()) {
            PoliticsAndWarV3 api = new PoliticsAndWarV3(ApiKeyPool.create(key));
            try {
                api.getApiKeyStats();
                validKey = key.getKey();
                break;
            } catch (IllegalArgumentException | GraphQLException e) {
                lastError = e;
            }
        }
        if (lastError != null && validKey == null) throw lastError;
        return validKey;
    }

    public PnwPusherHandler getHandler() {
        return root;
    }
}
