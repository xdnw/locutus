package link.locutus.discord.db;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;

public final class InternalTxPurger {

    private InternalTxPurger() {}

    private static final long NO_EPOCH = Long.MAX_VALUE;
    private static final long NO_TX_ID = Long.MIN_VALUE;

    /**
     * Extra safety: only purge rows where the banker nation is one endpoint.
     * This matches the old behavior, but with a sender/receiver type check to avoid id collisions.
     */
    private static final boolean REQUIRE_BANKER_ENDPOINT = false;

    /** Resource columns in INTERNAL_TRANSACTIONS2: all ResourceType except CREDITS. */
    private static final ResourceType[] RESOURCE_COLS = buildResourceCols();

    private static ResourceType[] buildResourceCols() {
        int n = 0;
        for (ResourceType t : ResourceType.values) {
            if (t != ResourceType.CREDITS) n++;
        }
        ResourceType[] cols = new ResourceType[n];
        int i = 0;
        for (ResourceType t : ResourceType.values) {
            if (t != ResourceType.CREDITS) cols[i++] = t;
        }
        return cols;
    }

    private static final class ParsedExpireDecay {
        final long expireEpoch; // NO_EPOCH if missing/unparseable
        final long decayEpoch;  // NO_EPOCH if missing/unparseable
        final String baseNote;  // lowercased note, expire/decay tags removed, whitespace normalized

        ParsedExpireDecay(long expireEpoch, long decayEpoch, String baseNote) {
            this.expireEpoch = expireEpoch;
            this.decayEpoch = decayEpoch;
            this.baseNote = baseNote;
        }
    }

    /**
     * Directionless key (lo/hi endpoints normalized), plus banker/epochs/baseNote/resources.
     *
     * NOTE: resources are stored in a canonical orientation (flow from lo -> hi).
     * This allows matching BOTH:
     *   - opposite endpoints with identical amounts, AND
     *   - same endpoints with opposite-signed amounts
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

        @Override public int hashCode() { return hash; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TxKey k)) return false;
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

        boolean hasAny() { return one != NO_TX_ID; }

        void push(long txId) {
            if (one == NO_TX_ID) {
                one = txId;
                return;
            }
            if (extra == null) extra = new LongArrayList();
            extra.add(one);
            one = txId;
        }

        long pop() {
            if (one == NO_TX_ID) return NO_TX_ID;
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
     * Purges ALL expire/decay transactions that already have a cancelling counterpart
     * (same tx_datetime, same endpoints, same banker_nation_id, same base note,
     * same parsed expire/decay epochs, and resource vectors that are exact negations
     * after canonicalization).
     *
     * This supports BOTH encodings of a cancelling pair:
     *   - opposite sender/receiver with identical amounts
     *   - same sender/receiver with opposite-signed amounts
     *
     * Optimization/safety: matching is intentionally restricted to transfers sharing the exact same tx_datetime.
     * The query is ordered by tx_datetime and the in-memory match map is cleared when the timestamp changes.
     *
     * @param conn    JDBC connection
     * @param apply   if false = dry run (counts only)
     * @param details optional output
     * @return number of rows deleted (or would be deleted if apply=false)
     */
    public static int purgeAllOppositeExpireDecayPairs(Connection conn, boolean apply, StringBuilder details)
            throws IOException {

        StringBuilder sql = new StringBuilder(256);
        sql.append("SELECT tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type, banker_nation_id, note");
        for (ResourceType t : RESOURCE_COLS) {
            sql.append(", ").append(t.name());
        }
        sql.append(" FROM INTERNAL_TRANSACTIONS2")
                .append(" WHERE note IS NOT NULL")
                // do not exclude nation<->alliance where ids happen to be equal
                .append("   AND NOT (sender_id = receiver_id AND sender_type = receiver_type)")
                .append("   AND (instr(lower(note), '#expire') > 0 OR instr(lower(note), '#decay') > 0)");

        if (REQUIRE_BANKER_ENDPOINT) {
            sql.append("   AND ((sender_type = 1 AND sender_id = banker_nation_id)")
                    .append("     OR (receiver_type = 1 AND receiver_id = banker_nation_id))");
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

            while (rs.next()) {
                scanned++;

                int c = 1;
                long txId = rs.getLong(c++);
                long txDatetime = rs.getLong(c++);

                long senderId = rs.getLong(c++);
                int senderType = rs.getInt(c++);

                long receiverId = rs.getLong(c++);
                int receiverType = rs.getInt(c++);

                int bankerNationId = rs.getInt(c++);
                String note = rs.getString(c++);

                if (txDatetime != currentTs) {
                    unpaired.clear();
                    currentTs = txDatetime;
                    timestampGroups++;
                }

                ParsedExpireDecay parsed = parseExpireDecay(note, txDatetime);
                if (parsed == null) {
                    // Skip reading resource columns for non-parseable rows.
                    continue;
                }
                considered++;

                long[] resources = new long[RESOURCE_COLS.length];
                for (int i = 0; i < RESOURCE_COLS.length; i++) {
                    resources[i] = rs.getLong(c++);
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
                        resources
                );

                UnpairedTxIds oppositeBucket = unpaired.get(oppositeKey);
                if (oppositeBucket != null && oppositeBucket.hasAny()) {
                    long other = oppositeBucket.pop();
                    matchedPairs++;
                    if (apply) {
                        toDelete.add(txId);
                        toDelete.add(other);
                    }
                    if (oppositeBucket.isEmpty()) {
                        // IMPORTANT: must remove while oppositeKey still matches (resources currently negated)
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
                            resources
                    );

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
            if (details != null) details.append("Dry-run: nothing deleted.\n");
            return rowsMatched > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rowsMatched;
        }

        int deleted = deleteByTxId(conn, toDelete);

        if (details != null) {
            details.append("Deleted ").append(deleted).append(" rows.\n");
        }

        return deleted;
    }

    private static int deleteByTxId(Connection conn, LongArrayList txIds) throws IOException {
        if (txIds == null || txIds.isEmpty()) return 0;

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
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    private static int cmpParty(long id1, int type1, long id2, int type2) {
        int c = Long.compare(id1, id2);
        if (c != 0) return c;
        return Integer.compare(type1, type2);
    }

    private static void negateInPlace(long[] v) {
        for (int i = 0; i < v.length; i++) {
            v[i] = -v[i];
        }
    }

    /**
     * Parses #expire / #decay tags (case-insensitive) from the note.
     * Returns null if neither tag can be resolved to an epoch.
     *
     * baseNote = lowercased original note, with expire/decay tags+values removed, whitespace normalized.
     */
    private static ParsedExpireDecay parseExpireDecay(String note, long txDatetime) {
        if (note == null || note.isEmpty()) return null;

        String lower = note.toLowerCase(Locale.ROOT);

        long expire = NO_EPOCH;
        long decay = NO_EPOCH;

        StringBuilder base = new StringBuilder(lower.length());
        int i = 0;
        final int len = lower.length();

        while (i < len) {
            char ch = lower.charAt(i);

            if (ch != '#') {
                base.append(ch);
                i++;
                continue;
            }

            // Scan tag name: "#<non-ws non-# non-=>"
            int tagStart = i;
            int j = i + 1;
            while (j < len) {
                char cj = lower.charAt(j);
                if (cj == '=' || cj == '#' || Character.isWhitespace(cj)) break;
                j++;
            }

            String tag = lower.substring(tagStart, j);
            boolean isExpire = tag.equals("#expire");
            boolean isDecay = tag.equals("#decay");

            if (!isExpire && !isDecay) {
                // Not our tag: keep as-is (continue char-by-char to preserve text).
                base.append('#');
                i++;
                continue;
            }

            // Skip the tag itself (remove it from base note)
            i = j;

            // Optional separator (= or whitespace) + optional value token
            int k = i;

            boolean hasSeparator = false;
            if (k < len && lower.charAt(k) == '=') {
                hasSeparator = true;
                k++;
            } else if (k < len && Character.isWhitespace(lower.charAt(k))) {
                hasSeparator = true;
                while (k < len && Character.isWhitespace(lower.charAt(k))) k++;
            }

            String rawValue = null;
            if (hasSeparator) {
                int valueStart = k;
                int valueEnd = valueStart;
                while (valueEnd < len) {
                    char cv = lower.charAt(valueEnd);
                    if (cv == '#' || Character.isWhitespace(cv)) break;
                    valueEnd++;
                }
                rawValue = cutAllowed(lower.substring(valueStart, valueEnd));
                i = valueEnd;
            }

            long parsed = resolveEpoch(isExpire ? DepositType.EXPIRE : DepositType.DECAY, rawValue, txDatetime);
            if (parsed != NO_EPOCH) {
                if (isExpire) expire = parsed;
                else decay = parsed;
            }
        }

        if (expire == NO_EPOCH && decay == NO_EPOCH) return null;

        String baseNote = normalizeSpaces(base);
        return new ParsedExpireDecay(expire, decay, baseNote);
    }

    private static long resolveEpoch(DepositType type, String rawValue, long baseTime) {
        if (type == null) return NO_EPOCH;

        String value = (rawValue == null || rawValue.isEmpty()) ? null : rawValue;

        Object resolved = type.resolve(value, baseTime);
        if (resolved == null) return NO_EPOCH;

        if (resolved instanceof Number n) {
            return normalizeEpochMillis(n.longValue());
        }
        if (resolved instanceof java.time.Instant inst) {
            return inst.toEpochMilli();
        }
        if (resolved instanceof java.util.Date d) {
            return d.getTime();
        }
        return NO_EPOCH;
    }

    /**
     * Keeps only [a-z0-9:] prefix (similar to the previous regex trimming logic).
     * e.g. "timestamp:123," -> "timestamp:123"
     */
    private static String cutAllowed(String s) {
        if (s == null || s.isEmpty()) return "";
        int n = 0;
        while (n < s.length()) {
            char c = s.charAt(n);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ':') n++;
            else break;
        }
        return s.substring(0, n);
    }

    private static String normalizeSpaces(CharSequence s) {
        int len = s.length();
        StringBuilder out = new StringBuilder(len);

        boolean wroteAny = false;
        boolean inWs = false;

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                if (wroteAny) inWs = true;
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
        if (v > 0 && v < 100_000_000_000L) return v * 1000L;
        return v;
    }
}