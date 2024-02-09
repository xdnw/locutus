package link.locutus.discord.db.entities.conflict;

import com.google.gson.JsonArray;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import static link.locutus.discord.db.entities.conflict.ConflictColumn.header;
import static link.locutus.discord.db.entities.conflict.ConflictColumn.ranking;

public class OffDefStatGroup {
    public char totalWars;
    public char activeWars;
    public int attacks = 0;
    public char warsWon;
    public char warsLost;
    public char warsExpired;
    public char warsPeaced;
    public final char[] attackTypes = new char[AttackType.values.length];
    public final char[] attackSubTypes = new char[AttackTypeSubCategory.values.length];
    public final char[] successTypes = new char[SuccessType.values.length];
    public final char[] warTypes = new char[WarType.values.length];

    public static Map<ConflictColumn, Function<OffDefStatGroup, Object>> createHeader() {
        Map<ConflictColumn, Function<OffDefStatGroup, Object>> map = new Object2ObjectLinkedOpenHashMap<>();
        map.put(ranking("wars"), p -> (int) p.totalWars);
        map.put(header("wars_active"), p -> (int) p.activeWars);
        map.put(ranking("attacks"), p -> (int) p.attacks);
        map.put(ranking("wars_won"), p -> (int) p.warsWon);
        map.put(header("wars_lost"), p -> (int) p.warsLost);
        map.put(header("wars_expired"), p -> (int) p.warsExpired);
        map.put(header("wars_peaced"), p -> (int) p.warsPeaced);
        for (AttackType type : AttackType.values) {
            ConflictColumn col;
            String name = type.name().toLowerCase() + "_attacks";
            if (type == AttackType.MISSILE || type == AttackType.NUKE) {
                col = ranking(name);
            } else {
                col = header(name);
            }
            map.put(col, p -> (int) p.attackTypes[type.ordinal()]);
        }
        for (AttackTypeSubCategory type : AttackTypeSubCategory.values) {
            switch (type) {
                case AIRSTRIKE_MONEY:
                case AIRSTRIKE_INFRA:
                case MISSILE:
                case NUKE:
                    continue;
            }
            map.put(header(type.name().toLowerCase() + "_attacks"), p -> (int) p.attackSubTypes[type.ordinal()]);
        }
        for (SuccessType type : SuccessType.values) {
            map.put(header(type.name().toLowerCase() + "_attacks"), p -> (int) p.successTypes[type.ordinal()]);
        }
        for (WarType type : WarType.values) {
            map.put(header(type.name().toLowerCase() + "_wars"), p -> (int) p.warTypes[type.ordinal()]);
        }
        return map;
    }

    public void newWar(DBWar war, boolean isAttacker) {
        totalWars++;
        if (war.isActive()) activeWars++;
        else {
            addWarStatus(war.getStatus(), isAttacker);
        }
        warTypes[war.getWarType().ordinal()]++;
    }

    private void addWarStatus(WarStatus status, boolean isAttacker) {
        switch (status) {
            case DEFENDER_VICTORY -> {
                if (isAttacker) warsLost++;
                else warsWon++;
            }
            case ATTACKER_VICTORY -> {
                if (isAttacker) warsWon++;
                else warsLost++;
            }
            case PEACE -> {
                warsPeaced++;
            }
            case EXPIRED -> {
                warsExpired++;
            }
        }
    }

    public void updateWar(DBWar previous, DBWar current, boolean isAttacker) {
        addWarStatus(current.getStatus(), isAttacker);
        if (previous.isActive() && !current.isActive()) {
            activeWars--;
        }
    }

    public void newAttack(DBWar war, AbstractCursor attack, AttackTypeSubCategory subCategory) {
        attacks++;
        attackTypes[attack.getAttack_type().ordinal()]++;
        if (subCategory != null) {
            attackSubTypes[subCategory.ordinal()]++;
        }
        successTypes[attack.getSuccess().ordinal()]++;
    }
}
