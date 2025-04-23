package link.locutus.discord.util.task.mail;

import link.locutus.discord.web.WebUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record MailApiResponse(MailApiSuccess status, String error) {
    @NotNull
    @Override
    public String toString() {
        if (error != null && !error.isEmpty()) {
            return WebUtil.GSON.toJson(Map.of(
                    "status", status.toString(),
                    "error", error
            ));
        }
        return WebUtil.GSON.toJson(Map.of(
                "status", status.toString()
        ));
    }
}
