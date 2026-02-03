package link.locutus.discord.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

import static link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.handleAddbalanceAllianceScope;

public class ResetDepositsBuilder {
    public static final class Pending {
        int sign;               // sign of ALL indices in this bucket
        final IntArrayList idx; // indices into `transactions`

        Pending(int sign) {
            this.sign = sign;
            this.idx = new IntArrayList(1);
        }
    }

    private static boolean isExpireAt(String s, int i) {
        // "#expire=" length 8
        return i + 8 <= s.length()
                && s.charAt(i) == '#'
                && ((s.charAt(i + 1) | 32) == 'e')
                && ((s.charAt(i + 2) | 32) == 'x')
                && ((s.charAt(i + 3) | 32) == 'p')
                && ((s.charAt(i + 4) | 32) == 'i')
                && ((s.charAt(i + 5) | 32) == 'r')
                && ((s.charAt(i + 6) | 32) == 'e')
                && s.charAt(i + 7) == '=';
    }

    private static boolean isDecayAt(String s, int i) {
        // "#decay=" length 7
        return i + 7 <= s.length()
                && s.charAt(i) == '#'
                && ((s.charAt(i + 1) | 32) == 'd')
                && ((s.charAt(i + 2) | 32) == 'e')
                && ((s.charAt(i + 3) | 32) == 'c')
                && ((s.charAt(i + 4) | 32) == 'a')
                && ((s.charAt(i + 5) | 32) == 'y')
                && s.charAt(i + 6) == '=';
    }

    private static int skipTagValue(String s, int i) {
        // skip until whitespace or next '#'
        for (int n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '#') return i;
        }
        return i;
    }

    private static long fnv1a64(long h, long x) {
        // fold long into hash (byte-perfect FNV isn't necessary here; this is fast and stable)
        h ^= x;
        return h * 0x100000001b3L;
    }

    private static long mix64(long z) {
        // MurmurHash3 finalizer
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    /** Hash note with: lowercasing, collapsing whitespace, and removing #expire=... and #decay=... */
    private static long baseNoteHash(String note) {
        if (note == null || note.isEmpty()) return 0L;

        long h = 0xcbf29ce484222325L; // FNV offset
        boolean wroteAny = false;
        boolean spacePending = false;

        for (int i = 0, n = note.length(); i < n; ) {
            char c = note.charAt(i);

            if (Character.isWhitespace(c)) {
                spacePending = wroteAny;
                i++;
                continue;
            }

            if (c == '#') {
                if (isExpireAt(note, i)) {
                    i = skipTagValue(note, i + 8);
                    spacePending = wroteAny;
                    continue;
                }
                if (isDecayAt(note, i)) {
                    i = skipTagValue(note, i + 7);
                    spacePending = wroteAny;
                    continue;
                }
            }

            if (spacePending) {
                h = fnv1a64(h, ' ');
                spacePending = false;
            }

            if (c >= 'A' && c <= 'Z') c = (char) (c + 32);
            h = fnv1a64(h, c);
            wroteAny = true;
            i++;
        }

        return h;
    }

    private static long resourcesHash64(double[] res) {
        long h = 0xcbf29ce484222325L;
        for (double v2 : res) {
            long v = ArrayUtil.toCents(v2);
            h = fnv1a64(h, v);
            h = fnv1a64(h, v >>> 32);
        }
        return h;
    }

    private static int resourcesVectorSign(double[] res) {
        int s = 0;
        for (double v2 : res) {
            long cents = ArrayUtil.toCents(v2);
            if (cents == 0) continue;

            int vs = cents > 0 ? 1 : -1;
            if (s == 0) s = vs;
            else if (s != vs) return 0; // mixed +/- vector, can't treat as simple negation
        }
        return (s == 0) ? 1 : s; // all zero -> treat as positive
    }

    private static long resourcesHashAbs64(double[] res) {
        long h = 0xcbf29ce484222325L;
        for (double v2 : res) {
            long v = ArrayUtil.toCents(v2);
            if (v < 0) v = -v;
            h = fnv1a64(h, v);
            h = fnv1a64(h, v >>> 32);
        }
        return h;
    }

    private static double[] absCopyIfNeeded(double[] res) {
        for (double v : res) {
            if (v < 0) {
                double[] copy = Arrays.copyOf(res, res.length);
                for (int i = 0; i < copy.length; i++) copy[i] = Math.abs(copy[i]);
                return copy;
            }
        }
        return res;
    }

    /** Key used for pairing (+1 with -1) without allocating strings. */
    public static long resetKey(Transaction2 tx, long expireEpoch, long decayEpoch) {
        int a = (int) Math.min(tx.sender_id, tx.receiver_id);
        int b = (int) Math.max(tx.sender_id, tx.receiver_id);

        long h = 0xcbf29ce484222325L;
        h = fnv1a64(h, a);
        h = fnv1a64(h, b);
        h = fnv1a64(h, expireEpoch);
        h = fnv1a64(h, decayEpoch);
        h = fnv1a64(h, resourcesHashAbs64(tx.resources));
        h = fnv1a64(h, baseNoteHash(tx.note));
        return mix64(h);
    }

    private static String canonicalizeNote(String note,
                                           boolean hasExpire, long expireEpoch,
                                           boolean hasDecay, long decayEpoch) {
        // Only called for the tiny set that actually needs resetting.
        StringBuilder sb = new StringBuilder(note == null ? 32 : note.length() + 32);

        boolean wroteAny = false;
        boolean spacePending = false;

        if (note != null && !note.isEmpty()) {
            for (int i = 0, n = note.length(); i < n; ) {
                char c = note.charAt(i);

                if (Character.isWhitespace(c)) {
                    spacePending = wroteAny;
                    i++;
                    continue;
                }

                if (c == '#') {
                    if (isExpireAt(note, i)) {
                        i = skipTagValue(note, i + 8);
                        spacePending = wroteAny;
                        continue;
                    }
                    if (isDecayAt(note, i)) {
                        i = skipTagValue(note, i + 7);
                        spacePending = wroteAny;
                        continue;
                    }
                }

                if (spacePending) {
                    sb.append(' ');
                    spacePending = false;
                }

                if (c >= 'A' && c <= 'Z') c = (char) (c + 32);
                sb.append(c);
                wroteAny = true;
                i++;
            }
        }

        if (hasExpire) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("#expire=timestamp:").append(expireEpoch);
        }
        if (hasDecay) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("#decay=timestamp:").append(decayEpoch);
        }
        return sb.toString();
    }

    public interface ProgressCallback {
        void update(String message);

        ProgressCallback NOOP = _msg -> {};
    }

    public static final class Result {
        private final String filter;
        private final int nationCount;
        private final boolean force;
        private final boolean updateBulk;
        private final boolean changed;

        private final String details;

        private final double[] totalDeposits;
        private final double[] totalTax;
        private final double[] totalLoan;
        private final double[] totalExpire;
        private final double[] totalEscrow;

        private Result(
                String filter,
                int nationCount,
                boolean force,
                boolean updateBulk,
                boolean changed,
                String details,
                double[] totalDeposits,
                double[] totalTax,
                double[] totalLoan,
                double[] totalExpire,
                double[] totalEscrow
        ) {
            this.filter = filter;
            this.nationCount = nationCount;
            this.force = force;
            this.updateBulk = updateBulk;
            this.changed = changed;
            this.details = details;
            this.totalDeposits = totalDeposits;
            this.totalTax = totalTax;
            this.totalLoan = totalLoan;
            this.totalExpire = totalExpire;
            this.totalEscrow = totalEscrow;
        }

        public String getFilter() { return filter; }
        public int getNationCount() { return nationCount; }
        public boolean isForce() { return force; }
        public boolean isUpdateBulk() { return updateBulk; }
        public boolean hasChanges() { return changed; }

        /** The long, per-transaction log (what you were attaching as transaction.txt). */
        public String getDetails() { return details; }

        public double[] getTotalDeposits() { return Arrays.copyOf(totalDeposits, totalDeposits.length); }
        public double[] getTotalTax() { return Arrays.copyOf(totalTax, totalTax.length); }
        public double[] getTotalLoan() { return Arrays.copyOf(totalLoan, totalLoan.length); }
        public double[] getTotalExpire() { return Arrays.copyOf(totalExpire, totalExpire.length); }
        public double[] getTotalEscrow() { return Arrays.copyOf(totalEscrow, totalEscrow.length); }

        public String buildConfirmationTitle() {
            String name = filter;
            return "Reset deposits for " + (name.length() > 100 ? nationCount + " nations" : name);
        }

        /**
         * Builds the confirmation body (what you were sending to confirmation()).
         * Caller can attach {@link #getDetails()} as a file.
         */
        public String buildConfirmationBodyForDiscord() {
            return buildConfirmationBodyForDiscord(2000);
        }

        public String buildConfirmationBodyForDiscord(int maxChars) {
            String name = filter;

            StringBuilder body = new StringBuilder();
            if (!ResourceType.isZero(totalDeposits)) {
                body.append("Net Adding `").append(name).append(' ')
                        .append(ResourceType.toString(totalDeposits))
                        .append(" #deposit`\n");
            }
            if (!ResourceType.isZero(totalTax)) {
                body.append("Net Adding `").append(name).append(' ')
                        .append(ResourceType.toString(totalTax))
                        .append(" #tax`\n");
            }
            if (!ResourceType.isZero(totalLoan)) {
                body.append("Net Adding `").append(name).append(' ')
                        .append(ResourceType.toString(totalLoan))
                        .append(" #loan`\n");
            }
            if (!ResourceType.isZero(totalExpire)) {
                body.append("Net Adding `").append(name).append(' ')
                        .append(ResourceType.toString(totalExpire))
                        .append(" #expire`\n");
            }
            if (!ResourceType.isZero(totalEscrow)) {
                body.append("Deleting Escrow: `").append(name).append(' ')
                        .append(ResourceType.toString(totalEscrow))
                        .append("`\n");
            }

            double[] total = ResourceType.getBuffer();
            total = ResourceType.add(total, totalDeposits);
            total = ResourceType.add(total, totalTax);
            total = ResourceType.add(total, totalLoan);
            total = ResourceType.add(total, totalExpire);
            total = ResourceType.subtract(total, totalEscrow);

            body.append("Total Net: `").append(name).append(' ')
                    .append(ResourceType.toString(total))
                    .append("`\n");
            body.append("\n\nSee attached file for transaction details\n");

            String bodyStr = body.toString();
            // Keep your existing sizing behavior
            if (maxChars > 0 && bodyStr.length() > maxChars - 15) {
                int cut = Math.max(0, maxChars - 36);
                bodyStr = bodyStr.substring(0, Math.min(bodyStr.length(), cut))
                        + "\n\n... (truncated, see attached file)";
            }

            return bodyStr;
        }
    }

    private final GuildDB db;
    private final DBNation me;
    private final NationList nations;

    private boolean ignoreGrants;
    private boolean ignoreLoans;
    private boolean ignoreTaxes;
    private boolean ignoreBankDeposits;
    private boolean ignoreEscrow;
    private boolean force;

    private ProgressCallback progress = ProgressCallback.NOOP;
    private long progressIntervalMs = 5000L;
    private LongSupplier nowSupplier = System::currentTimeMillis;

    public ResetDepositsBuilder(GuildDB db, DBNation me, NationList nations) {
        this.db = Objects.requireNonNull(db, "db");
        this.me = Objects.requireNonNull(me, "me");
        this.nations = Objects.requireNonNull(nations, "nations");
    }

    public ResetDepositsBuilder ignoreGrants(boolean v) { this.ignoreGrants = v; return this; }
    public ResetDepositsBuilder ignoreLoans(boolean v) { this.ignoreLoans = v; return this; }
    public ResetDepositsBuilder ignoreTaxes(boolean v) { this.ignoreTaxes = v; return this; }
    public ResetDepositsBuilder ignoreBankDeposits(boolean v) { this.ignoreBankDeposits = v; return this; }
    public ResetDepositsBuilder ignoreEscrow(boolean v) { this.ignoreEscrow = v; return this; }
    public ResetDepositsBuilder force(boolean v) { this.force = v; return this; }

    /** Optional: hook for your command to update a "please wait" message. */
    public ResetDepositsBuilder onProgress(ProgressCallback cb) {
        this.progress = (cb == null ? ProgressCallback.NOOP : cb);
        return this;
    }

    /** Optional: defaults to 5000ms like your original code. Set 0 to disable. */
    public ResetDepositsBuilder progressIntervalMs(long ms) {
        this.progressIntervalMs = Math.max(0L, ms);
        return this;
    }

    /** Optional: for testing. */
    public ResetDepositsBuilder nowSupplier(LongSupplier supplier) {
        this.nowSupplier = (supplier == null ? System::currentTimeMillis : supplier);
        return this;
    }

    public Result execute() throws IOException {
        final long now = nowSupplier.getAsLong();
        final String filter = nations.getFilter();

        StringBuilder details = new StringBuilder("Resetting deposits for `")
                .append(filter)
                .append("`\n");

        double[] totalDeposits = ResourceType.getBuffer();
        double[] totalTax = ResourceType.getBuffer();
        double[] totalLoan = ResourceType.getBuffer();
        double[] totalExpire = ResourceType.getBuffer();
        double[] totalEscrow = ResourceType.getBuffer();

        boolean updateBulk = Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS > 0;

        // keep side-effects, but no UI here
        if (force && updateBulk) {
            Locutus.imp().runEventsAsync(events -> Locutus.imp().getBankDB().updateBankRecs(false, events));
        }

        Set<DBNation> nationSet = nations.getNations();
        ValueStore<DBNation> cache = PlaceholderCache.createCache(nationSet, DBNation.class);

        long lastProgressMs = nowSupplier.getAsLong();
        boolean changed = false;

        for (DBNation nation : nationSet) {
            long cur = nowSupplier.getAsLong();
            if (progressIntervalMs > 0 && lastProgressMs + progressIntervalMs < cur) {
                lastProgressMs = cur;
                progress.update("Resetting deposits for " + nation.getMarkdownUrl());
            }

            Map<DepositType, double[]> depoByType = nation.getDeposits(
                    cache, db, null,
                    true, true,
                    force && !updateBulk ? 0L : -1L,
                    0, Long.MAX_VALUE,
                    true
            );

            changed |= resetSimpleType(now, nation, depoByType, DepositType.DEPOSIT, "#deposit",
                    ignoreBankDeposits, totalDeposits, details);

            changed |= resetSimpleType(now, nation, depoByType, DepositType.TAX, "#tax",
                    ignoreTaxes, totalTax, details);

            changed |= resetSimpleType(now, nation, depoByType, DepositType.LOAN, "#loan",
                    ignoreLoans, totalLoan, details);

            changed |= resetGrantExpireDecay(now, nation, depoByType, totalExpire, details);

            changed |= resetEscrow(nation, totalEscrow, details);
        }

        return new Result(
                filter,
                nationSet.size(),
                force,
                updateBulk,
                changed,
                details.toString(),
                Arrays.copyOf(totalDeposits, totalDeposits.length),
                Arrays.copyOf(totalTax, totalTax.length),
                Arrays.copyOf(totalLoan, totalLoan.length),
                Arrays.copyOf(totalExpire, totalExpire.length),
                Arrays.copyOf(totalEscrow, totalEscrow.length)
        );
    }

    private boolean resetSimpleType(
            long now,
            DBNation nation,
            Map<DepositType, double[]> depoByType,
            DepositType type,
            String note,
            boolean ignore,
            double[] total,
            StringBuilder details
    ) throws IOException {
        double[] res = depoByType.get(type);
        if (res == null || ignore || ResourceType.isZero(res)) return false;

        ResourceType.round(res);
        details.append("Subtracting `")
                .append(nation.getQualifiedId()).append(' ')
                .append(ResourceType.toString(res)).append(' ')
                .append(note)
                .append("`\n");

        ResourceType.subtract(total, res);

        if (force) {
            db.subBalance(now, nation, me.getNation_id(), note, res);
        }
        return true;
    }

    private boolean resetGrantExpireDecay(
            long now,
            DBNation nation,
            Map<DepositType, double[]> depoByType,
            double[] totalExpire,
            StringBuilder details
    ) throws IOException {
        double[] grant = depoByType.get(DepositType.GRANT);
        if (grant == null || ignoreGrants || ResourceType.isZero(grant)) return false;

        final int nationId = nation.getNation_id();

        List<Map.Entry<Integer, Transaction2>> transactions =
                nation.getTransactions(db, null, true, true, true, -1, 0, Long.MAX_VALUE, true);

        // Iterate newest->oldest if possible (pairs tend to be created later, reduces pending size).
        int start = 0, end = transactions.size(), step = 1;
        if (transactions.size() > 1) {
            long t0 = transactions.get(0).getValue().tx_datetime;
            long t1 = transactions.get(transactions.size() - 1).getValue().tx_datetime;
            if (t0 < t1) { // looks oldest->newest
                start = transactions.size() - 1;
                end = -1;
                step = -1;
            }
        }

        Long2ObjectOpenHashMap<Pending> pending = new Long2ObjectOpenHashMap<>();
        pending.defaultReturnValue(null);

        // 1 pass: cancel out already-reset pairs
        for (int i = start; i != end; i += step) {
            Map.Entry<Integer, Transaction2> entry = transactions.get(i);
            int sign = entry.getKey();
            Transaction2 tx = entry.getValue();

            if (tx.note == null) continue;
            if (tx.receiver_id != nationId && tx.sender_id != nationId) continue;

            // cheap prefilter (avoid parsing notes for most tx)
            String note = tx.note;
            if (note.indexOf("#expire") < 0 && note.indexOf("#decay") < 0
                    && note.indexOf("#EXPIRE") < 0 && note.indexOf("#DECAY") < 0) {
                continue;
            }

            Map<DepositType, Object> noteMap = tx.getNoteMap();
            Object expireObj = noteMap.get(DepositType.EXPIRE);
            Object decayObj = noteMap.get(DepositType.DECAY);
            if (!(expireObj instanceof Number) && !(decayObj instanceof Number)) continue;

            long expireEpoch = (expireObj instanceof Number n) ? n.longValue() : Long.MAX_VALUE;
            long decayEpoch = (decayObj instanceof Number n) ? n.longValue() : Long.MAX_VALUE;

            if (expireEpoch == Long.MAX_VALUE && decayEpoch == Long.MAX_VALUE) continue;
            if (expireEpoch <= now || decayEpoch <= now) continue;

            int rSign = resourcesVectorSign(tx.resources);
            if (rSign == 0) continue; // mixed +/-, skip pairing (or handle separately)

            int effectiveSign = sign * rSign;  // <- THIS is the important bit

            long key = resetKey(tx, expireEpoch, decayEpoch);

            Pending p = pending.get(key);
            if (p == null) {
                p = new Pending(effectiveSign);
                p.idx.add(i);
                pending.put(key, p);
                continue;
            }

            if (p.sign == -effectiveSign) {
                p.idx.removeInt(p.idx.size() - 1);
                if (p.idx.isEmpty()) pending.remove(key);
            } else {
                p.idx.add(i);
            }
        }

        // 2) Only remaining entries need resets
        boolean changed = false;
        for (Pending p : pending.values()) {
            int sign = p.sign;

            for (int k = 0; k < p.idx.size(); k++) {
                int idx = p.idx.getInt(k);
                Transaction2 tx = transactions.get(idx).getValue();
                double[] amt = absCopyIfNeeded(tx.resources);

                Map<DepositType, Object> noteMap = tx.getNoteMap();
                Object expireObj = noteMap.get(DepositType.EXPIRE);
                Object decayObj = noteMap.get(DepositType.DECAY);

                long expireEpoch = (expireObj instanceof Number n) ? n.longValue() : Long.MAX_VALUE;
                long decayEpoch = (decayObj instanceof Number n) ? n.longValue() : Long.MAX_VALUE;

                String noteCopy = canonicalizeNote(
                        tx.note,
                        expireObj instanceof Number, expireEpoch,
                        decayObj instanceof Number, decayEpoch
                );

                if (sign == 1) {
                    details.append("Subtracting `")
                            .append(nation.getQualifiedId()).append(' ')
                            .append(ResourceType.toString(amt)).append(' ')
                            .append(noteCopy).append("`\n");

                    ResourceType.subtract(totalExpire, amt);
                    if (force) {
                        db.subBalance(decayEpoch == Long.MAX_VALUE ? now : tx.tx_datetime,
                                nation, me.getNation_id(), noteCopy, amt);
                    }
                    changed = true;

                } else if (sign == -1) {
                    details.append("Adding `")
                            .append(nation.getQualifiedId()).append(' ')
                            .append(ResourceType.toString(amt)).append(' ')
                            .append(noteCopy).append("`\n");

                    ResourceType.add(totalExpire, amt);
                    if (force) {
                        db.addBalance(decayEpoch == Long.MAX_VALUE ? now : tx.tx_datetime,
                                nation, me.getNation_id(), noteCopy, amt);
                    }
                    changed = true;
                } else {
                    Logg.error("Invalid sign for deposits reset " + sign);
                }
            }
        }

        return changed;
    }

    private boolean resetEscrow(DBNation nation, double[] totalEscrow, StringBuilder details) {
        if (ignoreEscrow) return false;

        try {
            Map.Entry<double[], Long> escrowedPair = db.getEscrowed(nation);
            if (escrowedPair != null && !ResourceType.isZero(escrowedPair.getKey())) {
                details.append("Subtracting escrow: `")
                        .append(nation.getQualifiedId()).append(' ')
                        .append(ResourceType.toString(escrowedPair.getKey()))
                        .append("`\n");

                ResourceType.subtract(totalEscrow, escrowedPair.getKey());

                if (force) db.setEscrowed(nation, null, 0);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            details.append("Failed to reset escrow balance: ").append(e.getMessage()).append('\n');
            return true; // something happened (error)
        }

        return false;
    }
}
