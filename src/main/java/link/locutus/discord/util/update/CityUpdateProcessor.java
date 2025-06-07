package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.city.CityBuildingChangeEvent;
import link.locutus.discord.event.city.CityInfraBuyEvent;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.CaughtTask;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class CityUpdateProcessor {

    private final Map<Integer, Map<Integer, MMRChange>> changes2;
//    private final ConcurrentLinkedQueue<MMRChange> changes;

    public CityUpdateProcessor() {
        changes2 = new ConcurrentHashMap<>();
        Locutus.imp().getRepeatingTasks().addTask("Officer MMR Alerts", new CaughtTask() {
            @Override
            public void runUnsafe() throws Exception {
                runOfficerMMRTask();
            }
        }, 60, TimeUnit.SECONDS);
    }

    @Subscribe
    public void onInfraBuy(CityInfraBuyEvent event) {
        DBCity city = event.getCurrent();

        DBNation nation = DBNation.getById(event.getNationId());

        if (city.getInfra() % 50 != 0 && nation != null) {
            // TODO FIXME infra buy audit calling sometimes when no infra is bought
            if (true) return;
            AlertUtil.auditAlert(nation, AutoAuditType.UNEVEN_INFRA, new Function<GuildDB, String>() {
                @Override
                public String apply(GuildDB guildDB) {
                    int ideal = (int) (city.getInfra() - city.getInfra() % 50);
                    String msg = AutoAuditType.UNEVEN_INFRA.msg()
                            .replace("{city}", PW.City.getCityUrl(city.getId()));
                    return "You bought uneven infra in <" + PW.City.getCityUrl(city.getId()) + "> (" + MathMan.format(city.getInfra()) + " infra) but only get a building slot every `50` infra.\n" +
                            "You can enter e.g. `@" + ideal + "` to buy up to that amount";
                }
            });
        }
    }

    private synchronized void runOfficerMMRTask() {
        long cutoff = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60 * 2);
        if (changes2.isEmpty()) return;

        Map<Integer, List<MMRChange>> changesByNation = new Int2ObjectOpenHashMap<>();
        Iterator<Map.Entry<Integer, Map<Integer, MMRChange>>> iter = changes2.entrySet().iterator();
        outer:
        while (iter.hasNext()) {
            Map.Entry<Integer, Map<Integer, MMRChange>> entry = iter.next();
            DBNation nation = DBNation.getById(entry.getKey());
            if (nation == null) {
                iter.remove();
                continue;
            }
            Map<Integer, MMRChange> changesByCities = entry.getValue();
            // if changes are too recent, dont post
            if (nation.getCities() * 0.5 > changesByCities.size()) continue;
            for (MMRChange change : changesByCities.values()) {
                if (change.time > cutoff) continue outer;
            }

            iter.remove();
            changesByNation.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        if (changesByNation.isEmpty()) return;

        for (Map.Entry<Integer, List<MMRChange>> entry : changesByNation.entrySet()) {
            DBNation nation = DBNation.getById(entry.getKey());
            if (nation == null) continue;

            int arrLen = 0;
            Map<Integer, int[]> beforeByCity = new Int2ObjectOpenHashMap<>();
            Map<Integer, int[]> afterByCity = new Int2ObjectOpenHashMap<>();
            Set<String> reasons = new HashSet<>();
            for (MMRChange change : entry.getValue()) {
                int[] mmrFrom = beforeByCity.computeIfAbsent(change.cityId, f -> change.mmrFrom);
                int[] mmrTo = afterByCity.computeIfAbsent(change.cityId, f -> change.mmrTo);
                for (int i = 0; i < change.mmrTo.length; i++) mmrFrom[i] = Math.min(mmrFrom[i], change.mmrFrom[i]);
                for (int i = 0; i < change.mmrTo.length; i++) mmrTo[i] = Math.min(mmrTo[i], change.mmrTo[i]);
                arrLen = change.mmrTo.length;
                if (change.reason != null) {
                    reasons.add(change.reason);
                }
            }
            double[] beforeAvg = new double[arrLen];
            double[] afterAvg = new double[arrLen];
            for (int cityId : afterByCity.keySet()) {
                int[] mmrFrom = beforeByCity.get(cityId);
                int[] mmrTo = afterByCity.get(cityId);
                for (int i = 0; i < mmrTo.length; i++) {
                    beforeAvg[i] += mmrFrom[i];
                    afterAvg[i] += mmrTo[i];
                }
            }
            for (int i = 0; i < arrLen; i++) {
                beforeAvg[i] /= beforeByCity.size();
                afterAvg[i] /= beforeByCity.size();
            }

            boolean isGov = nation.getPositionEnum().id > Rank.MEMBER.id || nation.hasPermission(AlliancePermission.WITHDRAW_BANK);
            int reqRank = Settings.INSTANCE.TASKS.OFFICER_MMR_ALERT_TOP_X;

            DBAlliance alliance = nation.getAlliance();
            if (isGov) {
                if (alliance == null) isGov = false;
                else if (alliance.getRank() > reqRank) {
                    DBAlliance parent = alliance.getCachedParentOfThisOffshore();
                    if (parent == null || parent.getRank() > reqRank) {
                        isGov = false;
                    }
                }
            }


            String title = "MMR: " + nation.getNation() + " | " + nation.getAllianceName();
            StringBuilder body = new StringBuilder();
            body.append(nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup() + "\n");
            body.append("-# caller: " + StringMan.getString(reasons) + "\n");

            if (alliance != null) {
                DBAlliance parent = alliance.getCachedParentOfThisOffshore();
                if (parent != null) {
                    body.append("(offshore for: " + parent.getMarkdownUrl() + ")\n");
                }
            }
            MilitaryBuilding building0 = Buildings.BARRACKS;
            body.append("Cities: " + nation.getCities() + " (MMR changed in " + beforeByCity.size() + " cities)\n");

            for (int i = 0; i < arrLen; i++) {
                Building building = Buildings.get(i + building0.ordinal());
                String fromStr = MathMan.format(beforeAvg[i]);
                String toStr = MathMan.format(afterAvg[i]);
                body.append("**" + building.nameSnakeCase() + "**: " + fromStr + "->" + toStr + "\n");
            }

            if (nation.getNumWars() > 0) {
                String warUrl = nation.getUrl() + "&display=war";
                body.append("\n\nOff/Def wars: " + MarkupUtil.markdownUrl(nation.getOff() + "/" + nation.getDef(), warUrl));
            }

            Set<MessageChannel> channels = new HashSet<>();
            if (isGov && nation.getNumWars() == 0) {
                AlertUtil.forEachChannel(f -> true, GuildKey.ORBIS_OFFICER_MMR_CHANGE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                    @Override
                    public void accept(MessageChannel channel, GuildDB guildDB) {
                        DiscordUtil.createEmbedCommand(channel, title, body.toString());
                    }
                });
            }

            if (nation.getAlliance_id() != 0 && nation.getPositionEnum().id > Rank.APPLICANT.id) {
                AlertUtil.forEachChannel(f -> f.isValidAlliance() && f.isEnemyAlliance(nation.getAlliance_id()), GuildKey.ENEMY_MMR_CHANGE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                    @Override
                    public void accept(MessageChannel channel, GuildDB guildDB) {
                        DiscordUtil.createEmbedCommand(channel, title, body.toString());
                    }
                });
            }
        }
    }

    public static class MMRChange {
        public final long time;
        public final int[] mmrFrom;
        public final int[] mmrTo;
        public final int cityId;
        public final int nationId;
        public final String reason;

        public MMRChange(long time, int[] mmrFrom, int[] mmrTo, int cityId, int nationId, String reason) {
            this.time = time;
            this.mmrFrom = mmrFrom;
            this.mmrTo = mmrTo;
            this.cityId = cityId;
            this.nationId = nationId;
            this.reason = reason;
        }
    }

    @Subscribe
    public void onCityChangeBuildings(CityBuildingChangeEvent event) {
        DBNation nation = event.getNation();
        if (nation == null) return;

        processOfficerChangeMMR(nation, event.getPrevious(), event.getCurrent(), event.getReason());
    }

    private void processOfficerChangeMMR(DBNation nation, DBCity cityFrom, DBCity cityTo, String reason) {
        if (this.changes2 == null) return;

        DBAlliance alliance = nation.getAlliance();
        if (alliance == null) return;
        if (nation.getPositionEnum().id <= Rank.APPLICANT.id) return;
        if (nation.getCities() < 10) return;

        int[] mmrFrom = cityFrom.getMMRArray();
        int[] mmrTo = cityTo.getMMRArray();
        int increase = 0;
        for (int i = 0; i < 3; i++) increase += mmrTo[i] - mmrFrom[i];

        if (increase <= 0) return;

        MMRChange change = new MMRChange(cityTo.getFetched(), mmrFrom, mmrTo, cityTo.getId(), nation.getNation_id(), reason);
        changes2.computeIfAbsent(nation.getNation_id(), f -> new ConcurrentHashMap<>()).put(cityTo.getId(), change);
    }
}
