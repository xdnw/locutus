package link.locutus.discord.util.task.ia;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.building.ServiceBuilding;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.config.Messages;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.domains.Alliance;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.scheduler.ThrowingSupplier;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IACheckup {
    public static Map<IACheckup.AuditType, Map.Entry<Object, String>> simplify(Map<IACheckup.AuditType, Map.Entry<Object, String>> auditFinal) {
        Map<AuditType, Map.Entry<Object, String>> audit = new LinkedHashMap<>(auditFinal);
        audit.entrySet().removeIf(f -> {
            Map.Entry<Object, String> value = f.getValue();
            return value == null || value.getValue() == null;
        });

        Map<IACheckup.AuditType, Map.Entry<Object, String>> tmp = new LinkedHashMap<>(audit);
        tmp.entrySet().removeIf(f -> {
            IACheckup.AuditType required = f.getKey().required;
            while (required != null) {
                if (audit.containsKey(required)) return true;
                required = required.required;
            }
            return false;
        });
        return tmp;
    }

    public static void createEmbed(IMessageIO channel, String command, DBNation nation, Map<IACheckup.AuditType, Map.Entry<Object, String>> auditFinal, Integer page) {
        Map<AuditType, Map.Entry<Object, String>> audit = simplify(auditFinal);
        int failed = audit.size();

        boolean newPage = page == null;
        if (page == null) page = 0;
        String title = (page + 1) + "/" + failed + " tips for " + nation.getNation();

        List<String> pages = new ArrayList<>();
        for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : audit.entrySet()) {
            IACheckup.AuditType type = entry.getKey();
            Map.Entry<Object, String> info = entry.getValue();
            if (info == null || info.getValue() == null) continue;

            StringBuilder body = new StringBuilder();

            body.append("**" + type.name() + "**: ").append(type.emoji).append(" ");
            body.append(info.getValue());
            pages.add(body.toString());
        }
        DiscordUtil.paginate(channel, title, command, page, 1, pages, "", true);
    }

    private Map<DBNation, Map<ResourceType, Double>> memberStockpile;
    private Map<DBNation, List<Transaction2>> memberTransfers;
    private final GuildDB db;

    private final AllianceList alliance;

    public IACheckup(GuildDB db, AllianceList alliance, boolean useCache) throws IOException {
        if (db == null) throw new IllegalStateException("No database found");
        if (alliance == null || alliance.isEmpty()) throw new IllegalStateException("No alliance found");
        this.db = db;
        this.alliance = alliance;
        memberStockpile = new HashMap<>();
        if (!useCache) {
            memberStockpile = alliance.getMemberStockpile();
        }
        this.memberTransfers = new HashMap<>();
    }

    public AllianceList getAlliance() {
        return alliance;
    }

    public Map<DBNation, Map<AuditType, Map.Entry<Object, String>>> checkup(Consumer<DBNation> onEach, boolean fast) throws InterruptedException, ExecutionException, IOException {
        List<DBNation> nations = new ArrayList<>(alliance.getNations(true, 0, true));
        return checkup(nations, onEach, fast);
    }

    public Map<DBNation, Map<AuditType, Map.Entry<Object, String>>> checkup(Collection<DBNation> nations, Consumer<DBNation> onEach, boolean fast) throws InterruptedException, ExecutionException, IOException {
        return checkup(nations, onEach, AuditType.values(), fast);
    }

    public Map<DBNation, Map<AuditType, Map.Entry<Object, String>>> checkup(Collection<DBNation> nations, Consumer<DBNation> onEach, AuditType[] auditTypes, boolean fast) throws InterruptedException, ExecutionException, IOException {
        Map<DBNation, Map<AuditType, Map.Entry<Object, String>>> result = new LinkedHashMap<>();
        for (DBNation nation : nations) {
            if (nation.getVm_turns() != 0 || nation.getActive_m() > 10000) continue;

            if (onEach != null) onEach.accept(nation);

            Map<AuditType, Map.Entry<Object, String>> nationMap = checkup(nation, auditTypes, fast, fast);
            result.put(nation, nationMap);
        }
        return result;
    }

    public Map<AuditType, Map.Entry<Object, String>> checkup(DBNation nation) throws InterruptedException, ExecutionException, IOException {
        return checkup(nation, AuditType.values());
    }

    public Map<AuditType, Map.Entry<Object, String>> checkupSafe(DBNation nation, boolean individual, boolean fast) {
        try {
            Map<AuditType, Map.Entry<Object, String>> result = checkup(nation, individual, fast);
            return result;
        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<AuditType, Map.Entry<Object, String>> checkup(DBNation nation, boolean individual, boolean fast) throws InterruptedException, ExecutionException, IOException {
        return checkup(nation, AuditType.values(), individual, fast);
    }

    public Map<AuditType, Map.Entry<Object, String>> checkup(DBNation nation, AuditType[] audits) throws InterruptedException, ExecutionException, IOException {
        return checkup(nation, audits, true, false);
    }

    public Map<AuditType, Map.Entry<Object, String>> checkup(DBNation nation, AuditType[] audits, boolean individual, boolean fast) throws InterruptedException, ExecutionException, IOException {
        int days = 120;

        long start = System.currentTimeMillis();
        Map<Integer, JavaCity> cities = nation.getCityMap(false);
        if (cities.isEmpty()) {
            return new HashMap<>();
        }
        List<Transaction2> transactions = memberTransfers.computeIfAbsent(nation, f -> {
            try {
                return nation.getTransactions(fast ? -1L : 1, false);
            } catch (RuntimeException e) {
                e.printStackTrace();
                return Locutus.imp().getBankDB().getTransactionsByNation(nation.getNation_id());
            }
        });
        Map<ResourceType, Double> stockpile = memberStockpile.get(nation);
        Map<AuditType, Map.Entry<Object, String>> results = new LinkedHashMap<>();
        for (AuditType type : audits) {
            long start2 = System.currentTimeMillis();
            audit(type, nation, transactions, cities, stockpile, results, individual, fast);
            long diff = System.currentTimeMillis() - start2;
            if (diff > 10) {
                System.out.println("remove:||Checkup Diff " + type + " | " + diff + " ms");
            }
        }

        long bitMask = 0;
        for (AuditType audit : audits) {
            Map.Entry<Object, String> result = results.get(audit);
            boolean passed = result == null || result.getValue() == null;
            bitMask |= (passed ? 1 : 0) << audit.ordinal();
        }

//        ByteBuffer lastCheckupBuffer = nation.getMeta(NationMeta.CHECKUPS_PASSED);
//        if (lastCheckupBuffer != null) {
//            long lastCheckup = lastCheckupBuffer.getLong();
//        }

//        nation.setMeta(NationMeta.CHECKUPS_PASSED, bitMask);

        results.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);

        results.keySet().stream().filter(f -> f.severity == AuditSeverity.DANGER).count();

        return results;
    }

    private void audit(AuditType type, DBNation nation, List<Transaction2> transactions, Map<Integer, JavaCity> cities, Map<ResourceType, Double> stockpile, Map<AuditType, Map.Entry<Object, String>> results, boolean individual, boolean fast) throws InterruptedException, ExecutionException, IOException {
        if (results.containsKey(type)) {
            return;
        }
        if (type.required != null) {
            if (!results.containsKey(type.required)) {
                audit(type.required, nation, transactions, cities, stockpile, results, individual, fast);
            }
            Map.Entry<Object, String> requiredResult = results.get(type.required);
            if (requiredResult != null) {
                results.put(type, null);
                return;
            }
        }
        Map.Entry<Object, String> value = checkup(type, nation, cities, transactions, stockpile, individual, fast);
        results.put(type, value);
    }

    public enum AuditSeverity {
        INFO,
        WARNING,
        DANGER,
    }

    public enum AuditType {
        CHECK_RANK("\uD83E\uDD47", AuditSeverity.WARNING),
        INACTIVE("\uD83D\uDCA4", AuditSeverity.DANGER),
        FINISH_OBJECTIVES("\uD83D\uDC69\u200D\uD83C\uDFEB", AuditSeverity.WARNING),
        FIX_COLOR(FINISH_OBJECTIVES, "\uD83C\uDFA8", AuditSeverity.WARNING),
        CHANGE_CONTINENT(FINISH_OBJECTIVES, "\uD83C\uDF0D", AuditSeverity.WARNING),
        FIX_WAR_POLICY(FINISH_OBJECTIVES, "\uD83D\uDCDC", AuditSeverity.WARNING),
        RAID(FINISH_OBJECTIVES, "\uD83D\uDD2A", AuditSeverity.WARNING),
        UNUSED_MAP( "\uD83D\uDE34", AuditSeverity.WARNING),
        BARRACKS(FINISH_OBJECTIVES, "\uD83C\uDFD5", AuditSeverity.WARNING),
        INCORRECT_MMR(FINISH_OBJECTIVES, "\uD83C\uDFE2", AuditSeverity.WARNING),
        BUY_SOLDIERS(BARRACKS, "\uD83D\uDC82", AuditSeverity.WARNING),
        BUY_HANGARS(BUY_SOLDIERS, "\u2708\uFE0F", AuditSeverity.WARNING),
        BUY_PLANES(BUY_HANGARS, "\u2708\uFE0F", AuditSeverity.WARNING),
        BUY_SHIPS(BUY_PLANES, "\uD83D\uDEA2", AuditSeverity.WARNING),
        BEIGE_LOOT(BUY_SOLDIERS, "\uD83D\uDC82", AuditSeverity.INFO),
        RAID_TURN_CHANGE(BEIGE_LOOT, "\uD83C\uDFAF", AuditSeverity.INFO),
        BUY_SPIES(FINISH_OBJECTIVES, "\uD83D\uDD75", AuditSeverity.WARNING),
        GATHER_INTEL(BUY_SPIES, "\uD83D\uDD0E", AuditSeverity.INFO),
        SPY_COMMAND(GATHER_INTEL, "\uD83D\uDCE1", AuditSeverity.INFO),
        LOOT_COMMAND(SPY_COMMAND, "", AuditSeverity.INFO),
        DAILY_SPYOPS(LOOT_COMMAND, "\uD83D\uDEF0", AuditSeverity.INFO),
        DEPOSIT_RESOURCES(FINISH_OBJECTIVES, "\uD83C\uDFE6", AuditSeverity.WARNING),
        CHECK_DEPOSITS(DEPOSIT_RESOURCES, "", AuditSeverity.INFO),
        WITHDRAW_DEPOSITS(CHECK_DEPOSITS, "\uD83C\uDFE7", AuditSeverity.INFO),
        OBTAIN_RESOURCES(FINISH_OBJECTIVES, "\uD83C\uDFE7", AuditSeverity.DANGER),
        SAFEKEEP(FINISH_OBJECTIVES, "\uD83C\uDFE6", AuditSeverity.WARNING),
        OBTAIN_WARCHEST(OBTAIN_RESOURCES, "\uD83C\uDFE7", AuditSeverity.INFO),
        BUY_CITY(FINISH_OBJECTIVES, "\uD83C\uDFD9", AuditSeverity.INFO),
        BUY_PROJECT(FINISH_OBJECTIVES, "\uD83D\uDE80", AuditSeverity.INFO),
        BUY_RESOURCE_PRODUCTION_CENTER(FINISH_OBJECTIVES, "\uD83D\uDE80", AuditSeverity.INFO),
        BUY_INFRA(FINISH_OBJECTIVES, "\uD83C\uDFD7", AuditSeverity.INFO),
        BUY_LAND(FINISH_OBJECTIVES, "\uD83C\uDFDE", AuditSeverity.INFO),
        UNPOWERED(FINISH_OBJECTIVES, "\uD83D\uDD0C", AuditSeverity.DANGER),
        OVERPOWERED(FINISH_OBJECTIVES, "\u26A1", AuditSeverity.DANGER),
        NOT_NUCLEAR(FINISH_OBJECTIVES, "\u2622", AuditSeverity.WARNING),
        FREE_SLOTS(FINISH_OBJECTIVES, "\uD83D\uDEA7", AuditSeverity.DANGER),
        NEGATIVE_REVENUE(FINISH_OBJECTIVES, "\uD83E\uDD7A", AuditSeverity.DANGER),
        MISSING_PRODUCTION_BONUS(FINISH_OBJECTIVES, "\uD83D\uDCC8", AuditSeverity.WARNING),
        EXCESS_HOSPITAL(FINISH_OBJECTIVES, "\uD83C\uDFE5", AuditSeverity.WARNING),
        EXCESS_POLICE(FINISH_OBJECTIVES, "\uD83D\uDE93", AuditSeverity.WARNING),
        EXCESS_RECYCLING(FINISH_OBJECTIVES, "\u267B", AuditSeverity.WARNING),
        GENERATE_CITY_BUILDS(MISSING_PRODUCTION_BONUS, "", AuditSeverity.INFO),
        ROI(GENERATE_CITY_BUILDS, "", AuditSeverity.INFO),
        BLOCKADED("\uD83D\uDEA2", AuditSeverity.INFO),
//        LOSE_A_WAR(RAID_TURN_CHANGE, "", AuditSeverity.INFO),
//        PLAN_A_RAID_WITH_FRIENDS(LOSE_A_WAR, "", AuditSeverity.INFO),
//        CREATE_A_WAR_ROOM(PLAN_A_RAID_WITH_FRIENDS, "", AuditSeverity.INFO),
        ;

        public final AuditType required;
        public final String emoji;
        public final AuditSeverity severity;

        AuditType(String emoji) {
            this(null, emoji);
        }

        AuditType(String emoji, AuditSeverity severity) {
            this(null, emoji, severity);
        }

        AuditType(AuditType required, String emoji) {
            this(required, emoji, AuditSeverity.INFO);
        }

        AuditType(AuditType required, String emoji, AuditSeverity severity) {
            this.required = required;
            this.emoji = emoji;
            this.severity = severity;
        }

        @Command(desc = "Audit severity")
        public AuditSeverity getSeverity() {
            return severity;
        }

        @Command(desc = "Audit emoji")
        public String getEmoji() {
            return emoji;
        }

        @Command(desc = "The required audit, or null")
        public AuditType getRequired() {
            return required;
        }

        @Command(desc = "Name of the audit")
        public String getName() {
            return name();
        }
    }

    private Map<Integer, Alliance> alliances = new HashMap<>();

    private Map.Entry<Object, String> checkup(AuditType type, DBNation nation, Map<Integer, JavaCity> cities, List<Transaction2> transactions, Map<ResourceType, Double> stockpile, boolean individual, boolean fast) throws InterruptedException, ExecutionException, IOException {
        boolean updateNation = individual && !fast;

        switch (type) {
            case CHECK_RANK: {
                Set<Integer> aaIds = db.getAllianceIds();
                if (aaIds.isEmpty()) return null;

                if (!aaIds.contains(nation.getAlliance_id())) {
                    int id = aaIds.iterator().next();
                    return new AbstractMap.SimpleEntry<>("APPLY", "Please apply to the alliance ingame: https://politicsandwar.com/alliance/join/id=" + id);
                }
                if (nation.getPosition() <= 1) {
                    return new AbstractMap.SimpleEntry<>("MEMBER", "Please discuss with your mentor about becoming a member");
                }
                return null;
            }
            case INACTIVE:
                return testIfCacheFails(() -> checkInactive(nation), updateNation);
            case FINISH_OBJECTIVES:
                return testIfCacheFails(() -> checkObjectives(nation), updateNation);
            case FIX_COLOR:
                return testIfCacheFails(() -> checkTradeColor(nation), updateNation);
            case FIX_WAR_POLICY:
                return testIfCacheFails(() -> checkWarPolicy(nation), updateNation);
            case CHANGE_CONTINENT:
                return testIfCacheFails(() -> checkContinent(nation), updateNation);
            case RAID:
                return testIfCacheFails(() -> checkOffensiveSlots(nation, db), updateNation);
            case UNUSED_MAP:
                return testIfCacheFails(() -> checkMAP(nation), updateNation);
            case BUY_SPIES:
                return checkSpies(nation);
            case BUY_HANGARS:
                return checkHangar(nation, cities);
            case BUY_PLANES:
                return checkAircraft(nation, cities, db);
            case BUY_SHIPS:
                return checkNavy(nation, cities);
            case BARRACKS: {
                if (nation.getCities() > 10 && !nation.isFightingActive()) {
                    return null;
                }

                double totalBarracks = 0;
                for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
                    totalBarracks += entry.getValue().get(Buildings.BARRACKS);
                }
                double avgBarracks = totalBarracks / cities.size();
                if (avgBarracks > 4) {
                    return null;
                }
                String desc = "Soldiers are the best unit for looting enemies and necessary for fighting wars, and are cheap. Get 5 barracks in each of your cities. <https://politicsandwar.com/cities/>\n\n" +
                        "*Note: You can sell off buildings, or buy more infrastructure if you are lacking building slots*";
                return new AbstractMap.SimpleEntry<>(nation.getMMR(), desc);
            }
            case INCORRECT_MMR:
                Map<NationFilter, MMRMatcher> requiredMmrMap = db.getOrNull(GuildKey.REQUIRED_MMR);
                return requiredMmrMap != null ? checkMMR(nation, cities, requiredMmrMap) : null;
            case BUY_SOLDIERS:
                return checkSoldierBuy(nation, cities);
//            case PLANES:
//                return checkPlaneBuy(nation, cities);
//            case DEL_TANKS:
//                return checkTanks(nation, cities);
//            case DEL_SHIPS:
//                return checkShips(nation);
            case BUY_CITY:
                return checkBuyCity(nation);
//                roi = roiMap.get(ROI.Investment.CITY_PROJECT);
////                if (roi != null && cities.size() < 25) {
////                    String message = Settings.commandPrefix(true) + "grant \"" + nation.getNation() + "\" " + roi.info + " 1";
////                    return new AbstractMap.SimpleEntry<>(roi.roi, message);
////                }
//                if (roi == null) {
//                    roi = roiMap.get(ROI.Investment.CITY);
//                }
//                if (roi != null && (pnwNation.getCities() >= 7) && (pnwNation.getCities() < 25)) {
//                    int amt = Math.max(1, 10 - pnwNation.getCities());
//                    String message = "To request a project on discord: !grant \"" + nation.getNation() + "\" city " + amt;
//                    return new AbstractMap.SimpleEntry<>(roi.roi, message);
//                }
            case BUY_PROJECT:
                return checkBuyProject(db, nation, cities);
//                roi = roiMap.get(ROI.Investment.RESOURCE_PROJECT);
//                if (roi != null && roi.roi > 5 && (pnwNation.getCities() >= 7)) {
//                    if (nation.getAvg_infra() > 1500) {
//                        String message = "To request a project on discord: !grant \"" + nation.getNation() + "\" " + roi.info + " 1";
//                        return new AbstractMap.SimpleEntry<>(roi.roi, message);
//                    }
//                }
//                return null;
            case BUY_RESOURCE_PRODUCTION_CENTER:
                return checkBuyRpc(db, nation, cities);
            case BUY_INFRA:
                return checkBuyInfra(nation, cities, db);
//                roi = roiMap.get(ROI.Investment.INFRA);
//                if (roi != null && roi.roi > 6 && nation.getDef() == 0 && (nation.getAvg_infra() < 600 || (nation.getCities() >= 10 && nation.getOff() == 0 && nation.getAvg_infra() > 1000))) {
//                    double amt = (double) roi.info;
//                    if (nation.getOff() > 0 && nation.getAvg_infra() < 800) amt = 800;
//                    String message = Settings.commandPrefix(true) + "grant \"" + nation.getNation() + "\" infra " + amt;
//                    return new AbstractMap.SimpleEntry<>(roi.roi, message);
//                }
//                return null;
            case BUY_LAND:
                return checkBuyLand(nation, cities);
//                roi = roiMap.get(ROI.Investment.LAND);
//                if (roi != null && roi.roi > 5) {
//                    String message = Settings.commandPrefix(true) + "grant \"" + nation.getNation() + "\" land " + roi.info;
//                    return new AbstractMap.SimpleEntry<>(roi.roi, message);
//                }
//                return null;
            case BLOCKADED:
                return checkBlockade(nation);
            case FREE_SLOTS:
                return checkEmptySlots(cities);
            case MISSING_PRODUCTION_BONUS:
                return checkProductionBonus(nation, cities);
            case NEGATIVE_REVENUE:
                return checkRevenue(nation);
            case UNPOWERED:
                return checkUnpowered(nation, cities);
            case OVERPOWERED:
                return checkOverpowered(cities);
            case NOT_NUCLEAR:
                return checkNuclearPower(cities);
            case EXCESS_HOSPITAL:
                return checkExcessService(nation, cities, Buildings.HOSPITAL, db);
            case EXCESS_RECYCLING:
                return checkExcessService(nation, cities, Buildings.RECYCLING_CENTER, db);
            case EXCESS_POLICE:
                return checkExcessService(nation, cities, Buildings.POLICE_STATION, db);
            case OBTAIN_RESOURCES:
                return stockpile == null || stockpile.isEmpty() ? null : testIfCacheFails(() -> checkSendResources(nation, stockpile, cities), updateNation);
            case SAFEKEEP:
                return stockpile == null || stockpile.isEmpty() ? null : testIfCacheFails(() -> checkSafekeep(nation, stockpile, cities), updateNation);
            case OBTAIN_WARCHEST:
                return stockpile == null || stockpile.isEmpty() ? null : checkWarchest(nation, stockpile, db);
            case BEIGE_LOOT:
                if (nation.getMeta(NationMeta.INTERVIEW_RAID_BEIGE) == null) {
                    String cmd = CM.war.find.raid.cmd.create("*", "15", null, null, "170", null, null, null, null, null, null).toString();
                    String shortDesc = "`" + cmd + "`";
                    String longDesc = "At higher city counts, there are less nations available to raid. You will need to find and hit nations as the come off of the beige protection color.\n" +
                            "To list raid targets currently on beige, use e.g.:\n" +
                            "> `" + cmd + "`";
                    return new AbstractMap.SimpleEntry<>(shortDesc, longDesc);
                }
                return null;
            case RAID_TURN_CHANGE:
                return checkRaidTurnChange(nation);
//            case LOSE_A_WAR: {
////                List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(nation.getNation_id());
////                wars.removeIf(f -> f.attacker_id == nation.getNation_id());
////                List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacksByWars(wars, 0);
////                attacks.removeIf(f -> f.attack_type != AttackType.VICTORY);
////                for (AbstractCursor attack : attacks) {
////                    if (attack.attacker_nation_id != nation.getNation_id()) return null;
////                }
////                String desc = "Get yourself rolled. e.g. Find an inactive raid target in an alliance, attack them and get some counters on yourself.\n" +
////                        "Remember to deposit your resources so nothing of value gets looted";
////                return new AbstractMap.SimpleEntry<>(false, desc);
//                return null;
//            }
            case DEPOSIT_RESOURCES: {
                Set<Integer> aaIds = db.getAllianceIds();
                if (!aaIds.isEmpty()) {
                    for (Transaction2 transaction : transactions) {
                        if (aaIds.contains((int) transaction.receiver_id)) return null;
                    }
                }
                String desc = "Having unnecessary resources or $$ on your nation will attract raiders. It is important to safekeep so it wont get stolen when you lose a war. Visit the alliance bank page and store funds for safekeeping:\n" +
                        "https://politicsandwar.com/alliance/id=" + nation.getAlliance_id() + "&display=bank\n\n" +
                        "*Note: deposit $1 if you don't need to safekeep so we know that you understand how to use the bank*";
                return new AbstractMap.SimpleEntry<>(false, desc);
            }
            case CHECK_DEPOSITS: {
                if (nation.getMeta(NationMeta.INTERVIEW_DEPOSITS) != null) return null;
                String desc = "You can check your deposits using:\n" +
                        CM.deposits.check.cmd.toSlashMention() + "\n" +
                        "(Try checking your deposits now)";
                return new AbstractMap.SimpleEntry<>(false, desc);
            }
            case WITHDRAW_DEPOSITS: {
                MessageChannel channel = db.getResourceChannel(0);
                if (channel == null || !db.hasAlliance()) return null;
                for (Transaction2 transaction : transactions) {
                    if (transaction.receiver_id == (long) nation.getNation_id()) return null;
                }
                String desc = "You can request your funds by asking in <#" + channel.getIdLong() + ">\n\n" +
                        "Please request e.g. $1.";
                return new AbstractMap.SimpleEntry<>(false, desc);
            }
            case GATHER_INTEL: {
                if (nation.getMeta(NationMeta.INTERVIEW_SPYOP) != null) return null;
                String desc = "Please use the " + CM.spy.find.intel.cmd.toSlashMention() + " command for a spy target\n" +
                        "- go to their nation page, and click the espionage button\n" +
                        "- Copy the results and post them in any channel here (if you accidentally leave the page, the intel op still is in your notifications)\n\n" +
                        "Remember to purchase max spies every day";
                return new AbstractMap.SimpleEntry<>(false, desc);
            }
            case SPY_COMMAND: {
                if (nation.getMeta(NationMeta.INTERVIEW_SPIES) != null) return null;
                String desc = "Try using the commands e.g.:\n" +
                        "" + CM.nation.spies.cmd.create("https://politicsandwar.com/nation/id=6", null, null) + "\n";
                return new AbstractMap.SimpleEntry<>(false, desc);
            }
            case LOOT_COMMAND: {
                if (nation.getMeta(NationMeta.INTERVIEW_LOOT) != null) return null;
                String desc = "Try using the commands e.g.:\n" +
                        "" + CM.nation.loot.cmd.create("https://politicsandwar.com/nation/id=6", null, null).toSlashCommand() + "\n";
                return new AbstractMap.SimpleEntry<>(false, desc);
            }
            case GENERATE_CITY_BUILDS: {
                DBNation me = nation;
                if (me.getMeta(NationMeta.INTERVIEW_OPTIMALBUILD) != null) return null;
                double maxInfra = 0;
                Set<Integer> infraLevels = new HashSet<>();

                boolean oddInfraAmounts = false;
                boolean inefficientAmount = false;

                for (JavaCity city : cities.values()) {
                    double infra = city.getInfra();
                    maxInfra = Math.max(maxInfra, infra);
                    infraLevels.add((int) infra);
                    if (infra % 50 != 0) {
                        oddInfraAmounts = true;
                    }
                    if (infra % 100 != 0) {
                        inefficientAmount = true;
                    }
                }

                StringBuilder response = new StringBuilder();

                if (inefficientAmount) {
                    response.append("Infrastructure is cheapest when purchased in multiples of 100");
                }

                if (infraLevels.size() > 1) {
                    response.append("By having different amounts of infrastructure in each city, you cannot import the same build into all of them.\n");
                }

                int maxAllowed = me.getOff() > 0 || me.getDef() > 0 || me.getCities() < 10 ? 1700 : 2000;
                maxInfra = Math.min(maxAllowed, (50 * (((int) maxInfra + 49) / 50)));

                if (oddInfraAmounts) {
                    response.append("Each building requires 50 infrastructure to be built, but will continue to operate if infrastructure is lost. " +
                            "It is a waste to purchase infrastructure up to an amount that is not divisible by 50.\n" +
                            "Note: You can purchase up to a specified infra level by entering e.g. `@" + maxInfra + "`\n\n");
                }

                int[] mmr = {0, 0, 0, 0};
                if (me.getCities() < 10 || me.getOff() > 0) mmr[0] = 5;
                else mmr[2] = 5;
                if (me.getAvg_infra() > 1700) mmr[2] = 5;
                if (me.getCities() > 10 && me.getAvg_infra() > 1700 && mmr[0] == 0) mmr[1] = 2;

                response.append("Minimum military requirement (MMR) is what military buildings to have in a city and is in the format e.g. `mmr=1234` (1 barracks, 2 factories, 3 hangars, and 4 drydock) (don't actually use mmr=1234, this is an example)\n\n");

                Integer cityId = cities.keySet().iterator().next();
                String cityUrl = PnwUtil.getCityUrl(cityId);
                String mmrStr = StringMan.join(mmr, "");
                response.append("The " + CM.city.optimalBuild.cmd.toSlashMention() + " command can be used to generate a build for a city. Let's try the command now, e.g.:\n" +
                        "" + CM.city.optimalBuild.cmd.create(cityUrl,
                                null,
                                mmrStr,
                                null,
                                MathMan.format(maxInfra),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null).toSlashCommand());
                return new AbstractMap.SimpleEntry<>(false, response.toString());
            }
            case ROI: {
                if (nation.getMeta(NationMeta.INTERVIEW_ROI) != null) return null;

                String desc = "National Projects provide nation level benefits:\n" +
                        "<https://politicsandwar.com/nation/projects/>\n" +
                        "Cities (past your 10th) OR Projects can be purchased every 10 days. You start with 1 project slot, and get more for every 5k infra in your nation.\n\n" +
                        "To see which projects the bot recommends (for a 120 day period), use:\n" +
                        "> " + Settings.commandPrefix(true) + "roi {usermention} 120\n\n" +
                        "We recommend getting two resource projects after your 10th city";
                return new AbstractMap.SimpleEntry<>(false, desc);
            }
            case DAILY_SPYOPS: {
                ByteBuffer day = nation.getMeta(NationMeta.SPY_OPS_DAY);
                long dayVal = day == null ? 0 : day.getLong();
                long diff = TimeUtil.getDay() - dayVal;
                if (diff  <= 2) return null;

                String desc ="During Peace time, you can find targets to gather intel on using:\n" +
                        "" + CM.spy.find.intel.cmd.toSlashMention() + "\n" +
                        "During wartime, you can find enemies to spy using:\n" +
                        "" + CM.spy.find.target.cmd.create("enemies", "*", null, null, null, null) + "\n\n" +
                        "(You should conduct a spy op every day)";
                return new AbstractMap.SimpleEntry<>(diff, desc);
            }
//            case PLAN_A_RAID_WITH_FRIENDS:
//                return planRaid(nation);
//            case CREATE_A_WAR_ROOM:
//                return createWarRoom(nation);
        }
        throw new IllegalArgumentException("Unsupported: " + type);
    }

    private Map.Entry<Object, String> checkContinent(DBNation nation) {
        if (!Buildings.URANIUM_MINE.canBuild(nation.getContinent())) {
            Set<Continent> allowedContinents = new HashSet<>();
            for (Continent continent : Continent.values) {
                if (Buildings.URANIUM_MINE.canBuild(continent)) {
                    allowedContinents.add(continent);
                }
            }
            StringBuilder message = new StringBuilder("If you do not have a damaged build, and can easily switch, consider moving to a continent with Uranium. " +
                    "This would allow you to produce the resources needed to power your cities, which is useful under blockade. " +
                    "The AFRICA continent would allow you to be self sufficient for nuke production\n" +
                    "Options: " + StringMan.join(allowedContinents, ", ") +"\n" +
                    "Edit: <https://politicsandwar.com/nation/edit/>\n" +
                    "<https://politicsandwar.fandom.com/wiki/Resources#Natural_Resource_Availability_by_Continent>");
            return new AbstractMap.SimpleEntry<>(nation.getContinent(), message.toString());
        }
        return null;
    }

    private Map.Entry<Object, String> checkBlockade(DBNation nation) {
        Set<Integer> blockaders = nation.getBlockadedBy();
        if (blockaders.isEmpty()) return null;

        StringBuilder response = new StringBuilder();
        response.append(blockaders.size() + " nations are blockading you: ");
        String listStr = StringMan.join(blockaders.stream().map(f -> PnwUtil.getName(f, false)).collect(Collectors.toList()), ",");
        response.append(listStr).append("\n\n");

        response.append(Messages.BLOCKADE_HELP);

        return new AbstractMap.SimpleEntry<>(listStr, response.toString());
    }

    private Map.Entry<Object, String> checkWarchest(DBNation nation, Map<ResourceType, Double> stockpile, GuildDB db) {
        if (nation.getCities() < 10) return null;
        if (!db.getCoalition("enemies").isEmpty()) return null;
        Map<ResourceType, Double> perCity = db.getOrNull(GuildKey.WARCHEST_PER_CITY);
        if (perCity == null) return null;
        int airCap = nation.getCities() * Buildings.HANGAR.cap(nation::hasProject) * Buildings.HANGAR.max();
        double airPct = (double) nation.getAircraft() / airCap;
        if (airPct < 0.8) return null;
        Map<ResourceType, Double> required = PnwUtil.multiply(perCity, (double) nation.getCities());
        Map<ResourceType, Double> lacking = new LinkedHashMap<>();
        for (Map.Entry<ResourceType, Double> entry : required.entrySet()) {
            double lackingAmt = entry.getValue() - stockpile.getOrDefault(entry.getKey(), 0d);
            if (lackingAmt > 1) {
                lacking.put(entry.getKey(), lackingAmt);
            }
        }
        if (lacking.isEmpty()) return null;
        if (nation.isBlockaded()) return null;
        if (nation.getOff() > 0 || nation.getDef() > 0) return null;

        String message = "It is important to be able to fight a war with your military\n" +
                "The alliance recommends keeping the following on your nation: `" + PnwUtil.resourcesToString(required) + "`\n" +
                "You are lacking: `" + PnwUtil.resourcesToString(lacking) + "`";
        return new AbstractMap.SimpleEntry<>(lacking, message);
    }

    private Map.Entry<Object, String> planRaid(DBNation nation) {
        if (nation.getMeta(NationMeta.INTERVIEW_COUNTER) == null) {
            String desc = "Raiding/warring is always better with friends. Find a good target. Use the command\n" +
                    "" + CM.war.counter.nation.cmd.toSlashMention() + "\n" +
                    "And see who is online and in range to raid that person with you.";
            return new AbstractMap.SimpleEntry<>(1, desc);
        }
        return null;
    }

    private Map.Entry<Object, String> createWarRoom(DBNation nation) {
        if (nation.getMeta(NationMeta.INTERVIEW_WAR_ROOM) == null) {
            String desc = "War rooms are channels created to coordinate a war against an enemy target. They will be created automatically by the bot against active enemies.\n" +
                    "To manually create a war room, use: " + CM.war.room.create.cmd.toSlashMention() + "";
            return new AbstractMap.SimpleEntry<>(1, desc);
        }
        return null;
    }

    private Map.Entry<Object, String> checkHangar(DBNation nation, Map<Integer, JavaCity> cities) {
        if (nation.getAvg_infra() <= 1700) return null;
        if (nation.getOff() > 0 || nation.getDef() > 0) return null;
        if (nation.isBeige()) return null;
        if (nation.getCities() < 10) return null;

        Set<Integer> lessThan5 = new HashSet<>();
        Set<Integer> lessThan4 = new HashSet<>();
        double avgHangars = 0;
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            int hangars = entry.getValue().get(Buildings.HANGAR);
            avgHangars += hangars;

        }
        avgHangars /= cities.size();
        if (avgHangars <= 4) {
            String desc = "The following cities have < 5 hangars: ";
            return new AbstractMap.SimpleEntry<>(avgHangars, desc);
        }
        return null;
    }

    private Map.Entry<Object, String> checkNavy(DBNation nation, Map<Integer, JavaCity> cities) {
        if (nation.getCities() < 10) return null;
        if (nation.isBeige()) return null;
        if (nation.getShips() > 0) return null;
        if (nation.getNumWars() == 0) return null;
        for (DBWar war : nation.getActiveWars()) {
            DBNation other = war.getNation(!war.isAttacker(nation));
            if (other == null || other.getShips() > 1 || other.getAircraft() > nation.getAircraft()) return null;
        }
        String desc = "Buy ships to prevent yourself from being 1 shipped in a conflict";
        return new AbstractMap.SimpleEntry<>(0, desc);
    }

    private Map.Entry<Object, String> checkAircraft(DBNation nation, Map<Integer, JavaCity> cities, GuildDB db) {
        if (nation.getAvg_infra() < 1700) return null;
        if (nation.getOff() > 0 || nation.getDef() > 0) return null;
        if (nation.isBeige()) return null;
        if (nation.getCities() < 10) return null;
        if (!db.getCoalition("enemies").isEmpty()) return null;

        int pop = 0;
        int hangars = 0;
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            hangars += entry.getValue().get(Buildings.HANGAR);
            pop += entry.getValue().getPopulation(nation::hasProject);
        }

        double maxPlanes = Math.min(pop * 0.1, hangars * Buildings.HANGAR.cap(nation::hasProject));
        double threshold = maxPlanes * 0.9;
        if (nation.getAircraft() < threshold) {
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3);
            int previousAir = nation.getUnits(MilitaryUnit.AIRCRAFT, cutoff);
            if (previousAir == nation.getAircraft()) {
                String desc = "Planes can attack ground, air, or sea and are the best unit for defending your infra and allies. You can buy aircraft from the military tab. <https://politicsandwar.com/nation/military/aircraft/>";
                return new AbstractMap.SimpleEntry<>(nation.getAircraft() / (double) threshold, desc);
            }
        }
        return null;
    }

    private Map.Entry<Object, String> checkMAP(DBNation nation) {
        if (nation.getDef() > 0) return null;
        List<DBWar> maxMapWars = new ArrayList<>();
        for (DBWar war : nation.getActiveWars()) {
            if (war.getAttacker_id() != nation.getNation_id()) continue;
            if (war.getStatus() != WarStatus.ACTIVE) continue;
            DBNation defender = DBNation.getById(war.getDefender_id());
            if (defender == null || defender.getActive_m() < 2880) continue;
            Map.Entry<Integer, Integer> map = war.getMap(war.getAttacks2(false));
            if (map.getKey() >= 12) {
                maxMapWars.add(war);
            }
        }
        if (maxMapWars.isEmpty()) return null;

        List<Integer> warIds = maxMapWars.stream().map(f -> f.warId).collect(Collectors.toList());
        StringBuilder body = new StringBuilder();
        body.append("The following raids have 12 unused MAP:");
        for (DBWar war : maxMapWars) {
            body.append("\n").append("https://politicsandwar.com/nation/war/groundbattle/war=" + war.getWarId());
        }
        body.append("\nYou can get some of their $ by doing ground attacks, and then a portion of all their $/resources when you defeat them (as well as a cut of their alliance bank if they are in an alliance)");
        return new AbstractMap.SimpleEntry<>(warIds, body.toString());
    }

    private Map.Entry<Object, String> checkRaidTurnChange(DBNation me) {
        Set<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(me.getNation_id());
        wars.removeIf(w -> w.getAttacker_id() != me.getNation_id());

        for (DBWar war : wars) {
            long date = war.getDate();
            if (TimeUtil.getTurn(date) != TimeUtil.getTurn(date - 120000)) {
                return null;
            }
        }

        String cmd = CM.war.find.raid.cmd.create("*", "15", null, null, "12", null, null, null, null, null, null).toSlashCommand(false);
        String longDesc = "Let's declare on a target as they come off beige:\n" +
                "1. Use e.g. `" + cmd + "` to find a target that ends beige in the next 12 turns\n" +
                "2. Set a reminder on your phone, or on discord using " + CM.alerts.beige.beigeAlert.cmd.toSlashMention() + "\n" +
                "3. Get the war declaration page ready, and declare DURING turn change\n\n" +
                "*Note:*\n" +
                "- *If you don't get them on your first shot, try again later*\n" +
                "- *If you can't be active enough, just hit any gray nation during turn change*";

        return new AbstractMap.SimpleEntry<>(false, longDesc);
    }

    public static Map.Entry<Object, String> checkProductionBonus(DBNation nation, Map<Integer, JavaCity> cities) {
        Set<Integer> violationCityIds = new HashSet<>();
        for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
            int id = cityEntry.getKey();
            JavaCity city = cityEntry.getValue();

            Set<ResourceBuilding> uncapped = new HashSet<>();
            for (Building building : Buildings.values()) {
                if (!(building instanceof ResourceBuilding)) continue;
                int amt = city.get(building);
                if (amt > 0 && amt < building.cap(nation::hasProject)) {
                    uncapped.add((ResourceBuilding) building);
                }
            }

            if (uncapped.size() > 1) {
                violationCityIds.add(id);
            }
        }

        if (!violationCityIds.isEmpty()) {
            String message = "The following cities miss out on a production bonus by not having more of a single resource type " + StringMan.getString(violationCityIds);
            return new AbstractMap.SimpleEntry<>(violationCityIds, message);
        }
        return null;
    }

    private Map.Entry<Object, String> checkBuyRpc(GuildDB db, DBNation nation, Map<Integer, JavaCity> cities) {
        if (nation.getCities() > Projects.ACTIVITY_CENTER.maxCities()) return null;
        if (nation.getProjectTurns() > 0 || nation.getFreeProjectSlots() <= 0) return null;
        return new AbstractMap.SimpleEntry<>("1", "Go to the projects tab and buy the Activity Center");
    }

    private Map.Entry<Object, String> checkBuyProject(GuildDB db, DBNation nation, Map<Integer, JavaCity> cities) {
        int freeProjects = nation.projectSlots() - nation.getNumProjects();
        if (freeProjects <= 0) return null;

        if (nation.getProjectAbsoluteTurn() != null && nation.getProjectTurns() <= 0) {
            if (nation.isBlockaded()) return null;
            return new AbstractMap.SimpleEntry<>("1", "Your project timer is up. Use the #resource-request channel to request funds for a project");
        }
        return null;
    }

    private Map.Entry<Object, String> checkRevenue(DBNation nation) {
        double[] revenue = nation.getRevenue(12, true, true, false, false, false, false, 0d, false);

        double total = PnwUtil.convertedTotal(revenue, false);
        if (total < 0) {
            return new AbstractMap.SimpleEntry<>(total, "Your nation's city revenue is negative. Please fix your cities and/or ask a gov member for funds for a new build");
        }
        return null;
    }

    private Map.Entry<Object, String> checkBuyInfra(DBNation nation, Map<Integer, JavaCity> cities, GuildDB db) {
        boolean hasEnemies = !db.getCoalition(Coalition.ENEMIES).isEmpty();
        int minInfra = 1200;
        int grantTo = nation.getCities() < 10 ? 1200 : (hasEnemies ? 1500 : 1700);

        Map<Integer, Integer> grants = new HashMap<>();

        for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
            int id = cityEntry.getKey();
            JavaCity city = cityEntry.getValue();

            int required = city.getRequiredInfra();
            double actual = city.getInfra();

            if (required == actual) {
                if (actual < grantTo) {
                    grants.put(id, grantTo);
                }
            } else if (required < minInfra) {
                grants.put(id, grantTo);
            } else if (actual < 550) {
                grants.put(id, 550);
            }
        }
        if (grants.isEmpty()) return null;
        StringBuilder response = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : grants.entrySet()) {
            response.append(Messages.CITY_URL + entry.getKey() + " to @" + entry.getValue() + " infra\n");
        }
        if (hasEnemies || (nation.getCities() >= 10 && nation.getNumWarsAgainstActives() > 0)) {
            response.append("\nnote: do NOT repurchase infra unless the power plant is lost");
        }
        response.append("\nnote: buildings continue working even if you lose infra");
        if (nation.isBlockaded() || nation.getRelativeStrength() < 1) return null;
        return new AbstractMap.SimpleEntry<>(grants.keySet(), response.toString().trim());
    }

    private Map.Entry<Object, String> checkBuyLand(DBNation nation, Map<Integer, JavaCity> cities) {
        int minLand = 800;
        int maxLand = 2000;

        Map<Integer, Integer> grants = new HashMap<>();

        for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
            int id = cityEntry.getKey();
            JavaCity city = cityEntry.getValue();
            int grantLand = ((int) city.getInfra() / 133) * 100;
            grantLand = Math.min(maxLand, Math.max(grantLand, minLand));

            if (grantLand <= city.getLand()) continue;

            grants.put(id, grantLand);
        }
        if (grants.isEmpty()) return null;

        StringBuilder response = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : grants.entrySet()) {
            response.append(Messages.CITY_URL + entry.getKey() + " to @" + entry.getValue() + " land\n");
        }
        if (nation.isBlockaded()) return null;
        return new AbstractMap.SimpleEntry<>(grants.keySet(), response.toString().trim());
    }

    private Map.Entry<Object, String> checkBuyCity(DBNation nation) {
        int freeProjects = nation.projectSlots() - nation.getNumProjects();
        if (freeProjects > 0) return null;

        if (nation.getCities() < 10) {
            // raided 200m
            AttackCost cost = nation.getWarCost(false, false, false, false, false);
            double total = PnwUtil.convertedTotal(cost.getLoot(true));
            if (total < 200000000) {
                return null;
            }
        }
        if (nation.getCityTurns() <= 0 && nation.getCities() < 20) {
            if (nation.isBlockaded()) return null;

            double cost = PnwUtil.nextCityCost(nation.getCities(), true, nation.hasProject(Projects.URBAN_PLANNING), nation.hasProject(Projects.ADVANCED_URBAN_PLANNING), nation.hasProject(Projects.METROPOLITAN_PLANNING), nation.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY));
            Map<ResourceType, Double> resources = Collections.singletonMap(ResourceType.MONEY, cost);
            return new AbstractMap.SimpleEntry<>(nation.getCities(), "Your city timer is up. Use the #resource-request channel to request funds for a city");
        }
        return null;
    }

    private Map.Entry<Object, String> testIfCacheFails(Supplier<Map.Entry<Object, String>> supplier, boolean test) {
        return supplier.get();
    }

    private Map.Entry<Object, String> checkSendResources(DBNation nation, Map<ResourceType, Double> resources, Map<Integer, JavaCity> cities) {
        Map<ResourceType, Double> required = new HashMap<>();

        for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
            JavaCity city = cityEntry.getValue();
            Map<ResourceType, Double> cityProfit = PnwUtil.resourcesToMap(city.profit(nation.getContinent(), nation.getRads(), -1L, nation::hasProject, null, nation.getCities(), nation.getGrossModifier(), 12));
            for (Map.Entry<ResourceType, Double> entry : cityProfit.entrySet()) {
                if (entry.getValue() < 0) {
                    required.put(entry.getKey(), required.getOrDefault(entry.getKey(), 0d) - entry.getValue());
                }
            }
        }

        Map<ResourceType, Double> toSend = new HashMap<>();
        for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
            double diff = required.getOrDefault(entry.getKey(), 0d) - entry.getValue();
            if (diff > 0) {
                toSend.put(entry.getKey(), diff);
            }
        }
        if (!toSend.isEmpty()) {
            return new AbstractMap.SimpleEntry<>(toSend, "Requires: " + PnwUtil.resourcesToString(toSend));
        }
        return null;
    }

    private Map.Entry<Object, String> checkSafekeep(DBNation nation, Map<ResourceType, Double> resources, Map<Integer, JavaCity> cities) {
        if (nation.isBlockaded()) return null;

        double factor = 3;

        Map<ResourceType, Double> required = PnwUtil.multiply(db.getPerCityWarchest(), (double) nation.getCities());

        for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
            JavaCity city = cityEntry.getValue();
            Map<ResourceType, Double> cityProfit = PnwUtil.resourcesToMap(city.profit(nation.getContinent(), nation.getRads(), -1L, nation::hasProject, null, nation.getCities(), nation.getGrossModifier(), 12));
            for (Map.Entry<ResourceType, Double> entry : cityProfit.entrySet()) {
                if (entry.getValue() < 0) {
                    required.put(entry.getKey(), required.getOrDefault(entry.getKey(), 0d) - entry.getValue() * 7);
                }
            }
        }

        required.put(ResourceType.MONEY, 1000000d * nation.getCities());

        double convertedTotal = PnwUtil.convertedTotal(resources);

        resources = new HashMap<>(resources);
        for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
            double excess = entry.getValue() - required.getOrDefault(entry.getKey(), 0d) * factor;
            if (excess > 0 && entry.getKey() != ResourceType.CREDITS) entry.setValue(excess);
            else entry.setValue(0d);
        }

        resources.entrySet().removeIf(e -> e.getValue() <= 0);

        double excessTotal = PnwUtil.convertedTotal(resources);
        if (excessTotal > 1000000L * nation.getCities()) {
            if (nation.isBlockaded()) return null;
            String url = nation.getAllianceUrl() + "&display=bank";
            String message = "Excess resources can be deposited so you don't lose it in a war or attract pirates: `" + PnwUtil.resourcesToString(resources) + "` @ <" + url + ">";
            return new AbstractMap.SimpleEntry<>(PnwUtil.resourcesToString(resources), message);
        }
        return null;
    }

    private Map.Entry<Object, String> checkInactive(DBNation nation) {
        long daysInactive = TimeUnit.MINUTES.toDays(nation.getActive_m());
        if (daysInactive > 1) {
            String message = "Hasn't logged in for " + daysInactive + " days.";
            return new AbstractMap.SimpleEntry<>(daysInactive, message);
        }
        return null;
    }

    private Map.Entry<Object, String> checkObjectives(DBNation nation) {
        if (nation.getCities() == 1) {
            String message = "You can go to <" + Settings.INSTANCE.PNW_URL() + "/nation/objectives/>" + " and complete the objectives for some easy cash.";
            return new AbstractMap.SimpleEntry<>(true, message);
        }
        return null;
    }

    private Map.Entry<Object, String> checkWarPolicy(DBNation nation) {
        WarPolicy policy = nation.getWarPolicy();
        switch (policy) {
            case PIRATE:
                break;
            case FORTRESS:
                if (nation.getOff() == 0) break;
            default:
                String msg;
                if (nation.getOff() != 0 || nation.getCities() < 10) {
                    msg = "`pirate` to increase loot by 40%";
                } else {
                    msg = "`fortress` to reduce enemy MAP by 1";
                }
                msg = "You can go to <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>" + " and change your war policy to " + msg;
                return new AbstractMap.SimpleEntry<>(policy, msg);
        }
        return null;
    }

    private Map.Entry<Object, String> checkOffensiveSlots(DBNation nation, GuildDB db) {
        if (nation.getOff() >= 4) return null;
        Set<Integer> enemyAAs = db.getCoalition(Coalition.ENEMIES);
        Set<DBNation> targets = new LinkedHashSet<>();

        double score = nation.getScore();
        double maxScore = score * PnwUtil.WAR_RANGE_MAX_MODIFIER;
        double minScore = score * 0.75;

        boolean hasEnemies = false;
        boolean hasRaids = false;
        if (!enemyAAs.isEmpty()) {
            for (DBNation enemy : Locutus.imp().getNationDB().getNations(enemyAAs)) {
                if (enemy.getScore() >= score * 1.25 || enemy.getScore() <= minScore) continue;
                if (enemy.getVm_turns() != 0) continue;
                if (enemy.getCities() > nation.getCities() * 1.5) continue;
                if (enemy.getDef() >= 3) continue;
                if (enemy.hasUnsetMil()) continue;
                if (enemy.getAircraft() > nation.getAircraft()) continue;
                if (enemy.getGroundStrength(true, true) > nation.getGroundStrength(true, false)) continue;
                if (enemy.isBeige()) continue;
                targets.add(enemy);
                hasEnemies = true;
            }
        }
        if ((enemyAAs.isEmpty() && nation.getOff() < 4) || nation.getOff() < 2) {
            for (Map.Entry<Integer, DBNation> entry : Locutus.imp().getNationDB().getNations().entrySet()) {
                DBNation enemy = entry.getValue();
                if (enemy.getScore() >= maxScore || enemy.getScore() <= minScore) continue;
                if (enemy.getVm_turns() != 0) continue;
                if (enemy.getDef() >= 3) continue;
                if (enemy.hasUnsetMil()) continue;
                if (enemy.getActive_m() < 10000) continue;
                if (enemy.getAlliance_id() != 0) continue;
                if (enemy.isBeige()) continue;
                if (enemy.getAircraft() > nation.getAircraft() * 0.33 &&
                        enemy.getShips() > nation.getShips() * 0.33 &&
                        enemy.getSoldiers() + enemy.getTanks() * 22 > nation.getSoldiers()
                ) continue;
                targets.add(enemy);
                hasRaids = true;

            }
        }
        if (nation.getOff() >= targets.size() || targets.isEmpty()) return null;
        StringBuilder resposnse = new StringBuilder("You have " + (5 - nation.getOff()) + " free offensive slots. ");
        if (hasEnemies && nation.getOff() < 3) {
            String warPriority = CM.war.find.enemy.cmd.create(null, null, null, null, null, null, null, "true", null, null, null).toSlashCommand(false);
            resposnse.append("Please use " + warPriority+ " or " + CM.war.find.enemy.cmd.toSlashMention() + "");
        } else hasEnemies = false;
        if (hasRaids) {
            if (hasEnemies) resposnse.append("Please use ");
            else resposnse.append("or ");

            String cmd = CM.war.find.raid.cmd.create("*", null, null, null, null, null, null, null, null, null, null).toSlashCommand(false);
            resposnse.append("`" + cmd + "` ");
        }
        resposnse.append("for some juicy targets");
        return new AbstractMap.SimpleEntry<>(targets.size(), resposnse.toString());
    }

    private Map.Entry<Object, String> checkSpies(DBNation nation) {
        int maxSpies = nation.getSpyCap();
        Integer currentSpies = nation.getSpies();
        if (currentSpies == null || currentSpies >= maxSpies) return null;


        boolean buySpies = nation.getSpies() == 0 || nation.daysSinceLastSpyBuy() > 1;
        if (!buySpies) return null;
        String message = "You have " + nation.getSpies() + " spies. Spies can perform various operations, (like destroying planes), and can do so daily, without using a war slot, or bringing you out of beige. You should always purchase max spies every day";
        return new AbstractMap.SimpleEntry<>(nation.getSpies(), message);
    }

    private Map.Entry<Object, String> checkTanks(DBNation nation, Map<Integer, JavaCity> cities) {
        int numCities = nation.getCities();
        if (numCities >= 10) {
            return null;
        }
        int max = numCities * 0;
        String message = "";
        if (nation.getTanks() > max) {
            message = "You have " + nation.getTanks() + " tanks.";
        } else {
            Set<Integer> hasFactories = new HashSet<>();
            for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
                if (entry.getValue().get(Buildings.FACTORY) > 0) {
                    hasFactories.add(entry.getKey());
                }
            }
            if (!hasFactories.isEmpty()) {
                String also = message.isEmpty() ? "" : "also ";
                message += "You " + also + "have factories in the following cities: " + StringMan.getString(hasFactories) + ".";
            }
        }
        if (message.isEmpty()) message = null;
        else message += "Tanks are costly to build, maintain, easy to destroy with planes, and raise your score/soldiers ratio. For ground attacks you should be using soldiers, which can optionally attack without using munitions.";
        return new AbstractMap.SimpleEntry<>(nation.getTanks(), message);
    }

    private Map.Entry<Object, String> checkShips(DBNation nation) {
        int cities = nation.getCities();
        int max = cities * 5;
        String message = null;
        if (nation.getShips() > max) {
            message = "You have " + nation.getShips() + " ships. Ships are costly to build, maintain, easy to destroy with planes, and raise your score/soldiers ratio. For maximum profit you should only need 1 drydock and 1 boat for raiding purposes.";
        }
        return new AbstractMap.SimpleEntry<>(nation.getShips(), message);
    }

    private Map.Entry<Object, String> checkTradeColor(DBNation nation) {
        NationColor allianceColor = nation.getAlliance().getColor();
        Set<NationColor> allowedColors = new HashSet<>(Arrays.asList(NationColor.BEIGE));
        allowedColors.add(allianceColor);
        NationColor color = nation.getColor();

        String message = null;
        if (!allowedColors.contains(color)) {
            message = "You can go to <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>" + " and change your trade bloc from " + color + " to " + allianceColor.name() + " (for trade block revenue)";
            if (!nation.isGray() && !nation.isBeige()) {
                message += "\nnote: You do not receive trade bloc income by being on a different color to the alliance";
            }
        }
        return new AbstractMap.SimpleEntry<>(nation.getColor(), message);
    }
//
//    private Map.Entry<Object, String> checkBarracks(Map.Entry<Integer, JavaCity> entry) {
//        JavaCity city = entry.getValue();
//        if (city.get(Buildings.BARRACKS) != 5) {
//            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " does not have max barracks, reducing your potential raid loot.";
//        }
//        return null;
//    }
//
//    private Map.Entry<Object, String> checkFactory(DBNation nation, Map.Entry<Integer, JavaCity> entry) {
//        JavaCity city = entry.getValue();
//        int amt = city.get(Buildings.FACTORY);
//        if (amt > 2) {
//            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " has " + amt + " factories. Tanks are costly to build, maintain, easy to destroy with planes, and raise your score/soldiers ratio.";
////        } else if (amt < target) {
////            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " has " + amt + " factories. " + target + " factories with max tanks may be useful to help win ground control if you get countered.";
//        }
//        return null;
//    }
//
//    private Map.Entry<Object, String> checkShipyard(Map.Entry<Integer, JavaCity> entry) {
//        JavaCity city = entry.getValue();
//        if (city.get(Buildings.DRYDOCK) > 2) {
//            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " has more than 1 drydock. Ships are costly to build, maintain, easy to destroy with planes, and raise your score/soldiers ratio. For maximum profit you should only need 1 drydock and 1 boat for raiding purposes.";
//        }
//        return null;
//    }
//
//    private Map.Entry<Object, String> checkFarms(Map.Entry<Integer, JavaCity> entry) {
//        JavaCity city = entry.getValue();
//        if (city.get(Buildings.FARM) > 0 && city.getInfra() <= 3000) {
//            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " has a farm, which is unprofitable. It is much more cost effective to buy food on the market and instead switch to profitable manufacturing buildings";
//        }
//        return null;
//    }
//
//    public Map.Entry<Object, String> checkInfra(DBNation nation, Map.Entry<Integer, JavaCity> entry) {
//        JavaCity city = entry.getValue();
//        if (nation.getCities() < 10 && city.getInfra() > 1000) {
//            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " has more than 1000 infra making you a good target and raises your score/city and thus score/soldier ratio, reducing raid loot.";
//        }
//        if (nation.getCities() > 12 && city.getInfra() < 1500 && nation.getOff() < 3 && nation.getDef() != 0) {
//            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " has less than 1500 infra, reducing your econ profit.";
//        }
//        return null;
//    }

    public Map.Entry<Object, String> checkMMR(DBNation nation, Map<Integer, JavaCity> cities, Map<NationFilter, MMRMatcher> requiredMmrMap) {

        Set<String> allowedMMr = new HashSet<>();

        String myMMR = null;
        for (Map.Entry<NationFilter, MMRMatcher> entry : requiredMmrMap.entrySet()) {
            NationFilter nationMatcher = entry.getKey();
            if (myMMR == null) {
                myMMR = nation.getMMRBuildingStr();
            }
            MMRMatcher required = entry.getValue();
            if (required.test(myMMR)) {
                return null;
            } else {
                if (nationMatcher.toCached(10000).test(nation)) {
                    allowedMMr.add(required.getRequired());
                }
            }
        }

        if (allowedMMr.isEmpty()) return null;

        StringBuilder result = new StringBuilder();
        result.append("The allowed/recommended MMR to be on are the following:");
        for (String mmr : allowedMMr) {
            result.append("\nmmr=" + mmr + " (");
            String prefix = "";
            for (int i =0 ;i < mmr.length(); i++) {
                Building building = Buildings.get(i + Buildings.BARRACKS.ordinal());
                String amt = Character.isDigit(mmr.charAt(i)) ? ("" + mmr.charAt(i)) : "any";
                result.append(prefix + amt + " " + building.nameSnakeCase());
                prefix = ", ";
            }
            result.append(" in each city)");
        }
        if (allowedMMr.size() > 1) {
            result.append("\n\nNote: Contact a gov member if you are unsure which one is suitable");
        }

        return new AbstractMap.SimpleEntry<>(nation.getMMRBuildingStr(), result.toString());
    }

    public Map.Entry<Object, String> checkPlaneBuy(DBNation nation, Map<Integer, JavaCity> cities) {
        Set<Integer> citiesMissingHangars = new HashSet<>();
        Set<Integer> citiesHavingHangars = new HashSet<>();
        int numHangars = 0;
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            JavaCity city = entry.getValue();
            numHangars += city.get(Buildings.HANGAR);
            if (nation.getCities() >= 10 && nation.getAvg_infra() > 1000) {
                if (city.get(Buildings.HANGAR) < 5 && city.get(Buildings.HANGAR) > 1) {
                    citiesMissingHangars.add(entry.getKey());
                }
            } else if (nation.getCities() < 10 && nation.getAvg_infra() <= 1000) {
                if (city.get(Buildings.HANGAR) > 1) {
                    citiesHavingHangars.add(entry.getKey());
                }
            }
        }

        if (!citiesMissingHangars.isEmpty()) {
            return new AbstractMap.SimpleEntry<>("BUY", "The following cities have less than 5 hangars: " + StringMan.getString(citiesMissingHangars));
        }

        if (!citiesHavingHangars.isEmpty()) {
            return new AbstractMap.SimpleEntry<>("SELL", "The following cities have more than 0 hangars: " + StringMan.getString(citiesHavingHangars));
        }

        if (nation.getCities() <= 10 || nation.getOff() != 0 & nation.getDef() != 0) {
            return null;
        }
        try {
            int pop = 0;
            for (JavaCity city : cities.values()) {
                pop += city.getPopulation(nation::hasProject);
            }

            String message = null;

            double maxPlanes = Math.min(numHangars * 15, pop * 0.95 * 0.001);
            Integer currentAircraft = nation.getAircraft();
            int previousPlanes = nation.getUnits(MilitaryUnit.AIRCRAFT, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
            if (previousPlanes == currentAircraft && currentAircraft < maxPlanes) {
                double canBuy = Math.min(numHangars * 15, pop * 0.001);
                message = "You can purchase up to " + (int) canBuy + " aircraft.";
            }
            return new AbstractMap.SimpleEntry<>("BUY", message);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map.Entry<Object, String> checkSoldierBuy(DBNation nation, Map<Integer, JavaCity> cities) {
        int numBarracks = 0;
        Set<Integer> citiesMissingBarracks = new HashSet<>();
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            JavaCity city = entry.getValue();
            int amt = city.getRequiredInfra() > city.getInfra() ? 4 : 5;
            if (city.get(Buildings.BARRACKS) < amt && (city.getRequiredInfra() <= city.getInfra() || city.getRequiredInfra() <= 1000) && (nation.getCities() < 10 || nation.getNumWars() > 0 || !db.getCoalitionRaw(Coalition.ENEMIES).isEmpty())) {
                citiesMissingBarracks.add(entry.getKey());
            }
            numBarracks += city.get(Buildings.BARRACKS);
        }

        if (!citiesMissingBarracks.isEmpty()) {
            return new AbstractMap.SimpleEntry<>(nation.getAircraft(), "The following cities have less than 5 barracks: " + StringMan.getString(citiesMissingBarracks) + " ");
        }

        if (nation.getCities() >= 10 && nation.getNumWars() != 0 && nation.getAvg_infra() > 1000) {
            return null;
        }
        try {
            int pop = 0;
            for (JavaCity city : cities.values()) {
                pop += city.getPopulation(nation::hasProject);
            }

            String message = null;

            double maxSoldiers = Math.min(3000 * numBarracks, pop * 0.95 * 0.15);
            Integer currentSoldiers = nation.getSoldiers();
            int previousSoldiers = nation.getUnits(MilitaryUnit.SOLDIER, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
            if (previousSoldiers == currentSoldiers && currentSoldiers < maxSoldiers) {
                double canBuy = Math.min(pop * 0.15, 3000 * numBarracks);
                message = "You can purchase up to " + (int) canBuy + " soldiers.";
            }
            return new AbstractMap.SimpleEntry<>(nation.getAircraft(), message);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Map.Entry<Object, String> checkOverpowered(Map<Integer, JavaCity> cities) {
        Set<Integer> violationCityIds = new HashSet<>();
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            JavaCity city = entry.getValue();
            int minPower = 2000;
            int excessPower = city.getPoweredInfra() - city.getRequiredInfra();
            if (city.get(Buildings.OIL_POWER) != 0 || city.get(Buildings.OIL_POWER) != 0) minPower = 500;
            if (city.get(Buildings.WIND_POWER) != 0) minPower = 250;
            if (excessPower > minPower) {
                violationCityIds.add(entry.getKey());
            }
        }
        if (!violationCityIds.isEmpty()) {
            String message = "The following cities have more power buildings than necessary " + StringMan.getString(violationCityIds);
            return new AbstractMap.SimpleEntry<>(violationCityIds, message);
        }
        return null;
    }

    public static Map.Entry<Object, String> checkExcessService(DBNation nation, Map<Integer, JavaCity> cities, ServiceBuilding building, GuildDB db) {
        if (db != null && !db.getCoalition("enemies").isEmpty()) return null;

        Set<Integer> violationCityIds = new HashSet<>();
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            JavaCity city = entry.getValue();
            int amt = city.get(building);
            if (amt > 1) {
                JavaCity copy = new JavaCity(city);
                copy.set(building, amt - 1);

                if (city.getDisease(nation::hasProject) >= (copy.getDisease(nation::hasProject)) &&
                        city.getCrime(nation::hasProject) >= (copy.getCrime(nation::hasProject))) {

                    violationCityIds.add(entry.getKey());
                }
            }

        }
        if (!violationCityIds.isEmpty()) {
            String message = "The following cities have more " + building.name() + " than necessary " + StringMan.getString(violationCityIds);
            return new AbstractMap.SimpleEntry<>(violationCityIds, message);
        }
        return null;
    }

    public static Map.Entry<Object, String> checkUnpowered(DBNation nation, Map<Integer, JavaCity> cities) {
        Set<Integer> unpoweredInfra = new HashSet<>();
        Set<Integer> unpoweredRss = new HashSet<>();
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            JavaCity city = entry.getValue();
            JavaCity.Metrics metrics = city.getCachedMetrics();
            if (city.getInfra() > city.getPoweredInfra()) {
                unpoweredInfra.add(entry.getKey());
            } else if (metrics != null && metrics.powered != null && !metrics.powered) {
                unpoweredRss.add(entry.getKey());
            }
        }
        if (unpoweredInfra.isEmpty() && unpoweredRss.isEmpty()) return null;

        Set<Integer> unpowered = new HashSet<>();
        unpowered.addAll(unpoweredInfra);
        unpowered.addAll(unpoweredRss);

        StringBuilder response = new StringBuilder();
        if (!unpoweredInfra.isEmpty()) {
            response.append("The following cities are unpowered (insufficient power buildings) " + StringMan.getString(unpoweredInfra));
        }
        if (!unpoweredRss.isEmpty()) {
            double[] revenue = PnwUtil.getRevenue(null, 12, nation, cities.values(), true, false, false, true, false, 0d);
            for (int i = 0; i < revenue.length; i++) {
                if (revenue[i] >= 0) revenue[i] = 0;
                else revenue[i] = -revenue[i];
            }
            if (response.length() > 0) response.append("\n\n");
            response.append("The following cities are unpowered (insufficient resources) " + StringMan.getString(unpowered) +
                    "\nPlease ensure you have the resources to power your city for several days. " +
                    "You currently consume the following each day:\n" + PnwUtil.resourcesToString(revenue));
        }
        return new AbstractMap.SimpleEntry<>(unpowered, response.toString());
    }

    public static Map.Entry<Object, String> checkNuclearPower(Map<Integer, JavaCity> cities) {
        Set<Integer> unpowered = new HashSet<>();
        Set<Integer> nonNuclear = new HashSet<>();
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            JavaCity city = entry.getValue();
            if (city.getInfra() > city.getPoweredInfra()) {
                unpowered.add(entry.getKey());
            } else {
                double infra = city.getInfra();
                while (infra > 0) {
                    if (infra > 550) {
                        infra -= 2000;
                        if (city.get(Buildings.OIL_POWER) > 0) {
                            nonNuclear.add(entry.getKey());
                        }
                        if (city.get(Buildings.COAL_POWER) > 0) {
                            nonNuclear.add(entry.getKey());
                        }
                    }
                    else if (infra > 300) {
                        infra -= 500;
                        if (city.get(Buildings.WIND_POWER) > 0) {
                            nonNuclear.add(entry.getKey());
                        }
                    } else {
                        infra -= 250;
                    }
                }
            }
        }
        HashSet<Integer> combined = new LinkedHashSet<>();
        combined.addAll(unpowered);
        combined.addAll(nonNuclear);
        if (!combined.isEmpty()) {
            String message = "The following cities should use nuclear power " + StringMan.getString(combined);
            return new AbstractMap.SimpleEntry<>(combined, message);
        }
        return null;
    }
//    public Map.Entry<Object, String> checkPollution(Map.Entry<Integer, JavaCity> entry) {
//        JavaCity city = entry.getValue();
//        double pollution = city.getPollution();
//        if (pollution > 45 && city.get(Buildings.SUBWAY) == 0 && city.get(Buildings.RECYCLING_CENTER) == 0) {
//            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " has high pollution, and no subway or recycling center.";
//        }
//        return null;
//    }
//
//    public Map.Entry<Object, String> checkCrime(Map.Entry<Integer, JavaCity> entry) {
//        JavaCity city = entry.getValue();
//        double crime = city.getCrime();
//        if (crime > 2.5 && city.get(Buildings.POLICE_STATION) == 0) {
//            message = "<" + PnwUtil.getCityUrl(entry.getKey()) + ">" + " has a high crime rate, and no police station.";
//        }
//        return null;
//    }
//
    public static Map.Entry<Object, String> checkEmptySlots(Map<Integer, JavaCity> cities) {
        Set<Integer> empty = new LinkedHashSet<>();
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            JavaCity city = entry.getValue();
            if (city.getFreeSlots() > 0) {
                empty.add(entry.getKey());
            }
        }
        if (!empty.isEmpty()) {
            String message = "The following cities have free slots " + StringMan.getString(empty);
            return new AbstractMap.SimpleEntry<>(empty, message);
        }
        return null;
    }
}
