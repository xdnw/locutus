package link.locutus.discord.web.commands.binding.protos;

import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Continent;
public class DBTreasure_Web {
    @Nullable public long getDaysRemaining;
    @Nullable public DBNation_Web getNation;
    @Nullable public int getId;
    @Nullable public NationColor getColor;
    @Nullable public double getResource;
    @Nullable public long getTimeUntilNextSpawn;
    @Nullable public Set<DBNation_Web> getNationsInRange;
    @Nullable public String getName;
    @Nullable public int getBonus;
    @Nullable public Continent getContinent;
    @Nullable public double getResourceValue;
    @Nullable public long getSpawnDate;
    @Nullable public int getNation_id;
    @Nullable public int getNumNationsInRange;
}