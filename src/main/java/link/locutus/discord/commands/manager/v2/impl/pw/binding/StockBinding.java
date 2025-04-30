package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import com.google.common.reflect.TypeToken;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.stock.Exchange;
import link.locutus.discord.commands.stock.ExchangeCategory;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.NationOrExchange;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StockBinding extends BindingHelper {

    @Binding
    public Exchange exchange(StockDB db, String exchange) {
        Exchange obj = db.getExchange(exchange);
        if (obj == null) throw new IllegalArgumentException("Invalid company: " + exchange);
        return obj;
    }

    @Binding
    @Me
    public Exchange exchangeByContext(StockDB db, @Me Guild guild, @Me MessageChannel channel) {
        GuildDB rootGuild = Locutus.imp().getGuildDB(StockDB.ROOT_GUILD);
        if (rootGuild.getGuild().getIdLong() != guild.getIdLong()) {
            throw new IllegalArgumentException("This command must be used inside the company channel.");
        }
        String[] split = channel.getName().split("-");
        IllegalArgumentException err = new IllegalArgumentException("This command must be used inside the company channel in this guild.");
        if (split.length < 2 || !MathMan.isInteger(split[0])) {
            throw err;
        }
        Exchange exchangeByName = db.getExchange(split[1]);
        Exchange exchangeById = db.getExchange(Integer.parseInt(split[0]));
        if (exchangeById == null || !exchangeById.equals(exchangeByName)) throw err;
        return exchangeById;
    }

    @Binding
    public List<Exchange> exchanges(StockDB db, String exchanges) {
        List<Exchange> result = new ArrayList<>();
        for (String company : exchanges.split(",")) {
            result.add(exchange(db, company));
        }
        return result;
    }

    @Binding(examples = {"company", "alliance", "currency"})
    public ExchangeCategory category(String category) {
        return emum(ExchangeCategory.class, category);
    }

    @Binding
    public Map<Exchange, Double> stocks(StockDB db, String stock) {
        stock = stock.replace(" ", "").replace('=', ':').replaceAll("([0-9]),([0-9])", "$1$2").toUpperCase();
        Type type = new TypeToken<Map<String, Double>>() {
        }.getType();
        Map<String, Double> resultStr = WebUtil.GSON.fromJson(stock, type);
        Map<Exchange, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : resultStr.entrySet()) {
            Exchange exchange = db.getExchange(entry.getKey());
            if (exchange == null) throw new IllegalArgumentException("Invalid exchange: " + entry.getKey());
            result.put(exchange, entry.getValue());
        }
        return result;
    }

    @Binding
    public NationOrExchange nationOrExchange(StockDB db, String input) {
        if (input.charAt(0) == '*') return new NationOrExchange(exchange(db, input.substring(1)));
        return new NationOrExchange(DiscordUtil.parseNation(input, true));
    }
}
