package link.locutus.discord.web.commands.binding.protos;

import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import link.locutus.discord.apiv1.enums.TreatyType;
public class Treaty_Web {
    @Nullable public int getFromId;
    @Nullable public String toLineString;
    @Nullable public TreatyType getType;
    @Nullable public long getEndTime;
    @Nullable public boolean isPending;
    @Nullable public long getDate;
    @Nullable public int getId;
    @Nullable public DBAlliance_Web getTo;
    @Nullable public long getTurnEnds;
    @Nullable public DBAlliance_Web getFrom;
    @Nullable public double getResource;
    @Nullable public boolean isAlliance;
    @Nullable public double getResourceValue;
    @Nullable public int getToId;
    @Nullable public int getTurnsRemaining;
}