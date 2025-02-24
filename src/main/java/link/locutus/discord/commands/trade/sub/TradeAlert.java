//package link.locutus.discord.commands.trade.sub;
//
//import link.locutus.discord.Locutus;
//import link.locutus.discord.config.Settings;
//import link.locutus.discord.db.entities.DBNation;
//import link.locutus.discord.db.entities.DBTrade;
//import link.locutus.discord.util.discord.DiscordUtil;
//import link.locutus.discord.util.MarkupUtil;
//import link.locutus.discord.util.MathMan;
//import link.locutus.discord.apiv1.enums.ResourceType;
//import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
//
//import java.util.Objects;
//
//public class TradeAlert {
//    private final int previousLow;
//    private final int previousHigh;
//    private final DBNation previousLowNation;
//    private final DBNation previousHighNation;
//    private final DBNation currentLowNation;
//    private final DBNation currentHighNation;
//    private final int currentHigh;
//    private final int currentLow;
//    private final ResourceType resource;
//    private final DBTrade low;
//    private final DBTrade high;
//
//    public TradeAlert(ResourceType type, DBTrade low, DBTrade high) {
//        this.low = low;
//        this.high = high;
//        this.resource = type;
//        this.previousLow = Locutus.imp().getTradeManager().getLow(type);
//        this.previousHigh = Locutus.imp().getTradeManager().getHigh(type);
//        this.previousLowNation = Locutus.imp().getTradeManager().getLowNation(type);
//        this.previousHighNation = Locutus.imp().getTradeManager().getHighNation(type);
//
//        if (low != null) {
//            Locutus.imp().getTradeManager().setLow(type, low);
//        } else {
//            Locutus.imp().getTradeManager().setLow(type, null);
//        }
//
//        if (high != null) {
//            Locutus.imp().getTradeManager().setHigh(type, high);
//        } else {
//            Locutus.imp().getTradeManager().setHigh(type, null);
//        }
//
//        this.currentLowNation = Locutus.imp().getTradeManager().getLowNation(type);
//        this.currentHighNation = Locutus.imp().getTradeManager().getHighNation(type);
//        this.currentHigh = Locutus.imp().getTradeManager().getHigh(type);
//        this.currentLow = Locutus.imp().getTradeManager().getLow(type);
//    }
//
//    public ResourceType getResource() {
//        return resource;
//    }
//
//    public int getPreviousLow() {
//        return previousLow;
//    }
//
//    public int getPreviousHigh() {
//        return previousHigh;
//    }
//
//    public DBNation getPreviousLowNation() {
//        return previousLowNation;
//    }
//
//    public DBNation getPreviousHighNation() {
//        return previousHighNation;
//    }
//
//    public DBNation getCurrentLowNation() {
//        return currentLowNation;
//    }
//
//    public DBNation getCurrentHighNation() {
//        return currentHighNation;
//    }
//
//    public int getCurrentHigh() {
//        return currentHigh;
//    }
//
//    public int getCurrentLow() {
//        return currentLow;
//    }
//
//    public void toCard(String title, MessageChannel channel) {
//        StringBuilder body = new StringBuilder();
//        String lowStr = MarkupUtil.markdownUrl("Low: $" + MathMan.format(currentLow), url(resource, true));
//        if (Objects.equals(previousLow, currentLow)) {
//            body.append(lowStr);
//        } else {
//            if (low != null) lowStr += "x" + low.getQuantity();
//            body.append("**" + lowStr + "**");
//        }
//        body.append("\n");
//        String highStr = MarkupUtil.markdownUrl("High: $" + MathMan.format(currentHigh), url(resource, false));
//        if (Objects.equals(previousHigh, currentHigh)) {
//            body.append(highStr);
//        } else {
//            if (high != null) highStr += "x" + high.getQuantity();
//            body.append("**" + highStr + "**");
//        }
//        DiscordUtil.createEmbedCommand(channel, title, body.toString());
//    }
//
//    public String url(ResourceType type, boolean isBuy) {
//        String url = "" + Settings.PNW_URL() + "/index.php?id=90&display=world&resource1=%s&buysell=" + (isBuy ? "buy" : "sell") + "&ob=price&od=DEF";
//        return String.format(url, type.name().toLowerCase());
//    }
//}
