package link.locutus.discord.gpt;

import link.locutus.discord.config.Settings;

import java.sql.SQLException;
import java.util.List;

public class GPTTest {
    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        GptHandler handler = new GptHandler();
//        List<ModerationResult> blah = handler.getModerator().moderate("self harm");
//        System.out.println(blah);

        String response = handler.getText2text().generate("What is your name?");
        System.out.println(response);
    }
}
