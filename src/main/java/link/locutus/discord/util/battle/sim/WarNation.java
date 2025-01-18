package link.locutus.discord.util.battle.sim;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.util.PW;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.building.Buildings;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class WarNation {
    private final DBNation nation;
    private boolean groundControl;
    private boolean airControl;
    private int blockade;
    private int resistance;
    private int actionPoints;
    private double[] consumption = new double[ResourceType.values.length];
    private boolean fortified;

    private double infraFactor = 1;
    private double lootFactor = 1;

    private int soldiers;
    private int tanks;
    private int aircraft;
    private int ships;
    private int cities;
    private int avg_infra;
    private long money;

    public static WarNation generate(String name) {
        DBNation nation1 = new SimpleDBNation(new DBNationData());
        nation1.setNation(name);
        nation1.setMissiles(0);
        nation1.setNukes(0);
        nation1.setWarPolicy(WarPolicy.PIRATE);
        int soldierMax = Buildings.BARRACKS.getUnitCap() * Buildings.BARRACKS.cap(f -> false) * 100;
        int tankMax = Buildings.FACTORY.getUnitCap() * Buildings.FACTORY.cap(f -> false) * 100;
        int airMax = Buildings.HANGAR.getUnitCap() * Buildings.HANGAR.cap(f -> false) * 100;
        int shipMax = Buildings.DRYDOCK.getUnitCap() * Buildings.DRYDOCK.cap(f -> false) * 100;

        nation1.setSoldiers(soldierMax);
        nation1.setTanks(tankMax);
        nation1.setAircraft(airMax);
        nation1.setShips(shipMax);
        nation1.setCities(1);

        WarNation warNation1 = new WarNation(nation1, false);
        warNation1.setActionPoints(12);
        warNation1.setResistance(100);
        warNation1.getNation().setMissiles(0);
        warNation1.getNation().setNukes(0);

        return warNation1;
    }

    public WarNation(DBNation nation) {
        this(nation, true);
    }

    public WarNation(DBNation nation, boolean clone) {
        this.nation = clone ? nation.copy() : nation;
        this.soldiers = nation.getSoldiers();
        this.tanks = nation.getTanks();
        this.aircraft = nation.getAircraft();
        this.ships = nation.getShips();
        this.cities = nation.getCities();
        this.avg_infra = (int) nation.getAvg_infra();
        this.money = 0;
    }

    public WarNation(WarNation other) {
        updateStats(other);
        this.nation = other.nation;
    }

    public int getSoldiers() {
        return soldiers;
    }

    public int getTanks() {
        return tanks;
    }

    public int getAircraft() {
        return aircraft;
    }

    public int getShips() {
        return ships;
    }

    public int getCities() {
        return cities;
    }

    public long getMoney() {
        return money;
    }

    public void setBlockade(int blockade) {
        this.blockade = blockade;
    }

    public void setConsumption(double[] consumption) {
        this.consumption = consumption;
    }

    public void setSoldiers(int soldiers) {
        this.soldiers = soldiers;
    }

    public void setTanks(int tanks) {
        this.tanks = tanks;
    }

    public void setAircraft(int aircraft) {
        this.aircraft = aircraft;
    }

    public void setShips(int ships) {
        this.ships = ships;
    }

    public void setCities(int cities) {
        this.cities = cities;
    }

    public void setAvg_infra(int avg_infra) {
        this.avg_infra = avg_infra;
    }

    public void setMoney(long money) {
        this.money = money;
    }

    public void updateStats(WarNation other) {
        this.groundControl = other.groundControl;
        this.airControl = other.airControl;
        this.blockade = other.blockade;
        this.resistance = other.resistance;
        this.actionPoints = other.actionPoints;
        this.consumption = other.consumption.clone();
        this.fortified = other.fortified;
        this.soldiers = other.getSoldiers();
        this.tanks = other.getTanks();
        this.aircraft = other.getAircraft();
        this.ships = other.getShips();
        this.cities = other.getCities();
        this.avg_infra = other.getAvg_infra();
        this.money = other.getMoney();
    }

    public int getAvg_infra() {
        return avg_infra;
    }

    public boolean isFortified() {
        return fortified;
    }

    public void setFortified(boolean fortified) {
        this.fortified = fortified;
    }

    public double getFortifyFactor() {
        return fortified ? 1.25 : 1;
    }

    public void setInfraFactor(double infraFactor) {
        this.infraFactor = infraFactor;
    }

    public void setLootFactor(double lootFactor) {
        this.lootFactor = lootFactor;
    }

    public double getInfraFactor() {
        return infraFactor;
    }

    public double getLootFactor() {
        return lootFactor;
    }

    public DBNation getNation() {
        return nation;
    }

    public int calculateVictoryLoot(WarNation other) {
        return (int) (getMoney() * 0.14);
    }

    public boolean isGroundControl() {
        return groundControl;
    }

    public void setGroundControl(boolean groundControl) {
        this.groundControl = groundControl;
    }

    public boolean isAirControl() {
        return airControl;
    }

    public void setAirControl(boolean airControl) {
        this.airControl = airControl;
    }

    public boolean isBlockade() {
        return blockade != 0;
    }

    public int getBlockade() {
        return blockade;
    }

    public void setBlockade(boolean blockade) {
        this.blockade = blockade ? Math.max(1, this.blockade) : 0;
    }

    public void incrementBlockade() {
        if (this.blockade != 0) this.blockade++;
    }

    public int getResistance() {
        return resistance;
    }

    public void setResistance(int resistance) {
        this.resistance = resistance;
    }

    public void decrementResistance(int resistance) {
        this.resistance -= resistance;
    }

    public int getActionPoints() {
        return actionPoints;
    }

    public void setActionPoints(int actionPoints) {
        this.actionPoints = actionPoints;
    }

    public void consume(ResourceType type, double value, int victory, double max) {
        switch (victory) {
            case 0:
                value = Math.min(value * 0.4, max);
                break;
            case 1:
                value *= 0.7;
                break;
            case 2:
                value *= 0.9;
                break;
        }
        consumption[type.ordinal()] += value;
    }

    public int getMaxAirStrength(WarNation enemy) {
        return (int) (getAircraft());// * (enemy.isGroundControl() ? 0.66 : 1));
    }

    public int getMaxTankStrength(WarNation enemy) {
        return (int) (getTanks() * (enemy.isAirControl() ? 0.66 : 1));
    }

    public boolean groundAttack(WarNation enemy, int soldiers, int tanks, boolean munitions) {
        return groundAttack(enemy, soldiers, tanks, munitions, false);
    }

    public boolean groundAttack(WarNation enemy, int soldiers, int tanks, boolean munitions, boolean suicide) {
        return groundAttack(enemy, munitions ? soldiers : 0, munitions ? 0 : soldiers, tanks, true, suicide, -1);
    }

    public boolean groundAttack(WarNation enemy, int soldiers, int soldiersUnarmed, int tanks, boolean enemyUsesMunitions, boolean suicide, int victory) {
        if (actionPoints < 3) return false;
        actionPoints -= 3;
        setFortified(false);

        double attTankStr = (tanks * 40);
        double attSoldStr = soldiers * 1.7_5 + soldiersUnarmed;
        double attStr = attSoldStr + attTankStr;
        double defTankStr = enemy.getMaxTankStrength(this) * 40;
        double defSoldStr = Math.max(50, enemy.getSoldiers() * (enemyUsesMunitions ? 1.7_5 : 1));
        double defStr = defSoldStr + defTankStr;

        double roll = roll(defStr, attStr);
        double attFactor = (1680 * (3 - roll) + 1800 * roll) / 3;
        double defFactor = 1680 + (1800 - attFactor);

        double defTankLoss = ((attTankStr * 0.7 + 1)/ defFactor + (attSoldStr * 0.7 + 1) / 2250) * 1.33;
        double attTankLoss =  ((defTankStr * 0.7 + 1)/ attFactor + (defSoldStr * 0.7 + 1) / 2250) * enemy.getFortifyFactor()  * 1.33;

        double attSoldLoss = ((defSoldStr * 0.7 + 1)/22 + (defTankStr * 0.7 + 1)/7.33) * enemy.getFortifyFactor() * 0.3125;
        double defSoldLoss = (attSoldStr * 0.7 + 1)/22 + (attTankStr * 0.7 + 1)/7.33 * 0.3125;

        victory = victory == -1 ? getVictoryType(defStr, attStr) : victory;

        double attMuni = (0.0002 * soldiers) + 0.01 * tanks;
        double attGas = 0.01 * tanks;
        consume(ResourceType.MUNITIONS, attMuni, 3, 0);
        consume(ResourceType.GASOLINE, attGas, 3, 0);

        double enemyMuni = 0.01 * enemy.getMaxTankStrength(this) + (enemyUsesMunitions ? 0.0002 * enemy.getSoldiers() : 0);
        double enemyGas = 0.01 * enemy.getMaxTankStrength(this);
        enemy.consume(ResourceType.MUNITIONS, enemyMuni, victory, attMuni);
        enemy.consume(ResourceType.GASOLINE, enemyGas, victory, attGas);

        boolean hadGc = isGroundControl();
        long money = enemy.getMoney();
        switch (victory) {
            case 0:
                if (!suicide && defSoldLoss * 5 + defTankLoss * 2500 <= attSoldLoss * 5 + (0.0002 * soldiers + 0.02 * tanks + attTankLoss) * 2500) {
                    return false;
                }
                break;
            case 3:
                setGroundControl(true);
                enemy.decrementResistance(3);
            case 2:
                enemy.decrementResistance(3);
            case 1:
                enemy.decrementResistance(4);

                /*
                Tanks' stolen money is rand(2010, 2515) / 100 * Tanks
Soldiers is rand (88, 110) / 100 * Soldiers
                 */

                double loot = Math.max(0, Math.min(Math.min(((soldiers * 0.99) + (tanks * 22.625) * victory), money * 0.75), money - 50000 * enemy.getCities())) * getLootFactor();

                double infra = Math.max(Math.min(((soldiers - (enemy.getSoldiers() * 0.5)) * 0.000606061 + (tanks - (enemy.getTanks() * 0.5)) * 0.01) * 0.95 * (victory / 3d), enemy.getAvg_infra() * 0.2 + 25), 0);

                setMoney((long) (getMoney() + loot));
                enemy.setMoney(Math.max(0, (long) (enemy.getMoney() - loot)));

                enemy.setAvg_infra((int) (enemy.getAvg_infra() - (infra / enemy.getCities())));

                enemy.setGroundControl(false);
        }

        if (hadGc && victory != 0) {
            double factor = 0;
            switch (victory) {
                case 3:
                    factor = 0.005025;
                    break;
                case 2:
                    factor = 0.00335;
                    break;
                case 1:
                    factor = 0.001675;
            }
            double defAirLosses = tanks * factor;
            enemy.setAircraft((int) Math.max(0, enemy.getAircraft() - defAirLosses));
        }

        enemy.setTanks((int) Math.max(0, enemy.getTanks() - defTankLoss));
        setTanks((int) Math.max(0, getTanks() - attTankLoss));

        enemy.setSoldiers((int) Math.max(0, enemy.getSoldiers() - defSoldLoss));
        setSoldiers((int) Math.max(0, getSoldiers() - attSoldLoss));

        checkResistance(enemy);
        return true;
    }

    public boolean airstrikeSoldiers(WarNation enemy, int aircraft, boolean suicide) {
        if (actionPoints < 4) return false;
        actionPoints -= 4;

        setFortified(false);
        double defStr = enemy.getMaxAirStrength(this);

        double roll = roll(defStr, aircraft);
        int victory = getVictoryType(defStr, aircraft);

        double attGas = 0.25 * aircraft;
        consume(ResourceType.GASOLINE, attGas, 3, 0);
        consume(ResourceType.MUNITIONS, 0.25 * aircraft, 3, 0);
        enemy.consume(ResourceType.GASOLINE, 0.25 * defStr, victory, attGas);
        enemy.consume(ResourceType.MUNITIONS, 0.25 * defStr, victory, attGas);

        int soldiersKilled = (int) (0.58139534883720930232558139534884 * (roll * Math.round( Math.max( Math.min(enemy.getSoldiers(), Math.min(enemy.getSoldiers() * 0.75 + 1000, (aircraft - defStr * 0.5) * 50 * 0.95)), 0)) / 3));

        if (soldiersKilled == 0 && !suicide) return false;

        double infra = Math.max(Math.min((aircraft - (defStr * 0.5)) * 0.35353535 * 0.95 * (roll / 3), enemy.getAvg_infra() * 0.5 + 100), 0);
        infra = infra / 3; // Not target infra

        if (victory != 0) {
            enemy.setAirControl(false);
        }
        if (victory == 3) {
            setAirControl(true);
        }

        switch (victory) {
            case 0:
                if (suicide) {
                    break;
                }
                return false;
            case 3:
                setAirControl(true);
                enemy.decrementResistance(3);
            case 2:
                enemy.decrementResistance(3);
            case 1:
                enemy.decrementResistance(6);
                enemy.setAirControl(false);
        }

        double attAirLoss = enemy.getFortifyFactor() * 9 * (defStr * 0.7)/54;
        double defAirLoss = 9 * (aircraft * 0.7)/54;

        setAircraft(Math.max(0, (int) (getAircraft() - attAirLoss)));
        enemy.setAircraft(Math.max(0, (int) (enemy.getAircraft() - defAirLoss)));

        if (victory != 0) {
            enemy.setAvg_infra((int) (enemy.getAvg_infra() - (infra / enemy.getCities())));

            enemy.setSoldiers(Math.max(0, enemy.getSoldiers() - soldiersKilled));
        }

        checkResistance(enemy);
        return true;
    }

    public boolean airstrikeTanks(WarNation enemy, int aircraft, boolean suicide) {
        if (actionPoints < 4) return false;
        actionPoints -= 4;

        setFortified(false);
        double defStr = enemy.getMaxAirStrength(this);

        double roll = roll(defStr, aircraft);
        int victory = getVictoryType(defStr, aircraft);

        double attGas = 0.25 * aircraft;
        consume(ResourceType.GASOLINE, attGas, 3, 0);
        consume(ResourceType.MUNITIONS, attGas, 3, 0);
        enemy.consume(ResourceType.GASOLINE, 0.25 * defStr, victory, attGas);
        enemy.consume(ResourceType.MUNITIONS, 0.25 * defStr, victory, attGas);

        int tanksKilled = (int) (0.32558139534883720930232558139535 * (roll * Math.round(Math.max( Math.min(enemy.getTanks(), Math.min(enemy.getTanks() * 0.75 + 10, (aircraft - defStr * 0.5) * 2.5 * 0.95)), 0)) / 3));

        if (tanksKilled == 0 && !suicide) return false;

        double infra = Math.max(Math.min((aircraft - (defStr * 0.5)) * 0.35353535 * 0.95 * (roll / 3), enemy.getAvg_infra() * 0.5 + 100), 0);
        infra = infra / 3; // Not target infra

        switch (victory) {
            case 0:
                if (suicide) {
                    break;
                }
                return false;
            case 3:
                setAirControl(true);
                enemy.decrementResistance(3);
            case 2:
                enemy.decrementResistance(3);
            case 1:
                enemy.decrementResistance(6);
                enemy.setAirControl(false);
        }

        double attAirLoss = 0.5 * enemy.getFortifyFactor() * 9 * (defStr * 0.7)/54;
        double defAirLoss = 0.5 * 9 * (aircraft * 0.7)/54;

        setAircraft(Math.max(0, (int) (getAircraft() - attAirLoss)));
        enemy.setAircraft(Math.max(0, (int) (enemy.getAircraft() - defAirLoss)));

        if (victory != 0) {
            enemy.setAvg_infra((int) (enemy.getAvg_infra() - (infra / enemy.getCities())));

            enemy.setTanks(Math.max(0, enemy.getTanks() - tanksKilled));
        }

        checkResistance(enemy);
        return true;
    }

    public boolean airstrikeShips(WarNation enemy, int aircraft, boolean suicide) {
        if (actionPoints < 4) return false;
        actionPoints -= 4;

        setFortified(false);
        double defStr = enemy.getMaxAirStrength(this);

        double roll = roll(defStr, aircraft);
        int victory = getVictoryType(defStr, aircraft);

        double attGas = 0.25 * aircraft;
        consume(ResourceType.GASOLINE, attGas, 3, 0);
        consume(ResourceType.MUNITIONS, attGas, 3, 0);
        enemy.consume(ResourceType.GASOLINE, 0.25 * defStr, victory, attGas);
        enemy.consume(ResourceType.MUNITIONS, 0.25 * defStr, victory, attGas);

        int shipsKilled = (int) (0.82926829268292682926829268292683 * (roll * Math.round(Math.max( Math.min(enemy.getShips(), Math.min(enemy.getShips() * 0.5 + 4, (aircraft - defStr * 0.5) * 0.0285 * 0.95)), 0)) / 3));

        if (shipsKilled == 0 && !suicide) return false;

        double infra = Math.max(Math.min((aircraft - (defStr * 0.5)) * 0.35353535 * 0.95 * (roll / 3), enemy.getAvg_infra() * 0.5 + 100), 0);
        infra = infra / 3; // Not target infra

        switch (victory) {
            case 0:
                if (suicide) {
                    break;
                }
                return false;
            case 3:
                setAirControl(true);
                enemy.decrementResistance(3);
            case 2:
                enemy.decrementResistance(3);
            case 1:
                enemy.decrementResistance(6);
                enemy.setAirControl(false);
        }

        double attAirLoss = 0.5 * enemy.getFortifyFactor() * 9 * (defStr * 0.7)/54;
        double defAirLoss = 0.5 * 9 * (aircraft * 0.7)/54;

        setAircraft(Math.max(0, (int) (getAircraft() - attAirLoss)));
        enemy.setAircraft(Math.max(0, (int) (enemy.getAircraft() - defAirLoss)));

        if (victory != 0) {
            enemy.setAvg_infra((int) (enemy.getAvg_infra() - (infra / enemy.getCities())));

            enemy.setShips(Math.max(0, enemy.getShips() - shipsKilled));
        }

        checkResistance(enemy);
        return true;
    }

    public boolean airstrikeInfra(WarNation enemy, int aircraft, boolean suicide) {
        if (actionPoints < 4) return false;
        actionPoints -= 4;

        setFortified(false);
        double defStr = enemy.getMaxAirStrength(this);

        double roll = roll(defStr, aircraft);
        int victory = getVictoryType(defStr, aircraft);

        double attGas = 0.25 * aircraft;
        consume(ResourceType.GASOLINE, attGas, 3, 0);
        consume(ResourceType.MUNITIONS, attGas, 3, 0);
        enemy.consume(ResourceType.GASOLINE, 0.25 * defStr, victory, attGas);
        enemy.consume(ResourceType.MUNITIONS, 0.25 * defStr, victory, attGas);

        double infra = Math.max(Math.min((aircraft - (defStr * 0.5)) * 0.35353535 * 0.95 * (roll / 3), enemy.getAvg_infra() * 0.5 + 100), 0);

        switch (victory) {
            case 0:
                if (suicide) {
                    break;
                }
                return false;
            case 3:
                setAirControl(true);
                enemy.decrementResistance(3);
            case 2:
                enemy.decrementResistance(3);
            case 1:
                enemy.decrementResistance(6);
                enemy.setAirControl(false);
        }

        double attAirLoss = 0.5 * enemy.getFortifyFactor() * 9 * (defStr * 0.7)/54;
        double defAirLoss = 0.5 * 9 * (aircraft * 0.7)/54;

        setAircraft(Math.max(0, (int) (getAircraft() - attAirLoss)));
        enemy.setAircraft(Math.max(0, (int) (enemy.getAircraft() - defAirLoss)));

        if (victory != 0) {
            enemy.setAvg_infra((int) (enemy.getAvg_infra() - (infra / enemy.getCities())));
        }

        if (infra == 0 || enemy.getAvg_infra() == 0) {
            return false;
        }

        checkResistance(enemy);
        return true;
    }

    public boolean airstrikeAir(WarNation enemy, int aircraft) {
        return airstrikeAir(enemy, aircraft, false);
    }

    public boolean airstrikeAir(WarNation enemy, int aircraft, boolean suicide) {
        if (actionPoints < 4) return false;
        actionPoints -= 4;

        setFortified(false);
        double defStr = enemy.getMaxAirStrength(this);

        double roll = roll(defStr, aircraft);
        int victory = getVictoryType(defStr, aircraft);

        double attGas = 0.25 * aircraft;
        consume(ResourceType.GASOLINE, attGas, 3, 0);
        consume(ResourceType.MUNITIONS, attGas, 3, 0);
        enemy.consume(ResourceType.GASOLINE, 0.25 * defStr, victory, attGas);
        enemy.consume(ResourceType.MUNITIONS, 0.25 * defStr, victory, attGas);

        double infra = Math.max(Math.min((aircraft - (defStr * 0.5)) * 0.35353535 * 0.95 * (roll / 3), enemy.getAvg_infra() * 0.5 + 100), 0);
        infra = infra / 3; // Not target infra

        switch (victory) {
            case 0:
                if (suicide) {
                    break;
                }
                return false;
            case 3:
                setAirControl(true);
                enemy.decrementResistance(3);
            case 2:
                enemy.decrementResistance(3);
            case 1:
                enemy.decrementResistance(6);
                enemy.setAirControl(false);
        }

        double attAirLoss = 0.63855421686746987951807228915663 * enemy.getFortifyFactor() * 9 * (defStr * 0.7)/54;
        double defAirLoss = 0.63855421686746987951807228915663 * 9 * (aircraft * 0.7)/38;

        if (defAirLoss != 0) {

            setAircraft(Math.max(0, (int) (getAircraft() - attAirLoss)));
            enemy.setAircraft(Math.max(0, (int) (enemy.getAircraft() - defAirLoss)));

            if (victory != 0) {
                enemy.setAvg_infra((int) (enemy.getAvg_infra() - (infra / enemy.getCities())));
            }
        }

        checkResistance(enemy);
        return true;
    }

    public void checkResistance(WarNation enemy) {
        if (enemy.getResistance() <= 0) {


            double infraLost = enemy.getAvg_infra() * 0.1 * getInfraFactor();
            enemy.setAvg_infra((int) (enemy.getAvg_infra() - infraLost));

            long loot = (long) (enemy.getMoney() * 0.1 * getLootFactor());
            enemy.setMoney(enemy.getMoney() - loot);
            setMoney(getMoney() + loot);
        }
    }

    public boolean naval(WarNation enemy, int ships, boolean suicide) {
        if (actionPoints < 4) return false;
        actionPoints -= 4;

        setFortified(false);
        int defShips = enemy.getShips();

        double roll = roll(defShips, ships);
        int victory = getVictoryType(defShips, ships);

        switch (victory) {
            case 0:
                if (suicide) {
                    break;
                }
                return false;
            case 3:
                enemy.setBlockade(true);
                enemy.decrementResistance(3);
            case 2:
                enemy.decrementResistance(3);
            case 1:
                enemy.decrementResistance(8);
                setBlockade(false);
        }

        if (victory != 0) {
            double infra = Math.max( Math.min(ships - (defShips * 0.5) * 2.625 * 0.95 * (roll / 3), enemy.getAvg_infra() * 0.5 + 25), 0);
            enemy.setAvg_infra((int) (enemy.getAvg_infra() - (infra / enemy.getCities())));
        }

        double attNavyLoss = 0.44166666666666666666666666666667 * enemy.getFortifyFactor() * 12 * (defShips * 0.7)/35;
        double defNavyLoss = 0.44166666666666666666666666666667 * 12 * (ships * 0.7)/35;

        setShips(Math.max(0, (int) (getShips() - attNavyLoss)));
        enemy.setShips(Math.max(0, (int) (enemy.getShips() - defNavyLoss)));

        int attGas = 2 * ships;
        int attMuni = 3 * ships;
        consume(ResourceType.GASOLINE, attGas, 3, 0);
        consume(ResourceType.MUNITIONS, attMuni, 3, 0);
        enemy.consume(ResourceType.GASOLINE, 2 * defShips, victory, attGas);
        enemy.consume(ResourceType.MUNITIONS, 3 * defShips, victory, attMuni);

        checkResistance(enemy);
        return true;
    }

    public boolean wait(WarNation enemy) {
        actionPoints = Math.min(actionPoints + 1, 12);
        enemy.actionPoints = Math.min(enemy.actionPoints + 1, 12);
        return true;
    }

    public boolean fortify(WarNation enemy) {
        if (actionPoints < 3 || isFortified()) return false;
        actionPoints -= 3;

        setFortified(true);
        return true;
    }

    public static double roll(double defending, double attacking) {
        double minDef = defending * 0.4d;
        double minAtt = attacking * 0.4d;
        if (attacking <= minDef || attacking == 0) {
            return 0;
        }
        if (defending < minAtt) {
            return 3;
        }
        double defMean = (defending + minDef) * 0.5;
        double greater = attacking - defMean;
        double lessThan = defMean - minAtt;
        if (greater <= 0) {
            return 0;
        }
        if (lessThan <= 0) {
            return 3;
        }
        return 3 * greater / (greater + lessThan);
    }

    public static int getVictoryType(double defending, double attacking) {
        return (int) Math.max(0, Math.min(3, Math.round(roll(defending, attacking))));
    }

    public Map<ResourceType, Double> getConsumption() {
        return ResourceType.resourcesToMap(consumption);
    }

    public double getConsumption(ResourceType type) {
        return consumption[type.ordinal()];
    }

    public int[] getMilitaryLosses(int[] buffer, WarNation origin) {
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            int originUnits = origin.getUnits(unit);
            int unitLosses = originUnits - getUnits(unit);
            if (unitLosses != 0) {
                buffer[unit.ordinal()] += unitLosses;
            }
        }
        return buffer;
    }

    public int getUnitLoss(MilitaryUnit unit, WarNation parent) {
        return parent.getUnits(unit) - getUnits(unit);
    }

    public int getUnits(MilitaryUnit unit) {
        switch (unit)  {
            case SOLDIER:
                return soldiers;
            case TANK:
                return tanks;
            case AIRCRAFT:
                return aircraft;
            case SHIP:
                return ships;
            default:
                return 0;
        }
    }

    public double getUnitLossCost(WarNation origin) {
        double total = 0;
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            int originUnits = origin.getUnits(unit);
            int unitLosses = originUnits - getUnits(unit);
            if (unitLosses != 0) {
                total += unit.getConvertedCost() * unitLosses;
            }
        }
        return total;
    }

    public double getTotalLossCost(WarNation origin, boolean loot, boolean includeConsumption, boolean includeInfra) {
        double total = getUnitLossCost(origin);
        if (includeConsumption) {
            for (int i = 0; i < consumption.length; i++) {
                double value = consumption[i];
                if (value != 0) total += value * ResourceType.values[i].getMarketValue();
            }
        }
        if (includeInfra) {
            double infraLosses = PW.City.Infra.calculateInfra(getAvg_infra(), origin.getAvg_infra());
            total += infraLosses;
        }

        if (loot) {
            long lootLosses = origin.getMoney() - getMoney();
            total += lootLosses;
        }
        return total;
    }

    public Map<MilitaryUnit, Integer> getLosses(WarNation origin) {
        Map<MilitaryUnit, Integer> total = new HashMap<>();
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            int originUnits = origin.getUnits(unit);
            int unitLosses = originUnits - getUnits(unit);
            if (unitLosses != 0) {
                total.put(unit, unitLosses);
            }
        }
        return total;
    }

    public Map<ResourceType, Double> getNetLosses(WarNation original) {
        return getNetLosses(original, true, true, true);
    }

    public Map<ResourceType, Double> getNetLosses(WarNation original, boolean loot, boolean includeConsumption, boolean includeInfra) {
        Map<ResourceType, Double> map = includeConsumption ? ResourceType.resourcesToMap(consumption) : new HashMap<>();

        int soldierLosses = original.getSoldiers() - getSoldiers();
        int tankLosses = original.getTanks() - getTanks();
        int airLosses = original.getAircraft() - getAircraft();
        int navalLosses = original.getShips() - getShips();

        map.put(ResourceType.MONEY, soldierLosses * 5 + map.getOrDefault(ResourceType.MONEY, 0d));

        map.put(ResourceType.MONEY, tankLosses * 60 + map.getOrDefault(ResourceType.MONEY, 0d));
        map.put(ResourceType.STEEL, tankLosses + map.getOrDefault(ResourceType.STEEL, 0d));

        map.put(ResourceType.MONEY, airLosses * 4000 + map.getOrDefault(ResourceType.MONEY, 0d));
        map.put(ResourceType.ALUMINUM, airLosses * 5 + map.getOrDefault(ResourceType.ALUMINUM, 0d));

        map.put(ResourceType.MONEY, navalLosses * 50000 + map.getOrDefault(ResourceType.MONEY, 0d));
        map.put(ResourceType.STEEL, navalLosses * 30 + map.getOrDefault(ResourceType.STEEL, 0d));

        if (includeInfra) {
            double infraLosses = PW.City.Infra.calculateInfra(getAvg_infra(), original.getAvg_infra());
            map.put(ResourceType.MONEY, infraLosses * getCities() + map.getOrDefault(ResourceType.MONEY, 0d));
        }

        if (loot) {
            long lootLosses = original.getMoney() - getMoney();
            map.put(ResourceType.MONEY, lootLosses + map.getOrDefault(ResourceType.MONEY, 0d));
        }

        return map;
    }

    public void setMMR(int barracks, int factories, int hangars, int drydocks) {
        soldiers = barracks * cities * Buildings.BARRACKS.getUnitCap();
        tanks = factories * cities * Buildings.FACTORY.getUnitCap();
        aircraft = hangars * cities * Buildings.HANGAR.getUnitCap();
        ships = drydocks * cities * Buildings.DRYDOCK.getUnitCap();
    }

    /*
    Actions
     */

    public static class Actions {
        public static final Method GROUND_ATTACK;
        public static final Method AIRSTRIKE_SOLDIERS;
        public static final Method AIRSTRIKE_TANKS;
        public static final Method AIRSTRIKE_AIR;
        public static final Method AIRSTRIKE_SHIPS;
        public static final Method AIRSTRIKE_INFRA;
        public static final Method NAVAL_ATTACK;
        public static final Method FORTIFY;
        public static final Method WAIT;

        static {
            try {
                GROUND_ATTACK = WarNation.class.getDeclaredMethod("groundAttack", WarNation.class, int.class, int.class, boolean.class);
                AIRSTRIKE_SOLDIERS = WarNation.class.getDeclaredMethod("airstrikeSoldiers", WarNation.class, int.class, boolean.class);
                AIRSTRIKE_TANKS =    WarNation.class.getDeclaredMethod("airstrikeTanks", WarNation.class, int.class, boolean.class);
                AIRSTRIKE_AIR =      WarNation.class.getDeclaredMethod("airstrikeAir", WarNation.class, int.class, boolean.class);
                AIRSTRIKE_SHIPS =    WarNation.class.getDeclaredMethod("airstrikeShips", WarNation.class, int.class, boolean.class);
                AIRSTRIKE_INFRA =    WarNation.class.getDeclaredMethod("airstrikeInfra", WarNation.class, int.class, boolean.class);
                NAVAL_ATTACK =    WarNation.class.getDeclaredMethod("naval", WarNation.class, int.class, boolean.class);
                FORTIFY =    WarNation.class.getDeclaredMethod("fortify", WarNation.class);
                WAIT =    WarNation.class.getDeclaredMethod("wait", WarNation.class);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
