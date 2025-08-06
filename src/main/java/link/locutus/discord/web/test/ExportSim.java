package link.locutus.discord.web.test;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.battle.sim.SimulatedWarNode;
import link.locutus.discord.util.battle.sim.WarNation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ExportSim {
    private final List<String> row;
    private final ArrayList<String> header;
    private int count = 0;
    private PrintWriter out;

    public ExportSim(File file) throws IOException {
        out = new PrintWriter(new FastBufferedOutputStream(new FileOutputStream(file)));
        this.row = new ArrayList<>(Arrays.asList("attack_type",
//                "success",

                "str_attsoldiers_armed",
                "str_attsoldiers_unarmed",
                "str_atttanks",
                "str_attaircraft",
                "str_attships",

                "str_defsoldiers",
                "str_deftanks",
                "str_defaircraft",
                "str_defships",
                "str_definfra",

                "loss_attsoldiers",
                "loss_atttanks",
                "loss_attaircraft",
                "loss_attships",
                "loss_attvalue",

                "loss_defsoldiers",
                "loss_deftanks",
                "loss_defaircraft",
                "loss_defships",
                "loss_definfra",
                "loss_defvalue",

                "att_gas_used",
                "att_mun_used",
                "def_gas_used",
                "def_mun_used"
        ));

        this.header = new ArrayList<>(row);

        writeHeader();
    }

    private void iterateSim(WarNation warNation1, WarNation warNation1Tmp, WarNation warNation2, WarNation warNation2Tmp) throws IOException {
        count++;

        warNation1Tmp.updateStats(warNation1);
        warNation2Tmp.updateStats(warNation2);

        SimulatedWarNode origin = new SimulatedWarNode(warNation1Tmp, warNation2Tmp, WarType.RAID, 60, true);
        Function<SimulatedWarNode, Double> valueFunction = node -> -node.warDistance(origin);
        Function<SimulatedWarNode, SimulatedWarNode.WarGoal> goal = new Function<SimulatedWarNode, SimulatedWarNode.WarGoal>() {
            @Override
            public SimulatedWarNode.WarGoal apply(SimulatedWarNode node) {
                if (node.getAggressor().getResistance() <= 0 || node.getDefender().getResistance() <= 0 || node.getTurnsLeft() <= 0) {
                    return SimulatedWarNode.WarGoal.SUCCESS;
                }
                return SimulatedWarNode.WarGoal.CONTINUE;
            }
        };

        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        SimulatedWarNode solution = origin.minimax(System.currentTimeMillis(), alpha, beta, true, valueFunction, goal, 1000);

        List<SimulatedWarNode> solutionList = solution.toActionList();
        StringBuilder result = new StringBuilder();
        int wait = 0;
        for (SimulatedWarNode node : solutionList) {
            if (node.getMethod() == WarNation.Actions.WAIT) {
                wait++;
                continue;
            }
            if (wait != 0) {
                wait = 0;
            }
            result.append('\n').append(node.getActionString());
        }
    }

    private void iterateAttack(AttackType type, WarNation warNation1, WarNation warNation1Tmp, WarNation warNation2, WarNation warNation2Tmp, boolean munitions, BiConsumer<WarNation, WarNation> attack) throws IOException {
        double gas1 = warNation1Tmp.getConsumption(ResourceType.GASOLINE);
        double muni1 = warNation1Tmp.getConsumption(ResourceType.MUNITIONS);

        double gas2 = warNation2Tmp.getConsumption(ResourceType.GASOLINE);
        double muni2 = warNation2Tmp.getConsumption(ResourceType.MUNITIONS);

        double losses1 = warNation1Tmp.getTotalLossCost(warNation1, true, true, true);
        double losses2 = warNation2Tmp.getTotalLossCost(warNation2, true, true, true);

        DBNation n1 = warNation1.getNation();
        DBNation n2 = warNation2.getNation();

        DBNation n1Tmp = warNation1Tmp.getNation();
        DBNation n2Tmp = warNation2Tmp.getNation();

        row.set(0, type.name());

        if (munitions) {
            row.set(1, n1.getSoldiers() + "");
            row.set(2, 0 + "");
        } else {
            row.set(1, 0 + "");
            row.set(2, n1.getSoldiers() + "");
        }

        row.set(3, n1.getTanks() + "");
        row.set(4, n1.getAircraft() + "");
        row.set(5, n1.getShips() + "");

        row.set(6, n2.getSoldiers() + "");
        row.set(7, n2.getTanks() + "");
        row.set(8, n2.getAircraft() + "");
        row.set(9, n2.getShips() + "");
        row.set(10, n2.getInfra() + "");

        row.set(11, (n1.getSoldiers() - n1Tmp.getSoldiers()) + "");
        row.set(12, (n1.getTanks() - n1Tmp.getTanks()) + "");
        row.set(13, (n1.getAircraft() - n1Tmp.getAircraft()) + "");
        row.set(14, (n1.getShips() - n1Tmp.getShips()) + "");
        row.set(15, losses1 + "");

        row.set(16, (n2.getSoldiers() - n2Tmp.getSoldiers()) + "");
        row.set(17, (n2.getTanks() - n2Tmp.getTanks()) + "");
        row.set(18, (n2.getAircraft() - n2Tmp.getAircraft()) + "");
        row.set(19, (n2.getShips() - n2Tmp.getShips()) + "");
        row.set(20, (n2.getInfra() - n2Tmp.getInfra()) + "");
        row.set(21, losses2 + "");

        row.set(22, gas1 + "");
        row.set(23, muni1 + "");
        row.set(24, gas2 + "");
        row.set(25, muni2 + "");

        double losses1NoC = warNation1Tmp.getTotalLossCost(warNation1, true, false, true);
        double losses2NoC = warNation2Tmp.getTotalLossCost(warNation2, true, false, true);
        System.out.println(StringMan.join(row, ","));
//
//        writeHeader();

    }

    private void writeHeader() throws IOException {
        for (int i = 0; i < row.size() - 1; i++) {
            out.write(row.get(i));
            out.write(',');
        }
        out.write(row.get(row.size() - 1));
        out.write('\n');
    }

    public void iterateMMR(DBNation nation1, DBNation nation2) throws IOException {
        long start = System.currentTimeMillis();

        nation1.setNation("nation1");
        nation2.setNation("nation2");
        nation1.setMissiles(0);
        nation1.setNukes(0);
        nation2.setMissiles(0);
        nation2.setNukes(0);
        nation1.setWarPolicy(WarPolicy.PIRATE);
        nation2.setWarPolicy(WarPolicy.PIRATE);

        int soldierMax = Buildings.BARRACKS.getUnitCap() * Buildings.BARRACKS.cap(Predicates.alwaysFalse()) * 100;
        int tankMax = Buildings.FACTORY.getUnitCap() * Buildings.FACTORY.cap(Predicates.alwaysFalse()) * 100;
        int airMax = Buildings.HANGAR.getUnitCap() * Buildings.HANGAR.cap(Predicates.alwaysFalse()) * 100;
        int shipMax = Buildings.DRYDOCK.getUnitCap() * Buildings.DRYDOCK.cap(Predicates.alwaysFalse()) * 100;

        nation2.setSoldiers(soldierMax);
        nation2.setTanks(tankMax);
        nation2.setAircraft(airMax);
        nation2.setShips(shipMax);
        nation2.setCities(1);

        nation1.setSoldiers(soldierMax);
        nation1.setTanks(tankMax);
        nation1.setAircraft(airMax);
        nation1.setShips(shipMax);
        nation1.setCities(1);

        WarNation warNation1 = new WarNation(nation1, false);
        WarNation warNation2 = new WarNation(nation2, false);
        warNation1.setActionPoints(12);
        warNation2.setActionPoints(12);
        warNation1.setResistance(100);
        warNation2.setResistance(100);

        warNation1.getNation().setMissiles(0);
        warNation1.getNation().setNukes(0);

        warNation2.getNation().setMissiles(0);
        warNation2.getNation().setNukes(0);

        WarNation warNation1Tmp = new WarNation(warNation1);
        WarNation warNation2Tmp = new WarNation(warNation2);

        double minRatio = 0.1;
        double maxRatio = 4;
        double stepSize = 0.2;

        int minInfra = 1000;
        int maxInfra = 1000;
        int stepInfra = 250;

        double factor = 4;

        outer:
        for (int infra = minInfra; infra <= maxInfra; infra += stepInfra) {
            warNation1.setAvg_infra(infra);

            for (double soldiers = minRatio; soldiers <= maxRatio; soldiers += Math.max(stepSize, soldiers / factor)) {
                warNation1.setSoldiers((int) (soldierMax * soldiers));

                for (double tanks = minRatio; tanks <= maxRatio; tanks += Math.max(stepSize, tanks / 8)) {
                    warNation1.setTanks((int) (tankMax * tanks));

                    for (double planes = minRatio; planes <= maxRatio; planes += Math.max(stepSize, planes / factor)) {
                        warNation1.setAircraft((int) (airMax * planes));

                        for (double ships = minRatio; ships <= maxRatio; ships += Math.max(stepSize, ships / factor)) {
                            warNation1.setShips((int) (shipMax * ships));


                            count++;
                            iterateSim(warNation1, warNation1Tmp, warNation2, warNation2Tmp);

                            if (count > 1000) break outer;
                        }
                    }
                }
            }
        }

        long diff = System.currentTimeMillis() - start;
        System.out.println("Total " + count + " | " + diff + "ms");
    }

    public void iterateMMR2(DBNation nation1, DBNation nation2) throws IOException {
        long start = System.currentTimeMillis();

        nation1.setNation("nation1");
        nation2.setNation("nation2");
        nation1.setMissiles(0);
        nation1.setNukes(0);
        nation2.setMissiles(0);
        nation2.setNukes(0);
        nation1.setWarPolicy(WarPolicy.PIRATE);
        nation2.setWarPolicy(WarPolicy.PIRATE);

        int soldierMax = Buildings.BARRACKS.getUnitCap() * Buildings.BARRACKS.cap(Predicates.alwaysFalse()) * 100;
        int tankMax = Buildings.FACTORY.getUnitCap() * Buildings.FACTORY.cap(Predicates.alwaysFalse()) * 100;
        int airMax = Buildings.HANGAR.getUnitCap() * Buildings.HANGAR.cap(Predicates.alwaysFalse()) * 100;
        int shipMax = Buildings.DRYDOCK.getUnitCap() * Buildings.DRYDOCK.cap(Predicates.alwaysFalse()) * 100;

        nation2.setSoldiers(soldierMax);
        nation2.setTanks(tankMax);
        nation2.setAircraft(airMax);
        nation2.setShips(shipMax);
        nation2.setCities(1);

        nation1.setSoldiers(soldierMax);
        nation1.setTanks(tankMax);
        nation1.setAircraft(airMax);
        nation1.setShips(shipMax);
        nation1.setCities(1);

        WarNation warNation1 = new WarNation(nation1, false);
        WarNation warNation2 = new WarNation(nation2, false);
        warNation1.setActionPoints(12);
        warNation2.setActionPoints(12);
        warNation1.setResistance(100);
        warNation2.setResistance(100);

        warNation1.getNation().setMissiles(0);
        warNation1.getNation().setNukes(0);

        warNation2.getNation().setMissiles(0);
        warNation2.getNation().setNukes(0);

        WarNation warNation1Tmp = new WarNation(warNation1);
        WarNation warNation2Tmp = new WarNation(warNation2);

        double minRatio = 0.1;
        double maxRatio = 8;
        double stepSize = 0.05;

        int minInfra = 1000;
        int maxInfra = 1000;
        int stepInfra = 250;

        double factor = 16;

        for (int infra = minInfra; infra <= maxInfra; infra += stepInfra) {
            warNation1.setAvg_infra(infra);
            warNation1.setAvg_infra(infra);

            for (double soldiers = minRatio; soldiers <= maxRatio; soldiers += Math.max(stepSize, soldiers / factor)) {
                warNation1.setSoldiers((int) (soldierMax * soldiers));
                warNation1.setTanks(0);

                iterateAttack(AttackType.GROUND, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                        false,
                        (a, b) -> a.groundAttack(b, a.getSoldiers(), 0, false, true));

                iterateAttack(AttackType.GROUND, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                        true,
                        (a, b) -> a.groundAttack(b, a.getSoldiers(), 0, true, true));

                for (double tanks = minRatio; tanks <= maxRatio; tanks += Math.max(stepSize, tanks / 8)) {
                    warNation1.setTanks((int) (tankMax * tanks));

                    iterateAttack(AttackType.GROUND, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                            false,
                            (a, b) -> a.groundAttack(b, a.getSoldiers(), a.getTanks(), false, true));

                    iterateAttack(AttackType.GROUND, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                            true,
                            (a, b) -> a.groundAttack(b, a.getSoldiers(), a.getTanks(), true, true));
                }
            }

            for (double planes = minRatio; planes <= maxRatio; planes += Math.max(stepSize, planes / factor)) {
                warNation1.setAircraft((int) (airMax * planes));

                iterateAttack(AttackType.AIRSTRIKE_AIRCRAFT, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                        true,
                        (a, b) -> a.airstrikeAir(b, a.getAircraft(), true));

                iterateAttack(AttackType.AIRSTRIKE_INFRA, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                        true,
                        (a, b) -> a.airstrikeInfra(b, a.getAircraft(), true));

                for (double ships = minRatio; ships <= maxRatio; ships += Math.max(stepSize, ships / factor)) {
                    warNation1.setShips((int) (shipMax * ships));

                    iterateAttack(AttackType.AIRSTRIKE_SHIP, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                            true,
                            (a, b) -> a.airstrikeShips(b, a.getAircraft(), true));
                }
                for (double soldiers = minRatio; soldiers <= maxRatio; soldiers += Math.max(stepSize, soldiers / factor)) {
                    warNation1.setSoldiers((int) (soldierMax * soldiers));

                    iterateAttack(AttackType.AIRSTRIKE_SOLDIER, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                            true,
                            (a, b) -> a.airstrikeSoldiers(b, a.getAircraft(), true));
                }
                for (double tanks = minRatio; tanks <= maxRatio; tanks += Math.max(stepSize, tanks / factor)) {
                    warNation1.setTanks((int) (tankMax * tanks));

                    iterateAttack(AttackType.AIRSTRIKE_TANK, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                            true,
                            (a, b) -> a.airstrikeTanks(b, a.getAircraft(), true));
                }
            }
            for (double ships = minRatio; ships <= maxRatio; ships += Math.max(stepSize, ships / factor)) {
                warNation1.setShips((int) (shipMax * ships));

                iterateAttack(AttackType.NAVAL, warNation1, warNation1Tmp, warNation2, warNation2Tmp,
                        true,
                        (a, b) -> a.naval(b, a.getShips(), true));
            }
        }

        long diff = System.currentTimeMillis() - start;
        System.out.println("Total " + count + " | " + diff + "ms");
    }

    public PrintWriter getOut() {
        return out;
    }
}
