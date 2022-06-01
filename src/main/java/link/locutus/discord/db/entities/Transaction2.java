package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.Offer;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.ResourceType;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;

import static link.locutus.discord.apiv1.enums.ResourceType.ALUMINUM;
import static link.locutus.discord.apiv1.enums.ResourceType.BAUXITE;
import static link.locutus.discord.apiv1.enums.ResourceType.COAL;
import static link.locutus.discord.apiv1.enums.ResourceType.FOOD;

import static link.locutus.discord.apiv1.enums.ResourceType.GASOLINE;
import static link.locutus.discord.apiv1.enums.ResourceType.IRON;
import static link.locutus.discord.apiv1.enums.ResourceType.LEAD;
import static link.locutus.discord.apiv1.enums.ResourceType.MONEY;
import static link.locutus.discord.apiv1.enums.ResourceType.MUNITIONS;
import static link.locutus.discord.apiv1.enums.ResourceType.OIL;
import static link.locutus.discord.apiv1.enums.ResourceType.STEEL;
import static link.locutus.discord.apiv1.enums.ResourceType.URANIUM;

public class Transaction2 {
    public int tx_id;
    public long tx_datetime;
    public long sender_id;
    public int sender_type;
    public long receiver_id;
    public int receiver_type;
    public int banker_nation;
    public String note;
    public double[] resources;

    public Transaction2(int tx_id, long tx_datetime, long sender_id, int sender_type, long receiver_id, int receiver_type, int banker_nation, String note, double[] resources) {
        this.tx_id = tx_id;
        this.tx_datetime = tx_datetime;
        this.sender_id = sender_id;
        this.sender_type = sender_type;
        this.receiver_id = receiver_id;
        this.receiver_type = receiver_type;
        this.banker_nation = banker_nation;
        this.note = note;
        this.resources = resources;
    }

    public static Transaction2 fromAPiv3(JsonObject json) throws ParseException {
        int id = Integer.parseInt(json.get("id").getAsString());
        long date = Instant.parse(json.get("date").getAsString()).toEpochMilli();
        int sid = json.get("sid").getAsInt();
        int stype = json.get("stype").getAsInt();
        int rid = json.get("rid").getAsInt();
        int rtype = json.get("rtype").getAsInt();
        int pid = json.get("pid").getAsInt();
        String note = json.get("note").getAsString();
        Transaction2 tx = new Transaction2(id, date, sid, stype, rid, rtype, pid, note, ResourceType.getBuffer());

        tx.resources[ResourceType.MONEY.ordinal()] = json.get("money").getAsDouble();
        tx.resources[ResourceType.COAL.ordinal()] = json.get("coal").getAsDouble();
        tx.resources[ResourceType.OIL.ordinal()] = json.get("oil").getAsDouble();
        tx.resources[ResourceType.URANIUM.ordinal()] = json.get("uranium").getAsDouble();
        tx.resources[ResourceType.IRON.ordinal()] = json.get("iron").getAsDouble();
        tx.resources[ResourceType.BAUXITE.ordinal()] = json.get("bauxite").getAsDouble();
        tx.resources[ResourceType.LEAD.ordinal()] = json.get("lead").getAsDouble();
        tx.resources[ResourceType.GASOLINE.ordinal()] = json.get("gasoline").getAsDouble();
        tx.resources[ResourceType.MUNITIONS.ordinal()] = json.get("munitions").getAsDouble();
        tx.resources[ResourceType.STEEL.ordinal()] = json.get("steel").getAsDouble();
        tx.resources[ResourceType.ALUMINUM.ordinal()] = json.get("aluminum").getAsDouble();
        tx.resources[ResourceType.FOOD.ordinal()] = json.get("food").getAsDouble();

        return tx;
    }

    public Transaction2(ResultSet rs) throws SQLException {
        tx_id = rs.getInt("tx_id");
        tx_datetime = rs.getLong("tx_datetime");
        sender_id = rs.getLong("sender_id");
        sender_type = rs.getInt("sender_type");
        receiver_id = rs.getLong("receiver_id");
        receiver_type = rs.getInt("receiver_type");
        banker_nation = rs.getInt("banker_nation_id");
        note = rs.getString("note");
        resources = new double[ResourceType.values.length];
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            resources[type.ordinal()] = rs.getLong(type.name()) / 100d;
        }
    }

    private static Map.Entry<Long, Integer> idIsAlliance(Element td) {
        try {
            long id;
            int type;

            String url = td.getElementsByTag("a").get(0).attr("href").toLowerCase();
            id = Integer.parseInt(url.split("=")[1]);
            if (url.contains("/alliance/")) {
                type = 2;
            } else if (url.contains("/nation/")) {
                type = 1;
            } else {
                type = 0;
            }
            return new AbstractMap.SimpleEntry<>(id, type);
        } catch (IndexOutOfBoundsException ignore) {
            return new AbstractMap.SimpleEntry<>(0L, 0);
        }
    }

    public static Transaction2 ofBankRecord(Element row) {
        return ofBankRecord(row, 5);
    }

    public static Transaction2 ofBankRecord(Element row, int resourceOffset) {
        Elements columns = row.getElementsByTag("td");

        int index = MathMan.parseInt(columns.get(0).text().replace(")", ""));

        Element noteElem = columns.get(1).select("img").first();
        String note = noteElem != null ? Parser.unescapeEntities(noteElem.attr("title"), true) : null;

        long date = TimeUtil.parseDate(TimeUtil.MMDDYYYY_HH_MM_A, columns.get(1).text());

        Map.Entry<Long, Integer> sender = idIsAlliance(columns.get(2));
        Map.Entry<Long, Integer> receiver = idIsAlliance(columns.get(3));
        Map.Entry<Long, Integer> banker = idIsAlliance(columns.get(4));

        ResourceType[] resources = {MONEY, FOOD, COAL, OIL, URANIUM, LEAD, IRON, BAUXITE, GASOLINE, MUNITIONS, STEEL, ALUMINUM};

        double[] amounts = ResourceType.getBuffer();

        for (int j = 0; j < resources.length; j++) {
            ResourceType resource = resources[j];
            double amt = MathMan.parseDouble(columns.get(resourceOffset + j).text());
            if (amt != 0) {
                amounts[resource.ordinal()] += amt;
            }
        }
        return new Transaction2(-1, date, sender.getKey(), sender.getValue(), receiver.getKey(), receiver.getValue(), banker.getKey().intValue(), note, amounts);
    }

    public Transaction2(BankRecord transfer) {
        tx_id = transfer.getTxId();
        tx_datetime = TimeUtil.parseDate(TimeUtil.YYYY_MM_DD_HH_MM_SS, transfer.getTxDatetime());
        sender_id = transfer.getSenderId();
        sender_type = transfer.getSenderType();
        receiver_id = transfer.getReceiverId();
        receiver_type = transfer.getReceiverType();
        banker_nation = transfer.getBankerNationId();
        note = transfer.getNote();
        resources = new double[ResourceType.values.length];

        resources = transfer.toMap();
    }

    public Transaction2(BankDB.TaxDeposit tax) {
        this.tx_id = tax.index;
        this.tx_datetime = tax.date;
        this.sender_id = tax.nationId;
        this.sender_type = 1;
        this.receiver_id = tax.allianceId;
        this.receiver_type = 2;
        this.banker_nation = tax.nationId;
        this.note = "#tax";
        this.resources = tax.resources;
    }

    public Transaction2(Offer offer) {
        tx_id = offer.getTradeId();
        tx_datetime = offer.getEpochms();
        sender_id = offer.getSeller();
        receiver_id = offer.getBuyer();
        receiver_type = 1;
        sender_type = 1;
        banker_nation = 0;
        resources = new double[ResourceType.values.length];
        resources[offer.getResource().ordinal()] += offer.getAmount();
        resources[0] -= offer.getTotal();
    }

    public Transaction2(Transfer transfer) {
        tx_id = 0;
        tx_datetime = transfer.getDate();
        sender_id = transfer.getSender();
        sender_type = transfer.isSenderAA() ? 2 : 1;
        receiver_id = transfer.getReceiver();
        receiver_type = transfer.isReceiverAA() ? 2 : 1;
        banker_nation = transfer.getBanker();
        note = transfer.getNote();
        resources = new double[ResourceType.values.length];

        resources[transfer.getRss().ordinal()] = transfer.getAmount();
    }

    public boolean combine(Transfer other) {
        Transaction2 tx = new Transaction2(other);
        if (tx.tx_datetime != tx_datetime) return false;
        if (tx.sender_id != sender_id) return false;
        if (tx.sender_type != sender_type) return false;
        if (tx.receiver_id != receiver_id) return false;
        if (tx.receiver_type != receiver_type) return false;
        if (tx.banker_nation != banker_nation) return false;
        if (!Objects.equals(tx.note,note)) return false;
        if (resources[other.getRss().ordinal()] != 0) return false;
        resources[other.getRss().ordinal()] += other.getAmount();
        return true;
    }

    public String createInsert(String table, boolean id) {
        StringBuilder sql = new StringBuilder("INSERT " + (id ? "OR REPLACE " : "") + "INTO `" + table + "` (" + (id ? "tx_id, " : "") + "tx_datetime, sender_id, sender_type, receiver_id, receiver_type, banker_nation_id, note");
        int fieldCount = id ? 8 : 7;
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            sql.append(", " + type.name());
            fieldCount++;
        }
        sql.append(") VALUES(" + StringMan.repeat("?,", fieldCount - 1) + "?" + ")");
        return sql.toString();
    }

    public void set(PreparedStatement stmt) throws SQLException {
        stmt.setInt(1, tx_id);
        stmt.setLong(2, tx_datetime);
        stmt.setLong(3, sender_id);
        stmt.setInt(4, sender_type);
        stmt.setLong(5, receiver_id);
        stmt.setInt(6, receiver_type);
        stmt.setInt(7, banker_nation);
        if (note == null) {
            stmt.setNull(8, Types.VARCHAR);
        }
        else {
            stmt.setString(8, note);
        }
        int i = 9;
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            stmt.setLong(i, (long) (resources[type.ordinal()] * 100d));
            i++;
        }
    }

    public void setNoID(PreparedStatement stmt) throws SQLException {
        stmt.setLong(1, tx_datetime);
        stmt.setLong(2, sender_id);
        stmt.setInt(3, sender_type);
        stmt.setLong(4, receiver_id);
        stmt.setInt(5, receiver_type);
        stmt.setInt(6, banker_nation);
        if (note == null) {
            stmt.setNull(7, Types.VARCHAR);
        } else {
            stmt.setString(7, note);
        }
        int i = 8;
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            stmt.setLong(i, (long) (resources[type.ordinal()] * 100d));
            i++;
        }
    }

    public long getDate() {
        return tx_datetime;
    }

    public long getReceiver() {
        return receiver_id;
    }

    public long getSender() {
        return sender_id;
    }

    public double convertedTotal() {
        return PnwUtil.convertedTotal(resources);
    }

    public String toSimpleString() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(tx_datetime), ZoneOffset.UTC).toLocalDate() +
                " | " + note +
                " | sender: " + PnwUtil.getName(sender_id, sender_type == 2) +
                " | receiver: " + PnwUtil.getName(receiver_id, receiver_type == 2) +
                " | banker: " + PnwUtil.getName(banker_nation, false) +
                " | " + PnwUtil.resourcesToString(resources);
    }

    @Override
    public String toString() {
        return toSimpleString();
    }

    public boolean isSenderGuild() {
        return sender_type == 3;
    }

    public boolean isReceiverGuild() {
        return receiver_type == 3;
    }

    public boolean isSenderAA() {
        return sender_type == 2;
    }

    public boolean isReceiverAA() {
        return receiver_type == 2;
    }

    public boolean isSenderNation() {
        return sender_type == 1;
    }

    public boolean isReceiverNation() {
        return receiver_type == 1;
    }

    public NationOrAllianceOrGuild getSenderObj() {
        return NationOrAllianceOrGuild.create(sender_id, sender_type);
    }

    public NationOrAllianceOrGuild getReceiverObj() {
        return NationOrAllianceOrGuild.create(receiver_id, receiver_type);
    }
}
