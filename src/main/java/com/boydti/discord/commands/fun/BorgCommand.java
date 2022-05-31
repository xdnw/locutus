package com.boydti.discord.commands.fun;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.config.Messages;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BorgCommand extends Command {
    public BorgCommand() {
        super("borg", "lucutus", "danzek", CommandCategory.FUN);
    }

    @Override
    public String help() {
        return "!borg";
    }

    @Override
    public String desc() {
        return "The Borg are a friendly race sharing their appreciation for equality and cultural appropriation via their trademark Borg cubes. Their enemies, the human supremacists of the federation are all that stands in their way.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) {
        if (args.isEmpty()) {
//            return "https://cdn.discordapp.com/attachments/925584954958696508/931884130654904350/kpul5uloscn41.png";
            return Messages.SLOGAN;
        }

        String[] Bs = {"\uD835\uDDBB", "\uD835\uDDA1", "B", "b", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Os = {"\uD835\uDDC8", "\uD835\uDE7E", "O", "o", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Rs = {"\uD835\uDDCB", "\uD835\uDDB1", "R", "r", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Gs = {"\uD835\uDDC0", "\uD835\uDDA6", "G", "g", "\u200A", "\u200B", "\uFEFF", "\u180E"};

        String[][] CODES = new String[][] {
                Bs,
                Os,
                Rs,
                Gs
        };
        String msg = StringMan.join(args, " ");

        StringBuilder output = new StringBuilder();
        if (true) {
            String input = msg;
            while (!input.isEmpty()) {
                boolean found = false;
                int id = 0;
                for (int tmp = 0; tmp < 2; tmp++) {
                    outer:
                    for (int i = 0; i < CODES.length; i++) {
                        String[] codei = CODES[i];
                        for (int j = 0; j < codei.length; j++) {
                            String codeij = codei[j];
                            if (input.startsWith(codeij)) {
                                input = input.substring(codeij.length());
                                id += j << (tmp * 3);
                                found = true;
                                break outer;
                            }
                        }
                    }
                }
                if (!found) {
                    output.setLength(0);
                    break;
                }
                if (id == 0) output.append(" ");
                else if (id <= 'z' + 1) output.append((char) ('a' + id - 1));
                else output.append((char) '0' + id - 27);
            }
            if (output.length() != 0) {
                return "`" + output.toString() + "`\n\n - " + event.getAuthor().getName();
            }
        }
        int i = 0;

        msg = msg.toLowerCase();
        int id = -1;
        for (char c : msg.toCharArray()) {
            if (c == ' ') id = 0;
            else if (Character.isLetter(c)) id = 1 + (c - 'a');
            else if (Character.isDigit(c)) id = 26 + 1 + '0';

            if (id == -1) continue;

            for (int j = 0; j < 2; j++) {
                int letterId = id & 0x7;
                id = id >> 3;

                String letter = CODES[i % 4][letterId];
                if (letterId < 4) i++;
                output.append(letter);
            }
        }
        return "`" + output.toString() + "`";
    }
}
