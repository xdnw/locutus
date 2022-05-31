package com.boydti.discord.web.test;

import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Filter;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;

import java.util.Set;

public class TestCommands {

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }
}