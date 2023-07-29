package link.locutus.discord.util.battle.sim;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.apiv1.domains.War;
import link.locutus.discord.apiv1.domains.subdomains.WarContainer;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SimulatedWarNode {
    private final WarNation aggressor;
    private final WarNation defender;
    private final boolean isAttacker;
    private int turnsLeft;

    private SimulatedWarNode parent;
    private Method method;
    private Object[] arguments;
    private double value = Double.NaN;
    private Object children;

    public SimulatedWarNode(WarNation aggressor, WarNation defender, WarType type, int turns, boolean init) {
        this.aggressor = aggressor;
        this.defender = defender;
        this.turnsLeft = turns;
        this.isAttacker = false;

        if (init) {
            int ap = 6;
            for (WarNation warNation : Arrays.asList(aggressor, defender)) {
                switch (warNation.getNation().getWarPolicy()) {
                    case FORTRESS:
                        ap--;
                        break;
                    case BLITZKRIEG:
                        ap++;
                        break;
                }
            }
            aggressor.setResistance(100);
            defender.setResistance(100);
            aggressor.setActionPoints(ap);
            defender.setActionPoints(ap);

            setupWarType(type);
            setupLootFactor();
        }
    }

    public static SimulatedWarNode of(DBNation attackerDefault, String... args) throws IOException {
        WarType type = WarType.RAID;
        DBNation defender = null;
        DBNation attacker = null;

        switch (args.length) {
            default: {
                throw new IllegalArgumentException("Invalid argument length, expected: `<war-url>` or `<defender> <attacker> <type>`, but instead got " + StringMan.getString(args));
            }
            case 3:
                type = WarType.parse(args[2].toLowerCase());
                switch (type) {
                    case RAID:
                    case ORD:
                    case ATT:
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid raid type " + type + ". expected either: RAID, ORDINARY, ATTRITION");
                }
            case 2:
                Integer attackerId = DiscordUtil.parseNationId(args[1]);
                if (attackerId == null) {
                    throw new IllegalArgumentException("Invalid attacker: " + args[1]);
                }
                attacker = Locutus.imp().getNationDB().getNation(attackerId);
                // attacker
                // defender
            case 1:
                if (args[0].contains("/war=")) {
                    int warId = Integer.parseInt(args[0].split("=")[1].replaceAll("/", ""));
                    DBWar war = Locutus.imp().getWarDb().getWar(warId);

                    if (war == null) {
                        throw new IllegalArgumentException("Invalid war");
                    }

                    return new SimulatedWarNode(war);
                } else {
                    if (attacker == null) {
                        attacker = attackerDefault;
                    }
                    Integer defenderId = DiscordUtil.parseNationId(args[0]);
                    if (defenderId == null) {
                        throw new IllegalArgumentException( "Invalid defender or war link: " + args[0]);
                    }
                    defender = Locutus.imp().getNationDB().getNation(defenderId);

                    return new SimulatedWarNode(new WarNation(attacker), new WarNation(defender), type, 60, true);
                }
        }
    }

    public void setupWarType(WarType warType) {
        switch (warType) {
            case ORD:
                aggressor.setInfraFactor(0.5);
                aggressor.setLootFactor(0.5);
                defender.setInfraFactor(0.5);
                defender.setLootFactor(0.5);
                break;
            case ATT:
                aggressor.setInfraFactor(1);
                aggressor.setLootFactor(0.25);
                defender.setInfraFactor(1);
                defender.setLootFactor(0.5);
                break;
            case RAID:
                aggressor.setInfraFactor(0.25);
                aggressor.setLootFactor(1);
                defender.setInfraFactor(0.5);
                defender.setLootFactor(1);
                break;
        }
    }

    private void setupLootFactor() {
        setupLootFactor(aggressor, defender);
        setupLootFactor(defender, aggressor);
    }

    private void setupLootFactor(WarNation a, WarNation b) {
        if (a.getNation().getWarPolicy() == WarPolicy.PIRATE) {
            a.setLootFactor(a.getLootFactor() * 1.4);
        }
        if (a.getNation().getWarPolicy() == WarPolicy.MONEYBAGS) {
            b.setLootFactor(b.getLootFactor() / 1.4);
        }
    }

    public SimulatedWarNode(SimulatedWarNode parent, boolean attack, Method method, Object... params) {
        this.parent = parent;
        this.method = method;
        this.arguments = params;
        this.isAttacker = attack;

        this.aggressor = new WarNation(parent.aggressor);
        this.defender = new WarNation(parent.defender);
        this.turnsLeft = parent.turnsLeft;
    }

    public void addChild(SimulatedWarNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        if (this.children instanceof SimulatedWarNode) {
            List<SimulatedWarNode> list = new ArrayList<>();
            list.add(child);
            list.add((SimulatedWarNode) this.children);
            this.children = list;
        } else {
            this.children = child;
        }
    }

    public int getTurnsLeft() {
        return turnsLeft;
    }

    public void decrementTurns() {
        turnsLeft--;
        aggressor.incrementBlockade();
        defender.incrementBlockade();
    }

    public List<SimulatedWarNode> toActionList() {
        List<SimulatedWarNode> actions = new ArrayList<>();
        SimulatedWarNode current = this;

        while (current.getParent() != null) {
            actions.add(current);
            current = current.getParent();
        }

        Collections.reverse(actions);

        return actions;
    }

    public SimulatedWarNode getParent() {
        return parent;
    }

    public Method getMethod() {
        return method;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public double getValue(Function<SimulatedWarNode, Double> valueFunction) {
        if (!Double.isFinite(this.value)) {
            this.value = valueFunction.apply(this);
        }
        return this.value;
    }

    public SimulatedWarNode(WarCard card, boolean isAttacker) {
        this.aggressor = card.toWarNation(true);
        this.defender = card.toWarNation(false);
        this.turnsLeft = card.turnsLeft();
        this.isAttacker = false;
    }

    public SimulatedWarNode(DBWar war) {
        int aggId = war.attacker_id;
        int defId = war.defender_id;

        aggressor = new WarNation(Locutus.imp().getNationDB().getNation(aggId));
        defender = new WarNation(Locutus.imp().getNationDB().getNation(defId));

        List<AbstractCursor> attacks = war.getAttacks();
        Map.Entry<Integer, Integer> resistance = war.getResistance(attacks);

        aggressor.setResistance(resistance.getKey());
        defender.setResistance(resistance.getValue());

        Map.Entry<Integer, Integer> map = war.getMap(attacks);

        aggressor.setActionPoints(map.getKey());
        defender.setActionPoints(map.getValue());

        setupWarType(war.getWarType());
        setupLootFactor();

        int groundControl = war.getGroundControl();
        int airSuperiority = war.getAirControl();
        int blockade = war.getBlockader();

        if (groundControl == war.attacker_id) {
            aggressor.setGroundControl(true);
        } else if (groundControl == war.defender_id) {
            defender.setGroundControl(true);
        }

        if (airSuperiority==(war.attacker_id)) {
            aggressor.setAirControl(true);
        } else if (airSuperiority==(war.defender_id)) {
            defender.setAirControl(true);
        }

        if (blockade==(war.attacker_id)) {
            aggressor.setBlockade(true);
        } else if (blockade==(war.defender_id)) {
            defender.setBlockade(true);
        }

        this.turnsLeft = war.getTurnsLeft();

        this.isAttacker = false;
    }

    public WarNation get(WarNation previous) {
        if (aggressor.getNation() == previous.getNation()) {
            return aggressor;
        }
        return defender;
    }

    public WarNation getAggressor() {
        return aggressor;
    }

    public WarNation getDefender() {
        return defender;
    }

    public double warDistance(SimulatedWarNode origin) {
        double aggLoss = aggressor.getTotalLossCost(origin.getAggressor(), false, true, false);
        double defLoss = defender.getTotalLossCost(origin.getAggressor(), false, true, false);
        double total = aggLoss - defLoss;

        if (aggressor.isBlockade()) {
            total += Math.abs(aggLoss) * 0.1 * (Math.pow(1.1, aggressor.getBlockade()));
        } else if (defender.isBlockade()) {
            total -= Math.abs(defLoss) * 0.1 * (Math.pow(1.1, aggressor.getBlockade()));
        }

        if (aggressor.isAirControl()) {
            total -= Math.abs(defLoss);
        } else if (defender.isAirControl()) {
            total += Math.abs(aggLoss);
        }

        if (aggressor.isGroundControl()) {
            total -= Math.abs(defLoss) * 0.5;
        } else if (defender.isGroundControl()) {
            total += Math.abs(aggLoss) * 0.5;
        }

//        int attAirLoss = origin.aggressor.getMaxAirStrength(defender) - aggressor.getMaxAirStrength(defender);
//        int defAirLoss = origin.defender.getMaxAirStrength(aggressor) - defender.getMaxAirStrength(aggressor);
//
//        total += (attAirLoss - defAirLoss) * 100000;

        return total;
    }

    public double raidDistance(SimulatedWarNode origin) {
        Map<ResourceType, Double> aggressorLosses = aggressor.getNetLosses(origin.getAggressor());
        double total = PnwUtil.convertedTotal(aggressorLosses);

        int turnDiff = origin.turnsLeft - turnsLeft;
        if (turnDiff > 0) {
            double loot = defender.calculateVictoryLoot(aggressor);
            if (defender.getResistance() <= 0) {
                total -= loot / turnDiff;
            } else {
                int resChange = origin.getDefender().getResistance() - Math.max(0, defender.getResistance());
                total -= (loot * resChange / 100d) / turnDiff;
            }
        }

        return total;
    }

    public void getWarOptionsFast(boolean isAggressor) {
        // get war options, find the one with the highest net damage, then do that.
    }

    public List<SimulatedWarNode> getWarOptions(boolean isAggressor) {
        WarNation aggressor = isAggressor ? this.aggressor : this.defender;
        WarNation defender = isAggressor ? this.defender : this.aggressor;

        WarNation attN = aggressor;

        SimulatedWarNode next;

        if (aggressor.getActionPoints() < 4)
        {
            next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.WAIT);
            if (next.getAggressor().wait(next.getDefender())) {
                next.decrementTurns();
                addChild(next);
            }
            return getChildren();
        }

        {
            if (defender.getAircraft() > 3) {
                next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.AIRSTRIKE_AIR, aggressor.getMaxAirStrength(defender), false);
                if (next.get(aggressor).airstrikeAir(next.get(defender), aggressor.getMaxAirStrength(defender), false)) {
                    addChild(next);
                }
            }

            double enemyGroundStr = defender.getSoldiers() * 1.75 + defender.getTanks() * 22.86d;
            double attGroundStr = aggressor.getSoldiers() * 1.75 + aggressor.getTanks() * 22.86d;
            if (attGroundStr >= enemyGroundStr * 0.25) {
                if (aggressor.getSoldiers() > enemyGroundStr * 3) {
                    next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.GROUND_ATTACK, attN.getSoldiers(), 0, false);
                    addChild(next);
                } else if (aggressor.getSoldiers() * 1.75 > enemyGroundStr * 2) {
                    next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.GROUND_ATTACK, attN.getSoldiers(), 0, true);
                    if (next.get(aggressor).groundAttack(next.get(defender), attN.getSoldiers(), 0, true, false)) {
                        addChild(next);
                    }
                } else {
                    next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.GROUND_ATTACK, attN.getSoldiers(), aggressor.getTanks(), true);
                    if (next.get(aggressor).groundAttack(next.get(defender), attN.getSoldiers(), aggressor.getTanks(), true, false)) {
                        addChild(next);
                    } else {
                        next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.GROUND_ATTACK, attN.getSoldiers(), 0, true, true);
                        if (next.get(aggressor).groundAttack(next.get(defender), attN.getSoldiers(), 0, true, true)) {
                            addChild(next);
                        }
                    }
                }
            }

            if (defender.getTanks() > 7) {
                next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.AIRSTRIKE_TANKS, aggressor.getMaxAirStrength(defender), false);
                if (next.get(aggressor).airstrikeTanks(next.get(defender), aggressor.getMaxAirStrength(defender), false)) {
                    addChild(next);
                }
            }

            if (defender.getShips() > 0) {
                next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.AIRSTRIKE_SHIPS, aggressor.getMaxAirStrength(defender), false);
                if (next.get(aggressor).airstrikeShips(next.get(defender), aggressor.getMaxAirStrength(defender), false)) {
                    addChild(next);
                }
            }

            if (defender.getSoldiers() > 600) {
                next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.AIRSTRIKE_SOLDIERS, aggressor.getMaxAirStrength(defender), false);
                if (next.get(aggressor).airstrikeSoldiers(next.get(defender), aggressor.getMaxAirStrength(defender), false)) {
                    addChild(next);
                }
            }

            next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.NAVAL_ATTACK, aggressor.getShips(), false);
            if (next.get(aggressor).naval(next.get(defender), aggressor.getShips(), false)) {
                addChild(next);
            }

            if (defender.getAvg_infra() > 1700) {
                next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.AIRSTRIKE_INFRA, aggressor.getMaxAirStrength(defender), false);
                if (next.get(aggressor).airstrikeInfra(next.get(defender), aggressor.getMaxAirStrength(defender), false)) {
                    addChild(next);
                }
            }
        }
        if (getChildren().size() == 0 && aggressor.getMaxAirStrength(defender) > defender.getMaxAirStrength(aggressor) * 0.3) {
            next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.AIRSTRIKE_AIR, aggressor.getMaxAirStrength(defender), true);
            if (next.get(aggressor).airstrikeAir(next.get(defender), aggressor.getMaxAirStrength(defender), true)) {
                addChild(next);
            }
        }
        if (getChildren().size() == 0) {
            next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.FORTIFY);
            if (next.get(aggressor).fortify(next.get(defender))) {
                addChild(next);
            }
        }
        if (getChildren().size() == 0) {
            next = new SimulatedWarNode(this, isAggressor, WarNation.Actions.WAIT);
            if (next.get(aggressor).wait(next.get(defender))) {
                next.decrementTurns();
                addChild(next);
            }
        }
        return getChildren();
    }

    public List<SimulatedWarNode> getChildren() {
        if (children instanceof SimulatedWarNode) {
            return Collections.singletonList((SimulatedWarNode) children);
        } else if (children != null) {
            return (List<SimulatedWarNode>) children;
        }
        return Collections.emptyList();
    }

    public MessageEmbed toString(SimulatedWarNode origin) {
        WarNation winner = aggressor.getResistance() <= 0 ? defender : defender.getResistance() <= 0 ? aggressor : null;
        WarNation loser = null;

        String title;

        if (winner != null) {
            loser = aggressor == winner ? defender : aggressor;
            title = (winner.getNation().getNation() + " defeated " + loser.getNation().getNation());
        } else {
            title = "Stalemate";
        }

        EmbedBuilder builder = new EmbedBuilder().setTitle(title);

        WarNation attOri = origin.get(aggressor);
        WarNation defOri = origin.get(defender);

        Map<MilitaryUnit, Integer> attLoss = aggressor.getLosses(attOri);
        Map<MilitaryUnit, Integer> defLoss = defender.getLosses(defOri);

        StringBuilder response = new StringBuilder();

        response.append('\n').append("**Net losses**:").append("```")
                .append(aggressor.getNation().getNation() + ":").append('\n')
                .append(String.format("%5s", attLoss.get(MilitaryUnit.SOLDIER))).append(" \uD83D\uDC82")
                .append(" | ").append(String.format("%4s", attLoss.get(MilitaryUnit.TANK))).append(" \u2699")
                .append(" | ").append(String.format("%3s", attLoss.get(MilitaryUnit.AIRCRAFT))).append(" \u2708")
                .append(" | ").append(String.format("%2s", attLoss.get(MilitaryUnit.SHIP))).append(" \u26F5")
                .append(" | ").append(attOri.getAvg_infra() - aggressor.getAvg_infra()).append(" \uD83C\uDFD7")

                .append('\n')
                .append(defender.getNation().getNation() + ":").append('\n')
                .append(String.format("%5s", defLoss.get(MilitaryUnit.SOLDIER))).append(" \uD83D\uDC82")
                .append(" | ").append(String.format("%4s", defLoss.get(MilitaryUnit.TANK))).append(" \u2699")
                .append(" | ").append(String.format("%3s", defLoss.get(MilitaryUnit.AIRCRAFT))).append(" \u2708")
                .append(" | ").append(String.format("%2s", defLoss.get(MilitaryUnit.SHIP))).append(" \u26F5")
                .append(" | ").append(defOri.getAvg_infra() - defender.getAvg_infra()).append(" \uD83C\uDFD7")

                .append("```")
        ;
        long attLoot = aggressor.getMoney() - attOri.getMoney();
        if (attLoot > 0) {
            response.append('\n').append(" +").append(aggressor.getNation().getNation()).append(" looted an estimated $").append(attLoot);
        }

        long defLoot = defender.getMoney() - defOri.getMoney();
        if (defLoot > 0) {
            response.append('\n').append(" +").append(defender.getNation().getNation()).append(" looted an estimated $").append(defLoot);
        }

        Map<ResourceType, Double> attUse = aggressor.getConsumption();
        Map<ResourceType, Double> defUse = defender.getConsumption();

        response.append('\n').append('\n').append("**").append(aggressor.getNation().getNation() + " consumption: ").append("**")
                .append("```")
                .append(PnwUtil.resourcesToString(PnwUtil.roundResources(attUse)))
                .append("```");

        response.append("**").append(defender.getNation().getNation() + " consumption: ").append("**")
                .append("```")
                .append(PnwUtil.resourcesToString(PnwUtil.roundResources(defUse)))
                .append("```");

        return builder.setDescription(response).build();
    }

    public enum WarGoal {
        SUCCESS,
        FAILURE,
        CONTINUE
    }

    public SimulatedWarNode minimax(boolean war, long timeout) {
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        Function<SimulatedWarNode, Double> raidFunction = new Function<SimulatedWarNode, Double>() {
            @Override
            public Double apply(SimulatedWarNode simulatedWarNode) {
                // TODO calculate military composition as well
                // TODO calculate infra destroyed from beiging
                return -simulatedWarNode.raidDistance(SimulatedWarNode.this);
            }
        };

        Function<SimulatedWarNode, Double> warFunction = new Function<SimulatedWarNode, Double>() {
            @Override
            public Double apply(SimulatedWarNode node) {
                return -node.warDistance(SimulatedWarNode.this);
            }
        };

        Function<SimulatedWarNode, Double> valueFunction = war ? warFunction : raidFunction;

        Function<SimulatedWarNode, SimulatedWarNode.WarGoal> goal = new Function<SimulatedWarNode, SimulatedWarNode.WarGoal>() {
            @Override
            public SimulatedWarNode.WarGoal apply(SimulatedWarNode node) {
                if (node.getAggressor().getResistance() <= 0 || node.getDefender().getResistance() <= 0 || node.getTurnsLeft() <= 0) {
                    return SimulatedWarNode.WarGoal.SUCCESS;
                }
                return SimulatedWarNode.WarGoal.CONTINUE;
            }
        };

        return minimax(System.currentTimeMillis(), alpha, beta, true, valueFunction, goal, timeout);
    }

    public SimulatedWarNode minimax(long end, double alpha, double beta, boolean isAttacker, Function<SimulatedWarNode, Double> valueFunction, Function<SimulatedWarNode, WarGoal> goal) {
        return minimax(end, alpha, beta, isAttacker, valueFunction, goal, 10000);
    }

    public SimulatedWarNode minimax(long end, double alpha, double beta, boolean isAttacker, Function<SimulatedWarNode, Double> valueFunction, Function<SimulatedWarNode, WarGoal> goal, long timeout) {
        return minimax(end, alpha, beta, isAttacker, valueFunction, valueFunction, goal, timeout);
    }

    public SimulatedWarNode minimax(long end, double alpha, double beta, boolean isAttacker, Function<SimulatedWarNode, Double> attValue, Function<SimulatedWarNode, Double> defValue, Function<SimulatedWarNode, WarGoal> goal, long timeout) {
        switch (goal.apply(this)) {
            case SUCCESS:
                return this;
            case FAILURE:
                return null;
            case CONTINUE:
                break;
        }
        if (System.currentTimeMillis() - end > timeout) {
            return null;
        }
        if (isAttacker) {
            SimulatedWarNode best = null;
            double maxEval = Double.NEGATIVE_INFINITY;
            getWarOptions(isAttacker);
            for (SimulatedWarNode child : getChildren()) {
                SimulatedWarNode next = child.minimax(end, alpha, beta, !isAttacker, attValue, defValue, goal, timeout);
                if (next != null) {
                    double eval = next.getValue(attValue);
                    if (eval > maxEval) {
                        maxEval = eval;
                        best = next;
                    }
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            value = maxEval;
            return best;
        } else {
            SimulatedWarNode best = null;
            double minEval = Double.POSITIVE_INFINITY;
            getWarOptions(isAttacker);
            for (SimulatedWarNode child : getChildren()) {
                SimulatedWarNode next = child.minimax(end, alpha, beta, !isAttacker, attValue, defValue, goal, timeout);
                if (next != null) {
                    double eval = next.getValue(defValue);
                    if (eval < minEval) {
                        minEval = eval;
                        best = next;
                    }
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            value = minEval;
            return best;
        }
    }

    public List<SimulatedWarNode> getRaidOptions() {
        return getRaidOptions(aggressor, defender);
    }

    private List<SimulatedWarNode> getRaidOptions(WarNation attacker, WarNation enemy) {
        double defGroundStr = enemy.getSoldiers() * 1.75 + enemy.getMaxTankStrength(attacker);
        double requiredForAssuredGroundVictory = defGroundStr * 2.5;

        int attackSoldiers = attacker.getSoldiers();
        int attackTanks = 0;
        boolean munitions = false;

        boolean immenseGround = true;

        if (requiredForAssuredGroundVictory > attackSoldiers) {
            munitions = true;
            requiredForAssuredGroundVictory -= attackSoldiers * 1.75;
            if (requiredForAssuredGroundVictory > 0) {
                attackTanks = (int) Math.min(attacker.getMaxTankStrength(enemy), requiredForAssuredGroundVictory + 1);
                if (requiredForAssuredGroundVictory > attackTanks) {
                    double roll = WarNation.roll(defGroundStr, attackSoldiers * 1.75 + attackTanks * 40);
                    if (roll < 1.5) {
                        attackSoldiers = 0;
                        attackTanks = 0;
                    }
                    else if (roll < 2.5) {
                        immenseGround = false;
                    }
                }
            }
        }

        if (attackSoldiers != 0 || attackTanks != 0) {
            SimulatedWarNode next = new SimulatedWarNode(this, true, WarNation.Actions.GROUND_ATTACK, attackSoldiers, attackTanks, munitions, false);
            if (next.getAggressor().groundAttack(next.getDefender(), attackSoldiers, attackTanks, munitions, false)) {
                addChild(next);
            }
            if (immenseGround) {
                return getChildren();
            }
        }

        int attAirStr = attacker.getMaxAirStrength(enemy);
        int enemyAirStr = enemy.getMaxAirStrength(attacker);
        int assuredAirVictoryStr = (int) Math.max(3, Math.ceil(enemyAirStr * 2.5));

        if (assuredAirVictoryStr <= attAirStr) {
            if (enemyAirStr > 0) {
                SimulatedWarNode next = new SimulatedWarNode(this, true, WarNation.Actions.AIRSTRIKE_AIR, assuredAirVictoryStr, false);
                if (next.getAggressor().airstrikeAir(next.getDefender(), assuredAirVictoryStr, false)) {
                    addChild(next);
                }
            } else if (enemy.getMaxTankStrength(attacker) != 0) {
                SimulatedWarNode next = new SimulatedWarNode(this, true, WarNation.Actions.AIRSTRIKE_TANKS, assuredAirVictoryStr, false);
                if (next.getAggressor().airstrikeTanks(next.getDefender(), assuredAirVictoryStr, false)) {
                    addChild(next);
                }
            } else {
                SimulatedWarNode next = new SimulatedWarNode(this, true, WarNation.Actions.AIRSTRIKE_SOLDIERS, assuredAirVictoryStr, false);
                if (next.getAggressor().airstrikeSoldiers(next.getDefender(), assuredAirVictoryStr, false)) {
                    addChild(next);
                }
            }
        } else if (enemyAirStr < attAirStr && attAirStr >= 3) {
            SimulatedWarNode next = new SimulatedWarNode(this, true, WarNation.Actions.AIRSTRIKE_AIR, attAirStr, false);
            if (next.getAggressor().airstrikeAir(next.getDefender(), attAirStr, false)) {
                addChild(next);
            }
        }

        int enemyNavy = enemy.getShips();
        int assuredNavalVictoryStr = (int) Math.max(1, Math.ceil(enemyNavy * 2.5));
        if (assuredNavalVictoryStr <= attacker.getShips()) {
            SimulatedWarNode next = new SimulatedWarNode(this, true, WarNation.Actions.NAVAL_ATTACK, assuredNavalVictoryStr, false);
            if (next.getAggressor().naval(next.getDefender(), assuredNavalVictoryStr, false)) {
                addChild(next);
            }
        } else if (attacker.getShips() > 0) {
            SimulatedWarNode next = new SimulatedWarNode(this, true, WarNation.Actions.NAVAL_ATTACK, attacker.getShips(), false);
            if (next.getAggressor().naval(next.getDefender(), attacker.getShips(), false)) {
                addChild(next);
            }
        }
        if (aggressor.getActionPoints() < 4) {
            SimulatedWarNode next = new SimulatedWarNode(this, true, WarNation.Actions.WAIT);
            if (next.getAggressor().wait(next.getDefender())) {
                next.decrementTurns();
                addChild(next);
            }
        }
        return getChildren();
    }

    public String getActionString() {
        if (method == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();

        WarNation actor = isAttacker ? aggressor : defender;

        result.append(actor.getNation().getNation()).append(": ");
        result.append(method.getName());

        Parameter[] methodParams = method.getParameters();
        for (int i = 1; i < methodParams.length; i++) {
            result.append(" | ").append(methodParams[i].getName()).append("=").append(arguments[i - 1]);
        }

        if (parent != null) {
            WarNation att = parent.getAggressor();
            WarNation def = parent.getDefender();

            for (MilitaryUnit unit : MilitaryUnit.values()) {
                if (att.getUnitLoss(unit, aggressor) != 0) {
                    result.append(" (att_" + unit.name().toLowerCase() + "=" + att.getUnitLoss(unit, aggressor) + ")");
                }
                if (def.getUnitLoss(unit, defender) != 0) {
                    result.append(" (def_" + unit.name().toLowerCase() + "=" + def.getUnitLoss(unit, defender) + ")");
                }
            }
        }

        return result.toString();
    }

    @Override
    public String toString() {
        return getActionString();
    }
}
