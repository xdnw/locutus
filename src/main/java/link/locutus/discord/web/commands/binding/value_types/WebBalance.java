package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.apiv1.enums.DepositType;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;

public class WebBalance {
    public int id;
    public boolean is_aa;
    public double[] total;
    public boolean include_grants;
    public Map<Long, Integer> access;
    public Map<String, double[]> breakdown;
    @Nullable public String no_access_msg;

    public WebBalance() {
    }
}
