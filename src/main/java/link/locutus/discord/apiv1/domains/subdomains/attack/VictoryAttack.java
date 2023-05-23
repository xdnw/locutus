package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;

public abstract class VictoryAttack extends AbstractAttack {
    public VictoryAttack(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    public static VictoryAttack create(int id, long date, boolean isAttackerIdGreater, double[] loot, double lootPct, double infra_destroyed_value) {
        boolean noLoot = (loot == null || ResourceType.isEmpty(loot));
        boolean noInfra = (infra_destroyed_value == 0);
        if (noLoot) {
            if (noInfra) {
                return new VictoryAttackNoLootNoInfra(id, date, isAttackerIdGreater);
            } else if (infra_destroyed_value < Integer.MAX_VALUE / 100d) {
                    return new VictoryAttackNoLootInfraInt(id, date, isAttackerIdGreater, (int) infra_destroyed_value);
            } else {
                return new VictoryAttackNoLootInfra(id, date, isAttackerIdGreater, infra_destroyed_value);
            }
        } else if (noInfra) {
            return new VictoryAttackLootNoInfra(id, date, isAttackerIdGreater, loot, lootPct);
        } else {
            return new VictoryAttackLootInfra(id, date, isAttackerIdGreater, loot, lootPct, infra_destroyed_value);
        }
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.VICTORY;
    }

    @Override
    public SuccessType getSuccess() {
        return SuccessType.UTTER_FAILURE;
    }

    @Override
    public int getAttcas1() {
        return 0;
    }

    @Override
    public int getAttcas2() {
        return 0;
    }

    @Override
    public int getDefcas1() {
        return 0;
    }

    @Override
    public int getDefcas2() {
        return 0;
    }

    @Override
    public int getDefcas3() {
        return 0;
    }

    @Override
    public double getCity_infra_before() {
        return 0;
    }

    @Override
    public double getInfra_destroyed() {
        return 0;
    }

    @Override
    public abstract double getInfra_destroyed_value();

    @Override
    public int getImprovements_destroyed() {
        return 0;
    }

    @Override
    public double getMoney_looted() {
        double[] loot = getLoot();
        return loot == null ? 0 : loot[0];
    }

    @Override
    public double getAtt_gas_used() {
        return 0;
    }

    @Override
    public double getAtt_mun_used() {
        return 0;
    }

    @Override
    public double getDef_gas_used() {
        return 0;
    }

    @Override
    public double getDef_mun_used() {
        return 0;
    }

    public static class VictoryAttackNoLootNoInfra extends VictoryAttack {
        public VictoryAttackNoLootNoInfra(int id, long date, boolean isAttackerIdGreater) {
            super(id, date, isAttackerIdGreater);
        }

        @Override
        public double[] getLoot() {
            return null;
        }

        @Override
        public double getLootPercent() {
            return 0;
        }

        @Override
        public double getInfra_destroyed_value() {
            return 0;
        }
    }

    public static class VictoryAttackLootNoInfra extends VictoryAttack {
        public ResourceType.IResourceArray loot;
        public char pct_cents;

        public VictoryAttackLootNoInfra(int id, long date, boolean isAttackerIdGreater, double[] loot, double percent) {
            super(id, date, isAttackerIdGreater);
            this.loot = ResourceType.IResourceArray.create(loot);
            this.pct_cents = (char) (percent * 100);
        }

        @Override
        public double[] getLoot() {
            return loot.get();
        }

        @Override
        public double getLootPercent() {
            return pct_cents / 100d;
        }

        @Override
        public double getInfra_destroyed_value() {
            return 0;
        }
    }

    public static class VictoryAttackLootInfra extends VictoryAttack {
        public ResourceType.IResourceArray loot;
        public long pair;

        public VictoryAttackLootInfra(int id, long date, boolean isAttackerIdGreater, double[] loot, double percent, double infra_destroyed) {
            super(id, date, isAttackerIdGreater);
            this.loot = ResourceType.IResourceArray.create(loot);
            char pct_cents = (char) (percent * 100);
            long infra_destroyed_cents = (long) (infra_destroyed * 100);
            this.pair = pct_cents + (infra_destroyed_cents << 16);
        }

        @Override
        public double[] getLoot() {
            return loot.get();
        }

        @Override
        public double getLootPercent() {
            return (this.pair & 0xFFFF) / 100d;
        }

        @Override
        public double getInfra_destroyed_value() {
            return (this.pair >>> 16) / 100d;
        }
    }

    public static class VictoryAttackNoLootInfraInt extends VictoryAttack {
        public int infra_destroyed_cents;

        public VictoryAttackNoLootInfraInt(int id, long date, boolean isAttackerIdGreater, double infra_destroyed) {
            super(id, date, isAttackerIdGreater);
            this.infra_destroyed_cents = (int) (infra_destroyed * 100d);
        }

        @Override
        public double[] getLoot() {
            return null;
        }

        @Override
        public double getLootPercent() {
            return 0;
        }

        @Override
        public double getInfra_destroyed_value() {
            return infra_destroyed_cents / 100d;
        }
    }

    public static class VictoryAttackNoLootInfra extends VictoryAttack {
        public long infra_destroyed_cents;

        public VictoryAttackNoLootInfra(int id, long date, boolean isAttackerIdGreater, double infra_destroyed) {
            super(id, date, isAttackerIdGreater);
            this.infra_destroyed_cents = (long) (infra_destroyed * 100d);
        }

        @Override
        public double[] getLoot() {
            return null;
        }

        @Override
        public double getLootPercent() {
            return 0;
        }

        @Override
        public double getInfra_destroyed_value() {
            return infra_destroyed_cents / 100d;
        }
    }
}
