package com.boydti.discord.commands.manager.result;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

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

    @Override
    public void build(MessageReceivedEvent event) {
        
    }
}
