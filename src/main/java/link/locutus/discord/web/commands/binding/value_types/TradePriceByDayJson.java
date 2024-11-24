package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class TradePriceByDayJson {
    public String x;
    public String y;
    public List<String> labels;
    public List<Long> timestamps;
    public List<List<Double>> prices;
}
