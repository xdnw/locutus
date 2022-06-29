package link.locutus.discord.util.task;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.task.multi.GetUid;
import link.locutus.discord.util.update.NationUpdateProcessor;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv1.domains.AllianceMembers;
import link.locutus.discord.apiv1.domains.subdomains.AllianceMembersContainer;
import link.locutus.discord.apiv1.domains.subdomains.SNationContainer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public enum SyncUtil {
    INSTANCE
    ;

    private final ReentrantLock lock = new ReentrantLock();
    public synchronized boolean syncIfFree(Consumer<String> updater, boolean force) {
        if (lock.tryLock()) {
            try {
                return sync(updater, force);
            } catch (Throwable e) {
                e.printStackTrace();
                updater.accept(e.getMessage());
                return false;
            } finally {
                lock.unlock();
            }
        } else {
            return false;
        }
    }

    public boolean isLocked() {
        return lock.isLocked();
    }

    private long lastTurn = TimeUtil.getTurn();

    public boolean checkVM() {
        if (TimeUtil.getTurn() != lastTurn) {
            lastTurn = TimeUtil.getTurn();
            return true;
        }
        return false;
    }

    public synchronized boolean sync(Consumer<String> updater, boolean force) throws IOException, ParseException {
        updater.accept(" - Collecting nation list, please wait.");
        boolean checkVM = checkVM();
        if (checkVM && (TimeUtil.getTurn() % 12) == 0) {

//         Get API of registered servers, perform any registered update events etc  - deprecated, moved to guildhandler
//            for (Map.Entry<Long, GuildDB> entry : Locutus.imp().getGuildDatabases().entrySet()) {
//                GuildDB db = entry.getValue();
//                try {
//                    Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
//                    if (aaId == null || db.getOrNull(GuildDB.Key.API_KEY) == null) continue;
//                    PoliticsAndWarV2 apy = db.getApi();
//                } catch (Throwable ignore) {
//                    ignore.printStackTrace();
//                }
//            }
        }

        long start = System.currentTimeMillis();
        List<SNationContainer> nations = Locutus.imp().getPnwApi().getNationsByScore(checkVM, 999999, -1).getNationsContainer();

        updater.accept(" - Collecting nation list, please wait...");

        NationDB db = Locutus.imp().getNationDB();
        Map<Integer, DBNation> nationMap = new HashMap<>();
        Map<Integer, SNationContainer> snationMap = new HashMap<>();
        for (SNationContainer wrapper : nations) {
            DBNation nation = new DBNation(wrapper);
            nationMap.put(nation.getNation_id(), nation);
            snationMap.put(nation.getNation_id(), wrapper);
        }

        updater.accept(" - Updating from cache.");

        Map<Integer, DBNation> dbCache = db.getNations(nationMap, checkVM);
        if (!checkVM) {
            for (Map.Entry<Integer, DBNation> entry : dbCache.entrySet()) {
                DBNation nation = entry.getValue();
                nationMap.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        updater.accept(" - Updating from cache..");

        NationUpdateProcessor.process(dbCache, nationMap, true, NationUpdateProcessor.UpdateType.INITIAL, start);

        updater.accept(" - Collecting rankings.");

        NationUpdateProcessor.process(dbCache, nationMap, false, NationUpdateProcessor.UpdateType.CHANGE, start);

        updater.accept(" - Fetching military.");

        new UpdateNationMilTask(nationMap).call();

        updater.accept(" - Updating military.");

        NationUpdateProcessor.process(dbCache, nationMap, false, NationUpdateProcessor.UpdateType.MILITARY, start);

        List<Map.Entry<Double, DBNation>> fetchSpiesList = new LinkedList<>();
        if (Settings.INSTANCE.TASKS.FETCH_SPIES_BACKGROUND_API || Settings.INSTANCE.TASKS.FETCH_SPIES_BACKGROUND_SCRAPE) {
            updater.accept(" - Estimating spy ops..");

            long currentTurn = TimeUtil.getTurn();
            String spyKey = "spies.";

            Set<Integer> trackspiesIds = new HashSet<>();

            trackspiesIds.addAll(Locutus.imp().getGuildDB(Locutus.imp().getServer()).getCoalition("trackspies"));

            if (Settings.INSTANCE.TASKS.FETCH_SPIES_BACKGROUND_API) {
                // Update spy counts for alliances using `$spySheet`
                for (GuildDB otherDb : Locutus.imp().getGuildDatabases().values()) {
                    if (!otherDb.isValidAlliance() || otherDb.isDelegateServer() || !otherDb.enabledSpySheet())
                        continue;
                    try {
                        Integer aaId = otherDb.getOrNull(GuildDB.Key.ALLIANCE_ID, false);
                        if (aaId != null) {
                            PoliticsAndWarV2 api = otherDb.getApi();
                            if (api == null) continue;
                            if (checkVM && (TimeUtil.getTurn() % 12) == 0) {
                                Set<DBNation> toUpdate = new LinkedHashSet<>();
                                AllianceMembers members = api.getAllianceMembers(aaId);
                                for (AllianceMembersContainer member : members.getNations()) {
                                    Integer spies = Integer.parseInt(member.getSpies());
                                    DBNation nation = Locutus.imp().getNationDB().getNation(member.getNationId());
                                    if (nation != null && !spies.equals(nation.getSpies())) {
                                        Locutus.imp().getNationDB().setSpies(nation.getNation_id(), spies);
                                        nation.setSpies(spies);
                                        toUpdate.add(nation);
                                    }
                                }
                                Locutus.imp().getNationDB().saveNations(toUpdate);
                            }
                        }
                        trackspiesIds.remove(aaId);
                    } catch (Throwable ignore) {
                    }
                }
            }

            if (Settings.INSTANCE.TASKS.FETCH_SPIES_BACKGROUND_SCRAPE) {
                for (Map.Entry<Integer, DBNation> entry : nationMap.entrySet()) {
                    DBNation nation = entry.getValue();
                    if (!trackspiesIds.contains(nation.getAlliance_id())) continue;

                    if (nation.getCities() < 10) continue;
                    if (nation.getNumWars() == 0) continue;
                    if (nation.getVm_turns() != 0) continue;
//            if (nation.isBeige()) continue;
                    if (nation.getAlliance_id() == 0) continue;
                    if (nation.getPosition() <= 1) continue;
                    if (nation.getActive_m() > TimeUnit.DAYS.toMinutes(1)) continue;

                    ByteBuffer meta = nation.getMeta(NationMeta.UPDATE_SPIES);
                    long lastturn = meta == null ? 0 : meta.getLong();

                    long lastUpdate = TimeUtil.getTimeFromTurn(lastturn);
                    long lastActive = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(nation.getActive_m());

                    long diff = currentTurn - lastturn;
                    if (diff > 1) {
                        if (diff <= 12 && nation.getSpies() != null) {
                            if (diff < 6 && lastturn != 0 && nation.getSpies() > 15) {
                                continue;
                            }
                            if (nation.getNumWars() == 0 && !nation.isBeige()) {
                                continue;
                            }
                            if (lastActive < lastUpdate) {
                                continue;
                            }
                        }
                        double factor = diff;

                        if (nation.getSpies() == null) {
                            factor += 100;
                        } else if (factor > 1) {
                            if (trackspiesIds.contains(nation.getAlliance_id())) {
                                factor += 12;
                            } else if (trackspiesIds.contains(nation.getAlliance_id())) {
                                factor += 6;
                            } else if (factor < 24) {
                                continue;
                            }
                        }
                        AbstractMap.SimpleEntry<Double, DBNation> pair = new AbstractMap.SimpleEntry<>(factor, nation);
                        fetchSpiesList.add(pair);
                    }
                }
            }
        }
//
        if (!fetchSpiesList.isEmpty()) {
            fetchSpiesList.sort((o1, o2) -> {
                return Double.compare(o2.getKey(), o1.getKey());
            });
            updater.accept(" - Estimating spy ops... (" + fetchSpiesList.size() + ")");
            {
                int i = 0;
                for (Map.Entry<Double, DBNation> entry : fetchSpiesList) {
                    DBNation nation = entry.getValue();
                    if (nation.getAlliance_id() != 0) {
                        String msg = " - Estimating spy ops for " + nation.getNation() + "... " + (i++) + "/" + fetchSpiesList.size();
                        updater.accept(msg);

                        nation.setMeta(NationMeta.UPDATE_SPIES, TimeUtil.getTurn());
                        nation.updateSpies(true);

                        if (i > 30) {
                            if (System.currentTimeMillis() - start > 60000) break;
                        }
                    }
                }
            }
        }

        if (Settings.INSTANCE.TASKS.FETCH_UUID) {
//            updater.accept(" - Uploading spreadsheets");
//            new UpdateBalanceSheetTask(nationMap).call(); // deprecated, it can get updated via command

            updater.accept(" - Checking uuids"); // for `!multi`

            long currentDay = TimeUtil.getDay();

            int i = 0;
            for (DBNation nation : dbCache.values()) {
                if (System.currentTimeMillis() - start > 55000) break;
                if (nation.getNation_id() == 6) continue;
                if (nation.isBeige()) continue;
                if (nation.getActive_m() < 60 || nation.getActive_m() > 120) continue;
                if (Locutus.imp().getDiscordDB().isVerified(nation.getNation_id())) continue;
                if (nation.getActive_m() >= 60 && nation.getActive_m() <= 120) {
                    ByteBuffer uuidUpdate = nation.getMeta(NationMeta.UPDATE_UUID);
                    long uuidUpdateTime = uuidUpdate == null ? 0 : uuidUpdate.getLong();
                    if (uuidUpdateTime >= currentDay - 7) continue;
                    if (i++ > 5) break;

                    updater.accept(" - Checking uuid for " + nation.getNation() + " (" + i + "/" + dbCache.size() + ")");

                    try {
                        new GetUid(nation).call();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    nation.setMeta(NationMeta.UPDATE_UUID, currentDay);
                }
            }
        }

        NationUpdateProcessor.process(dbCache, nationMap, true, NationUpdateProcessor.UpdateType.DONE, start);

        updater.accept("Done!");
        return true;
    }
}
