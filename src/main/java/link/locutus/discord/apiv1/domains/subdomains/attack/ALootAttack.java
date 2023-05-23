package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;

public abstract class ALootAttack extends AbstractAttack {
    protected ALootAttack(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    public static ALootAttack create(int id, long date, boolean isAttackerIdGreater, double[] loot, double lootPct) {
        boolean noLoot = (loot == null || ResourceType.isEmpty(loot));
        if (noLoot) {
            return new ALootAttack.ALootAttackNoLootNoInfra(id, date, isAttackerIdGreater);
        } else {
            return new ALootAttack.ALootAttackLootNoInfra(id, date, isAttackerIdGreater, loot, lootPct);
        }
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.A_LOOT;
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
    public final double getInfra_destroyed_value() {
        return 0;
    }

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

    public static class ALootAttackNoLootNoInfra extends ALootAttack {

        protected ALootAttackNoLootNoInfra(int id, long date, boolean isAttackerIdGreater) {
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
    }

    public static class ALootAttackLootNoInfra extends ALootAttack {
        public ResourceType.IResourceArray loot;
        public char pct_cents;

        public ALootAttackLootNoInfra(int id, long date, boolean isAttackerIdGreater, double[] loot, double percent) {
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
    }
}
