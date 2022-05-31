package com.boydti.discord.util.task.war;

import com.boydti.discord.Locutus;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.entities.CounterStat;
import com.boydti.discord.db.entities.DBWar;
import com.boydti.discord.db.entities.WarStatus;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.battle.WarNation;
import com.boydti.discord.apiv1.domains.subdomains.DBAttack;
import com.boydti.discord.apiv1.domains.subdomains.WarContainer;
import com.boydti.discord.apiv1.enums.MilitaryUnit;
import com.boydti.discord.apiv1.enums.Rank;
import com.boydti.discord.apiv1.enums.WarPolicy;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class WarCard {
    private final int warId;
    public CounterStat counterStat;
    public int airSuperiority;
    public int groundControl;
    public int blockaded;
    public boolean attackerFortified;
    public boolean defenderFortified;
    public int attackerResistance;
    public int defenderResistance;
    public int attackerMAP;
    public int defenderMAP;

    private DBWar war;
    private String warReason;

    public WarCard(DBWar war, boolean checkCounters) {
        this(war, checkCounters, false);
    }

    public WarCard(DBWar war, List<DBAttack> attacks, boolean onlyCheckBlockade) {
        this.war = war;
        this.warId = war.warId;
        update(attacks, onlyCheckBlockade);
    }

    public WarCard(DBWar war, List<DBAttack> attacks, boolean checkGC, boolean checkAC, boolean checkBlockade) {
        this.war = war;
        this.warId = war.warId;
        update(attacks, checkGC, checkAC, checkBlockade);
    }

    public WarCard(DBWar war, boolean checkCounters, boolean onlyCheckBlockade) {
        this.warId = war.warId;
        update(war, checkCounters, onlyCheckBlockade);
    }

    public WarNation toWarNation(boolean attacker) {
        return toWarNation(attacker, id -> {
            DBNation nation = Locutus.imp().getNationDB().getNation(id);
            return nation == null ? null : new DBNation(nation);
        });
    }

    public WarNation toWarNation(boolean attacker, Function<Integer, DBNation> provideNation) {
        int nationId = attacker ? war.attacker_id : war.defender_id;
        int otherId = attacker ? war.defender_id : war.attacker_id;
        DBNation nation = provideNation.apply(nationId);
        DBNation other = provideNation.apply(otherId);

        WarNation wn = new WarNation(nation, false);

        if (nation.getWarPolicy() == WarPolicy.PIRATE) {
            wn.setLootFactor(wn.getLootFactor() * 1.4);
        }
        if (other.getWarPolicy() == WarPolicy.MONEYBAGS) {
            wn.setLootFactor(wn.getLootFactor() / 1.4);
        }
        if (groundControl == nationId) {
            wn.setGroundControl(true);
        }
        if (airSuperiority == nationId) {
            wn.setAirControl(true);
        }
        if (blockaded == nationId) {
            wn.setBlockade(true);
        }
        wn.setFortified(attacker ? attackerFortified : defenderFortified);
        wn.setResistance(attacker ? attackerResistance : defenderResistance);
        wn.setActionPoints(attacker ? attackerMAP : defenderMAP);
        switch (war.warType) {
            case RAID:
                if (attacker) {
                    wn.setInfraFactor(0.25);
                    wn.setLootFactor(1);
                } else {
                    wn.setInfraFactor(0.5);
                    wn.setLootFactor(1);
                }
                break;
            case ORD:
                if (attacker) {
                    wn.setInfraFactor(0.5);
                    wn.setLootFactor(0.5);
                } else {
                    wn.setInfraFactor(0.5);
                    wn.setLootFactor(0.5);
                }
                break;
            case ATT:
                if (attacker) {
                    wn.setInfraFactor(1);
                    wn.setLootFactor(0.25);
                } else {
                    wn.setInfraFactor(1);
                    wn.setLootFactor(0.5);
                }
                break;
        }
        return wn;
    }

    public WarCard(int warId) {
        this.warId = warId;
        this.war = Locutus.imp().getWarDb().getWar(warId);
        update(war);
    }

    public String condensedSubInfo(boolean attacker) {
        StringBuilder attStr = new StringBuilder();
        int nation_id = attacker ? this.war.attacker_id : this.war.defender_id;
        if (blockaded == nation_id) attStr.append("\u26F5");
        if (airSuperiority == nation_id) attStr.append("\u2708");
        if (groundControl == nation_id) attStr.append("\uD83D\uDC82");
        if (!attacker ? defenderFortified : attackerFortified) attStr.append("\uD83C\uDFF0");
        if (war.status == (!attacker ? WarStatus.DEFENDER_OFFERED_PEACE : WarStatus.ATTACKER_OFFERED_PEACE)) {
            attStr.append("\u2764");
        }
        attStr.append((attacker ? attackerMAP : defenderMAP) + "/12,");
        attStr.append((attacker ? attackerResistance : defenderResistance) + "%");
        return attStr.toString();
    }
////        WarContainer pnwWar = Locutus.imp().getPnwApi().getWar(warId).getWar().get(0);
//        if (war == null) {
//            war = new DBWar(warId, pnwWar);
//        } else {
//            war.update(pnwWar);
//        }
//
//        List<DBAttack> attacks = Locutus.imp().getWarDB().getAttacksByWarId(war.warId);
//        Map<MilitaryUnit, Integer> attUnitLoss = new HashMap<>();
//        Map<MilitaryUnit, Integer> defUnitLoss = new HashMap<>();
//        Map<ResourceType, Double> attRssLoss = new HashMap<>();
//        Map<ResourceType, Double> defRssLoss = new HashMap<>();
//        double attLossConverted = 0;
//        double defLossConverted = 0;
//        double attInfraLoss = 0;
//        double defInfraLoss = 0;
//
//        for (DBAttack attack : attacks) {
//            attUnitLoss = PnwUtil.add(attack.getUnitLosses(true), attUnitLoss);
//            defUnitLoss = PnwUtil.add(attack.getUnitLosses(false), defUnitLoss);
//            PnwUtil.addResourcesToA(attRssLoss, attack.getLosses(true));
//            PnwUtil.addResourcesToA(defRssLoss, attack.getLosses(false));
//            if (attack.infra_destroyed != null) {
//                if (attack.victor == attack.defender_nation_id) {
////                    attLossConverted += attack.infra_destroyed_value;
//                    attInfraLoss += attack.infra_destroyed;
//                } else {
////                    defLossConverted += attack.infra_destroyed_value;
//                    defInfraLoss += attack.infra_destroyed;
//                }
//            }
//        }
//        attLossConverted += PnwUtil.convertedTotal(attRssLoss);
//        defLossConverted += PnwUtil.convertedTotal(defRssLoss);
//
//        DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
//        DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
//
//        String title = String.format("%s > %s - %s - %s",
//                attacker.getNation(),
////                attacker.getUrl(),
//                defender.getNation(),
////                defender.getUrl(),
//                war.warType,
////                warUrl,
//                war.status
//        );
//
//        StringBuilder description = new StringBuilder();
//
//        String warUrl = "" + Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + warId;
//        description.append("Link: [\"" + pnwWar.getWarReason() + "\"\n](" + warUrl + ")");
//
//        description.append(formatNation(attacker, war, pnwWar));
//        description.append(formatNation(defender, war, pnwWar));
//
//        description.append(pnwWar.getTurnsLeft() + "/60 Turns left");
//
//        description.append("\n\n");
//
//        description.append("Press " + cmdEmoji + " to refresh\n");
//        description.append("Press " + simEmoji + " to simulate\n");
//        description.append("Press " + counterEmoji + " to find counters\n");
//        description.append("Press " + spyEmoji + " to find spyops\n");
//
//        this.title = title;
//        this.description = description.toString();
//    }

    public int turnsLeft() {
        long turnStart = TimeUtil.getTurn(war.date);
        long turnNow = TimeUtil.getTurn();
        return (int) Math.max(0, (60 - (turnNow - turnStart)));
    }

    public void update(DBWar war) {
        update(war, true, false);
    }

    public void update(DBWar war, boolean checkCounters, boolean onlyCheckBlockade) {
        this.war = war;
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacksByWarId(warId);
        update(attacks, onlyCheckBlockade);
        if (checkCounters) updateCounterStats();
    }

    public String getTitle() {
        String title = String.format("%s > %s - %s - %s",
                PnwUtil.getName(war.attacker_id, false),
                PnwUtil.getName(war.defender_id, false),
                war.warType,
                war.status
        );
        return title;
    }

    public String getDescription() {
        return getDescription(false);
    }

    public String getDescription(boolean addReactions) {
        StringBuilder description = new StringBuilder();

        if (counterStat != null) {
            switch (counterStat.type) {
                case UNCONTESTED:
                    break;
                case GETS_COUNTERED:
                    description.append("**This has been countered**\n");
                    break;
                case IS_COUNTER:
                    description.append("**This is a counter**\n");
                    break;
                case ESCALATION:
                    description.append("**This is an escalation**\n");
                    break;
            }
        }

        String warUrl = "" + Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + warId;
        String warReason = this.warReason == null ? "Click here" : this.warReason;
        description.append("Link: [\"" + warReason + "\"\n](" + warUrl + ")");

        description.append(formatNation(true));
        description.append(formatNation(false));

        long turnStart = TimeUtil.getTurn(war.date);
        long turnNow = TimeUtil.getTurn();
        description.append(60 - (turnNow - turnStart) + "/60 Turns left");

        if (addReactions) {
            description.append("\n\n");
            description.append("Press " + cmdEmoji + " to refresh\n");
            description.append("Press " + simEmoji + " to simulate\n");
            description.append("Press " + counterEmoji + " to find counters\n");
            description.append("Press " + spyEmoji + " to find spyops\n");
        }
        return description.toString();
    }

    public void update(List<DBAttack> attacks, boolean onlyCheckBlockade) {
        if (onlyCheckBlockade) update(attacks, false, false, true);
        else  update(attacks, true, true, true);
    }

    public void update(List<DBAttack> attacks, boolean checkGC, boolean checkAC, boolean checkBlockade) {
        Map.Entry<Integer, Integer> res = this.war.getResistance(attacks);
        this.attackerResistance = res.getKey();
        this.defenderResistance = res.getValue();

        Map.Entry<Integer, Integer> map = this.war.getMap(attacks);
        this.attackerMAP = map.getKey();
        this.defenderMAP = map.getValue();

        long gcDate = Long.MAX_VALUE;
        long acDate = Long.MAX_VALUE;
        long blockadeDate = Long.MAX_VALUE;

        boolean isActive = war.isActive();
//
        for (DBAttack attack : attacks) {
            if (attack.attacker_nation_id == war.attacker_id) attackerFortified = false; else defenderFortified = false;
            switch (attack.attack_type) {
                case FORTIFY:
                    if (attack.attacker_nation_id == war.attacker_id) attackerFortified = true;
                    else defenderFortified = true;
                    break;
                case GROUND:
                    switch (attack.success) {
                        case 3:
                            gcDate = attack.epoch;
                            groundControl = attack.attacker_nation_id;
                        case 2:
                        case 1:
                            if (groundControl != attack.attacker_nation_id) groundControl = 0;
                    }
                    break;
                case AIRSTRIKE1:
                case AIRSTRIKE2:
                case AIRSTRIKE3:
                case AIRSTRIKE4:
                case AIRSTRIKE5:
                case AIRSTRIKE6:
                    switch (attack.success) {
                        case 3:
                            acDate = attack.epoch;
                            airSuperiority = attack.attacker_nation_id;
                        case 2:
                        case 1:
                            if (airSuperiority != attack.attacker_nation_id) airSuperiority = 0;
                    }
                    break;
                case NAVAL:
                    switch (attack.success) {
                        case 3:
                            blockadeDate = attack.epoch;
                            blockaded = attack.defender_nation_id;
                        case 2:
                        case 1:
                            if (blockaded != attack.defender_nation_id) blockaded = 0;
                    }
                    break;
                case VICTORY:
                    isActive = false;
                    break;
            }
        }

        if (isActive) {
            if (checkGC && gcDate != Long.MAX_VALUE) {
                attacks = Locutus.imp().getWarDb().getAttacks(groundControl, gcDate);
                attacks.removeIf(a -> a.defender_nation_id != groundControl || a.success != 3);
                if (!attacks.isEmpty()
                        || (Locutus.imp().getNationDB().getMinMilitary(groundControl, MilitaryUnit.SOLDIER, gcDate) == 0
                        && Locutus.imp().getNationDB().getMinMilitary(groundControl, MilitaryUnit.TANK, gcDate) == 0
                )) groundControl = 0;
            }
            if (checkAC && acDate != Long.MAX_VALUE) {
                attacks = Locutus.imp().getWarDb().getAttacks(airSuperiority, acDate);
                attacks.removeIf(a -> a.defender_nation_id != airSuperiority || a.success != 3);
                if (!attacks.isEmpty()
                        || Locutus.imp().getNationDB().getMinMilitary(airSuperiority, MilitaryUnit.AIRCRAFT, acDate) == 0)
                    airSuperiority = 0;
            }
            if (checkBlockade && blockadeDate != Long.MAX_VALUE) {
                int blockader = blockaded == war.attacker_id ? war.defender_id : war.attacker_id;
                attacks = Locutus.imp().getWarDb().getAttacks(blockader, blockadeDate);
                attacks.removeIf(a -> a.defender_nation_id != blockader || a.success != 3);
                if (!attacks.isEmpty() ||
                        Locutus.imp().getNationDB().getMinMilitary(blockader, MilitaryUnit.SHIP, blockadeDate) == 0) {
                    blockaded = 0;
                }
            }
        } else {
            gcDate = Long.MAX_VALUE;
            acDate = Long.MAX_VALUE;
            blockadeDate = Long.MAX_VALUE;
        }
    }

    public void update(WarContainer pnwWar) {
        this.airSuperiority = Integer.parseInt(pnwWar.getAirSuperiority());
        this.groundControl = Integer.parseInt(pnwWar.getGroundControl());
        this.blockaded = Integer.parseInt(pnwWar.getBlockade());
        this.attackerFortified = pnwWar.isAggressorIsFortified();
        this.defenderFortified = pnwWar.isDefenderIsFortified();
        this.attackerResistance = Integer.parseInt(pnwWar.getAggressorResistance());
        this.defenderResistance = Integer.parseInt(pnwWar.getDefenderResistance());
        this.attackerMAP = Integer.parseInt(pnwWar.getAggressorMilitaryActionPoints());
        this.defenderMAP = Integer.parseInt(pnwWar.getDefenderMilitaryActionPoints());
    }

    public void updateCounterStats() {
        this.counterStat = Locutus.imp().getWarDb().getCounterStat(war);
    }

    public void update() {
        updateCounterStats();
        update(Locutus.imp().getWarDb().getWar(warId));
    }

    public CounterStat getCounterStat() {
        return counterStat;
    }

    private static final String cmdEmoji = "\uD83D\uDD04";
    private static final String simEmoji = "\uD83E\uDD16";
    private static final String counterEmoji = "\uD83C\uDD98";
    public static final String spyEmoji = "\uD83D\uDD75";

    public void embed(MessageChannel channel) {
        embed(channel, false);
    }

    public void embed(MessageChannel channel, boolean addReactions) {
        String warUrl = "" + Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + warId;
        String cmd = "!WarInfo " + warUrl;
        String sim = "~!simulate " + warUrl;
        String counter = "~!counter " + warUrl;
        String counterSpy = "~!counterspy " + warUrl + " *";

        String pendingEmoji = "\u2705";
        String pending = "_!UpdateEmbed role:milcom 'description:{description}\n" +
                "\n" +
                "Assigned to %user% in {timediff}'";

        if (addReactions) {
            String desc = getDescription();
            desc += "\n\nPress " + pendingEmoji + " to assign";
            DiscordUtil.createEmbedCommand(channel, getTitle(), desc, pendingEmoji, pending, cmdEmoji, cmd);
        } else {
            DiscordUtil.createEmbedCommand(channel, getTitle(), getDescription());
        }
    }

    private String getSquare(int resistance) {
        if (resistance > 80) {
            return "\uD83D\uDFE9";
        }
        if (resistance > 65) {
            return "\uD83D\uDFE8";
        }
        if (resistance > 30) {
            return "\uD83D\uDFE7";
        }
        return "\uD83D\uDFE5";
    }

    private String formatNation(boolean attacker) {
        String nationFormat = "[%s](%s) - [%s](" + Settings.INSTANCE.PNW_URL() + "/alliance/id=%s) - %s - %s - %s\n" + // name - alliance - active
                "%s " +
                "%s " +
                "**Resistance**:\n" +
                "%s\n" +
                "**Military Action Points Available**:\n" +
                "%s/12\n\n";

        String fortSym = "\uD83D\uDEE1";
        String gcSym = "\uD83D\uDC82";
        String acSym = "\u2708";
        String blockadeSym = "\u26F5";
        String peaceSym = "\uD83D\uDD4A";

        int nationId = attacker ? war.attacker_id : war.defender_id;
        int otherId = attacker ? war.defender_id : war.attacker_id;

        String control = "";
        if (blockaded == nationId) {
            control += blockadeSym;
        }
        if (groundControl == nationId) {
            control += gcSym;
        }
        if (airSuperiority == nationId) {
            control += acSym;
        }
        if (attacker ? attackerFortified : defenderFortified) {
            control += fortSym;
        }
        if (war.status == (attacker ? WarStatus.ATTACKER_OFFERED_PEACE : WarStatus.DEFENDER_OFFERED_PEACE)) {
            control += peaceSym;
        }

        int resistance = attacker ? attackerResistance : defenderResistance;
        String resBar = StringMan.repeat(getSquare(resistance), (resistance + 9) / 10);
        resBar = resBar + ("(" + resistance + "/100)");

        int allianceId = attacker ? war.attacker_aa : war.defender_aa;
        String alliance = PnwUtil.getName(allianceId, true);

        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
        String active_m = "";
        String markdown1 = "";
        String markdown2 = "";
        Rank rank = Rank.REMOVE;

        if (nation != null) {
            active_m = TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m());
            markdown1 = nation.toMarkdown(false, true, false);
            markdown2 = nation.toMarkdown(false, false, true);
            rank = Rank.byId(nation.getPosition());
        }

        return String.format(nationFormat,
                PnwUtil.getName(nationId, false),
                PnwUtil.getUrl(nationId, false),
                alliance,
                allianceId,
                control,
                active_m,
                rank,
                markdown1,
                markdown2,
                resBar,
                attacker ? attackerMAP : defenderMAP
        );
    }

    public DBWar getWar() {
        return war;
    }

    public boolean isActive() {
        if (war.status != WarStatus.ACTIVE && war.status != WarStatus.DEFENDER_OFFERED_PEACE && war.status != WarStatus.ATTACKER_OFFERED_PEACE) {
            return false;
        }
        if (attackerResistance > 0 && defenderResistance > 0) {
            long turnStart = TimeUtil.getTurn(war.date);
            long turnNow = TimeUtil.getTurn();
            return turnNow - turnStart < 60;
        }
        return false;
    }
}