package link.locutus.discord.gpt.pw;

import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DBMainV3;

import java.sql.SQLException;

public class GptDatabase extends DBMainV3 {
    public GptDatabase() throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, "gpt", false);
    }

    @Override
    public void createTables() {

    }
}
