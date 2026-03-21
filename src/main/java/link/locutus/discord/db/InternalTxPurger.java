package link.locutus.discord.db;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.TransactionNote;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;

public final class InternalTxPurger {

    private InternalTxPurger() {
    }

    private static final long NO_EPOCH = Long.MAX_VALUE;
    private static final long NO_TX_ID = Long.MIN_VALUE;

    /**
     * Extra safety: only purge rows where the banker nation is one endpoint.
     * This matches the old behavior, but with a sender/receiver type check to avoid
     * id collisions.
     */
    private static final boolean REQUIRE_BANKER_ENDPOINT = false;

    /**
     * Resource columns in INTERNAL_TRANSACTIONS2: all ResourceType except CREDITS.
     */
    private static final ResourceType[] RESOURCE_COLS = buildResourceCols();

    private static ResourceType[] buildResourceCols() {
        int n = 0;
        for (ResourceType t : ResourceType.values) {
            if (t != ResourceType.CREDITS)
                n++;
        }
        ResourceType[] cols = new ResourceType[n];
        int i = 0;
        for (ResourceType t : ResourceType.values) {
            if (t != ResourceType.CREDITS)
                cols[i++] = t;
        }
        return cols;
    }

    private static final class ParsedExpireDecay {
        final long expireEpoch; // NO_EPOCH if missing/unparseable
        final long decayEpoch; // NO_EPOCH if missing/unparseable
        final String baseNote; // lowercased note, expire/decay tags removed, whitespace normalized

        ParsedExpireDecay(long expireEpoch, long decayEpoch, String baseNote) {
            this.expireEpoch = expireEpoch;
            this.decayEpoch = decayEpoch;
            this.baseNote = baseNote;
        }
    }

    /**
     * Directionless key (lo/hi endpoints normalized), plus
     * banker/epochs/baseNote/resources.
     *
     * NOTE: resources are stored in a canonical orientation (flow from lo -> hi).
     * This allows matching BOTH:
     * - opposite endpoints with identical amounts, AND
     * - same endpoints with opposite-signed amounts
     * by simply pairing resource vectors that are exact negations.
     */
    private static final class TxKey {
        final long loId;
        final int loType;
        final long hiId;
        final int hiType;
        final int bankerNationId;
        final long expireEpoch;
        final long decayEpoch;
        final String baseNote;
        final long[] resources; // fixed order: RESOURCE_COLS (canonical lo->hi orientation; may be negative)
        final int hash;

        TxKey(long loId, int loType,
                long hiId, int hiType,
                int bankerNationId,
                long expireEpoch, long decayEpoch,
                String baseNote,
                long[] resources) {

            this.loId = loId;
            this.loType = loType;
            this.hiId = hiId;
            this.hiType = hiType;
            this.bankerNationId = bankerNationId;
            this.expireEpoch = expireEpoch;
            this.decayEpoch = decayEpoch;
            this.baseNote = baseNote;
            this.resources = resources;

            int h = 1;
            h = 31 * h + Long.hashCode(loId);
            h = 31 * h + loType;
            h = 31 * h + Long.hashCode(hiId);
            h = 31 * h + hiType;
            h = 31 * h + bankerNationId;
            h = 31 * h + Long.hashCode(expireEpoch);
            h = 31 * h + Long.hashCode(decayEpoch);
            h = 31 * h + baseNote.hashCode();
            h = 31 * h + Arrays.hashCode(resources);
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof TxKey k))
                return false;
            return loId == k.loId
                    && loType == k.loType
                    && hiId == k.hiId
                    && hiType == k.hiType
                    && bankerNationId == k.bankerNationId
                    && expireEpoch == k.expireEpoch
                    && decayEpoch == k.decayEpoch
                    && baseNote.equals(k.baseNote)
                    && Arrays.equals(resources, k.resources);
        }
    }

    /**
     * Holds unmatched tx_ids for one key.
     * Optimized for the common case: 0 or 1 pending tx per key.
     */
    private static final class UnpairedTxIds {
        long one = NO_TX_ID;
        LongArrayList extra;

        boolean hasAny() {
            return one != NO_TX_ID;
        }

        void push(long txId) {
            if (one == NO_TX_ID) {
                one = txId;
                return;
            }
            if (extra == null)
                extra = new LongArrayList();
            extra.add(one);
            one = txId;
        }

        long pop() {
            if (one == NO_TX_ID)
                return NO_TX_ID;
            long out = one;
            if (extra != null && !extra.isEmpty()) {
                one = extra.removeLong(extra.size() - 1);
            } else {
                one = NO_TX_ID;
            }
            return out;
        }

        boolean isEmpty() {
            return one == NO_TX_ID && (extra == null || extra.isEmpty());
        }
    }

    /**
     * Purges ALL expire/decay transactions that already have a cancelling
     * counterpart
     * (same tx_datetime, same endpoints, same banker_nation_id, same base note,
     * same parsed expire/decay epochs, and resource vectors that are exact
     * negations
     * after canonicalization).
     *
     * This supports BOTH encodings of a cancelling pair:
     * - opposite sender/receiver with identical amounts
     * - same sender/receiver with opposite-signed amounts
     *
     * Optimization/safety: matching is intentionally restricted to transfers
     * sharing the exact same tx_datetime.
     * The query is ordered by tx_datetime and the in-memory match map is cleared
     * when the timestamp changes.
     *
     * @param conn    JDBC connection
     * @param apply   if false = dry run (counts only)
     * @param details optional output
     * @return number of rows deleted (or would be deleted if apply=false)
     */
    public static int purgeAllOppositeExpireDecayPairs(Connection conn, boolean apply, StringBuilder details)
            throws IOException {

        StringBuilder sql = new StringBuilder(256);
        sql.append("SELECT * FROM INTERNAL_TRANSACTIONS2")
                .append(" WHERE sender_key != receiver_key");

        if (REQUIRE_BANKER_ENDPOINT) {
            sql.append("   AND ((sender_key = ((CAST(banker_nation_id AS BIGINT) << 3) | 1))")
                    .append("     OR (receiver_key = ((CAST(banker_nation_id AS BIGINT) << 3) | 1)))");
        }

        sql.append(" ORDER BY tx_datetime, tx_id");

        Object2ObjectOpenHashMap<TxKey, UnpairedTxIds> unpaired = new Object2ObjectOpenHashMap<>();
        LongArrayList toDelete = apply ? new LongArrayList() : null;

        long scanned = 0;
        long considered = 0;
        long matchedPairs = 0;
        long timestampGroups = 0;

        long currentTs = Long.MIN_VALUE;

        try (PreparedStatement ps = conn.prepareStatement(sql.toString());
                ResultSet rs = ps.executeQuery()) {
            BitBuffer noteBuffer = Transaction2.reusableNoteBuffer();

            while (rs.next()) {
                scanned++;

                Transaction2 tx = Transaction2.load(rs, noteBuffer);
                long txId = tx.tx_id;
                long txDatetime = tx.tx_datetime;
                long senderId = tx.sender_id;
                int senderType = tx.sender_type;
                long receiverId = tx.receiver_id;
                int receiverType = tx.receiver_type;
                int bankerNationId = tx.banker_nation;

                if (txDatetime != currentTs) {
                    unpaired.clear();
                    currentTs = txDatetime;
                    timestampGroups++;
                }

                if (!tx.hasNoteTag(DepositType.EXPIRE) && !tx.hasNoteTag(DepositType.DECAY)) {
                    continue;
                }

                ParsedExpireDecay parsed = parseExpireDecay(tx.getStructuredNote());
                if (parsed == null) {
                    continue;
                }
                considered++;

                long[] resources = new long[RESOURCE_COLS.length];
                for (int i = 0; i < RESOURCE_COLS.length; i++) {
                    resources[i] = ArrayUtil.toCents(tx.resources[RESOURCE_COLS[i].ordinal()]);
                }

                // Normalize endpoints (directionless key)
                int cmp = cmpParty(senderId, senderType, receiverId, receiverType);
                final long loId;
                final int loType;
                final long hiId;
                final int hiType;
                final boolean senderIsLo;
                if (cmp <= 0) {
                    loId = senderId;
                    loType = senderType;
                    hiId = receiverId;
                    hiType = receiverType;
                    senderIsLo = true;
                } else {
                    loId = receiverId;
                    loType = receiverType;
                    hiId = senderId;
                    hiType = senderType;
                    senderIsLo = false;
                }

                // Canonicalize to "flow from lo -> hi"
                if (!senderIsLo) {
                    negateInPlace(resources);
                }

                // Look for an existing tx with the exact opposite canonical resource vector.
                // (This covers both opposite endpoints and same endpoints-but-negated amounts.)
                negateInPlace(resources); // now = opposite vector
                TxKey oppositeKey = new TxKey(
                        loId, loType,
                        hiId, hiType,
                        bankerNationId,
                        parsed.expireEpoch,
                        parsed.decayEpoch,
                        parsed.baseNote,
                        resources);

                UnpairedTxIds oppositeBucket = unpaired.get(oppositeKey);
                if (oppositeBucket != null && oppositeBucket.hasAny()) {
                    long other = oppositeBucket.pop();
                    matchedPairs++;
                    if (apply) {
                        toDelete.add(txId);
                        toDelete.add(other);
                    }
                    if (oppositeBucket.isEmpty()) {
                        // IMPORTANT: must remove while oppositeKey still matches (resources currently
                        // negated)
                        unpaired.remove(oppositeKey);
                    }
                    // restore (not strictly required since we don't store current after a match)
                    negateInPlace(resources);
                } else {
                    // restore, then store current as unpaired
                    negateInPlace(resources);

                    TxKey key = new TxKey(
                            loId, loType,
                            hiId, hiType,
                            bankerNationId,
                            parsed.expireEpoch,
                            parsed.decayEpoch,
                            parsed.baseNote,
                            resources);

                    UnpairedTxIds bucket = unpaired.get(key);
                    if (bucket == null) {
                        bucket = new UnpairedTxIds();
                        unpaired.put(key, bucket);
                    }
                    bucket.push(txId);
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed scanning INTERNAL_TRANSACTIONS2", e);
        }

        long rowsMatched = matchedPairs * 2L;

        if (details != null) {
            details.append("Scanned ").append(scanned).append(" rows; considered ").append(considered)
                    .append(" expire/decay candidates; processed ").append(timestampGroups)
                    .append(" distinct tx_datetime values; matched ").append(matchedPairs)
                    .append(" cancelling pairs (").append(rowsMatched).append(" rows).\n");
        }

        if (matchedPairs == 0) {
            return 0;
        }

        if (!apply) {
            if (details != null)
                details.append("Dry-run: nothing deleted.\n");
            return rowsMatched > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rowsMatched;
        }

        int deleted = deleteByTxId(conn, toDelete);

        if (details != null) {
            details.append("Deleted ").append(deleted).append(" rows.\n");
        }

        return deleted;
    }

    private static int deleteByTxId(Connection conn, LongArrayList txIds) throws IOException {
        if (txIds == null || txIds.isEmpty())
            return 0;

        final int CHUNK = 900; // keep under SQLite bind limit (999)
        int totalDeleted = 0;

        try {
            for (int off = 0; off < txIds.size(); off += CHUNK) {
                int len = Math.min(CHUNK, txIds.size() - off);
                String sql = "DELETE FROM INTERNAL_TRANSACTIONS2 WHERE tx_id IN (" + placeholders(len) + ")";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < len; i++) {
                        ps.setLong(i + 1, txIds.getLong(off + i));
                    }
                    totalDeleted += ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed deleting INTERNAL_TRANSACTIONS2 rows", e);
        }

        return totalDeleted;
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder(n * 2 - 1);
        for (int i = 0; i < n; i++) {
            if (i > 0)
                sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    private static int cmpParty(long id1, int type1, long id2, int type2) {
        int c = Long.compare(id1, id2);
        if (c != 0)
            return c;
        return Integer.compare(type1, type2);
    }

    private static void negateInPlace(long[] v) {
        for (int i = 0; i < v.length; i++) {
            v[i] = -v[i];
        }
    }

    private static ParsedExpireDecay parseExpireDecay(TransactionNote note) {
        if (note == null || note.isEmpty())
            return null;

        long expire = note.get(DepositType.EXPIRE) instanceof Number n ? normalizeEpochMillis(n.longValue()) : NO_EPOCH;
        long decay = note.get(DepositType.DECAY) instanceof Number n ? normalizeEpochMillis(n.longValue()) : NO_EPOCH;

        if (expire == NO_EPOCH && decay == NO_EPOCH)
            return null;

        String baseNote = note.without(DepositType.EXPIRE, DepositType.DECAY)
                .toLegacyString();
        return new ParsedExpireDecay(expire, decay, baseNote);
    }

    private static String normalizeSpaces(CharSequence s) {
        int len = s.length();
        StringBuilder out = new StringBuilder(len);

        boolean wroteAny = false;
        boolean inWs = false;

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                if (wroteAny)
                    inWs = true;
                continue;
            }
            if (inWs) {
                out.append(' ');
                inWs = false;
            }
            out.append(c);
            wroteAny = true;
        }

        return out.toString();
    }

    private static long normalizeEpochMillis(long v) {
        // If it looks like seconds, convert to millis.
        // (Epoch millis ~ 13 digits; seconds ~ 10 digits.)
        if (v > 0 && v < 100_000_000_000L)
            return v * 1000L;
        return v;
    }
}