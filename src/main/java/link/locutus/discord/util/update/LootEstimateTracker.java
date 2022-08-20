package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import com.politicsandwar.graphql.model.BBGame;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.event.bank.LootInfoEvent;
import link.locutus.discord.event.bank.TransactionEvent;
import link.locutus.discord.event.baseball.BaseballGameEvent;
import link.locutus.discord.event.city.CityBuildingChangeEvent;
import link.locutus.discord.event.city.CityCreateEvent;
import link.locutus.discord.event.city.CityInfraBuyEvent;
import link.locutus.discord.event.city.CityInfraSellEvent;
import link.locutus.discord.event.city.CityLandBuyEvent;
import link.locutus.discord.event.city.CityLandSellEvent;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.event.nation.NationChangeActiveEvent;
import link.locutus.discord.event.nation.NationChangeUnitEvent;
import link.locutus.discord.event.nation.NationCreateProjectEvent;
import link.locutus.discord.event.trade.TradeCompleteEvent;
import link.locutus.discord.event.war.AttackEvent;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import org.checkerframework.checker.units.qual.min;
import rocker.grant.nation;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class LootEstimateTracker {
    private final Map<Integer, LootEstimate> nationLootMap = new Int2ObjectOpenHashMap<>();
    private final long queueBufferMs;
    private final boolean allowQueue;
    private final Consumer<Map<Integer, LootEstimate>> save;
    private final Function<Integer, DBNation> nationFactory;

    public LootEstimateTracker(boolean allowQueue, long queueBufferMS, Supplier<Map<Integer, LootEstimate>> load, Consumer<Map<Integer, LootEstimate>> save, Function<Integer, DBNation> nationFactory) {
        this.allowQueue = allowQueue;
        this.queueBufferMs = queueBufferMS;

        this.save = save;
        this.nationFactory = nationFactory;

        loadLootEstimates(load.get());
    }

    public void update() {
        long cutoff = System.currentTimeMillis() - queueBufferMs;
        Map<Integer, LootEstimate> toSave = new HashMap<>();
        Set<Integer> toDelete = new HashSet<>();

        synchronized (nationLootMap) {
            // remove deleted nations
            for (Map.Entry<Integer, LootEstimate> entry : nationLootMap.entrySet()) {
                int nationId = entry.getKey();
                LootEstimate estimate = entry.getValue();

                synchronized (estimate) {
                    DBNation nation = nationFactory.apply(entry.getKey());
                    if (nation == null) {
                        estimate.markDeleted();
                        toSave.put(nationId, estimate);
                        toDelete.add(nationId);
                        continue;
                    }
                    estimate.flushOrders(cutoff);
                    if (estimate.isDirty()) {
                        toSave.put(nationId, estimate);
                        estimate.dirty = false;
                    }
                }
            }
            for (int id : toDelete) {
                nationLootMap.remove(id);
            }
        }
        if (!toSave.isEmpty()) {
            save.accept(toSave);
        }
    }

    public void add(int nationId, long date, double minAndMaxMoney) {
        add(nationId, date, minAndMaxMoney, ResourceType.MONEY);
    }

    public void add(int nationId, long date, double minAndMax, ResourceType type) {
        if (minAndMax == 0) return;
        add(nationId, date, type.toArray(minAndMax));
    }

    public void add(int nationId, long date, double[] minAndMax) {
        add(nationId, date, minAndMax, minAndMax);
    }

    public void add(int nationId, long date, double[] min, double[] max) {
        if (ResourceType.isEmpty(min) && ResourceType.isEmpty(max)) return;
        LootEstimate estimate = getOrCreate(nationId);
        if (max == null) max = min;

        if (date <= 0) date = System.currentTimeMillis();
        Order order = new Order(min, max, date);
        estimate.addOrder(order, allowQueue);
    }

    private LootEstimate getOrCreate(int nationId) {
        DBNation nation = nationFactory.apply(nationId);
        if (nation == null) return null;

        boolean newInstance = false;
        LootEstimate existing;
        synchronized (nationLootMap) {
            existing = nationLootMap.get(nationId);
            if (existing == null) {
                existing = new LootEstimate();
                nationLootMap.put(nationId, existing);
                newInstance = true;
            }
        }
        if (newInstance) {
            LootEntry loot = nation.getBeigeLoot();
            if (loot != null) {
                existing.min = loot.getTotal_rss().clone();
                existing.max = loot.getTotal_rss().clone();
                existing.lastTurnRevenue = TimeUtil.getTurn(loot.getDate());
            } else {
                if (nation.isPowered()) {
                    nation.getResourcesNeeded(new HashMap<>(), 5, false);
                } else if (nation.getCities() == 1) {
                    // start with $1m

                }
                existing.min = ResourceType.getBuffer();
                existing.max = ResourceType.getBuffer();
                existing.lastTurnRevenue = TimeUtil.getTurn();
            }
            save.accept(Collections.singletonMap(nation.getId(), existing));
        }
        return existing;
    }

    public boolean loadLootEstimates(Map<Integer, LootEstimate> estimates) {
        Map<Integer, LootEstimate> toDelete = new HashMap<>();
        for (Map.Entry<Integer, LootEstimate> entry : estimates.entrySet()) {
            LootEstimate estimate = entry.getValue();
            int nationId = entry.getKey();
            DBNation nation = nationFactory.apply(nationId);
            if (nation == null) {
                estimate.markDeleted();
                toDelete.put(nationId, estimate);
            } else {
                nationLootMap.put(nationId, estimate);
            }
        }
        if (!toDelete.isEmpty()) {
            save.accept(toDelete);
        }
        return true;
    }

    public static class LootEstimate {
        public double[] min;
        public double[] max;

        public double[] revenueCache;
        public long lastTurnRevenue;
        public boolean dirty;

        private Deque<Order> orders = null;

        public LootEstimate() {
            this.min = ResourceType.getBuffer();
            this.max = ResourceType.getBuffer();
        }

        public void markDeleted() {
            this.min = null;
            this.max = null;
            this.revenueCache = null;
            this.orders = null;
        }

        public boolean isDeleted() {
            return min == null;
        }

        public synchronized void addOrder(Order order, boolean allowQueue) {
            if (allowQueue) {
                if (order.isAbsolute) {
                    if (this.orders != null) {
                        orders.removeIf(f -> f.date < order.date);
                    }
                }
                if (this.orders == null) {
                    this.orders = new ArrayDeque<>();
                }
                this.orders.add(order);
            }
            if (order.isAbsolute) {
                if (!Arrays.equals(min, order.min) || !Arrays.equals(max, order.max)) {
                    min = order.min.clone();
                    max = order.max.clone();
                    if (this.orders != null && this.orders.size() > 1) {
                        for (Order newOrder : this.orders) {
                            if (newOrder.date > order.date) {
                                this.min = ResourceType.add(this.min, newOrder.min);
                                this.max = ResourceType.add(this.max, newOrder.max);
                            }
                        }
                    }
                    dirty = true;
                }
            } else {
                this.min = ResourceType.add(this.min, order.min);
                this.max = ResourceType.add(this.max, order.max);
                dirty = true;
            }
        }
        public Map.Entry<double[], double[]> getLootEstimateRange() {
            return Map.entry(ResourceType.ceil(min.clone(), 0), max.clone());
        }


        public synchronized void flushOrders(long maxDate) {
            if (orders != null && !orders.isEmpty()) {
                List<Order> ordersSorted = new ArrayList<>(orders.size());
                if (ordersSorted.size() > 0) {
                    ordersSorted.sort(Comparator.comparingLong(o -> o.date));
                }
                double[] minCopy = min.clone();
                double[] maxCopy = max.clone();
                int i = ordersSorted.size() - 1;
                for (; i >= 0; i--) {
                    Order order = ordersSorted.get(i);
                    if (order.isAbsolute) {
                        System.arraycopy(order.min, 0, minCopy, 0, minCopy.length);
                        System.arraycopy(order.max, 0, maxCopy, 0, maxCopy.length);
                        i++;
                        break;
                    } else {
                        // subtract
                        minCopy = ResourceType.subtract(minCopy, order.min);
                        maxCopy = ResourceType.subtract(maxCopy, order.max);
                    }
                }

                ResourceType.ceil(minCopy, 0);
                ResourceType.ceil(maxCopy, 0);
                for (; i < ordersSorted.size(); i++) {
                    Order order = ordersSorted.get(i);
                    minCopy = ResourceType.add(minCopy, order.min);
                    maxCopy = ResourceType.add(maxCopy, order.max);
                    if (order.date > maxDate) {
                        // don't ceil after set
                    } else {
                        ResourceType.ceil(minCopy, 0);
                        ResourceType.ceil(maxCopy, 0);
                    }
                }
                if (!dirty) {
                    dirty |= !Arrays.equals(min, minCopy) || !Arrays.equals(max, maxCopy);
                }
                min = minCopy;
                max = maxCopy;

                orders.removeIf(f -> f.date <= maxDate);
                if (orders.isEmpty()) orders = null;
            }
        }

        public boolean hasQueue(long cutoff) {
            if (orders == null || orders.isEmpty()) {
                return false;
            }
            for (Order order : orders) {
                if (order.date < cutoff) return true;
            }
            return false;
        }

        public void set(long date, double[] resources, boolean allowQueue) {
            Order order = new Order(resources, resources, date).setAbsolute();
            addOrder(order, allowQueue);
        }

        public boolean isDirty() {
            return isDirty();
        }
    }

    public static class Order {
        public final double[] min;
        public final double[] max;
        public final long date;
        public boolean isAbsolute = false;

        public Order(double[] min, double[] max, long date) {
            this.min = min;
            this.max = max;
            this.date = date;
        }

        public Order setAbsolute() {
            this.isAbsolute = true;
            return this;
        }
    }

    /*
        Nation loot info

        Buy infra
        Sell infra
        Buy land
        Sell land
        Sell buildings
        Attacks (consumption)
        Attacks (ground loot)
        Attacks (victory)

        Buy city
        Buy project
        Baseball
        Login bonus
        Buy unit
        Sell unit

        Trade
        Bank

        City revenue
            - If taxable, then add lower/upper

        write the queue to a file
        Only update nation every 2h, or when saving
        // flush the queue every 2h (or when force save)
     */


    @Subscribe
    public void onTurnChange(TurnChangeEvent event) {
        Map<Integer, TaxBracket> brackets = Locutus.imp().getBankDB().getTaxBracketsAndEstimates();

        double[] existingBuffer = ResourceType.getBuffer();

        long date = event.getTimeCreated();
        long turn = TimeUtil.getTurn();

        for (DBNation nation : Locutus.imp().getNationDB().getNationsMatching(f -> true)) {
            if (nation.getVm_turns() > 0) {
                LootEstimate estimate = getOrCreate(nation.getId());
                if (estimate != null) {
                    estimate.lastTurnRevenue = turn;
                    estimate.dirty = true;
                }
                continue;
            }
            // get num turns since last update
            LootEstimate estimate = getOrCreate(nation.getNation_id());
            if (estimate == null) continue;

            System.arraycopy(estimate.max, 0, existingBuffer, 0, estimate.max.length);

            int turnDiff = (int) (turn - estimate.lastTurnRevenue);
            boolean noFood = estimate.max[ResourceType.FOOD.ordinal()] <= 0;

            double[] revenue = nation.getRevenue(turnDiff, true, true, true, noFood, false);
            for (ResourceType type : ResourceType.values) {
                if (type.isManufactured()) {
                    double min = 0;
                    for (ResourceType input : type.getInputs()) {
                        double net = estimate.max[input.ordinal()] + revenue[input.ordinal()];
                        if (net < min) min = net;
                    }
                    if (min < 0) {
                        for (ResourceType input : type.getInputs()) {
                            revenue[input.ordinal()] -= min;
                        }
                        revenue[type.ordinal()] += min * type.getManufacturingMultiplier();
                    }
                }
            }


            if (nation.isTaxable()) {
                TaxBracket rate = brackets.get(nation.getTax_id());
                if (rate != null) {
                    // add X% to min and max
                    for (ResourceType type : ResourceType.values()) {
                        if (type.isRaw() || type.isManufactured()) {
                            revenue[type.ordinal()] *= ((100 - rate.rssRate) / 100d);
                        } else {
                            revenue[type.ordinal()] *= ((100 - rate.moneyRate) / 100d);
                        }
                    }
                    add(nation.getId(), date, revenue, revenue);
                } else {
                    add(nation.getId(), date, ResourceType.getBuffer(), revenue);
                }
            } else {
                add(nation.getId(), date, revenue, revenue);
            }
            estimate.lastTurnRevenue = turn;
        }
    }

    private void handleInfraBuySell(DBNation nation, double from, double to, long date) {
        if (nation != null) {
            double cost = nation.infraCost(from, to);
            add(nation.getId(), date, cost);
        }
    }

    @Subscribe
    public void onInfraBuy(CityInfraBuyEvent event) {
        handleInfraBuySell(event.getNation(), event.getPrevious().infra, event.getCurrent().infra, event.getTimeCreated());
    }

    @Subscribe
    public void onInfraSell(CityInfraSellEvent event) {
        handleInfraBuySell(event.getNation(), event.getPrevious().infra, event.getCurrent().infra, event.getTimeCreated());
    }

    @Subscribe
    private void handleLandBuySell(DBNation nation, double from, double to, long date) {
        if (nation != null) {
            double cost = nation.landCost(from, to);
            add(nation.getId(), date, cost);
        }
    }

    @Subscribe
    public void onLandBuy(CityLandBuyEvent event) {
        handleInfraBuySell(event.getNation(), event.getPrevious().land, event.getCurrent().land, event.getTimeCreated());
    }

    @Subscribe
    public void onLandSell(CityLandSellEvent event) {
        handleInfraBuySell(event.getNation(), event.getPrevious().land, event.getCurrent().land, event.getTimeCreated());
    }

    @Subscribe
    public void onBuildingSell(CityBuildingChangeEvent event) {
        // half of money and 75% rss back on selling
        double[] total = ResourceType.getBuffer();
        for (Map.Entry<Building, Integer> entry : event.getChange().entrySet()) {
            int amt = entry.getValue();
            if (amt > 0) {
                Building building = entry.getKey();
                total = building.cost(total, amt);
            }
        }
        add(event.getNationId(), event.getTimeCreated(), total);
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        DBAttack attack = event.getAttack();

        // consumption
        double[] attLoss = PnwUtil.resourcesToArray(attack.getLosses(true, false, false, true, true));
        double[] defLoss = PnwUtil.resourcesToArray(attack.getLosses(false, false, false, true, true));

        // Handle airstrike money (since it comes under unit losses, which we are excluding)
        if (attack.attack_type == AttackType.AIRSTRIKE4 && attack.defcas1 > 0) {
            defLoss[ResourceType.MONEY.ordinal()] += attack.defcas1;
        }
        if (attack.success > 0) {
            DBNation attacker = nationFactory.apply(attack.attacker_nation_id);
            if (attacker != null && attacker.hasProject(Projects.MILITARY_SALVAGE)) {
                Map<ResourceType, Double> unitLosses = attack.getLosses(true, true, false, false, false);
                attLoss[ResourceType.STEEL.ordinal()] -= unitLosses.getOrDefault(ResourceType.STEEL, 0d) * 0.05;
                attLoss[ResourceType.ALUMINUM.ordinal()] -= unitLosses.getOrDefault(ResourceType.ALUMINUM, 0d) * 0.05;
            }
        }
        // negate this
        add(attack.attacker_nation_id, event.getTimeCreated(), ResourceType.negative(attLoss));
        add(attack.defender_nation_id, event.getTimeCreated(), ResourceType.negative(defLoss));
    }

    @Subscribe
    public void onCityBuy(CityCreateEvent event) {
        DBNation nation = event.getNation();
        DBCity city = event.getCurrent();
        if (nation != null && city.created > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) {
            Map<Integer, DBCity> cities = nation._getCitiesV3();
            int index = (int) cities.values().stream().filter(f -> f.created <= city.created).count();
            double cost = PnwUtil.cityCost(nation, index - 1, index);
            add(nation.getId(), event.getTimeCreated(), cost);
        }
    }

    @Subscribe
    public void onProjectBuy(NationCreateProjectEvent event) {
        DBNation nation = event.getCurrent();
        if (nation != null) {
            double[] cost = nation.projectCost(event.getProject());
            add(nation.getId(), event.getTimeCreated(), cost);
        }
    }

    @Subscribe
    public void onBaseball(BaseballGameEvent event) {
        BBGame game = event.getGame();
        if (game.getSpoils() != null && game.getSpoils() > 0) {
            int id = event.getWinnerId();
            if (id != 0) {
                add(id, event.getTimeCreated(), game.getSpoils());
            }
        }
    }

    @Subscribe
    public void onLogin(NationChangeActiveEvent event) {
        DBNation previous = event.getPrevious();
        DBNation nation = event.getCurrent();
        long day = TimeUtil.getDay();
        if (TimeUtil.getDay(previous.lastActiveMs()) == day || nation.getVm_turns() != 0) return;

        // get last login day

        ByteBuffer lastLoginDayBuf = nation.getMeta(NationMeta.LAST_LOGIN_DAY);
        long lastLoginDay = lastLoginDayBuf != null ? lastLoginDayBuf.getLong() : 0;

        if (lastLoginDay == day) return;

        ByteBuffer lastLoginCountBuf = nation.getMeta(NationMeta.LAST_LOGIN_COUNT);
        int lastLoginCount = lastLoginCountBuf != null ? lastLoginCountBuf.getInt() : 0;

        nation.setMeta(NationMeta.LAST_LOGIN_DAY, day);
        nation.setMeta(NationMeta.LAST_LOGIN_COUNT, lastLoginCount + 1);

        double loginCap = nation.getAgeDays() < 60 ? 1_000_000 : 500_000;
        double dailyBonusBase = 200_000;
        double dailyBonusIncrement = 50_000;
        double bonus = Math.min(loginCap, dailyBonusBase + dailyBonusIncrement * (lastLoginCount));

        add(nation.getId(), event.getTimeCreated(), bonus);
    }

    @Subscribe
    public void onUnitBuy(NationChangeUnitEvent event) {
        if (event.isAttack()) return;

        MilitaryUnit unit = event.getUnit();
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        int amt = current.getUnits(unit) - previous.getUnits(unit);
        if (amt != 0) {
            double[] cost = unit.getCost(amt);
            add(current.getId(), event.getTimeCreated(), cost);
        }
    }

    @Subscribe
    public void onTrade(TradeCompleteEvent event) {
        DBTrade trade = event.getCurrent();
        add(trade.getSeller(), event.getTimeCreated(),
                trade.getResource().builder(-trade.getQuantity()).addMoney(trade.getTotal()).build());
        add(trade.getBuyer(), event.getTimeCreated(),
                trade.getResource().builder(trade.getQuantity()).addMoney(-trade.getTotal()).build());
    }

    @Subscribe
    public void bankEvent(TransactionEvent event) {
        Transaction2 tx = event.getTransaction();
        if (tx.isSenderNation()) {
            double[] negativeResources = ResourceType.MONEY.builder(0).subtract(tx.resources).build();
            add((int) tx.receiver_id, tx.tx_datetime, negativeResources);
        }
        if (tx.isReceiverNation()) {
            add((int) tx.receiver_id, tx.tx_datetime, tx.resources);
        }
    }

    @Subscribe
    public void onLootInfo(LootInfoEvent event) {
        LootEntry loot = event.getLoot();
        if (!loot.isAlliance()) {
            int id = loot.getId();
            LootEstimate estimate = getOrCreate(id);
            estimate.set(loot.getDate(), loot.getTotal_rss(), allowQueue);
        }
    }



//    public void update(Map<Integer, Map.Entry<Long, double[]>> lootData) {
//        for (Map.Entry<Integer, Map.Entry<Long, double[]>> entry : lootData.entrySet()) {
//            Integer id = entry.getKey();
//            Map.Entry<Long, double[]> turnLoot = entry.getValue();
//            update(id, turnLoot.getValue(), turnLoot.getKey());
//        }
//    }
//
//    public void update(int nationId, double[] loot, long turn) {
//        AbstractMap.SimpleEntry<Long, double[]> existing = lootData.get(nationId);
//        if (existing == null || existing.getKey() <= turn) {
//            existing = new AbstractMap.SimpleEntry<>(turn, loot);
//            lootData.put(nationId, existing);
//        }
//    }
//
//    public double[] estimateLoot(DBNation nation, boolean update, double[] buffer) {
//        if (buffer == null) buffer = new double[ResourceType.values.length];
//
//        Map<Integer, JavaCity> cities = nation.getCityMap(update, false);
//        Arrays.fill(buffer, 0);
//        double rads = (1 + (35 / (-1000d)));
//        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
//            buffer = entry.getValue().profit(rads, p -> false, buffer, nation.getCities());
//        }
//
//        AbstractMap.SimpleEntry<Long, double[]> last = lootData.get(nation.getNation_id());
//
//        long currentTurn = TimeUtil.getTurn();
//        long minutesInactive = TimeUnit.DAYS.toMinutes(7);
//
//        if (last != null) {
//            long lastTurn = last.getKey();
//            double[] loot = last.getValue();
//
//
//
//            // subtract unit purchases
//            // subtract war cost + add loot
//            // subtract city purchases
//            // subtract trades
//            // subtract bank transfers
//
//        } else {
//
//        }
//        return null;
//    }
}
