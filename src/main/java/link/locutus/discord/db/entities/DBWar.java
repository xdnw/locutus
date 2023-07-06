package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.War;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SWarContainer;
import link.locutus.discord.apiv1.domains.subdomains.WarContainer;
import link.locutus.discord.apiv1.enums.WarType;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class DBWar {
    public int warId;
    public int attacker_id;
    public int defender_id;
    public int attacker_aa;
    public int defender_aa;
    public WarType warType;
    public WarStatus status;
    public long date;

    public NationInfo attacker_info;
    public NationInfo defender_info;

    public int getTurnsLeft() {
        return (int) (TimeUtil.getTurn() - TimeUtil.getTurn(date) + 60);
    }

    public static class NationInfo {
        public NationColor color;
        public int cities;
        public double score;
        public int position;
        public int infra;
        public int soldiers;
        public int tanks;
        public int aircraft;
        public int ships;
        public int missiles;
        public int nukes;
        public long lastLogin;
        public long projects;
        public Continent continent;
        public WarPolicy war_policy;
        public int tax_id;
    }

    public DBWar(int warId, int attacker_id, int defender_id, int attacker_aa, int defender_aa, WarType warType, WarStatus status, long date) {
        this.warId = warId;
        this.attacker_id = attacker_id;
        this.defender_id = defender_id;
        this.attacker_aa = attacker_aa;
        this.defender_aa = defender_aa;
        this.warType = warType;
        this.status = status;
        this.date = date;
    }

    public DBWar(War war) {
        this.warId = war.getId();
        this.attacker_id = war.getAtt_id();
        this.defender_id = war.getDef_id();
        this.attacker_aa = war.getAtt_alliance_id();
        this.defender_aa = war.getDef_alliance_id();
        this.warType = WarType.fromV3(war.getWar_type());
        if (war.getWinner_id() != null && war.getWinner_id() > 0) {
            if (war.getWinner_id() == attacker_id) {
                this.status = WarStatus.ATTACKER_VICTORY;
            } else {
                status = WarStatus.DEFENDER_VICTORY;
            }
        } else if (war.getAtt_peace() && war.getDef_peace()) {
            status = WarStatus.PEACE;
        } else if (TimeUtil.getTurn() - TimeUtil.getTurn(war.getDate().toEpochMilli()) >= 60) {
            status = WarStatus.EXPIRED;
        } else if (war.getAtt_peace()) {
            status = WarStatus.ATTACKER_OFFERED_PEACE;
        } else if (war.getDef_peace()) {
            status = WarStatus.DEFENDER_OFFERED_PEACE;
        } else {
            status = WarStatus.ACTIVE;
        }
        this.date = war.getDate().toEpochMilli();
    }

    public String getWarInfoEmbed(boolean isAttacker, boolean loot) {
        return getWarInfoEmbed(isAttacker, loot, true);
    }

    public String getWarInfoEmbed(boolean isAttacker, boolean loot, boolean title) {
        StringBuilder body = new StringBuilder();

        DBNation enemy = getNation(!isAttacker);
        if (enemy == null) return body.toString();
        WarCard card = new WarCard(this, false);

        if (title) {
            String typeStr = isAttacker ? "\uD83D\uDD2A" : "\uD83D\uDEE1";
            body.append(typeStr);
            body.append("`" + enemy.getNation() + "`")
                    .append(" | ").append(enemy.getAllianceName()).append(":");
        }
        if (loot && isAttacker) {
            double lootValue = enemy.lootTotal();
            body.append("$" + MathMan.format((int) lootValue));
        }
        body.append(enemy.toCityMilMarkedown());

        String attStr = card.condensedSubInfo(isAttacker);
        String defStr = card.condensedSubInfo(!isAttacker);
        body.append("```" + attStr + "|" + defStr + "``` ");
        body.append(StringMan.repeat("\u2501", 10) + "\n");
        return body.toString();
    }

    public List<DBAttack> getAttacks() {
        return Locutus.imp().getWarDb().getAttacksByWar(this);
    }

    public List<DBAttack> getAttacks(Collection<DBAttack> attacks) {
        List<DBAttack> result = new ArrayList<>();
        for (DBAttack attack : attacks) {
            if (attack.getWar_id() == warId) result.add(attack);
        }
        return result;
    }

    /**
     * Resistance
     * @param attacks
     * @return [attacker, defender]
     */
    public Map.Entry<Integer, Integer> getResistance(List<DBAttack> attacks) {
        int[] result = {100, 100};
        for (DBAttack attack : attacks) {
            if (attack.getSuccess() == 0) continue;
            int resI = attack.getAttacker_nation_id() == attacker_id ? 1 : 0;
            int damage;
            switch (attack.getAttack_type()) {
                default:continue;
                case FORTIFY:
//                    result[(resI + 1) % 2] = Math.min(result[(resI + 1) % 2] + 10, 100);
                    continue;
                case GROUND:
                    damage = 10;
                    break;
                case AIRSTRIKE_INFRA:
                case AIRSTRIKE_SOLDIER:
                case AIRSTRIKE_TANK:
                case AIRSTRIKE_MONEY:
                case AIRSTRIKE_SHIP:
                case AIRSTRIKE_AIRCRAFT:
                    damage = 12;
                    break;
                case NAVAL:
                    damage = 14;
                    break;
                case MISSILE:
                    damage = 24;
                    break;
                case NUKE:
                    damage = 31;
                    break;
            }
            damage -= (9 - attack.getSuccess() * 3);
            result[resI] = Math.max(0, result[resI] - damage);
        }
        return new AbstractMap.SimpleEntry<>(result[0], result[1]);
    }

    public Map.Entry<Integer, Integer> getMap(List<DBAttack> attacks) {
        DBNation attacker = Locutus.imp().getNationDB().getNation(attacker_id);
        DBNation defender = Locutus.imp().getNationDB().getNation(defender_id);

        if (attacker == null || defender == null) {
            return new AbstractMap.SimpleEntry<>(0, 0);
        }

        long turnStart = TimeUtil.getTurn(date);
        long turnEnd = TimeUtil.getTurn();
        long[] attTurnMap = {turnStart, 6};
        long[] defTurnMap = {turnStart, 6};
        switch (defender.getWarPolicy()) {
            case FORTRESS:
                attTurnMap[1]--;
                defTurnMap[1]--;
                break;
            case BLITZKRIEG:
                attTurnMap[1]++;
        }

        boolean selfAttack = false;
        boolean enemyAttack = false;

        for (DBAttack attack : attacks) {
            if (attack.getAttacker_nation_id() == attacker_id) {
                selfAttack = true;
            } else {
                enemyAttack = true;
            }
        }

        int wastedMap = 0;
        boolean fortified = false;
        boolean wasted = false;

        outer:
        for (DBAttack attack : attacks) {
            long[] turnMap;
            if (attack.getAttacker_nation_id() == attacker_id) {
                selfAttack = true;
                turnMap = attTurnMap;
            } else {
                enemyAttack = true;
                turnMap = defTurnMap;
            }
            long lastTurn = turnMap[0];
            long turn = TimeUtil.getTurn(attack.getDate());
            int mapUsed = 0;
            switch (attack.getAttack_type()) {
                case FORTIFY:
                    if (attack.getAttacker_nation_id() == attacker_id) {
                        fortified = true;
                    }
                case GROUND:
                    mapUsed = 3;
                    break;
                case AIRSTRIKE_INFRA:
                case AIRSTRIKE_SOLDIER:
                case AIRSTRIKE_TANK:
                case AIRSTRIKE_MONEY:
                case AIRSTRIKE_SHIP:
                case AIRSTRIKE_AIRCRAFT:
                case NAVAL:
                    mapUsed = 4;
                    break;
                case MISSILE:
                    mapUsed = 8;
                    break;
                case NUKE:

                    mapUsed = 12;
                    break;
                default:
                    break outer;
            }
            turnMap[1] += (turn - lastTurn);
            if (turnMap[1] > 12) {
                wastedMap += turnMap[1] - 12;
                turnMap[1] = 12;
            }
            turnMap[1] -= mapUsed;
            turnMap[0] = turn;
        }
        attTurnMap[1] = Math.min(12, attTurnMap[1] + turnEnd - attTurnMap[0]);
        defTurnMap[1] = Math.min(12, defTurnMap[1] + turnEnd - defTurnMap[0]);
        return new AbstractMap.SimpleEntry<>((int) attTurnMap[1], (int) defTurnMap[1]);
    }

    public boolean isActive() {
        switch (status) {
            case ACTIVE:
            case DEFENDER_OFFERED_PEACE:
            case ATTACKER_OFFERED_PEACE:
                return true;
            default:
            case DEFENDER_VICTORY:
            case ATTACKER_VICTORY:
            case PEACE:
            case EXPIRED:
                return false;
        }
    }

    public int getWarId() {
        return warId;
    }

    public int getAttacker_id() {
        return attacker_id;
    }

    public int getDefender_id() {
        return defender_id;
    }

    public int getAttacker_aa() {
        return attacker_aa;
    }

    public int getDefender_aa() {
        return defender_aa;
    }

    public WarType getWarType() {
        return warType;
    }

    public WarStatus getStatus() {
        return status;
    }

    public long getDate() {
        return date;
    }

    public DBWar(SWarContainer c) {
        this.warId = c.getWarID();
        this.attacker_id = c.getAttackerID();
        this.defender_id = c.getDefenderID();

        DBAlliance attacker_aa = Locutus.imp().getNationDB().getAllianceByName(c.getAttackerAA());
        this.attacker_aa = attacker_aa == null ? 0 : attacker_aa.getAlliance_id();
        DBAlliance defender_aa = Locutus.imp().getNationDB().getAllianceByName(c.getDefenderAA());
        this.defender_aa = defender_aa == null ? 0 : defender_aa.getAlliance_id();
        this.warType = WarType.parse(c.getWarType());
        this.status = WarStatus.parse(c.getStatus());
        this.date = TimeUtil.parseDate(TimeUtil.WAR_FORMAT, c.getDate());
    }

    public DBWar(DBWar other) {
        this.warId = other.warId;
        this.attacker_id = other.attacker_id;
        this.defender_id = other.defender_id;
        this.attacker_aa = other.attacker_aa;
        this.defender_aa = other.defender_aa;
        this.warType = other.warType;
        this.status = other.status;
        this.date = other.date;
    }

    public DBWar(int warId, WarContainer c) {
        this.warId = warId;
        this.status = WarStatus.ACTIVE;
        update(c);
    }

    public void update(WarContainer c) {
        this.attacker_id = Integer.parseInt(c.getAggressorId());
        this.defender_id = Integer.parseInt(c.getDefenderId());
        DBAlliance attacker_aa = Locutus.imp().getNationDB().getAllianceByName(c.getAggressorAllianceName());
        this.attacker_aa = attacker_aa == null ? 0 : attacker_aa.getAlliance_id();
        DBAlliance defender_aa = Locutus.imp().getNationDB().getAllianceByName(c.getDefenderAllianceName());
        this.defender_aa = defender_aa == null ? 0 : defender_aa.getAlliance_id();
        this.warType = WarType.parse(c.getWarType());
        this.date = TimeUtil.parseDate(TimeUtil.WAR_FORMAT, c.getDate());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DBWar dbWar = (DBWar) o;

        if (warId != dbWar.warId) return false;
        if (attacker_id != dbWar.attacker_id) return false;
        if (defender_id != dbWar.defender_id) return false;
        if (attacker_aa != dbWar.attacker_aa) return false;
        if (defender_aa != dbWar.defender_aa) return false;
        if (warType != dbWar.warType) return false;
        return status == dbWar.status;
    }

    public String toUrl() {
        return Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + warId;
    }

    @Override
    public String toString() {
        return "{" +
                "warId=" + warId +
                ", attacker_id=" + attacker_id +
                ", defender_id=" + defender_id +
                ", attacker_aa=" + attacker_aa +
                ", defender_aa=" + defender_aa +
                ", warType=" + warType +
                ", status=" + status +
                ", date=" + date +
                '}';
    }

    @Override
    public int hashCode() {
        return warId;
    }

    public Boolean isAttacker(DBNation nation) {
        if (nation.getNation_id() == attacker_id) return true;
        if (nation.getNation_id() == defender_id) return false;
        return null;
    }

    public DBNation getNation(Boolean attacker) {
        if (attacker == null) return null;
        return Locutus.imp().getNationDB().getNation(attacker ? attacker_id : defender_id);
    }

    public CounterStat getCounterStat() {
        return Locutus.imp().getWarDb().getCounterStat(this);
    }

    public WarAttackParser toParser(boolean primary) {
        return new WarAttackParser(this, primary);
    }

    public AttackCost toCost() {
        return toCost(getAttacks());
    }

    public AttackCost toCost(List<DBAttack> attacks) {
        String nameA = PnwUtil.getName(attacker_id, false);
        String nameB = PnwUtil.getName(defender_id, false);
        Function<DBAttack, Boolean> isPrimary = a -> a.getAttacker_nation_id() == attacker_id;
        Function<DBAttack, Boolean> isSecondary = b -> b.getAttacker_nation_id() == defender_id;
        AttackCost cost = new AttackCost(nameA, nameB);
        cost.addCost(attacks, isPrimary, isSecondary);
        return cost;
    }

    public String getNationHtmlUrl(boolean attacker) {
        int id = attacker ? attacker_id : defender_id;
        return MarkupUtil.htmlUrl(PnwUtil.getName(id, false), PnwUtil.getNationUrl(id));
    }

    public String getAllianceHtmlUrl(boolean attacker) {
        int id = attacker ? attacker_aa : defender_aa;
        return MarkupUtil.htmlUrl(PnwUtil.getName(id, true), PnwUtil.getAllianceUrl(id));
    }

    public int getNationId(int allianceId) {
        if (attacker_aa == allianceId) return attacker_id;
        if (defender_aa == allianceId) return defender_id;
        return 0;
    }
    public boolean isAttacker(int nation_id) {
        return this.attacker_id == nation_id;
    }

    public int getAllianceId(int attacker_nation_id) {
        return attacker_nation_id == this.attacker_id ? this.attacker_aa : (attacker_nation_id == this.defender_id ? this.defender_aa : 0);
    }

    public long possibleEndDate() {
        return TimeUtil.getTimeFromTurn(TimeUtil.getTurn(date) + 60);
    }

    public int getControl(Predicate<AttackType> attackType, MilitaryUnit... units) {
        long acDate = 0;
        int acNation = 0;
        for (DBAttack attack : getAttacks()) {
            if (attackType.test(attack.getAttack_type())) {
                switch (SuccessType.values[attack.getSuccess()]) {
                    case PYRRHIC_VICTORY, MODERATE_SUCCESS -> {
                        if (acNation != attack.getAttacker_nation_id()) {
                            acNation = 0;
                            acDate = 0;
                        }
                    }
                    case IMMENSE_TRIUMPH -> {
                        acNation = attack.getAttacker_nation_id();
                        acDate = attack.getDate();
                    }
                }
            }
        }
        if (acNation != 0) {
            for (DBAttack attack : Locutus.imp().getWarDb().getAttacks(acNation, acDate)) {
                if (attackType.test(attack.getAttack_type()) &&
                        SuccessType.values[attack.getSuccess()] == SuccessType.IMMENSE_TRIUMPH &&
                        attack.getDefender_nation_id() == acNation &&
                        attack.getDate() > acDate) {
                    return 0;
                }

            }
        }
        DBNation nation = DBNation.getById(acNation);
        if (nation != null) {
            boolean hasUnits = false;
            for (MilitaryUnit unit : units) {
                if (nation.getUnits(unit) > 0) {
                    hasUnits = true;
                    break;
                }
            }
            if (!hasUnits) return 0;
        }
        return acNation;
    }

    public int getGroundControl() {
        return getControl(f -> f == AttackType.GROUND, MilitaryUnit.SOLDIER, MilitaryUnit.TANK);
    }

    public int getBlockader() {
        return getControl(f -> f == AttackType.NAVAL, MilitaryUnit.SHIP);
    }

    public int getAirControl() {
        return getControl(f -> switch (f) {
            case AIRSTRIKE_INFRA -> true;
            case AIRSTRIKE_SOLDIER -> true;
            case AIRSTRIKE_TANK -> true;
            case AIRSTRIKE_MONEY -> true;
            case AIRSTRIKE_SHIP -> true;
            case AIRSTRIKE_AIRCRAFT -> true;
            default -> false;
        }, MilitaryUnit.AIRCRAFT);
    }
}