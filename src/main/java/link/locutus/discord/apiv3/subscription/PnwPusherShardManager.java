package link.locutus.discord.apiv3.subscription;

import com.politicsandwar.graphql.model.Alliance;
import com.politicsandwar.graphql.model.City;
import com.politicsandwar.graphql.model.Nation;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
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
import java.util.stream.Collectors;

public class PnwPusherShardManager {
    private final Map<Integer, PnwPusherHandler> allianceHandlers;
    private PnwPusherHandler root;
    private SpyTracker spyTracker;

    public PnwPusherShardManager() {
        this.allianceHandlers = new ConcurrentHashMap<>();


        /**
         * Spy tracker in Locutus class
         * on pusher update, update spy tracker
         *
         * run spy tracker at interval
         *
         */
    }

    public void load() {
        this.root = new PnwPusherHandler(Settings.INSTANCE.API_KEY_PRIMARY).connect();
        this.spyTracker = new SpyTracker();
        this.spyTracker.loadCasualties(null);
    }

    public void setupSubscriptions(DBAlliance alliance) {
        GuildDB db = alliance.getGuildDB();

        if (db != null) {
            setupSpySubscriptions(db, alliance);
        }
    }

    private Set<Integer> runningAlliances = new HashSet<>();

    public boolean setupSpySubscriptions(GuildDB db, DBAlliance alliance) {
        MessageChannel channel = db.getOrNull(GuildDB.Key.ESPIONAGE_ALERT_CHANNEL);
        if (channel == null) return false;
        return setupSpySubscriptions(db, alliance, channel);
    }
    public synchronized boolean setupSpySubscriptions(GuildDB db, DBAlliance alliance, MessageChannel channel) {
        if (runningAlliances.contains(alliance.getAlliance_id())) {
            return true;
        }
        if (channel == null) return false;
        if (!channel.canTalk()) {
            db.deleteInfo(GuildDB.Key.ESPIONAGE_ALERT_CHANNEL);
            return false;
        }
        PnwPusherHandler pusher;
        try {
            pusher = getAlliancePusher(alliance.getAlliance_id(), true);
        } catch (IllegalArgumentException ignore) {
            RateLimitUtil.queueMessage(channel, "Disabling " + GuildDB.Key.ESPIONAGE_ALERT_CHANNEL + ": " + ignore.getMessage(), false);
            db.deleteInfo(GuildDB.Key.ESPIONAGE_ALERT_CHANNEL);
            return false;
        }
        System.out.println("Enabling pusher for " + alliance.getAlliance_id());
        runningAlliances.add(alliance.getAlliance_id());
        pusher = pusher.subscribeBuilder(Nation.class, PnwPusherEvent.UPDATE).addFilter(PnwPusherFilter.ALLIANCE_ID, alliance.getAlliance_id()).build(nations -> {
            try {
                spyTracker.updateCasualties(nations);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        System.out.println("Subscribed to " + alliance.getAlliance_id());
        pusher.connect();
        System.out.println("Connected to " + alliance.getAlliance_id());
        spyTracker.loadCasualties(alliance.getAlliance_id());
        System.out.println("Loaded casualties for " + alliance.getAlliance_id());
        return true;
    }

    public void subscribeDefaultEvents() {
        // get nation db
        NationDB nationDB = Locutus.imp().getNationDB();

        { // nations
            root.subscribeBuilder(Nation.class, PnwPusherEvent.CREATE).build(nations -> {
                for (Nation nation : nations) nationDB.markNationDirty(nation.getId());
                Locutus.imp().runEventsAsync(events -> nationDB.updateNations(nations, events));
            });
            root.subscribeBuilder(Nation.class, PnwPusherEvent.DELETE).build(nations -> {
                Locutus.imp().runEventsAsync(events -> nationDB.deleteNations(nations.stream().map(Nation::getId).collect(Collectors.toSet()), events));
            });
            root.subscribeBuilder(Nation.class, PnwPusherEvent.UPDATE).build(nations -> {
                for (Nation nation : nations) {
                    nationDB.markNationDirty(nation.getId());
                }
                try {
                    spyTracker.updateCasualties(nations);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Locutus.imp().runEventsAsync(events -> nationDB.updateNations(nations, events));
            });
            System.out.println("Loading alliances");
            for (DBAlliance alliance : nationDB.getAlliances()) {
                setupSubscriptions(alliance);
            }
            System.out.println("Done loading alliances");
            // register alliances
        }

        { // cities
            root.subscribeBuilder(City.class, PnwPusherEvent.CREATE).build(cities ->
                    Locutus.imp().runEventsAsync(events -> nationDB.updateCities(cities, events)));
            root.subscribeBuilder(City.class, PnwPusherEvent.UPDATE).build(cities ->
                    Locutus.imp().runEventsAsync(events -> nationDB.updateCities(cities, events)));
            root.subscribeBuilder(City.class, PnwPusherEvent.DELETE).build(cities ->
                    Locutus.imp().runEventsAsync(events -> nationDB.deleteCities(cities, events)));
        }

        { // alliance
            root.subscribeBuilder(Alliance.class, PnwPusherEvent.CREATE).build(alliances -> {
                Locutus.imp().runEventsAsync(events -> nationDB.processUpdatedAlliances(alliances, events));
            });
            root.subscribeBuilder(Alliance.class, PnwPusherEvent.UPDATE).build(alliances -> {
                Locutus.imp().runEventsAsync(events -> nationDB.processUpdatedAlliances(alliances, events));
            });
            root.subscribeBuilder(Alliance.class, PnwPusherEvent.DELETE).build(alliances -> {
                Locutus.imp().runEventsAsync(events -> nationDB.deleteAlliances(alliances.stream().map(Alliance::getId).collect(Collectors.toSet()), events));
            });
        }

        root.connect();
    }

    public PnwPusherHandler getAlliancePusher(int allianceId, boolean create) {
        PnwPusherHandler existing = allianceHandlers.get(allianceId);
        if (existing != null || !create) return existing;
        synchronized (this) {
            existing = allianceHandlers.get(allianceId);
            if (existing != null) return existing;

            DBAlliance alliance = DBAlliance.get(allianceId);

            // get api (see spies)
            ApiKeyPool keys = alliance.getApiKeys(false, AlliancePermission.SEE_SPIES);
            if (keys == null || keys.size() == 0) return null;

            String validKey = null;
            IllegalArgumentException lastError = null;
            for (ApiKeyPool.ApiKey key : keys.getKeys()) {
                PoliticsAndWarV3 api = new PoliticsAndWarV3(ApiKeyPool.create(key));
                try {
                    api.testBotKey();
                    validKey = key.getKey();
                    break;
                } catch (IllegalArgumentException e) {
                    lastError = e;
                }
            }
            if (lastError != null && validKey == null) throw lastError;

            PnwPusherHandler pusher = new PnwPusherHandler(validKey).connect();

            allianceHandlers.put(allianceId, existing = pusher);
        }
        return existing;
    }

    public void disableAlliancePusher(int allianceId) {
        PnwPusherHandler handler = allianceHandlers.remove(allianceId);
        if (handler != null) {
            handler.disconnect();
        }
    }

    public PnwPusherHandler getRoot() {
        return root;
    }
}
