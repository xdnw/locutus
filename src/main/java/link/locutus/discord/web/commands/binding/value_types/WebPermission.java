package link.locutus.discord.web.commands.binding.value_types;

public class WebPermission {
    public String message;
    public boolean success;

    public WebPermission(String message, boolean success) {
        this.message = message;
        this.success = success;
    }
}
