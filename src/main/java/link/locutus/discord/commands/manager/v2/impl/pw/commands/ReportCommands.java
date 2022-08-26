package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ReportCommands {
//    public static String REPORT_SHEET = "1HoENcKOBgc9lkdNEPvAyDT_SVtU6qYxOL4CYHwPNaHU";
//    @Command(desc = "List recent reports, which you can upvote / downvote")
//    public void list(@Me DBNation me, @Me User user) {
//
//    }
//
//    @Command(desc = "List your own reports (and allow you to remove them)")
//    public void manage(@Me DBNation me, @Me User user) {
//
//    }
//
//    @Command
//    public String list(@Switch('n') DBNation nation, @Switch('u') User user) {
//
//    }
//
//    @Command(desc = "Report a nation")
//    public String report(@Me DBNation me, @Me User author, @Me GuildDB db, ReportType type, DBNation target, @TextArea String message, @Switch('i') String imageEvidenceUrl, @Switch('u') User user, @Switch('f') String forumPost, @Switch('m') Message newsReport, @Switch('s') SpreadSheet sheet) throws GeneralSecurityException, IOException {
//        if (sheet == null) sheet = SpreadSheet.create(REPORT_SHEET);
//
//
//        // Use the Locutus managed coalition whitelisted_news_servers. Add the following servers to them: RON, PoliticsAndWar
//        // Add validation for forum url and image url
//
//        /*
//        Report types:
//         - Multis / rerolls
//         - Stealing funds
//         - Not repaying loans
//         - Toxic language
//         - Being booted from an alliance?
//         - Leaking information
//         - FA blunders
//         */
//
//        // Report Id    Nation Id    Discord Id    Report Type    Reporter Nation Id    Reporter Discord Id    Reporter Alliance Id    Reporter Guild Id    Reporter Credentials (at time)    Report Message    Image Url    Forum Url    News Url
//
//        // TODO
//        // Auto generate multi information
//        // Auto generate alliance leave information
//    }
//
//    public static enum ReportType {
//        MULTI,
//        REROLL,
//        STEALING,
//        DEFAULT,
//        LEAKING,
//        BEHAVIOR,
//        FA_BLUNDER
//
//    }
}
