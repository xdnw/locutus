package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.util.offshore.TransferResult;

import java.util.List;

public class WebTransferResult {
    public String status;
    public String status_msg;
    public boolean status_success;
    public int receiver_id;
    public boolean receiver_is_aa;
    public String receiver_name;
    public List<String> messages;
    public double[] amount;
    public String note;

    public WebTransferResult(TransferResult result) {
        this.status = result.getStatus().name();
        this.status_msg = result.getStatus().getMessage();
        this.status_success = result.getStatus().isSuccess();
        this.receiver_id = result.getReceiver().getId();
        this.receiver_is_aa = result.getReceiver().isAlliance();
        this.receiver_name = result.getReceiver().getName();
        this.messages = result.getMessage();
        this.amount = result.getAmount();
        this.note = result.getNote();
    }
}
