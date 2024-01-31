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

    public static Map<String, Function<OffDefStatGroup, Object>> createHeader() {
        Map<String, Function<OffDefStatGroup, Object>> header = new Object2ObjectLinkedOpenHashMap<>();
        header.put("wars", p -> (int) p.totalWars);
        header.put("wars_active", p -> (int) p.activeWars);
        header.put("attacks", p -> (int) p.attacks);
        header.put("wars_won", p -> (int) p.warsWon);
        header.put("wars_lost", p -> (int) p.warsLost);
        header.put("wars_expired", p -> (int) p.warsExpired);
        header.put("wars_peaced", p -> (int) p.warsPeaced);
        return header;
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
