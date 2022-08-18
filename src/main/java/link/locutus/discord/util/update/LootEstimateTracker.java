//package link.locutus.discord.util.update;
//
//import com.google.common.eventbus.Subscribe;
//import com.politicsandwar.graphql.model.BBGame;
//import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
//import link.locutus.discord.Locutus;
//import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
//import link.locutus.discord.apiv1.enums.AttackType;
//import link.locutus.discord.apiv1.enums.MilitaryUnit;
//import link.locutus.discord.apiv1.enums.city.building.Building;
//import link.locutus.discord.db.entities.DBCity;
//import link.locutus.discord.db.entities.DBNation;
//import link.locutus.discord.db.entities.LootEntry;
//import link.locutus.discord.db.entities.NationMeta;
//import link.locutus.discord.db.entities.TaxBracket;
//import link.locutus.discord.event.bank.TransactionEvent;
//import link.locutus.discord.event.baseball.BaseballGameEvent;
//import link.locutus.discord.event.city.CityBuildingChangeEvent;
//import link.locutus.discord.event.city.CityCreateEvent;
//import link.locutus.discord.event.city.CityInfraBuyEvent;
//import link.locutus.discord.event.city.CityInfraSellEvent;
//import link.locutus.discord.event.city.CityLandBuyEvent;
//import link.locutus.discord.event.city.CityLandSellEvent;
//import link.locutus.discord.event.game.TurnChangeEvent;
//import link.locutus.discord.event.nation.NationChangeActiveEvent;
//import link.locutus.discord.event.nation.NationChangeUnitEvent;
//import link.locutus.discord.event.nation.NationCreateProjectEvent;
//import link.locutus.discord.event.war.AttackEvent;
//import link.locutus.discord.util.PnwUtil;
//import link.locutus.discord.util.TimeUtil;
//import link.locutus.discord.apiv1.enums.ResourceType;
//
//import java.nio.ByteBuffer;
//import java.util.ArrayDeque;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.Deque;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Consumer;
//import java.util.function.Function;
//import java.util.function.Supplier;
//
//public class LootEstimateTracker {
//    private final Map<Integer, LootEstimate> nationLootMap = new Int2ObjectOpenHashMap<>();
//    private final long queueBufferMs;
//    private final boolean allowQueue;
//    private final Consumer<Map<Integer, LootEstimate>> save;
//    private final Function<Integer, DBNation> nationFactory;
//
//    public LootEstimateTracker(boolean allowQueue, long queueBufferMS, Supplier<Map<Integer, LootEstimate>> load, Consumer<Map<Integer, LootEstimate>> save, Function<Integer, DBNation> nationFactory) {
//        this.allowQueue = allowQueue;
//        this.queueBufferMs = queueBufferMS;
//
//        this.save = save;
//        this.nationFactory = nationFactory;
//
//        loadLootEstimates(load.get());
//    }
//
//    public void update() {
//        long cutoff = System.currentTimeMillis() - queueBufferMs;
//        Map<Integer, LootEstimate> toUpdate = new HashMap<>();
//        Map<Integer, LootEstimate> toSave = new HashMap<>();
//        synchronized (nationLootMap) {
//            // remove deleted nations
//            for (Map.Entry<Integer, LootEstimate> entry : nationLootMap.entrySet()) {
//                int nationId = entry.getKey();
//                LootEstimate estimate = entry.getValue();
//
//                synchronized (estimate) {
//                    DBNation nation = nationFactory.apply(entry.getKey());
//                    if (nation == null) {
//                        estimate.markDeleted();
//                        toSave.put(nationId, estimate);
//                        continue;
//                    }
//                    if (estimate.hasQueue(cutoff)) {
//                        toUpdate.put(nationId, estimate);
//                    }
//                }
//            }
//            for (Map.Entry<Integer, LootEstimate> entry : toSave.entrySet()) {
//                nationLootMap.remove(entry.getKey());
//            }
//        }
//        for (Map.Entry<Integer, LootEstimate> entry : toUpdate.entrySet()) {
//            LootEstimate estimate = entry.getValue();
//            if (estimate.flushOrders(cutoff)) {
//                toSave.put(entry.getKey(), estimate);
//            }
//        }
//        if (!toSave.isEmpty()) {
//            save.accept(toSave);
//        }
//    }
//
//    public void add(int nationId, long date, double minAndMaxMoney) {
//        add(nationId, date, minAndMaxMoney, ResourceType.MONEY);
//    }
//
//    public void add(int nationId, long date, double minAndMax, ResourceType type) {
//        if (minAndMax == 0) return;
//        add(nationId, date, type.toArray(minAndMax));
//    }
//
//    public void add(int nationId, long date, double[] minAndMax) {
//        add(nationId, date, minAndMax, minAndMax);
//    }
//
//    public void add(int nationId, long date, double[] min, double[] max) {
//        if (ResourceType.isEmpty(min) && ResourceType.isEmpty(max)) return;
//        LootEstimate estimate = getOrCreate(nationId);
//        if (max == null) max = min;
//
//        if (date <= 0) date = System.currentTimeMillis();
//        Order order = new Order(min, max, date);
//        if (allowQueue) {
//            estimate.addOrder(order);
//        } else {
//            estimate.flushOrder(order);
//        }
//    }
//
//    private LootEstimate getOrCreate(int nationId) {
//        DBNation nation = nationFactory.apply(nationId);
//        if (nation == null) return null;
//
//        boolean newInstance = false;
//        LootEstimate existing;
//        synchronized (nationLootMap) {
//            existing = nationLootMap.get(nationId);
//            if (existing == null) {
//                nationLootMap.put(nationId, existing = new LootEstimate());
//                newInstance = true;
//            }
//        }
//        if (newInstance) {
//            LootEntry loot = nation.getBeigeLoot();
//            if (loot != null) {
//                existing.min = loot.getTotal_rss().clone();
//                existing.max = loot.getTotal_rss().clone();
//
//                // TODO turns revenue until now
//                existing.lastUpdated = loot.getDate();
//            } else {
//                existing.min = ResourceType.getBuffer();
//                existing.max = ResourceType.getBuffer();
//                existing.lastUpdated = System.currentTimeMillis();
//            }
//            save.accept(Collections.singletonMap(nation.getId(), existing));
//        }
//        return existing;
//    }
//
//    public boolean loadLootEstimates(Map<Integer, LootEstimate> estimates) {
//        Map<Integer, LootEstimate> toDelete = new HashMap<>();
//        for (Map.Entry<Integer, LootEstimate> entry : estimates.entrySet()) {
//            LootEstimate estimate = entry.getValue();
//            int nationId = entry.getKey();
//            DBNation nation = nationFactory.apply(nationId);
//            if (nation == null) {
//                estimate.markDeleted();
//                toDelete.put(nationId, estimate);
//            } else {
//                nationLootMap.put(nationId, estimate);
//            }
//        }
//        if (!toDelete.isEmpty()) {
//            save.accept(toDelete);
//        }
//        return true;
//    }
//
//    public static class LootEstimate {
//        public double[] min;
//        public double[] max;
//
//        public double[] revenueCache;
//        public long lastUpdated;
//
//        private Deque<Order> orders = null;
//
//        public LootEstimate() {
//            this.min = ResourceType.getBuffer();
//            this.max = ResourceType.getBuffer();
//        }
//
//        public void markDeleted() {
//            this.min = min;
//            this.max = max;
//            this.revenueCache = null;
//            this.orders = null;
//        }
//
//        public boolean isDeleted() {
//            return min == null;
//        }
//
//        public void addOrder(Order order) {
//            if (this.orders == null) {
//                this.orders = new ArrayDeque<>();
//            }
//            this.orders.add(order);
//        }
//
//        public LootEstimate flushOrder(Order order) {
//            min = PnwUtil.add(min, order.min);
//            max = PnwUtil.add(max, order.max);
//            return this;
//        }
//
//        public LootEstimate ceilZero() {
//            for (int i = 0; i < min.length; i++) {
//                if (min[i] < 0) min[i] = 0;
//                if (max[i] < 0) max[i] = 0;
//            }
//            return this;
//        }
//
//        public Map.Entry<double[], double[]> getLootEstimateRange(boolean includeOrders) {
//            double[] min = this.min;
//            double[] max = this.max;
//            if (includeOrders && orders != null && !orders.isEmpty()) {
//                min = min.clone();
//                max = max.clone();
//                for (Order order : orders) {
//                    min = PnwUtil.add(min, order.min);
//                    max = PnwUtil.add(max, order.max);
//                }
//            }
//            return Map.entry(min, max);
//        }
//
//        public boolean flushOrders(long maxDate) {
//            if (orders != null) {
//                List<Order> ordersSorted = null;
//                for (Order order : orders) {
//                    if (order.date < maxDate) {
//                        if (ordersSorted == null) ordersSorted = new ArrayList<>(orders.size());
//                        ordersSorted.add(order);
//                    }
//                }
//                // nothing to flush
//                if (ordersSorted == null) return false;
//
//                // If some orders were not included
//                if (orders.size() > ordersSorted.size()) {
//                    orders.removeAll(ordersSorted);
//                } else {
//                    orders = null;
//                }
//
//                if (ordersSorted.size() > 1) {
//                    ordersSorted.sort(Comparator.comparingLong(o -> o.date));
//                }
//                for (Order order : ordersSorted) {
//                    flushOrder(order).ceilZero();
//                }
//                return true;
//            }
//            return false;
//        }
//
//        public boolean hasQueue(long cutoff) {
//            if (orders == null || orders.isEmpty()) {
//                return false;
//            }
//            for (Order order : orders) {
//                if (order.date < cutoff) return true;
//            }
//            return false;
//        }
//    }
//
//    public class Order {
//        public final double[] min;
//        public final double[] max;
//        public final long date;
//
//        public Order(double[] min, double[] max, long date) {
//            this.min = min;
//            this.max = max;
//            this.date = date;
//        }
//    }
//
//    /*
//        Nation loot info
//
//        Buy infra
//        Sell infra
//        Buy land
//        Sell land
//        Sell buildings
//        Attacks (consumption)
//        Attacks (ground loot)
//        Attacks (victory)
//
//        Buy city
//        Buy project
//        Baseball
//        Login bonus
//        Buy unit
//        Sell unit
//
//        Trade
//        Bank
//
//        City revenue
//            - If taxable, then add lower/upper
//
//        TODO: Add each order to the queue with date
//        write the queue to a file
//        Only update nation every 2h, or when saving
//        // flush the queue every 2h (or when force save)
//     */
//
//
//    @Subscribe
//    public void onTurnChange(TurnChangeEvent event) {
//        Map<Integer, TaxBracket> brackets = Locutus.imp().getBankDB().getTaxBracketsAndEstimates();
//
//        long date = event.getTimeCreated();
//        for (DBNation nation : Locutus.imp().getNationDB().getNationsMatching(f -> f.getVm_turns() == 0)) {
//            // get num turns since last update
//            int nationId = nation.getId();
//            double[] revenue = nation.getRevenue();
//
//            if (nation.isTaxable()) {
//                TaxBracket rate = brackets.get(nation.getTax_id());
//                if (rate != null) {
//                    // add X% to min and max
//                    for (ResourceType type : ResourceType.values()) {
//                        if (type.isRaw() || type.isManufactured()) {
//                            revenue[type.ordinal()] *= (rate.rssRate / 100d);
//                        } else {
//                            revenue[type.ordinal()] *= (rate.moneyRate / 100d);
//                        }
//                    }
//                    add(nationId, date, revenue, revenue);
//                } else {
//                    // add to max
//                    add(nationId, date, ResourceType.getBuffer(), revenue);
//                }
//            } else {
//                add(nationId, date, revenue, revenue);
//            }
//        }
//    }
//
//    private void handleInfraBuySell(DBNation nation, double from, double to, long date) {
//        if (nation != null) {
//            double cost = nation.infraCost(from, to);
//            add(nation.getId(), date, cost);
//        }
//    }
//
//    @Subscribe
//    public void onInfraBuy(CityInfraBuyEvent event) {
//        handleInfraBuySell(event.getNation(), event.getPrevious().infra, event.getCurrent().infra, event.getTimeCreated());
//    }
//
//    @Subscribe
//    public void onInfraSell(CityInfraSellEvent event) {
//        handleInfraBuySell(event.getNation(), event.getPrevious().infra, event.getCurrent().infra, event.getTimeCreated());
//    }
//
//    @Subscribe
//    private void handleLandBuySell(DBNation nation, double from, double to, long date) {
//        if (nation != null) {
//            double cost = nation.landCost(from, to);
//            add(nation.getId(), date, cost);
//        }
//    }
//
//    @Subscribe
//    public void onLandBuy(CityLandBuyEvent event) {
//        handleInfraBuySell(event.getNation(), event.getPrevious().land, event.getCurrent().land, event.getTimeCreated());
//    }
//
//    @Subscribe
//    public void onLandSell(CityLandSellEvent event) {
//        handleInfraBuySell(event.getNation(), event.getPrevious().land, event.getCurrent().land, event.getTimeCreated());
//    }
//
//    @Subscribe
//    public void onBuildingSell(CityBuildingChangeEvent event) {
//        // half of money and 75% rss back on selling
//        double[] total = ResourceType.getBuffer();
//        for (Map.Entry<Building, Integer> entry : event.getChange().entrySet()) {
//            int amt = entry.getValue();
//            if (amt > 0) {
//                Building building = entry.getKey();
//                total = building.cost(total, amt);
//            }
//        }
//        add(event.getNationId(), event.getTimeCreated(), total);
//    }
//
//    @Subscribe
//    public void onAttack(AttackEvent event) {
//        DBAttack attack = event.getAttack();
//
//        // consumption
//        Map<ResourceType, Double> attLoss = attack.getLosses(true, false, false, true, true);
//        Map<ResourceType, Double> defLoss = attack.getLosses(false, false, false, true, true);
//
//        // Handle airstrike money (since it comes under unit losses, which we are excluding)
//        if (attack.attack_type == AttackType.AIRSTRIKE4 && attack.defcas1 > 0) {
//            defLoss.put(ResourceType.MONEY, (defLoss.getOrDefault(ResourceType.MONEY, 0d) + attack.defcas1));
//        }
//        add(attack.attacker_nation_id, event.getTimeCreated(), PnwUtil.resourcesToArray(attLoss));
//        add(attack.attacker_nation_id, event.getTimeCreated(), PnwUtil.resourcesToArray(defLoss));
//    }
//
//    @Subscribe
//    public void onCityBuy(CityCreateEvent event) {
//        DBNation nation = event.getNation();
//        DBCity city = event.getCurrent();
//        if (nation != null) {
//            Map<Integer, DBCity> cities = nation._getCitiesV3();
//            int index = (int) cities.values().stream().filter(f -> f.created <= city.created).count();
//            double cost = PnwUtil.cityCost(nation, index - 1, index);
//            add(nation.getId(), event.getTimeCreated(), cost);
//        }
//    }
//
//    @Subscribe
//    public void onProjectBuy(NationCreateProjectEvent event) {
//        DBNation nation = event.getCurrent();
//        if (nation != null) {
//            double[] cost = nation.projectCost(event.getProject());
//            add(nation.getId(), event.getTimeCreated(), cost);
//        }
//    }
//
//    @Subscribe
//    public void onBaseball(BaseballGameEvent event) {
//        BBGame game = event.getGame();
//        if (game.getSpoils() != null && game.getSpoils() > 0) {
//            int id = event.getWinnerId();
//            if (id != 0) {
//                add(id, event.getTimeCreated(), game.getSpoils());
//            }
//        }
//    }
//
//    @Subscribe
//    public void onLogin(NationChangeActiveEvent event) {
//        DBNation previous = event.getPrevious();
//        DBNation nation = event.getCurrent();
//        long day = TimeUtil.getDay();
//        if (TimeUtil.getDay(previous.lastActiveMs()) == day) return;
//
//        // get last login day
//
//        ByteBuffer lastLoginDayBuf = nation.getMeta(NationMeta.LAST_LOGIN_DAY);
//        long lastLoginDay = lastLoginDayBuf != null ? lastLoginDayBuf.getLong() : 0;
//
//        if (lastLoginDay == day) return;
//
//        ByteBuffer lastLoginCountBuf = nation.getMeta(NationMeta.LAST_LOGIN_COUNT);
//        int lastLoginCount = lastLoginCountBuf != null ? lastLoginCountBuf.getInt() : 0;
//
//        nation.setMeta(NationMeta.LAST_LOGIN_DAY, day);
//        nation.setMeta(NationMeta.LAST_LOGIN_COUNT, lastLoginCount + 1);
//
//        double loginCap = nation.getAgeDays() < 60 ? 1_000_000 : 500_000;
//        double dailyBonusBase = 200_000;
//        double dailyBonusIncrement = 50_000;
//        double bonus = Math.min(loginCap, dailyBonusBase + dailyBonusIncrement * (lastLoginCount));
//
//        add(nation.getId(), event.getTimeCreated(), bonus);
//    }
//
//    @Subscribe
//    public void onUnitBuy(NationChangeUnitEvent event) {
//        if (event.isAttack()) return;
//
//        MilitaryUnit unit = event.getUnit();
//        DBNation previous = event.getPrevious();
//        DBNation current = event.getCurrent();
//
//        int amt = current.getUnits(unit) - previous.getUnits(unit);
//        if (amt != 0) {
//            double[] cost = unit.getCost(amt);
//            add(current.getId(), event.getTimeCreated(), cost);
//        }
//    }
//
//    @Subscribe
//    public void onTrade(TradeCompleteEvent event) {
//        // Add the following to pusher
//
//        // fetch trades every turn
//        // add trades to pusher
//    }
//
//    @Subscribe
//    public void bankEvent(TransactionEvent event) {
//        // add syncbanks command <nations>
//        // todo add bank subscription to pusher
//        // fetch bank recs every turn and at startup
//    }
//
//    @Subscribe
//    public void onLootInfo(LootInfoEvent event) {
//        // get the current estimate
//    }
//
//
//
////    public void update(Map<Integer, Map.Entry<Long, double[]>> lootData) {
////        for (Map.Entry<Integer, Map.Entry<Long, double[]>> entry : lootData.entrySet()) {
////            Integer id = entry.getKey();
////            Map.Entry<Long, double[]> turnLoot = entry.getValue();
////            update(id, turnLoot.getValue(), turnLoot.getKey());
////        }
////    }
////
////    public void update(int nationId, double[] loot, long turn) {
////        AbstractMap.SimpleEntry<Long, double[]> existing = lootData.get(nationId);
////        if (existing == null || existing.getKey() <= turn) {
////            existing = new AbstractMap.SimpleEntry<>(turn, loot);
////            lootData.put(nationId, existing);
////        }
////    }
////
////    public double[] estimateLoot(DBNation nation, boolean update, double[] buffer) {
////        if (buffer == null) buffer = new double[ResourceType.values.length];
////
////        Map<Integer, JavaCity> cities = nation.getCityMap(update, false);
////        Arrays.fill(buffer, 0);
////        double rads = (1 + (35 / (-1000d)));
////        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
////            buffer = entry.getValue().profit(rads, p -> false, buffer, nation.getCities());
////        }
////
////        AbstractMap.SimpleEntry<Long, double[]> last = lootData.get(nation.getNation_id());
////
////        long currentTurn = TimeUtil.getTurn();
////        long minutesInactive = TimeUnit.DAYS.toMinutes(7);
////
////        if (last != null) {
////            long lastTurn = last.getKey();
////            double[] loot = last.getValue();
////
////
////
////            // subtract unit purchases
////            // subtract war cost + add loot
////            // subtract city purchases
////            // subtract trades
////            // subtract bank transfers
////
////        } else {
////
////        }
////        return null;
////    }
//}
