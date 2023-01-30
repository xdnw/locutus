package link.locutus.discord.commands.account.question.questions;

import link.locutus.discord.commands.account.question.Question;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;

public enum MMRTutorial implements Question {
    START("MMR stands for minimum military requirement, and is the military buildings required in each city.\n" +
            "Specified in the format: e.g. `mmr=0251` which is:\n" +
            "0 barracks\n" +
            "2 factories\n" +
            "5 hangars\n" +
            "0 drydock\n", false),

    DONE("That's all  for now. Check back later.", true) {
        @Override
        public boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
            return false;
        }
    }

    ;

    private final String content;
    private final boolean validateOnInit;
    private final String[] options;

    MMRTutorial(String content, boolean validateOnInit, String... options) {
        this.content = content;
        this.validateOnInit = validateOnInit;
        this.options = options;
    }

    public String getContent() {
        return content;
    }

    public boolean isValidateOnInit() {
        return validateOnInit;
    }

    public String[] getOptions() {
        return options;
    }
}
