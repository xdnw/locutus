package link.locutus.discord.db.guild;

import java.util.Objects;

public final class InternalTransferPlanner {
    private InternalTransferPlanner() {}

    public enum AccountKind {
        GUILD,
        ALLIANCE
    }

    public static final class BackingAccount {
        public final AccountKind kind;
        public final long id;

        public BackingAccount(AccountKind kind, long id) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.id = id;
        }

        public static BackingAccount guild(long guildId) {
            return new BackingAccount(AccountKind.GUILD, guildId);
        }

        public static BackingAccount alliance(long allianceId) {
            return new BackingAccount(AccountKind.ALLIANCE, allianceId);
        }

        @Override
        public String toString() {
            return kind + ":" + id;
        }
    }

    public static final class Request {
        public final long senderGuildId;
        public final Integer senderNationId;
        public final BackingAccount senderBacking;

        public final long receiverGuildId;
        public final Integer receiverNationId;
        public final BackingAccount receiverBacking;

        public final long senderOffshoreId;
        public final long receiverOffshoreId;

        public Request(
                long senderGuildId,
                Integer senderNationId,
                BackingAccount senderBacking,
                long receiverGuildId,
                Integer receiverNationId,
                BackingAccount receiverBacking,
                long senderOffshoreId,
                long receiverOffshoreId
        ) {
            this.senderGuildId = senderGuildId;
            this.senderNationId = senderNationId;
            this.senderBacking = Objects.requireNonNull(senderBacking, "senderBacking");
            this.receiverGuildId = receiverGuildId;
            this.receiverNationId = receiverNationId;
            this.receiverBacking = Objects.requireNonNull(receiverBacking, "receiverBacking");
            this.senderOffshoreId = senderOffshoreId;
            this.receiverOffshoreId = receiverOffshoreId;
        }
    }

    public static final class Plan {
        public final boolean sameBacking;
        public final boolean sameNationBalance;

        public final boolean debitSenderNation;
        public final boolean debitSenderBacking;
        public final boolean creditReceiverBacking;
        public final boolean creditReceiverNation;

        public final boolean movesBacking;
        public final boolean crossesOffshore;

        public Plan(
                boolean sameBacking,
                boolean sameNationBalance,
                boolean debitSenderNation,
                boolean debitSenderBacking,
                boolean creditReceiverBacking,
                boolean creditReceiverNation,
                boolean movesBacking,
                boolean crossesOffshore
        ) {
            this.sameBacking = sameBacking;
            this.sameNationBalance = sameNationBalance;
            this.debitSenderNation = debitSenderNation;
            this.debitSenderBacking = debitSenderBacking;
            this.creditReceiverBacking = creditReceiverBacking;
            this.creditReceiverNation = creditReceiverNation;
            this.movesBacking = movesBacking;
            this.crossesOffshore = crossesOffshore;
        }

        @Override
        public String toString() {
            return "Plan{" +
                    "sameBacking=" + sameBacking +
                    ", sameNationBalance=" + sameNationBalance +
                    ", debitSenderNation=" + debitSenderNation +
                    ", debitSenderBacking=" + debitSenderBacking +
                    ", creditReceiverBacking=" + creditReceiverBacking +
                    ", creditReceiverNation=" + creditReceiverNation +
                    ", movesBacking=" + movesBacking +
                    ", crossesOffshore=" + crossesOffshore +
                    '}';
        }
    }

    public static Plan plan(Request req) {
        Objects.requireNonNull(req, "req");

        boolean sameBacking =
                req.senderBacking.kind == req.receiverBacking.kind
                        && req.senderBacking.id == req.receiverBacking.id;

        boolean sameNationBalance =
                req.senderNationId != null
                        && req.receiverNationId != null
                        && req.senderGuildId == req.receiverGuildId
                        && req.senderNationId.equals(req.receiverNationId);

        // backing->same backing account with no nation legs is a no-op/self-transfer
        if (sameBacking && req.senderNationId == null && req.receiverNationId == null) {
            throw new IllegalArgumentException("Sender and receiver backing accounts are the same");
        }

        // same guild + same nation local balance is also a self-transfer
        if (sameBacking && sameNationBalance) {
            throw new IllegalArgumentException("Sender and receiver nation balances are the same");
        }

        boolean debitSenderNation = req.senderNationId != null;

        // Sender backing is debited when:
        // - source is directly a backing account, or
        // - value leaves the sender backing domain
        boolean debitSenderBacking = req.senderNationId == null || !sameBacking;

        // CRITICAL RULE:
        // Receiver backing is credited ONLY when value enters a different backing account.
        //
        // Same-backing nation -> backing-account is a donation/surrender of a local claim.
        // It must NOT mint offshore/backing funds.
        boolean creditReceiverBacking = !sameBacking;

        boolean creditReceiverNation = req.receiverNationId != null && !sameNationBalance;

        boolean movesBacking = debitSenderBacking || creditReceiverBacking;
        boolean crossesOffshore = movesBacking && req.senderOffshoreId != req.receiverOffshoreId;

        if (creditReceiverBacking && !debitSenderBacking) {
            throw new IllegalStateException(
                    "Invariant violation: receiver backing credit requires sender backing debit"
            );
        }

        if (sameBacking && creditReceiverBacking) {
            throw new IllegalStateException(
                    "Invariant violation: same-backing transfer cannot credit receiver backing"
            );
        }

        if (sameBacking && req.senderNationId != null && req.receiverNationId == null) {
            if (debitSenderBacking) {
                throw new IllegalStateException(
                        "Invariant violation: same-backing nation -> backing must not debit sender backing"
                );
            }
            if (creditReceiverBacking) {
                throw new IllegalStateException(
                        "Invariant violation: same-backing nation -> backing must not credit receiver backing"
                );
            }
        }

        return new Plan(
                sameBacking,
                sameNationBalance,
                debitSenderNation,
                debitSenderBacking,
                creditReceiverBacking,
                creditReceiverNation,
                movesBacking,
                crossesOffshore
        );
    }
}