package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

public class Trending extends Command {

    public Trending() {
        super("Trending", "TrendingTrades", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " [days]";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        int days = 1;
        if (args.size() == 1) {
            days = Integer.parseInt(args.get(0));
        } else if (args.size() != 0) return usage(args.size(), 0, 1, channel);


        Map<ResourceType, Map<Integer, LongAdder>> sold = new EnumMap<>(ResourceType.class);
        Map<ResourceType, Map<Integer, LongAdder>> bought = new EnumMap<>(ResourceType.class);

        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> averages = Locutus.imp().getTradeManager().getAverage(cutoffMs);

        Map<ResourceType, Double> lowMap = averages.getKey();
        Map<ResourceType, Double> highMap = averages.getValue();

        TradeDB tradeDB = Locutus.imp().getTradeManager().getTradeDb();
        for ( DBTrade offer : tradeDB.getTrades(cutoffMs)) {
            // Ignore outliers
            int ppu = offer.getPpu();


            double lowCutoff = averages.getKey().get(offer.getResource()) * 0.5;
            double highCutoff = averages.getValue().get(offer.getResource()) * 2;

            if (ppu < lowCutoff || ppu > highCutoff) continue;

            if (offer.getResource() == ResourceType.CREDITS) {
                ppu /= 10000;
            } else if (offer.getResource() != ResourceType.FOOD) {
                ppu /= 10;
            }

            Map<ResourceType, Map<Integer, LongAdder>> map = offer.isBuy() ? sold : bought;
            Map<Integer, LongAdder> rssMap = map.get(offer.getResource());
            if (rssMap == null) {
                rssMap = new HashMap<>();
                map.put(offer.getResource(), rssMap);
            }
            LongAdder cumulative = rssMap.get(ppu);
            if (cumulative == null) {
                cumulative = new LongAdder();
                rssMap.put(ppu, cumulative);
            }
            cumulative.add(offer.getQuantity());
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = SpreadSheet.create(db, SheetKeys.TRADE_VOLUME_SHEET);

        List<Object> header = new ArrayList<>();
        header.add("PPU (adjusted)");
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.MONEY) {
                header.add(value.name() + " BUY");
                header.add(value.name() + " SELL");
            }
        }
        sheet.setHeader(header);

        Map<ResourceType, Long> soldPrevious = new HashMap<>();
        Map<ResourceType, Long> boughtPrevious = new HashMap<>();

        for (int i = 30; i < 10000; i += 5) {
            header.set(0, Integer.toString(i));
            for (int j = 1; j < ResourceType.values.length; j++) {
                ResourceType value = ResourceType.values[j];
                {
                    int headerIndex = (j - 1) * 2 + 1;
                    Map<Integer, LongAdder> soldByType = sold.getOrDefault(value, Collections.emptyMap());
                    LongAdder amt = soldByType.getOrDefault(i, new LongAdder());
                    if (amt.longValue() == 0) {
                        header.set(headerIndex, "");
                    } else {
                        Long previous = soldPrevious.getOrDefault(value, 0L);
                        header.set(headerIndex, previous + amt.longValue());
                        soldPrevious.put(value, previous + amt.longValue());
                    }
                }
                {
                    int headerIndex = (j - 1) * 2 + 2;
                    Map<Integer, LongAdder> soldByType = bought.getOrDefault(value, Collections.emptyMap());
                    LongAdder amt = soldByType.getOrDefault(i, new LongAdder());
                    if (amt.longValue() == 0) {
                        header.set(headerIndex, "");
                    } else {
                        Long previous = boughtPrevious.getOrDefault(value, 0L);
                        header.set(headerIndex, previous + amt.longValue());
                        boughtPrevious.put(value, previous + amt.longValue());
                    }
                }
            }

            sheet.addRow(header);
        }

        sheet.updateWrite();

        sheet.attach(channel.create(), "trending").send();
        return null;
    }
}
