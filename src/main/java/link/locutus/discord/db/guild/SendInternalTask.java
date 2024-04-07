package link.locutus.discord.db.guild;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
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

public class SendInternalTask {
    private final static Map<Long, Double> CURR_TURN_VALUE_BY_USER = new Long2ObjectOpenHashMap<>();
    private final static Map<Long, Double> LAST_TURN_BY_USER = new Long2ObjectOpenHashMap<>();
    private static long CURR_TURN = 0;

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

    public SendInternalTask(@Me User banker, @Me DBNation bankerNation, GuildDB senderDB, DBAlliance senderAlliance, DBNation senderNation, GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation, double[] amount) throws IOException {
        if (OffshoreInstance.DISABLE_TRANSFERS) throw new IllegalArgumentException("Error: Maintenance");
        checkNotNull(bankerNation, "No banker specified. Register with " + CM.register.cmd.toSlashMention());
        checkArgsNotNull(senderDB, senderAlliance, senderNation, receiverDB, receiverAlliance, receiverNation);
        checkNonNegative(amount);
        checkNotGreater(amount, 2_000_000_000);

        senderAlliance = checkSenderAlliance(senderDB, senderAlliance, senderNation);
        receiverAlliance = checkReceiverAlliance(receiverDB, receiverAlliance, receiverNation);
        receiverDB = checkReceiverDB(receiverDB, receiverAlliance);

        checkReceiverActive(receiverDB, receiverAlliance, receiverNation);
        checkNationMember(senderNation, receiverNation);

        // TODO check if they have member role and member withdraw self with that alliance

        int roleAA = senderAlliance != null ? senderAlliance.getId() : 0;
        boolean hasEcon = Roles.ECON.has(banker, senderDB.getGuild(), roleAA);
        boolean canWithdrawSelf = hasEcon || Roles.ECON_WITHDRAW_SELF.has(banker, senderDB.getGuild(), roleAA);
        checkWithdrawPerms(roleAA, hasEcon, canWithdrawSelf, senderDB, senderNation, bankerNation, banker);

        this.senderOffshore = senderDB.getOffshore();
        this.receiverOffshore = receiverDB.getOffshore();
        checkOffshores(senderOffshore, senderDB, senderAlliance, senderNation, receiverOffshore, receiverDB, receiverAlliance, receiverNation);

        this.receiverChannel = receiverDB.getResourceChannel(receiverAlliance != null ? receiverAlliance.getId() : 0);
        checkNotNull(receiverChannel, "Please have an admin set: " + GuildKey.RESOURCE_REQUEST_CHANNEL.getCommandMention() + " in receiving " + receiverDB.getGuild());
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

        checkTransferLimits(banker.getIdLong(), amount, 6_000_000_000L);
    }

    ////////////////////////////////////////////////
    //////////////// Public methods ////////////////
    ////////////////////////////////////////////////

    private String toMarkdown(List<NationOrAllianceOrGuild> senderOrReceiver, boolean isSender) {
        if (senderOrReceiver.isEmpty()) {
            return isSender ? "[Add Balance]" : "[Donate]";
        }
        return senderOrReceiver.stream().map(f -> {
            if (f.isGuild()) return f.asGuild().getGuild().toString();
            if (f.isAlliance()) return f.asAlliance().getMarkdownUrl();
            return f.asNation().getMarkdownUrl();
        }).collect(Collectors.joining(" | "));
    }

    public List<TransferResult> send(boolean requireConfirmation) throws IOException {
        if (!TimeUtil.checkTurnChange()) {
            return List.of(new TransferResult(OffshoreInstance.TransferStatus.TURN_CHANGE, receiverDB, amount, "#deposit").addMessage(OffshoreInstance.TransferStatus.TURN_CHANGE.getMessage()));
        }
        long now = System.currentTimeMillis();
        double[] nationBalance = getNationBalance(requireConfirmation);
        boolean checkAccount = checkOffshoreAccountBalance();
        double[] accountBalance = checkAccount ? getOffshoreAccountBalance(requireConfirmation) : null;

        if (requireConfirmation) {
            return List.of(createConfirmation(accountBalance, nationBalance));
        }

        List<TransferResult> results = new ArrayList<>();
        try {
            // subtract from nation account
            if (nationBalance != null) {
                senderOffshore.disabledNations.put(senderNation.getId(), now);
                senderDB.subtractBalance(now, senderNation, bankerNation.getNation_id(), "#deposit", amount);
                double[] newBalance = senderNation.getNetDeposits(senderDB, -1, true);
                TransferResult error = checkDiff(nationBalance, newBalance, senderNation);
                if (error != null) return List.of(error);
            }

            // add to disabled
            if (accountBalance != null) {
                senderOffshore.disabledGuilds.put(senderDB.getIdLong(), true);
                // subtract from offshore account
                NationOrAllianceOrGuild account = senderAlliance != null ? senderAlliance : senderDB;
                senderOffshore.getGuildDB().addTransfer(now, 0, 0, account.getIdLong(), account.getReceiverType(), bankerNation.getId(), "#deposit", amount);
                // ensure subtraction is correct, else throw error (include instructions to unlock account)
                double[] newBalance = ResourceType.resourcesToArray(senderAlliance != null ? senderOffshore.getDeposits(senderAlliance.getAlliance_id(), false) : senderOffshore.getDeposits(senderDB.getIdLong(), false));
                TransferResult error = checkDiff(accountBalance, newBalance, account);
                if (error != null) return List.of(error);
            }

            // add to offshore account
            if (accountBalance != null) {
                if (senderOffshore.getAllianceId() != receiverOffshore.getAllianceId()) {
                    TransferResult transfer = senderOffshore.transferUnsafe(null, receiverOffshore.getAlliance(), ResourceType.resourcesToMap(amount), "#ignore");
                    results.add(transfer);
                    if (!transfer.getStatus().isSuccess()) {
                        transfer.addMessage("Failed to transfer to " + receiverOffshore.getAlliance().getMarkdownUrl() + " in-game. See " + CM.offshore.unlockTransfers.cmd.create(senderDB.getIdLong() + "", null) + " in " + senderOffshore.getGuild());
                        return results;
                    } else {
                        transfer.addMessage("Transferred to " + receiverOffshore.getAlliance().getMarkdownUrl() + " in-game");
                    }
                }
                if (receiverAlliance == null) {
                    receiverOffshore.getGuildDB().addTransfer(now, receiverDB.getIdLong(), receiverDB.getReceiverType(), 0, 0, bankerNation.getId(), "#deposit", amount);
                    results.add(new TransferResult(OffshoreInstance.TransferStatus.SUCCESS, receiverDB, amount, "#deposit").addMessage("Added to guild account: " + receiverDB.getGuild().toString()));
                } else {
                    receiverOffshore.getGuildDB().addBalance(now, receiverAlliance, bankerNation.getId(), "#deposit", amount);
                    results.add(new TransferResult(OffshoreInstance.TransferStatus.SUCCESS, receiverAlliance, amount, "#deposit").addMessage("Added to alliance account " + receiverAlliance.getMarkdownUrl()));
                }
            }
            // add to nation account
            if (receiverNation != null && (senderDB != receiverDB || !Objects.equals(receiverNation, senderNation))) {
                // add to receiverDB nation account #deposit
                receiverDB.addBalance(now, receiverNation, bankerNation.getId(), "#deposit", amount);
                results.add(new TransferResult(OffshoreInstance.TransferStatus.SUCCESS, receiverNation, amount, "#deposit").addMessage("Added to nation account " + receiverNation.getMarkdownUrl()));
            }

            // undisabled
            if (nationBalance != null) {
                senderOffshore.disabledNations.remove(senderNation.getId());
            }
            if (accountBalance != null) {
                senderOffshore.disabledGuilds.remove(senderDB.getIdLong());
            }

            try {
                if (receiverChannel.canTalk()) {
                    StringBuilder message = new StringBuilder("Internal Transfer ");
                    if (senderDB != receiverDB) message.append(senderDB.getGuild() + " ");
                    if (senderAlliance != null && !Objects.equals(senderAlliance, receiverAlliance))
                        message.append(" AA:" + senderAlliance.getName());
                    if (senderNation != null) message.append(" " + senderNation.getName());
                    message.append(" -> ");
                    if (senderDB != receiverDB) message.append(receiverDB.getGuild() + " ");
                    if (receiverAlliance != null && !Objects.equals(senderAlliance, receiverAlliance))
                        message.append(" AA:" + receiverAlliance.getName());
                    if (receiverNation != null) message.append(" " + receiverNation.getName());
                    message.append(": " + ResourceType.resourcesToString(amount) + ", note: `#deposit`");
                    RateLimitUtil.queueMessage(receiverChannel, message.toString(), true);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }

            return results;
        } catch (Throwable e) {
            e.printStackTrace();
            results.add(new TransferResult(OffshoreInstance.TransferStatus.OTHER, receiverDB, amount, "#deposit").addMessage("Internal error: " + e.getMessage()));
            return results;
        }
    }

    private boolean checkOffshoreAccountBalance() {
        if (senderAlliance != null) {
            if (senderDB != receiverDB) {
                return true;
            } else if (receiverAlliance != null) {
                return senderAlliance.getId() != receiverAlliance.getId();
            } else {
                throw new IllegalArgumentException("Cannot send to alliance in same guild when sender alliance is null");
            }
        } else {
            if (senderDB != receiverDB) {
                return true;
            } else if (receiverAlliance != null) {
                throw new IllegalArgumentException("Cannot send to alliance in same guild when sender alliance is null");
            } else if (senderOffshore.getAllianceId() != receiverOffshore.getAllianceId()) {
                throw new IllegalArgumentException("Cannot send to offshore in different guild when sender alliance is null");
            } else {
                return false;
            }
        }
    }

    private double[] getOffshoreAccountBalance(boolean requireConfirmation) throws IOException {
        double[] depoArr;
        String senderStr;
        if (senderAlliance != null) {
            Map<ResourceType, Double> balance = senderOffshore.getDeposits(senderAlliance.getAlliance_id(), !requireConfirmation);
            depoArr = checkNotNull(ResourceType.resourcesToArray(balance), "Sender alliance balance cannot be null");
            senderStr = senderAlliance.getMarkdownUrl();
        } else {
            Map<ResourceType, Double> balance = senderOffshore.getDeposits(senderDB.getIdLong(), !requireConfirmation);
            depoArr = checkNotNull(ResourceType.resourcesToArray(balance), "Sender guild balance cannot be null");
            senderStr = senderDB.getGuild().toString();
        }
        checkDeposits(depoArr, amount, "offshore account", senderStr);
        return depoArr;
    }

    private TransferResult createConfirmation(double[] accountBalance, double[] nationBalance) {
        StringBuilder body = new StringBuilder();
        List<NationOrAllianceOrGuild> senders = new ArrayList<>();
        List<NationOrAllianceOrGuild> receivers = new ArrayList<>();
        if (!Objects.equals(senderDB, receiverDB)) {
            senders.add(senderDB);
            receivers.add(receiverDB);
        }
        if (!Objects.equals(senderAlliance, receiverAlliance)) {
            if (senderAlliance != null) senders.add(senderAlliance);
            if (receiverAlliance != null) receivers.add(receiverAlliance);
        }
        if (senderDB != receiverDB || !Objects.equals(senderNation, receiverNation)) {
            if (senderNation != null) senders.add(senderNation);
            if (receiverNation != null) receivers.add(receiverNation);
        }

        List<String> actions = new ArrayList<>();
        boolean notInternal = accountBalance != null && senderOffshore.getAllianceId() != receiverOffshore.getAllianceId();
        if (notInternal) {
            actions.add("Funds will be sent in-game to the offshore: " + receiverOffshore.getAlliance().getMarkdownUrl());
        } else {
            actions.add("Funds will be sent internally");
        }
        if (accountBalance != null) {
            actions.add("Deducting from offshore balance");
        } else {
            actions.add("Will **NOT** deduct from any offshore balance");
        }
        if (nationBalance != null) {
            actions.add("Deducting from nation balance (this server)");
        } else {
            actions.add("Will **NOT** deduct from any nation balance");
        }
        String receiverName = receivers.isEmpty() ? "[Donate]" : receivers.stream()
                .map(NationOrAllianceOrGuild::getQualifiedName).collect(Collectors.joining(" | "));
        String type = notInternal ? "Account" : "Internal";
        String title = type + " transfer ~$" + MathMan.format(ResourceType.convertedTotal(amount)) + " to " + receiverName;
        body.append("**" + title + "**\n");

        actions.forEach(f -> body.append("- " + f + "\n"));
        body.append("\n");

        String note = "#deposit";
        body.append("**Amount:** `" + ResourceType.resourcesToString(amount) + "`\n- worth: ~$" + MathMan.format(ResourceType.convertedTotal(amount)) + "\n");
        body.append("**Sender**: " + toMarkdown(senders, true) + "\n");
        body.append("**Receiver**: " + receiverName + "\n");
        body.append("**Note**: `" + note + "`\n");
        body.append("**Banker**: ");
        if (banker != null) {
            body.append(banker.getAsMention() + " ");
        }
        if (bankerNation != null) {
            body.append(bankerNation.getMarkdownUrl());
        }
        body.append("\n");
        return new TransferResult(OffshoreInstance.TransferStatus.CONFIRMATION, receiverDB, amount, "#deposit").addMessage(body.toString());
    }

    private double[] getNationBalance(boolean requireConfirmation) throws IOException {
        if (senderNation != null) {
            double[] nationBalance = checkNotNull(senderNation.getNetDeposits(senderDB, requireConfirmation ? 0 : -1, true), "Sender nation balance cannot be null");
            checkDeposits(nationBalance, amount, "nation", senderNation.getMarkdownUrl());
            return nationBalance;
        }
        return null;
    }

    /////////////////////////////////////////////////
    //////////////// Argument checks ////////////////
    /////////////////////////////////////////////////

    private void checkNonNegative(double[] amount) {
        for (int i = 0; i < amount.length; i++) {
            if (!Double.isFinite(amount[i]) || amount[i] < 0) {
                throw new IllegalArgumentException("You cannot send negative amounts for " + ResourceType.values[i]);
            }
        }
    }

    private void checkArgsNotNull(GuildDB senderDB, DBAlliance senderAlliance, DBNation senderNation, GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation) {
        if (senderDB == null && senderNation == null && senderAlliance == null)
            throw new IllegalArgumentException("Sender cannot be null");
        if (receiverDB == null && receiverNation == null && receiverAlliance == null)
            throw new IllegalArgumentException("Receiver cannot be null");
        if (senderDB == null) {
            throw new IllegalArgumentException("Sender DB cannot be null");
        }
    }

    private DBAlliance checkSenderAlliance(GuildDB senderDB, DBAlliance senderAlliance, DBNation senderNation) {
        if (senderAlliance == null) {
            Set<Integer> aaIds = senderDB.getAllianceIds();
            if (!aaIds.isEmpty()) {
                if (aaIds.size() == 1) {
                    senderAlliance = DBAlliance.getOrCreate(aaIds.iterator().next());
                } else if (aaIds.contains(senderNation.getAlliance_id())) {
                    senderAlliance = DBAlliance.getOrCreate(senderNation.getAlliance_id());
                } else {
                    throw new IllegalArgumentException("Sender DB " + senderDB + " has multiple alliances: " + StringMan.getString(aaIds) + " and must be specified via the `from_alliance` argument");
                }
            }
        }
        if (senderAlliance != null) {
            if (!senderDB.isAllianceId(senderAlliance.getId())) {
                throw new IllegalArgumentException("Sender alliance: " + senderAlliance.getAlliance_id() + " is not registered to: " + senderDB.getGuild());
            }
            // Possibly can remove this check
            if (senderNation != null && senderAlliance.getAlliance_id() != senderNation.getAlliance_id()) {
                throw new IllegalArgumentException("Sender alliance: " + senderAlliance.getAlliance_id() + " does not match nation: " + senderNation.getNation());
            }
        }
        return senderAlliance;
    }

    private TransferResult checkDiff(double[] nationBalance, double[] newBalance, NationOrAllianceOrGuild account) {
        double[] diff = ResourceType.getBuffer();
        for (int i = 0; i < newBalance.length; i++) {
            diff[i] = nationBalance[i] - newBalance[i];
        }
        for (int i = 0; i < amount.length; i++) {
            if (Math.round((diff[i] - amount[i]) * 100) > 1) {
                String name = account.isGuild() ? account.asGuild().getGuild().toString() : account.isAlliance() ? account.asAlliance().getMarkdownUrl() : account.asNation().getMarkdownUrl();
                String[] message = {"Internal error: " + ResourceType.resourcesToString(diff) + " != " + ResourceType.resourcesToString(amount),
                        "Account: " + name + " failed to adjust balance. Have a guild admin use: " + CM.bank.unlockTransfers.cmd.create(account.getQualifiedId(), null) + " in " + senderOffshore.getGuildDB()};
                return new TransferResult(OffshoreInstance.TransferStatus.OTHER, account, amount, "#deposit").addMessage(message);
            }
        }
        return null;
    }

    private DBAlliance checkReceiverAlliance(GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation) {
        if (receiverAlliance == null) {
            if (receiverDB == null) {
                if (receiverNation != null) {
                    receiverAlliance = receiverNation.getAlliance();
                    if (receiverAlliance == null) {
                        throw new IllegalArgumentException("No Alliance not found for nation: " + receiverNation.getMarkdownUrl());
                    }
                } else {
                    throw new IllegalArgumentException("No receiver specified.");
                }
            } else {
                Set<Integer> aaIds = receiverDB.getAllianceIds();
                if (!aaIds.isEmpty()) {
                    if (aaIds.size() == 1) {
                        receiverAlliance = DBAlliance.getOrCreate(aaIds.iterator().next());
                    } else if (aaIds.contains(receiverNation.getAlliance_id())) {
                        receiverAlliance = receiverNation.getAlliance();
                    } else {
                        throw new IllegalArgumentException("Receiver DB " + receiverDB + " has multiple alliances: " + StringMan.getString(aaIds) + " and must be specified with the `receiver_alliance` argument");
                    }
                }
            }
        }
        return receiverAlliance;
    }

    private GuildDB checkReceiverDB(GuildDB receiverDB, DBAlliance receiverAlliance) {
        if (receiverDB == null) {
            if (receiverAlliance != null) {
                receiverDB = receiverAlliance.getGuildDB();
                if (receiverDB == null) {
                    throw new IllegalArgumentException("No guild found for alliance: " + receiverAlliance.getMarkdownUrl() + ". Register to a guild using " + CM.settings_default.registerAlliance.cmd.toSlashMention());
                }
            } else {
                throw new IllegalArgumentException("No receiver guild or alliance specified. Please specify a guild using the `receiver_guild` argument, or the alliance with `receiver_alliance`");
            }
        }
        if (receiverAlliance != null && !receiverDB.isAllianceId(receiverAlliance.getId())) {
            throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id() + " is not registered to receiver DB: " + receiverDB.getGuild());
        }
        return receiverDB;
    }

    private void checkReceiverActive(GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation) {
        boolean isActive = false;
        if (receiverAlliance != null) {
            if (!receiverDB.isAllianceId(receiverAlliance.getId())) {
                throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id() + " is not registered to receiver: " + receiverDB.getGuild());
            }
            if (receiverNation != null && receiverAlliance.getAlliance_id() != receiverNation.getAlliance_id()) {
                throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id() + " does not match nation: " + receiverNation.getNation());
            }
            if (receiverAlliance.getNations(f -> f.getPositionEnum().id >= Rank.HEIR.id && f.active_m() < 10080 && f.getVm_turns() == 0).isEmpty()) {
                throw new IllegalArgumentException("The alliance: " + receiverAlliance.getMarkdownUrl() + " has no active leaders or heirs (<7d, no Vacation Mode)");
            } else {
                isActive = true;
            }
            Set<Integer> aaIds = receiverDB.getAllianceIds();
            if (!aaIds.isEmpty() && !aaIds.contains(receiverAlliance.getAlliance_id())) {
                throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id() + " does not match guild AA: " + StringMan.getString(aaIds));
            }
        }
        if (!isActive) {
            if (!receiverDB.isOwnerActive()) {
                throw new IllegalArgumentException("Receiver guild owner has no registered active nation (" + receiverDB.getGuild().getOwner() + ") Are they registered or in vacation mode?");
            }
        }
        if (receiverNation != null) {
            if (receiverNation.getVm_turns() > 0)
                throw new IllegalArgumentException("Receiver nation (" + receiverNation.getMarkdownUrl() + ") is in VM");
            if (receiverNation.active_m() > 10000)
                throw new IllegalArgumentException("Receiver nation (" + receiverNation.getMarkdownUrl() + ") is inactive in-game");
        }
    }

    private void checkNationMember(DBNation senderNation, DBNation receiverNation) {
        // Possibly remove
        if (senderNation != null && senderNation.getPositionEnum().id < Rank.MEMBER.id) {
            throw new IllegalArgumentException("Sender Nation " + senderNation.getNation() + " is not member in-game");
        }
        if (receiverNation != null && receiverNation.getPositionEnum().id < Rank.MEMBER.id) {
            throw new IllegalArgumentException("Receiver Nation " + receiverNation.getNation() + " is not member in-game");
        }
    }

    private void checkWithdrawPerms(int allianceId, boolean hasEcon, boolean canWithdrawSelf, GuildDB senderDB, DBNation senderNation, DBNation bankerNation, User banker) {
        if (!canWithdrawSelf) {
            Map<Long, Role> roles = Roles.ECON_WITHDRAW_SELF.toRoleMap(senderDB);
            Role role = roles.getOrDefault((long) allianceId, roles.get(0L));
            if (role != null) {
                throw new IllegalArgumentException("Missing " + role.getName() + " to withdraw from the sender guild: " + senderDB.getGuild());
            } else {
                throw new IllegalArgumentException("No permission to withdraw from: " + senderDB.getGuild() + " see: " + CM.role.setAlias.cmd.toSlashMention() + " with roles: " + Roles.ECON_WITHDRAW_SELF + "," + Roles.ECON);
            }
        }
        if (!hasEcon && !Roles.MEMBER.has(banker, senderDB.getGuild())) {
            throw new IllegalArgumentException("Banker " + banker.getName() + " does not have the member role in " + senderDB.getGuild() + ". See: " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (senderNation == null && !hasEcon) {
            Map<Long, Role> roles = Roles.ECON.toRoleMap(senderDB);
            Role role = roles.getOrDefault((long) allianceId, roles.get(0L));
            if (role == null) {
                throw new IllegalArgumentException("You cannot send from the alliance account (Did you instead mean to send from your deposits?). See: " + CM.role.setAlias.cmd.toSlashMention() + " with role " + Roles.ECON);
            } else {
                throw new IllegalArgumentException("You cannot send from the alliance account (Did you instead mean to send from your deposits?). Missing role " + role.getName());
            }
        }
        if (!hasEcon && senderNation.getNation_id() != bankerNation.getNation_id()) {
            throw new IllegalArgumentException("Lacking role: " + Roles.ECON + " (see " + CM.role.setAlias.cmd.toSlashMention() + "). You do not have permission to send from other nations");
        }
        if (!hasEcon && senderDB.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW) != Boolean.TRUE) {
            throw new IllegalArgumentException("Lacking role: " + Roles.ECON + " (see " + CM.role.setAlias.cmd.toSlashMention() + "). Member withdrawals are not enabled, see: " + GuildKey.MEMBER_CAN_WITHDRAW.getCommandMention());
        }
    }

    private void checkChange(GuildDB senderDB, DBAlliance senderAlliance, DBNation senderNation, GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation) {
        if (Objects.equals(senderDB, receiverDB) && Objects.equals(senderAlliance,receiverAlliance) && Objects.equals(senderNation, receiverNation)) {
            throw new IllegalArgumentException("Sender and receiver cannot be the same");
        }
    }

    private void checkNotGreater(double[] amount, long maxValue) {
        double value = ResourceType.convertedTotal(amount);
        if (value > maxValue + 1) {
            throw new IllegalArgumentException("Cannot send more than $" + MathMan.format(maxValue) + " worth in a single transfer. (`" + ResourceType.resourcesToString(amount) + "` is worth ~$" + MathMan.format(value) + ")");
        }
    }

    private void updateCurrValues(long turn) {
        if (CURR_TURN != turn) {
            synchronized (CURR_TURN_VALUE_BY_USER) {
                LAST_TURN_BY_USER.clear();
                LAST_TURN_BY_USER.putAll(CURR_TURN_VALUE_BY_USER);
                CURR_TURN_VALUE_BY_USER.clear();
                CURR_TURN = turn;
            }
        }
    }

    private void checkTransferLimits(long userId, double[] amount, long maxValue) {
        long turn = TimeUtil.getTurn();
        updateCurrValues(turn);

        double newValue = ResourceType.convertedTotal(amount);
        double currValue = CURR_TURN_VALUE_BY_USER.getOrDefault(userId, 0D);
        double lastValue = LAST_TURN_BY_USER.getOrDefault(userId, 0D);
        double remaining = Math.max(0, maxValue - currValue - lastValue);
        int turnsReset = 0;
        if (newValue + currValue > maxValue + 1) {
            turnsReset = 2;
        }
        if (newValue + currValue + lastValue > maxValue + 1) {
            turnsReset = 1;
        }
        if (turnsReset != 0) {
            StringBuilder msg = new StringBuilder();
            if (remaining > 0) {
                msg.append("You have exceeded the transfer limit for this turn.");
            } else {
                msg.append("You can only send up to $" + MathMan.format(maxValue) + " worth before reaching the transfer limit for this turn.");
            }
            msg.append("Please wait " + turnsReset + " turn" + (turnsReset > 1 ? "s" : "") + " before you can send ~$" + MathMan.format(remaining) + " worth again.");
            AlertUtil.error("Transfer cap exceeded", msg + "\n" + bankerNation.getMarkdownUrl() + " | " + banker.getAsMention(), true);
            throw new IllegalArgumentException(msg.toString());
        }
    }

    private void checkOffshores(OffshoreInstance senderOffshore,
                                GuildDB senderDB,
                                DBAlliance senderAlliance,
                                DBNation senderNation,
                                OffshoreInstance receiverOffshore,
                                GuildDB receiverDB,
                                DBAlliance receiverAlliance,
                                DBNation receiverNation) {
        if (senderOffshore == null) {
            throw new IllegalArgumentException("Sender Guild: " + senderDB.getGuild() + " has no offshore. See: " + CM.offshore.add.cmd.toSlashMention());
        }
        if (receiverOffshore == null) {
            throw new IllegalArgumentException("Receiver Guild: " + receiverDB.getGuild() + " has no offshore. See: " + CM.offshore.add.cmd.toSlashMention());
        }
        checkOffshoreDisabled(senderOffshore, senderDB, senderAlliance, senderNation);
        checkOffshoreDisabled(senderOffshore, receiverDB, receiverAlliance, receiverNation);
        if (senderOffshore.getAllianceId() != receiverOffshore.getAllianceId()) {
            checkOffshoreDisabled(receiverOffshore, senderDB, senderAlliance, senderNation);
            checkOffshoreDisabled(receiverOffshore, receiverDB, receiverAlliance, receiverNation);
        }
        checkHasAccount(senderOffshore, senderDB, senderAlliance);
        checkHasAccount(receiverOffshore, receiverDB, receiverAlliance);
    }

    private void checkHasAccount(OffshoreInstance offshore, GuildDB guildDB, DBAlliance alliance) {
        if (alliance != null) {
            if (!offshore.hasAccount(alliance)) {
                throw new IllegalArgumentException("Offshore does not have account for sender alliance: " + alliance.getMarkdownUrl() + ". Please use: " + CM.offshore.add.cmd.create(offshore.getAlliance().getQualifiedId(), null, null, null) + " in " + guildDB.getGuild());
            }
        } else if (!offshore.hasAccount(guildDB)) {
            throw new IllegalArgumentException("Offshore offshore does not have account for sender guild: " + guildDB.getGuild() + ". Please use: " + CM.offshore.add.cmd.create(offshore.getAlliance().getQualifiedId(), null, null, null) + " in " + guildDB.getGuild());
        }
    }

    private void checkOffshoreDisabled(OffshoreInstance offshore, GuildDB server, DBAlliance alliance, DBNation nation) {
        if (offshore.isDisabled(server.getIdLong())) {
            throw new IllegalArgumentException("Error sending to " + server.getGuild() + ". Please use: " + CM.offshore.unlockTransfers.cmd.create(server.getIdLong() + "", null) + " in " + offshore.getGuild());
        }
        if (alliance != null && offshore.isDisabled(alliance.getId())) {
            throw new IllegalArgumentException("Error with account " + alliance.getMarkdownUrl() + ". Please use: " + CM.offshore.unlockTransfers.cmd.create(alliance.getQualifiedId(), null) + " in " + offshore.getGuild());
        }
        if (nation != null && offshore.disabledNations.containsKey(nation.getId())) {
            throw new IllegalArgumentException("Error with account " + nation.getMarkdownUrl() + ". Please use: " + CM.offshore.unlockTransfers.cmd.create(nation.getId() + "", null) + " in " + offshore.getGuild());
        }
    }

    private void checkDeposits(double[] deposits, double[] amount, String senderTypeStr, String senderName) {
        double[] normalized = PW.normalize(deposits);

        if (ResourceType.convertedTotal(deposits) <= 0) throw new IllegalArgumentException("Sender " + senderTypeStr + " (" + senderName + ") does not have any deposits");

        for (int i = 0; i < deposits.length; i++) {
            if (Math.round(amount[i] * 100) > Math.round(normalized[i] * 100)) {
                String msg = "Sender " + senderTypeStr + " (" + senderName + ") can only send " + MathMan.format(normalized[i]) + "x" + ResourceType.values[i] + "(not " + MathMan.format(amount[i]) + ")";
                if (Math.round(amount[i] * 100) > Math.round(deposits[i] * 100)) {
                    throw new IllegalArgumentException(msg);
                } else {
                    throw new IllegalArgumentException(msg + "\nNote: Transfer limit is reduced by negative resources in deposits");
                }
            }
        }
    }
}
