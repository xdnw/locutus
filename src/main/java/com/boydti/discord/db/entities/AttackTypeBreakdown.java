package com.boydti.discord.db.entities;

import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.update.WarUpdateProcessor;
import com.boydti.discord.apiv1.domains.subdomains.DBAttack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildMessageChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AttackTypeBreakdown {
    private final String nameB;
    private final String nameA;
    private final AtomicInteger totalA = new AtomicInteger();
    private final AtomicInteger totalB = new AtomicInteger();
    private final Map<WarUpdateProcessor.AttackTypeSubCategory, Integer> mapA = new HashMap<>();
    private final Map<WarUpdateProcessor.AttackTypeSubCategory, Integer> mapB = new HashMap<>();

    public AttackTypeBreakdown(String nameA, String nameB) {
        this.nameA = nameA;
        this.nameB = nameB;
    }

    public int getTotalAttacks(boolean primary) {
        return (primary ? totalA : totalB).get();
    }

    public int getTotalAttacks(WarUpdateProcessor.AttackTypeSubCategory category, boolean primary) {
        return (primary ? mapA : mapB).getOrDefault(category, 0);
    }

    public void addAttack(DBAttack attack, boolean isAttacker) {
        addAttack(attack, p -> isAttacker, p -> !isAttacker);
    }

    public AttackTypeBreakdown addAttacks(Collection<DBAttack> attacks, Function<DBAttack, Boolean> isPrimary, Function<DBAttack, Boolean> isSecondary) {
        for (DBAttack attack : attacks) {
            addAttack(attack, isPrimary, isSecondary);
        }
        return this;
    }

    public AttackTypeBreakdown addAttack(DBAttack attack, Function<DBAttack, Boolean> isPrimary, Function<DBAttack, Boolean> isSecondary) {
        boolean primary = isPrimary.apply(attack);
        boolean secondary = isSecondary.apply(attack);
        if (!primary && !secondary) return this;

        Map<WarUpdateProcessor.AttackTypeSubCategory, Integer> map = primary ? mapA : mapB;
        AtomicInteger total = primary ? totalA : totalB;

        WarUpdateProcessor.AttackTypeSubCategory category = WarUpdateProcessor.incrementCategory(attack, map);
        if (category != null) {
            total.incrementAndGet();
        }
        return this;
    }

    public void toEmbed(GuildMessageChannel channel) {
        DiscordUtil.createEmbedCommand(channel, toEmbed());
    }

    public Consumer<EmbedBuilder> toEmbed() {
        List<WarUpdateProcessor.AttackTypeSubCategory> allTypes = new ArrayList<>();
        allTypes.addAll(mapA.keySet());
        allTypes.addAll(mapB.keySet());
        Collections.sort(allTypes);
        allTypes = new ArrayList<>(new LinkedHashSet<>(allTypes));
        List<String> typesStr = allTypes.stream().map(r -> r.name().toLowerCase()).collect(Collectors.toList());
        typesStr.add("TOTAL");

        List<WarUpdateProcessor.AttackTypeSubCategory> finalAllTypes = allTypes;
        return b -> {

            ArrayList<String> groupA = new ArrayList<>();
            ArrayList<String> groupB = new ArrayList<>();

            for (WarUpdateProcessor.AttackTypeSubCategory type : finalAllTypes) {
                groupA.add(MathMan.format(mapA.getOrDefault(type, 0)));
                groupB.add(MathMan.format(mapB.getOrDefault(type, 0)));
            }
            groupA.add(MathMan.format(totalA.get()));
            groupB.add(MathMan.format(totalB.get()));

            b.addField("Type", StringMan.join(typesStr, "\n"), true);
            b.addField(nameA, StringMan.join(groupA, "\n"), true);
            b.addField(nameB, StringMan.join(groupB, "\n"), true);
        };
    }
}
