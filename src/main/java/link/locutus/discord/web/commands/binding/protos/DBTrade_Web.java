package link.locutus.discord.web.commands.binding.protos;

import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.politicsandwar.graphql.model.TradeType;
import link.locutus.discord.apiv1.enums.ResourceType;
public class DBTrade_Web {
    @Nullable public boolean isBuy;
    @Nullable public int getBuyer;
    @Nullable public TradeType getType;
    @Nullable public int getSeller;
    @Nullable public DBNation_Web getBuyerNation;
    @Nullable public long getDate;
    @Nullable public int getQuantity;
    @Nullable public ResourceType getResource;
    @Nullable public long getDate_accepted;
    @Nullable public double getResourceValue;
    @Nullable public DBNation_Web getSellerNation;
    @Nullable public int getPpu;
    @Nullable public int getParent_id;
    @Nullable public int getTradeId;
}