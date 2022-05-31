package com.boydti.discord.commands.bank;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class AddTaxBracket extends Command {
    public AddTaxBracket() {
        super(CommandCategory.ECON);
    }

    @Override
    public String help() {
        return super.help() + " <tax-url> <money-rate> <rss-rate>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 3) {
            return usage(event);
        }
        if (!args.get(0).contains("tax_id=")) {
            return "Invalid tax url: `" + args.get(0) + "`";
        }
        int taxId = Integer.parseInt(args.get(0).split("=")[0]);
        if (!MathMan.isInteger(args.get(1))) return "Invalid money tax rate: `" + args.get(1) + "`";
        if (!MathMan.isInteger(args.get(2))) return "Invalid money tax rate: `" + args.get(2) + "`";
        int money = Integer.parseInt(args.get(1));
        int rss = Integer.parseInt(args.get(2));

        Locutus.imp().getBankDB().addTaxBracket(taxId, money, rss);

        return "Set " + taxId + " to " + money + "/" + rss;
    }
}
