package link.locutus.discord.db.entities;

import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.scheduler.KeyValue;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

public class Transfer {
    private boolean isReceiverAA;
    private boolean isSenderAA;
    private long date;
    private String note;
    private int sender;
    private int receiver;
    private int banker;
    private ResourceType rss;
    private double amount;

    public static List<Transfer> ofNationBank(Element row) {
        return of(row, 5);
    }

    public static List<Transfer> ofAllianceBank(Element row) {
        return of(row, 5);
    }

    public static Transfer of(ResultSet rs) throws SQLException {
        long date = rs.getLong("date");
        String note = rs.getString("note");
        int bankId = rs.getInt("bank");
        int receiver = rs.getInt("receiver");
        int banker = rs.getInt("banker");
        ResourceType resource = ResourceType.values[rs.getInt("resource")];
        double amount = rs.getDouble("amount");

        ResultSetMetaData rsmd = rs.getMetaData();
        if (rsmd.getColumnCount() == 8) {
            boolean isDeposit = rs.getBoolean("is_deposit");
            return new Transfer(date, note, bankId, receiver, isDeposit, banker, resource, amount);
        } else if (rsmd.getColumnCount() == 7) {
            return new Transfer(date, note, bankId, true, receiver, true, banker, resource, amount);
        } else {
            throw new UnsupportedOperationException("Unknown column count.");
        }
    }

    public static List<Transfer> of(Element row, int resourceOffset) {
        List<Transfer> list = new ArrayList<>();

        Elements columns = row.getElementsByTag("td");

        int index = MathMan.parseInt(columns.get(0).text().replace(")", ""));

        Element noteElem = columns.get(1).select("img").first();
        String note = noteElem != null ? Parser.unescapeEntities(noteElem.attr("title"), true) : null;

        long date = TimeUtil.parseDate(TimeUtil.MMDDYYYY_HH_MM_A, columns.get(1).text());

        Map.Entry<Integer, Boolean> sender = idIsAlliance(columns.get(2));
        Map.Entry<Integer, Boolean> receiver = idIsAlliance(columns.get(3));
        Map.Entry<Integer, Boolean> banker = idIsAlliance(columns.get(4));

        ResourceType[] resources = {ResourceType.MONEY, ResourceType.FOOD, ResourceType.COAL, ResourceType.OIL, ResourceType.URANIUM, ResourceType.LEAD, ResourceType.IRON, ResourceType.BAUXITE, ResourceType.GASOLINE, ResourceType.MUNITIONS, ResourceType.STEEL, ResourceType.ALUMINUM};

        for (int j = 0; j < resources.length; j++) {
            ResourceType resource = resources[j];
            double amt = MathMan.parseDouble(columns.get(resourceOffset + j).text());
            if (amt != 0) {
                list.add(new Transfer(date, note, sender.getKey(), sender.getValue(), receiver.getKey(), receiver.getValue(), banker.getKey(), resource, amt));
            }
        }
        return list;
    }

    private static Map.Entry<Integer, Boolean> idIsAlliance(Element td) {
        try {
            String url = td.getElementsByTag("a").get(0).attr("href").toLowerCase();
            int id = Integer.parseInt(url.split("=")[1]);
            boolean isAA = url.contains("/alliance/");
            return new KeyValue<>(id, isAA);
        } catch (IndexOutOfBoundsException ignore) {
            return new KeyValue<>(0, false);
        }
    }

    public Transfer(long date, String note, int sender, int receiver, boolean isReceiverAA, int banker, ResourceType rss, double amount) {
        this.date = date;
        this.note = note;
        this.sender = sender;
        this.receiver = receiver;
        this.banker = banker;
        this.isReceiverAA = isReceiverAA;
        this.isSenderAA = !isReceiverAA;
        this.rss = rss;
        this.amount = amount;
    }

    public Transfer(long date, String note, int sender, boolean isSenderAA, int receiver, boolean isReceiverAA, int banker, ResourceType rss, double amount) {
        this.date = date;
        this.note = note;
        this.sender = sender;
        this.receiver = receiver;
        this.banker = banker;
        this.isReceiverAA = isReceiverAA;
        this.isSenderAA = isSenderAA;
        this.rss = rss;
        this.amount = amount;
    }

    public boolean isReceiverAA() {
        return isReceiverAA;
    }

    public void setReceiverAA(boolean receiverAA) {
        isReceiverAA = receiverAA;
    }

    public boolean isSenderAA() {
        return isSenderAA;
    }

    public void setSenderAA(boolean senderAA) {
        isSenderAA = senderAA;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public int getSender() {
        return sender;
    }

    public void setSender(int sender) {
        this.sender = sender;
    }

    public int getReceiver() {
        return receiver;
    }

    public void setReceiver(int receiver) {
        this.receiver = receiver;
    }

    public int getBanker() {
        return banker;
    }

    public void setBanker(int banker) {
        this.banker = banker;
    }

    public ResourceType getRss() {
        return rss;
    }

    public void setRss(ResourceType rss) {
        this.rss = rss;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Transfer transfer = (Transfer) o;

        if (date != transfer.date) return false;
        if (sender != transfer.sender) return false;
        if (receiver != transfer.receiver) return false;
        if (banker != transfer.banker) return false;
        if (Double.compare(transfer.amount, amount) != 0) return false;
        return rss == transfer.rss;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (int) (date ^ (date >>> 32));
        result = 31 * result + sender;
        result = 31 * result + receiver;
        result = 31 * result + banker;
        result = 31 * result + (rss != null ? rss.hashCode() : 0);
        temp = Double.doubleToLongBits(amount);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Transfer{" +
                "isReceiverAA=" + isReceiverAA +
                ", isSenderAA=" + isSenderAA +
                ", date=" + date +
                ", note='" + note + '\'' +
                ", sender=" + sender +
                ", receiver=" + receiver +
                ", banker=" + banker +
                ", rss=" + rss +
                ", amount=" + amount +
                '}';
    }

    public String toSimpleString() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC).toLocalDate() +
                " | " + note +
                " | sender: " + PW.getName(sender, isSenderAA) +
                " | receiver: " + PW.getName(receiver, isReceiverAA) +
                " | banker: " + PW.getName(banker, false) +
                " | " + MathMan.format(amount) + "x" + rss.name();
    }

    public double convertedTotal() {
        return ResourceType.convertedTotal(getRss(), getAmount());
    }
}
