package link.locutus.discord.db.entities;

import com.google.common.hash.Hashing;
import com.politicsandwar.graphql.model.Bankrec;
import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import org.example.jooq.bank.tables.records.Transactions_2Record;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static link.locutus.discord.apiv1.enums.ResourceType.*;
import static link.locutus.discord.util.math.ArrayUtil.DOUBLE_ADD;

public class Transaction2 {
    private static final int NOTE_BUFFER_SIZE = 512;
    private static final int NOTE_DB_FORMAT_MAGIC = 0x54;
    private static final int NOTE_DB_FORMAT_VERSION = 1;

    private static boolean hasLegacyRootAccountTag(DepositType type) {
        return switch (type) {
            case DEPOSIT, TAX, LOAN, GRANT, IGNORE, TRADE -> true;
            default -> false;
        };
    }

    private static final ThreadLocal<BitBuffer> NOTE_BUFFER = ThreadLocal
            .withInitial(() -> new BitBuffer(NOTE_BUFFER_SIZE));
    private static final Pattern LOOT_CAPTURE = Pattern
            .compile("^(.+?) defeated (.+?)'s nation and captured(?:\\.|\\b).*$");
    private static final Pattern ALLIANCE_BANK_LOOT = Pattern.compile("of the alliance bank inventory\\.");

    public int original_id;

    public int tx_id;
    public long tx_datetime;
    public long sender_id;
    public int sender_type;
    public long receiver_id;
    public int receiver_type;
    public int banker_nation;
    public double[] resources;

    private final NoteState noteData;

    private Transaction2(int tx_id, long tx_datetime, long sender_id, int sender_type, long receiver_id,
            int receiver_type, int banker_nation, String note, double[] resources) {
        this.tx_id = tx_id;
        this.tx_datetime = tx_datetime;
        this.sender_id = sender_id;
        this.sender_type = sender_type;
        this.receiver_id = receiver_id;
        this.receiver_type = receiver_type;
        this.banker_nation = banker_nation;
        this.resources = resources;
        this.noteData = NoteState.fromRaw(note, tx_datetime, tx_id, receiver_type);
    }

    private Transaction2(int tx_id, long tx_datetime, long sender_id, int sender_type, long receiver_id,
            int receiver_type, int banker_nation, Map<DepositType, Object> parsed, boolean validHash,
            boolean isLootTransfer, double[] resources) {
        this.tx_id = tx_id;
        this.tx_datetime = tx_datetime;
        this.sender_id = sender_id;
        this.sender_type = sender_type;
        this.receiver_id = receiver_id;
        this.receiver_type = receiver_type;
        this.banker_nation = banker_nation;
        this.resources = resources;
        this.noteData = NoteState.fromParsed(parsed, validHash, isLootTransfer);
    }

    private Transaction2(BankRecord transfer) {
        tx_id = transfer.getTxId();
        tx_datetime = TimeUtil.parseDate(TimeUtil.YYYY_MM_DD_HH_MM_SS, transfer.getTxDatetime());
        sender_id = transfer.getSenderId();
        sender_type = transfer.getSenderType();
        receiver_id = transfer.getReceiverId();
        receiver_type = transfer.getReceiverType();
        banker_nation = transfer.getBankerNationId();
        noteData = NoteState.fromRaw(transfer.getNote(), tx_datetime, tx_id, receiver_type);
        resources = new double[ResourceType.values.length];
        resources = transfer.toMap();
    }

    private Transaction2(TaxDeposit tax) {
        this.tx_id = tax.index;
        this.tx_datetime = tax.date;
        this.sender_id = tax.nationId;
        this.sender_type = 1;
        this.receiver_id = tax.allianceId;
        this.receiver_type = 2;
        this.banker_nation = tax.nationId;
        this.noteData = NoteState.fromParsed(DepositType.TAX.toParsedNote(), false, false);
        this.resources = tax.resources;
    }

    private Transaction2(DBTrade offer) {
        tx_id = offer.getTradeId();
        tx_datetime = offer.getDate();
        sender_id = offer.getSeller();
        receiver_id = offer.getBuyer();
        receiver_type = 1;
        sender_type = 1;
        banker_nation = 0;
        noteData = NoteState.fromRaw(null, tx_datetime, tx_id, receiver_type);
        resources = new double[ResourceType.values.length];
        int ordinal = offer.getResource().ordinal();
        resources[ordinal] += offer.getQuantity();
        resources[0] -= offer.getTotal();
    }

    private Transaction2(Transfer transfer) {
        tx_id = 0;
        tx_datetime = transfer.getDate();
        sender_id = transfer.getSender();
        sender_type = transfer.isSenderAA() ? 2 : 1;
        receiver_id = transfer.getReceiver();
        receiver_type = transfer.isReceiverAA() ? 2 : 1;
        banker_nation = transfer.getBanker();
        noteData = NoteState.fromRaw(transfer.getNote(), tx_datetime, tx_id, receiver_type);
        resources = new double[ResourceType.values.length];

        resources[transfer.getRss().ordinal()] = transfer.getAmount();
    }

    private Transaction2(Transaction2 other) {
        this.original_id = other.original_id;
        this.tx_id = other.tx_id;
        this.tx_datetime = other.tx_datetime;
        this.sender_id = other.sender_id;
        this.sender_type = other.sender_type;
        this.receiver_id = other.receiver_id;
        this.receiver_type = other.receiver_type;
        this.banker_nation = other.banker_nation;
        this.resources = other.resources;
        this.noteData = other.noteData;
    }

    public static Transaction2 constructLegacy(int tx_id, long tx_datetime, long sender_id, int sender_type,
            long receiver_id, int receiver_type, int banker_nation, String note, double[] resources) {
        return new Transaction2(tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type,
                banker_nation, note, resources);
    }

    public static Transaction2 construct(int tx_id, long tx_datetime, long sender_id, int sender_type,
            long receiver_id, int receiver_type, int banker_nation, Map<DepositType, Object> parsed,
            boolean validHash, boolean isLootTransfer, double[] resources) {
        return new Transaction2(tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type,
                banker_nation, parsed, validHash, isLootTransfer, resources);
    }

    public static Transaction2 construct(int tx_id, long tx_datetime, long sender_id, int sender_type,
            long receiver_id, int receiver_type, int banker_nation, TransactionNote note,
            boolean validHash, boolean isLootTransfer, double[] resources) {
        return new Transaction2(tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type,
                banker_nation, note == null ? Collections.emptyMap() : note.asMap(), validHash, isLootTransfer,
                resources);
    }

    public static Transaction2 fromBankRecord(BankRecord transfer) {
        return new Transaction2(transfer);
    }

    public static Transaction2 fromTaxDeposit(TaxDeposit tax) {
        return new Transaction2(tax);
    }

    public static Transaction2 fromTrade(DBTrade offer) {
        return new Transaction2(offer);
    }

    public static Transaction2 fromTransfer(Transfer transfer) {
        return new Transaction2(transfer);
    }

    public static Transaction2 loadLegacy(ResultSet rs) throws SQLException {
        int tx_id = rs.getInt("tx_id");
        long tx_datetime = rs.getLong("tx_datetime");
        long sender_id = rs.getLong("sender_id");
        int sender_type = rs.getInt("sender_type");
        long receiver_id = rs.getLong("receiver_id");
        int receiver_type = rs.getInt("receiver_type");
        int banker_nation = rs.getInt("banker_nation_id");
        String note = rs.getString("note");
        double[] resources = new double[ResourceType.values.length];
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS)
                continue;
            resources[type.ordinal()] = ArrayUtil.fromCents(rs.getLong(type.name()));
        }
        Transaction2 tx = new Transaction2(tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type,
                banker_nation, note, resources);
        tx.original_id = tx_id;
        return tx;
    }

    public static Transaction2 load(ResultSet rs) throws SQLException {
        return load(rs, noteBuffer());
    }

    public static Transaction2 load(ResultSet rs, BitBuffer buffer) throws SQLException {
        int tx_id = rs.getInt("tx_id");
        long tx_datetime = rs.getLong("tx_datetime");
        long sender_key = rs.getLong("sender_key");
        long receiver_key = rs.getLong("receiver_key");
        int banker_nation = rs.getInt("banker_nation_id");
        byte[] data = rs.getBytes("note");
        return fromPayload(tx_id, tx_datetime,
                TransactionEndpointKey.idFromKey(sender_key), TransactionEndpointKey.typeFromKey(sender_key),
                TransactionEndpointKey.idFromKey(receiver_key), TransactionEndpointKey.typeFromKey(receiver_key),
                banker_nation, data, buffer);
    }

    public static Transaction2 loadSplit(ResultSet rs, BitBuffer buffer) throws SQLException {
        int tx_id = rs.getInt("tx_id");
        long tx_datetime = rs.getLong("tx_datetime");
        long sender_id = rs.getLong("sender_id");
        int sender_type = rs.getInt("sender_type");
        long receiver_id = rs.getLong("receiver_id");
        int receiver_type = rs.getInt("receiver_type");
        int banker_nation = rs.getInt("banker_nation_id");
        byte[] data = rs.getBytes("note");
        return fromPayload(tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type, banker_nation,
                data, buffer);
    }

    public static Transaction2 fromApiV3(Bankrec rec) {
        double[] resources = ResourceType.fromApiV3(rec, getBuffer());
        return constructLegacy(
                rec.getId(),
                rec.getDate().toEpochMilli(),
                rec.getSender_id(),
                rec.getSender_type(),
                rec.getReceiver_id(),
                rec.getReceiver_type(),
                rec.getBanker_id(),
                rec.getNote(),
                resources);
    }

    private static final Pattern MD5_HASH = Pattern.compile("#([a-fA-F0-9]{32})");

    public static boolean isValidHash(String note, int tx_id) {
        if (note == null || note.isEmpty()) {
            return false;
        }
        Matcher matcher = MD5_HASH.matcher(note);
        if (matcher.find()) {
            String md5Hash = matcher.group(1);
            String expected = Hashing.md5()
                    .hashString(Settings.INSTANCE.CONVERSION_SECRET + tx_id, StandardCharsets.UTF_8)
                    .toString();
            return md5Hash.equalsIgnoreCase(expected);
        }
        return false;
    }

    public static boolean isLootTransfer(String note) {
        return note != null && (LOOT_CAPTURE.matcher(note).matches() || isAllianceBankLoot(note));
    }

    public boolean isLootTransfer() {
        return noteData.isLootTransfer();
    }

    public static BitBuffer reusableNoteBuffer() {
        return noteBuffer();
    }

    public static TransactionNote noteOf(DepositType type) {
        return TransactionNote.of(type);
    }

    public static TransactionNote noteOf(Map<DepositType, Object> parsed) {
        return TransactionNote.of(parsed);
    }

    public static TransactionNote.Builder noteBuilder() {
        return TransactionNote.builder();
    }

    public static TransactionNote.Builder noteBuilder(Map<DepositType, Object> parsed) {
        return TransactionNote.builder(parsed);
    }

    public Map<DepositType, Object> getNoteMap() {
        return noteData.getParsed();
    }

    public TransactionNote getStructuredNote() {
        return noteData.getNote();
    }

    public TransactionNote.Builder editNote() {
        return getStructuredNote().toBuilder();
    }

    public Transaction2 withStructuredNote(TransactionNote note, boolean validHash) {
        Transaction2 copy = construct(tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type,
                banker_nation, note, validHash, isLootTransfer(), resources);
        copy.original_id = original_id;
        return copy;
    }

    public NoteState note() {
        return noteData;
    }

    public boolean hasNoteData() {
        return noteData.hasData();
    }

    public boolean hasNoteTag(DepositType type) {
        return noteData.hasTag(type);
    }

    public Object getNoteValue(DepositType type) {
        return noteData.get(type);
    }

    public long getTaggedAccountId() {
        return getTaggedAccountId(getNoteMap());
    }

    public String getLegacyNote() {
        return noteData.toLegacyString();
    }

    public byte[] getNoteBytes() {
        return PayloadState.toBytes(noteData, resources, noteBuffer());
    }

    public static int noteDbFormatMagic() {
        return NOTE_DB_FORMAT_MAGIC;
    }

    public static int noteDbFormatVersion() {
        return NOTE_DB_FORMAT_VERSION;
    }

    public String getNoteSummary() {
        return noteData.toSummaryString();
    }

    @Deprecated
    public static Map<DepositType, Object> parseTransferHashNotes(String note, long date) {
        return TransactionNote.parseLegacy(note, date).asMap();
    }

    public static Map<DepositType, Object> parseTransferHashNotes(DepositType note, long date) {
        if (note == null) {
            return Collections.emptyMap();
        }
        return note.toParsedNote();
    }

    public boolean isValidHash() {
        return noteData.isValidHash();
    }

    public void setValidHash(boolean validHash) {
        this.noteData.setValidHash(validHash);
    }

    public boolean isSelfWithdrawal(int nationId) {
        if (this.isSenderAA()) {
            Map<DepositType, Object> noteMap = getNoteMap();
            if (noteMap.containsKey(DepositType.DEPOSIT)) {
                Object banker = noteMap.get(DepositType.BANKER);
                if (banker == null) banker = noteMap.get(DepositType.RECEIVER_ID);
                if (banker instanceof Number n) {
                    return n.intValue() == nationId;
                }
            }
        }
        return false;
    }

    public long getAccountId(Set<Integer> offshoreAlliances, boolean ignoreIgnore) {
        Map<DepositType, Object> notes2 = getNoteMap();
        if (!notes2.isEmpty()) {
            if (ignoreIgnore && notes2.containsKey(DepositType.IGNORE))
                return 0;
            long taggedAccountId = getTaggedAccountId(notes2);
            if (taggedAccountId != 0) {
                return taggedAccountId;
            }
        }
        if (!isReceiverAA()) {
            return sender_id;
        }
        if (!isSenderAA()) {
            return receiver_id;
        }
        if (offshoreAlliances.contains((int) receiver_id)) {
            return sender_id;
        }
        if (offshoreAlliances.contains((int) sender_id)) {
            return receiver_id;
        }
        return 0;
    }

    private static long getTaggedAccountId(Map<DepositType, Object> notes) {
        Object allianceAccount = notes.get(DepositType.ALLIANCE);
        Object guildAccount = notes.get(DepositType.GUILD);
        if (allianceAccount != null && guildAccount != null) {
            return 0;
        }
        if (allianceAccount instanceof Number n) {
            return n.longValue();
        }
        if (guildAccount instanceof Number n) {
            return n.longValue();
        }
        for (Map.Entry<DepositType, Object> entry : notes.entrySet()) {
            DepositType type = entry.getKey();
            Object value = entry.getValue();
            // Preserve historical root-level account ownership markers, but keep the
            // supported tag set explicit instead of inferring it from generic note shape.
            if (hasLegacyRootAccountTag(type) && value instanceof Number n) {
                return n.longValue();
            }
        }
        return 0;
    }

    public boolean isTrackedForGuild(GuildDB db, Set<Integer> aaIds, Set<Integer> offshoreAAs) {
        if (aaIds.contains((int) sender_id) || aaIds.contains((int) receiver_id))
            return true;
        long accountId = getAccountId(offshoreAAs, false);
        return (accountId == 0 || accountId == db.getIdLong() || aaIds.contains((int) accountId));
    }

    public static Transaction2 fromTX2Table(Transactions_2Record record) {
        return fromTX2Table(record, noteBuffer());
    }

    public static Transaction2 fromTX2Table(Transactions_2Record record, BitBuffer buffer) {
        long senderKey = record.getSenderKey();
        long receiverKey = record.getReceiverKey();
        return fromPayload(
                record.getTxId(),
                record.getTxDatetime(),
                TransactionEndpointKey.idFromKey(senderKey),
                TransactionEndpointKey.typeFromKey(senderKey),
                TransactionEndpointKey.idFromKey(receiverKey),
                TransactionEndpointKey.typeFromKey(receiverKey),
                record.getBankerNationId(),
                record.getNote(),
                buffer);
    }

    private static Transaction2 fromPayload(int tx_id, long tx_datetime, long sender_id, int sender_type,
            long receiver_id, int receiver_type, int banker_nation, byte[] data, BitBuffer buffer) {
        PayloadState payload = PayloadState.fromBytes(data, buffer);
        Transaction2 tx = construct(
                tx_id,
                tx_datetime,
                sender_id,
                sender_type,
                receiver_id,
                receiver_type,
                banker_nation,
                payload.noteState().getParsed(),
                payload.noteState().isValidHash(),
                payload.noteState().isLootTransfer(),
                payload.resources());
        tx.original_id = tx_id;
        return tx;
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
            return new KeyValue<>(id, type);
        } catch (IndexOutOfBoundsException ignore) {
            return new KeyValue<>(0L, 0);
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

        ResourceType[] resources = { MONEY, FOOD, COAL, OIL, URANIUM, LEAD, IRON, BAUXITE, GASOLINE, MUNITIONS, STEEL,
                ALUMINUM };

        double[] amounts = ResourceType.getBuffer();

        for (int j = 0; j < resources.length; j++) {
            ResourceType resource = resources[j];
            double amt = MathMan.parseDouble(columns.get(resourceOffset + j).text());
            if (amt != 0) {
                amounts[resource.ordinal()] = DOUBLE_ADD.applyAsDouble(amounts[resource.ordinal()], amt);
            }
        }
        return constructLegacy(-1, date, sender.getKey(), sender.getValue(), receiver.getKey(), receiver.getValue(),
                banker.getKey().intValue(), note, amounts);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Transaction2 tx) {
            return tx.tx_id == tx_id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return tx_id;
    }

    public String createInsert(String table, boolean id, boolean ignore) {
        StringBuilder sql = new StringBuilder("INSERT " + (id ? "OR " + (ignore ? "IGNORE" : "REPLACE") + " " : "")
                + "INTO `" + table + "` (" + (id ? "tx_id, " : "")
                + "tx_datetime, sender_key, receiver_key, banker_nation_id, note");
        int fieldCount = id ? 6 : 5;
        sql.append(") VALUES(" + StringMan.repeat("?,", fieldCount - 1) + "?" + ")");
        return sql.toString();
    }

    public void set(PreparedStatement stmt) throws SQLException {
        stmt.setInt(1, tx_id);
        stmt.setLong(2, tx_datetime);
        stmt.setLong(3, getSenderKey());
        stmt.setLong(4, getReceiverKey());
        stmt.setInt(5, banker_nation);
        byte[] noteBytes = getNoteBytes();
        if (noteBytes == null) {
            stmt.setNull(6, Types.BLOB);
        } else {
            stmt.setBytes(6, noteBytes);
        }
    }

    public void setNoID(PreparedStatement stmt) throws SQLException {
        stmt.setLong(1, tx_datetime);
        stmt.setLong(2, getSenderKey());
        stmt.setLong(3, getReceiverKey());
        stmt.setInt(4, banker_nation);
        byte[] noteBytes = getNoteBytes();
        if (noteBytes == null) {
            stmt.setNull(5, Types.BLOB);
        } else {
            stmt.setBytes(5, noteBytes);
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

    public boolean matchesSender(long endpointId, int endpointType) {
        return TransactionEndpointKey.matches(sender_id, sender_type, endpointId, endpointType);
    }

    public boolean matchesReceiver(long endpointId, int endpointType) {
        return TransactionEndpointKey.matches(receiver_id, receiver_type, endpointId, endpointType);
    }

    public boolean matchesEndpoint(long endpointId, int endpointType) {
        return matchesSender(endpointId, endpointType) || matchesReceiver(endpointId, endpointType);
    }

    public int endpointDirection(long endpointId, int endpointType) {
        return TransactionEndpointKey.direction(sender_id, sender_type, receiver_id, receiver_type, endpointId,
                endpointType);
    }

    public long getSenderKey() {
        return TransactionEndpointKey.encode(sender_id, sender_type);
    }

    public long getReceiverKey() {
        return TransactionEndpointKey.encode(receiver_id, receiver_type);
    }

    public double convertedTotal() {
        return ResourceType.convertedTotal(resources);
    }

    public String toSimpleString() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(tx_datetime), ZoneOffset.UTC).toLocalDate() +
                " | " + getNoteSummary() +
                " | sender: " + PW.getName(sender_id, sender_type == 2) +
                " | receiver: " + PW.getName(receiver_id, receiver_type == 2) +
                " | banker: " + PW.getName(banker_nation, false) +
                " | " + ResourceType.toString(resources);
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

    public void set(Transactions_2Record record) {
        record.setTxId(tx_id);
        record.setTxDatetime(tx_datetime);
        record.setSenderKey(getSenderKey());
        record.setReceiverKey(getReceiverKey());
        record.setBankerNationId(banker_nation);
        record.setNote(getNoteBytes());

    }

    public boolean isInternal() {
        return tx_id == -1 && original_id != -1;
    }

    public static final class NoteState {
        private final TransactionNote note;
        private boolean validHash;
        private final boolean lootTransfer;

        private NoteState(TransactionNote note, boolean validHash, boolean lootTransfer) {
            this.note = note == null ? TransactionNote.empty() : note;
            this.validHash = validHash;
            this.lootTransfer = lootTransfer;
        }

        private static NoteState fromRaw(String rawNote, long date, int txId, int receiverType) {
            TransactionNote note = TransactionNote.parseLegacy(rawNote, date);
            boolean validHash = rawNote != null && note.hasTag(DepositType.CASH) && receiverType != 1
                    && Transaction2.isValidHash(rawNote, txId);
            return new NoteState(note, validHash, Transaction2.isLootTransfer(rawNote));
        }

        private static NoteState fromParsed(Map<DepositType, Object> parsed, boolean validHash, boolean lootTransfer) {
            return new NoteState(TransactionNote.of(parsed), validHash, lootTransfer);
        }

        public boolean hasData() {
            return lootTransfer || !note.isEmpty();
        }

        public boolean hasTag(DepositType type) {
            return note.hasTag(type);
        }

        public Object get(DepositType type) {
            return note.get(type);
        }

        public Map<DepositType, Object> getParsed() {
            return note.asMap();
        }

        public TransactionNote getNote() {
            return note;
        }

        public boolean isValidHash() {
            return validHash;
        }

        public void setValidHash(boolean validHash) {
            this.validHash = validHash;
        }

        public boolean isLootTransfer() {
            return lootTransfer;
        }

        public String toLegacyString() {
            return note.toLegacyString();
        }

        public String toSummaryString() {
            if (!note.isEmpty()) {
                return note.toDisplayString();
            }
            if (lootTransfer) {
                return "[loot-transfer]";
            }
            return null;
        }
    }

    private static final class PayloadState {
        private final NoteState noteState;
        private final double[] resources;

        private PayloadState(NoteState noteState, double[] resources) {
            this.noteState = noteState;
            this.resources = resources == null ? ResourceType.getBuffer() : resources;
        }

        private static PayloadState fromBytes(byte[] bytes, BitBuffer buffer) {
            if (bytes == null || bytes.length == 0) {
                return new PayloadState(NoteState.fromParsed(Collections.emptyMap(), false, false), ResourceType.getBuffer());
            }
            buffer.setBytes(bytes);
            boolean validHash = buffer.readBit();
            boolean lootTransfer = buffer.readBit();
            double[] resources = readResources(buffer);
            TransactionNote note = TransactionNote.of(DepositType.readMap(buffer));
            return new PayloadState(new NoteState(note, validHash, lootTransfer), resources);
        }

        private static byte[] toBytes(NoteState noteState, double[] resources, BitBuffer buffer) {
            if ((noteState == null || !noteState.hasData()) && isEmptyResources(resources)) {
                return null;
            }
            buffer.reset();
            buffer.writeBit(noteState != null && noteState.isValidHash());
            buffer.writeBit(noteState != null && noteState.isLootTransfer());
            writeResources(buffer, resources);
            DepositType.serialize(noteState == null ? Collections.emptyMap() : noteState.getParsed(), buffer);
            return buffer.getWrittenBytes();
        }

        private NoteState noteState() {
            return noteState;
        }

        private double[] resources() {
            return resources;
        }
    }

    private static boolean isAllianceBankLoot(String note) {
        return note != null && ALLIANCE_BANK_LOOT.matcher(note).find();
    }

    private static boolean isEmptyResources(double[] resources) {
        if (resources == null) {
            return true;
        }
        for (ResourceType type : ResourceType.values) {
            if (type != ResourceType.CREDITS && resources[type.ordinal()] != 0d) {
                return false;
            }
        }
        return true;
    }

    private static void writeResources(BitBuffer buffer, double[] resources) {
        int count = 0;
        if (resources != null) {
            for (ResourceType type : ResourceType.values) {
                if (type != ResourceType.CREDITS && resources[type.ordinal()] != 0d) {
                    count++;
                }
            }
        }
        buffer.writeVarInt(count);
        if (resources == null) {
            return;
        }
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) {
                continue;
            }
            long amountCents = ArrayUtil.toCents(resources[type.ordinal()]);
            if (amountCents == 0L) {
                continue;
            }
            buffer.writeVarInt(type.ordinal());
            buffer.writeVarLong(zigZagEncode(amountCents));
        }
    }

    private static double[] readResources(BitBuffer buffer) {
        double[] resources = ResourceType.getBuffer();
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            int ordinal = buffer.readVarInt();
            long amountCents = zigZagDecode(buffer.readVarLong());
            resources[ordinal] = ArrayUtil.fromCents(amountCents);
        }
        return resources;
    }

    private static long zigZagEncode(long value) {
        return (value << 1) ^ (value >> 63);
    }

    private static long zigZagDecode(long value) {
        return (value >>> 1) ^ -(value & 1L);
    }

    private static BitBuffer noteBuffer() {
        return NOTE_BUFFER.get().reset();
    }
}
