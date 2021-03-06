package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.config.Messages;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.NationMeta;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FunCommands {
    private Map<Integer, Boolean> received = new ConcurrentHashMap<>();

    @Command(desc = "He's making a list, And checking it twice; Gonna find out Who's naughty and nice. Saint Borgolas is coming to town. **RESISTANCE IS FUTILE**")
    public String borgmas(@Me DBNation me) throws IOException {
        if (me.getMeta(NationMeta.BORGMAS) != null || received.put(me.getNation_id(), true) != null) {
            return "You've already opened your presents this year. Merry Borgmas!";
        }

        Map.Entry<Integer, Integer> commends = me.getCommends();
        if (commends.getKey() == 0 && commends.getValue() == 0) {
            return me.getLeader() + "... is not on the list. How unfortunate \uD83D\uDE44";
        }

        me.setMeta(NationMeta.BORGMAS, (byte) 1);

        int up = commends.getKey();
        int down = commends.getValue();

        Map<ResourceType, Double> resources;
        String message;
        boolean good = (down == 0 || down * 3 < up);
        if (good) {
            message = "You open your present to find... a borg implant! (Batteries not included, may contain traces of nuts and bolts).\nMerry Borgmas!\nhttps://dcassetcdn.com/w1k/submissions/160000/160404_d209.jpg";
            resources = Collections.singletonMap(ResourceType.STEEL, 0.01);
        } else {
            message = "You open your present to find a 10kg hunk of coal...\nMerry Borgmas!\nhttps://dcassetcdn.com/w1k/submissions/160000/160404_d209.jpg";
            resources = Collections.singletonMap(ResourceType.COAL, 0.01);
        }

        String note = "Merry Borgmas!";
        Map.Entry<OffshoreInstance.TransferStatus, String> result = Locutus.imp().getRootBank().transfer(me, resources, note);
        return message;
    }

    private String[] lines = null;

    @Command
    public String joke() {
        if (lines == null) {
            lines = FileUtil.readFile("/fun/jokes.txt").split("\\r?\\n");
        }
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }

    @Command(desc = "The Borg are a friendly race sharing their appreciation for equality and cultural appropriation via their trademark Borg cubes. Their enemies, the human supremacists of the federation are all that stands in their way.")
    public String borg(@Default String msg) {
        if (msg == null || msg.isEmpty()) {
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
                return "Output:\n" + output.toString();
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
        return "Output:\n" + output.toString();
    }
}
