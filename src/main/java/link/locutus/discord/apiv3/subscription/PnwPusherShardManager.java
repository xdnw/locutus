package link.locutus.discord.apiv3.subscription;

import com.politicsandwar.graphql.model.Alliance;
import com.politicsandwar.graphql.model.Bounty;
import com.politicsandwar.graphql.model.City;
import com.politicsandwar.graphql.model.Nation;
import com.pusher.client.connection.ConnectionStateChange;
import graphql.GraphQLException;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.SpyTracker;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PnwPusherShardManager {
    private PnwPusherHandler root;
    private SpyTracker spyTracker;

    public PnwPusherShardManager() {
    }

    public void load() {
        this.root = new PnwPusherHandler().connect(null, new Consumer<ConnectionStateChange>() {
//            private long lastFetch = System.currentTimeMillis();
            @Override
            public void accept(ConnectionStateChange connectionStateChange) {
//                if (spyTracker != null && connectionStateChange.getPreviousState() == ConnectionState.RECONNECTING) {
//                    if (System.currentTimeMillis() - lastFetch > TimeUnit.MINUTES.toMillis(5)) {
//                        lastFetch = System.currentTimeMillis();
//                    }
//                    Set<Integer> aasToUpdate;
//                    synchronized (runningAlliances) {
//                        aasToUpdate = new HashSet<>(runningAlliances);
//                    }
//                    for (int aaId : aasToUpdate) {
//                        DBAlliance alliance = DBAlliance.get(aaId);
//                        if (alliance == null) continue;
//                        spyTracker.loadCasualties(aaId);
//                    }
//                }
            }
        });
        this.spyTracker = new SpyTracker();
        this.spyTracker.loadCasualties(null);
    }

    public SpyTracker getSpyTracker() {
        return spyTracker;
    }

    public void setupSubscriptions(DBAlliance alliance) {
        GuildDB db = alliance.getGuildDB();
        if (db != null) {
            setupSpySubscriptions(db, alliance);
        }
    }

    private final Set<Integer> runningAlliances = new IntOpenHashSet();

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
                MessageChannel channel = db.getOrNull(GuildKey.ESPIONAGE_ALERT_CHANNEL);
                if (channel != null && channel.canTalk()) {
                    try {
                        RateLimitUtil.queueMessage(channel, "Disabling " + GuildKey.ESPIONAGE_ALERT_CHANNEL.name() + " (invalid key)", false);
                    } catch (Throwable ignore2) {}
                }
                db.deleteInfo(GuildKey.ESPIONAGE_ALERT_CHANNEL);
                return false;
            }
            runningAlliances.add(allianceId);
            this.root.subscribeBuilder(key, Nation.class, PnwPusherEvent.UPDATE).addFilter(PnwPusherFilter.ALLIANCE_ID, allianceId).build(nations -> {
                try {
                    spyTracker.updateCasualties(nations);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
            spyTracker.loadCasualties(allianceId);
            Logg.text("Loaded spy tracker for " + allianceId);
            this.root.connect();
            return true;
        }
    }

    public void subscribeDefaultEvents() {
        // get nation db
        NationDB nationDB = Locutus.imp().getNationDB();

        { // nations
            root.subscribeBuilder(Locutus.loader().getApiKey(), Nation.class, PnwPusherEvent.CREATE).build(nations -> {
                long ts = System.currentTimeMillis();
//                for (Nation nation : nations) nationDB.markNationDirty(nation.getId());
                Locutus.imp().runEventsAsync(events -> {
                    for (Nation nation : nations) {
                        nation.setGross_national_income(null);
                    }
                    nationDB.updateNations(nations, events, ts);
                });
            });
            root.subscribeBuilder(Locutus.loader().getApiKey(), Nation.class, PnwPusherEvent.DELETE).build(nations -> {
                Locutus.imp().runEventsAsync(events -> nationDB.deleteNations(nations.stream().map(Nation::getId).collect(Collectors.toSet()), events));
            });
            root.subscribeBuilder(Locutus.loader().getApiKey(), Nation.class, PnwPusherEvent.UPDATE).build(nations -> {
                try {
                    spyTracker.updateCasualties(nations);
                    Locutus.imp().runEventsAsync(f -> {
                        for (Nation nation : nations) {
                            nation.setGross_national_income(null);
                        }
                        Locutus.imp().getNationDB().updateNations(nations, f, -1);
                    });
                } catch (Throwable e) {
                    e.printStackTrace();
                }
//                Locutus.imp().runEventsAsync(events -> nationDB.updateNations(nations, events));
            });
            for (DBAlliance alliance : nationDB.getAlliances()) {
                if (!alliance.getNations(f -> f.active_m() < 7200 && f.getPositionEnum().id > Rank.APPLICANT.id && f.getVm_turns() == 0).isEmpty()) {
                    try {
                        setupSubscriptions(alliance);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        {// bounty
            root.subscribeBuilder(Locutus.loader().getApiKey(), Bounty.class, PnwPusherEvent.CREATE).build(bounties -> {
                try {
                    spyTracker.checkBounties(bounties);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        { // cities
            root.subscribeBuilder(Locutus.loader().getApiKey(), City.class, PnwPusherEvent.CREATE).build(cities ->
                    Locutus.imp().runEventsAsync(events -> nationDB.updateCitiesSubscribe(cities, events)));

            root.subscribeBuilder(Locutus.loader().getApiKey(), City.class, PnwPusherEvent.UPDATE).build(cities ->
                    Locutus.imp().runEventsAsync(events -> nationDB.updateCitiesSubscribe(cities, events)));

            root.subscribeBuilder(Locutus.loader().getApiKey(), City.class, PnwPusherEvent.DELETE).build(cities ->
                    Locutus.imp().runEventsAsync(events -> nationDB.deleteCities(cities, events)));
        }

        { // alliance
            root.subscribeBuilder(Locutus.loader().getApiKey(), Alliance.class, PnwPusherEvent.CREATE).build(alliances -> {
                Locutus.imp().runEventsAsync(events -> nationDB.processUpdatedAlliances(alliances, events));
            });
            root.subscribeBuilder(Locutus.loader().getApiKey(), Alliance.class, PnwPusherEvent.UPDATE).build(alliances -> {
                Locutus.imp().runEventsAsync(events -> nationDB.processUpdatedAlliances(alliances, events));
            });
            root.subscribeBuilder(Locutus.loader().getApiKey(), Alliance.class, PnwPusherEvent.DELETE).build(alliances -> {
                Locutus.imp().runEventsAsync(events -> nationDB.deleteAlliances(alliances.stream().map(Alliance::getId).collect(Collectors.toSet()), events));
            });
        }

        root.connect();
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
