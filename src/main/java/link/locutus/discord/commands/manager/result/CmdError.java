package link.locutus.discord.commands.manager.result;

public class CmdError extends CmdResult {

    public CmdError(Throwable e) {
        super(false);
        if (e.getMessage() == null) {
            message(e.getClass().getSimpleName());
        } else if (e instanceof IllegalArgumentException) {
            message(e.getMessage());
        } else {
            message(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
