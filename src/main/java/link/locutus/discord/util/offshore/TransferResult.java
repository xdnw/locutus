package link.locutus.discord.util.offshore;

import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TransferResult {
    private OffshoreInstance.TransferStatus status;
    private final NationOrAlliance receiver;
    private final List<String> resultMessage;
    private final double[] amount;
    private final String note;

    public TransferResult(OffshoreInstance.TransferStatus status, NationOrAlliance receiver, Map<ResourceType, Double> amount, String note) {
        this(status, receiver, PnwUtil.resourcesToArray(amount), note);
    }

    public void setStatus(OffshoreInstance.TransferStatus status) {
        this.status = status;
    }

    public TransferResult(OffshoreInstance.TransferStatus status, NationOrAlliance receiver, double[] amount, String note) {
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

    public NationOrAlliance getReceiver() {
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
        String msg = "Transfer: `" + status.name() + "` to " + receiver.getMarkdownUrl() + " for `" + PnwUtil.resourcesToString(amount) + "` using note `" + note + "`";
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
        body.append("**Amount:** `").append(PnwUtil.resourcesToString(amount)).append("`\n");
        body.append(" - worth: `$" + MathMan.format(PnwUtil.convertedTotal(amount)) + "`\n");
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
