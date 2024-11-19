package link.locutus.discord.web.commands.binding.protos;

import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import link.locutus.discord.apiv1.enums.ResourceType;
public class TaxDeposit_Web {
    @Nullable public String getNationInfo;
    @Nullable public int getTaxId;
    @Nullable public Map<ResourceType, Double> getResourcesMap;
    @Nullable public double getAmount;
    @Nullable public DBNation_Web getNation;
    @Nullable public int getId;
    @Nullable public long getTurnsOld;
    @Nullable public int getMoneyRate;
    @Nullable public int getInternalResourceRate;
    @Nullable public long getDateMs;
    @Nullable public String getResourcesJson;
    @Nullable public double getResource;
    @Nullable public int getResourceRate;
    @Nullable public double[] getResourcesArray;
    @Nullable public String getDateStr;
    @Nullable public double getResourceValue;
    @Nullable public int getNationId;
    @Nullable public double getMarketValue;
    @Nullable public String getAllianceInfo;
    @Nullable public int getAllianceId;
    @Nullable public DBAlliance_Web getAlliance;
    @Nullable public int getInternalMoneyRate;
}