package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class Nev extends Command {
    private static final char[] zalgo_up =
            {'\u030d', /*        */'\u030e', /*    */'\u0304', /*        */'\u0305', /*         */
                    '\u033f', /*         */'\u0311', /*    */'\u0306', /*       */'\u0310', /*       */
                    '\u0352', /*         */'\u0357', /*    */'\u0351', /*       */'\u0307', /*       */
                    '\u0308', /*         */'\u030a', /*    */'\u0342', /*       */'\u0343', /*       */
                    '\u0344', /*          */'\u034a', /*     */'\u034b', /*       */'\u034c', /*       */
                    '\u0303', /*         */'\u0302', /*    */'\u030c', /*       */'\u0350', /*       */
                    '\u0300', /*         */'\u0301', /*    */'\u030b', /*       */'\u030f', /*       */
                    '\u0312', /*         */'\u0313', /*    */'\u0314', /*       */'\u033d', /*       */
                    '\u0309', /*         */'\u0363', /*    */'\u0364', /*       */'\u0365', /*       */
                    '\u0366', /*         */'\u0367', /*    */'\u0368', /*       */'\u0369', /*       */
                    '\u036a', /*         */'\u036b', /*    */'\u036c', /*       */'\u036d', /*       */
                    '\u036e', /*         */'\u036f', /*    */'\u033e', /*       */'\u035b', /*       */
                    '\u0346', /*         */'\u031a' /*    */
            };
    private static final char[] zalgo_down =
            {'\u0316', /*     */'\u0317', /*       */'\u0318', /*       */'\u0319', /*        */
                    '\u031c', /*       */'\u031d', /*       */'\u031e', /*       */'\u031f', /*        */
                    '\u0320', /*       */'\u0324', /*       */'\u0325', /*       */'\u0326', /*        */
                    '\u0329', /*       */'\u032a', /*       */'\u032b', /*       */'\u032c', /*        */
                    '\u032d', /*       */'\u032e', /*       */'\u032f', /*       */'\u0330', /*        */
                    '\u0331', /*       */'\u0332', /*       */'\u0333', /*       */'\u0339', /*        */
                    '\u033a', /*       */'\u033b', /*       */'\u033c', /*       */'\u0345', /*        */
                    '\u0347', /*       */'\u0348', /*       */'\u0349', /*       */'\u034d', /*        */
                    '\u034e', /*       */'\u0353', /*       */'\u0354', /*       */'\u0355', /*        */
                    '\u0356', /*       */'\u0359', /*       */'\u035a', /*       */'\u0323' /*        */
            };
    //those always stay in the middle
    private static final char[] zalgo_mid =
            {'\u0315', /*          */'\u031b', /*        */'\u0340', /*         */'\u0341', /*          */
                    '\u0358', /*          */'\u0321', /*        */'\u0322', /*         */'\u0327', /*          */
                    '\u0328', /*          */'\u0334', /*        */'\u0335', /*         */'\u0336', /*          */
                    '\u034f', /*          */'\u035c', /*        */'\u035d', /*         */'\u035e', /*          */
                    '\u035f', /*          */'\u0360', /*        */'\u0362', /*         */'\u0338', /*          */
                    '\u0337', /*          */'\u0361', /*        */'\u0489' /*        */
            };
    int zal = 0;

    public Nev() {
        super(CommandCategory.FUN);
    }

    private static int rand(int max) {
        return (int) Math.floor(Math.random() * max);
    }

    private static char rand_zalgo(char[] array) {
        int ind = (int) Math.floor(Math.random() * array.length);
        return array[ind];
    }


    // rand funcs
    //---------------------------------------------------

    //gets an int between 0 and max

    private static boolean is_zalgo_char(char c) {
        for (char value : zalgo_up)
            if (c == value)
                return true;
        for (char value : zalgo_down)
            if (c == value)
                return true;
        for (char value : zalgo_mid)
            if (c == value)
                return true;
        return false;
    }

    //gets a random char from a zalgo char table

    public static String goZalgo(String iText, boolean zalgo_opt_mini, boolean zalgo_opt_normal, boolean up,
                                 boolean down, boolean mid) {
        StringBuilder zalgoTxt = new StringBuilder();

        for (int i = 0; i < iText.length(); i++) {
            if (is_zalgo_char(iText.charAt(i)))
                continue;

            int num_up;
            int num_mid;
            int num_down;

            //add the normal character
            zalgoTxt.append(iText.charAt(i));

            //options
            if (zalgo_opt_mini) {
                num_up = rand(8);
                num_mid = rand(2);
                num_down = rand(8);
            } else if (zalgo_opt_normal) {
                num_up = rand(16) / 2 + 1;
                num_mid = rand(6) / 2;
                num_down = rand(16) / 2 + 1;
            } else //maxi
            {
                num_up = rand(64) / 4 + 3;
                num_mid = rand(16) / 4 + 1;
                num_down = rand(64) / 4 + 3;
            }

            if (up)
                for (int j = 0; j < num_up; j++)
                    zalgoTxt.append(rand_zalgo(zalgo_up));
            if (mid)
                for (int j = 0; j < num_mid; j++)
                    zalgoTxt.append(rand_zalgo(zalgo_mid));
            if (down)
                for (int j = 0; j < num_down; j++)
                    zalgoTxt.append(rand_zalgo(zalgo_down));
        }


        return zalgoTxt.toString();
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of();
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String text = "Nev. The warm light in this cold dark world. The music note in the deafening wasteland of our society.";

        if (zal++ > 0) {
            StringBuilder result = new StringBuilder();
            char[] arr = text.toCharArray();
            for (char myChar : arr) {
                if (zal >= arr.length || ThreadLocalRandom.current().nextInt(arr.length - zal) == 0) {
                    String zalChar = goZalgo("" + myChar, true, false, true, true, true);
                    result.append(zalChar);
                } else {
                    result.append(myChar);
                }
            }
            return result.toString();
        }

        return text;
    }
}
