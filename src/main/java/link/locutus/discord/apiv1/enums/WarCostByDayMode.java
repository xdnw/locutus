package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.AttackCost;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public enum WarCostByDayMode {
    COST {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> ResourceType.convertedTotal(cost.getTotal(f));
        }
    },
    LOOT {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> -ResourceType.convertedTotal(cost.getLoot(f));
        }
    },
    BUILDING {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> (double) cost.getNumBuildingsDestroyed(f);
        }
    },
    CONSUMPTION {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> ResourceType.convertedTotal(cost.getConsumption(f));
        }
    },
    MUNITIONS {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> cost.getConsumption(f).getOrDefault(ResourceType.MUNITIONS, 0d);
        }
    },
    GASOLINE {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> cost.getConsumption(f).getOrDefault(ResourceType.GASOLINE, 0d);
        }
    },
    UNIT {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> ResourceType.convertedTotal(cost.getUnitCost(f));
        }
    },
    NUKE {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.NUKE, 0).doubleValue();
        }
    },
    MISSILE {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.MISSILE, 0).doubleValue();
        }
    },
    SHIP {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.SHIP, 0).doubleValue();
        }
    },
    AIRCRAFT {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.AIRCRAFT, 0).doubleValue();
        }
    },
    TANK {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.TANK, 0).doubleValue();
        }
    },
    SOLDIER {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> cost.getUnitsLost(f).getOrDefault(MilitaryUnit.SOLDIER, 0).doubleValue();
        }
    },
    INFRASTRUCTURE {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return cost::getInfraLost;
        }
    },
//    ATTACK_TYPE {
//        @Override
//        public Function<Boolean, Double> apply(AttackCost cost) {
//            return null;
//        }
//    };

    // GROUND
    GROUND {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.GROUND);
        }
    },
    // AIRSTRIKE_INFRA
    AIRSTRIKE_INFRA {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.AIRSTRIKE_INFRA);
        }
    },
    // AIRSTRIKE_SOLDIER
    AIRSTRIKE_SOLDIER {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.AIRSTRIKE_SOLDIER);
        }
    },
    // AIRSTRIKE_TANK
    AIRSTRIKE_TANK {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.AIRSTRIKE_TANK);
        }
    },
    // AIRSTRIKE_MONEY
    AIRSTRIKE_MONEY {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.AIRSTRIKE_MONEY);
        }
    },
    // AIRSTRIKE_SHIP
    AIRSTRIKE_SHIP {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.AIRSTRIKE_SHIP);
        }
    },
    // AIRSTRIKE_AIRCRAFT
    AIRSTRIKE_AIRCRAFT {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.AIRSTRIKE_AIRCRAFT);
        }
    },
    // NAVAL
    NAVAL {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.NAVAL);
        }
    },
    // MISSILE_SUCCESS
    MISSILE_SUCCESS {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.MISSILE, SuccessType.PYRRHIC_VICTORY);
        }
    },
    // NUKE_SUCCESS
    NUKE_SUCCESS {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return forAttackType(cost, AttackType.NUKE, SuccessType.PYRRHIC_VICTORY);
        }
    },

    IT_ATTACKS {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> {
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

    NUM_ATTACKS {
        @Override
        public Function<Boolean, Double> apply(AttackCost cost) {
            return f -> (double) cost.getAttacks(f).size();
        }
    },
    ;

    public abstract BiFunction<AttackCost, Boolean, Double> apply();

    public static Function<Boolean, Double> forAttackType(AttackCost cost, AttackType type, SuccessType requiredSuccess) {
        return f -> {
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

    public static Function<Boolean, Double> forAttackType(AttackCost cost, AttackType type) {
        return f -> {
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
}
