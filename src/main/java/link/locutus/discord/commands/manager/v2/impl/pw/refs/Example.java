package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.CommandRef;

import java.util.Map;

public class Example {
    private final CommandRef ref;
    private final String label;
    private final String description;

    public Example(CommandRef ref, String label, String description) {
        this.ref = ref;
        this.label = label;
        this.description = description;
    }
}
