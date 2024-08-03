package link.locutus.discord.util.offshore;

import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TransferResult {
    private OffshoreInstance.TransferStatus status;
    private final NationOrAllianceOrGuild receiver;
    private final List<String> resultMessage;
    private final double[] amount;
    private final String note;

    public static Map<OffshoreInstance.TransferStatus, Integer> count(Collection<TransferResult> list) {
        Map<OffshoreInstance.TransferStatus, Integer> map = new HashMap<>();
        for (TransferResult result : list) {
            map.put(result.getStatus(), map.getOrDefault(result.getStatus(), 0) + 1);
        }
        return ArrayUtil.sortMap(map, false);
    }

    public static String toFileString(Collection<TransferResult> list) {
        return "Receiver\tStatus\nNote\nMessage" +
                list.stream().map(f ->
                        f.getReceiver().getName() + "\t" +
                        f.getStatus().name() + "\t" +
                        f.getNote() + "\t" +
                        f.getStatus().getMessage()
                ).collect(Collectors.joining("\n"));
    }

    public static Map.Entry<String, String> toEmbed(List<TransferResult> results) {
        String title, body;
        if (results.size() == 1) {
            TransferResult result = results.get(0);
            title = result.toTitleString();
            body = result.toEmbedString();
        } else {
            int success = results.stream().mapToInt(f -> f.getStatus().isSuccess() ? 1 : 0).sum();
            int failed = results.size() - success;
            title = success > 0 ? failed > 0 ? "Error transferring" : "Successfully transferred" : "Aborted transfer";
            if (failed > 0) {
                title += " (" + success + " successful, " + failed + " failed)";
            }
            body = results.stream().map(TransferResult::toLineString).collect(Collectors.joining("\n"));
        }
        return Map.entry(title, body);
    }

    public TransferResult(OffshoreInstance.TransferStatus status, NationOrAllianceOrGuild receiver, Map<ResourceType, Double> amount, String note) {
        this(status, receiver, ResourceType.resourcesToArray(amount), note);
    }

    public static Map<NationOrAllianceOrGuild, TransferResult> toMap(List<TransferResult> list) {
        return list.stream().collect(Collectors.toMap(TransferResult::getReceiver, Function.identity()));
    }

    public void setStatus(OffshoreInstance.TransferStatus status) {
        this.status = status;
    }

    public TransferResult(OffshoreInstance.TransferStatus status, NationOrAllianceOrGuild receiver, double[] amount, String note) {
        this.status = status;
        this.receiver = receiver;
        this.resultMessage = new ArrayList<>();
        this.amount = amount;
        this.note = note;
    }

    public TransferResult addMessage(String... messages) {
        this.resultMessage.addAll(Arrays.asList(messages));
        return this;
    }

    public TransferResult addMessages(List<String> messages) {
        this.resultMessage.addAll(messages);
        return this;
    }

    public OffshoreInstance.TransferStatus getStatus() {
        return status;
    }

    public NationOrAllianceOrGuild getReceiver() {
        return receiver;
    }

    public List<String> getMessage() {
        return resultMessage;
    }

    public String getMessageJoined(boolean dotPoints) {
        if (dotPoints) {
            return "- " + String.join("\n- ", resultMessage);
        }
        return String.join("\n", resultMessage);
    }

    public double[] getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public String toLineString() {
        String msg = "Transfer: `" + status.name() + "` to " + receiver.getMarkdownUrl() + " for `" + ResourceType.resourcesToString(amount) + "` using note `" + note + "`";
        if (!resultMessage.isEmpty()) {
            msg += "\n" + getMessageJoined(true);
        }
        return msg;
    }

    public String toEmbedString() {
        StringBuilder body = new StringBuilder();
        body.append("**Status:** `").append(status.name()).append("`\n");
        body.append("**To:** ").append(receiver.getMarkdownUrl());
        if (receiver.isNation()) {
            DBNation nation = receiver.asNation();
            if (nation.getAlliance_id() > 0) {
                body.append(" | ").append(nation.getAlliance().getMarkdownUrl());
                if (nation.getPositionEnum().id <= Rank.APPLICANT.id) {
                    body.append(" (applicant)");
                }
            } else {
                body.append(" | AA:0");
            }
        }
        body.append("\n");
        body.append("**Amount:** `").append(ResourceType.resourcesToString(amount)).append("`\n");
        body.append(" - worth: `$" + MathMan.format(ResourceType.convertedTotal(amount)) + "`\n");
        body.append("**Note:** `").append(note).append("`\n");
        if (status != OffshoreInstance.TransferStatus.SUCCESS) {
            if (resultMessage.size() == 1) {
                body.append("**Response:** ").append(getMessageJoined(true));
            } else if (!resultMessage.isEmpty()) {
                body.append("**Response:**\n").append(getMessageJoined(true));
            }
        }
        return body.toString();
    }

    public String toTitleString() {
        String title;
        if (status.isSuccess()) {
            title = "Successfully transferred";
        } else {
            title = "Failed to transfer";
        }
        title += " to " + receiver.getTypePrefix() + ":" + receiver.getName();
        if (status != OffshoreInstance.TransferStatus.SUCCESS) {
            title += " (" + status.name() + ")";
        }
        return title;
    }
}
