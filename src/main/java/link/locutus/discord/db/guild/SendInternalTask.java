package link.locutus.discord.db.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankerWithdrawUsageTracker;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TransactionNote;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.DeferredPriority;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitedSource;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.SendPolicy;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.TransferResult;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static link.locutus.discord.util.offshore.OffshoreInstance.DISABLED_MESSAGE;

public class SendInternalTask {
    private enum SendInternalRateLimit implements RateLimitedSource {
        TRANSFER_LOG(SendPolicy.CONDENSE, DeferredPriority.SEND_INTERNAL_TRANSFER_LOG);

        private final SendPolicy sendPolicy;
        private final DeferredPriority deferredPriority;

        SendInternalRateLimit(SendPolicy sendPolicy, DeferredPriority deferredPriority) {
            this.sendPolicy = sendPolicy;
            this.deferredPriority = deferredPriority;
        }

        @Override
        public SendPolicy sendPolicy() {
            return sendPolicy;
        }

        @Override
        public DeferredPriority deferredPriority() {
            return deferredPriority;
        }
    }

    private static final DepositType NOTE = DepositType.DEPOSIT;
    private static final TransactionNote STRUCTURED_NOTE = TransactionNote.of(NOTE);

    private final User banker;
    private final double[] amount;
    private final DBNation receiverNation;
    private final DBAlliance receiverAlliance;
    private final GuildDB receiverDB;
    private final DBNation senderNation;
    private final DBAlliance senderAlliance;
    private final GuildDB senderDB;
    private final DBNation bankerNation;
    private final MessageChannel receiverChannel;
    private final OffshoreInstance senderOffshore;
    private final OffshoreInstance receiverOffshore;

    private static final class TransferPlan {
        final NationOrAllianceOrGuild senderBacking;
        final NationOrAllianceOrGuild receiverBacking;
        final boolean sameBacking;
        final boolean debitSenderNation;
        final boolean debitSenderBacking;
        final boolean creditReceiverBacking;
        final boolean creditReceiverNation;
        final boolean movesBacking;
        final boolean crossesOffshore;

        private TransferPlan(
                NationOrAllianceOrGuild senderBacking,
                NationOrAllianceOrGuild receiverBacking,
                boolean sameBacking,
                boolean debitSenderNation,
                boolean debitSenderBacking,
                boolean creditReceiverBacking,
                boolean creditReceiverNation,
                boolean movesBacking,
                boolean crossesOffshore) {
            this.senderBacking = senderBacking;
            this.receiverBacking = receiverBacking;
            this.sameBacking = sameBacking;
            this.debitSenderNation = debitSenderNation;
            this.debitSenderBacking = debitSenderBacking;
            this.creditReceiverBacking = creditReceiverBacking;
            this.creditReceiverNation = creditReceiverNation;
            this.movesBacking = movesBacking;
            this.crossesOffshore = crossesOffshore;
        }
    }

    public SendInternalTask(
            @Me User banker,
            @Me DBNation bankerNation,
            GuildDB senderDB,
            DBAlliance senderAlliance,
            DBNation senderNation,
            GuildDB receiverDB,
            DBAlliance receiverAlliance,
            DBNation receiverNation,
            double[] amount) throws IOException {
        if (OffshoreInstance.DISABLE_TRANSFERS
                && (!Settings.INSTANCE.DISCORD.BOT_OWNER_IS_LOCUTUS_ADMIN
                        || banker.getIdLong() != Locutus.loader().getAdminUserId())) {
            throw new IllegalArgumentException(DISABLED_MESSAGE);
        }

        checkNotNull(bankerNation, "No banker specified. Register with " + CM.register.cmd.toSlashMention());
        checkArgsNotNull(senderDB, senderAlliance, senderNation, receiverDB, receiverAlliance, receiverNation);
        checkNonNegative(amount);
        checkNotGreater(amount, 2_000_000_000);

        senderAlliance = checkSenderAlliance(senderDB, senderAlliance, senderNation);
        receiverDB = checkReceiverDB(receiverDB, receiverAlliance, receiverNation);
        receiverAlliance = checkReceiverAlliance(receiverDB, receiverAlliance, receiverNation);

        checkNationInGuild(senderDB, senderNation, "Sender");
        checkNationInGuild(receiverDB, receiverNation, "Receiver");

        checkReceiverActive(receiverDB, receiverAlliance, receiverNation);
        checkNationMember(senderNation, receiverNation);

        int roleAA = senderAlliance != null ? senderAlliance.getId() : 0;
        boolean hasEcon = Roles.ECON.has(banker, senderDB.getGuild(), roleAA);
        boolean canWithdrawSelf = hasEcon || Roles.ECON_WITHDRAW_SELF.has(banker, senderDB.getGuild(), roleAA);

        if (!hasEcon && Roles.TEMP.has(banker, senderDB.getGuild())) {
            throw new IllegalArgumentException(
                    "Banker " + banker.getName() + " has the TEMP role. Please remove that role and try again.");
        }

        checkWithdrawPerms(roleAA, hasEcon, canWithdrawSelf, senderDB, senderNation, bankerNation, banker);

        this.senderOffshore = senderDB.getOffshore();
        this.receiverOffshore = receiverDB.getOffshore();

        checkOffshores(senderOffshore, senderDB, senderAlliance, senderNation, receiverOffshore, receiverDB,
                receiverAlliance, receiverNation);

        this.receiverChannel = receiverDB.getResourceChannel(receiverAlliance != null ? receiverAlliance.getId() : 0);
        checkNotNull(
                receiverChannel,
                "Please have an admin set: " + GuildKey.RESOURCE_REQUEST_CHANNEL.getCommandMention()
                        + " in receiving " + receiverDB.getGuild());

        checkChange(senderDB, senderAlliance, senderNation, receiverDB, receiverAlliance, receiverNation);

        this.banker = banker;
        this.bankerNation = bankerNation;
        this.senderDB = senderDB;
        this.senderAlliance = senderAlliance;
        this.senderNation = senderNation;
        this.receiverDB = receiverDB;
        this.receiverAlliance = receiverAlliance;
        this.receiverNation = receiverNation;
        this.amount = amount;

        checkTransferLimits(senderOffshore, senderDB, bankerNation, amount, 6_000_000_000D);
    }

    private void checkTransferLimits(OffshoreInstance senderOffshore, GuildDB senderDB, DBNation senderNation,
            double[] amount, double defaultCap) {
        TransferResult result = senderOffshore.checkLimit(senderDB, senderNation, null, amount, NOTE, defaultCap);
        if (result != null) {
            throw new IllegalArgumentException(result.getMessageJoined(true));
        }
    }

    private BankerWithdrawUsageTracker.Reservation reserveTransferLimitUsage(double defaultCap) {
        Double withdrawLimit = senderDB.getHandler().getWithdrawLimit(bankerNation.getNation_id());
        if (withdrawLimit == null) {
            withdrawLimit = defaultCap;
        }
        if (withdrawLimit == null || withdrawLimit >= Long.MAX_VALUE) {
            return null;
        }
        BankerWithdrawUsageTracker.Reservation reservation = senderDB.getHandler().reserveBankerWithdrawUsage(
                bankerNation.getNation_id(),
                ResourceType.convertedTotal(amount),
                withdrawLimit,
                System.currentTimeMillis());
        if (reservation == null) {
            TransferResult result = senderOffshore.checkLimit(senderDB, bankerNation, null, amount, NOTE, defaultCap);
            throw new IllegalArgumentException(
                    result == null ? "Unable to reserve banker withdraw limit" : result.getMessageJoined(true));
        }
        return reservation;
    }

    ////////////////////////////////////////////////////////////////
    ////////////////////////// Public methods //////////////////////
    ////////////////////////////////////////////////////////////////

    public List<TransferResult> send(boolean requireConfirmation) throws IOException {
        if (!TimeUtil.checkTurnChange()) {
            return List.of(new TransferResult(OffshoreInstance.TransferStatus.TURN_CHANGE, receiverDB, amount, NOTE)
                    .addMessage(OffshoreInstance.TransferStatus.TURN_CHANGE.getMessage()));
        }

        TransferPlan plan = createTransferPlan();

        double[] senderNationBalanceBefore = plan.debitSenderNation ? getSenderNationBalance(requireConfirmation)
                : null;
        double[] senderBackingBalanceBefore = plan.debitSenderBacking ? getSenderBackingBalance(requireConfirmation)
                : null;

        if (requireConfirmation) {
            return List.of(createConfirmation(plan));
        }

        BankerWithdrawUsageTracker.Reservation bankerWithdrawReservation = reserveTransferLimitUsage(6_000_000_000D);

        double[] receiverBackingBalanceBefore = plan.creditReceiverBacking
                ? getBackingBalance(receiverOffshore, receiverDB, receiverAlliance, true)
                : null;

        double[] receiverNationBalanceBefore = plan.creditReceiverNation
                ? getCurrentNationBalance(receiverNation, receiverDB)
                : null;

        long now = System.currentTimeMillis();
        List<TransferResult> results = new ArrayList<>();

        boolean senderNationLocked = false;
        boolean senderBackingLocked = false;
        boolean receiverBackingLocked = false;
        boolean receiverNationLocked = false;

        boolean didDebitSenderNation = false;
        boolean didDebitSenderBacking = false;
        boolean didCreditReceiverBacking = false;
        boolean didCreditReceiverNation = false;
        boolean didExternalTransfer = false;

        try {
            // 1) Debit sender nation local balance
            if (plan.debitSenderNation) {
                lockNation(senderOffshore, senderNation, now);
                senderNationLocked = true;

                senderDB.subtractBalance(now, senderNation, bankerNation.getNation_id(), STRUCTURED_NOTE, amount);

                double[] newBalance = getCurrentNationBalance(senderNation, senderDB);
                TransferResult error = checkExpectedDelta(
                        senderNationBalanceBefore,
                        newBalance,
                        senderNation,
                        false,
                        senderOffshore);
                if (error != null) {
                    results.add(error);
                    return results;
                }

                didDebitSenderNation = true;
            }

            // 2) Debit sender backing/offshore account
            if (plan.debitSenderBacking) {
                lockBackingAccount(senderOffshore, senderDB, senderAlliance);
                senderBackingLocked = true;

                debitSenderBackingAccount(now, plan.senderBacking);

                double[] newBalance = getBackingBalance(senderOffshore, senderDB, senderAlliance, false);
                TransferResult error = checkExpectedDelta(
                        senderBackingBalanceBefore,
                        newBalance,
                        plan.senderBacking,
                        false,
                        senderOffshore);
                if (error != null) {
                    results.add(error);
                    return results;
                }

                didDebitSenderBacking = true;
            }

            // Hard invariant: receiver backing must never be credited unless sender backing
            // was actually debited
            if (plan.creditReceiverBacking && !didDebitSenderBacking) {
                throw new IllegalStateException(
                        "Invariant violation: refusing to credit receiver backing account without a prior sender backing debit");
            }

            // 3) External offshore-to-offshore in-game transfer, if needed
            if (plan.crossesOffshore) {
                TransferResult transfer = senderOffshore.transferUnsafe(
                        null,
                        receiverOffshore.getAlliance(),
                        ResourceType.resourcesToMap(amount),
                        TransactionNote.of(DepositType.IGNORE));
                results.add(transfer);

                if (!transfer.getStatus().isSuccess()) {
                    transfer.addMessage(
                            "Failed to transfer to " + receiverOffshore.getAlliance().getMarkdownUrl()
                                    + " in-game. See "
                                    + CM.offshore.unlockTransfers.cmd.nationOrAllianceOrGuild(senderDB.getIdLong() + "")
                                    + " in " + senderOffshore.getGuild());
                    return results;
                } else {
                    transfer.addMessage(
                            "Transferred to " + receiverOffshore.getAlliance().getMarkdownUrl() + " in-game");
                    didExternalTransfer = true;
                }
            }

            // 4) Credit receiver backing/offshore account
            if (plan.creditReceiverBacking) {
                lockBackingAccount(receiverOffshore, receiverDB, receiverAlliance);
                receiverBackingLocked = true;

                creditReceiverBackingAccount(now);

                double[] newBalance = getBackingBalance(receiverOffshore, receiverDB, receiverAlliance, false);
                TransferResult error = checkExpectedDelta(
                        receiverBackingBalanceBefore,
                        newBalance,
                        plan.receiverBacking,
                        true,
                        receiverOffshore);
                if (error != null) {
                    results.add(error);
                    return results;
                }

                if (receiverAlliance == null) {
                    results.add(new TransferResult(OffshoreInstance.TransferStatus.SUCCESS, receiverDB, amount, NOTE)
                            .addMessage("Added to guild account: " + receiverDB.getGuild().toString()));
                } else {
                    results.add(
                            new TransferResult(OffshoreInstance.TransferStatus.SUCCESS, receiverAlliance, amount, NOTE)
                                    .addMessage("Added to alliance account " + receiverAlliance.getMarkdownUrl()));
                }

                didCreditReceiverBacking = true;
            }

            // 5) Credit receiver nation local balance
            if (plan.creditReceiverNation) {
                lockNation(receiverOffshore, receiverNation, now);
                receiverNationLocked = true;

                receiverDB.addBalance(now, receiverNation, bankerNation.getId(), STRUCTURED_NOTE, amount);

                double[] newBalance = getCurrentNationBalance(receiverNation, receiverDB);
                TransferResult error = checkExpectedDelta(
                        receiverNationBalanceBefore,
                        newBalance,
                        receiverNation,
                        true,
                        receiverOffshore);
                if (error != null) {
                    results.add(error);
                    return results;
                }

                results.add(new TransferResult(OffshoreInstance.TransferStatus.SUCCESS, receiverNation, amount, NOTE)
                        .addMessage("Added to nation account " + receiverNation.getMarkdownUrl()));

                didCreditReceiverNation = true;
            }

            // 6) Donation-only path (same backing nation -> backing account)
            if (isDonationOnly(plan)) {
                if (!results.isEmpty()) {
                    throw new IllegalStateException(
                            "Invariant violation: donation-only path should not have intermediate results");
                }

                results.add(new TransferResult(OffshoreInstance.TransferStatus.SUCCESS, plan.receiverBacking, amount,
                        NOTE)
                        .addMessage(
                                "Donation completed. Deducted from nation balance only; sender and receiver share the same backing account, so no offshore/backing account was credited."));
            } else if (results.isEmpty()) {
                throw new IllegalStateException("Invariant violation: successful transfer produced no result entries");
            }

            // 7) Unlock success path
            if (senderNationLocked) {
                unlockNation(senderOffshore, senderNation);
            }
            if (senderBackingLocked) {
                unlockBackingAccount(senderOffshore, senderDB, senderAlliance);
            }
            if (receiverBackingLocked) {
                unlockBackingAccount(receiverOffshore, receiverDB, receiverAlliance);
            }
            if (receiverNationLocked) {
                unlockNation(receiverOffshore, receiverNation);
            }

            try {
                if (receiverChannel.canTalk()) {
                    StringBuilder message = new StringBuilder("Internal Transfer ");
                    if (senderDB != receiverDB)
                        message.append(senderDB.getGuild()).append(" ");
                    if (senderAlliance != null && !Objects.equals(senderAlliance, receiverAlliance))
                        message.append(" AA:").append(senderAlliance.getName());
                    if (senderNation != null)
                        message.append(" ").append(senderNation.getName());
                    message.append(" -> ");
                    if (receiverDB != senderDB)
                        message.append(receiverDB.getGuild()).append(" ");
                    if (receiverAlliance != null && !Objects.equals(senderAlliance, receiverAlliance))
                        message.append(" AA:").append(receiverAlliance.getName());
                    if (receiverNation != null)
                        message.append(" ").append(receiverNation.getName());
                    message.append(": ").append(ResourceType.toString(amount)).append(", note: `").append(NOTE)
                            .append("`");
                    RateLimitUtil.queueMessage(receiverChannel, message.toString(), SendInternalRateLimit.TRANSFER_LOG);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }

            bankerWithdrawReservation = null;
            return results;
        } catch (Throwable e) {
            e.printStackTrace();

            String raw = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            StringBuilder msg = new StringBuilder("Internal error: ").append(StringMan.stripApiKey(raw));

            if (didDebitSenderNation || didDebitSenderBacking || didCreditReceiverBacking || didCreditReceiverNation) {
                msg.append("\nTransfer state remains locked for manual reconciliation.");
            }
            if (didExternalTransfer) {
                msg.append("\nWarning: the in-game offshore transfer may already have completed.");
            }
            if (didCreditReceiverBacking) {
                msg.append("\nWarning: the receiver backing/offshore account may already have been credited locally.");
            }
            if (didCreditReceiverNation) {
                msg.append("\nWarning: the receiver nation balance may already have been credited locally.");
            }

            results.add(new TransferResult(OffshoreInstance.TransferStatus.OTHER, receiverDB, amount, NOTE)
                    .addMessage(msg.toString()));
            return results;
        } finally {
            if (bankerWithdrawReservation != null) {
                senderDB.getHandler().rollbackBankerWithdrawUsage(
                        bankerNation.getNation_id(),
                        bankerWithdrawReservation,
                        System.currentTimeMillis());
            }
        }
    }

    ////////////////////////////////////////////////////////////////
    ////////////////////// Planning / accounting ///////////////////
    ////////////////////////////////////////////////////////////////

    private TransferPlan createTransferPlan() {
        NationOrAllianceOrGuild senderBacking = resolveBackingAccount(senderDB, senderAlliance);
        NationOrAllianceOrGuild receiverBacking = resolveBackingAccount(receiverDB, receiverAlliance);

        InternalTransferPlanner.Plan p = InternalTransferPlanner.plan(
                buildPlannerRequest(
                        senderDB,
                        senderAlliance,
                        senderNation,
                        receiverDB,
                        receiverAlliance,
                        receiverNation,
                        senderOffshore.getAllianceId(),
                        receiverOffshore.getAllianceId()));

        return new TransferPlan(
                senderBacking,
                receiverBacking,
                p.sameBacking,
                p.debitSenderNation,
                p.debitSenderBacking,
                p.creditReceiverBacking,
                p.creditReceiverNation,
                p.movesBacking,
                p.crossesOffshore);
    }

    private void lockNation(OffshoreInstance offshore, DBNation nation, long now) {
        if (nation != null) {
            offshore.disabledNations.put(nation.getId(), now);
        }
    }

    private void unlockNation(OffshoreInstance offshore, DBNation nation) {
        if (nation != null) {
            offshore.disabledNations.remove(nation.getId());
        }
    }

    private void lockBackingAccount(OffshoreInstance offshore, GuildDB guildDB, DBAlliance alliance) {
        long id = alliance != null ? alliance.getIdLong() : guildDB.getIdLong();
        offshore.disabledGuilds.put(id, true);
    }

    private void unlockBackingAccount(OffshoreInstance offshore, GuildDB guildDB, DBAlliance alliance) {
        long id = alliance != null ? alliance.getIdLong() : guildDB.getIdLong();
        offshore.disabledGuilds.remove(id);
    }

    private void debitSenderBackingAccount(long now, NationOrAllianceOrGuild account) {
        senderOffshore.getGuildDB().addTransfer(
                now,
                0, 0,
                account.getIdLong(), account.getReceiverType(),
                bankerNation.getId(),
                NOTE,
                amount);
    }

    private void creditReceiverBackingAccount(long now) {
        if (receiverAlliance == null) {
            receiverOffshore.getGuildDB().addTransfer(
                    now,
                    receiverDB.getIdLong(), receiverDB.getReceiverType(),
                    0, 0,
                    bankerNation.getId(),
                    STRUCTURED_NOTE,
                    amount);
        } else {
            receiverOffshore.getGuildDB().addBalance(
                    now,
                    receiverAlliance,
                    bankerNation.getId(),
                    STRUCTURED_NOTE,
                    amount);
        }
    }

    private double[] getSenderNationBalance(boolean requireConfirmation) throws IOException {
        if (senderNation == null)
            return null;

        double[] nationBalance = checkNotNull(
                senderNation.getNetDeposits(null, senderDB, requireConfirmation ? 0 : -1, true),
                "Sender nation balance cannot be null");
        checkDeposits(nationBalance, amount, "nation", senderNation.getMarkdownUrl());
        return nationBalance;
    }

    private double[] getCurrentNationBalance(DBNation nation, GuildDB guildDB) throws IOException {
        return checkNotNull(
                nation.getNetDeposits(null, guildDB, -1, true),
                "Nation balance cannot be null");
    }

    private double[] getSenderBackingBalance(boolean requireConfirmation) throws IOException {
        NationOrAllianceOrGuild senderBacking = resolveBackingAccount(senderDB, senderAlliance);
        double[] balance = getBackingBalance(senderOffshore, senderDB, senderAlliance, !requireConfirmation);
        checkDeposits(balance, amount, "backing account", accountName(senderBacking));
        return balance;
    }

    private double[] getBackingBalance(OffshoreInstance offshore, GuildDB guildDB, DBAlliance alliance, boolean force)
            throws IOException {
        Map<ResourceType, Double> balance = alliance != null
                ? offshore.getDeposits(alliance.getAlliance_id(), force)
                : offshore.getDeposits(guildDB.getIdLong(), force);

        return checkNotNull(ResourceType.resourcesToArray(balance), "Backing account balance cannot be null");
    }

    private TransferResult checkExpectedDelta(
            double[] before,
            double[] after,
            NationOrAllianceOrGuild account,
            boolean increase,
            OffshoreInstance unlockOffshore) {
        double[] diff = ResourceType.getBuffer();
        for (int i = 0; i < after.length; i++) {
            diff[i] = increase ? after[i] - before[i] : before[i] - after[i];
        }

        for (int i = 0; i < amount.length; i++) {
            long diffCents = ArrayUtil.toCents(diff[i]);
            long amountCents = ArrayUtil.toCents(amount[i]);

            if (Math.abs(diffCents - amountCents) > 1) {
                String[] message = {
                        "Internal error: expected "
                                + (increase ? "increase " : "decrease ")
                                + ResourceType.toString(amount)
                                + " but observed "
                                + ResourceType.toString(diff),
                        "Account: " + accountName(account) + " failed to adjust balance. Have a guild admin use: "
                                + CM.bank.unlockTransfers.cmd.nationOrAllianceOrGuild(account.getQualifiedId())
                                + " in " + unlockOffshore.getGuildDB()
                };
                return new TransferResult(OffshoreInstance.TransferStatus.OTHER, account, amount, NOTE)
                        .addMessage(message);
            }
        }
        return null;
    }

    private String accountName(NationOrAllianceOrGuild account) {
        if (account.isGuild())
            return account.asGuild().getGuild().toString();
        if (account.isAlliance())
            return account.asAlliance().getMarkdownUrl();
        return account.asNation().getMarkdownUrl();
    }

    ////////////////////////////////////////////////////////////////
    ////////////////////// Confirmation helpers ////////////////////
    ////////////////////////////////////////////////////////////////

    private String toMarkdown(List<NationOrAllianceOrGuild> senderOrReceiver, boolean isSender) {
        if (senderOrReceiver.isEmpty()) {
            return isSender ? "[Add Balance]" : "[Donate]";
        }
        return senderOrReceiver.stream().map(f -> {
            if (f.isGuild())
                return f.asGuild().getGuild().toString();
            if (f.isAlliance())
                return f.asAlliance().getMarkdownUrl();
            return f.asNation().getMarkdownUrl();
        }).collect(Collectors.joining(" | "));
    }

    private String getReceiverDisplayName() {
        List<String> parts = new ArrayList<>();
        if (receiverAlliance != null) {
            parts.add(receiverAlliance.getMarkdownUrl());
        } else if (receiverDB != null) {
            parts.add(receiverDB.getGuild().toString());
        }
        if (receiverNation != null) {
            parts.add(receiverNation.getMarkdownUrl());
        }
        return parts.isEmpty() ? "[Donate]" : String.join(" | ", parts);
    }

    private TransferResult createConfirmation(TransferPlan plan) {
        StringBuilder body = new StringBuilder();

        List<NationOrAllianceOrGuild> senders = new ArrayList<>();
        if (plan.debitSenderBacking) {
            senders.add(plan.senderBacking);
        }
        if (plan.debitSenderNation) {
            senders.add(senderNation);
        }

        String receiverName = getReceiverDisplayName();

        List<String> actions = new ArrayList<>();
        if (plan.crossesOffshore) {
            actions.add(
                    "Funds will be sent in-game to the offshore: " + receiverOffshore.getAlliance().getMarkdownUrl());
        } else if (plan.movesBacking) {
            actions.add("Backing/offshore funds will move internally within the same offshore");
        } else {
            actions.add("No backing/offshore funds will move");
        }

        if (plan.debitSenderBacking) {
            actions.add("Deducting from sender backing/offshore account");
        } else {
            actions.add("Will **NOT** deduct from sender backing/offshore account");
        }

        if (plan.creditReceiverBacking) {
            actions.add("Crediting receiver backing/offshore account");
        } else if (senderNation != null && receiverNation == null && plan.sameBacking) {
            actions.add("Will **NOT** credit receiver backing/offshore account (same-backing donation)");
        } else {
            actions.add("Will **NOT** credit any receiver backing/offshore account");
        }

        if (plan.debitSenderNation) {
            actions.add("Deducting from sender nation balance (local guild balance)");
        } else {
            actions.add("Will **NOT** deduct from any nation balance");
        }

        if (plan.creditReceiverNation) {
            actions.add("Crediting receiver nation balance");
        } else {
            actions.add("Will **NOT** credit any receiver nation balance");
        }

        String type = plan.crossesOffshore ? "Account" : "Internal";
        String title = type + " transfer ~$" + MathMan.format(ResourceType.convertedTotal(amount)) + " to "
                + receiverName;

        body.append("**").append(title).append("**\n");
        actions.forEach(f -> body.append("- ").append(f).append("\n"));
        body.append("\n");

        body.append("**Amount:** `").append(ResourceType.toString(amount)).append("`\n");
        body.append("- worth: ~$").append(MathMan.format(ResourceType.convertedTotal(amount))).append("\n");
        body.append("**Sender**: ").append(toMarkdown(senders, true)).append("\n");
        body.append("**Receiver**: ").append(receiverName).append("\n");
        body.append("**Note**: `").append(NOTE).append("`\n");
        body.append("**Banker**: ");
        if (banker != null)
            body.append(banker.getAsMention()).append(" ");
        if (bankerNation != null)
            body.append(bankerNation.getMarkdownUrl());
        body.append("\n");

        return new TransferResult(OffshoreInstance.TransferStatus.CONFIRMATION, receiverDB, amount, NOTE)
                .addMessage(body.toString());
    }

    ////////////////////////////////////////////////////////////////
    ////////////////////// Argument checks /////////////////////////
    ////////////////////////////////////////////////////////////////

    private void checkNonNegative(double[] amount) {
        for (int i = 0; i < amount.length; i++) {
            if (!Double.isFinite(amount[i]) || amount[i] < 0) {
                throw new IllegalArgumentException("You cannot send negative amounts for " + ResourceType.values[i]);
            }
        }
    }

    private void checkArgsNotNull(
            GuildDB senderDB,
            DBAlliance senderAlliance,
            DBNation senderNation,
            GuildDB receiverDB,
            DBAlliance receiverAlliance,
            DBNation receiverNation) {
        if (senderDB == null && senderNation == null && senderAlliance == null) {
            throw new IllegalArgumentException("Sender cannot be null");
        }
        if (receiverDB == null && receiverNation == null && receiverAlliance == null) {
            throw new IllegalArgumentException("Receiver cannot be null");
        }
        if (senderDB == null) {
            throw new IllegalArgumentException("Sender DB cannot be null");
        }
    }

    private void checkNationInGuild(GuildDB guildDB, DBNation nation, String side) {
        if (guildDB == null || nation == null)
            return;

        Set<Integer> aaIds = guildDB.getAllianceIds();
        if (!aaIds.isEmpty() && !aaIds.contains(nation.getAlliance_id())) {
            throw new IllegalArgumentException(side + " nation: " + nation.getMarkdownUrl()
                    + " is not in an alliance registered to: " + guildDB.getGuild());
        }
    }

    private DBAlliance checkSenderAlliance(GuildDB senderDB, DBAlliance senderAlliance, DBNation senderNation) {
        if (senderAlliance != null) {
            if (!senderDB.isAllianceId(senderAlliance.getId())) {
                throw new IllegalArgumentException("Sender alliance: " + senderAlliance.getAlliance_id()
                        + " is not registered to: " + senderDB.getGuild());
            }
            if (senderNation != null && senderAlliance.getAlliance_id() != senderNation.getAlliance_id()) {
                throw new IllegalArgumentException("Sender alliance: " + senderAlliance.getAlliance_id()
                        + " does not match nation: " + senderNation.getNation());
            }
        }
        return senderAlliance;
    }

    private DBAlliance checkReceiverAlliance(GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation) {
        if (receiverAlliance != null) {
            if (!receiverDB.isAllianceId(receiverAlliance.getId())) {
                throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id()
                        + " is not registered to receiver DB: " + receiverDB.getGuild());
            }
            if (receiverNation != null && receiverAlliance.getAlliance_id() != receiverNation.getAlliance_id()) {
                throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id()
                        + " does not match nation: " + receiverNation.getNation());
            }
        }
        return receiverAlliance;
    }

    private GuildDB checkReceiverDB(GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation) {
        if (receiverDB == null) {
            if (receiverAlliance != null) {
                receiverDB = receiverAlliance.getGuildDB();
                if (receiverDB == null) {
                    throw new IllegalArgumentException(
                            "No guild found for alliance: " + receiverAlliance.getMarkdownUrl()
                                    + ". Register to a guild using "
                                    + CM.settings_default.registerAlliance.cmd.toSlashMention());
                }
            } else if (receiverNation != null) {
                DBAlliance nationAlliance = receiverNation.getAlliance();
                if (nationAlliance == null) {
                    throw new IllegalArgumentException(
                            "No alliance found for nation: " + receiverNation.getMarkdownUrl());
                }
                receiverDB = nationAlliance.getGuildDB();
                if (receiverDB == null) {
                    throw new IllegalArgumentException("No guild found for alliance: " + nationAlliance.getMarkdownUrl()
                            + ". Register to a guild using "
                            + CM.settings_default.registerAlliance.cmd.toSlashMention());
                }
            } else {
                throw new IllegalArgumentException(
                        "No receiver guild or alliance specified. Please specify a guild using the `receiver_account` argument, or the alliance with `receiver_account`");
            }
        }

        if (receiverAlliance != null && !receiverDB.isAllianceId(receiverAlliance.getId())) {
            throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id()
                    + " is not registered to receiver DB: " + receiverDB.getGuild());
        }

        return receiverDB;
    }

    private void checkReceiverActive(GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation) {
        boolean isActive = false;
        if (receiverAlliance != null) {
            if (!receiverDB.isAllianceId(receiverAlliance.getId())) {
                throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id()
                        + " is not registered to receiver: " + receiverDB.getGuild());
            }
            if (receiverNation != null && receiverAlliance.getAlliance_id() != receiverNation.getAlliance_id()) {
                throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id()
                        + " does not match nation: " + receiverNation.getNation());
            }
            if (receiverAlliance
                    .getNations(
                            f -> f.getPositionEnum().id >= Rank.HEIR.id && f.active_m() < 10080 && f.getVm_turns() == 0)
                    .isEmpty()) {
                throw new IllegalArgumentException("The alliance: " + receiverAlliance.getMarkdownUrl()
                        + " has no active leaders or heirs (<7d, no Vacation Mode)");
            } else {
                isActive = true;
            }
            Set<Integer> aaIds = receiverDB.getAllianceIds();
            if (!aaIds.isEmpty() && !aaIds.contains(receiverAlliance.getAlliance_id())) {
                throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id()
                        + " does not match guild AA: " + StringMan.getString(aaIds));
            }
        }
        if (!isActive) {
            if (!receiverDB.isOwnerActive()) {
                throw new IllegalArgumentException("Receiver guild owner has no registered active nation ("
                        + receiverDB.getGuild().getOwner() + ") Are they registered or in vacation mode?");
            }
        }
        if (receiverNation != null) {
            if (receiverNation.getVm_turns() > 0)
                throw new IllegalArgumentException(
                        "Receiver nation (" + receiverNation.getMarkdownUrl() + ") is in VM");
            if (receiverNation.active_m() > 10000)
                throw new IllegalArgumentException(
                        "Receiver nation (" + receiverNation.getMarkdownUrl() + ") is inactive in-game");
        }
    }

    private void checkNationMember(DBNation senderNation, DBNation receiverNation) {
        if (senderNation != null && senderNation.getPositionEnum().id < Rank.MEMBER.id) {
            throw new IllegalArgumentException("Sender Nation " + senderNation.getNation() + " is not member in-game");
        }
        if (receiverNation != null && receiverNation.getPositionEnum().id < Rank.MEMBER.id) {
            throw new IllegalArgumentException(
                    "Receiver Nation " + receiverNation.getNation() + " is not member in-game");
        }
    }

    private void checkWithdrawPerms(
            int allianceId,
            boolean hasEcon,
            boolean canWithdrawSelf,
            GuildDB senderDB,
            DBNation senderNation,
            DBNation bankerNation,
            User banker) {
        if (!canWithdrawSelf) {
            Map<Long, Role> roles = Roles.ECON_WITHDRAW_SELF.toRoleMap(senderDB);
            Role role = roles.getOrDefault((long) allianceId, roles.get(0L));
            if (role != null) {
                throw new IllegalArgumentException(
                        "Missing " + role.getName() + " to withdraw from the sender guild: " + senderDB.getGuild());
            } else {
                throw new IllegalArgumentException("No permission to withdraw from: " + senderDB.getGuild()
                        + " see: " + CM.role.setAlias.cmd.toSlashMention()
                        + " with roles: " + Roles.ECON_WITHDRAW_SELF + "," + Roles.ECON);
            }
        }
        if (!hasEcon && !Roles.MEMBER.has(banker, senderDB.getGuild())) {
            throw new IllegalArgumentException(
                    "Banker " + banker.getName() + " does not have the member role in " + senderDB.getGuild()
                            + ". See: " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (senderNation == null && !hasEcon) {
            Map<Long, Role> roles = Roles.ECON.toRoleMap(senderDB);
            Role role = roles.getOrDefault((long) allianceId, roles.get(0L));
            if (role == null) {
                throw new IllegalArgumentException(
                        "You cannot send from the alliance account (Did you instead mean to send from your deposits?). See: "
                                + CM.role.setAlias.cmd.toSlashMention() + " with role " + Roles.ECON);
            } else {
                throw new IllegalArgumentException(
                        "You cannot send from the alliance account (Did you instead mean to send from your deposits?). Missing role "
                                + role.getName());
            }
        }
        if (!hasEcon && senderNation.getNation_id() != bankerNation.getNation_id()) {
            throw new IllegalArgumentException(
                    "Lacking role: " + Roles.ECON + " (see " + CM.role.setAlias.cmd.toSlashMention()
                            + "). You do not have permission to send from other nations");
        }
        if (!hasEcon && senderDB.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW) != Boolean.TRUE) {
            throw new IllegalArgumentException(
                    "Lacking role: " + Roles.ECON + " (see " + CM.role.setAlias.cmd.toSlashMention()
                            + "). Member withdrawals are not enabled, see: "
                            + GuildKey.MEMBER_CAN_WITHDRAW.getCommandMention());
        }
    }

    private void checkChange(
            GuildDB senderDB,
            DBAlliance senderAlliance,
            DBNation senderNation,
            GuildDB receiverDB,
            DBAlliance receiverAlliance,
            DBNation receiverNation) {
        InternalTransferPlanner.plan(
                buildPlannerRequest(
                        senderDB,
                        senderAlliance,
                        senderNation,
                        receiverDB,
                        receiverAlliance,
                        receiverNation,
                        0L,
                        0L));
    }

    private void checkNotGreater(double[] amount, long maxValue) {
        double value = ResourceType.convertedTotal(amount);
        if (value > maxValue + 1) {
            throw new IllegalArgumentException("Cannot send more than $" + MathMan.format(maxValue)
                    + " worth in a single transfer. (`" + ResourceType.toString(amount)
                    + "` is worth ~$" + MathMan.format(value) + ")");
        }
    }

    private void checkOffshores(
            OffshoreInstance senderOffshore,
            GuildDB senderDB,
            DBAlliance senderAlliance,
            DBNation senderNation,
            OffshoreInstance receiverOffshore,
            GuildDB receiverDB,
            DBAlliance receiverAlliance,
            DBNation receiverNation) {
        if (senderOffshore == null) {
            throw new IllegalArgumentException("Sender Guild: " + senderDB.getGuild() + " has no offshore. See: "
                    + CM.offshore.add.cmd.toSlashMention());
        }
        if (receiverOffshore == null) {
            throw new IllegalArgumentException("Receiver Guild: " + receiverDB.getGuild() + " has no offshore. See: "
                    + CM.offshore.add.cmd.toSlashMention());
        }

        checkValidOffshore(senderOffshore, senderDB, "Sender");
        checkValidOffshore(receiverOffshore, receiverDB, "Receiver");

        checkOffshoreDisabled(senderOffshore, senderDB, senderAlliance, senderNation);
        checkOffshoreDisabled(senderOffshore, receiverDB, receiverAlliance, receiverNation);

        if (senderOffshore.getAllianceId() != receiverOffshore.getAllianceId()) {
            checkOffshoreDisabled(receiverOffshore, senderDB, senderAlliance, senderNation);
            checkOffshoreDisabled(receiverOffshore, receiverDB, receiverAlliance, receiverNation);
        }

        checkHasAccount(senderOffshore, senderDB, senderAlliance, "Sender");
        checkHasAccount(receiverOffshore, receiverDB, receiverAlliance, "Receiver");
    }

    private void checkHasAccount(OffshoreInstance offshore, GuildDB guildDB, DBAlliance alliance, String side) {
        if (alliance != null) {
            if (!offshore.hasAccount(alliance)) {
                throw new IllegalArgumentException(
                        "Offshore does not have account for " + side.toLowerCase() + " alliance: "
                                + alliance.getMarkdownUrl() + ". Please use: "
                                + CM.offshore.add.cmd.offshoreAlliance(offshore.getAlliance().getQualifiedId())
                                + " in " + guildDB.getGuild());
            }
        } else if (!offshore.hasAccount(guildDB)) {
            throw new IllegalArgumentException("Offshore does not have account for " + side.toLowerCase() + " guild: "
                    + guildDB.getGuild() + ". Please use: "
                    + CM.offshore.add.cmd.offshoreAlliance(offshore.getAlliance().getQualifiedId())
                    + " in " + guildDB.getGuild());
        }
    }

    private void checkOffshoreDisabled(OffshoreInstance offshore, GuildDB server, DBAlliance alliance,
            DBNation nation) {
        if (offshore.isDisabled(server.getIdLong())) {
            throw new IllegalArgumentException("Error sending to " + server.getGuild()
                    + ". Please use: "
                    + CM.offshore.unlockTransfers.cmd.nationOrAllianceOrGuild(server.getIdLong() + "")
                    + " in " + offshore.getGuild());
        }
        if (alliance != null && offshore.isDisabled(alliance.getId())) {
            throw new IllegalArgumentException("Error with account " + alliance.getMarkdownUrl()
                    + ". Please use: "
                    + CM.offshore.unlockTransfers.cmd.nationOrAllianceOrGuild(alliance.getQualifiedId())
                    + " in " + offshore.getGuild());
        }
        if (nation != null && offshore.disabledNations.containsKey(nation.getId())) {
            throw new IllegalArgumentException("Error with account " + nation.getMarkdownUrl()
                    + ". Please use: " + CM.offshore.unlockTransfers.cmd.nationOrAllianceOrGuild(nation.getId() + "")
                    + " in " + offshore.getGuild());
        }
    }

    private void checkDeposits(double[] deposits, double[] amount, String senderTypeStr, String senderName) {
        double[] normalized = PW.normalize(deposits);

        if (ResourceType.convertedTotal(deposits) <= 0) {
            throw new IllegalArgumentException(
                    "Sender " + senderTypeStr + " (" + senderName + ") does not have any deposits");
        }

        for (int i = 0; i < deposits.length; i++) {
            if (ArrayUtil.toCents(amount[i]) > ArrayUtil.toCents(normalized[i])) {
                String msg = "Sender " + senderTypeStr + " (" + senderName + ") can only send "
                        + MathMan.format(normalized[i]) + "x" + ResourceType.values[i]
                        + "(not " + MathMan.format(amount[i]) + ")";
                if (ArrayUtil.toCents(amount[i]) > ArrayUtil.toCents(deposits[i])) {
                    throw new IllegalArgumentException(msg);
                } else {
                    throw new IllegalArgumentException(
                            msg + "\nNote: Transfer limit is reduced by negative resources in deposits");
                }
            }
        }
    }

    private boolean isDonationOnly(TransferPlan plan) {
        return plan.sameBacking
                && plan.debitSenderNation
                && !plan.debitSenderBacking
                && !plan.creditReceiverBacking
                && !plan.creditReceiverNation;
    }

    private void checkValidOffshore(OffshoreInstance offshore, GuildDB guildDB, String side) {
        if (offshore.getAllianceId() <= 0 || offshore.getAlliance() == null) {
            throw new IllegalArgumentException(
                    side + " offshore for " + guildDB.getGuild()
                            + " is invalid. Please reconfigure the offshore.");
        }
    }

    private static NationOrAllianceOrGuild resolveBackingAccount(GuildDB guildDB, DBAlliance alliance) {
        return alliance != null ? alliance : guildDB;
    }

    private static InternalTransferPlanner.BackingAccount toPlannerBacking(NationOrAllianceOrGuild account) {
        if (account.isAlliance()) {
            return InternalTransferPlanner.BackingAccount.alliance(account.asAlliance().getId());
        }
        if (account.isGuild()) {
            return InternalTransferPlanner.BackingAccount.guild(account.asGuild().getIdLong());
        }
        throw new IllegalArgumentException(
                "Backing account must be a guild or alliance: " + account.getQualifiedName());
    }

    private static InternalTransferPlanner.Request buildPlannerRequest(
            GuildDB senderDB,
            DBAlliance senderAlliance,
            DBNation senderNation,
            GuildDB receiverDB,
            DBAlliance receiverAlliance,
            DBNation receiverNation,
            long senderOffshoreId,
            long receiverOffshoreId) {
        NationOrAllianceOrGuild senderBacking = resolveBackingAccount(senderDB, senderAlliance);
        NationOrAllianceOrGuild receiverBacking = resolveBackingAccount(receiverDB, receiverAlliance);

        return new InternalTransferPlanner.Request(
                senderDB.getIdLong(),
                senderNation != null ? senderNation.getId() : null,
                toPlannerBacking(senderBacking),

                receiverDB.getIdLong(),
                receiverNation != null ? receiverNation.getId() : null,
                toPlannerBacking(receiverBacking),

                senderOffshoreId,
                receiverOffshoreId);
    }
}