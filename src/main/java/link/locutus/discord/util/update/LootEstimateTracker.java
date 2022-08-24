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
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
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
import link.locutus.discord.event.trade.TradeCreateEvent;
import link.locutus.discord.event.trade.TradeEvent;
import link.locutus.discord.event.war.AttackEvent;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.scheduler.TriConsumer;
import org.checkerframework.checker.units.qual.min;
import rocker.grant.nation;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class LootEstimateTracker {
    private final Map<Integer, LootEstimate> nationLootMap = new Int2ObjectOpenHashMap<>();
    private final long queueBufferMs;
    private final boolean allowQueue;
    private final Consumer<Map<Integer, LootEstimate>> saveLootEstimate;
    private final Function<Integer, DBNation> nationFactory;
    private final TriConsumer<Integer, Integer, double[]> saveTaxDiff;

    public LootEstimateTracker(boolean allowQueue, long queueBufferMS, boolean checkValid, Supplier<Map<Integer, LootEstimate>> load, Consumer<Map<Integer, LootEstimate>> saveLootEstimate, TriConsumer<Integer, Integer, double[]> saveTaxDiff, Function<Integer, DBNation> nationFactory) {
        this.allowQueue = allowQueue;
        this.queueBufferMs = queueBufferMS;

        this.saveLootEstimate = saveLootEstimate;
        this.saveTaxDiff = saveTaxDiff;
        this.nationFactory = nationFactory;

        loadLootEstimates(load.get(), checkValid);
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
            saveLootEstimate.accept(toSave);
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
        if (estimate == null) return;
        if (max == null) max = min;

        if (date <= 0) date = System.currentTimeMillis();
        Order order = new Order(min, max, date);
        estimate.addOrder(order, allowQueue);
    }

    public LootEstimate getOrCreate(int nationId) {
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
//                if (nation.isPowered()) {
//                    nation.getResourcesNeeded(new HashMap<>(), 5, false);
//                } else if (nation.getCities() == 1) {
//                    // start with $1m
//                }
                existing.min = ResourceType.getBuffer();
                existing.max = ResourceType.getBuffer();
                existing.lastTurnRevenue = TimeUtil.getTurn();
            }
            saveLootEstimate.accept(Collections.singletonMap(nation.getId(), existing));
        }
        return existing;
    }

    public boolean loadLootEstimates(Map<Integer, LootEstimate> estimates, boolean checkValid) {
        Map<Integer, LootEstimate> toDelete = new HashMap<>();
        for (Map.Entry<Integer, LootEstimate> entry : estimates.entrySet()) {
            LootEstimate estimate = entry.getValue();
            int nationId = entry.getKey();
            if (checkValid) {
                DBNation nation = nationFactory.apply(nationId);
                if (nation == null) {
                    estimate.markDeleted();
                    toDelete.put(nationId, estimate);
                    continue;
                }
            }
            nationLootMap.put(nationId, estimate);
        }
        if (!toDelete.isEmpty()) {
            saveLootEstimate.accept(toDelete);
        }
        return true;
    }

    public void resolve(double[] min, double[] requiredOffset, double[] max, double[] actual, double[] currentDiff, int taxId, Map<Integer, double[]> diffByTaxId) {


        StringBuilder result = new StringBuilder();
        result.append("min:" + PnwUtil.resourcesToString(min));
        result.append(" offset:" + PnwUtil.resourcesToString(requiredOffset));
        result.append(" max:" + PnwUtil.resourcesToString(max));
        result.append(" actual:" + PnwUtil.resourcesToString(actual));
        result.append(" currentDiff:" + PnwUtil.resourcesToString(currentDiff));
        result.append(" taxId:" + taxId);
        result.append(" diffByTaxId:" + StringMan.getString(diffByTaxId) + "\n");

        File output = new File("data/lootResolve.txt");
        synchronized (output) {
            try {
                Files.write(output.toPath(), result.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class LootEstimate {
        public double[] min;
        public double[] requiredOffset;
        public double[] max;

        public int tax_id;
        public Map<Integer, double[]> diffByTaxId;
        public long lastTurnRevenue;
        public long lastResolved;
        public boolean dirty;

        private Deque<Order> orders = null;

        public LootEstimate() {
            this.min = ResourceType.getBuffer();
            this.max = ResourceType.getBuffer();
        }

        public synchronized void addDiffByTaxId(int taxId, double[] diff) {
            if (ResourceType.isEmpty(diff)) {
                if (diffByTaxId != null) diffByTaxId.remove(taxId);
                return;
            }
            if (diffByTaxId == null) {
                diffByTaxId = new Int2ObjectOpenHashMap<>();
            }
            diffByTaxId.put(taxId, diff);
        }

        public synchronized double[] getDiffByTaxId(int tax_id) {
            return getDiffByTaxId(tax_id, min, max);
        }

        public synchronized double[] getDiffByTaxId(int tax_id, double[] min, double[] max) {
            if (tax_id == 0) return ResourceType.getBuffer();
            if (this.tax_id != tax_id) {
                return diffByTaxId == null ? null : diffByTaxId.get(tax_id);
            }
            double[] diff = ResourceType.subtract(max.clone(), min);
            if (diffByTaxId != null && !diffByTaxId.isEmpty()) {
                for (Map.Entry<Integer, double[]> entry : diffByTaxId.entrySet()) {
                    if (entry.getKey() != tax_id) {
                        ResourceType.subtract(diff, entry.getValue());
                    }
                }

            }
            return diff;
        }

        public synchronized void markDeleted() {
            this.min = null;
            this.max = null;
            this.requiredOffset = null;
            this.orders = null;
        }

        public boolean isDeleted() {
            return min == null;
        }

        public synchronized void addUnknownRevenue(LootEstimateTracker parent, int nationId, int taxId, long timestamp, double[] revenue) {
            if (taxId == 0) throw new IllegalStateException("taxId == 0 Is not taxable!");

            if (taxId != this.tax_id) {
                if (this.tax_id != 0) {
                    double[] oldDiff = getDiffByTaxId(this.tax_id);
                    if (!ResourceType.isEmpty(oldDiff)) {
                        addDiffByTaxId(this.tax_id, oldDiff);
                        parent.saveTaxDiff.consume(nationId, this.tax_id, oldDiff);
                    }
                    dirty = true;
                }
                this.tax_id = taxId;
            }
            double[] onlyNegatives = ResourceType.getBuffer();
            for (int i = 0; i < revenue.length; i++) {
                double value = revenue[i];
                if (value < 0) onlyNegatives[i] = value;
            }
            addOrder(new Order(onlyNegatives, revenue, timestamp), false);
        }

        public synchronized void addAbsolute(LootEstimateTracker parent, int nationId, long timestamp, double[] resources, boolean allowQueue) {
            // invalid resolution
            if (lastResolved >= timestamp) return;

            double[] minCopy = min;
            double[] maxCopy = max;
            boolean copied = false;
            if (orders != null && !orders.isEmpty()) {
                for (Order order : orders) {
                    if (order.date <= timestamp) continue;

                    if (!copied) {
                        minCopy = minCopy.clone();
                        maxCopy = maxCopy.clone();
                        copied = true;
                    }

                    ResourceType.subtract(minCopy, order.min);
                    ResourceType.subtract(maxCopy, order.max);
                }
            }

            double[] diff = getDiffByTaxId(tax_id, minCopy, maxCopy);
            parent.resolve(minCopy, requiredOffset, maxCopy, resources, diff, tax_id, diffByTaxId);

            // delete diff
            parent.saveTaxDiff.consume(nationId, 0, null);

            diffByTaxId = null;

            lastResolved = Math.max(lastResolved, timestamp);
            addOrder(new Order(resources, resources, timestamp).setAbsolute(), allowQueue);

        }

        public synchronized void addOrder(Order order, boolean allowQueue) {
            if (allowQueue) {
                if (order.isAbsolute) {
                    if (this.orders != null) {
                        orders.removeIf(f -> f.date < order.date);
                    }
                } else if (this.orders != null && !this.orders.isEmpty()) {
                    for (Order other : this.orders) {
                        if (other.isAbsolute && other.date >= order.date) return;
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
                    Arrays.fill(requiredOffset, 0);
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


        public boolean ceil(double[] inputArray, double[] outputArray) {
            boolean changed = false;
            for (int j = 0; j < inputArray.length; j++) {
                double amt = inputArray[j];
                if (amt < 0) {
                    double existing = outputArray[j];
                    if (-amt > existing) {
                        changed = true;
                        outputArray[j] = -amt;
                    }
                }
            }
            return changed;
        }

        public synchronized void flushOrders(long maxDate) {
            if (orders != null && !orders.isEmpty()) {
                List<Order> ordersSorted = new ArrayList<>(orders);
                ordersSorted.sort(Comparator.comparingLong(o -> o.date));

                double[] minCopy = min.clone();
                int i = ordersSorted.size() - 1;
                for (; i >= 0; i--) {
                    Order order = ordersSorted.get(i);
                    if (order.isAbsolute) {
                        System.arraycopy(order.min, 0, minCopy, 0, minCopy.length);
                        i++;
                        break;
                    } else {
                        // subtract
                        minCopy = ResourceType.subtract(minCopy, order.min);
                    }
                }

                dirty |= ceil(minCopy, requiredOffset);
                for (; i < ordersSorted.size(); i++) {
                    Order order = ordersSorted.get(i);
                    minCopy = ResourceType.add(minCopy, order.min);
                    if (order.date > maxDate) {
                        // don't ceil after set
                    } else {
                        if (order.isAbsolute) {
                            Arrays.fill(requiredOffset, 0);
                        }
                        dirty |= ceil(minCopy, requiredOffset);
                    }
                }

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
        boolean hasSalvage = false;
        if (attack.success > 0) {
            DBNation attacker = nationFactory.apply(attack.attacker_nation_id);
            hasSalvage = attacker != null && attacker.hasProject(Projects.MILITARY_SALVAGE);
        }
    }

    public void onAttack(DBAttack attack, boolean hasSalvage) {
        // consumption
        double[] attLoss = PnwUtil.resourcesToArray(attack.getLosses(true, false, false, true, true));
        double[] defLoss = PnwUtil.resourcesToArray(attack.getLosses(false, false, false, true, true));

        // Handle airstrike money (since it comes under unit losses, which we are excluding)
        if (attack.attack_type == AttackType.AIRSTRIKE4 && attack.defcas1 > 0) {
            defLoss[ResourceType.MONEY.ordinal()] += attack.defcas1;
        }
        if (attack.success > 0 && hasSalvage) {
            Map<ResourceType, Double> unitLosses = attack.getLosses(true, true, false, false, false);
            attLoss[ResourceType.STEEL.ordinal()] -= unitLosses.getOrDefault(ResourceType.STEEL, 0d) * 0.05;
            attLoss[ResourceType.ALUMINUM.ordinal()] -= unitLosses.getOrDefault(ResourceType.ALUMINUM, 0d) * 0.05;
        }
        // negate this
        add(attack.attacker_nation_id, attack.epoch, ResourceType.negative(attLoss));
        add(attack.defender_nation_id, attack.epoch, ResourceType.negative(defLoss));
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

        loginBonus(nation.getId(), nation.getAgeDays(), lastLoginCount, event.getTimeCreated());
    }

    public void loginBonus(int nationId, int ageDays, int loginCount, long date) {
        double loginCap = ageDays < 60 ? 1_000_000 : 500_000;
        double dailyBonusBase = 200_000;
        double dailyBonusIncrement = 50_000;
        double bonus = Math.min(loginCap, dailyBonusBase + dailyBonusIncrement * (loginCount));

        add(nationId, date, bonus);
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
    public void onTradeCreate(TradeCreateEvent event) {
        DBTrade trade = event.getCurrent();
        if (trade.getSeller() > 0 && trade.getBuyer() > 0) {
            handleTrade(event);
        }
    }
    @Subscribe
    public void onTrade(TradeCompleteEvent event) {
        handleTrade(event);
    }

    public void handleTrade(TradeEvent event) {
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
            estimate.addAbsolute(this, id, loot.getDate(), loot.getTotal_rss(), allowQueue);
        }
    }
}