package link.locutus.discord.apiv1.domains.subdomains.attack;

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.DBMainV2;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingFunction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractAttack implements IAttack {
    private final long date_id_isattacker;
    protected AbstractAttack(int id, long date, boolean isAttackerIdGreater) {
        long dateRelative = (date - TimeUtil.getOrigin());
        this.date_id_isattacker = (long) (dateRelative) << 26 | (long) id << 1 | (isAttackerIdGreater ? 1 : 0);
    }

    @Override
    public int getWar_attack_id() {
        return (int) ((date_id_isattacker & 0x1FFFFFFL) >> 1);
    }

    @Override
    public long getDate() {
        return (((date_id_isattacker >> 26) & 0x3FFFFFFFFFL)) + TimeUtil.getOrigin();
    }

    @Override
    public boolean isAttackerIdGreater() {
        return (date_id_isattacker & 1) == 1;
    }

    private static ThrowingFunction<ResultSet, SuccessType> getSuccess = (rs) -> SuccessType.values[rs.getInt(8)];
    private static ThrowingFunction<ResultSet, Double> getInfraDestroyed = (rs) -> DBMainV2.getLongDef0(rs, 15) * 0.01; // cents
    private static ThrowingFunction<ResultSet, Integer> getImprovementsDestroyed = (rs) -> DBMainV2.getIntDef0(rs, 16);
    private static ThrowingFunction<ResultSet, Double> getCityInfraBefore = (rs) -> DBMainV2.getLongDef0(rs, 21) * 0.01; // cents
    private static ThrowingFunction<ResultSet, Double> getInfraDestroyedValue = (rs) -> DBMainV2.getLongDef0(rs, 22) * 0.01; // cents
    private static ThrowingFunction<ResultSet, Double> getMoney_looted = (rs) -> DBMainV2.getLongDef0(rs, 17) * 0.01; // cents
    private static ThrowingFunction<ResultSet, double[]> getLootBytes = (rs) -> {
        byte[] bytes = DBMainV2.getBytes(rs, 19);
        if (bytes == null) return null;
        return ArrayUtil.toDoubleArray(bytes);
    };
    private static ThrowingFunction<ResultSet, Double> getLootPercent = (rs) -> rs.getInt(20) * 0.0001; // times 0.0001
    // getAtt_gas_used
    private static ThrowingFunction<ResultSet, Double> getAtt_gas_used = (rs) -> DBMainV2.getLongDef0(rs, 23) * 0.01; // cents
    // getAtt_mun_used
    private static ThrowingFunction<ResultSet, Double> getAtt_mun_used = (rs) -> DBMainV2.getLongDef0(rs, 24) * 0.01; // cents
    // getDef_gas_used
    private static ThrowingFunction<ResultSet, Double> getDef_gas_used = (rs) -> DBMainV2.getLongDef0(rs, 25) * 0.01; // cents
    // getDef_mun_used
    private static ThrowingFunction<ResultSet, Double> getDef_mun_used = (rs) -> DBMainV2.getLongDef0(rs, 26) * 0.01; // cents
    // getAttcas1
    private static ThrowingFunction<ResultSet, Integer> getAttcas1 = (rs) -> rs.getInt( 9);
    // getAttcas2
    private static ThrowingFunction<ResultSet, Integer> getAttcas2 = (rs) -> rs.getInt(10);
    // getDefcas1
    private static ThrowingFunction<ResultSet, Integer> getDefcas1 = (rs) -> rs.getInt(11);
    // getDefcas2
    private static ThrowingFunction<ResultSet, Integer> getDefcas2 = (rs) -> rs.getInt(12);
    // getDefcas3
    private static ThrowingFunction<ResultSet, Integer> getDefcas3 = (rs) -> rs.getInt(13);

    public static void createSingle(ResultSet rs, Consumer<Integer> warIdOut, Consumer<AbstractAttack> attackOut) throws SQLException {
        int war_attack_id = rs.getInt(1);
        long date = rs.getLong(2);
        int war_id = rs.getInt(3);
        int attacker_nation_id = rs.getInt(4);
        int defender_nation_id = rs.getInt(5);
        AttackType type = AttackType.values[rs.getInt(6)];
        AbstractAttack attack = createGeneral(
                war_attack_id,
                date,
                attacker_nation_id > defender_nation_id,
                type,
                () -> getSuccess.apply(rs),
                () -> getAttcas1.apply(rs),
                () -> getAttcas2.apply(rs),
                () -> getDefcas1.apply(rs),
                () -> getDefcas2.apply(rs),
                () -> getDefcas3.apply(rs),
                () -> getInfraDestroyed.apply(rs),
                () -> getImprovementsDestroyed.apply(rs),
                () -> getMoney_looted.apply(rs),
                () -> getLootBytes.apply(rs),
                () -> getLootPercent.apply(rs),
                () -> getCityInfraBefore.apply(rs),
                () -> getInfraDestroyedValue.apply(rs),
                () -> getAtt_gas_used.apply(rs),
                () -> getAtt_mun_used.apply(rs),
                () -> getDef_gas_used.apply(rs),
                () -> getDef_mun_used.apply(rs)
        );
        warIdOut.accept(war_id);
        attackOut.accept(attack);
    }

    public static void createAll(ResultSet rs, Map<Integer, Object> attacksByWar) throws SQLException {
        ObjectList<AbstractAttack> allAttacks = new ObjectArrayList<>();
        IntList attackWarIds = new IntArrayList();
        Int2ByteOpenHashMap attacksPerWar = new Int2ByteOpenHashMap();
        while (rs.next()) {
            createSingle(rs, (warId) -> {
                attackWarIds.add(warId.intValue());
                attacksPerWar.addTo(warId, (byte) 1);
            }, allAttacks::add);
        }

        for (int i = 0; i < allAttacks.size(); i++) {
            AbstractAttack attack = allAttacks.get(i);
            int war_id = attackWarIds.getInt(i);
            int attacks = attacksPerWar.get(war_id);
            Object existing = attacksByWar.get(war_id);
            if (existing == null && attacks == 1) {
                attacksByWar.put(war_id, attack);
            } else {
                AbstractAttack[] existingAttacks = (AbstractAttack[]) existing;
                // set length - attacks to attack
                existingAttacks[existingAttacks.length - attacks] = attack;
                // decriment attacksPerWar
                attacksPerWar.addTo(war_id, (byte) -1);
            }
        }
    }

    public static AbstractAttack createGeneral(int war_attack_id,
                                               long date,
                                               boolean isAttackerIdGreater,
                                               AttackType attack_type,
                                               Supplier<SuccessType> success,
                                               Supplier<Integer> attcas1,
                                               Supplier<Integer> attcas2,
                                               Supplier<Integer> defcas1,
                                               Supplier<Integer> defcas2,
                                               Supplier<Integer> defcas3,
                                               Supplier<Double> infra_destroyed,
                                               Supplier<Integer> improvements_destroyed,
                                               Supplier<Double> money_looted,
                                               Supplier<double[]> loot,
                                               Supplier<Double> lootPct,
                                               Supplier<Double> city_infra_before,
                                               Supplier<Double> infra_destroyed_value,
                                               Supplier<Double> att_gas_used,
                                               Supplier<Double> att_mun_used,
                                               Supplier<Double> def_gas_used,
                                               Supplier<Double> def_mun_used) {
        switch (attack_type) {
            case GROUND -> {
                double att_mun = att_mun_used.get();
                double def_mun = def_mun_used.get();
                return GroundAttack.create(war_attack_id, date, isAttackerIdGreater,
                        success.get(), attcas1.get(), attcas2, defcas1.get(), defcas2.get(), defcas3,
                        city_infra_before, infra_destroyed, improvements_destroyed,
                        money_looted, att_mun == 0 ? 0 : att_gas_used.get(), att_mun, def_mun == 0 ? 0 : def_gas_used.get(), def_mun);
            }
            case VICTORY -> {
                double[] lootArr = loot.get();
                double pct; pct = lootArr == null ? 0 : lootPct.get();
                return VictoryAttack.create(war_attack_id, date, isAttackerIdGreater,
                        lootArr, pct, infra_destroyed_value.get());
            }
            case A_LOOT -> {
                double[] lootArr = loot.get();
                double pct; pct = lootArr == null ? 0 : lootPct.get();
                return ALootAttack.create(war_attack_id, date, isAttackerIdGreater,
                        lootArr, pct);
            }
            case FORTIFY -> {
                return new FortifyAttack(war_attack_id, date, isAttackerIdGreater);
            }
            case MISSILE -> {
                return MissileAttack.create(war_attack_id, date, isAttackerIdGreater, success.get(), improvements_destroyed, city_infra_before, infra_destroyed);
            }
            case NUKE -> {
                return NukeAttack.create(war_attack_id, date, isAttackerIdGreater, success.get(), improvements_destroyed, city_infra_before, infra_destroyed);
            }
            case AIRSTRIKE_INFRA -> {
                return AirstrikeInfra.create(war_attack_id, date, isAttackerIdGreater, success.get(),
                        attcas1,
                        defcas1.get(),
                        city_infra_before,
                        infra_destroyed,
                        improvements_destroyed,
                        att_gas_used.get(),
                        att_mun_used.get(),
                        def_gas_used.get(),
                        def_mun_used);
            }
            case AIRSTRIKE_SOLDIER,AIRSTRIKE_TANK,AIRSTRIKE_MONEY,AIRSTRIKE_SHIP -> {
                return AirstrikeUnit.create(war_attack_id,
                        date,
                        isAttackerIdGreater,
                        attack_type,
                        success.get(),
                        attcas1,
                        defcas1.get(),
                        defcas2,
                        city_infra_before,
                        infra_destroyed,
                        improvements_destroyed,
                        att_gas_used.get(),
                        att_mun_used.get(),
                        def_gas_used.get(),
                        def_mun_used);
            }
            case AIRSTRIKE_AIRCRAFT -> {
                return AirstrikeAircraft.create(war_attack_id, date, isAttackerIdGreater, success.get(),
                        attcas1,
                        defcas1.get(),
                        city_infra_before,
                        infra_destroyed,
                        improvements_destroyed,
                        att_gas_used.get(),
                        att_mun_used.get(),
                        def_gas_used.get(),
                        def_mun_used);
            }
            case NAVAL -> {
                return NavalAttack.create(war_attack_id, date, isAttackerIdGreater, success.get(),
                        attcas1,
                        att_gas_used.get(),
                        att_mun_used.get(),
                        defcas1.get(),
                        def_gas_used.get(),
                        def_mun_used,
                        city_infra_before,
                        infra_destroyed,
                        improvements_destroyed);
            }
            case PEACE -> {
                return new PeaceAttack(war_attack_id, date, isAttackerIdGreater);
            }
            default -> {
                throw new IllegalStateException("Unexpected attack_type: " + attack_type);
            }
        }
    }
}
