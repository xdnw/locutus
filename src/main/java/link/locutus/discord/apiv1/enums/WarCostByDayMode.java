package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.WarParser;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public enum WarCostByDayMode {
    COST {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> ResourceType.convertedTotal(cost.getTotal(f));
        }
    },
    LOOT {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> -ResourceType.convertedTotal(cost.getLoot(f));
        }
    },
    BUILDING {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> (double) cost.getNumBuildingsDestroyed(f);
        }
    },
    CONSUMPTION {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> ResourceType.convertedTotal(cost.getConsumption(f));
        }
    },
    MUNITIONS {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getConsumption(f).getOrDefault(ResourceType.MUNITIONS, 0d);
        }
    },
    GASOLINE {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getConsumption(f).getOrDefault(ResourceType.GASOLINE, 0d);
        }
    },
    UNIT {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> ResourceType.convertedTotal(cost.getUnitCost(f));
        }
    },
    NUKE {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.NUKE, 0).doubleValue();
        }
    },
    MISSILE {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.MISSILE, 0).doubleValue();
        }
    },
    SHIP {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.SHIP, 0).doubleValue();
        }
    },
    AIRCRAFT {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.AIRCRAFT, 0).doubleValue();
        }
    },
    TANK {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.TANK, 0).doubleValue();
        }
    },
    SOLDIER {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.SOLDIER, 0).doubleValue();
        }
    },
    INFRASTRUCTURE {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> cost.getInfraLost(f);
        }
    },
    GROUND(true)  {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.GROUND);
        }
    },
    AIRSTRIKE_INFRA(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.AIRSTRIKE_INFRA);
        }
    },
    AIRSTRIKE_SOLDIER(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.AIRSTRIKE_SOLDIER);
        }
    },
    AIRSTRIKE_TANK(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.AIRSTRIKE_TANK);
        }
    },
    AIRSTRIKE_MONEY(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.AIRSTRIKE_MONEY);
        }
    },
    AIRSTRIKE_SHIP(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.AIRSTRIKE_SHIP);
        }
    },
    AIRSTRIKE_AIRCRAFT(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.AIRSTRIKE_AIRCRAFT);
        }
    },
    AIRSTRIKE(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> {
                Set<AbstractCursor> attacks = cost.getAttacks(f);
                int count = 0;
                for (AbstractCursor a : attacks) {
                    MilitaryUnit[] units = a.getAttack_type().getUnits();
                    if (units != null && units[0] == MilitaryUnit.AIRCRAFT) {
                        count++;
                    }
                }
                return (double) count;
            };
        }
    },
    NAVAL_SHIP(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.NAVAL);
        }
    },
    NAVAL_AIR(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.NAVAL_AIR);
        }
    },
    NAVAL_GROUND(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.NAVAL_GROUND);
        }
    },
    NAVAL_INFRA(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.NAVAL_INFRA);
        }
    },
    MISSILE_SUCCESS(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.MISSILE, SuccessType.PYRRHIC_VICTORY);
        }
    },
    NUKE_SUCCESS(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return forAttackType(AttackType.NUKE, SuccessType.PYRRHIC_VICTORY);
        }
    },
    IT_ATTACKS(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> {
                Set<AbstractCursor> attacks = cost.getAttacks(f);
                int count = 0;
                for (AbstractCursor a : attacks) {
                    if (a.getSuccess() == SuccessType.IMMENSE_TRIUMPH) {
                        count++;
                    }
                }
                return (double) count;
            };
        }
    },
    NUM_ATTACKS(true) {
        @Override
        public BiFunction<AttackCost, Boolean, Double> apply() {
            return (cost, f) -> (double) cost.getAttacks(f).size();
        }
    };
    ;

    public final boolean isAttackType;

    WarCostByDayMode() {
        this.isAttackType = false;
    }

    WarCostByDayMode(boolean isAttackType) {
        this.isAttackType = isAttackType;
    }

    public abstract BiFunction<AttackCost, Boolean, Double> apply();

    public static BiFunction<AttackCost, Boolean, Double> forAttackType(AttackType type, SuccessType requiredSuccess) {
        return (cost, f) -> {
            Set<AbstractCursor> attacks = cost.getAttacks(f);
            int count = 0;
            for (AbstractCursor a : attacks) {
                if (a.getAttack_type() == type && a.getSuccess().ordinal() >= requiredSuccess.ordinal()) {
                    count++;
                }
            }
            return (double) count;
        };
    }

    public static BiFunction<AttackCost, Boolean, Double> forAttackType(AttackType type) {
        return (cost, f) -> {
            Set<AbstractCursor> attacks = cost.getAttacks(f);
            int count = 0;
            for (AbstractCursor a : attacks) {
                if (a.getAttack_type() == type) {
                    count++;
                }
            }
            return (double) count;
        };
    }

    public static void addRanking(TimeNumericTable<Map<String, WarParser>> table, long dayRelative, long dayOffset, Map<String, WarParser> parserMap, Map<String, Map<Long, AttackCost>> byDayMap, Function<AttackCost, Number> calc) {
        Comparable[] values = new Comparable[parserMap.size() + 1];
        values[0] = dayRelative;

        int i = 1;
        for (Map.Entry<String, WarParser> entry : parserMap.entrySet()) {
            String name = entry.getKey();
            WarParser parser = entry.getValue();
            Map<Long, AttackCost> costByDay = byDayMap.get(name);

            AttackCost cost = costByDay.getOrDefault(dayRelative + dayOffset, null);
            double val = 0;
            if (cost != null) {
                val = calc.apply(cost).doubleValue();
            }
            values[i++] = val;

        }
        table.getData().add(values);
    }
}
