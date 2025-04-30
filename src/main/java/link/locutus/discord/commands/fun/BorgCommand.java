package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Messages;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class BorgCommand extends Command {
    public BorgCommand() {
        super("borg", "lucutus", "danzek", CommandCategory.FUN);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.fun.borg.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "borg";
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
//            return "https://cdn.discordapp.com/attachments/925584954958696508/931884130654904350/kpul5uloscn41.png";
            return Messages.SLOGAN;
        }

        String[] Bs = {"\uD835\uDDBB", "\uD835\uDDA1", "B", "b", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Os = {"\uD835\uDDC8", "\uD835\uDE7E", "O", "o", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Rs = {"\uD835\uDDCB", "\uD835\uDDB1", "R", "r", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Gs = {"\uD835\uDDC0", "\uD835\uDDA6", "G", "g", "\u200A", "\u200B", "\uFEFF", "\u180E"};

        String[][] CODES = new String[][]{
                Bs,
                Os,
                Rs,
                Gs
        };
        String msg = StringMan.join(args, " ");

        StringBuilder output = new StringBuilder();
        String input = msg;
        while (!input.isEmpty()) {
            boolean found = false;
            int id = 0;
            for (int tmp = 0; tmp < 2; tmp++) {
                outer:
                for (String[] codei : CODES) {
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
            else output.append('0' + id - 27);
        }
        if (output.length() != 0) {
            return "`" + output + "`\n\n- " + author.getName();
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
        return "`" + output + "`";
    }
}
